package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import java.util.Objects;

/** Ensures a failed verification Run cannot leave its linked domain row PENDING. */
public final class CoverLetterVerificationFailureHandler
        implements WorkflowFailureHandler {

    private final CoverLetterCommandPort commandPort;

    public CoverLetterVerificationFailureHandler(
            CoverLetterCommandPort commandPort) {
        this.commandPort = Objects.requireNonNull(commandPort);
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return run.workflowType() == WorkflowType.COVER_LETTER_VERIFICATION;
    }

    @Override
    public void onFailure(
            AgentRunSnapshot run, AiExecutionException failure) {
        commandPort.failPendingVerification(run.userId(), run.id());
    }
}
