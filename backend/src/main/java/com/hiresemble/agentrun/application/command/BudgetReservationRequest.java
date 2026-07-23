package com.hiresemble.agentrun.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetReservationRequest(
        UUID userId,
        UUID agentRunId,
        String operationType,
        BigDecimal worstCaseCostUsd,
        Long priceVersion,
        Instant requestedAt) {}
