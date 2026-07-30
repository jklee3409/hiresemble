package com.hiresemble.job.application.port;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSnapshot;
import com.hiresemble.job.application.model.JobAnalysisModels.RetrievedVerifiedEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAnalysisQueryPort {

    JobAnalysisSnapshot loadSnapshot(
            UUID userId,
            UUID jobId,
            long expectedJobVersion,
            AiQualityMode qualityMode,
            String expectedContextHash);

    Optional<JobAnalysisDetail> findReusable(
            UUID userId,
            UUID jobId,
            String contextHash,
            AiQualityMode qualityMode);

    List<RetrievedVerifiedEvidence> searchVerifiedEvidence(
            UUID userId,
            UUID jobId,
            long expectedJobVersion,
            AiQualityMode qualityMode,
            String expectedContextHash,
            String queryText,
            List<Double> queryVector,
            long embeddingPolicyVersion,
            int embeddingGeneration,
            int limit);
}
