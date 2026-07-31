package com.hiresemble.interview.application.model;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.interview.domain.InterviewAnswerVersionSource;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.SourceCoverage;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InterviewModels {

    private InterviewModels() {}

    public record AcceptedPreparation(
            UUID questionSetId, UUID researchRunId, UUID agentRunId) {}

    public record AcceptedFeedback(
            UUID agentRunId,
            AgentRunStatus status,
            String resourceType,
            UUID resourceId) {}

    public record InterviewJobProjection(
            int preparationCount, UUID latestQuestionSetId) {}

    public record PreparedPreparation(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            UUID researchRunId,
            UUID questionSetId,
            UUID agentRunId,
            ResearchQuality researchQuality,
            AiQualityMode qualityMode,
            List<InterviewQuestionType> questionTypes,
            int questionCount,
            PreparationContext context,
            UUID retryOfResearchRunId) {
        public PreparedPreparation {
            questionTypes = List.copyOf(questionTypes);
        }
    }

    public record PreparationContext(
            UUID userId,
            UUID jobId,
            long jobVersion,
            String companyName,
            String positionName,
            String jobTitle,
            String roleCategory,
            String jobDescription,
            UUID jobAnalysisId,
            String analysisSummary,
            UUID coverLetterId,
            String coverLetterTitle,
            CoverLetterStatus coverLetterStatus,
            List<CoverAnswerContext> coverAnswers,
            StructuredProfileContext profile,
            List<EvidenceContext> evidence) {
        public PreparationContext {
            coverAnswers = List.copyOf(coverAnswers);
            evidence = List.copyOf(evidence);
        }
    }

    public record CoverAnswerContext(
            UUID questionId, String questionText, UUID answerVersionId, String answerText) {}

    public record StructuredProfileContext(
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            FinalEducationContext finalEducation) {
        public StructuredProfileContext {
            desiredRoles = List.copyOf(desiredRoles);
            desiredIndustries = List.copyOf(desiredIndustries);
            desiredLocations = List.copyOf(desiredLocations);
        }
    }

    public record FinalEducationContext(
            UUID id,
            String schoolName,
            String major,
            String degree,
            EducationLevel educationLevel,
            EducationStatus educationStatus,
            LocalDate graduationDate) {}

    public record EvidenceContext(
            UUID id,
            EvidenceSourceType sourceType,
            String category,
            String title,
            String content,
            EvidenceVerificationStatus verificationStatus) {}

    public record GeneratedQuestion(
            UUID id,
            int questionOrder,
            InterviewQuestionType questionType,
            String questionText,
            String intent,
            List<String> evaluationPoints,
            String answerGuide,
            List<String> followUpQuestions,
            List<UUID> evidenceIds,
            List<UUID> sourceIds) {
        public GeneratedQuestion {
            evaluationPoints = List.copyOf(evaluationPoints);
            followUpQuestions = List.copyOf(followUpQuestions);
            evidenceIds = List.copyOf(evidenceIds);
            sourceIds = List.copyOf(sourceIds);
        }

        public boolean sourceBased() {
            return !sourceIds.isEmpty();
        }
    }

    public record QuestionSetRow(
            UUID id,
            UUID jobId,
            String companyName,
            String positionName,
            String jobTitle,
            UUID coverLetterId,
            String coverLetterTitle,
            CoverLetterStatus coverLetterStatus,
            String title,
            int questionCount,
            UUID researchRunId,
            SourceCoverage sourceCoverage,
            UUID agentRunId,
            AgentRunStatus agentRunStatus,
            String currentStep,
            int progressPercent,
            Instant createdAt,
            Instant updatedAt) {}

    public record QuestionRow(
            UUID id,
            UUID questionSetId,
            int questionOrder,
            InterviewQuestionType questionType,
            String questionText,
            String intent,
            List<String> evaluationPoints,
            String answerGuide,
            List<String> followUpQuestions,
            boolean sourceBased,
            Instant createdAt) {
        public QuestionRow {
            evaluationPoints = List.copyOf(evaluationPoints);
            followUpQuestions = List.copyOf(followUpQuestions);
        }
    }

    public record QuestionView(
            QuestionRow question,
            List<EvidenceRefRow> evidenceRefs,
            List<ResearchSourceRow> sourceRefs,
            AnswerVersionRow currentAnswer,
            FeedbackRow latestFeedback) {
        public QuestionView {
            evidenceRefs = List.copyOf(evidenceRefs);
            sourceRefs = List.copyOf(sourceRefs);
        }
    }

    public record QuestionSetView(
            QuestionSetRow summary,
            ResearchRunRow research,
            List<QuestionView> questions) {
        public QuestionSetView {
            questions = List.copyOf(questions);
        }
    }

    public record AnswerVersionRow(
            UUID id,
            UUID questionId,
            UUID parentVersionId,
            int versionNo,
            String content,
            InterviewAnswerVersionSource sourceType,
            boolean current,
            Instant createdAt) {}

    public record FeedbackScore(
            String criterion, BigDecimal score, String explanation) {}

    public record FeedbackRow(
            UUID id,
            UUID answerVersionId,
            List<FeedbackScore> scores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> suggestions,
            String revisedExample,
            UUID agentRunId,
            Instant createdAt) {
        public FeedbackRow {
            scores = List.copyOf(scores);
            strengths = List.copyOf(strengths);
            weaknesses = List.copyOf(weaknesses);
            suggestions = List.copyOf(suggestions);
        }
    }

    public record EvidenceRefRow(
            UUID id,
            String title,
            String category,
            EvidenceVerificationStatus verificationStatus,
            EvidenceSourceType sourceType,
            boolean sourceDeleted) {}

    public record PageSlice<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }

    public record FeedbackContext(
            UUID userId,
            UUID answerVersionId,
            UUID questionId,
            String questionText,
            String intent,
            List<String> evaluationPoints,
            String answerGuide,
            String answerContent,
            UUID jobId,
            String companyName,
            String positionName,
            UUID coverLetterId) {
        public FeedbackContext {
            evaluationPoints = List.copyOf(evaluationPoints);
        }
    }

    public record FeedbackResult(
            List<FeedbackScore> scores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> suggestions,
            String revisedExample) {
        public FeedbackResult {
            scores = List.copyOf(scores);
            strengths = List.copyOf(strengths);
            weaknesses = List.copyOf(weaknesses);
            suggestions = List.copyOf(suggestions);
        }
    }
}
