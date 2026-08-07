package com.hiresemble.githubsource.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GitHubSourceRecords {

    private GitHubSourceRecords() {}

    public record Source(
            UUID id,
            UUID userId,
            GitHubSourceKind sourceKind,
            GitHubAccountType accountType,
            String originalUrl,
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
            long sourceRevision,
            Instant lastSuccessfulSyncAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {}

    public record Repository(
            UUID id,
            UUID userId,
            long externalRepositoryId,
            String nodeId,
            String ownerLogin,
            String repositoryName,
            String canonicalUrl,
            String defaultBranch,
            boolean fork,
            boolean archived,
            String description,
            List<String> topics,
            String metadataEtag,
            boolean selected,
            Integer selectionOrder,
            Instant pushedAt,
            Instant createdAt,
            Instant updatedAt) {
        public Repository {
            topics = topics == null ? List.of() : List.copyOf(topics);
        }
    }

    public record Snapshot(
            UUID id,
            UUID userId,
            UUID repositoryId,
            String commitSha,
            String treeSha,
            String githubApiVersion,
            String retrievalPolicyVersion,
            boolean selectionComplete,
            boolean upstreamTruncated,
            String storageKey,
            String checksumSha256,
            long sanitizedBytes,
            Instant capturedAt) {}

    public record SourceUnit(
            UUID id,
            UUID userId,
            UUID snapshotId,
            String unitType,
            String repositoryPath,
            String blobSha,
            String language,
            Integer lineStart,
            Integer lineEnd,
            String contentHash,
            String excerpt,
            int snapshotOrdinal,
            Instant createdAt) {}

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public Page {
            items = List.copyOf(items);
        }
    }
}
