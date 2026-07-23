package com.hiresemble.agentrun.application.command;

import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.SafeError;
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
