package com.hiresemble.ai.orchestration;

/**
 * Commits a successful or reused checkpoint together with its domain application.
 *
 * <p>Gateway calls, validation, usage recording, and cancellation checks happen before this
 * boundary. A committed completed step therefore implies that its domain effect committed too.
 */
@FunctionalInterface
public interface StepCompletionTransaction {

    void execute(Runnable work);
}
