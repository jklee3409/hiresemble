package com.hiresemble.ai.orchestration;

import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunLeaseHeartbeatPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.port.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.model.AgentStepSnapshot;
import com.hiresemble.agentrun.application.model.ClaimedAgentRun;
import com.hiresemble.agentrun.application.port.DomainResultApplyPort;
import com.hiresemble.agentrun.application.command.DomainResultCommand;
import com.hiresemble.agentrun.application.model.ReusableStepSnapshot;
import com.hiresemble.agentrun.application.command.StepCheckpointCommand;
import com.hiresemble.agentrun.application.command.StepStartCommand;
import com.hiresemble.agentrun.application.command.UsageRecordCommand;
import com.hiresemble.agentrun.application.port.UsageRecorderPort;
import com.hiresemble.agentrun.application.port.WorkflowExecutionPort;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.ai.budget.BudgetGuard;
import com.hiresemble.ai.context.ContextBuilder;
import com.hiresemble.ai.context.ContextBuilder.ContextRequest;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.ModelRouter;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.model.ModelRouter.RoutingRequest;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.ai.workflow.WorkflowRegistry.WorkflowDefinition;
import com.hiresemble.ai.workflow.WorkflowRegistry.WorkflowConfigurationException;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainApplyPlan;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Executes a bounded registry sequence; it has no agent-selected loop and never accesses repositories directly. */
public final class AgentOrchestrator implements WorkflowExecutionPort {

    private static final int MAX_ATTEMPTS = 3;

    private final WorkflowRegistry workflowRegistry;
    private final ContextBuilder contextBuilder;
    private final ModelRouter modelRouter;
    private final PromptRegistry promptRegistry;
    private final StructuredOutputValidator outputValidator;
    private final ChatGateway chatGateway;
    private final EmbeddingGateway embeddingGateway;
    private final WebSearchGateway webSearchGateway;
    private final AgentRunQueryPort runQueryPort;
    private final AgentRunStatePort runStatePort;
    private final AgentStepCheckpointPort stepCheckpointPort;
    private final UsageRecorderPort usageRecorderPort;
    private final DomainResultApplyPort domainResultApplyPort;
    private final AgentRunCancellationPort cancellationPort;
    private final AgentRunLeaseHeartbeatPort leaseHeartbeatPort;
    private final BudgetGuard budgetGuard;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final List<WorkflowFailureHandler> failureHandlers;

