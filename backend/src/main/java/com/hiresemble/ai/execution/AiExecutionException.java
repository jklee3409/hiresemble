package com.hiresemble.ai.execution;

import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.Objects;

/** Carries only a stable classification and safe projection; provider payloads stay in the cause boundary. */
public final class AiExecutionException extends RuntimeException {

    private final FailureKind failureKind;
    private final String safeCode;
    private final String safeMessage;
    private final boolean retryable;

    private AiExecutionException(
            FailureKind failureKind,
            String safeCode,
            String safeMessage,
            boolean retryable,
            Throwable cause) {
        super(safeCode, cause);
        this.failureKind = Objects.requireNonNull(failureKind);
        this.safeCode = Objects.requireNonNull(safeCode);
        this.safeMessage = Objects.requireNonNull(safeMessage);
        this.retryable = retryable;
    }

    public static AiExecutionException retryable(
            FailureKind kind, String safeCode, String safeMessage) {
        if (!kind.automaticallyRetryable()) {
            throw new IllegalArgumentException("failure kind is not retryable");
        }
        return new AiExecutionException(kind, safeCode, safeMessage, true, null);
    }

    public static AiExecutionException nonRetryable(
            FailureKind kind, String safeCode, String safeMessage) {
        return new AiExecutionException(kind, safeCode, safeMessage, false, null);
    }

    public FailureKind failureKind() { return failureKind; }
    public String safeCode() { return safeCode; }
    public String safeMessage() { return safeMessage; }
    public boolean retryable() { return retryable; }
}
