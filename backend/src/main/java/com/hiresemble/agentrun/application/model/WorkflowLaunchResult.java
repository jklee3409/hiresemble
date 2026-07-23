package com.hiresemble.agentrun.application.model;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import java.util.UUID;

public record WorkflowLaunchResult(
        UUID agentRunId,
        AgentRunStatus status,
        String resourceType,
        UUID resourceId,
        boolean replayed) {}
