package com.hiresemble.githubsource.application;

import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator.ValidationResult;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.ApplySummary;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.Discovery;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.FinalSummary;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.RawCapture;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.SnapshotBundle;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GitHubWorkflowCommandPort {

    Source begin(UUID userId, UUID sourceId, UUID runId, Instant now);

    Discovery discover(UUID userId, UUID sourceId, UUID runId, Instant now);

    RawCapture capture(UUID userId, UUID sourceId, Repository repository);

    SnapshotBundle captureAndStore(
            UUID userId, UUID sourceId, Repository repository, Instant now);

    SnapshotBundle sanitizeAndStore(UUID userId, UUID sourceId, RawCapture capture, Instant now);

    ValidationResult validateCandidates(
            UUID userId,
            UUID sourceId,
            long sourceRevision,
            SnapshotBundle bundle,
            List<GitHubEvidenceCandidate> candidates);

    ApplySummary applyCandidates(
            UUID userId,
            UUID sourceId,
            SnapshotBundle bundle,
            ValidationResult validation,
            EmbeddingPolicy embeddingPolicy,
            Instant now);

    FinalSummary finalizeSource(
            UUID userId,
            UUID sourceId,
            UUID runId,
            boolean partial,
            List<ApplySummary> summaries,
            int extraRejectedCount,
            Instant now);

    void fail(UUID userId, UUID sourceId, UUID runId, Instant now);
}
