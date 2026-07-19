package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import java.util.UUID;

public record RunAcceptedDto(
        UUID agentRunId,
        AgentRunStatus status,
        String resourceType,
        UUID resourceId,
        boolean replayed) {}
