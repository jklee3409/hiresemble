package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import java.time.Instant;
import java.util.UUID;

public record HeartbeatEventDto(
        UUID agentRunId,
        long stateVersion,
        Instant occurredAt,
        Instant serverTime,
        AgentRunStatus status) {}
