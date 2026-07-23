package com.hiresemble.agentrun.domain;

import com.hiresemble.agentrun.domain.model.AgentRun;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AgentStep;
import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentRunDomainTest {

    @ParameterizedTest
    @MethodSource("allowedRunTransitions")
    void allowsExactlyTheCanonicalRunTransitions(AgentRunStatus source, AgentRunStatus target) {
        AgentRun run = run(source, false);
        long version = run.stateVersion();

        run.transitionTo(target);

        assertThat(run.status()).isEqualTo(target);
        assertThat(run.stateVersion()).isEqualTo(version + 1);
    }

    @ParameterizedTest
    @MethodSource("forbiddenRunTransitions")
    void rejectsEveryNonCanonicalRunTransition(AgentRunStatus source, AgentRunStatus target) {
        assertThatThrownBy(() -> run(source, false).transitionTo(target))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @MethodSource("allowedStepTransitions")
    void allowsExactlyTheCanonicalStepTransitions(AgentStepStatus source, AgentStepStatus target) {
        AgentStep step = step(source, 1, 3);
        step.transitionTo(target);
        assertThat(step.status()).isEqualTo(target);
    }

    @ParameterizedTest
    @MethodSource("forbiddenStepTransitions")
    void rejectsEveryNonCanonicalStepTransition(AgentStepStatus source, AgentStepStatus target) {
        assertThatThrownBy(() -> step(source, 1, 3).transitionTo(target))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalRetryCreatesANewRunLineageAndRestartsRunAttemptOnly() {
        AgentRun failed = run(AgentRunStatus.FAILED, true);

        AgentRun retry = failed.retry(UUID.randomUUID());

        assertThat(retry.id()).isNotEqualTo(failed.id());
        assertThat(retry.retryOfRunId()).isEqualTo(failed.id());
        assertThat(retry.rootRunId()).isEqualTo(failed.rootRunId());
        assertThat(retry.runAttemptNo()).isEqualTo(failed.runAttemptNo() + 1);
        assertThat(retry.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(retry.stateVersion()).isZero();
        assertThat(step(AgentStepStatus.FAILED, 1, 3).nextAttempt(UUID.randomUUID()).attempt())
                .isEqualTo(2);
    }

    @Test
    void waitingUserResumesTheSameRunWhileTerminalNeverReopens() {
        AgentRun waiting = run(AgentRunStatus.WAITING_USER, false);
        UUID id = waiting.id();
        waiting.transitionTo(AgentRunStatus.QUEUED);
        assertThat(waiting.id()).isEqualTo(id);

        for (AgentRunStatus terminal : new AgentRunStatus[] {
                AgentRunStatus.SUCCEEDED,
                AgentRunStatus.FAILED,
                AgentRunStatus.CANCELLED,
                AgentRunStatus.INTERRUPTED}) {
            assertThatThrownBy(() -> run(terminal, true).transitionTo(AgentRunStatus.RUNNING))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void cancellationRequestIsDistinctFromTerminalCancellationAndChangesVersion() {
        AgentRun running = run(AgentRunStatus.RUNNING, false);
        long version = running.stateVersion();

        running.requestCancellation(Instant.parse("2026-07-19T00:00:00Z"));

        assertThat(running.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(running.cancelRequestedAt()).isNotNull();
        assertThat(running.stateVersion()).isEqualTo(version + 1);
        assertThat(running.cancellable()).isFalse();
        running.transitionTo(AgentRunStatus.CANCELLED);
        assertThat(running.status()).isEqualTo(AgentRunStatus.CANCELLED);
    }

    @Test
    void stepAttemptIsBoundedToThreeAndWaitingResumeDoesNotConsumeAnAttempt() {
        assertThatThrownBy(() -> step(AgentStepStatus.PENDING, 1, 4))
                .isInstanceOf(IllegalArgumentException.class);
        AgentStep waiting = step(AgentStepStatus.WAITING_USER, 2, 3);
        waiting.transitionTo(AgentStepStatus.PENDING);
        assertThat(waiting.attempt()).isEqualTo(2);
        assertThatThrownBy(() -> step(AgentStepStatus.FAILED, 3, 3).nextAttempt(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryableAndCancellableAreServerCalculated() {
        assertThat(run(AgentRunStatus.FAILED, true).retryable()).isTrue();
        assertThat(run(AgentRunStatus.FAILED, false).retryable()).isFalse();
        assertThat(run(AgentRunStatus.INTERRUPTED, true).retryable()).isTrue();
        assertThat(run(AgentRunStatus.SUCCEEDED, true).retryable()).isFalse();
        assertThat(run(AgentRunStatus.QUEUED, false).cancellable()).isTrue();
        assertThat(run(AgentRunStatus.WAITING_USER, false).cancellable()).isTrue();
        assertThat(run(AgentRunStatus.CANCELLED, false).cancellable()).isFalse();
    }

    @Test
    void safeProjectionsRejectCrossOriginRoutesAndInternalDetails() {
        assertThatThrownBy(() -> new RequiredUserAction(
                        RequiredUserActionType.INCREASE_BUDGET,
                        null,
                        "https://outside.example/settings",
                        "예산을 확인해 주세요."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SafeError(
                        "PROVIDER_FAILED", "SQLException at jdbc:postgresql://secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RequiredUserAction(
                        RequiredUserActionType.ENABLE_HIGH_QUALITY,
                        null,
                        "/settings/ai",
                        "고품질 설정을 확인해 주세요."))
                .isNotNull();
    }

    private static AgentRun run(AgentRunStatus status, boolean retryable) {
        UUID id = UUID.randomUUID();
        return AgentRun.rehydrate(
                id, UUID.randomUUID(), WorkflowType.JOB_ANALYSIS, status,
                null, id, 1, 7, null, retryable);
    }

    private static AgentStep step(AgentStepStatus status, int attempt, int maxAttempts) {
        return new AgentStep(
                UUID.randomUUID(), UUID.randomUUID(), "FIXTURE", null,
                attempt, maxAttempts, status);
    }

    static Stream<Arguments> allowedRunTransitions() {
        return Stream.of(
                Arguments.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING),
                Arguments.of(AgentRunStatus.QUEUED, AgentRunStatus.CANCELLED),
                Arguments.of(AgentRunStatus.RUNNING, AgentRunStatus.WAITING_USER),
                Arguments.of(AgentRunStatus.RUNNING, AgentRunStatus.SUCCEEDED),
                Arguments.of(AgentRunStatus.RUNNING, AgentRunStatus.FAILED),
                Arguments.of(AgentRunStatus.RUNNING, AgentRunStatus.CANCELLED),
                Arguments.of(AgentRunStatus.RUNNING, AgentRunStatus.INTERRUPTED),
                Arguments.of(AgentRunStatus.WAITING_USER, AgentRunStatus.QUEUED),
                Arguments.of(AgentRunStatus.WAITING_USER, AgentRunStatus.CANCELLED));
    }

    static Stream<Arguments> forbiddenRunTransitions() {
        return cartesianRun().filter(arguments -> {
            Object[] values = arguments.get();
            return !((AgentRunStatus) values[0]).canTransitionTo((AgentRunStatus) values[1]);
        });
    }

    static Stream<Arguments> cartesianRun() {
        return Arrays.stream(AgentRunStatus.values()).flatMap(source ->
                Arrays.stream(AgentRunStatus.values()).map(target -> Arguments.of(source, target)));
    }

    static Stream<Arguments> allowedStepTransitions() {
        return Stream.of(
                Arguments.of(AgentStepStatus.PENDING, AgentStepStatus.RUNNING),
                Arguments.of(AgentStepStatus.PENDING, AgentStepStatus.SKIPPED),
                Arguments.of(AgentStepStatus.PENDING, AgentStepStatus.REUSED),
                Arguments.of(AgentStepStatus.PENDING, AgentStepStatus.CANCELLED),
                Arguments.of(AgentStepStatus.RUNNING, AgentStepStatus.WAITING_USER),
                Arguments.of(AgentStepStatus.RUNNING, AgentStepStatus.SUCCEEDED),
                Arguments.of(AgentStepStatus.RUNNING, AgentStepStatus.FAILED),
                Arguments.of(AgentStepStatus.RUNNING, AgentStepStatus.CANCELLED),
                Arguments.of(AgentStepStatus.RUNNING, AgentStepStatus.INTERRUPTED),
                Arguments.of(AgentStepStatus.WAITING_USER, AgentStepStatus.PENDING),
                Arguments.of(AgentStepStatus.WAITING_USER, AgentStepStatus.CANCELLED));
    }

    static Stream<Arguments> forbiddenStepTransitions() {
        return Arrays.stream(AgentStepStatus.values()).flatMap(source ->
                Arrays.stream(AgentStepStatus.values())
                        .filter(target -> !source.canTransitionTo(target))
                        .map(target -> Arguments.of(source, target)));
    }
}
