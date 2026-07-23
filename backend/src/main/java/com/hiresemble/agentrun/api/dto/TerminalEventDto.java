package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TerminalEventDto(
        UUID agentRunId,
        long stateVersion,
        Instant occurredAt,
        AgentRunStatus status,
        Instant completedAt,
        BigDecimal actualCostUsd,
        boolean retryable,
        SafeErrorDto safeError,
        String resourceType,
        UUID resourceId) {}
