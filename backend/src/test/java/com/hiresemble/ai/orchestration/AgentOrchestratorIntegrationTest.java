package com.hiresemble.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunLeaseHeartbeatPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunResumePort;
import com.hiresemble.agentrun.application.port.AgentRunRetryPort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.application.model.ClaimedAgentRun;
import com.hiresemble.agentrun.application.port.DomainResultApplyPort;
import com.hiresemble.agentrun.application.command.DomainResultCommand;
import com.hiresemble.agentrun.application.port.UsageRecorderPort;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.agentrun.infrastructure.worker.AgentRunReconciler;
import com.hiresemble.agentrun.infrastructure.config.AgentRuntimeProperties;
import com.hiresemble.agentrun.infrastructure.scheduling.ScheduledAgentRunLeaseHeartbeat;
import com.hiresemble.ai.budget.BudgetGuard;
import com.hiresemble.ai.context.ContextBuilder;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.context.ContextBuilder.ResourceSnapshotRef;
import com.hiresemble.ai.context.ContextBuilder.TruncationSummary;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.infrastructure.DisabledAiGateways;
import com.hiresemble.ai.model.ModelRouter.ModelPolicy;
import com.hiresemble.ai.model.PolicyModelRouter;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.prompt.PromptRegistry.PromptKey;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.ai.workflow.WorkflowRegistry.WorkflowDefinition;
import com.hiresemble.ai.workflow.TerminalPartialPolicy;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainApplyPlan;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentOrchestratorIntegrationTest extends PostgresIntegrationTest {

    private static final String WORKFLOW_VERSION = "p3-fixture-v1";
    private static final String FAN_OUT_WORKFLOW_VERSION =
            "p7-fan-out-fixture-v1";
    private static final String INPUT_HASH = "a".repeat(64);
    private static final String OUTPUT_HASH = "b".repeat(64);

    @Autowired private WorkflowLauncher workflowLauncher;
    @Autowired private AgentRunQueryPort runQueryPort;
    @Autowired private AgentRunStatePort runStatePort;
    @Autowired private AgentStepCheckpointPort stepCheckpointPort;
    @Autowired private UsageRecorderPort usageRecorderPort;
    @Autowired private AgentRunCancellationPort cancellationPort;
    @Autowired private AgentRunLeaseHeartbeatPort leaseHeartbeatPort;
    @Autowired private AgentRunResumePort resumePort;
    @Autowired private AgentRunRetryPort retryPort;
    @Autowired private BudgetReservationPort budgetReservationPort;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    private long modelPolicyVersion;
    private UUID userId;
    private UUID resourceId;
    private MutableContextBuilder contextBuilder;
    private FakeChatGateway chatGateway;
    private FakeDomainApply domainApply;

    @BeforeEach
    void setUpFixture() {
        modelPolicyVersion = seedModelPolicy();
        userId = seedUser();
        resourceId = UUID.randomUUID();
        contextBuilder = new MutableContextBuilder(modelPolicyVersion, resourceId);
        chatGateway = new FakeChatGateway();
        domainApply = new FakeDomainApply(userId, resourceId, jdbcTemplate);
    }

    @Test
    void fixedThreeStepWorkflowSucceedsAndPersistsInjectedPolicyAndZeroCostUsage() {
        WorkflowLaunchResult launch = launch(AiQualityMode.ECONOMY, INPUT_HASH);

        execute(launch.agentRunId(), fixtureOrchestrator(false, false));

        AgentRunSnapshot run = run(launch.agentRunId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run.resourceType()).isNull();
        assertThat(run.resourceId()).isNull();
        assertThat(run.progressPercent()).isEqualTo(100);
        assertThat(run.steps()).extracting(step -> step.stepKey() + ":" + step.status())
                .containsExactly(
                        "LOAD_FIXTURE:SUCCEEDED",
                        "TRANSFORM_FIXTURE:SUCCEEDED",
                        "APPLY_FIXTURE:SUCCEEDED");
        assertThat(domainApply.appliedCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_usage_records WHERE agent_run_id = ? AND usage_type = 'CHAT' AND cost_usd = 0",
                Long.class, run.id())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT DISTINCT model_policy_version FROM agent_steps WHERE agent_run_id = ?",
                Long.class, run.id())).containsExactly(modelPolicyVersion);
    }

    @Test
    void seededRetrySuccessAndResultReferenceRemainInTerminalPartialResult()
            throws Exception {
        WorkflowLaunchResult launch =
                launch(AiQualityMode.ECONOMY, INPUT_HASH);
        UUID answerVersionId = UUID.randomUUID();
        PartialResult seeded = new PartialResult(
                List.of("previous-question"),
                List.of(),
                List.of(new ResourceReference(
                        "COVER_LETTER_ANSWER_VERSION",
                        answerVersionId,
                        "자기소개서 답변 버전")));
        jdbcTemplate.update(
                """
                UPDATE agent_runs
                SET partial_result_json = CAST(? AS jsonb)
                WHERE user_id = ? AND id = ?
                """,
                objectMapper.writeValueAsString(seeded),
                userId,
                launch.agentRunId());

        execute(launch.agentRunId(), fixtureOrchestrator(false, false));

        AgentRunSnapshot completed = run(launch.agentRunId());
        assertThat(completed.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(completed.partialResult().succeededScopeKeys())
                .containsExactly("previous-question");
        assertThat(completed.partialResult().failedScopeKeys()).isEmpty();
        assertThat(completed.partialResult().resultRefs())
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.resourceType())
                            .isEqualTo("COVER_LETTER_ANSWER_VERSION");
                    assertThat(reference.resourceId())
                            .isEqualTo(answerVersionId);
                });
    }

    @Test
    void questionLocalDomainFailureKeepsSiblingResultAndTerminatesPartialFailure() {
        UUID succeededQuestionId = UUID.randomUUID();
        UUID failedQuestionId = UUID.randomUUID();
        UUID answerVersionId = UUID.randomUUID();
        WorkflowLaunchResult launch = workflowLauncher.launch(
                new WorkflowLaunchCommand(
                        userId,
                        WorkflowType.COVER_LETTER_GENERATION,
                        FAN_OUT_WORKFLOW_VERSION,
                        "9".repeat(64),
                        objectMapper
                                .createObjectNode()
                                .put("fixtureRef", "p7-fan-out"),
                        AiQualityMode.BALANCED,
                        BigDecimal.ZERO.setScale(6),
                        null,
                        null));

        execute(
                launch.agentRunId(),
                fanOutOrchestrator(
                        succeededQuestionId,
                        failedQuestionId,
                        answerVersionId));

        AgentRunSnapshot failed = run(launch.agentRunId());
        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.safeError().code())
                .isEqualTo("COVER_LETTER_GENERATION_PARTIAL_FAILURE");
        assertThat(failed.retryable()).isFalse();
        assertThat(failed.partialResult().succeededScopeKeys())
                .containsExactly(succeededQuestionId.toString());
        assertThat(failed.partialResult().failedScopeKeys())
                .containsExactly(failedQuestionId.toString());
        assertThat(failed.partialResult().resultRefs())
                .singleElement()
                .satisfies(reference -> assertThat(reference.resourceId())
                        .isEqualTo(answerVersionId));
        assertThat(failed.steps())
                .extracting(step -> step.scopeKey() + ":" + step.status())
                .containsExactlyInAnyOrder(
                        succeededQuestionId + ":SUCCEEDED",
                        failedQuestionId + ":FAILED");
    }

    @Test
    void terminalPartialPolicyCanCompleteFailedScopesAsSuccessfulPartialResult() {
        UUID succeededQuestionId = UUID.randomUUID();
        UUID failedQuestionId = UUID.randomUUID();
        UUID answerVersionId = UUID.randomUUID();
        WorkflowLaunchResult launch = workflowLauncher.launch(
                new WorkflowLaunchCommand(
                        userId,
                        WorkflowType.COVER_LETTER_GENERATION,
                        FAN_OUT_WORKFLOW_VERSION,
                        "8".repeat(64),
                        objectMapper.createObjectNode().put("fixtureRef", "partial-success"),
                        AiQualityMode.BALANCED,
                        BigDecimal.ZERO.setScale(6),
                        null,
                        null));

        execute(
                launch.agentRunId(),
                fanOutOrchestrator(
                        succeededQuestionId,
                        failedQuestionId,
                        answerVersionId,
                        TerminalPartialPolicy.succeed()));

        AgentRunSnapshot completed = run(launch.agentRunId());
        assertThat(completed.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(completed.safeError()).isNull();
        assertThat(completed.retryable()).isFalse();
        assertThat(completed.partialResult().failedScopeKeys())
                .containsExactly(failedQuestionId.toString());
    }

    @Test
    void transientFailureRetriesWithinThreeAttemptsAndExhaustionFailsSafely() {
        chatGateway.failuresBeforeSuccess.set(1);
        UUID successfulRun = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        execute(successfulRun, fixtureOrchestrator(false, false));

        assertThat(run(successfulRun).status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run(successfulRun).steps().stream()
                .filter(step -> step.stepKey().equals("TRANSFORM_FIXTURE"))
                .map(AgentStepSnapshotView::from))
                .containsExactly(
                        new AgentStepSnapshotView(1, AgentStepStatus.FAILED),
                        new AgentStepSnapshotView(2, AgentStepStatus.SUCCEEDED));

        chatGateway.failuresBeforeSuccess.set(Integer.MAX_VALUE);
        UUID failedRun = launch(AiQualityMode.ECONOMY, "c".repeat(64)).agentRunId();
        execute(failedRun, fixtureOrchestrator(false, false));

        AgentRunSnapshot failed = run(failedRun);
        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.retryable()).isTrue();
        assertThat(failed.safeError().message()).doesNotContain("exception", "provider-response", "fixture-secret");
        assertThat(failed.steps().stream().filter(step -> step.stepKey().equals("TRANSFORM_FIXTURE")))
                .hasSize(3)
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(AgentStepStatus.FAILED));
    }

    @Test
    void structuredOutputFailureConsumesAttemptAndPromotesOnlyEconomyLowCost() {
        chatGateway.invalidResponsesBeforeSuccess.set(1);
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();

        execute(runId, fixtureOrchestrator(false, false));

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(chatGateway.products).containsExactly("low", "balanced");
        assertThat(chatGateway.instructions.get(1))
                .contains("outside the supplied context")
                .doesNotContain("fixture-secret");
        assertThat(run(runId).steps().stream()
                .filter(step -> step.stepKey().equals("TRANSFORM_FIXTURE"))
                .map(step -> step.attempt() + ":" + step.status()))
                .containsExactly("1:FAILED", "2:SUCCEEDED");
    }

    @Test
    void semanticThenNetworkKeepsCorrectionAndSucceedsWithinIndependentLimits() {
        chatGateway.script(
                GatewayOutcome.SEMANTIC_INVALID,
                GatewayOutcome.NETWORK,
                GatewayOutcome.VALID);
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();

        execute(runId, fixtureOrchestrator(false, false));

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(chatGateway.instructions).hasSize(3);
        assertThat(chatGateway.instructions.get(1)).contains("outside the supplied context");
        assertThat(chatGateway.instructions.get(2)).contains("outside the supplied context");
        assertThat(chatGateway.products).containsExactly("low", "balanced", "balanced");
        assertThat(usageCount(runId)).isEqualTo(3L);
    }

    @Test
    void networkThenSemanticAddsCorrectionOnlyToFollowingSemanticAttempt() {
        chatGateway.script(
                GatewayOutcome.NETWORK,
                GatewayOutcome.SEMANTIC_INVALID,
                GatewayOutcome.VALID);
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();

        execute(runId, fixtureOrchestrator(false, false));

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(chatGateway.instructions).hasSize(3);
        assertThat(chatGateway.instructions.get(1))
                .doesNotContain("Correction for this bounded retry");
        assertThat(chatGateway.instructions.get(2)).contains("outside the supplied context");
        assertThat(usageCount(runId)).isEqualTo(3L);
    }

    @Test
    void semanticThenTransportExhaustionTerminatesWithoutGuidanceLossOrExtraCall() {
        chatGateway.script(
                GatewayOutcome.SEMANTIC_INVALID,
                GatewayOutcome.NETWORK,
                GatewayOutcome.NETWORK,
                GatewayOutcome.VALID);
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();

        execute(runId, fixtureOrchestrator(false, false));

        AgentRunSnapshot failed = run(runId);
        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.steps().stream()
                        .filter(step -> step.stepKey().equals("TRANSFORM_FIXTURE")))
                .hasSize(3)
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(AgentStepStatus.FAILED));
        assertThat(chatGateway.instructions).hasSize(3);
        assertThat(chatGateway.instructions.subList(1, 3))
                .allSatisfy(value -> assertThat(value).contains("outside the supplied context"));
        assertThat(usageCount(runId)).isEqualTo(3L);
    }

    @Test
    void continuousTransportFailuresUseHardCallCapAndRecordEachCallOnce() {
        chatGateway.script(
                GatewayOutcome.NETWORK,
                GatewayOutcome.NETWORK,
                GatewayOutcome.NETWORK,
                GatewayOutcome.VALID);
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();

        execute(runId, fixtureOrchestrator(false, false));

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(chatGateway.instructions).hasSize(3);
        assertThat(usageCount(runId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(DISTINCT provider_call_id) FROM ai_usage_records WHERE agent_run_id = ?",
                        Long.class,
                        runId))
                .isEqualTo(3L);
    }

    @Test
    void structuredFailureExhaustionAllowsTerminalNewRunRetryWithStepAttemptsReset() {
        chatGateway.invalidResponsesBeforeSuccess.set(Integer.MAX_VALUE);
        AgentOrchestrator orchestrator = fixtureOrchestrator(false, false);
        UUID predecessorId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        execute(predecessorId, orchestrator);

        AgentRunSnapshot predecessor = run(predecessorId);
        assertThat(predecessor.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(predecessor.safeError().code()).isEqualTo("AI_SO_WORKFLOW_CONTEXT_INVALID");
        assertThat(predecessor.steps().stream()
                .filter(step -> step.stepKey().equals("TRANSFORM_FIXTURE"))).hasSize(2);

        chatGateway.invalidResponsesBeforeSuccess.set(0);
        UUID successorId = retryPort.retry(userId, predecessorId, "fixture-retry-0001").agentRunId();
        execute(successorId, orchestrator);

        AgentRunSnapshot successor = run(successorId);
        assertThat(successor.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(successor.retryOfRunId()).isEqualTo(predecessorId);
        assertThat(successor.rootRunId()).isEqualTo(predecessor.rootRunId());
        assertThat(successor.runAttemptNo()).isEqualTo(2);
        assertThat(successor.steps()).allSatisfy(step -> assertThat(step.attempt()).isEqualTo(1));
        assertThat(successor.steps().getFirst().status()).isEqualTo(AgentStepStatus.REUSED);
    }

    @Test
    void waitingUserResumesSameRunAndSameStepAttempt() {
        contextBuilder.waiting.set(true);
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        AgentOrchestrator orchestrator = fixtureOrchestrator(true, false);

        execute(runId, orchestrator);
        AgentRunSnapshot waiting = run(runId);
        assertThat(waiting.status()).isEqualTo(AgentRunStatus.WAITING_USER);
        assertThat(waiting.requiredUserAction().route()).startsWith("/agent-runs/");
        assertThat(waiting.reservedCostUsd()).isEqualByComparingTo("0");

        contextBuilder.waiting.set(false);
        resumePort.resume(userId, runId, waiting.stateVersion(), Instant.now());
        execute(runId, orchestrator);

        AgentRunSnapshot completed = run(runId);
        assertThat(completed.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(completed.runAttemptNo()).isEqualTo(1);
        assertThat(completed.steps().stream()
                .filter(step -> step.stepKey().equals("TRANSFORM_FIXTURE")))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.attempt()).isEqualTo(1);
                    assertThat(step.status()).isEqualTo(AgentStepStatus.SUCCEEDED);
                });
    }

    @Test
    void sameInputReusesAllStepsChangedInputAndHighQualityDoNotReuse() {
        UUID first = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        AgentOrchestrator orchestrator = fixtureOrchestrator(false, false);
        execute(first, orchestrator);

        UUID same = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        execute(same, orchestrator);
        List<String> firstHashes = jdbcTemplate.queryForList(
                "SELECT step_key || ':' || input_hash FROM agent_steps WHERE agent_run_id = ? ORDER BY step_order",
                String.class, first);
        List<String> sameHashes = jdbcTemplate.queryForList(
                "SELECT step_key || ':' || input_hash FROM agent_steps WHERE agent_run_id = ? ORDER BY step_order",
                String.class, same);
        assertThat(sameHashes).as("first hashes: %s", firstHashes).isEqualTo(firstHashes);
        assertThat(run(same).steps()).allSatisfy(step ->
                assertThat(step.status()).isEqualTo(AgentStepStatus.REUSED));
        assertThat(domainApply.applyCallCount()).isEqualTo(2);
        assertThat(domainApply.appliedCount()).isEqualTo(1);

        UUID changed = launch(AiQualityMode.ECONOMY, "d".repeat(64)).agentRunId();
        execute(changed, orchestrator);
        assertThat(run(changed).steps()).allSatisfy(step ->
                assertThat(step.status()).isEqualTo(AgentStepStatus.SUCCEEDED));

        jdbcTemplate.update("UPDATE user_ai_preferences SET high_quality_enabled = true WHERE user_id = ?", userId);
        contextBuilder.highQualityEnabled.set(true);
        UUID highQuality = launch(AiQualityMode.HIGH_QUALITY, INPUT_HASH).agentRunId();
        execute(highQuality, orchestrator);
        assertThat(run(highQuality).steps()).allSatisfy(step ->
                assertThat(step.status()).isEqualTo(AgentStepStatus.SUCCEEDED));
    }

    @Test
    void freshApplyFailureRollsBackSucceededCheckpointAndDomainMutation() {
        domainApply.failAfterDomainWrite();
        UUID runId = launch(AiQualityMode.ECONOMY, "e".repeat(64)).agentRunId();

        execute(runId, fixtureOrchestrator(false, false));

        AgentRunSnapshot failed = run(runId);
        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.safeError().code()).isEqualTo("AI_DOMAIN_APPLY_FAILED");
        assertThat(failed.steps())
                .extracting(step -> step.stepKey() + ":" + step.status())
                .containsExactly(
                        "LOAD_FIXTURE:SUCCEEDED",
                        "TRANSFORM_FIXTURE:SUCCEEDED",
                        "APPLY_FIXTURE:FAILED");
        assertThat(domainApply.appliedCount()).isZero();
        assertThat(domainApply.applyCallCount()).isEqualTo(1);
        assertThat(domainApply.failedMarkerCount()).isZero();
    }

    @Test
    void reusedApplyFailureRollsBackReusedCheckpointAndDomainMutation() {
        String canonicalInputHash = "f".repeat(64);
        AgentOrchestrator orchestrator = fixtureOrchestrator(false, false);
        UUID firstRunId = launch(AiQualityMode.ECONOMY, canonicalInputHash).agentRunId();
        execute(firstRunId, orchestrator);
        assertThat(run(firstRunId).status()).isEqualTo(AgentRunStatus.SUCCEEDED);

        domainApply.failAfterDomainWrite();
        UUID reusedRunId = launch(AiQualityMode.ECONOMY, canonicalInputHash).agentRunId();
        execute(reusedRunId, orchestrator);

        AgentRunSnapshot failed = run(reusedRunId);
        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.safeError().code()).isEqualTo("AI_DOMAIN_APPLY_FAILED");
        assertThat(failed.steps())
                .extracting(step -> step.stepKey() + ":" + step.status())
                .containsExactly(
                        "LOAD_FIXTURE:REUSED",
                        "TRANSFORM_FIXTURE:REUSED",
                        "APPLY_FIXTURE:FAILED");
        assertThat(domainApply.appliedCount()).isEqualTo(1);
        assertThat(domainApply.applyCallCount()).isEqualTo(2);
        assertThat(domainApply.failedMarkerCount()).isZero();
    }

    @Test
    void restartSkipsCommittedApplyOnlyAfterCheckpointAndDomainMutationCommitTogether() {
        UUID runId = launch(AiQualityMode.ECONOMY, "1".repeat(64)).agentRunId();
        ClaimedAgentRun claim = claim(runId);
        AtomicInteger completionCount = new AtomicInteger();
        SpringStepCompletionTransaction delegate =
                new SpringStepCompletionTransaction(transactionManager);
        StepCompletionTransaction crashAfterPersist = work -> {
            delegate.execute(work);
            if (completionCount.incrementAndGet() == 3) {
                throw new SimulatedProcessCrash();
            }
        };

        AgentOrchestrator crashing = fixtureOrchestrator(
                false,
                false,
                () -> {},
                leaseHeartbeatPort,
                crashAfterPersist);
        assertThatThrownBy(() -> crashing.execute(claim))
                .isInstanceOf(SimulatedProcessCrash.class);

        AgentRunSnapshot interrupted = run(runId);
        assertThat(interrupted.status()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(interrupted.steps())
                .extracting(step -> step.stepKey() + ":" + step.status())
                .containsExactly(
                        "LOAD_FIXTURE:SUCCEEDED",
                        "TRANSFORM_FIXTURE:SUCCEEDED",
                        "APPLY_FIXTURE:SUCCEEDED");
        assertThat(domainApply.appliedCount()).isEqualTo(1);
        assertThat(domainApply.applyCallCount()).isEqualTo(1);
        assertThat(domainApply.committedMarkerCount()).isEqualTo(1);

        fixtureOrchestrator(false, false).execute(claim);

        AgentRunSnapshot restarted = run(runId);
        assertThat(restarted.status())
                .as(restarted.toString())
                .isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(domainApply.appliedCount()).isEqualTo(1);
        assertThat(domainApply.applyCallCount()).isEqualTo(1);
        assertThat(domainApply.committedMarkerCount()).isEqualTo(1);
    }

    @Test
    void cancellationDuringGatewayRecordsUsageButNeverAppliesResult() throws Exception {
        chatGateway.block = true;
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        AgentOrchestrator orchestrator = fixtureOrchestrator(false, false);
        ClaimedAgentRun claim = claim(runId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> orchestrator.execute(claim));
            assertThat(chatGateway.entered.await(10, TimeUnit.SECONDS)).isTrue();
            AgentRunSnapshot running = run(runId);
            cancellationPort.requestCancellation(userId, runId, running.stateVersion(), Instant.now());
            chatGateway.release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            chatGateway.release.countDown();
            executor.shutdownNow();
        }

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(domainApply.appliedCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_usage_records WHERE agent_run_id = ?", Long.class, runId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, runId)).isEqualTo("RELEASED");
    }

    @Test
    void periodicHeartbeatKeepsBlockedGatewayRunOutOfStaleReconciliation() throws Exception {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setHeartbeatInterval(Duration.ofMillis(25));
        properties.setLeaseDuration(Duration.ofMillis(200));
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-agent-lease-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        AgentRunLeaseHeartbeatPort shortLeaseHeartbeat = new ScheduledAgentRunLeaseHeartbeat(
                runStatePort, scheduler, properties, Clock.systemUTC());

        chatGateway.block = true;
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        Instant claimedAt = Instant.now();
        ClaimedAgentRun claim = runStatePort.claim(
                runId, "short-lease-worker", claimedAt, properties.getLeaseDuration()).orElseThrow();
        AgentOrchestrator orchestrator = fixtureOrchestrator(
                false, false, () -> {}, shortLeaseHeartbeat);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> execution = executor.submit(() -> orchestrator.execute(claim));
        try {
            assertThat(chatGateway.entered.await(10, TimeUnit.SECONDS)).isTrue();
            AgentRunSnapshot firstBlockedSnapshot = run(runId);

            Thread.sleep(450);
            Instant reconciliationTime = Instant.now();
            AgentRunDispatchPort noOpDispatch = new AgentRunDispatchPort() {
                @Override
                public void enqueue(UUID ignored) {}

                @Override
                public void scanQueued() {}
            };
            AgentRunReconciler reconciler = new AgentRunReconciler(
                    runStatePort,
                    noOpDispatch,
                    properties,
                    Clock.fixed(reconciliationTime, ZoneOffset.UTC));
            reconciler.reconcileOnce();

            AgentRunSnapshot afterReconciliation = run(runId);
            assertThat(afterReconciliation.status()).isEqualTo(AgentRunStatus.RUNNING);
            assertThat(afterReconciliation.heartbeatAt())
                    .isAfter(firstBlockedSnapshot.heartbeatAt());
            assertThat(afterReconciliation.leaseExpiresAt()).isAfter(reconciliationTime);

            chatGateway.release.countDown();
            execution.get(10, TimeUnit.SECONDS);
        } finally {
            chatGateway.release.countDown();
            executor.shutdownNow();
            scheduler.shutdown();
        }

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    }

    @Test
    void cancellationRequestedDuringProjectionIsObservedImmediatelyBeforeApply() {
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        AtomicBoolean requestBeforeApply = new AtomicBoolean(true);
        AgentOrchestrator orchestrator = fixtureOrchestrator(false, false, () -> {
            if (requestBeforeApply.compareAndSet(true, false)) {
                AgentRunSnapshot running = run(runId);
                cancellationPort.requestCancellation(userId, runId, running.stateVersion(), Instant.now());
            }
        });

        execute(runId, orchestrator);

        assertThat(run(runId).status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(domainApply.appliedCount()).isZero();
    }

    @Test
    void interruptedWorkerTerminatesRunAsRetryableInterruptedAndReleasesReserve() {
        UUID runId = launch(AiQualityMode.ECONOMY, INPUT_HASH).agentRunId();
        ClaimedAgentRun claim = claim(runId);
        try {
            Thread.currentThread().interrupt();
            fixtureOrchestrator(false, false).execute(claim);
        } finally {
            Thread.interrupted();
        }

        AgentRunSnapshot interrupted = run(runId);
        assertThat(interrupted.status()).isEqualTo(AgentRunStatus.INTERRUPTED);
        assertThat(interrupted.retryable()).isTrue();
        assertThat(interrupted.safeError().code()).isEqualTo("AI_WORKER_INTERRUPTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, runId)).isEqualTo("RELEASED");
    }

    private AgentOrchestrator fixtureOrchestrator(boolean waitingCapable, boolean unused) {
        return fixtureOrchestrator(waitingCapable, unused, () -> {});
    }

    private AgentOrchestrator fixtureOrchestrator(
            boolean waitingCapable, boolean unused, Runnable beforeApplyProjection) {
        return fixtureOrchestrator(
                waitingCapable, unused, beforeApplyProjection, leaseHeartbeatPort);
    }

    private AgentOrchestrator fixtureOrchestrator(
            boolean waitingCapable,
            boolean unused,
            Runnable beforeApplyProjection,
            AgentRunLeaseHeartbeatPort heartbeatPort) {
        return fixtureOrchestrator(
                waitingCapable,
                unused,
                beforeApplyProjection,
                heartbeatPort,
                new SpringStepCompletionTransaction(transactionManager));
    }

    private AgentOrchestrator fixtureOrchestrator(
            boolean waitingCapable,
            boolean unused,
            Runnable beforeApplyProjection,
            AgentRunLeaseHeartbeatPort heartbeatPort,
            StepCompletionTransaction completionTransaction) {
        WorkflowDefinition fixture = fixtureDefinition();
        List<WorkflowStepExecutor<?>> executors = List.of(
                new FixtureStepExecutor("LOAD_FIXTURE", false, false, () -> {}),
                new FixtureStepExecutor("TRANSFORM_FIXTURE", true, waitingCapable, () -> {}),
                new FixtureStepExecutor("APPLY_FIXTURE", false, false, beforeApplyProjection));
        ExecutableWorkflowContribution contribution = new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_GENERATION, WORKFLOW_VERSION,
                TerminalPartialPolicy.rejectUnexpected(),
                List.of(
                        new ExecutableWorkflowStep("LOAD_FIXTURE", executors.get(0)),
                        new ExecutableWorkflowStep("TRANSFORM_FIXTURE", executors.get(1)),
                        new ExecutableWorkflowStep("APPLY_FIXTURE", executors.get(2))));
        List<WorkflowDefinition> definitions = new ArrayList<>(CanonicalWorkflowDefinitions.all());
        definitions.add(fixture);
        WorkflowRegistry registry = new WorkflowRegistry(definitions, List.of(contribution));
        DisabledAiGateways disabled = new DisabledAiGateways();
        return new AgentOrchestrator(
                registry,
                contextBuilder,
                new PolicyModelRouter(new ModelPolicy(
                        modelPolicyVersion, true, "fixture-provider", "low", "balanced", "high",
                        Set.of(WorkflowType.COVER_LETTER_GENERATION))),
                prompts(),
                new StructuredOutputValidator(objectMapper),
                chatGateway,
                disabled,
                disabled,
                runQueryPort,
                runStatePort,
                stepCheckpointPort,
                usageRecorderPort,
                domainApply,
                cancellationPort,
                heartbeatPort,
                new BudgetGuard(budgetReservationPort),
                objectMapper,
                Clock.systemUTC(),
                List.of(),
                completionTransaction);
    }

    private AgentOrchestrator fanOutOrchestrator(
            UUID succeededQuestionId,
            UUID failedQuestionId,
            UUID answerVersionId) {
        return fanOutOrchestrator(
                succeededQuestionId,
                failedQuestionId,
                answerVersionId,
                TerminalPartialPolicy.fail(
                        "COVER_LETTER_GENERATION_PARTIAL_FAILURE",
                        "일부 자기소개서 문항을 생성하지 못했습니다.",
                        TerminalPartialPolicy.RetryPolicy.INHERIT_FAILURES));
    }

    private AgentOrchestrator fanOutOrchestrator(
            UUID succeededQuestionId,
            UUID failedQuestionId,
            UUID answerVersionId,
            TerminalPartialPolicy partialPolicy) {
        WorkflowDefinition fanOut = new WorkflowDefinition(
                WorkflowType.COVER_LETTER_GENERATION,
                FAN_OUT_WORKFLOW_VERSION,
                false,
                EnumSet.allOf(AiQualityMode.class),
                List.of(new StepDefinition(
                        "WRITE_ANSWER",
                        "FanOutFixture",
                        "input-v1",
                        "output-v1",
                        Set.of(),
                        0,
                        20,
                        ModelTier.LOW_COST,
                        Set.of(),
                        new BigDecimal("100"))));
        WorkflowStepExecutor<FixtureOutput> executor =
                new FanOutFixtureExecutor(
                        succeededQuestionId,
                        failedQuestionId,
                        answerVersionId);
        ExecutableWorkflowContribution contribution =
                new ExecutableWorkflowContribution(
                        WorkflowType.COVER_LETTER_GENERATION,
                        FAN_OUT_WORKFLOW_VERSION,
                        partialPolicy,
                        List.of(new ExecutableWorkflowStep(
                                "WRITE_ANSWER", executor)));
        List<WorkflowDefinition> definitions =
                new ArrayList<>(CanonicalWorkflowDefinitions.all());
        definitions.add(fanOut);
        PromptRegistry promptRegistry = new PromptRegistry(List.of(
                new PromptDefinition(
                        new PromptKey(
                                WorkflowType.COVER_LETTER_GENERATION,
                                FAN_OUT_WORKFLOW_VERSION,
                                "WRITE_ANSWER"),
                        "p7-fan-out-fixture-prompt-v1",
                        FixtureInput.class,
                        FixtureOutput.class,
                        "output-v1",
                        Set.of(),
                        1,
                        1,
                        0,
                        "Execute the bounded local fan-out fixture.")));
        DisabledAiGateways disabled = new DisabledAiGateways();
        return new AgentOrchestrator(
                new WorkflowRegistry(definitions, List.of(contribution)),
                contextBuilder,
                new PolicyModelRouter(new ModelPolicy(
                        modelPolicyVersion,
                        true,
                        "fixture-provider",
                        "low",
                        "balanced",
                        "high",
                        Set.of(WorkflowType.COVER_LETTER_GENERATION))),
                promptRegistry,
                new StructuredOutputValidator(objectMapper),
                chatGateway,
                disabled,
                disabled,
                runQueryPort,
                runStatePort,
                stepCheckpointPort,
                usageRecorderPort,
                domainApply,
                cancellationPort,
                leaseHeartbeatPort,
                new BudgetGuard(budgetReservationPort),
                objectMapper,
                Clock.systemUTC(),
                List.of(),
                new SpringStepCompletionTransaction(transactionManager));
    }

    private WorkflowDefinition fixtureDefinition() {
        Set<FailureKind> retryable = EnumSet.of(
                FailureKind.RATE_LIMIT, FailureKind.PROVIDER_5XX, FailureKind.NETWORK,
                FailureKind.TIMEOUT, FailureKind.STRUCTURED_OUTPUT);
        return new WorkflowDefinition(
                WorkflowType.COVER_LETTER_GENERATION,
                WORKFLOW_VERSION,
                false,
                EnumSet.allOf(AiQualityMode.class),
                List.of(
                        new StepDefinition("LOAD_FIXTURE", "FixtureLoader", "input-v1", "output-v1",
                                Set.of(), 0, 1, ModelTier.LOW_COST, Set.of(), new BigDecimal("30")),
                        new StepDefinition("TRANSFORM_FIXTURE", "FixtureTransformer", "input-v1", "output-v1",
                                Set.of(), 1, 1, ModelTier.BALANCED, retryable, new BigDecimal("40")),
                        new StepDefinition("APPLY_FIXTURE", "FixtureApplier", "input-v1", "output-v1",
                                Set.of(), 0, 1, ModelTier.LOW_COST, Set.of(), new BigDecimal("30"))));
    }

    private PromptRegistry prompts() {
        String instructions;
        try (var stream = getClass().getResourceAsStream("/prompts/p3-fake-fixture.txt")) {
            if (stream == null) throw new IllegalStateException("fixture prompt is missing");
            instructions = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("fixture prompt cannot be read", exception);
        }
        return new PromptRegistry(List.of("LOAD_FIXTURE", "TRANSFORM_FIXTURE", "APPLY_FIXTURE")
                .stream()
                .map(step -> new PromptDefinition(
                        new PromptKey(WorkflowType.COVER_LETTER_GENERATION, WORKFLOW_VERSION, step),
                        "fixture-prompt-v1", FixtureInput.class, FixtureOutput.class, "output-v1",
                        Set.of(), 500, 200, step.equals("TRANSFORM_FIXTURE") ? 1 : 0, instructions))
                .toList());
    }

    private WorkflowLaunchResult launch(AiQualityMode quality, String canonicalInputHash) {
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                userId,
                WorkflowType.COVER_LETTER_GENERATION,
                WORKFLOW_VERSION,
                canonicalInputHash,
                objectMapper.createObjectNode().put("fixtureRef", "safe-fixture-ref"),
                quality,
                BigDecimal.ZERO.setScale(6),
                null,
                null));
    }

    private void execute(UUID runId, AgentOrchestrator orchestrator) {
        orchestrator.execute(claim(runId));
    }

    private ClaimedAgentRun claim(UUID runId) {
        return runStatePort.claim(runId, "fixture-worker", Instant.now(), Duration.ofSeconds(60))
                .orElseThrow();
    }

    private AgentRunSnapshot run(UUID runId) {
        return runQueryPort.findByOwner(userId, runId).orElseThrow();
    }

    private long usageCount(UUID runId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_usage_records WHERE agent_run_id = ?",
                Long.class,
                runId);
    }

    private long seedModelPolicy() {
        long version = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L) + 10_000L;
        jdbcTemplate.update("""
                INSERT INTO ai_model_policies (id,version,policy_json,active,created_at)
                VALUES (?,?,'{}',false,now())
                """, UUID.randomUUID(), version);
        return version;
    }

    private UUID seedUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,?,'AI Fixture','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """, id, "ai-fixture-" + id + "@example.test", "hash");
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), id);
        jdbcTemplate.update("""
                INSERT INTO user_ai_preferences (
                    id,user_id,budget_policy_version,default_quality_mode,high_quality_enabled,
                    daily_budget_usd,active,version,created_at,updated_at
                ) VALUES (?,?,1,'ECONOMY',false,1.000000,true,0,now(),now())
                """, UUID.randomUUID(), id);
        return id;
    }

    private final class FixtureStepExecutor implements WorkflowStepExecutor<FixtureOutput> {
        private final String stepKey;
        private final boolean chat;
        private final boolean waitingCapable;
        private final Runnable beforeProjection;

        private FixtureStepExecutor(
                String stepKey, boolean chat, boolean waitingCapable, Runnable beforeProjection) {
            this.stepKey = stepKey;
            this.chat = chat;
            this.waitingCapable = waitingCapable;
            this.beforeProjection = beforeProjection;
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            boolean waiting = waitingCapable && contextBuilder.waiting.get();
            RequiredUserAction action = waiting ? new RequiredUserAction(
                    RequiredUserActionType.INCREASE_BUDGET,
                    null,
                    "/agent-runs/" + context.run().id(),
                    "테스트 입력을 확인해 주세요.") : null;
            JsonNode refs = objectMapper.createObjectNode()
                    .put("fixtureRef", "safe-fixture-ref")
                    .put("upstreamCount", context.upstreamOutputs().size());
            JsonNode payload = objectMapper.createObjectNode()
                    .put("fixtureRef", "ephemeral-fixture")
                    .put("stepKey", stepKey);
            String upstreamHashes = context.upstreamOutputs().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + entry.getValue().path("resultHash").asText())
                    .collect(java.util.stream.Collectors.joining(","));
            return new StepInput(null, refs,
                    context.run().canonicalInputHash() + ":" + stepKey + ":" + upstreamHashes,
                    payload, action, 0);
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            if (chat) {
                return invocation.chatGateway().chat(new ChatGateway.ChatRequest(
                        invocation.modelRoute().providerKey(), invocation.modelRoute().productKey(),
                        invocation.prompt().promptVersion(), invocation.prompt().instructions(),
                        invocation.input().gatewayPayload(), invocation.prompt().outputSchemaVersion(),
                        invocation.prompt().toolAllowlist(), invocation.prompt().maxModelCalls(),
                        Duration.ofSeconds(2)));
            }
            return successResponse(null);
        }

        @Override
        public Contract<FixtureOutput> outputContract() {
            return new Contract<>(
                    FixtureOutput.class,
                    "output-v1",
                    tree -> {
                        if (!tree.isObject() || !tree.has("resultRef") || !tree.has("resultHash")
                                || !tree.has("valid")) throw new IllegalArgumentException();
                    },
                    output -> {
                        if (output.resultRef() == null || output.resultRef().isBlank()
                                || output.resultHash() == null
                                || !output.resultHash().matches("[0-9a-f]{64}")) {
                            throw new IllegalArgumentException();
                        }
                    },
                    output -> { if (!output.valid()) throw new IllegalArgumentException(); },
                    output -> { if (stepKey.equals("APPLY_FIXTURE") && !output.valid()) throw new IllegalArgumentException(); });
        }

        @Override
        public JsonNode minimalOutput(FixtureOutput output, ObjectMapper mapper) {
            if (stepKey.equals("APPLY_FIXTURE")) beforeProjection.run();
            return mapper.createObjectNode()
                    .put("resultRef", output.resultRef())
                    .put("resultHash", output.resultHash())
                    .put("validated", output.valid());
        }

        @Override
        public Optional<DomainApplyPlan> domainApply(
                FixtureOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            return stepKey.equals("APPLY_FIXTURE")
                    ? Optional.of(new DomainApplyPlan("FIXTURE", resourceId, 0)) : Optional.empty();
        }

        @Override
        public Optional<DomainApplyPlan> domainApplyFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            return stepKey.equals("APPLY_FIXTURE")
                    ? Optional.of(new DomainApplyPlan("FIXTURE", resourceId, 0)) : Optional.empty();
        }
    }

    private final class FanOutFixtureExecutor
            implements WorkflowStepExecutor<FixtureOutput> {

        private final UUID succeededQuestionId;
        private final UUID failedQuestionId;
        private final UUID answerVersionId;

        private FanOutFixtureExecutor(
                UUID succeededQuestionId,
                UUID failedQuestionId,
                UUID answerVersionId) {
            this.succeededQuestionId = succeededQuestionId;
            this.failedQuestionId = failedQuestionId;
            this.answerVersionId = answerVersionId;
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return prepareInputs(context).getFirst();
        }

        @Override
        public List<StepInput> prepareInputs(
                StepExecutionContext context) {
            return List.of(
                    input(context, succeededQuestionId),
                    input(context, failedQuestionId));
        }

        private StepInput input(
                StepExecutionContext context, UUID questionId) {
            return new StepInput(
                    questionId.toString(),
                    objectMapper
                            .createObjectNode()
                            .put("questionId", questionId.toString()),
                    context.run().id() + ":" + questionId,
                    objectMapper
                            .createObjectNode()
                            .put("questionId", questionId.toString()),
                    null,
                    0);
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            if (failedQuestionId
                    .toString()
                    .equals(invocation.input().scopeKey())) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.DOMAIN_VALIDATION,
                        "P7_FIXTURE_QUESTION_INVALID",
                        "해당 문항을 생성하지 못했습니다.");
            }
            return successResponse(null);
        }

        @Override
        public Contract<FixtureOutput> outputContract() {
            return new Contract<>(
                    FixtureOutput.class,
                    "output-v1",
                    tree -> {
                        if (!tree.isObject()
                                || !tree.has("resultRef")
                                || !tree.has("resultHash")
                                || !tree.has("valid")) {
                            throw new IllegalArgumentException();
                        }
                    },
                    output -> {},
                    output -> {},
                    output -> {});
        }

        @Override
        public JsonNode minimalOutput(
                FixtureOutput output, ObjectMapper mapper) {
            return mapper.createObjectNode()
                    .put("resultRef", output.resultRef())
                    .put("resultHash", output.resultHash())
                    .put("valid", output.valid());
        }

        @Override
        public Optional<PartialResult> partialResult(
                FixtureOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            return Optional.of(new PartialResult(
                    List.of(context.scopeKey()),
                    List.of(),
                    List.of(new ResourceReference(
                            "COVER_LETTER_ANSWER_VERSION",
                            answerVersionId,
                            "자기소개서 답변 버전"))));
        }

        @Override
        public Optional<PartialResult> partialResultFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            return partialResult(null, minimalOutput, context);
        }

        @Override
        public boolean continueAfterScopeFailure(
                AiExecutionException failure,
                StepExecutionContext context) {
            return context.scopeKey() != null
                    && failure.failureKind()
                            == FailureKind.DOMAIN_VALIDATION;
        }
    }

    private final class MutableContextBuilder implements ContextBuilder {
        private final long policyVersion;
        private final UUID resourceId;
        private final AtomicBoolean waiting = new AtomicBoolean();
        private final AtomicBoolean highQualityEnabled = new AtomicBoolean();

        private MutableContextBuilder(long policyVersion, UUID resourceId) {
            this.policyVersion = policyVersion;
            this.resourceId = resourceId;
        }

        @Override
        public ContextSnapshot build(ContextRequest request) {
            return new ContextSnapshot(
                    request.run().userId(),
                    List.of(new ResourceSnapshotRef("FIXTURE", resourceId, 0, request.run().canonicalInputHash())),
                    List.of(), List.of(), new TruncationSummary(1, 0, List.of()),
                    sha256(request.run().canonicalInputHash() + ":context"),
                    "VERIFIED",
                    policyVersion,
                    highQualityEnabled.get(),
                    true);
        }
    }

    private enum GatewayOutcome {
        SEMANTIC_INVALID,
        NETWORK,
        VALID
    }

    private static final class FakeChatGateway implements ChatGateway {
        private final AtomicInteger failuresBeforeSuccess = new AtomicInteger();
        private final AtomicInteger invalidResponsesBeforeSuccess = new AtomicInteger();
        private final List<String> products = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<String> instructions = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final java.util.Queue<GatewayOutcome> scriptedOutcomes =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean block;

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            products.add(request.productKey());
            instructions.add(request.instructions());
            entered.countDown();
            if (block) {
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw AiExecutionException.retryable(
                                FailureKind.TIMEOUT, "AI_PROVIDER_TIMEOUT", "AI 공급자 응답 시간이 초과되었습니다.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw AiExecutionException.retryable(
                            FailureKind.TIMEOUT, "AI_PROVIDER_TIMEOUT", "AI 공급자 응답 시간이 초과되었습니다.");
                }
            }
            GatewayOutcome scripted = scriptedOutcomes.poll();
            if (scripted == GatewayOutcome.NETWORK) {
                throw AiExecutionException.retryable(
                        FailureKind.NETWORK,
                        "AI_PROVIDER_TEMPORARY_FAILURE",
                        "AI 공급자 연결이 일시적으로 불안정합니다.",
                        List.of(callUsage(request)));
            }
            if (scripted == GatewayOutcome.SEMANTIC_INVALID) {
                return invalidResponse(request);
            }
            if (scripted == GatewayOutcome.VALID) {
                return successResponse(callUsage(request));
            }
            if (failuresBeforeSuccess.getAndUpdate(value -> value > 0 ? value - 1 : value) > 0) {
                throw AiExecutionException.retryable(
                        FailureKind.NETWORK,
                        "AI_PROVIDER_TEMPORARY_FAILURE",
                        "AI 공급자 연결이 일시적으로 불안정합니다.");
            }
            if (invalidResponsesBeforeSuccess.getAndUpdate(value -> value > 0 ? value - 1 : value) > 0) {
                return invalidResponse(request);
            }
            return successResponse(zeroUsage(request));
        }

        private void script(GatewayOutcome... outcomes) {
            scriptedOutcomes.addAll(List.of(outcomes));
        }

        private AiGatewayResponse invalidResponse(ChatRequest request) {
            return new AiGatewayResponse(
                    "{\"resultRef\":\"safe-ref\",\"resultHash\":\""
                            + OUTPUT_HASH + "\",\"valid\":false}",
                    callUsage(request));
        }

        private AiUsage callUsage(ChatRequest request) {
            return new AiUsage(
                    UsageType.CHAT,
                    request.providerKey(),
                    request.productKey(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    BigDecimal.ZERO.setScale(6),
                    1,
                    UUID.randomUUID());
        }

        private AiUsage zeroUsage(ChatRequest request) {
            return new AiUsage(UsageType.CHAT, request.providerKey(), request.productKey(),
                    0, 0, 0, 0, 0, null, null, BigDecimal.ZERO.setScale(6), 1);
        }
    }

    private static final class FakeDomainApply implements DomainResultApplyPort {
        private final UUID expectedUserId;
        private final UUID expectedResourceId;
        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger appliedCount = new AtomicInteger();
        private final AtomicInteger applyCallCount = new AtomicInteger();
        private final AtomicBoolean failAfterDomainWrite = new AtomicBoolean();
        private volatile String failedMarker;

        private FakeDomainApply(
                UUID expectedUserId,
                UUID expectedResourceId,
                JdbcTemplate jdbcTemplate) {
            this.expectedUserId = expectedUserId;
            this.expectedResourceId = expectedResourceId;
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public ApplyResult apply(DomainResultCommand command) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(command.userId()).isEqualTo(expectedUserId);
            assertThat(command.resourceType()).isEqualTo("FIXTURE");
            assertThat(command.resourceId()).isEqualTo(expectedResourceId);
            assertThat(command.expectedResourceVersion()).isZero();
            assertThat(command.inputHash()).matches("[0-9a-f]{64}");
            assertThat(command.validatedResultReference().isObject()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(java.sql.Connection.TRANSACTION_SERIALIZABLE);
            applyCallCount.incrementAndGet();
            if (failAfterDomainWrite.compareAndSet(true, false)) {
                failedMarker = "ai-failed-" + command.agentRunId();
                jdbcTemplate.update("""
                        INSERT INTO companies (
                            id, normalized_name, display_name, official_website,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, NULL, now(), now())
                        """, UUID.randomUUID(), failedMarker, failedMarker);
                throw new IllegalStateException("fixture domain apply crash");
            }
            String key =
                    "ai-applied-" + command.resourceId() + "-" + command.inputHash();
            if (jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM companies WHERE normalized_name = ?",
                            Long.class,
                            key)
                    > 0) {
                return ApplyResult.ALREADY_APPLIED;
            }
            jdbcTemplate.update("""
                    INSERT INTO companies (
                        id, normalized_name, display_name, official_website,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, NULL, now(), now())
                    """, UUID.randomUUID(), key, key);
            appliedCount.incrementAndGet();
            return ApplyResult.APPLIED;
        }

        private void failAfterDomainWrite() {
            failAfterDomainWrite.set(true);
        }

        private int appliedCount() { return appliedCount.get(); }

        private int applyCallCount() { return applyCallCount.get(); }

        private long failedMarkerCount() {
            return jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM companies WHERE normalized_name = ?",
                    Long.class,
                    failedMarker);
        }

        private long committedMarkerCount() {
            return jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM companies WHERE normalized_name LIKE ?",
                    Long.class,
                    "ai-applied-" + expectedResourceId + "-%");
        }
    }

    private static final class SimulatedProcessCrash extends Error {}

    private static AiGatewayResponse successResponse(AiUsage usage) {
        return new AiGatewayResponse(
                "{\"resultRef\":\"fixture-result\",\"resultHash\":\"" + OUTPUT_HASH
                        + "\",\"valid\":true}", usage);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record FixtureInput(String fixtureRef) {}
    private record FixtureOutput(String resultRef, String resultHash, boolean valid) {}
    private record AgentStepSnapshotView(int attempt, AgentStepStatus status) {
        private static AgentStepSnapshotView from(com.hiresemble.agentrun.application.model.AgentStepSnapshot step) {
            return new AgentStepSnapshotView(step.attempt(), step.status());
        }
    }
}
