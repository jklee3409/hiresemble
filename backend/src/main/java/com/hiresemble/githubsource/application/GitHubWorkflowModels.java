package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.application.GitHubSanitizerModels.RawRepository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Snapshot;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.SourceUnit;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.ApplyResult;
import java.util.List;
import java.util.UUID;

public final class GitHubWorkflowModels {

    private GitHubWorkflowModels() {}

    public record Discovery(Source source, List<Repository> repositories) {
        public Discovery {
            repositories = List.copyOf(repositories);
        }
    }

    public record RawCapture(
            Repository repository,
            Snapshot reusableSnapshot,
            RawRepository rawRepository) {
        public RawCapture {
            if ((reusableSnapshot == null) == (rawRepository == null)) {
                throw new IllegalArgumentException("capture must be reusable or fresh");
            }
        }

        public boolean reused() {
            return reusableSnapshot != null;
        }
    }

    public record SnapshotBundle(
            Repository repository,
            Snapshot snapshot,
            List<SourceUnitContent> units,
            boolean incomplete,
            boolean reused) {
        public SnapshotBundle {
            units = List.copyOf(units);
            if (units.isEmpty()) throw new IllegalArgumentException("snapshot units are empty");
        }
    }

    public record SourceUnitContent(String opaqueReference, SourceUnit unit, String content) {}

    public record ValidatedCandidates(
            UUID repositoryId,
            UUID snapshotId,
            List<com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate>
                    candidates,
            int rejectedCount) {
        public ValidatedCandidates {
            candidates = List.copyOf(candidates);
        }
    }

    public record ApplySummary(
            UUID repositoryId,
            UUID snapshotId,
            ApplyResult result,
            int rejectedCount,
            int newCount,
            int corroboratedCount,
            int reviewRequiredCount) {}

    public record FinalSummary(Source source, boolean partial) {}
}
