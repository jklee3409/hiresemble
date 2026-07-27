package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.job.application.port.JobWorkflowCommandPort;
import com.hiresemble.job.application.port.JobWorkflowQueryPort;
import com.hiresemble.job.domain.JobExtractionStatus;
import java.util.Objects;

/** Moves an active Job extraction to FAILED without changing its separate business status. */
public final class JobPostingExtractionFailureHandler implements WorkflowFailureHandler {

    private final JobWorkflowQueryPort queryPort;
    private final JobWorkflowCommandPort commandPort;

    public JobPostingExtractionFailureHandler(
            JobWorkflowQueryPort queryPort,
            JobWorkflowCommandPort commandPort) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return run.workflowType() == WorkflowType.JOB_POSTING_EXTRACTION
                && "JOB".equals(run.resourceType())
                && run.resourceId() != null;
    }

    @Override
    public void onFailure(AgentRunSnapshot run, AiExecutionException failure) {
        var job = queryPort.snapshot(run.userId(), run.resourceId());
        if (!run.id().equals(job.latestAgentRunId())) {
            return;
        }
        if (job.extractionStatus() == JobExtractionStatus.QUEUED
                || job.extractionStatus() == JobExtractionStatus.EXTRACTING) {
            commandPort.markFailed(
                    run.userId(),
                    run.resourceId(),
                    run.id(),
                    job.version());
        }
    }
}
