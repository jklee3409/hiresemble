package com.hiresemble.githubsource.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;

public enum GitHubSourceStatus {
    DISCOVERING,
    WAITING_USER,
    QUEUED,
    RUNNING,
    READY,
    PARTIAL,
    FAILED;

    public boolean terminalSnapshotState() {
        return this == READY || this == PARTIAL;
    }

    public void requireTransitionTo(GitHubSourceStatus next) {
        boolean allowed = switch (this) {
            case DISCOVERING -> next == WAITING_USER || next == RUNNING || next == FAILED;
            case WAITING_USER -> next == QUEUED || next == FAILED;
            case QUEUED -> next == RUNNING || next == FAILED;
            case RUNNING -> next == READY || next == PARTIAL || next == FAILED;
            case READY, PARTIAL, FAILED -> next == QUEUED;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }
}
