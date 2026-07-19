package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentStepStatus;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.SafeError;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record StepCheckpointCommand(
        UUID userId,
        UUID agentRunId,
        UUID agentStepId,
        UUID claimToken,
        AgentStepStatus targetStatus,
        String outputHash,
        JsonNode minimalOutput,
        ModelTier modelTierUsed,
        UUID reusedStepId,
        SafeError safeError,
        Instant occurredAt) {}
