package com.hiresemble.agentrun.api.dto;

import java.time.Instant;
import java.util.UUID;

public record StepEventDto(
        UUID agentRunId, long stateVersion, Instant occurredAt, AgentStepDto step) {}
