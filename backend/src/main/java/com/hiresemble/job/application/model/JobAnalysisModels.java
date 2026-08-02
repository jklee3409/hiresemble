package com.hiresemble.job.application.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.CriterionSupportType;
import com.hiresemble.job.domain.FitCriterionCategory;
import com.hiresemble.job.domain.JobAnalysisEvidenceUsageType;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.job.domain.StructuredProfileFactType;
import com.hiresemble.job.domain.OutdatedReason;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class JobAnalysisModels {

    private JobAnalysisModels() {}

    public record ProfileContext(
            UUID profileId,
            long version,
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            LocalDate expectedGraduationDate,
            List<StructuredProfileFact> structuredFacts) {
        public ProfileContext {
            desiredRoles = List.copyOf(desiredRoles);
            desiredIndustries = List.copyOf(desiredIndustries);
            desiredLocations = List.copyOf(desiredLocations);
            structuredFacts = structuredFacts == null ? List.of() : List.copyOf(structuredFacts);
        }

        public ProfileContext(
                UUID profileId,
                long version,
                String introduction,
                List<String> desiredRoles,
                List<String> desiredIndustries,
                List<String> desiredLocations,
                LocalDate expectedGraduationDate) {
            this(profileId, version, introduction, desiredRoles, desiredIndustries,
                    desiredLocations, expectedGraduationDate, List.of());
        }
    }

    public record StructuredProfileFact(
            String reference,
            StructuredProfileFactType factType,
            UUID sourceEntityId,
            long sourceEntityVersion,
            String value,
            boolean selfReported,
            String factHash) {}

    public record StructuredFactUsage(
            String reference,
            JobAnalysisEvidenceUsageType usageType) {}

    public record VerifiedEvidence(
            UUID id,
            EvidenceSourceType sourceType,
            UUID sourceEntityId,
            UUID documentId,
            String evidenceCategory,
            String title,
            EvidenceVerificationStatus verificationStatus,
            boolean sourceDeleted,
            long version,
            String evidenceHash) {}

    public record RetrievedVerifiedEvidence(
            VerifiedEvidence evidence,
            String content,
            UUID matchedChunkId,
            UUID matchedDocumentId,
            String maskedContext,
            Double distance) {}

    public record JobAnalysisSnapshot(
            UUID userId,
            UUID jobId,
            long jobVersion,
            String companyName,
            String title,
            String positionName,
            String roleCategory,
            String employmentType,
            String location,
            String descriptionText,
            Instant deadlineAt,
            String jobContentHash,
            ProfileContext profile,
            List<VerifiedEvidence> verifiedEvidence,
            String profileSnapshotHash,
            String evidenceSnapshotHash,
            String contextHash,
            String rubricVersion,
            String workflowVersion,
            AiQualityMode qualityMode,
            long embeddingPolicyVersion,
            int embeddingGeneration,
            String retrievalPolicyVersion,
            UUID reusableAnalysisId) {
        public JobAnalysisSnapshot {
            verifiedEvidence = List.copyOf(verifiedEvidence);
        }
    }

    public record RequirementItem(
            FitCriterionCategory category,
            String text,
            boolean required,
            String sourceLocation) {}

    public record CriterionDraft(
            FitCriterionCategory category,
            String criterion,
            MatchLevel matchLevel,
            String explanation,
            String sourceLocation,
            List<UUID> evidenceIds,
            List<String> structuredFactRefs,
            CriterionSupportType supportType,
            LocalDate requiredByDate) {
        public CriterionDraft {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
            structuredFactRefs = structuredFactRefs == null ? List.of() : List.copyOf(structuredFactRefs);
        }

        public CriterionDraft(
                FitCriterionCategory category,
                String criterion,
                MatchLevel matchLevel,
                String explanation,
                String sourceLocation,
                List<UUID> evidenceIds) {
            this(category, criterion, matchLevel, explanation, sourceLocation, evidenceIds, List.of());
        }

        public CriterionDraft(
                FitCriterionCategory category,
                String criterion,
                MatchLevel matchLevel,
                String explanation,
                String sourceLocation,
                List<UUID> evidenceIds,
                List<String> structuredFactRefs) {
            this(category, criterion, matchLevel, explanation, sourceLocation, evidenceIds,
                    structuredFactRefs, CriterionSupportType.GENERAL, null);
        }

        public CriterionDraft(
                FitCriterionCategory category,
                String criterion,
                MatchLevel matchLevel,
                String explanation,
                String sourceLocation,
                List<UUID> evidenceIds,
                List<String> structuredFactRefs,
                CriterionSupportType supportType) {
            this(category, criterion, matchLevel, explanation, sourceLocation, evidenceIds,
                    structuredFactRefs, supportType, null);
        }
    }

    public record EvidenceUsage(
            UUID evidenceId,
            JobAnalysisEvidenceUsageType usageType) {}

    public record PersistJobAnalysis(
            UUID jobId,
            long expectedJobVersion,
            String expectedJobContentHash,
            String expectedProfileSnapshotHash,
            String expectedEvidenceSnapshotHash,
            String expectedContextHash,
            AiQualityMode qualityMode,
            Eligibility eligibility,
            List<CriterionDraft> criteria,
            List<RequirementItem> responsibilities,
            List<RequirementItem> requiredQualifications,
            List<RequirementItem> preferredQualifications,
            List<String> strengths,
            List<String> gaps,
            List<EvidenceUsage> additionalEvidenceUsages,
            List<StructuredFactUsage> additionalStructuredFactUsages,
            String analysisSummary) {
        public PersistJobAnalysis {
            criteria = List.copyOf(criteria);
            responsibilities = List.copyOf(responsibilities);
            requiredQualifications = List.copyOf(requiredQualifications);
            preferredQualifications = List.copyOf(preferredQualifications);
            strengths = List.copyOf(strengths);
            gaps = List.copyOf(gaps);
            additionalEvidenceUsages =
                    additionalEvidenceUsages == null ? List.of() : List.copyOf(additionalEvidenceUsages);
            additionalStructuredFactUsages = additionalStructuredFactUsages == null
                    ? List.of()
                    : List.copyOf(additionalStructuredFactUsages);
        }

        public PersistJobAnalysis(
                UUID jobId, long expectedJobVersion, String expectedJobContentHash,
                String expectedProfileSnapshotHash, String expectedEvidenceSnapshotHash,
                String expectedContextHash, AiQualityMode qualityMode, Eligibility eligibility,
                List<CriterionDraft> criteria, List<RequirementItem> responsibilities,
                List<RequirementItem> requiredQualifications,
                List<RequirementItem> preferredQualifications, List<String> strengths,
                List<String> gaps, List<EvidenceUsage> additionalEvidenceUsages,
                String analysisSummary) {
            this(jobId, expectedJobVersion, expectedJobContentHash, expectedProfileSnapshotHash,
                    expectedEvidenceSnapshotHash, expectedContextHash, qualityMode, eligibility,
                    criteria, responsibilities, requiredQualifications, preferredQualifications,
                    strengths, gaps, additionalEvidenceUsages, List.of(), analysisSummary);
        }
    }

    public record EvidenceReference(
            UUID id,
            String title,
            String evidenceCategory,
            EvidenceVerificationStatus verificationStatus,
            EvidenceSourceType sourceType,
            boolean sourceDeleted) {}

    public record ScoreCriterion(
            UUID id,
            FitCriterionCategory category,
            String criterion,
            BigDecimal weight,
            MatchLevel matchLevel,
            BigDecimal score,
            List<EvidenceReference> evidenceReferences,
            String explanation,
            String sourceLocation) {
        public ScoreCriterion {
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }

    public record JobAnalysisSummary(
            UUID id,
            UUID userId,
            UUID jobId,
            int analysisVersion,
            Eligibility eligibility,
            BigDecimal fitScore,
            boolean analysisOutdated,
            List<OutdatedReason> outdatedReasons,
            Instant createdAt,
            UUID agentRunId,
            String jobContentHash,
            String profileSnapshotHash,
            String evidenceSnapshotHash,
            String contextHash,
            AiQualityMode qualityMode) {
        public JobAnalysisSummary {
            outdatedReasons = List.copyOf(outdatedReasons);
        }
    }

    public record JobAnalysisDetail(
            JobAnalysisSummary summary,
            List<ScoreCriterion> scoreBreakdown,
            List<RequirementItem> requiredQualifications,
            List<RequirementItem> preferredQualifications,
            List<RequirementItem> responsibilities,
            List<String> strengths,
            List<String> gaps,
            List<EvidenceReference> matchedEvidenceReferences,
            String analysisSummary) {
        public JobAnalysisDetail {
            scoreBreakdown = List.copyOf(scoreBreakdown);
            requiredQualifications = List.copyOf(requiredQualifications);
            preferredQualifications = List.copyOf(preferredQualifications);
            responsibilities = List.copyOf(responsibilities);
            strengths = List.copyOf(strengths);
            gaps = List.copyOf(gaps);
            matchedEvidenceReferences = List.copyOf(matchedEvidenceReferences);
        }
    }

    public record JobAnalysisPage(
            List<JobAnalysisSummary> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public JobAnalysisPage {
            items = List.copyOf(items);
        }
    }
}
