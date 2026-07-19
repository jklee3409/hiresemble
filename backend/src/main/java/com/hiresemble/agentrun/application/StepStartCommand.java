package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AiQualityMode;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record StepStartCommand(
        UUID userId,
        UUID agentRunId,
        UUID claimToken,
        String stepKey,
        String scopeKey,
        int stepOrder,
        String agentName,
        int attempt,
        int maxAttempts,
        String inputHash,
        JsonNode sanitizedInputRefs,
        String outputSchemaVersion,
        long modelPolicyVersion,
        String promptVersion,
        AiQualityMode requestedQualityMode,
        Instant occurredAt) {}
