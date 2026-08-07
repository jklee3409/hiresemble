package com.hiresemble.githubsource.application;

import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import java.util.List;
import java.util.UUID;

public interface GitHubWorkflowQueryPort {

    Source source(UUID userId, UUID sourceId);

    List<Repository> selectedRepositories(UUID userId, UUID sourceId);

    GitHubWorkflowModels.SnapshotBundle snapshotBundle(
            UUID userId, UUID sourceId, UUID repositoryId);

    EmbeddingPolicy activeEmbeddingPolicy();
}
