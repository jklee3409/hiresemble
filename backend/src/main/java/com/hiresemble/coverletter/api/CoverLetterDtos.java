package com.hiresemble.coverletter.api;

import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.job.api.JobAnalysisDtos.EvidenceRefDto;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CoverLetterDtos {

    private CoverLetterDtos() {}

    @Schema(name = "JobRefDto")
    public record JobRefDto(
            UUID id,
            @Schema(nullable = true, maxLength = 200) String companyName,
            @Schema(nullable = true, maxLength = 300) String positionName,
            @Schema(nullable = true, maxLength = 300) String title) {}

    @Schema(name = "VerificationIssueDto")
    public record VerificationIssueDto(
            VerificationIssueCode code,
            IssueSeverity severity,
            @Schema(minLength = 1, maxLength = 1000) String message,
            @Schema(nullable = true, maxLength = 1000) String relatedText,
            List<EvidenceRefDto> evidenceRefs) {
        public VerificationIssueDto {
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }

    @Schema(name = "VerifiedClaimDto")
    public record VerifiedClaimDto(
            @Schema(minLength = 1, maxLength = 2000) String claim,
            boolean supported,
            List<EvidenceRefDto> evidenceRefs) {
        public VerifiedClaimDto {
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }

    @Schema(name = "CoverLetterAnswerVersionDto")
    public record CoverLetterAnswerVersionDto(
            UUID id,
            UUID questionId,
            @Schema(nullable = true) UUID parentVersionId,
            @Schema(nullable = true) UUID restoredFromVersionId,
            @Schema(minimum = "1") int versionNo,
            TipTapDocumentDto contentJson,
            @Schema(maxLength = 20000) String plainText,
            @Schema(minimum = "0", maximum = "20000") int characterCount,
            CoverLetterVersionSource sourceType,
            boolean isCurrent,
            AnswerCreatedBy createdBy,
            Instant createdAt) {}

    @Schema(name = "VerificationDto")
    public record VerificationDto(
            UUID id,
            UUID answerVersionId,
            VerificationStatus status,
            List<VerificationIssueDto> issues,
            @ArraySchema(
                    maxItems = 20,
                    schema = @Schema(minLength = 1, maxLength = 1000))
            List<String> suggestions,
            List<VerifiedClaimDto> verifiedClaims,
            List<EvidenceRefDto> evidenceRefs,
            @Schema(nullable = true) UUID agentRunId,
            Instant createdAt) {
        public VerificationDto {
            issues = List.copyOf(issues);
            suggestions = List.copyOf(suggestions);
            verifiedClaims = List.copyOf(verifiedClaims);
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }

    @Schema(name = "CoverLetterQuestionDto")
    public record CoverLetterQuestionDto(
            UUID id,
            @Schema(minimum = "1", maximum = "20") int questionOrder,
            @Schema(minLength = 1, maxLength = 2000) String questionText,
            @Schema(nullable = true, minimum = "1", maximum = "10000") Integer maxLength,
            @Schema(nullable = true, maxLength = 2000) String memo,
            @Schema(nullable = true) CoverLetterAnswerVersionDto currentAnswer,
            @Schema(nullable = true) VerificationDto latestVerification,
            long version,
            @Schema(nullable = true) Instant deletedAt) {}

    @Schema(name = "CoverLetterSummaryDto")
    public record CoverLetterSummaryDto(
            UUID id,
            JobRefDto job,
            @Schema(minLength = 1, maxLength = 300) String title,
            CoverLetterStatus status,
            @Schema(minimum = "0", maximum = "20") int questionCount,
            @Schema(minimum = "0", maximum = "20") int answeredQuestionCount,
            @Schema(nullable = true) VerificationStatus latestVerificationStatus,
            @Schema(minimum = "0") int warningCount,
            boolean canEdit,
            boolean canArchive,
            boolean canUnarchive,
            boolean canFinalize,
            long version,
            @Schema(nullable = true) Instant finalizedAt,
            @Schema(nullable = true) Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CoverLetterDetailDto")
    public record CoverLetterDetailDto(
            UUID id,
            JobRefDto job,
            @Schema(minLength = 1, maxLength = 300) String title,
            CoverLetterStatus status,
            @Schema(minimum = "0", maximum = "20") int questionCount,
            @Schema(minimum = "0", maximum = "20") int answeredQuestionCount,
            @Schema(nullable = true) VerificationStatus latestVerificationStatus,
            @Schema(minimum = "0") int warningCount,
            boolean canEdit,
            boolean canArchive,
            boolean canUnarchive,
            boolean canFinalize,
            long version,
            @Schema(nullable = true) Instant finalizedAt,
            @Schema(nullable = true) Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            List<CoverLetterQuestionDto> questions) {
        public CoverLetterDetailDto {
            questions = List.copyOf(questions);
        }
    }
}
