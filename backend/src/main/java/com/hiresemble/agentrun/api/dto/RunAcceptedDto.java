package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import java.util.UUID;

public record RunAcceptedDto(
        UUID agentRunId,
        AgentRunStatus status,
        String resourceType,
        UUID resourceId,
        boolean replayed) {}
