package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.PartialResult;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.agentrun.domain.SafeError;
import com.hiresemble.agentrun.domain.WorkflowType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record AgentRunSnapshot(
        UUID id,
        UUID userId,
        WorkflowType workflowType,
        AgentRunStatus status,
        String currentStep,
        int progressPercent,
        String workflowVersion,
        String canonicalInputHash,
        JsonNode inputReferenceSnapshot,
        long budgetPolicyVersion,
        Long priceVersion,
        AiQualityMode requestedQualityMode,
        ModelTier highestModelTierUsed,
        BigDecimal estimatedCostUsd,
        BigDecimal reservedCostUsd,
        BigDecimal actualCostUsd,
        String resourceType,
        UUID resourceId,
        UUID retryOfRunId,
        UUID rootRunId,
        int runAttemptNo,
        boolean retryableFailure,
        SafeError safeError,
        PartialResult partialResult,
        UUID claimToken,
        String claimedBy,
        Instant leaseExpiresAt,
        Instant heartbeatAt,
        Instant cancelRequestedAt,
        RequiredUserAction requiredUserAction,
        long stateVersion,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt,
        List<AgentStepSnapshot> steps) {

    public AgentRunSnapshot {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public boolean retryable() {
        return (status == AgentRunStatus.FAILED || status == AgentRunStatus.INTERRUPTED)
                && retryableFailure;
    }

    public boolean cancellable() {
        return cancelRequestedAt == null && (status == AgentRunStatus.QUEUED
                || status == AgentRunStatus.RUNNING
                || status == AgentRunStatus.WAITING_USER);
    }
}
