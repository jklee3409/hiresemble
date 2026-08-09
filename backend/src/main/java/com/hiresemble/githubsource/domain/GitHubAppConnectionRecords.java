package com.hiresemble.githubsource.domain;

import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GitHubAppConnectionRecords {

    private GitHubAppConnectionRecords() {}

    public record Attempt(
            UUID id,
            UUID userId,
            String sessionBindingDigest,
            String stateDigest,
            String phase,
            Long pendingInstallationId,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record Connection(
            UUID id,
            UUID userId,
            long installationId,
            long targetAccountId,
            String targetAccountLogin,
            GitHubAccountType targetAccountType,
            GitHubRepositorySelectionKind repositorySelection,
            GitHubAppConnectionStatus status,
            long version,
            Instant connectedAt,
            Instant verifiedAt,
            Instant lastCheckedAt,
            Instant disconnectedAt) {}

    public record VerifiedInstallation(
            long installationId,
            long targetAccountId,
            String targetAccountLogin,
            GitHubAccountType targetAccountType,
            GitHubRepositorySelectionKind repositorySelection,
            boolean suspended,
            List<RepositoryMetadata> repositories) {
        public VerifiedInstallation {
            repositories = List.copyOf(repositories);
        }
    }

    public record TokenTarget(
            UUID connectionId,
            long installationId,
            GitHubAccountType targetAccountType,
            GitHubAppConnectionStatus status) {}
}
