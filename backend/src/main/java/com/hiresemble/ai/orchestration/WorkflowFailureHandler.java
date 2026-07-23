package com.hiresemble.ai.orchestration;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.ai.execution.AiExecutionException;

/** Domain-port compensation invoked before a failed Run reaches its terminal state. */
public interface WorkflowFailureHandler {

    boolean supports(AgentRunSnapshot run);

    void onFailure(AgentRunSnapshot run, AiExecutionException failure);
}
