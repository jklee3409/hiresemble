package com.hiresemble.agentrun.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_USER,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    INTERRUPTED;

    private static final Set<AgentRunStatus> TERMINAL =
            EnumSet.of(SUCCEEDED, FAILED, CANCELLED, INTERRUPTED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(AgentRunStatus target) {
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == WAITING_USER
                    || target == SUCCEEDED
                    || target == FAILED
                    || target == CANCELLED
                    || target == INTERRUPTED;
            case WAITING_USER -> target == QUEUED || target == CANCELLED;
            case SUCCEEDED, FAILED, CANCELLED, INTERRUPTED -> false;
        };
    }
}
