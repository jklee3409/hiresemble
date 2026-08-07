package com.hiresemble.githubsource.api;

import com.hiresemble.agentrun.api.dto.RequiredUserActionDto;
import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubSourceKind;
import com.hiresemble.githubsource.domain.GitHubSourceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public final class GitHubSourceDtos {

    private GitHubSourceDtos() {}

    @Schema(name = "GitHubRepositoryDto")
    public record GitHubRepositoryDto(
            UUID id,
            String ownerLogin,
            String repositoryName,
            String canonicalUrl,
            String description,
            String defaultBranch,
            boolean fork,
            boolean archived,
            boolean selected,
            Instant pushedAt) {}

    @Schema(name = "GitHubSourceSummaryDto")
    public record GitHubSourceSummaryDto(
            UUID id,
            GitHubSourceKind sourceKind,
            GitHubAccountType accountType,
            String canonicalUrl,
            String ownerLogin,
            String repositoryName,
            GitHubSourceStatus status,
            int discoveredRepositoryCount,
            int selectedRepositoryCount,
            boolean repositoryDiscoveryTruncated,
            int newExperienceCount,
            int corroboratedExperienceCount,
            int reviewRequiredCount,
            int rejectedCandidateCount,
            boolean snapshotIncomplete,
            UUID latestAgentRunId,
            Instant lastSuccessfulSyncAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "GitHubSourceDetailDto")
    public record GitHubSourceDetailDto(
            GitHubSourceSummaryDto source,
            @Schema(nullable = true) RequiredUserActionDto requiredUserAction) {}

    @Schema(name = "GitHubRefreshResultDto")
    public record GitHubRefreshResultDto(
            boolean changed,
            GitHubSourceDetailDto source,
            @Schema(nullable = true) RunAcceptedDto run) {}
}
