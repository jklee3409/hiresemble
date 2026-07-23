package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import java.time.Instant;
import java.util.UUID;

public interface AgentRunResumePort {
    AgentRunSnapshot resume(
            UUID userId, UUID agentRunId, long expectedStateVersion, Instant requestedAt);
}
