package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
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
