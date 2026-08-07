package com.hiresemble.ai.workflow.github;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.githubsource.application.GitHubWorkflowCommandPort;
import java.time.Clock;

public final class GitHubIngestionFailureHandler implements WorkflowFailureHandler {

    private final GitHubWorkflowCommandPort commandPort;
    private final Clock clock;

    public GitHubIngestionFailureHandler(GitHubWorkflowCommandPort commandPort, Clock clock) {
        this.commandPort = commandPort;
        this.clock = clock;
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return run.workflowType() == WorkflowType.GITHUB_INGESTION
                && "GITHUB_SOURCE".equals(run.resourceType())
                && run.resourceId() != null;
    }

    @Override
    public void onFailure(AgentRunSnapshot run, AiExecutionException failure) {
        commandPort.fail(run.userId(), run.resourceId(), run.id(), clock.instant());
    }
}
