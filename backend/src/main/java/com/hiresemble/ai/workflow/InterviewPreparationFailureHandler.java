package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.interview.application.port.InterviewWorkflowCommandPort;
import java.util.Objects;
import java.util.UUID;

/** Projects a failed preparation run into its research resource without deleting its placeholder. */
public final class InterviewPreparationFailureHandler implements WorkflowFailureHandler {

    private final InterviewWorkflowCommandPort commandPort;

    public InterviewPreparationFailureHandler(InterviewWorkflowCommandPort commandPort) {
        this.commandPort = Objects.requireNonNull(commandPort);
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return run.workflowType() == WorkflowType.INTERVIEW_PREPARATION;
    }

    @Override
    public void onFailure(AgentRunSnapshot run, AiExecutionException failure) {
        String value = run.inputReferenceSnapshot().path("researchRunId").asText();
        try {
            commandPort.failPreparation(
                    run.userId(),
                    UUID.fromString(value),
                    failure.safeCode(),
                    failure.retryable());
        } catch (IllegalArgumentException ignored) {
            // A malformed internal snapshot is already terminal and has no safe domain target.
        }
    }
}
