package com.hiresemble.githubsource.api;

import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubRefreshResultDto;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubRepositoryDto;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubSourceDetailDto;
import com.hiresemble.githubsource.api.GitHubSourceDtos.GitHubSourceSummaryDto;
import com.hiresemble.githubsource.api.GitHubSourceRequests.CreateGitHubSourceRequest;
import com.hiresemble.githubsource.api.GitHubSourceRequests.RefreshRequest;
import com.hiresemble.githubsource.api.GitHubSourceRequests.RepositorySelectionRequest;
import com.hiresemble.githubsource.application.GitHubSourceApplicationService;
import com.hiresemble.githubsource.application.GitHubSourceApplicationService.RefreshResult;
import com.hiresemble.githubsource.domain.GitHubSourceKind;
import com.hiresemble.githubsource.domain.GitHubSourceStatus;
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
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/github-sources")
@ConditionalOnProperty(name = "hiresemble.github.enabled", havingValue = "true")
@Tag(name = "GitHub Sources", description = "Owner-scoped public GitHub ingestion.")
@SecurityRequirement(name = "sessionCookie")
public class GitHubSourceController {

    private final GitHubSourceApplicationService service;
    private final GitHubSourceApiMapper mapper;

    public GitHubSourceController(
            GitHubSourceApplicationService service, GitHubSourceApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(operationId = "createGitHubSource", summary = "Register a public GitHub source")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> create(
            @Valid @RequestBody CreateGitHubSourceRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var response = service.register(
                user.id(), request.url(), request.participationConfirmed(), idempotencyKey);
        return ResponseEntity.status(response.status())
                .body(mapper.run(response.body(), response.replayed()));
    }

    @GetMapping
    @Operation(operationId = "listGitHubSources", summary = "List GitHub sources")
    public PageResponse<GitHubSourceSummaryDto> list(
            @RequestParam(required = false) GitHubSourceStatus status,
            @RequestParam(required = false) GitHubSourceKind sourceKind,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var values = service.list(user.id(), status, sourceKind, page, size, sort);
        return new PageResponse<>(
                values.items().stream().map(mapper::summary).toList(),
                values.page(), values.size(), values.totalElements(), values.totalPages());
    }

    @GetMapping("/{sourceId}")
    @Operation(operationId = "getGitHubSource", summary = "Get GitHub source detail")
    public GitHubSourceDetailDto detail(
            @PathVariable UUID sourceId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.detail(user.id(), sourceId));
    }

    @GetMapping("/{sourceId}/repositories")
    @Operation(operationId = "listGitHubRepositories", summary = "List discovered repositories")
    public PageResponse<GitHubRepositoryDto> repositories(
            @PathVariable UUID sourceId,
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) Boolean selected,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "pushedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var values = service.repositories(
                user.id(), sourceId, query, selected, page, size, sort);
        return new PageResponse<>(
                values.items().stream().map(mapper::repository).toList(),
                values.page(), values.size(), values.totalElements(), values.totalPages());
    }

    @PutMapping("/{sourceId}/repository-selection")
    @Operation(operationId = "selectGitHubRepositories", summary = "Select repositories and resume the same run")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> select(
            @PathVariable UUID sourceId,
            @Valid @RequestBody RepositorySelectionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var response = service.selectRepositories(
                user.id(), sourceId, request.repositoryIds(), request.version(), idempotencyKey);
        return ResponseEntity.status(response.status())
                .body(mapper.run(response.body(), response.replayed()));
    }

    @PostMapping("/{sourceId}/refresh")
    @Operation(operationId = "refreshGitHubSource", summary = "Refresh a GitHub source")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = GitHubRefreshResultDto.class)))
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = GitHubRefreshResultDto.class)))
    public ResponseEntity<GitHubRefreshResultDto> refresh(
            @PathVariable UUID sourceId,
            @Valid @RequestBody RefreshRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<RefreshResult> response = service.refresh(
                user.id(), sourceId, request.version(), idempotencyKey);
        return ResponseEntity.status(HttpStatusCode.valueOf(response.status()))
                .body(mapper.refresh(response.body(), response.replayed()));
    }

    @DeleteMapping("/{sourceId}")
    @Operation(operationId = "deleteGitHubSource", summary = "Delete a GitHub source")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> delete(
            @PathVariable UUID sourceId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user.id(), sourceId, version);
        return ResponseEntity.noContent().build();
    }
}
