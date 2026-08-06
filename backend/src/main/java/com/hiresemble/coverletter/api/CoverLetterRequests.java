package com.hiresemble.coverletter.api;

import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CoverLetterRequests {

    private CoverLetterRequests() {}

    public record CreateCoverLetterRequest(
            @NotBlank @Size(max = 300) String title) {}

    public record UpdateCoverLetterRequest(
            @NotBlank @Size(max = 300) String title,
            @PositiveOrZero long version) {}

    public record CreateQuestionRequest(
            @Min(1) @Max(20) int questionOrder,
            @NotBlank @Size(max = 2000) String questionText,
            @Min(1) @Max(10000) Integer maxLength,
            @Size(max = 2000) String memo,
            @PositiveOrZero long coverLetterVersion) {}

    public record UpdateQuestionRequest(
            @Min(1) @Max(20) int questionOrder,
            @NotBlank @Size(max = 2000) String questionText,
            @Min(1) @Max(10000) Integer maxLength,
            @Size(max = 2000) String memo,
            @PositiveOrZero long version) {}

    public record ReorderQuestionsRequest(
            @NotEmpty @Size(max = 20) List<@NotNull UUID> questionIds,
            @PositiveOrZero long version) {
        public ReorderQuestionsRequest {
            questionIds = questionIds == null ? null : List.copyOf(questionIds);
        }
    }

    public record GenerateCoverLetterRequest(
            @NotEmpty @Size(max = 20) List<@NotNull UUID> questionIds,
            @Size(max = 50) List<@NotNull UUID> preferredEvidenceIds,
            @NotBlank @Size(max = 64) String model,
            boolean avoidExperienceDuplication,
            @PositiveOrZero long coverLetterVersion) {
        public GenerateCoverLetterRequest {
            questionIds = questionIds == null ? null : List.copyOf(questionIds);
            preferredEvidenceIds =
                    preferredEvidenceIds == null ? List.of() : List.copyOf(preferredEvidenceIds);
        }
    }

    public record SaveAnswerVersionRequest(
            @NotNull @Valid TipTapDocumentDto contentJson,
            UUID parentVersionId) {}

    public record RestoreAnswerVersionRequest(UUID expectedCurrentVersionId) {}

    public record VerifyAnswerVersionRequest(
            @NotBlank @Size(max = 64) String model) {}

    public record VersionCommandRequest(@PositiveOrZero long version) {}

    public record FinalizeCoverLetterRequest(
            @PositiveOrZero long version,
            @Size(max = 20) List<@NotNull UUID> acknowledgedWarningVerificationIds) {
        public FinalizeCoverLetterRequest {
            acknowledgedWarningVerificationIds = acknowledgedWarningVerificationIds == null
                    ? List.of()
                    : List.copyOf(acknowledgedWarningVerificationIds);
        }
    }
}
