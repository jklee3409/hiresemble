package com.hiresemble.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunRetryPort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.port.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.application.model.ClaimedAgentRun;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.agentrun.application.model.ReusableStepSnapshot;
import com.hiresemble.agentrun.application.command.StepCheckpointCommand;
import com.hiresemble.agentrun.application.command.StepStartCommand;
import com.hiresemble.agentrun.application.command.UsageRecordCommand;
import com.hiresemble.agentrun.application.port.UsageRecorderPort;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import(AgentRunRetryCancelIntegrationTest.CompensationConfiguration.class)
class AgentRunRetryCancelIntegrationTest extends AgentRunIntegrationSupport {

    @Autowired AgentRunStatePort statePort;
    @Autowired BudgetReservationPort budgetPort;
    @Autowired AgentRunRetryPort retryPort;
    @Autowired AgentRunCancellationPort cancellationPort;
    @Autowired AgentStepCheckpointPort stepPort;
    @Autowired UsageRecorderPort usageRecorder;
    @Autowired RecordingCompensation compensation;

    @Test
    void retryIsANewRunWithDurableLineageAndSameKeyReplaysTheAcceptedResponse() {
        UUID userId = seedUser("retry-lineage@example.com");
        UUID predecessorId = failRetryableRun(userId);

        WorkflowLaunchResult first = retryPort.retry(userId, predecessorId, "retry-key-0001");
        WorkflowLaunchResult replay = retryPort.retry(userId, predecessorId, "retry-key-0001");
        WorkflowLaunchResult compatible = retryPort.retry(userId, predecessorId, "retry-key-0002");

        assertThat(first.agentRunId()).isNotEqualTo(predecessorId);
        assertThat(first.replayed()).isFalse();
        assertThat(replay.agentRunId()).isEqualTo(first.agentRunId());
        assertThat(replay.replayed()).isTrue();
        assertThat(compatible.agentRunId()).isEqualTo(first.agentRunId());
        AgentRunSnapshot predecessor = run(userId, predecessorId);
        AgentRunSnapshot successor = run(userId, first.agentRunId());
        assertThat(predecessor.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(successor.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(successor.retryOfRunId()).isEqualTo(predecessorId);
        assertThat(successor.rootRunId()).isEqualTo(predecessor.rootRunId());
        assertThat(successor.runAttemptNo()).isEqualTo(2);
        assertThat(successor.steps()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE retry_of_run_id=?",
                Long.class, predecessorId)).isEqualTo(1L);
    }

    @Test
    void concurrentDifferentRetryKeysStillCreateOneCompatibleSuccessor() throws Exception {
        UUID userId = seedUser("retry-race@example.com");
        UUID predecessorId = failRetryableRun(userId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(
                    executor.submit(() -> retryAfterGate(
                            userId, predecessorId, "concurrent-key-a", ready, start)),
                    executor.submit(() -> retryAfterGate(
                            userId, predecessorId, "concurrent-key-b", ready, start)));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            UUID first = futures.get(0).get().agentRunId();
            UUID second = futures.get(1).get().agentRunId();
            assertThat(first).isEqualTo(second);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE retry_of_run_id=?",
                Long.class, predecessorId)).isEqualTo(1L);
    }

    @Test
    void incompatiblePreexistingSuccessorReturnsDedicatedRetryConflict() {
        UUID userId = seedUser("retry-incompatible@example.com");
        UUID predecessorId = failRetryableRun(userId);
        WorkflowLaunchResult first = retryPort.retry(
                userId, predecessorId, "incompatible-key-01");
        jdbcTemplate.update(
                "UPDATE agent_runs SET requested_quality_mode='BALANCED' WHERE id=?",
                first.agentRunId());

        assertThatThrownBy(() -> retryPort.retry(
                userId, predecessorId, "incompatible-key-02"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE retry_of_run_id=?",
                Long.class, predecessorId)).isEqualTo(1L);
    }

