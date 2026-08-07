package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.domain.GitHubAccountType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class GitHubGatewayModels {

    private GitHubGatewayModels() {}

    public record RepositoryMetadata(
            long externalId,
            String nodeId,
            String ownerLogin,
            String repositoryName,
            String canonicalUrl,
            String defaultBranch,
            boolean privateRepository,
            boolean fork,
            boolean archived,
            String description,
            List<String> topics,
            Instant pushedAt,
            String etag) {
        public RepositoryMetadata {
            topics = topics == null ? List.of() : List.copyOf(topics);
        }
    }

    public record AccountDiscovery(
            GitHubAccountType accountType,
            List<RepositoryMetadata> repositories,
            boolean truncated) {
        public AccountDiscovery {
            repositories = List.copyOf(repositories);
        }
    }

    public record ConditionalRepository(RepositoryMetadata repository, boolean notModified) {}

    public record CommitMetadata(String commitSha, String treeSha, String etag) {}

    public record TreeEntry(String path, String mode, String type, long size, String sha) {}

    public record TreeSnapshot(
            String treeSha, List<TreeEntry> entries, boolean truncated, String etag) {
        public TreeSnapshot {
            entries = List.copyOf(entries);
        }
    }

    public record Blob(String sha, byte[] content) {
        public Blob {
            content = content.clone();
        }
    }

    public record RepositoryCapture(
            RepositoryMetadata repository,
            CommitMetadata commit,
            TreeSnapshot tree,
            Map<String, Long> languages,
            List<BlobWithPath> blobs) {
        public RepositoryCapture {
            languages = Map.copyOf(languages);
            blobs = List.copyOf(blobs);
        }
    }

    public record BlobWithPath(TreeEntry entry, Blob blob) {}
}
