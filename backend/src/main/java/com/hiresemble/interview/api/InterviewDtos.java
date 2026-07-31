package com.hiresemble.interview.api;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.coverletter.api.CoverLetterDtos.JobRefDto;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.interview.domain.InterviewAnswerVersionSource;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.job.api.JobAnalysisDtos.EvidenceRefDto;
import com.hiresemble.research.api.ResearchDtos.ResearchRunDto;
import com.hiresemble.research.api.ResearchDtos.ResearchSourceRefDto;
import com.hiresemble.research.domain.SourceCoverage;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InterviewDtos {

    private InterviewDtos() {}

    @Schema(name = "CoverLetterRefDto")
    public record CoverLetterRefDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 300) String title,
            CoverLetterStatus status) {}

    @Schema(name = "AgentRunRefDto")
    public record AgentRunRefDto(
            UUID id,
            AgentRunStatus status,
            @Schema(nullable = true, maxLength = 100) String currentStep,
            @Schema(minimum = "0", maximum = "100") int progressPercent) {}

    @Schema(name = "FeedbackScoreDto")
    public record FeedbackScoreDto(
            @Schema(minLength = 1, maxLength = 100) String criterion,
            @Schema(minimum = "0", maximum = "100") BigDecimal score,
            @Schema(nullable = true, maxLength = 1000) String explanation) {}

    @Schema(name = "InterviewAnswerVersionDto")
    public record InterviewAnswerVersionDto(
            UUID id,
            UUID questionId,
            @Schema(nullable = true) UUID parentVersionId,
            @Schema(minimum = "1") int versionNo,
            @Schema(minLength = 1, maxLength = 20000) String content,
            InterviewAnswerVersionSource sourceType,
            boolean isCurrent,
            Instant createdAt) {}

    @Schema(name = "InterviewFeedbackDto")
    public record InterviewFeedbackDto(
            UUID id,
            UUID answerVersionId,
            @ArraySchema(minItems = 1, maxItems = 20)
            List<FeedbackScoreDto> scores,
            @ArraySchema(
                    maxItems = 20,
                    schema = @Schema(minLength = 1, maxLength = 1000))
            List<String> strengths,
            @ArraySchema(
                    maxItems = 20,
                    schema = @Schema(minLength = 1, maxLength = 1000))
            List<String> weaknesses,
            @ArraySchema(
                    maxItems = 20,
                    schema = @Schema(minLength = 1, maxLength = 1000))
            List<String> suggestions,
            @Schema(nullable = true, maxLength = 10000) String revisedExample,
            UUID agentRunId,
            Instant createdAt) {
        public InterviewFeedbackDto {
            scores = List.copyOf(scores);
            strengths = List.copyOf(strengths);
            weaknesses = List.copyOf(weaknesses);
            suggestions = List.copyOf(suggestions);
        }
    }

    @Schema(name = "InterviewQuestionDto")
    public record InterviewQuestionDto(
            UUID id,
            @Schema(minimum = "1", maximum = "20") int questionOrder,
            InterviewQuestionType questionType,
            @Schema(minLength = 1, maxLength = 2000) String questionText,
            @Schema(nullable = true, maxLength = 2000) String intent,
            @ArraySchema(
                    maxItems = 20,
                    schema = @Schema(minLength = 1, maxLength = 500))
            List<String> evaluationPoints,
            @Schema(nullable = true, maxLength = 10000) String answerGuide,
            @ArraySchema(
                    maxItems = 10,
                    schema = @Schema(minLength = 1, maxLength = 2000))
            List<String> followUpQuestions,
            @ArraySchema(maxItems = 20)
            List<EvidenceRefDto> relatedEvidenceRefs,
            @ArraySchema(maxItems = 50)
            List<ResearchSourceRefDto> sourceRefs,
            boolean sourceBased,
            @Schema(nullable = true) InterviewAnswerVersionDto currentAnswer,
            @Schema(nullable = true) InterviewFeedbackDto latestFeedback) {
        public InterviewQuestionDto {
            evaluationPoints = List.copyOf(evaluationPoints);
            followUpQuestions = List.copyOf(followUpQuestions);
            relatedEvidenceRefs = List.copyOf(relatedEvidenceRefs);
            sourceRefs = List.copyOf(sourceRefs);
        }
    }

    @Schema(name = "QuestionSetSummaryDto")
    public record QuestionSetSummaryDto(
            UUID id,
            JobRefDto job,
            CoverLetterRefDto coverLetter,
            @Schema(minLength = 1, maxLength = 300) String title,
            @Schema(minimum = "0", maximum = "20") int questionCount,
            UUID researchRunId,
            @Schema(nullable = true) SourceCoverage sourceCoverage,
            AgentRunRefDto agentRun,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "QuestionSetDetailDto")
    public record QuestionSetDetailDto(
            UUID id,
            JobRefDto job,
            CoverLetterRefDto coverLetter,
            @Schema(minLength = 1, maxLength = 300) String title,
            @Schema(minimum = "0", maximum = "20") int questionCount,
            UUID researchRunId,
            @Schema(nullable = true) SourceCoverage sourceCoverage,
            AgentRunRefDto agentRun,
            Instant createdAt,
            Instant updatedAt,
            ResearchRunDto research,
            @ArraySchema(maxItems = 20)
            List<InterviewQuestionDto> questions) {
        public QuestionSetDetailDto {
            questions = List.copyOf(questions);
        }
    }

    @Schema(name = "InterviewPreparationAcceptedDto")
    public record InterviewPreparationAcceptedDto(
            UUID questionSetId,
            UUID researchRunId,
            UUID agentRunId,
            @Schema(allowableValues = "QUEUED") String status) {}
}
