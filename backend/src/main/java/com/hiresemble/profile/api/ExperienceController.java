package com.hiresemble.profile.api;

import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.profile.api.dto.ExperienceDtos.ExperienceItemDetailDto;
import com.hiresemble.profile.api.dto.ExperienceDtos.ExperienceItemDto;
import com.hiresemble.profile.api.dto.ExperienceRequests.ExperienceItemUpdateRequest;
import com.hiresemble.profile.api.dto.ExperienceRequests.ExperienceMatchResolutionRequest;
import com.hiresemble.profile.api.dto.ExperienceRequests.ExperienceVerificationRequest;
import com.hiresemble.profile.api.dto.PageResponse;
import com.hiresemble.profile.application.service.ExperienceApplicationService;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceMatchDecision;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceVerification;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceWrite;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value = "/api/v1/profile/experiences", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Authenticated profile and canonical experience library")
@SecurityRequirement(name = "sessionCookie")
public class ExperienceController {

    private final ExperienceApplicationService service;

    public ExperienceController(ExperienceApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            operationId = "listExperienceItems",
            summary = "List canonical experience items",
            description = "Lists one owner-scoped card per normalized strength or experience; corroborating document sources do not create another card.")
    public PageResponse<ExperienceItemDto> list(
            @RequestParam(required = false) EvidenceVerificationStatus verificationStatus,
            @RequestParam(required = false) ExperienceMatchKind matchKind,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ExperienceDtoMapper.page(service.list(
                user.id(), verificationStatus, matchKind, page, size, sort));
    }

    @GetMapping("/{experienceItemId}")
    @Operation(
            operationId = "getExperienceItem",
            summary = "Get one canonical experience and its sources",
            description = "Returns the canonical item and its owner-scoped source evidence links, including deleted-source tombstones.")
    public ExperienceItemDetailDto get(
            @PathVariable UUID experienceItemId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ExperienceDtoMapper.detail(service.get(user.id(), experienceItemId));
    }

    @PutMapping(value = "/{experienceItemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateExperienceItem",
            summary = "Edit a canonical experience",
            description = "Updates canonical wording with optimistic versioning; the current semantic embedding becomes stale until regenerated.")
    public ExperienceItemDetailDto update(
            @PathVariable UUID experienceItemId,
            @Valid @RequestBody ExperienceItemUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ExperienceDtoMapper.detail(service.update(
                user.id(),
                experienceItemId,
                new ExperienceWrite(request.title(), request.content(), request.version())));
    }

    @PatchMapping(
            value = "/{experienceItemId}/verification",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "verifyExperienceItem",
            summary = "Approve or reject a canonical experience",
            description = "Transitions the canonical experience and its downstream evidence projection with optimistic versioning.")
    public ExperienceItemDetailDto verify(
            @PathVariable UUID experienceItemId,
            @Valid @RequestBody ExperienceVerificationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ExperienceDtoMapper.detail(service.verify(
                user.id(),
                experienceItemId,
                new ExperienceVerification(request.status(), request.version())));
    }

    @PatchMapping(
            value = "/{experienceItemId}/match-resolution",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "resolveExperienceMatch",
            summary = "Resolve an ambiguous or conflicting match",
            description = "Keeps the candidate separate or merges its source links into the server-suggested owner-scoped target.")
    public ExperienceItemDetailDto resolveMatch(
            @PathVariable UUID experienceItemId,
            @Valid @RequestBody ExperienceMatchResolutionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ExperienceDtoMapper.detail(service.resolveMatch(
                user.id(),
                experienceItemId,
                new ExperienceMatchDecision(
                        request.resolution(), request.targetExperienceItemId(), request.version())));
    }
}
