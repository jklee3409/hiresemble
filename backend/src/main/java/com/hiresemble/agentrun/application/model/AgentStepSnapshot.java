package com.hiresemble.agentrun.application.model;

import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.agentrun.domain.model.SafeError;
import java.time.Instant;
import java.util.UUID;

public record AgentStepSnapshot(
        UUID id,
        String stepKey,
        String scopeKey,
        int stepOrder,
        String agentName,
        AgentStepStatus status,
        int attempt,
        int maxAttempts,
        Instant startedAt,
        Instant completedAt,
        SafeError safeError) {}
