package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import java.util.UUID;

public record WorkflowLaunchResult(
        UUID agentRunId,
        AgentRunStatus status,
        String resourceType,
        UUID resourceId,
        boolean replayed) {}
