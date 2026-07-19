package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.domain.AgentStepStatus;
import java.time.Instant;
import java.util.UUID;

public record AgentStepDto(
        UUID id,
        String stepKey,
        String scopeKey,
        int stepOrder,
        AgentStepStatus status,
        int attempt,
        int maxAttempts,
        Instant startedAt,
        Instant completedAt,
        SafeErrorDto safeError) {}
