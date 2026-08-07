package com.hiresemble.profile.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExperienceRecords {

    private ExperienceRecords() {}

    public record ExperienceItemRecord(
            UUID id,
            UUID userId,
            UUID canonicalEvidenceId,
            String evidenceCategory,
            String title,
            String content,
            EvidenceVerificationStatus verificationStatus,
            ExperienceMatchKind matchKind,
            UUID matchedExperienceItemId,
            BigDecimal matchSimilarity,
            String matchPolicyVersion,
            String canonicalFingerprint,
            int sourceCount,
            int documentSourceCount,
            int githubRepositorySourceCount,
            String primaryDocumentName,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record ExperienceSourceRecord(
            UUID evidenceId,
            EvidenceSourceType sourceType,
            UUID documentId,
            EvidenceVerificationStatus verificationStatus,
            ExperienceLinkKind relationKind,
            BigDecimal similarity,
            UUID githubSourceId,
            UUID githubRepositoryId,
            String repositoryName,
            String repositoryUrl,
            String commitShaShort,
            Instant capturedAt,
            String sourceExcerpt,
            Instant sourceDeletedAt,
            Instant createdAt) {}

    public record ExperienceItemDetail(
            ExperienceItemRecord item, List<ExperienceSourceRecord> sources) {
        public ExperienceItemDetail {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record SimilarExperienceRecord(
            ExperienceItemRecord item, double distance) {}

    public record EvidenceExperienceLink(
            UUID sourceEvidenceId,
            UUID experienceItemId,
            UUID canonicalEvidenceId,
            ExperienceLinkKind relationKind,
            ExperienceMatchKind matchKind) {}
}
