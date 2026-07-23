package com.hiresemble.agentrun.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AgentRun {

    private final UUID id;
    private final UUID userId;
    private final WorkflowType workflowType;
    private final UUID retryOfRunId;
    private final UUID rootRunId;
    private final int runAttemptNo;
    private AgentRunStatus status;
    private long stateVersion;
    private Instant cancelRequestedAt;
    private boolean retryableFailure;

    private AgentRun(
            UUID id,
            UUID userId,
            WorkflowType workflowType,
            AgentRunStatus status,
            UUID retryOfRunId,
            UUID rootRunId,
            int runAttemptNo,
            long stateVersion,
            Instant cancelRequestedAt,
            boolean retryableFailure) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.workflowType = Objects.requireNonNull(workflowType);
        this.status = Objects.requireNonNull(status);
        this.retryOfRunId = retryOfRunId;
        this.rootRunId = Objects.requireNonNull(rootRunId);
        if (runAttemptNo < 1 || stateVersion < 0) {
            throw new IllegalArgumentException("run attempt and state version must be valid");
        }
        if ((retryOfRunId == null && (runAttemptNo != 1 || !rootRunId.equals(id)))
                || (retryOfRunId != null && (runAttemptNo < 2 || rootRunId.equals(id)))) {
            throw new IllegalArgumentException("run lineage is invalid");
        }
        this.runAttemptNo = runAttemptNo;
        this.stateVersion = stateVersion;
        this.cancelRequestedAt = cancelRequestedAt;
        this.retryableFailure = retryableFailure;
    }

    public static AgentRun queued(UUID id, UUID userId, WorkflowType workflowType) {
        return new AgentRun(id, userId, workflowType, AgentRunStatus.QUEUED,
                null, id, 1, 0, null, false);
    }

    public static AgentRun rehydrate(
            UUID id,
            UUID userId,
            WorkflowType workflowType,
            AgentRunStatus status,
            UUID retryOfRunId,
            UUID rootRunId,
            int runAttemptNo,
            long stateVersion,
            Instant cancelRequestedAt,
            boolean retryableFailure) {
        return new AgentRun(id, userId, workflowType, status, retryOfRunId, rootRunId,
                runAttemptNo, stateVersion, cancelRequestedAt, retryableFailure);
    }

    public AgentRun retry(UUID successorId) {
        if (!retryable()) {
            throw new IllegalStateException("run is not retryable");
        }
        return new AgentRun(successorId, userId, workflowType, AgentRunStatus.QUEUED,
                id, rootRunId, runAttemptNo + 1, 0, null, false);
    }

    public void transitionTo(AgentRunStatus target) {
        if (!status.canTransitionTo(Objects.requireNonNull(target))) {
            throw new IllegalStateException("agent run transition is not allowed");
        }
        status = target;
        stateVersion++;
    }

    public void requestCancellation(Instant requestedAt) {
        if (!cancellable() || cancelRequestedAt != null) {
            throw new IllegalStateException("agent run cancellation cannot be requested");
        }
        cancelRequestedAt = Objects.requireNonNull(requestedAt);
        stateVersion++;
    }

    public void markRetryableFailure(boolean retryableFailure) {
        if (status != AgentRunStatus.FAILED && status != AgentRunStatus.INTERRUPTED) {
            throw new IllegalStateException("retry classification requires a failed terminal run");
        }
        this.retryableFailure = retryableFailure;
    }

    public boolean retryable() {
        return (status == AgentRunStatus.FAILED || status == AgentRunStatus.INTERRUPTED)
                && retryableFailure;
    }

    public boolean cancellable() {
        return cancelRequestedAt == null && (status == AgentRunStatus.QUEUED
                || status == AgentRunStatus.RUNNING
                || status == AgentRunStatus.WAITING_USER);
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public WorkflowType workflowType() { return workflowType; }
    public AgentRunStatus status() { return status; }
    public UUID retryOfRunId() { return retryOfRunId; }
    public UUID rootRunId() { return rootRunId; }
    public int runAttemptNo() { return runAttemptNo; }
    public long stateVersion() { return stateVersion; }
    public Instant cancelRequestedAt() { return cancelRequestedAt; }
}
