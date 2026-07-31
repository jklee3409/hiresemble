package com.hiresemble.interview.application.port;

import com.hiresemble.interview.application.model.InterviewModels.FeedbackResult;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackRow;
import com.hiresemble.interview.application.model.InterviewModels.GeneratedQuestion;
import com.hiresemble.research.application.model.ResearchModels.ResearchResult;
import java.util.List;
import java.util.UUID;

public interface InterviewWorkflowCommandPort {

    void markPreparationRunning(UUID userId, UUID researchRunId);

    void persistPreparation(
            UUID userId,
            UUID agentRunId,
            UUID researchRunId,
            UUID questionSetId,
            int expectedQuestionCount,
            ResearchResult research,
            List<GeneratedQuestion> questions);

    FeedbackRow persistFeedback(
            UUID userId,
            UUID agentRunId,
            UUID answerVersionId,
            FeedbackResult feedback);

    void failPreparation(
            UUID userId,
            UUID researchRunId,
            String safeErrorCode,
            boolean retryable);
}