    @Test
    void exactInputAndQualityCanReuseSucceededStepButChangedInputOrHighQualityCannot() {
        UUID userId = seedUser("step-reuse@example.com");
        long modelPolicy = seedModelPolicy();
        UUID firstRunId = launch(userId).agentRunId();
        ClaimedAgentRun firstClaim = statePort.claim(
                firstRunId, "reuse-source", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        String stepInputHash = "b".repeat(64);
        var source = stepPort.start(stepStart(
                userId, firstRunId, firstClaim.claimToken(), modelPolicy, stepInputHash,
                AiQualityMode.ECONOMY));
        stepPort.checkpoint(new StepCheckpointCommand(
                userId, firstRunId, source.id(), firstClaim.claimToken(),
                AgentStepStatus.SUCCEEDED, "c".repeat(64),
                objectMapper.createObjectNode()
                        .put("resultRef", "fixture-result")
                        .put("validated", true),
                ModelTier.LOW_COST, null, null, Instant.now()));

        ReusableStepSnapshot reusable = queryPort.findReusableStep(
                userId, "TRANSFORM_FIXTURE", null, stepInputHash, AiQualityMode.ECONOMY)
                .orElseThrow();
        assertThat(queryPort.findReusableStep(
                userId, "TRANSFORM_FIXTURE", null, "d".repeat(64), AiQualityMode.ECONOMY))
                .isEmpty();
        assertThat(queryPort.findReusableStep(
                userId, "TRANSFORM_FIXTURE", null, stepInputHash, AiQualityMode.HIGH_QUALITY))
                .isEmpty();

        UUID successorId = launch(userId).agentRunId();
        ClaimedAgentRun successorClaim = statePort.claim(
                successorId, "reuse-target", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        var reused = stepPort.reuse(stepStart(
                userId, successorId, successorClaim.claimToken(), modelPolicy, stepInputHash,
                AiQualityMode.ECONOMY), reusable);
        assertThat(reused.status()).isEqualTo(AgentStepStatus.REUSED);
        assertThat(reused.attempt()).isEqualTo(1);
    }

    @Test
    void cancellationUsesStateVersionAndCompensationParticipatesInTheTransaction() {
        compensation.reset();
        UUID userId = seedUser("cancel-compensation@example.com");
        UUID resourceId = UUID.randomUUID();
        UUID runId = launchWithResource(userId, resourceId).agentRunId();

        assertThatThrownBy(() -> cancellationPort.requestCancellation(
                userId, runId, 99, Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_VERSION_CONFLICT));
        assertThat(run(userId, runId).cancelRequestedAt()).isNull();

        compensation.failNext();
        assertThatThrownBy(() -> cancellationPort.requestCancellation(
                userId, runId, 0, Instant.now())).isInstanceOf(IllegalStateException.class);
        AgentRunSnapshot rolledBack = run(userId, runId);
        assertThat(rolledBack.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(rolledBack.cancelRequestedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id=?",
                String.class, runId)).isEqualTo("RESERVED");

        AgentRunSnapshot cancelled = cancellationPort.requestCancellation(
                userId, runId, rolledBack.stateVersion(), Instant.now());
        assertThat(cancelled.status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(cancelled.cancellable()).isFalse();
        assertThat(compensation.successfulCalls()).isEqualTo(1);
        assertThat(compensation.lastResourceId()).isEqualTo(resourceId);
        assertThatThrownBy(() -> cancellationPort.requestCancellation(
                userId, runId, cancelled.stateVersion(), Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CONFLICT));
    }

    @Test
    void runningCancellationIsARequestUntilWorkerSettlesAndCompletesIt() {
        compensation.reset();
        UUID userId = seedUser("cooperative-cancel@example.com");
        long priceVersion = seedPriceVersion();
        UUID priceItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_price_items (
                    id,price_version,provider_key,product_key,unit,unit_size,unit_price_usd,created_at
                ) VALUES (?,?,'FAKE','fixture-chat','CHAT_INPUT_TOKEN',1000,0.050000,now())
                """, priceItemId, priceVersion);
        UUID runId = launch(userId, new BigDecimal("0.200000"), priceVersion).agentRunId();
        ClaimedAgentRun claimed = statePort.claim(
                runId, "cancel-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        usageRecorder.record(new UsageRecordCommand(
                userId, runId, null, claimed.claimToken(), "FIXTURE_CHAT", UsageType.CHAT,
                "FAKE", "fixture-chat", ModelTier.LOW_COST,
                10, 0, 5, 0, 0, priceVersion, priceItemId,
                new BigDecimal("0.050000"), 1, Instant.now()));
        AgentRunSnapshot afterUsage = run(userId, runId);

        AgentRunSnapshot requested = cancellationPort.requestCancellation(
                userId, runId, afterUsage.stateVersion(), Instant.now());
        assertThat(requested.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(requested.cancelRequestedAt()).isNotNull();
        assertThat(requested.cancellable()).isFalse();
        assertThat(statePort.isCancellationRequested(userId, runId, claimed.claimToken())).isTrue();

        AgentRunSnapshot cancelled = cancellationPort.completeCancellation(
                userId, runId, claimed.claimToken(), Instant.now());
        assertThat(cancelled.status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(cancelled.reservedCostUsd()).isEqualByComparingTo("0.000000");
        assertThat(cancelled.actualCostUsd()).isEqualByComparingTo("0.050000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id=?",
                String.class, runId)).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT settled_usd FROM ai_budget_reservations WHERE agent_run_id=?",
                BigDecimal.class, runId)).isEqualByComparingTo("0.050000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT spent_usd FROM ai_budget_ledgers WHERE user_id=?",
                BigDecimal.class, userId)).isEqualByComparingTo("0.050000");
    }

    private UUID failRetryableRun(UUID userId) {
        UUID runId = launch(userId).agentRunId();
        ClaimedAgentRun claimed = statePort.claim(
                runId, "failure-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        budgetPort.releaseUnused(userId, runId, Instant.now());
        statePort.transition(new AgentRunTransitionCommand(
                userId, runId, claimed.claimToken(), claimed.run().stateVersion(),
                AgentRunStatus.FAILED, "TRANSFORM_FIXTURE", 50, ModelTier.LOW_COST,
                BigDecimal.ZERO, true, null,
                new SafeError("FIXTURE_TRANSIENT_FAILURE", "The fixture run could not finish."),
                null, Instant.now()));
        return runId;
    }

    private WorkflowLaunchResult retryAfterGate(
            UUID userId,
            UUID predecessorId,
            String key,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return retryPort.retry(userId, predecessorId, key);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private StepStartCommand stepStart(
            UUID userId,
            UUID runId,
            UUID claimToken,
            long modelPolicy,
            String inputHash,
            AiQualityMode qualityMode) {
        return new StepStartCommand(
                userId, runId, claimToken, "TRANSFORM_FIXTURE", null, 2,
                "FixtureAgent", 1, 3, inputHash,
                objectMapper.createObjectNode().put("fixtureRef", "safe-fixture"),
                "fixture-output-v1", modelPolicy, "fixture-prompt-v1", qualityMode,
                Instant.now());
    }

    private WorkflowLaunchResult launchWithResource(UUID userId, UUID resourceId) {
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                userId, WorkflowType.JOB_ANALYSIS, "fixture-v1", INPUT_HASH,
                objectMapper.createObjectNode().put("fixtureRef", resourceId.toString()),
                AiQualityMode.ECONOMY, BigDecimal.ZERO, null,
                new ResourceReference("FIXTURE_RESOURCE", resourceId, "Test fixture")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CompensationConfiguration {
        @Bean
        RecordingCompensation recordingCompensation() {
            return new RecordingCompensation();
        }
    }

    static final class RecordingCompensation implements ResourceCompensationPort {
        private final AtomicBoolean fail = new AtomicBoolean();
        private final AtomicInteger successfulCalls = new AtomicInteger();
        private volatile UUID lastResourceId;

        @Override
        public void compensate(
                UUID userId,
                UUID agentRunId,
                String resourceType,
                UUID resourceId) {
            if (fail.compareAndSet(true, false)) {
                throw new IllegalStateException("injected compensation failure");
            }
            lastResourceId = resourceId;
            successfulCalls.incrementAndGet();
        }

        void failNext() {
            fail.set(true);
        }

        int successfulCalls() {
            return successfulCalls.get();
        }

        UUID lastResourceId() {
            return lastResourceId;
        }

        void reset() {
            fail.set(false);
            successfulCalls.set(0);
            lastResourceId = null;
        }
    }
}
