package com.hiresemble.ai.validation;

import java.util.Objects;

/** Typed, value-free failure raised by structured output contract validators. */
public final class StructuredOutputValidationException extends RuntimeException {

    private final ValidationPhase phase;
    private final String safeReason;
    private final RetryDisposition retryDisposition;
    private final String correctionGuidance;

    private StructuredOutputValidationException(
            ValidationPhase phase,
            String safeReason,
            RetryDisposition retryDisposition,
            String correctionGuidance) {
        super(safeReason);
        this.phase = Objects.requireNonNull(phase);
        this.safeReason = requireSafeToken(safeReason);
        this.retryDisposition = Objects.requireNonNull(retryDisposition);
        this.correctionGuidance = correctionGuidance;
        if (retryDisposition == RetryDisposition.REPAIR_ONCE
                && (correctionGuidance == null || correctionGuidance.isBlank())) {
            throw new IllegalArgumentException("repair guidance is required");
        }
    }

    public static StructuredOutputValidationException deterministic(
            ValidationPhase phase, String safeReason) {
        return new StructuredOutputValidationException(
                phase, safeReason, RetryDisposition.DO_NOT_RETRY, null);
    }

    public static StructuredOutputValidationException repairable(
            ValidationPhase phase, String safeReason, String correctionGuidance) {
        return new StructuredOutputValidationException(
                phase, safeReason, RetryDisposition.REPAIR_ONCE,
                requireSafeGuidance(correctionGuidance));
    }

    public ValidationPhase phase() {
        return phase;
    }

    public String safeReason() {
        return safeReason;
    }

    public RetryDisposition retryDisposition() {
        return retryDisposition;
    }

    public String correctionGuidance() {
        return correctionGuidance;
    }

    private static String requireSafeToken(String value) {
        if (value == null || value.isBlank() || value.length() > 100
                || !value.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("safe reason is invalid");
        }
        return value;
    }

    private static String requireSafeGuidance(String value) {
        if (value == null || value.isBlank() || value.length() > 500
                || value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            throw new IllegalArgumentException("safe repair guidance is invalid");
        }
        return value;
    }

    public enum ValidationPhase {
        JSON_PARSE,
        SCHEMA_SHAPE,
        JAVA_BINDING,
        JAVA_RECORD,
        WORKFLOW_CONTEXT,
        DOMAIN_COMMAND
    }

    public enum RetryDisposition {
        DO_NOT_RETRY,
        REPAIR_ONCE
    }
}
