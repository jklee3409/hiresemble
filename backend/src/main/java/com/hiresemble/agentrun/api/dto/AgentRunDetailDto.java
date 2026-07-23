package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentRunDetailDto(
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
        Instant updatedAt,
        UUID retryOfRunId,
        UUID rootRunId,
        int runAttemptNo,
        Long durationMs,
        Instant startedAt,
        Instant completedAt,
        SafeErrorDto safeError,
        PartialResultDto partialResult,
        List<AgentStepDto> steps) {
    public AgentRunDetailDto {
        steps = List.copyOf(steps);
    }
}
