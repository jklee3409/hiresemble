package com.hiresemble.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.agentrun.application.port.AgentRunCreationPort;
import com.hiresemble.agentrun.application.port.AgentRunResumePort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.model.BudgetPolicySnapshot;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.application.command.BudgetReservationRequest;
import com.hiresemble.agentrun.application.model.ClaimedAgentRun;
import com.hiresemble.agentrun.application.command.UsageRecordCommand;
import com.hiresemble.agentrun.application.port.UsageRecorderPort;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AgentRunBudgetResumeIntegrationTest extends AgentRunIntegrationSupport {

    @Autowired AgentRunStatePort statePort;
    @Autowired BudgetReservationPort budgetPort;
    @Autowired UsageRecorderPort usageRecorder;
    @Autowired AgentRunResumePort resumePort;
    @Autowired AgentRunCreationPort creationPort;

    @Test
    void concurrentReservationsCannotExceedUserDailyLimitAndRejectedRunRollsBack() throws Exception {
        UUID userId = seedUser("concurrent-budget@example.com");
        jdbcTemplate.update("UPDATE user_ai_preferences SET daily_budget_usd=0.200000 WHERE user_id=?", userId);
        long priceVersion = seedPriceVersion();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(
                    executor.submit(() -> concurrentLaunch(userId, priceVersion, ready, start)),
                    executor.submit(() -> concurrentLaunch(userId, priceVersion, ready, start)));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = tasks.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();
            assertThat(outcomes).filteredOn(WorkflowLaunchResult.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(BusinessException.class::isInstance).hasSize(1);
            assertThat(((BusinessException) outcomes.stream()
                    .filter(BusinessException.class::isInstance).findFirst().orElseThrow()).errorCode())
                    .isEqualTo(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=?", Long.class, userId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reserved_usd FROM ai_budget_ledgers WHERE user_id=?",
                BigDecimal.class, userId)).isEqualByComparingTo("0.200000");
    }

    @Test
    void insufficientBudgetCreatesNeitherRunNorReservation() {
        UUID userId = seedUser("insufficient-budget@example.com");
        jdbcTemplate.update("UPDATE user_ai_preferences SET daily_budget_usd=0.100000 WHERE user_id=?", userId);
        long priceVersion = seedPriceVersion();

        assertThatThrownBy(() -> launch(userId, new BigDecimal("0.200000"), priceVersion))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=?", Long.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_budget_reservations WHERE user_id=?", Long.class, userId)).isZero();
    }

    @Test
    void asyncRunAndSystemMaximumsApplyEvenWhenUserPreferenceIsHigher() {
        UUID asyncUser = seedUser("async-run-max@example.com");
        jdbcTemplate.update("UPDATE user_ai_preferences SET daily_budget_usd=9 WHERE user_id=?", asyncUser);
        long priceVersion = seedPriceVersion();
        assertThatThrownBy(() -> launch(
                asyncUser, new BigDecimal("0.300001"), priceVersion))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=?", Long.class, asyncUser)).isZero();

        UUID systemUser = seedUser("system-max@example.com");
        jdbcTemplate.update("UPDATE user_ai_preferences SET daily_budget_usd=9 WHERE user_id=?", systemUser);
        for (int index = 0; index < 6; index++) {
            launch(systemUser, new BigDecimal("0.300000"), priceVersion);
        }
        launch(systemUser, new BigDecimal("0.200000"), priceVersion);
        assertThatThrownBy(() -> launch(
                systemUser, new BigDecimal("0.000001"), priceVersion))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reserved_usd FROM ai_budget_ledgers WHERE user_id=?",
                BigDecimal.class, systemUser)).isEqualByComparingTo("2.000000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=?",
                Long.class, systemUser)).isEqualTo(7L);
    }

    @Test
    void waitingUserReleasesUnusedReserveAndSameRunResumeReservesRemainingCost() {
        UUID userId = seedUser("waiting-resume@example.com");
        long priceVersion = seedPriceVersion();
        UUID runId = launch(userId, new BigDecimal("0.200000"), priceVersion).agentRunId();
        ClaimedAgentRun claimed = statePort.claim(
                runId, "waiting-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        budgetPort.releaseUnused(userId, runId, Instant.now());
        AgentRunSnapshot waiting = statePort.transition(new AgentRunTransitionCommand(
                userId, runId, claimed.claimToken(), claimed.run().stateVersion(),
                AgentRunStatus.WAITING_USER, "LOAD_FIXTURE", 20, null,
                BigDecimal.ZERO, false,
                new RequiredUserAction(
                        RequiredUserActionType.INCREASE_BUDGET, null,
                        "/settings/ai", "Review the AI budget before resuming."),
                null, null, Instant.now()));

        AgentRunSnapshot resumed = resumePort.resume(
                userId, runId, waiting.stateVersion(), Instant.now());

        assertThat(resumed.id()).isEqualTo(runId);
        assertThat(resumed.status()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(resumed.runAttemptNo()).isEqualTo(1);
        assertThat(resumed.reservedCostUsd()).isEqualByComparingTo("0.200000");
        assertThat(jdbcTemplate.queryForList("""
                SELECT status FROM ai_budget_reservations
                WHERE agent_run_id=? ORDER BY created_at,id
                """, String.class, runId)).containsExactly("RELEASED", "RESERVED");
    }

    @Test
    void paidUsageSettlesAgainstPinnedPriceAndZeroCostUsageIsStillRecorded() {
        UUID userId = seedUser("usage-settlement@example.com");
        long priceVersion = seedPriceVersion();
        UUID priceItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_price_items (
                    id,price_version,provider_key,product_key,unit,unit_size,unit_price_usd,created_at
                ) VALUES (?,?,'FAKE','fixture-chat','CHAT_INPUT_TOKEN',1000,0.050000,now())
                """, priceItemId, priceVersion);
        UUID paidRunId = launch(userId, new BigDecimal("0.200000"), priceVersion).agentRunId();
        ClaimedAgentRun paidClaim = statePort.claim(
                paidRunId, "usage-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        usageRecorder.record(usage(
                userId, paidRunId, paidClaim.claimToken(), priceVersion, priceItemId,
                new BigDecimal("0.050000"), UsageType.CHAT));
        AgentRunSnapshot afterUsage = run(userId, paidRunId);
        budgetPort.settle(userId, paidRunId, afterUsage.actualCostUsd(), Instant.now());
        AgentRunSnapshot settled = run(userId, paidRunId);
        statePort.transition(new AgentRunTransitionCommand(
                userId, paidRunId, paidClaim.claimToken(), settled.stateVersion(),
                AgentRunStatus.SUCCEEDED, "APPLY_FIXTURE", 100, ModelTier.LOW_COST,
                settled.actualCostUsd(), false, null, null, null, Instant.now()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id=?",
                String.class, paidRunId)).isEqualTo("SETTLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT spent_usd FROM ai_budget_ledgers WHERE user_id=?",
                BigDecimal.class, userId)).isEqualByComparingTo("0.050000");

        UUID zeroRunId = launch(userId).agentRunId();
        ClaimedAgentRun zeroClaim = statePort.claim(
                zeroRunId, "zero-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        usageRecorder.record(usage(
                userId, zeroRunId, zeroClaim.claimToken(), null, null,
                BigDecimal.ZERO, UsageType.EMBEDDING));
        usageRecorder.record(usage(
                userId, zeroRunId, zeroClaim.claimToken(), null, null,
                BigDecimal.ZERO, UsageType.SEARCH));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_usage_records
                WHERE agent_run_id=? AND cost_usd=0 AND price_version IS NULL
                """, Long.class, zeroRunId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForList("""
                SELECT usage_type FROM ai_usage_records
                WHERE agent_run_id=? ORDER BY usage_type
                """, String.class, zeroRunId)).containsExactly("EMBEDDING", "SEARCH");
    }

    @Test
    void actualAboveOriginalReserveCannotSettleUntilAtomicTopUpSucceeds() {
        UUID userId = seedUser("budget-top-up@example.com");
        long priceVersion = seedPriceVersion();
        UUID priceItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_price_items (
                    id,price_version,provider_key,product_key,unit,unit_size,unit_price_usd,created_at
                ) VALUES (?,?,'FAKE','fixture-chat','CHAT_INPUT_TOKEN',1000,0.150000,now())
                """, priceItemId, priceVersion);
        UUID runId = launch(userId, new BigDecimal("0.100000"), priceVersion).agentRunId();
        ClaimedAgentRun claim = statePort.claim(
                runId, "top-up-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        usageRecorder.record(usage(
                userId, runId, claim.claimToken(), priceVersion, priceItemId,
                new BigDecimal("0.150000"), UsageType.CHAT));

        assertThatThrownBy(() -> budgetPort.settle(
                userId, runId, new BigDecimal("0.150000"), Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id=?",
                String.class, runId)).isEqualTo("RESERVED");

        assertThat(budgetPort.topUp(
                userId, runId, new BigDecimal("0.050000"), Instant.now()).reservedUsd())
                .isEqualByComparingTo("0.150000");
        assertThat(budgetPort.settle(
                userId, runId, new BigDecimal("0.150000"), Instant.now()).status())
                .isEqualTo("SETTLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT spent_usd FROM ai_budget_ledgers WHERE user_id=?",
                BigDecimal.class, userId)).isEqualByComparingTo("0.150000");

        UUID cappedRunId = launch(userId, new BigDecimal("0.290000"), priceVersion).agentRunId();
        assertThatThrownBy(() -> budgetPort.topUp(
                userId, cappedRunId, new BigDecimal("0.020000"), Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED));
    }

    @Test
    void budgetDateUsesAsiaSeoulAcrossUtcDayBoundary() {
        UUID userId = seedUser("budget-date@example.com");
        Instant beforeMidnight = Instant.parse("2026-07-19T14:59:59Z");
        Instant afterMidnight = Instant.parse("2026-07-19T15:00:00Z");
        reserveDirect(userId, UUID.randomUUID(), beforeMidnight);
        reserveDirect(userId, UUID.randomUUID(), afterMidnight);

        assertThat(jdbcTemplate.queryForList("""
                SELECT budget_date FROM ai_budget_ledgers
                WHERE user_id=? ORDER BY budget_date
                """, LocalDate.class, userId))
                .containsExactly(LocalDate.of(2026, 7, 19), LocalDate.of(2026, 7, 20));
    }

    private Object concurrentLaunch(
            UUID userId,
            long priceVersion,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return launch(userId, new BigDecimal("0.200000"), priceVersion);
        } catch (BusinessException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private UsageRecordCommand usage(
            UUID userId,
            UUID runId,
            UUID claimToken,
            Long priceVersion,
            UUID priceItemId,
            BigDecimal cost,
            UsageType usageType) {
        return new UsageRecordCommand(
                userId, runId, null, claimToken, "FIXTURE_" + usageType.name(), usageType,
                "FAKE", "fixture-chat", ModelTier.LOW_COST,
                usageType == UsageType.CHAT ? 1 : 0, 0,
                usageType == UsageType.CHAT ? 1 : 0,
                usageType == UsageType.EMBEDDING ? 1 : 0,
                usageType == UsageType.SEARCH ? 1 : 0,
                priceVersion, priceItemId, cost, 1, Instant.now());
    }

    private void reserveDirect(UUID userId, UUID runId, Instant requestedAt) {
        WorkflowLaunchCommand command = new WorkflowLaunchCommand(
                userId, WorkflowType.JOB_ANALYSIS, "fixture-v1", INPUT_HASH,
                objectMapper.createObjectNode().put("fixtureRef", runId.toString()),
                AiQualityMode.ECONOMY, BigDecimal.ZERO, null, null);
        BudgetPolicySnapshot policy = budgetPort.activePolicy(userId);
        creationPort.createQueued(runId, command, policy.version(), requestedAt);
        budgetPort.reserve(new BudgetReservationRequest(
                userId, runId, WorkflowType.JOB_ANALYSIS.name(),
                BigDecimal.ZERO, null, requestedAt));
    }
}
