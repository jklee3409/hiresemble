package com.hiresemble.agentrun.api.dto;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
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
