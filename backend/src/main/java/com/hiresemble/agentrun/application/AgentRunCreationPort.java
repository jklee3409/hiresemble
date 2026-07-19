package com.hiresemble.agentrun.application;

import java.time.Instant;
import java.util.UUID;

public interface AgentRunCreationPort {
    AgentRunSnapshot createQueued(
            UUID agentRunId,
            WorkflowLaunchCommand command,
            long budgetPolicyVersion,
            Instant queuedAt);

    AgentRunSnapshot createRetry(
            UUID successorId,
            AgentRunSnapshot predecessor,
            long budgetPolicyVersion,
            Instant queuedAt);
}
