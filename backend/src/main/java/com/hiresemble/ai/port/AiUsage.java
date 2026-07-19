package com.hiresemble.ai.port;

import com.hiresemble.agentrun.domain.UsageType;
import java.math.BigDecimal;
import java.util.UUID;

public record AiUsage(
        UsageType usageType,
        String providerKey,
        String productKey,
        long inputUnits,
        long cachedInputUnits,
        long outputUnits,
        long embeddingUnits,
        long searchUnits,
        Long priceVersion,
        UUID priceItemId,
        BigDecimal costUsd,
        long durationMs) {

    public AiUsage {
        if (usageType == null || providerKey == null || providerKey.isBlank()
                || productKey == null || productKey.isBlank()
                || inputUnits < 0 || cachedInputUnits < 0 || outputUnits < 0
                || embeddingUnits < 0 || searchUnits < 0 || costUsd == null
                || costUsd.signum() < 0 || costUsd.scale() > 6 || durationMs < 0
                || (costUsd.signum() > 0 && (priceVersion == null || priceItemId == null))) {
            throw new IllegalArgumentException("AI usage is invalid");
        }
    }
}
