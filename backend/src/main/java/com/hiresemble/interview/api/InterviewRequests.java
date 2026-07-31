package com.hiresemble.interview.api;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.research.domain.ResearchQuality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class InterviewRequests {

    private InterviewRequests() {}

    public record CreateInterviewPreparationRequest(
            @NotNull UUID coverLetterId,
            @NotNull ResearchQuality researchQuality,
            @NotNull AiQualityMode qualityMode,
            @NotEmpty @Size(max = 7) List<@NotNull InterviewQuestionType> questionTypes,
            @Min(1) @Max(20) int questionCount) {
        public CreateInterviewPreparationRequest {
            questionTypes = questionTypes == null ? null : List.copyOf(questionTypes);
        }
    }

    public record ResearchRetryRequest(
            ResearchQuality researchQuality,
            AiQualityMode qualityMode) {}

    public record CreateInterviewAnswerVersionRequest(
            @NotBlank @Size(max = 20000) String content,
            UUID parentVersionId) {}

    public record InterviewAnswerFeedbackRequest(@NotNull AiQualityMode qualityMode) {}
}
