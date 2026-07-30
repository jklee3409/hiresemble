package com.hiresemble.coverletter.application.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterEvidenceUsageType;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CoverLetterModels {

    private CoverLetterModels() {}

    public record JobReference(
            UUID id, String companyName, String positionName, String title) {}

    public record EvidenceReference(
            UUID id,
            String title,
            String evidenceCategory,
            EvidenceVerificationStatus verificationStatus,
            EvidenceSourceType sourceType,
            boolean sourceDeleted) {}

    public record VerifiedEvidence(
            UUID id,
            EvidenceSourceType sourceType,
            UUID sourceEntityId,
            UUID documentId,
            String evidenceCategory,
            String title,
            String content,
            long version) {}

    public record EvidenceUse(
            UUID evidenceId, String claimText, CoverLetterEvidenceUsageType usageType) {}

    public record VerificationIssue(
            VerificationIssueCode code,
            IssueSeverity severity,
            String message,
            String relatedText,
            List<UUID> evidenceIds) {
        public VerificationIssue {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record VerifiedClaim(String claim, boolean supported, List<UUID> evidenceIds) {
        public VerifiedClaim {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record VerificationResult(
            VerificationStatus status,
            List<VerificationIssue> issues,
            List<String> suggestions,
            List<VerifiedClaim> verifiedClaims) {
        public VerificationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
            verifiedClaims = verifiedClaims == null ? List.of() : List.copyOf(verifiedClaims);
        }
    }

    public record AnswerVersion(
            UUID id,
            UUID userId,
            UUID questionId,
            UUID parentVersionId,
            UUID restoredFromVersionId,
            int versionNo,
            TipTapDocumentDto contentJson,
            String plainText,
            int characterCount,
            CoverLetterVersionSource sourceType,
            boolean current,
            AnswerCreatedBy createdBy,
            Instant createdAt) {}

    public record Verification(
            UUID id,
            UUID userId,
            UUID answerVersionId,
            VerificationStatus status,
            List<VerificationIssue> issues,
            List<String> suggestions,
            List<VerifiedClaim> verifiedClaims,
            List<EvidenceReference> evidenceReferences,
            UUID agentRunId,
            Instant createdAt) {
        public Verification {
            issues = List.copyOf(issues);
            suggestions = List.copyOf(suggestions);
            verifiedClaims = List.copyOf(verifiedClaims);
            evidenceReferences = List.copyOf(evidenceReferences);
        }
    }

    public record Question(
            UUID id,
            UUID userId,
            UUID coverLetterId,
            int questionOrder,
            String questionText,
            Integer maxLength,
            String memo,
            AnswerVersion currentAnswer,
            Verification latestVerification,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {}

    public record Summary(
            UUID id,
            UUID userId,
            UUID jobId,
            JobReference job,
            String title,
            CoverLetterStatus status,
            int questionCount,
            int answeredQuestionCount,
            VerificationStatus latestVerificationStatus,
            int warningCount,
            boolean canEdit,
            boolean canArchive,
            boolean canUnarchive,
            boolean canFinalize,
            long version,
            Instant finalizedAt,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record Detail(Summary summary, List<Question> questions) {
        public Detail {
            questions = List.copyOf(questions);
        }
    }

    public record Page(
            List<Summary> items, int page, int size, long totalElements, int totalPages) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record RunAccepted(
            UUID agentRunId,
            AgentRunStatus status,
            String resourceType,
            UUID resourceId) {}

    public record AppliedAnswer(
            AnswerVersion answerVersion,
            Verification generationVerification,
            long coverLetterVersion) {}

    public record RequirementContext(
            String category, String text, boolean required, String sourceLocation) {}

    public record JobContext(
            UUID jobId,
            long jobVersion,
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            UUID analysisId,
            int analysisVersion,
            boolean analysisOutdated,
            List<RequirementContext> requirements) {
        public JobContext {
            requirements = List.copyOf(requirements);
        }
    }

    public record GenerationQuestion(
            UUID questionId,
            int questionOrder,
            String questionText,
            Integer maxLength,
            UUID currentAnswerVersionId,
            String currentPlainText) {}

    public record GenerationSnapshot(
            UUID userId,
            UUID coverLetterId,
            long coverLetterVersion,
            String title,
            JobContext job,
            List<GenerationQuestion> questions,
            List<VerifiedEvidence> verifiedEvidence,
            List<UUID> preferredEvidenceIds,
            boolean avoidExperienceDuplication,
            AiQualityMode qualityMode,
            String snapshotHash) {
        public GenerationSnapshot {
            questions = List.copyOf(questions);
            verifiedEvidence = List.copyOf(verifiedEvidence);
            preferredEvidenceIds = List.copyOf(preferredEvidenceIds);
        }
    }

    public record HistoricalEvidence(
            UUID id,
            String title,
            String evidenceCategory,
            EvidenceSourceType sourceType,
            EvidenceVerificationStatus currentStatus,
            boolean sourceDeleted,
            String claimText,
            CoverLetterEvidenceUsageType usageType) {}

    public record VerificationSnapshot(
            UUID userId,
            UUID coverLetterId,
            long coverLetterVersion,
            Question question,
            AnswerVersion answerVersion,
            JobContext job,
            List<HistoricalEvidence> historicalEvidence,
            List<VerifiedEvidence> currentVerifiedEvidence,
            AiQualityMode qualityMode,
            String snapshotHash) {
        public VerificationSnapshot {
            historicalEvidence = List.copyOf(historicalEvidence);
            currentVerifiedEvidence = List.copyOf(currentVerifiedEvidence);
        }
    }

    public record CandidateChunk(
            UUID chunkId,
            UUID documentId,
            String maskedContent,
            double distance) {}

    public record PersistGeneratedAnswer(
            UUID coverLetterId,
            UUID questionId,
            long expectedCoverLetterVersion,
            UUID expectedCurrentVersionId,
            String expectedSnapshotHash,
            TipTapDocumentDto contentJson,
            List<EvidenceUse> evidenceUses,
            VerificationResult factCheck) {
        public PersistGeneratedAnswer {
            evidenceUses = evidenceUses == null ? List.of() : List.copyOf(evidenceUses);
        }
    }

    public record PersistVerification(
            UUID verificationId,
            UUID answerVersionId,
            String expectedSnapshotHash,
            VerificationResult result) {}
}
