package com.hiresemble.agentrun.domain;

import java.util.Objects;
import java.util.UUID;

public final class AgentStep {

    private final UUID id;
    private final UUID runId;
    private final String stepKey;
    private final String scopeKey;
    private final int attempt;
    private final int maxAttempts;
    private AgentStepStatus status;

    public AgentStep(
            UUID id,
            UUID runId,
            String stepKey,
            String scopeKey,
            int attempt,
            int maxAttempts,
            AgentStepStatus status) {
        this.id = Objects.requireNonNull(id);
        this.runId = Objects.requireNonNull(runId);
        if (stepKey == null || stepKey.isBlank() || stepKey.length() > 100) {
            throw new IllegalArgumentException("step key is invalid");
        }
        if (scopeKey != null && (scopeKey.isBlank() || scopeKey.length() > 100)) {
            throw new IllegalArgumentException("scope key is invalid");
        }
        if (maxAttempts < 1 || maxAttempts > 3 || attempt < 1 || attempt > maxAttempts) {
            throw new IllegalArgumentException("step attempt is invalid");
        }
        this.stepKey = stepKey;
        this.scopeKey = scopeKey;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.status = Objects.requireNonNull(status);
    }

    public void transitionTo(AgentStepStatus target) {
        if (!status.canTransitionTo(Objects.requireNonNull(target))) {
            throw new IllegalStateException("agent step transition is not allowed");
        }
        status = target;
    }

    public AgentStep nextAttempt(UUID nextId) {
        if (status != AgentStepStatus.FAILED || attempt >= maxAttempts) {
            throw new IllegalStateException("agent step has no remaining attempt");
        }
        return new AgentStep(nextId, runId, stepKey, scopeKey,
                attempt + 1, maxAttempts, AgentStepStatus.PENDING);
    }

    public UUID id() { return id; }
    public UUID runId() { return runId; }
    public String stepKey() { return stepKey; }
    public String scopeKey() { return scopeKey; }
    public int attempt() { return attempt; }
    public int maxAttempts() { return maxAttempts; }
    public AgentStepStatus status() { return status; }
}
