package com.hiresemble.agentrun.application.command;

import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.UsageType;
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
        UUID providerCallId,
        Instant occurredAt) {

    public UsageRecordCommand(
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
            Instant occurredAt) {
        this(
                userId,
                agentRunId,
                agentStepId,
                claimToken,
                operationType,
                usageType,
                providerKey,
                productKey,
                modelTier,
                inputUnits,
                cachedInputUnits,
                outputUnits,
                embeddingUnits,
                searchUnits,
                priceVersion,
                priceItemId,
                costUsd,
                durationMs,
                null,
                occurredAt);
    }
}
