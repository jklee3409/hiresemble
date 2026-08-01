package com.hiresemble.ai.execution;

import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.List;
import java.util.Objects;

/** Carries only a stable classification and safe projection; provider payloads stay in the cause boundary. */
public final class AiExecutionException extends RuntimeException {

    private final FailureKind failureKind;
    private final String safeCode;
    private final String safeMessage;
    private final boolean retryable;
    private final List<AiUsage> incurredUsages;
    private final int maxAutomaticAttempts;
    private final String correctionGuidance;
    private final ValidationPhase validationPhase;

    private AiExecutionException(
            FailureKind failureKind,
            String safeCode,
            String safeMessage,
            boolean retryable,
            List<AiUsage> incurredUsages,
            int maxAutomaticAttempts,
            String correctionGuidance,
            ValidationPhase validationPhase,
            Throwable cause) {
        super(safeCode, cause);
        this.failureKind = Objects.requireNonNull(failureKind);
        this.safeCode = Objects.requireNonNull(safeCode);
        this.safeMessage = Objects.requireNonNull(safeMessage);
        this.retryable = retryable;
        this.incurredUsages =
                incurredUsages == null ? List.of() : List.copyOf(incurredUsages);
        this.maxAutomaticAttempts = maxAutomaticAttempts;
        this.correctionGuidance = correctionGuidance;
        this.validationPhase = validationPhase;
    }

    public static AiExecutionException retryable(
            FailureKind kind, String safeCode, String safeMessage) {
        if (!kind.automaticallyRetryable()) {
            throw new IllegalArgumentException("failure kind is not retryable");
        }
        return new AiExecutionException(
                kind, safeCode, safeMessage, true, List.of(), 3, null, null, null);
    }

    public static AiExecutionException retryable(
            FailureKind kind,
            String safeCode,
            String safeMessage,
            List<AiUsage> incurredUsages) {
        if (!kind.automaticallyRetryable()) {
            throw new IllegalArgumentException("failure kind is not retryable");
        }
        return new AiExecutionException(
                kind, safeCode, safeMessage, true, incurredUsages, 3, null, null, null);
    }

    public static AiExecutionException repairableStructuredOutput(
            String safeCode,
            String safeMessage,
            ValidationPhase validationPhase,
            String correctionGuidance) {
        return new AiExecutionException(
                FailureKind.STRUCTURED_OUTPUT,
                safeCode,
                safeMessage,
                true,
                List.of(),
                2,
                Objects.requireNonNull(correctionGuidance),
                Objects.requireNonNull(validationPhase),
                null);
    }

    public static AiExecutionException deterministicStructuredOutput(
            String safeCode, String safeMessage, ValidationPhase validationPhase) {
        return new AiExecutionException(
                FailureKind.STRUCTURED_OUTPUT,
                safeCode,
                safeMessage,
                false,
                List.of(),
                1,
                null,
                Objects.requireNonNull(validationPhase),
                null);
    }

    public static AiExecutionException nonRetryable(
            FailureKind kind, String safeCode, String safeMessage) {
        return new AiExecutionException(
                kind, safeCode, safeMessage, false, List.of(), 1, null, null, null);
    }

    public static AiExecutionException nonRetryable(
            FailureKind kind,
            String safeCode,
            String safeMessage,
            List<AiUsage> incurredUsages) {
        return new AiExecutionException(
                kind, safeCode, safeMessage, false, incurredUsages, 1, null, null, null);
    }

    public FailureKind failureKind() { return failureKind; }
    public String safeCode() { return safeCode; }
    public String safeMessage() { return safeMessage; }
    public boolean retryable() { return retryable; }
    public List<AiUsage> incurredUsages() { return incurredUsages; }
    public int maxAutomaticAttempts() { return maxAutomaticAttempts; }
    public String correctionGuidance() { return correctionGuidance; }
    public ValidationPhase validationPhase() { return validationPhase; }

    public AiExecutionException withIncurredUsages(List<AiUsage> usages) {
        return new AiExecutionException(
                failureKind,
                safeCode,
                safeMessage,
                retryable,
                usages,
                maxAutomaticAttempts,
                correctionGuidance,
                validationPhase,
                null);
    }
}
