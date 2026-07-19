package com.hiresemble.agentrun.api;

import java.time.Instant;
import java.util.UUID;

public record WaitingUserEventDto(
        UUID agentRunId,
        long stateVersion,
        Instant occurredAt,
        RequiredUserActionDto requiredUserAction) {}
