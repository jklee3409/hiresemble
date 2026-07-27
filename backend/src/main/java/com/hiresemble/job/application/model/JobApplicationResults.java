package com.hiresemble.job.application.model;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobStatus;
import java.util.UUID;

public final class JobApplicationResults {

    private JobApplicationResults() {}

    public record JobCreationAccepted(
            UUID jobId,
            JobStatus status,
            JobExtractionStatus extractionStatus,
            UUID agentRunId) {}

    public record RunAccepted(
            UUID agentRunId,
            AgentRunStatus status,
            String resourceType,
            UUID resourceId) {}
}
