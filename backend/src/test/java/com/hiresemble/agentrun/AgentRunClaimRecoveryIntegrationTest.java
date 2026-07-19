package com.hiresemble.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hiresemble.agentrun.application.AgentRunQueryPort;
import com.hiresemble.agentrun.application.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.AgentRunStatePort;
import com.hiresemble.agentrun.application.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.BudgetReservationPort;
import com.hiresemble.agentrun.application.ClaimedAgentRun;
import com.hiresemble.agentrun.application.StepStartCommand;
import com.hiresemble.agentrun.application.WorkflowExecutionPort;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.infrastructure.AgentRunDispatcher;
import com.hiresemble.agentrun.infrastructure.AgentRunReconciler;
import com.hiresemble.agentrun.infrastructure.AgentRuntimeProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AgentRunClaimRecoveryIntegrationTest extends AgentRunIntegrationSupport {

    @Autowired AgentRunStatePort statePort;
    @Autowired BudgetReservationPort budgetPort;
    @Autowired AgentRunQueryPort agentRunQueryPort;
    @Autowired AgentStepCheckpointPort stepPort;

    @Test
    void twoDispatchersCompetingForOneQueuedRunProduceExactlyOneClaim() throws Exception {
        UUID userId = seedUser("claim-race@example.com");
        UUID runId = launch(userId).agentRunId();
        Instant now = Instant.now();
        Callable<Optional<ClaimedAgentRun>> first =
                () -> statePort.claim(runId, "worker-a", now, Duration.ofSeconds(60));
        Callable<Optional<ClaimedAgentRun>> second =
                () -> statePort.claim(runId, "worker-b", now, Duration.ofSeconds(60));

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Optional<ClaimedAgentRun>>> results = executor.invokeAll(List.of(first, second));
            long claimedCount = results.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).filter(Optional::isPresent).count();
            assertThat(claimedCount).isEqualTo(1);
        }
        AgentRunSnapshot claimed = run(userId, runId);
        assertThat(claimed.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(claimed.claimToken()).isNotNull();
        assertThat(claimed.stateVersion()).isEqualTo(1);
    }

    @Test
    void queuedRowsSurviveRestartDiscoveryAndTerminalRowsAreNeverClaimedAgain() {
        UUID userId = seedUser("restart-queued@example.com");
        UUID runId = launch(userId).agentRunId();

        assertThat(statePort.findQueuedRunIds(10)).contains(runId);
        ClaimedAgentRun claimed = statePort.claim(
                runId, "new-dispatcher", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        budgetPort.releaseUnused(userId, runId, Instant.now());
        AgentRunSnapshot succeeded = statePort.transition(new AgentRunTransitionCommand(
                userId, runId, claimed.claimToken(), claimed.run().stateVersion(),
                AgentRunStatus.SUCCEEDED, "APPLY_FIXTURE", 100, null,
                BigDecimal.ZERO, false, null, null, null, Instant.now()));

        assertThat(succeeded.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(statePort.findQueuedRunIds(10)).doesNotContain(runId);
        assertThat(statePort.claim(
                runId, "another-dispatcher", Instant.now(), Duration.ofSeconds(60))).isEmpty();
    }

    @Test
    void heartbeatExtendsLeaseAndExpiredRunningIsInterruptedWithReserveReleased() {
        UUID userId = seedUser("stale-recovery@example.com");
        long priceVersion = seedPriceVersion();
        UUID runId = launch(userId, new BigDecimal("0.200000"), priceVersion).agentRunId();
        Instant started = Instant.now();
        ClaimedAgentRun claimed = statePort.claim(
                runId, "stale-worker", started, Duration.ofSeconds(30)).orElseThrow();

        AgentRunSnapshot heartbeat = statePort.heartbeat(
                userId, runId, claimed.claimToken(), started.plusSeconds(10), Duration.ofSeconds(30));
        long modelPolicyVersion = seedModelPolicy();
        stepPort.start(new StepStartCommand(
                userId, runId, claimed.claimToken(), "TRANSFORM_FIXTURE", null, 2,
                "FixtureAgent", 1, 3, "b".repeat(64),
                objectMapper.createObjectNode().put("fixtureRef", "safe-fixture"),
                "fixture-output-v1", modelPolicyVersion, "fixture-prompt-v1",
                AiQualityMode.ECONOMY, started.plusSeconds(11)));
        assertThat(heartbeat.stateVersion()).isEqualTo(claimed.run().stateVersion() + 1);
        assertThat(heartbeat.leaseExpiresAt().toEpochMilli())
                .isEqualTo(started.plusSeconds(40).toEpochMilli());
        Instant reconciliationTime = started.plusSeconds(90);
        assertThat(statePort.findExpiredRunningIds(reconciliationTime, 10)).contains(runId);

        AgentRunDispatchPort dispatchPort = mock(AgentRunDispatchPort.class);
        AgentRunReconciler fixedReconciler = new AgentRunReconciler(
                statePort, dispatchPort, new AgentRuntimeProperties(),
                Clock.fixed(reconciliationTime, ZoneOffset.UTC));
        fixedReconciler.reconcileOnce();

        AgentRunSnapshot interrupted = run(userId, runId);
        assertThat(interrupted.status()).isEqualTo(AgentRunStatus.INTERRUPTED);
        assertThat(interrupted.retryable()).isTrue();
        assertThat(interrupted.reservedCostUsd()).isEqualByComparingTo("0.000000");
        assertThat(interrupted.claimToken()).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_budget_reservations
                WHERE agent_run_id=? AND status='RELEASED'
                """, Long.class, runId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM agent_steps WHERE agent_run_id=?
                """, String.class, runId)).isEqualTo("INTERRUPTED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saturatedExecutorLeavesRunQueuedUntilLaterDatabaseScan() throws Exception {
        UUID userId = seedUser("queue-saturation@example.com");
        UUID runId = launch(userId).agentRunId();
        ThreadPoolTaskExecutor saturated = new ThreadPoolTaskExecutor();
        saturated.setCorePoolSize(1);
        saturated.setMaxPoolSize(1);
        saturated.setQueueCapacity(1);
        saturated.initialize();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        CountDownLatch blockersFinished = new CountDownLatch(2);
        Runnable blocker = () -> {
            firstStarted.countDown();
            try {
                releaseBlockers.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                blockersFinished.countDown();
            }
        };
        saturated.execute(blocker);
        assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
        saturated.execute(blocker);

        CountDownLatch executed = new CountDownLatch(1);
        WorkflowExecutionPort executionPort = ignored -> executed.countDown();
        ObjectProvider<WorkflowExecutionPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(executionPort);
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setWorkerId("saturation-worker");
        AgentRunDispatcher dispatcher = new AgentRunDispatcher(
                statePort, provider, saturated, properties, Clock.systemUTC());
        try {
            dispatcher.enqueue(runId);
            assertThat(run(userId, runId).status()).isEqualTo(AgentRunStatus.QUEUED);
            assertThat(statePort.findQueuedRunIds(10)).contains(runId);

            releaseBlockers.countDown();
            assertThat(blockersFinished.await(2, TimeUnit.SECONDS)).isTrue();
            dispatcher.scanQueued();

            assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(run(userId, runId).status()).isEqualTo(AgentRunStatus.RUNNING);
        } finally {
            releaseBlockers.countDown();
            saturated.shutdown();
        }
    }
}
