package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.PartialResult;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.agentrun.domain.SafeError;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AgentRunTransitionCommand(
        UUID userId,
        UUID agentRunId,
        UUID claimToken,
        long expectedStateVersion,
        AgentRunStatus targetStatus,
        String currentStep,
        int progressPercent,
        ModelTier highestModelTierUsed,
        BigDecimal actualCostUsd,
        boolean retryableFailure,
        RequiredUserAction requiredUserAction,
        SafeError safeError,
        PartialResult partialResult,
        Instant occurredAt) {}
