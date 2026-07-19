package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.AgentRunStatus;
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
