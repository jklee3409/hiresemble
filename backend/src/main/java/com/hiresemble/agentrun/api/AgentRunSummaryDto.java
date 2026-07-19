package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.WorkflowType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AgentRunSummaryDto(
        UUID id,
        WorkflowType workflowType,
        String resourceType,
        UUID resourceId,
        AgentRunStatus status,
        String currentStep,
        int progressPercent,
        AiQualityMode requestedQualityMode,
        ModelTier highestModelTierUsed,
        BigDecimal estimatedCostUsd,
        BigDecimal reservedCostUsd,
        BigDecimal actualCostUsd,
        boolean retryable,
        boolean cancellable,
        RequiredUserActionDto requiredUserAction,
        long stateVersion,
        Instant queuedAt,
        Instant updatedAt) {}
