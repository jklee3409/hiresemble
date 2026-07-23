package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.ClaimedAgentRun;

public interface WorkflowExecutionPort {
    void execute(ClaimedAgentRun claimedRun);
}
