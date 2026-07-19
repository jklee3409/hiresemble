package com.hiresemble.agentrun.application;

import java.time.Instant;

public record AgentRunCommittedEvent(
        AgentRunEventType type,
        AgentRunSnapshot run,
        AgentStepSnapshot step,
        Instant occurredAt) {}
