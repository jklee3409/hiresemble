package com.hiresemble.agentrun.application.model;

import java.time.Instant;

public record AgentRunCommittedEvent(
        AgentRunEventType type,
        AgentRunSnapshot run,
        AgentStepSnapshot step,
        Instant occurredAt) {}
