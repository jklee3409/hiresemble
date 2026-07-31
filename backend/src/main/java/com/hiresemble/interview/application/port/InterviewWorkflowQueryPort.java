package com.hiresemble.interview.application.port;

import com.hiresemble.interview.application.model.InterviewModels.FeedbackContext;
import com.hiresemble.interview.application.model.InterviewModels.PreparationContext;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import java.util.List;
import java.util.UUID;

public interface InterviewWorkflowQueryPort {

    PreparationContext loadPreparationContext(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            String expectedContextHash);

    FeedbackContext loadFeedbackContext(
            UUID userId, UUID answerVersionId, String expectedContextHash);

    ResearchRunRow researchRun(UUID userId, UUID researchRunId);

    List<ResearchSourceRow> researchSources(UUID userId, UUID researchRunId);
}
