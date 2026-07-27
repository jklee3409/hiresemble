package com.hiresemble.job.application.port;

import com.hiresemble.job.domain.JobCommands.ExtractedFields;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import java.util.UUID;

public interface JobWorkflowCommandPort {

    JobRecord markExtracting(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion);

    JobRecord applyExtraction(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion,
            ExtractedFields fields);

    JobRecord markNeedsManualInput(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion);

    JobRecord markFailed(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion);

    void compensateToStableState(UUID userId, UUID jobId, UUID agentRunId);
}
