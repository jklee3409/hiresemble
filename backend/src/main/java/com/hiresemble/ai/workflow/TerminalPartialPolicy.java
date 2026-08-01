package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.PartialResult;
import java.util.Objects;
import java.util.regex.Pattern;

/** Workflow-owned decision for a terminal result that contains actual failed scopes. */
public record TerminalPartialPolicy(
        Outcome outcome,
        String safeErrorCode,
        String safeMessage,
        RetryPolicy retryPolicy) {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_]{1,100}");

    public TerminalPartialPolicy {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (outcome == Outcome.SUCCEEDED) {
            if (safeErrorCode != null || safeMessage != null || retryPolicy != RetryPolicy.NEVER) {
                throw new IllegalArgumentException("successful partial policy cannot expose an error");
            }
        } else {
            if (safeErrorCode == null || !SAFE_CODE.matcher(safeErrorCode).matches()) {
                throw new IllegalArgumentException("partial failure safe code is invalid");
            }
            if (safeMessage == null || safeMessage.isBlank() || safeMessage.length() > 500) {
                throw new IllegalArgumentException("partial failure safe message is invalid");
            }
        }
    }

    public static TerminalPartialPolicy succeed() {
        return new TerminalPartialPolicy(Outcome.SUCCEEDED, null, null, RetryPolicy.NEVER);
    }

    public static TerminalPartialPolicy fail(
            String safeErrorCode, String safeMessage, RetryPolicy retryPolicy) {
        return new TerminalPartialPolicy(
                Outcome.FAILED, safeErrorCode, safeMessage, retryPolicy);
    }

    public static TerminalPartialPolicy rejectUnexpected() {
        return fail(
                "AI_UNEXPECTED_PARTIAL_RESULT",
                "AI 작업의 부분 결과를 안전하게 완료할 수 없습니다.",
                RetryPolicy.NEVER);
    }

    public Decision decide(PartialResult partialResult, boolean accumulatedRetryable) {
        Objects.requireNonNull(partialResult, "partialResult");
        if (partialResult.failedScopeKeys().isEmpty()) {
            throw new IllegalArgumentException("terminal partial policy requires failed scopes");
        }
        return new Decision(
                outcome,
                safeErrorCode,
                safeMessage,
                outcome == Outcome.FAILED
                        && retryPolicy == RetryPolicy.INHERIT_FAILURES
                        && accumulatedRetryable);
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }

    public enum RetryPolicy {
        NEVER,
        INHERIT_FAILURES
    }

    public record Decision(
            Outcome outcome,
            String safeErrorCode,
            String safeMessage,
            boolean retryable) {}
}
