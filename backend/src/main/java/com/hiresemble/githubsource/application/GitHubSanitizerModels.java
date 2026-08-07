package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import java.util.List;
import java.util.Map;

public final class GitHubSanitizerModels {

    private GitHubSanitizerModels() {}

    public record SanitizedUnit(
            String unitType,
            String repositoryPath,
            String blobSha,
            String language,
            Integer lineStart,
            Integer lineEnd,
            String contentHash,
            String excerpt,
            String content) {}

    public record SanitizedRepository(
            RepositoryMetadata repository,
            String commitSha,
            String treeSha,
            List<SanitizedUnit> units,
            boolean selectionComplete,
            boolean upstreamTruncated,
            int sanitizedCodePoints,
            int excludedFileCount) {
        public SanitizedRepository {
            units = List.copyOf(units);
        }
    }

    public record RawRepository(
            RepositoryMetadata repository,
            String commitSha,
            String treeSha,
            boolean upstreamTruncated,
            Map<String, Long> languages,
            List<RawFile> files,
            boolean fileFetchComplete,
            int eligibleFileCount) {
        public RawRepository {
            languages = Map.copyOf(languages);
            files = List.copyOf(files);
        }
    }

    public record RawFile(TreeEntry entry, byte[] content) {
        public RawFile {
            content = content.clone();
        }
    }
}
