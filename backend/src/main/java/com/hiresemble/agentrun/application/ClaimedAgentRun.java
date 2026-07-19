package com.hiresemble.agentrun.application;

import java.time.Instant;
import java.util.UUID;

public record ClaimedAgentRun(
        AgentRunSnapshot run,
        UUID claimToken,
        String workerId,
        Instant leaseExpiresAt) {}
