package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProgressEventDto(
        UUID agentRunId,
        long stateVersion,
        Instant occurredAt,
        AgentRunStatus status,
        String currentStep,
        int progressPercent,
        BigDecimal actualCostUsd) {}
