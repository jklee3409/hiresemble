package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.ClaimedAgentRun;
import com.hiresemble.agentrun.application.model.SafeInterruption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunStatePort {
    Optional<ClaimedAgentRun> claimNext(String workerId, Instant now, Duration leaseDuration);
    Optional<ClaimedAgentRun> claim(UUID agentRunId, String workerId, Instant now, Duration leaseDuration);
    AgentRunSnapshot transition(AgentRunTransitionCommand command);
    AgentRunSnapshot updateProgress(
            UUID userId,
            UUID agentRunId,
            UUID claimToken,
            long expectedStateVersion,
            String currentStep,
            int progressPercent,
            Instant occurredAt);
    AgentRunSnapshot resumeWaiting(
            UUID userId,
            UUID agentRunId,
            long expectedStateVersion,
            java.math.BigDecimal reservedCostUsd,
            Instant occurredAt);
    AgentRunSnapshot markCancellationRequested(
            UUID userId,
            UUID agentRunId,
            long expectedStateVersion,
            Instant requestedAt);
    AgentRunSnapshot cancelUnclaimed(UUID userId, UUID agentRunId, Instant completedAt);
    AgentRunSnapshot cancelClaimed(
            UUID userId, UUID agentRunId, UUID claimToken, Instant completedAt);
    AgentRunSnapshot heartbeat(
            UUID userId,
            UUID agentRunId,
            UUID claimToken,
            Instant now,
            Duration leaseDuration);
    boolean isCancellationRequested(UUID userId, UUID agentRunId, UUID claimToken);
    List<UUID> findQueuedRunIds(int limit);
    List<UUID> findExpiredRunningIds(Instant now, int limit);
    AgentRunSnapshot interruptExpired(UUID agentRunId, Instant now, SafeInterruption safeInterruption);
}
