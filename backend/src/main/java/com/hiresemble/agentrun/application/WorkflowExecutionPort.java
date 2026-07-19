package com.hiresemble.agentrun.application;

public interface WorkflowExecutionPort {
    void execute(ClaimedAgentRun claimedRun);
}