    public AgentOrchestrator(
            WorkflowRegistry workflowRegistry,
            ContextBuilder contextBuilder,
            ModelRouter modelRouter,
            PromptRegistry promptRegistry,
            StructuredOutputValidator outputValidator,
            ChatGateway chatGateway,
            EmbeddingGateway embeddingGateway,
            WebSearchGateway webSearchGateway,
            AgentRunQueryPort runQueryPort,
            AgentRunStatePort runStatePort,
            AgentStepCheckpointPort stepCheckpointPort,
            UsageRecorderPort usageRecorderPort,
            DomainResultApplyPort domainResultApplyPort,
            AgentRunCancellationPort cancellationPort,
            AgentRunLeaseHeartbeatPort leaseHeartbeatPort,
            BudgetGuard budgetGuard,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                workflowRegistry, contextBuilder, modelRouter, promptRegistry, outputValidator,
                chatGateway, embeddingGateway, webSearchGateway, runQueryPort, runStatePort,
                stepCheckpointPort, usageRecorderPort, domainResultApplyPort, cancellationPort,
                leaseHeartbeatPort, budgetGuard, objectMapper, clock, List.of());
    }

    public AgentOrchestrator(
            WorkflowRegistry workflowRegistry,
            ContextBuilder contextBuilder,
            ModelRouter modelRouter,
            PromptRegistry promptRegistry,
            StructuredOutputValidator outputValidator,
            ChatGateway chatGateway,
            EmbeddingGateway embeddingGateway,
            WebSearchGateway webSearchGateway,
            AgentRunQueryPort runQueryPort,
            AgentRunStatePort runStatePort,
            AgentStepCheckpointPort stepCheckpointPort,
            UsageRecorderPort usageRecorderPort,
            DomainResultApplyPort domainResultApplyPort,
            AgentRunCancellationPort cancellationPort,
            AgentRunLeaseHeartbeatPort leaseHeartbeatPort,
            BudgetGuard budgetGuard,
            ObjectMapper objectMapper,
            Clock clock,
            List<WorkflowFailureHandler> failureHandlers) {
        this.workflowRegistry = workflowRegistry;
        this.contextBuilder = contextBuilder;
        this.modelRouter = modelRouter;
        this.promptRegistry = promptRegistry;
        this.outputValidator = outputValidator;
        this.chatGateway = chatGateway;
        this.embeddingGateway = embeddingGateway;
        this.webSearchGateway = webSearchGateway;
        this.runQueryPort = runQueryPort;
        this.runStatePort = runStatePort;
        this.stepCheckpointPort = stepCheckpointPort;
        this.usageRecorderPort = usageRecorderPort;
        this.domainResultApplyPort = domainResultApplyPort;
        this.cancellationPort = cancellationPort;
        this.leaseHeartbeatPort = leaseHeartbeatPort;
        this.budgetGuard = budgetGuard;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.failureHandlers = failureHandlers == null ? List.of() : List.copyOf(failureHandlers);
    }

    @Override
    public void execute(ClaimedAgentRun claimedRun) {
        AgentRunSnapshot initial = claimedRun.run();
        try {
            WorkflowDefinition definition = workflowRegistry.definition(
                    initial.workflowType(), initial.workflowVersion());
            ExecutableWorkflowContribution contribution = workflowRegistry
                    .executable(initial.workflowType(), initial.workflowVersion())
                    .orElseThrow(() -> new WorkflowConfigurationException(
                            "AI_WORKFLOW_EXECUTABLE_NOT_CONFIGURED"));
            if (initial.requestedQualityMode() != null
                    && !definition.allowedQualityModes().contains(initial.requestedQualityMode())) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.REQUEST_VALIDATION,
                        "QUALITY_MODE_NOT_SUPPORTED",
                        "선택한 품질 모드를 이 작업에 사용할 수 없습니다.");
            }
            ContextSnapshot context = contextBuilder.build(new ContextRequest(initial));
            if (!context.userId().equals(initial.userId())) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.OWNER,
                        "RESOURCE_NOT_FOUND",
                        "요청한 리소스를 찾을 수 없습니다.");
            }
            runFixedSequence(claimedRun, definition, contribution, context);
        } catch (AiExecutionException exception) {
            failRun(initial.userId(), initial.id(), claimedRun.claimToken(), exception);
        } catch (BusinessException exception) {
            failRun(initial.userId(), initial.id(), claimedRun.claimToken(), mapBusiness(exception));
        } catch (WorkflowConfigurationException exception) {
            failRun(initial.userId(), initial.id(), claimedRun.claimToken(),
                    AiExecutionException.nonRetryable(
                            FailureKind.CONFIGURATION,
                            exception.safeCode(),
                            "AI 실행 구성이 준비되지 않았습니다."));
        } catch (RuntimeException ignored) {
            failRun(initial.userId(), initial.id(), claimedRun.claimToken(),
                    AiExecutionException.nonRetryable(
                            FailureKind.CONFIGURATION,
                            "AI_WORKFLOW_EXECUTION_FAILED",
                            "AI 실행을 안전하게 완료하지 못했습니다."));
        }
    }

    private void runFixedSequence(
            ClaimedAgentRun claimed,
            WorkflowDefinition definition,
            ExecutableWorkflowContribution contribution,
            ContextSnapshot context) {
        Map<String, JsonNode> upstreamOutputs = new HashMap<>();
        Map<String, Object> ephemeralOutputs = new HashMap<>();
        BigDecimal completedWeight = BigDecimal.ZERO;
        PartialResult partialResult = null;
        List<StepDefinition> definitions = definition.steps();

        for (int index = 0; index < definitions.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                interruptRun(claimed.run().userId(), claimed.run().id(), claimed.claimToken());
                return;
            }
            AgentRunSnapshot run = current(claimed.run().userId(), claimed.run().id());
            if (run.status().isTerminal()) return;
            if (completeCancellationIfRequested(run, claimed.claimToken())) return;

            StepDefinition stepDefinition = definitions.get(index);
            ExecutableWorkflowStep executableStep = contribution.steps().get(index);
            StepExecutionContext executionContext =
                    new StepExecutionContext(run, context, upstreamOutputs, ephemeralOutputs);
            StepInput input = executableStep.executor().prepare(executionContext);
            PromptDefinition prompt = promptRegistry.require(
                    run.workflowType(), run.workflowVersion(), stepDefinition.stepKey());
            validatePromptContract(stepDefinition, prompt);
            String inputHash = inputHash(run, context, stepDefinition, prompt, input);

            Optional<ReusableStepSnapshot> reusable = executableStep.executor().reusable()
                    ? runQueryPort.findReusableStep(
                            run.userId(), stepDefinition.stepKey(), input.scopeKey(), inputHash,
                            run.requestedQualityMode())
                    : Optional.empty();
            Optional<AgentStepSnapshot> completedInThisRun =
                    latestStep(run, stepDefinition.stepKey(), input.scopeKey())
                    .filter(step -> step.status() == AgentStepStatus.SUCCEEDED
                            || step.status() == AgentStepStatus.REUSED);
            if (completedInThisRun.isPresent()) {
                ReusableStepSnapshot output = reusable.orElseThrow(() ->
                        AiExecutionException.nonRetryable(
                                FailureKind.CONFIGURATION,
                                "AI_STEP_CHECKPOINT_INCOMPLETE",
                                "저장된 AI 단계 결과를 복구하지 못했습니다."));
                upstreamOutputs.put(stepDefinition.stepKey(), output.minimalOutput());
                ephemeralOutputs.put(
                        stepDefinition.stepKey(),
                        executableStep.executor().ephemeralOutputFromMinimal(output.minimalOutput()));
                completedWeight = completedWeight.add(stepDefinition.progressWeight());
                continue;
            }
            if (reusable.isPresent()) {
                AgentStepSnapshot reused = stepCheckpointPort.reuse(
                        startCommand(run, claimed.claimToken(), stepDefinition, prompt, input, inputHash,
                                context.modelPolicyVersion(), 1),
                        reusable.get());
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) return;
                applyReused(executableStep.executor(), reusable.get().minimalOutput(), executionContext,
                        run, reused, inputHash);
                upstreamOutputs.put(stepDefinition.stepKey(), reusable.get().minimalOutput());
                ephemeralOutputs.put(
                        stepDefinition.stepKey(),
                        executableStep.executor().ephemeralOutputFromMinimal(
                                reusable.get().minimalOutput()));
                completedWeight = completedWeight.add(stepDefinition.progressWeight());
                updateProgress(run.userId(), run.id(), claimed.claimToken(), stepDefinition.stepKey(),
                        completedWeight.intValue());
                continue;
            }

            if (input.waitsForUser()) {
                AgentStepSnapshot waitingStep = startOrResumePending(
                        run, claimed.claimToken(), stepDefinition, prompt, input, inputHash,
                        context.modelPolicyVersion(), 1);
                stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                        run.userId(), run.id(), waitingStep.id(), claimed.claimToken(),
                        AgentStepStatus.WAITING_USER, null, null, null, null, null, clock.instant()));
                AgentRunSnapshot beforeWaiting = current(run.userId(), run.id());
                budgetGuard.releaseUnused(beforeWaiting, clock.instant());
                AgentRunSnapshot released = current(run.userId(), run.id());
                runStatePort.transition(new AgentRunTransitionCommand(
                        released.userId(), released.id(), claimed.claimToken(), released.stateVersion(),
                        AgentRunStatus.WAITING_USER, stepDefinition.stepKey(),
                        completedWeight.intValue(), released.highestModelTierUsed(),
                        released.actualCostUsd(), false, input.requiredUserAction(), null, null,
                        clock.instant()));
                return;
            }

            StepResult result = executeWithAttempts(
                    claimed, definition, stepDefinition, executableStep.executor(),
                    prompt, input, inputHash, executionContext);
            if (result.cancelledOrTerminal()) return;
            if (result.requiredUserAction() != null) {
                AgentRunSnapshot beforeWaiting = current(run.userId(), run.id());
                budgetGuard.releaseUnused(beforeWaiting, clock.instant());
                AgentRunSnapshot released = current(run.userId(), run.id());
                runStatePort.transition(new AgentRunTransitionCommand(
                        released.userId(), released.id(), claimed.claimToken(),
                        released.stateVersion(), AgentRunStatus.WAITING_USER,
                        stepDefinition.stepKey(), completedWeight.intValue(),
                        released.highestModelTierUsed(), released.actualCostUsd(), false,
                        result.requiredUserAction(), null, partialResult, clock.instant()));
                return;
            }
            upstreamOutputs.put(stepDefinition.stepKey(), result.minimalOutput());
            ephemeralOutputs.put(stepDefinition.stepKey(), result.ephemeralOutput());
            if (result.partialResult() != null) partialResult = result.partialResult();
            completedWeight = completedWeight.add(stepDefinition.progressWeight());
            updateProgress(run.userId(), run.id(), claimed.claimToken(), stepDefinition.stepKey(),
                    completedWeight.intValue());
        }

        AgentRunSnapshot completed = current(claimed.run().userId(), claimed.run().id());
        budgetGuard.settleSuccess(completed, clock.instant());
        completed = current(completed.userId(), completed.id());
        runStatePort.transition(new AgentRunTransitionCommand(
                completed.userId(), completed.id(), claimed.claimToken(), completed.stateVersion(),
                AgentRunStatus.SUCCEEDED, completed.currentStep(), 100,
                completed.highestModelTierUsed(), completed.actualCostUsd(), false,
                null, null, partialResult == null ? completed.partialResult() : partialResult,
                clock.instant()));
    }

    private StepResult executeWithAttempts(
            ClaimedAgentRun claimed,
            WorkflowDefinition definition,
            StepDefinition stepDefinition,
            WorkflowStepExecutor<?> executor,
            PromptDefinition prompt,
            StepInput input,
            String inputHash,
            StepExecutionContext executionContext) {
        AgentRunSnapshot run = current(claimed.run().userId(), claimed.run().id());
        skipStalePending(
                run, claimed.claimToken(), stepDefinition.stepKey(), input.scopeKey());
        run = current(run.userId(), run.id());
        int firstAttempt = nextAttempt(run, stepDefinition.stepKey(), input.scopeKey());
        ModelTier previousTier = null;
        FailureKind previousFailure = null;

        for (int attempt = firstAttempt; attempt <= MAX_ATTEMPTS; attempt++) {
            run = current(run.userId(), run.id());
            if (completeCancellationIfRequested(run, claimed.claimToken())) return StepResult.terminal();
            ModelRoute route = modelRouter.route(new RoutingRequest(
                    run.workflowType(), stepDefinition.stepKey(), run.requestedQualityMode(),
                    stepDefinition.preferredTier(), stepDefinition.requiresProvider(),
                    executionContext.contextSnapshot().highQualityEnabled(),
                    executionContext.contextSnapshot().budgetReservationConfirmed(),
                    attempt, previousTier, previousFailure));
            AgentStepSnapshot step = startOrResumePending(
                    run, claimed.claimToken(), stepDefinition, prompt, input, inputHash,
                    executionContext.contextSnapshot().modelPolicyVersion(), attempt);
            boolean activeStep = true;
            try {
                AgentRunSnapshot beforeCall = current(run.userId(), run.id());
                budgetGuard.ensureNextCallCovered(beforeCall, BigDecimal.ZERO, clock.instant());
                AiGatewayResponse response = leaseHeartbeatPort.maintain(
                        run.userId(), run.id(), claimed.claimToken(),
                        () -> executor.invoke(new GatewayInvocation(
                                input, route, prompt, chatGateway, embeddingGateway, webSearchGateway,
                                executionContext)));
                recordUsageIfPresent(run, claimed.claimToken(), step, route, response.usage());
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) {
                    return StepResult.terminal();
                }
                Object validated = validate(executor, response.rawJson(), executionContext);
                JsonNode minimalOutput = minimalOutput(executor, validated);
                Optional<com.hiresemble.agentrun.domain.model.RequiredUserAction> requiredAction =
                        executorRequiredUserAction(executor, validated, minimalOutput, executionContext);
                if (requiredAction.isPresent()) {
                    stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                            run.userId(), run.id(), step.id(), claimed.claimToken(),
                            AgentStepStatus.WAITING_USER, sha256(minimalOutput.toString()),
                            minimalOutput, route.tier(), null, null, clock.instant()));
                    activeStep = false;
                    return StepResult.waiting(
                            minimalOutput,
                            executorEphemeralOutput(executor, validated),
                            requiredAction.get());
                }
                stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                        run.userId(), run.id(), step.id(), claimed.claimToken(), AgentStepStatus.SUCCEEDED,
                        sha256(minimalOutput.toString()), minimalOutput, route.tier(), null, null,
                        clock.instant()));
                activeStep = false;
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) {
                    return StepResult.terminal();
                }
                applyFresh(executor, validated, minimalOutput, executionContext, run, step, inputHash);
                return new StepResult(
                        minimalOutput,
                        executorEphemeralOutput(executor, validated),
                        executorPartialResult(executor, validated, minimalOutput, executionContext)
                                .orElse(null),
                        null,
                        false);
            } catch (AiExecutionException exception) {
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) {
                    return StepResult.terminal();
                }
                if (activeStep) checkpointFailure(run, claimed.claimToken(), step, exception);
                previousTier = route.tier();
                previousFailure = exception.failureKind();
                boolean canRetry = exception.retryable()
                        && stepDefinition.retryableFailures().contains(exception.failureKind())
                        && attempt < MAX_ATTEMPTS;
                if (!canRetry) throw exception;
            }
        }
        throw AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_STEP_ATTEMPTS_EXHAUSTED",
                "AI 단계를 완료하지 못했습니다.");
    }

    private AgentStepSnapshot startOrResumePending(
            AgentRunSnapshot run,
            UUID claimToken,
            StepDefinition definition,
            PromptDefinition prompt,
            StepInput input,
            String inputHash,
            long modelPolicyVersion,
            int attempt) {
        Optional<AgentStepSnapshot> pending =
                latestStep(run, definition.stepKey(), input.scopeKey())
                .filter(step -> step.status() == AgentStepStatus.PENDING && step.attempt() == attempt);
        if (pending.isPresent()) {
            return stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                    run.userId(), run.id(), pending.get().id(), claimToken,
                    AgentStepStatus.RUNNING, null, null, null, null, null, clock.instant()));
        }
        return stepCheckpointPort.start(startCommand(
                run, claimToken, definition, prompt, input, inputHash, modelPolicyVersion, attempt));
    }

    private StepStartCommand startCommand(
            AgentRunSnapshot run,
            UUID claimToken,
            StepDefinition definition,
            PromptDefinition prompt,
            StepInput input,
            String inputHash,
            long modelPolicyVersion,
            int attempt) {
        return new StepStartCommand(
                run.userId(), run.id(), claimToken, definition.stepKey(), input.scopeKey(),
                stepOrder(run, definition.stepKey()), definition.agentName(), attempt, MAX_ATTEMPTS,
                inputHash, input.sanitizedInputRefs(), definition.outputSchemaVersion(),
                modelPolicyVersion,
                prompt.promptVersion(), run.requestedQualityMode(), clock.instant());
    }

    private int stepOrder(AgentRunSnapshot run, String stepKey) {
        WorkflowDefinition definition = workflowRegistry.definition(run.workflowType(), run.workflowVersion());
        for (int index = 0; index < definition.steps().size(); index++) {
            if (definition.steps().get(index).stepKey().equals(stepKey)) return index + 1;
        }
        throw new WorkflowConfigurationException("AI_WORKFLOW_STEP_ORDER_MISSING");
    }

    private int nextAttempt(AgentRunSnapshot run, String stepKey, String scopeKey) {
        Optional<AgentStepSnapshot> pending = latestStep(run, stepKey, scopeKey)
                .filter(step -> step.status() == AgentStepStatus.PENDING);
        if (pending.isPresent()) return pending.get().attempt();
        return run.steps().stream().filter(step -> step.stepKey().equals(stepKey)
                        && java.util.Objects.equals(step.scopeKey(), scopeKey))
                .mapToInt(AgentStepSnapshot::attempt).max().orElse(0) + 1;
    }

    private Optional<AgentStepSnapshot> latestStep(
            AgentRunSnapshot run, String stepKey, String scopeKey) {
        return run.steps().stream().filter(step -> step.stepKey().equals(stepKey)
                        && java.util.Objects.equals(step.scopeKey(), scopeKey))
                .max(java.util.Comparator.comparingInt(AgentStepSnapshot::attempt));
    }

    private void skipStalePending(
            AgentRunSnapshot run, UUID claimToken, String stepKey, String scopeKey) {
        for (AgentStepSnapshot step : run.steps()) {
            if (step.status() != AgentStepStatus.PENDING
                    || !step.stepKey().equals(stepKey)
                    || java.util.Objects.equals(step.scopeKey(), scopeKey)) {
                continue;
            }
            stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                    run.userId(), run.id(), step.id(), claimToken, AgentStepStatus.SKIPPED,
                    null, null, null, null, null, clock.instant()));
        }
    }

    private void recordUsageIfPresent(
            AgentRunSnapshot run,
            UUID claimToken,
            AgentStepSnapshot step,
            ModelRoute route,
            AiUsage usage) {
        if (usage == null) return;
        AgentRunSnapshot current = current(run.userId(), run.id());
        budgetGuard.ensureNextCallCovered(current, usage.costUsd(), clock.instant());
        usageRecorderPort.record(new UsageRecordCommand(
                run.userId(), run.id(), step.id(), claimToken, run.workflowType().name(),
                usage.usageType(), usage.providerKey(), usage.productKey(), route.tier(),
                usage.inputUnits(), usage.cachedInputUnits(), usage.outputUnits(),
                usage.embeddingUnits(), usage.searchUnits(), usage.priceVersion(), usage.priceItemId(),
                usage.costUsd(), usage.durationMs(), clock.instant()));
    }

    private void checkpointFailure(
            AgentRunSnapshot run, UUID claimToken, AgentStepSnapshot step, AiExecutionException failure) {
        stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                run.userId(), run.id(), step.id(), claimToken, AgentStepStatus.FAILED,
                null, null, null, null, safeError(failure), clock.instant()));
    }

    private void updateProgress(
            UUID userId, UUID runId, UUID claimToken, String stepKey, int progress) {
        AgentRunSnapshot current = current(userId, runId);
        runStatePort.updateProgress(userId, runId, claimToken, current.stateVersion(),
                stepKey, Math.min(progress, 99), clock.instant());
    }

    private boolean completeCancellationIfRequested(AgentRunSnapshot run, UUID claimToken) {
        if (!runStatePort.isCancellationRequested(run.userId(), run.id(), claimToken)) return false;
        cancellationPort.completeCancellation(run.userId(), run.id(), claimToken, clock.instant());
        return true;
    }

    private void failRun(UUID userId, UUID runId, UUID claimToken, AiExecutionException failure) {
        AgentRunSnapshot run;
        try {
            run = current(userId, runId);
        } catch (RuntimeException ignored) {
            return;
        }
        if (run.status() != AgentRunStatus.RUNNING || !claimToken.equals(run.claimToken())) return;
        if (completeCancellationIfRequested(run, claimToken)) return;
        try {
            applyFailureHandlers(run, failure);
            budgetGuard.releaseUnused(run, clock.instant());
            run = current(userId, runId);
            runStatePort.transition(new AgentRunTransitionCommand(
                    userId, runId, claimToken, run.stateVersion(), AgentRunStatus.FAILED,
                    run.currentStep(), run.progressPercent(), run.highestModelTierUsed(),
                    run.actualCostUsd(), failure.retryable(), null, safeError(failure),
                    run.partialResult(), clock.instant()));
        } catch (RuntimeException ignored) {
            // A concurrent cancel/reconciliation owns the terminal transition.
        }
    }

    private void interruptRun(UUID userId, UUID runId, UUID claimToken) {
        AgentRunSnapshot run = current(userId, runId);
        try {
            applyFailureHandlers(
                    run,
                    AiExecutionException.nonRetryable(
                            FailureKind.INTERRUPTION,
                            "AI_WORKER_INTERRUPTED",
                            "AI 실행 작업이 중단되었습니다."));
            budgetGuard.releaseUnused(run, clock.instant());
            run = current(userId, runId);
            runStatePort.transition(new AgentRunTransitionCommand(
                    userId, runId, claimToken, run.stateVersion(), AgentRunStatus.INTERRUPTED,
                    run.currentStep(), run.progressPercent(), run.highestModelTierUsed(),
                    run.actualCostUsd(), true, null,
                    new SafeError("AI_WORKER_INTERRUPTED", "AI 실행 작업이 중단되었습니다."),
                    run.partialResult(), clock.instant()));
        } catch (RuntimeException ignored) {
            // Reconciliation may have completed the same interruption.
        }
    }

    private void applyFresh(
            WorkflowStepExecutor<?> executor,
            Object validated,
            JsonNode minimalOutput,
            StepExecutionContext context,
            AgentRunSnapshot run,
            AgentStepSnapshot step,
            String inputHash) {
        domainApply(executorDomainApply(executor, validated, minimalOutput, context),
                run, step, inputHash, minimalOutput);
    }

    private void applyReused(
            WorkflowStepExecutor<?> executor,
            JsonNode minimalOutput,
            StepExecutionContext context,
            AgentRunSnapshot run,
            AgentStepSnapshot step,
            String inputHash) {
        domainApply(executor.domainApplyFromMinimal(minimalOutput, context),
                run, step, inputHash, minimalOutput);
    }

    private void domainApply(
            Optional<DomainApplyPlan> plan,
            AgentRunSnapshot run,
            AgentStepSnapshot step,
            String inputHash,
            JsonNode minimalOutput) {
        plan.ifPresent(value -> {
            try {
                domainResultApplyPort.apply(new DomainResultCommand(
                        run.userId(), run.id(), step.id(), value.resourceType(), value.resourceId(),
                        value.expectedResourceVersion(), inputHash, minimalOutput));
            } catch (BusinessException ignored) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.DOMAIN_VALIDATION,
                        "AI_DOMAIN_COMMAND_INVALID",
                        "AI 결과를 현재 리소스에 적용할 수 없습니다.");
            }
        });
    }

    private void validatePromptContract(StepDefinition step, PromptDefinition prompt) {
        if (!prompt.outputSchemaVersion().equals(step.outputSchemaVersion())
                || !prompt.toolAllowlist().equals(step.toolAllowlist())
                || prompt.maxModelCalls() != step.maxModelCalls()) {
            throw new WorkflowConfigurationException("AI_PROMPT_STEP_CONTRACT_MISMATCH");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object validate(
            WorkflowStepExecutor executor, String rawJson, StepExecutionContext context) {
        return outputValidator.validate(rawJson, executor.outputContract(context));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private JsonNode minimalOutput(WorkflowStepExecutor executor, Object value) {
        JsonNode output = executor.minimalOutput(value, objectMapper);
        if (output == null || !output.isObject()) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    "AI_MINIMAL_OUTPUT_INVALID",
                    "AI 결과 참조를 저장할 수 없습니다.");
        }
        return output;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<DomainApplyPlan> executorDomainApply(
            WorkflowStepExecutor executor,
            Object value,
            JsonNode minimalOutput,
            StepExecutionContext context) {
        return executor.domainApply(value, minimalOutput, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object executorEphemeralOutput(WorkflowStepExecutor executor, Object value) {
        return executor.ephemeralOutput(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<com.hiresemble.agentrun.domain.model.RequiredUserAction> executorRequiredUserAction(
            WorkflowStepExecutor executor,
            Object value,
            JsonNode minimalOutput,
            StepExecutionContext context) {
        return executor.requiredUserAction(value, minimalOutput, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<PartialResult> executorPartialResult(
            WorkflowStepExecutor executor,
            Object value,
            JsonNode minimalOutput,
            StepExecutionContext context) {
        return executor.partialResult(value, minimalOutput, context);
    }

    private void applyFailureHandlers(AgentRunSnapshot run, AiExecutionException failure) {
        for (WorkflowFailureHandler handler : failureHandlers) {
            if (!handler.supports(run)) continue;
            try {
                handler.onFailure(run, failure);
            } catch (RuntimeException ignored) {
                // The Run still needs a safe terminal transition; reconciliation owns final repair.
            }
        }
    }

    private AgentRunSnapshot current(UUID userId, UUID runId) {
        return runQueryPort.findByOwner(userId, runId).orElseThrow(() ->
                AiExecutionException.nonRetryable(
                        FailureKind.OWNER,
                        "RESOURCE_NOT_FOUND",
                        "요청한 리소스를 찾을 수 없습니다."));
    }

    private String inputHash(
            AgentRunSnapshot run,
            ContextSnapshot context,
            StepDefinition definition,
            PromptDefinition prompt,
            StepInput input) {
        return sha256(String.join("|",
                run.userId().toString(),
                run.workflowType().name(),
                run.workflowVersion(),
                definition.stepKey(),
                input.scopeKey() == null ? "" : input.scopeKey(),
                run.canonicalInputHash(),
                input.canonicalInputMaterial(),
                input.sanitizedInputRefs().toString(),
                context.contextHash(),
                context.truncationSummary().toString(),
                prompt.promptVersion(),
                definition.outputSchemaVersion(),
                Long.toString(context.modelPolicyVersion()),
                definition.preferredTier().name(),
                run.requestedQualityMode() == null ? "" : run.requestedQualityMode().name()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private SafeError safeError(AiExecutionException failure) {
        return new SafeError(failure.safeCode(), failure.safeMessage());
    }

    private AiExecutionException mapBusiness(BusinessException failure) {
        if (failure.errorCode() == ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED) {
            return AiExecutionException.nonRetryable(
                    FailureKind.BUDGET,
                    ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED.code(),
                    ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED.defaultMessage());
        }
        if (failure.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            return AiExecutionException.nonRetryable(
                    FailureKind.OWNER,
                    ErrorCode.RESOURCE_NOT_FOUND.code(),
                    ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
        }
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                "AI_DOMAIN_COMMAND_INVALID",
                "AI 결과를 현재 리소스에 적용할 수 없습니다.");
    }

    private record StepResult(
            JsonNode minimalOutput,
            Object ephemeralOutput,
            PartialResult partialResult,
            com.hiresemble.agentrun.domain.model.RequiredUserAction requiredUserAction,
            boolean cancelledOrTerminal) {
        private static StepResult terminal() {
            return new StepResult(null, null, null, null, true);
        }

        private static StepResult waiting(
                JsonNode minimalOutput,
                Object ephemeralOutput,
                com.hiresemble.agentrun.domain.model.RequiredUserAction requiredUserAction) {
            return new StepResult(minimalOutput, ephemeralOutput, null, requiredUserAction, false);
        }
    }
}
