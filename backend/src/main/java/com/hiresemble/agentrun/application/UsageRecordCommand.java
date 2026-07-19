package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.UsageType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UsageRecordCommand(
        UUID userId,
        UUID agentRunId,
        UUID agentStepId,
        UUID claimToken,
        String operationType,
        UsageType usageType,
        String providerKey,
        String productKey,
        ModelTier modelTier,
        long inputUnits,
        long cachedInputUnits,
        long outputUnits,
        long embeddingUnits,
        long searchUnits,
        Long priceVersion,
        UUID priceItemId,
        BigDecimal costUsd,
        long durationMs,
        Instant occurredAt) {}
