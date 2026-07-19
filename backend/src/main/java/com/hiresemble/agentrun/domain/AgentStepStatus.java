package com.hiresemble.agentrun.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AgentStepStatus {
    PENDING,
    RUNNING,
    WAITING_USER,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    REUSED,
    CANCELLED,
    INTERRUPTED;

    private static final Set<AgentStepStatus> TERMINAL =
            EnumSet.of(SUCCEEDED, FAILED, SKIPPED, REUSED, CANCELLED, INTERRUPTED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(AgentStepStatus target) {
        return switch (this) {
            case PENDING -> target == RUNNING
                    || target == SKIPPED
                    || target == REUSED
                    || target == CANCELLED;
            case RUNNING -> target == WAITING_USER
                    || target == SUCCEEDED
                    || target == FAILED
                    || target == CANCELLED
                    || target == INTERRUPTED;
            case WAITING_USER -> target == PENDING || target == CANCELLED;
            case SUCCEEDED, FAILED, SKIPPED, REUSED, CANCELLED, INTERRUPTED -> false;
        };
    }
}
