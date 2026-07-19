package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import java.time.Instant;
import java.util.UUID;

public record HeartbeatEventDto(
        UUID agentRunId,
        long stateVersion,
        Instant occurredAt,
        Instant serverTime,
        AgentRunStatus status) {}
