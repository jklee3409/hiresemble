package com.hiresemble.job.application.port;

import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail;
import com.hiresemble.job.application.model.JobAnalysisModels.PersistJobAnalysis;
import java.util.UUID;

public interface JobAnalysisCommandPort {

    JobAnalysisDetail persist(UUID userId, UUID agentRunId, PersistJobAnalysis command);

    JobAnalysisDetail attachReusable(
            UUID userId,
            UUID agentRunId,
            UUID jobId,
            UUID analysisId,
            String expectedContextHash);
}
