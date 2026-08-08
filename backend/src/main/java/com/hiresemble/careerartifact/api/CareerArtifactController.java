package com.hiresemble.careerartifact.api;

import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactAiModelDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactDetailDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactDownloadUrlDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactReadinessDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactSummaryDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactVersionSummaryDto;
import com.hiresemble.careerartifact.api.CareerArtifactRequests.CareerArtifactRenderProfileWrite;
import com.hiresemble.careerartifact.api.CareerArtifactRequests.CareerArtifactVersionRequest;
import com.hiresemble.careerartifact.api.CareerArtifactRequests.CreateCareerArtifactRequest;
import com.hiresemble.careerartifact.api.CareerArtifactRequests.GenerateCareerArtifactRequest;
import com.hiresemble.careerartifact.application.CareerArtifactApplicationService;
import com.hiresemble.careerartifact.application.CareerArtifactCommands.GenerationInput;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.LifecycleStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ProfileSection;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.profile.api.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/career-artifacts")
@ConditionalOnProperty(name = "hiresemble.career-artifact.enabled", havingValue = "true")
@Tag(name = "Career Artifacts", description = "Owner-scoped AI resume and portfolio artifacts.")
@SecurityRequirement(name = "sessionCookie")
public class CareerArtifactController {

    private final CareerArtifactApplicationService service;
    private final CareerArtifactApiMapper mapper;

    public CareerArtifactController(
            CareerArtifactApplicationService service,
            CareerArtifactApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/readiness")
    @Operation(operationId = "getCareerArtifactReadiness", summary = "Get generation readiness")
    public CareerArtifactReadinessDto readiness(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.readiness(service.readiness(user.id()));
    }

    @GetMapping("/ai-models")
    @Operation(operationId = "listCareerArtifactAiModels", summary = "List exact models")
    public List<CareerArtifactAiModelDto> models(
            @RequestParam ArtifactType type,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.models(type).stream().map(mapper::model).toList();
    }

    @PostMapping
    @Operation(operationId = "createCareerArtifact", summary = "Create and generate an artifact")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> create(
            @Valid @RequestBody CreateCareerArtifactRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var response = service.create(user.id(), createInput(request), idempotencyKey);
        var run = response.body();
        return ResponseEntity.status(response.status()).body(new RunAcceptedDto(
                run.agentRunId(), run.status(), run.resourceType(), run.resourceId(),
                response.replayed()));
    }

    @GetMapping
    @Operation(operationId = "listCareerArtifacts", summary = "List artifacts")
    public PageResponse<CareerArtifactSummaryDto> list(
            @RequestParam(required = false) ArtifactType artifactType,
            @RequestParam(required = false) LifecycleStatus lifecycleStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var values = service.list(
                user.id(), artifactType, lifecycleStatus, page, size, sort);
        return new PageResponse<>(
                values.items().stream().map(mapper::summary).toList(),
                values.page(), values.size(), values.totalElements(), values.totalPages());
    }

    @GetMapping("/{artifactId}")
    @Operation(operationId = "getCareerArtifact", summary = "Get artifact detail")
    public CareerArtifactDetailDto detail(
            @PathVariable UUID artifactId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.detail(user.id(), artifactId));
    }

    @GetMapping("/{artifactId}/versions")
    @Operation(operationId = "listCareerArtifactVersions", summary = "List immutable versions")
    public PageResponse<CareerArtifactVersionSummaryDto> versions(
            @PathVariable UUID artifactId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "versionNo,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var values = service.versions(user.id(), artifactId, page, size, sort);
        return new PageResponse<>(
                values.items().stream().map(mapper::version).toList(),
                values.page(), values.size(), values.totalElements(), values.totalPages());
    }

    @PostMapping("/{artifactId}/generations")
    @Operation(operationId = "generateCareerArtifactVersion", summary = "Regenerate an artifact")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> regenerate(
            @PathVariable UUID artifactId,
            @Valid @RequestBody GenerateCareerArtifactRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var response = service.regenerate(
                user.id(), artifactId, generationInput(request), idempotencyKey);
        var run = response.body();
        return ResponseEntity.status(response.status()).body(new RunAcceptedDto(
                run.agentRunId(), run.status(), run.resourceType(), run.resourceId(),
                response.replayed()));
    }

    @PostMapping("/{artifactId}/archive")
    @Operation(operationId = "archiveCareerArtifact", summary = "Archive an artifact")
    public CareerArtifactDetailDto archive(
            @PathVariable UUID artifactId,
            @Valid @RequestBody CareerArtifactVersionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.archive(user.id(), artifactId, request.version()));
    }

    @PostMapping("/{artifactId}/unarchive")
    @Operation(operationId = "unarchiveCareerArtifact", summary = "Unarchive an artifact")
    public CareerArtifactDetailDto unarchive(
            @PathVariable UUID artifactId,
            @Valid @RequestBody CareerArtifactVersionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.unarchive(user.id(), artifactId, request.version()));
    }

    @PostMapping("/{artifactId}/versions/{versionId}/download-url")
    @Operation(operationId = "createCareerArtifactDownloadUrl", summary = "Create a five-minute attachment URL")
    public CareerArtifactDownloadUrlDto download(
            @PathVariable UUID artifactId,
            @PathVariable UUID versionId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.download(service.download(user.id(), artifactId, versionId));
    }

    @DeleteMapping("/{artifactId}")
    @Operation(operationId = "deleteCareerArtifact", summary = "Soft delete an artifact")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> delete(
            @PathVariable UUID artifactId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user.id(), artifactId, version);
        return ResponseEntity.noContent().build();
    }

    private GenerationInput createInput(CreateCareerArtifactRequest request) {
        return new GenerationInput(
                request.artifactType(), request.title(), request.experienceItemIds(),
                request.model(), request.templateKey(), sections(request.includeProfileSections()),
                renderProfile(request.renderProfile()), null);
    }

    private GenerationInput generationInput(GenerateCareerArtifactRequest request) {
        return new GenerationInput(
                null, null, request.experienceItemIds(), request.model(), request.templateKey(),
                sections(request.includeProfileSections()), renderProfile(request.renderProfile()),
                request.version());
    }

    private Set<ProfileSection> sections(List<ProfileSection> values) {
        if (values == null) return Set.of();
        LinkedHashSet<ProfileSection> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return Set.copyOf(unique);
    }

    private CareerArtifactRenderProfile renderProfile(
            CareerArtifactRenderProfileWrite value) {
        try {
            return new CareerArtifactRenderProfile(
                    value.displayName(), value.email(), value.phone(),
                    value.links() == null ? List.of() : value.links().stream()
                            .map(link -> new CareerArtifactRenderProfile.Link(
                                    link.label(), link.url()))
                            .toList(),
                    value.includeContact());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
