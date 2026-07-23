package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import java.time.Instant;
import java.util.UUID;

public interface AgentRunCancellationPort {
    AgentRunSnapshot requestCancellation(
            UUID userId, UUID agentRunId, long expectedStateVersion, Instant requestedAt);
    AgentRunSnapshot completeCancellation(
            UUID userId, UUID agentRunId, UUID claimToken, Instant completedAt);
}
