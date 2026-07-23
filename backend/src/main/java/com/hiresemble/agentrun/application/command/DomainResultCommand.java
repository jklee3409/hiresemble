package com.hiresemble.agentrun.application.command;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record DomainResultCommand(
        UUID userId,
        UUID agentRunId,
        UUID agentStepId,
        String resourceType,
        UUID resourceId,
        long expectedResourceVersion,
        String inputHash,
        JsonNode validatedResultReference) {}
