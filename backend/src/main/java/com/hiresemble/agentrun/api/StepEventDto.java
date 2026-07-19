package com.hiresemble.agentrun.api;

import java.time.Instant;
import java.util.UUID;

public record StepEventDto(
        UUID agentRunId, long stateVersion, Instant occurredAt, AgentStepDto step) {}
