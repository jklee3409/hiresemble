package com.hiresemble.agentrun.application;

import java.util.UUID;

public interface AgentRunRetryPort {
    WorkflowLaunchResult retry(UUID userId, UUID predecessorRunId, String idempotencyKey);
}
