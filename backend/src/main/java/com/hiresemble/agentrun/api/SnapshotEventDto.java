package com.hiresemble.agentrun.api;

import java.time.Instant;
import java.util.UUID;

public record SnapshotEventDto(
        UUID agentRunId, long stateVersion, Instant occurredAt, AgentRunDetailDto run) {}
