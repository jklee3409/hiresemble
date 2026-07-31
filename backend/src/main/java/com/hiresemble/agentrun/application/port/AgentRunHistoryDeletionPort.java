package com.hiresemble.agentrun.application.port;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface AgentRunHistoryDeletionPort {
    void softDeleteTerminalRuns(UUID userId, Set<UUID> agentRunIds, Instant deletedAt);
}
