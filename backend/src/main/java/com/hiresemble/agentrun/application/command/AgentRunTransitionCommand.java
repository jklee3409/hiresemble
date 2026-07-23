package com.hiresemble.agentrun.application.command;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.SafeError;
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
