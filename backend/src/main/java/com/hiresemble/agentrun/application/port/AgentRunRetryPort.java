package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import java.util.UUID;

public interface AgentRunRetryPort {
    WorkflowLaunchResult retry(UUID userId, UUID predecessorRunId, String idempotencyKey);
}
