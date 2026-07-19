package com.hiresemble.agentrun.application;

import java.time.Instant;
import java.util.UUID;

public interface AgentRunCancellationPort {
    AgentRunSnapshot requestCancellation(
            UUID userId, UUID agentRunId, long expectedStateVersion, Instant requestedAt);
    AgentRunSnapshot completeCancellation(
            UUID userId, UUID agentRunId, UUID claimToken, Instant completedAt);
}
