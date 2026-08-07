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
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.ai.budget.AiCallCostEstimator;
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
import com.hiresemble.ai.workflow.TerminalPartialPolicy;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainApplyPlan;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AiCallCostEstimator callCostEstimator;
    private final StepCompletionTransaction stepCompletionTransaction;
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
            AiCallCostEstimator callCostEstimator,
            ObjectMapper objectMapper,
            Clock clock,
            List<WorkflowFailureHandler> failureHandlers,
            StepCompletionTransaction stepCompletionTransaction) {
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
        this.callCostEstimator = java.util.Objects.requireNonNull(callCostEstimator);
        this.stepCompletionTransaction = java.util.Objects.requireNonNull(
                stepCompletionTransaction);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.failureHandlers = failureHandlers == null ? List.of() : List.copyOf(failureHandlers);
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
            List<WorkflowFailureHandler> failureHandlers,
            StepCompletionTransaction stepCompletionTransaction) {
        this(
                workflowRegistry,
                contextBuilder,
                modelRouter,
                promptRegistry,
                outputValidator,
                chatGateway,
                embeddingGateway,
                webSearchGateway,
                runQueryPort,
                runStatePort,
                stepCheckpointPort,
                usageRecorderPort,
                domainResultApplyPort,
                cancellationPort,
                leaseHeartbeatPort,
                budgetGuard,
                (run, route, prompt, input) -> BigDecimal.ZERO,
                objectMapper,
                clock,
                failureHandlers,
                stepCompletionTransaction);
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
        PartialAccumulator partial = new PartialAccumulator();
        partial.merge(claimed.run().partialResult());
        Set<String> failedScopes = resumedFailedScopes(definition, claimed.run());
        failedScopes.forEach(partial::resumeFailure);
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
            StepExecutionContext preparationContext =
                    new StepExecutionContext(run, context, upstreamOutputs, ephemeralOutputs);
            PromptDefinition prompt = promptRegistry.require(
                    run.workflowType(), run.workflowVersion(), stepDefinition.stepKey());
            validatePromptContract(stepDefinition, prompt);
            List<StepInput> inputs =
                    List.copyOf(executableStep.executor().prepareInputs(preparationContext));
            validateFanOut(stepDefinition, inputs);

            BigDecimal weightBeforeStep = completedWeight;
            if (inputs.isEmpty()) {
                completedWeight = completedWeight.add(stepDefinition.progressWeight());
                updateProgress(
                        run.userId(),
                        run.id(),
                        claimed.claimToken(),
                        stepDefinition.stepKey(),
                        completedWeight.intValue());
                continue;
            }

            for (int scopeIndex = 0; scopeIndex < inputs.size(); scopeIndex++) {
                StepInput input = inputs.get(scopeIndex);
                String scopeKey = stepDefinition.maxFanOut() > 1
                        ? input.scopeKey()
                        : null;
                if (scopeKey != null && failedScopes.contains(scopeKey)) {
                    updateFanOutProgress(
                            run,
                            claimed.claimToken(),
                            stepDefinition,
                            weightBeforeStep,
                            scopeIndex + 1,
                            inputs.size());
                    continue;
                }

                AgentRunSnapshot scopeRun = current(run.userId(), run.id());
                if (scopeRun.status().isTerminal()) return;
                if (completeCancellationIfRequested(scopeRun, claimed.claimToken())) return;
                StepExecutionContext executionContext = new StepExecutionContext(
                                scopeRun, context, upstreamOutputs, ephemeralOutputs)
                        .forScope(scopeKey);
                String inputHash =
                        inputHash(scopeRun, context, stepDefinition, prompt, input);
                StepResult result;
                try {
                    result = executeStepInput(
                            claimed,
                            definition,
                            stepDefinition,
                            executableStep.executor(),
                            prompt,
                            input,
                            inputHash,
                            executionContext);
                } catch (AiExecutionException failure) {
                    if (scopeKey == null
                            || !executableStep
                                    .executor()
                                    .continueAfterScopeFailure(failure, executionContext)) {
                        throw failure;
                    }
                    failedScopes.add(scopeKey);
                    partial.fail(scopeKey, failure);
                    updateFanOutProgress(
                            scopeRun,
                            claimed.claimToken(),
                            stepDefinition,
                            weightBeforeStep,
                            scopeIndex + 1,
                            inputs.size());
                    continue;
                }
                if (result.cancelledOrTerminal()) return;
                if (result.requiredUserAction() != null) {
                    AgentRunSnapshot beforeWaiting =
                            current(scopeRun.userId(), scopeRun.id());
                    budgetGuard.releaseUnused(beforeWaiting, clock.instant());
                    AgentRunSnapshot released =
                            current(scopeRun.userId(), scopeRun.id());
                    runStatePort.transition(new AgentRunTransitionCommand(
                            released.userId(),
                            released.id(),
                            claimed.claimToken(),
                            released.stateVersion(),
                            AgentRunStatus.WAITING_USER,
                            stepDefinition.stepKey(),
                            fanOutProgress(
                                    weightBeforeStep,
                                    stepDefinition.progressWeight(),
                                    scopeIndex,
                                    inputs.size()),
                            released.highestModelTierUsed(),
                            released.actualCostUsd(),
                            false,
                            result.requiredUserAction(),
                            null,
                            partial.valueOrNull(),
                            clock.instant()));
                    return;
                }
                String outputKey =
                        StepExecutionContext.outputKey(stepDefinition.stepKey(), scopeKey);
                upstreamOutputs.put(outputKey, result.minimalOutput());
                ephemeralOutputs.put(outputKey, result.ephemeralOutput());
                partial.merge(result.partialResult());
                updateFanOutProgress(
                        scopeRun,
                        claimed.claimToken(),
                        stepDefinition,
                        weightBeforeStep,
                        scopeIndex + 1,
                        inputs.size());
            }
            completedWeight = weightBeforeStep.add(stepDefinition.progressWeight());
        }

        AgentRunSnapshot completed = current(claimed.run().userId(), claimed.run().id());
        if (partial.hasFailures()) {
            completePartialResult(
                    completed,
                    claimed.claimToken(),
                    partial,
                    contribution.terminalPartialPolicy());
            return;
        }
        budgetGuard.settleSuccess(completed, clock.instant());
        completed = current(completed.userId(), completed.id());
        runStatePort.transition(new AgentRunTransitionCommand(
                completed.userId(), completed.id(), claimed.claimToken(), completed.stateVersion(),
                AgentRunStatus.SUCCEEDED, completed.currentStep(), 100,
                completed.highestModelTierUsed(), completed.actualCostUsd(), false,
                null,
                null,
                partial.valueOrNull() == null
                        ? completed.partialResult()
                        : partial.valueOrNull(),
                clock.instant()));
    }

    private StepResult executeStepInput(
            ClaimedAgentRun claimed,
            WorkflowDefinition definition,
            StepDefinition stepDefinition,
            WorkflowStepExecutor<?> executor,
            PromptDefinition prompt,
            StepInput input,
            String inputHash,
            StepExecutionContext executionContext) {
        AgentRunSnapshot run = executionContext.run();
        if (executor.skip(executionContext)) {
            JsonNode skippedOutput = objectMapper.createObjectNode().put("skipped", true);
            Optional<AgentStepSnapshot> alreadySkipped =
                    latestStep(run, stepDefinition.stepKey(), input.scopeKey())
                            .filter(step -> step.status() == AgentStepStatus.SKIPPED);
            if (alreadySkipped.isEmpty()) {
                AgentStepSnapshot pending = latestStep(run, stepDefinition.stepKey(), input.scopeKey())
                        .filter(step -> step.status() == AgentStepStatus.PENDING)
                        .orElseGet(() -> stepCheckpointPort.start(startCommand(
                                run,
                                claimed.claimToken(),
                                stepDefinition,
                                prompt,
                                input,
                                inputHash,
                                executionContext.contextSnapshot().modelPolicyVersion(),
                                nextAttempt(run, stepDefinition.stepKey(), input.scopeKey()))));
                stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                        run.userId(), run.id(), pending.id(), claimed.claimToken(),
                        AgentStepStatus.SKIPPED, null, null, null, null, null, clock.instant()));
            }
            return new StepResult(skippedOutput, skippedOutput, null, null, false);
        }
        Optional<ReusableStepSnapshot> reusable = executor.reusable()
                ? runQueryPort.findReusableStep(
                        run.userId(),
                        stepDefinition.stepKey(),
                        input.scopeKey(),
                        inputHash,
                        run.requestedQualityMode())
                : Optional.empty();
        Optional<AgentStepSnapshot> completedInThisRun = executor.reusable()
                ? latestStep(run, stepDefinition.stepKey(), input.scopeKey())
                        .filter(step -> step.status() == AgentStepStatus.SUCCEEDED
                                || step.status() == AgentStepStatus.REUSED)
                : Optional.empty();
        if (completedInThisRun.isPresent()) {
            ReusableStepSnapshot output = reusable.orElseThrow(() ->
                    AiExecutionException.nonRetryable(
                            FailureKind.CONFIGURATION,
                            "AI_STEP_CHECKPOINT_INCOMPLETE",
                            "저장된 AI 단계 결과를 복구하지 못했습니다."));
            return new StepResult(
                    output.minimalOutput(),
                    executor.ephemeralOutputFromMinimal(output.minimalOutput()),
                    executorPartialResultFromMinimal(
                                    executor, output.minimalOutput(), executionContext)
                            .orElse(null),
                    null,
                    false);
        }
        if (reusable.isPresent()) {
            if (completeCancellationIfRequested(
                    current(run.userId(), run.id()), claimed.claimToken())) {
                return StepResult.terminal();
            }
            AtomicReference<DomainStepCompletion> completion = new AtomicReference<>();
            try {
                stepCompletionTransaction.execute(() -> {
                    DomainStepCompletion domainCompletion =
                            executorCompleteReused(
                                    executor,
                                    reusable.get().minimalOutput(),
                                    executionContext);
                    if (!domainCompletion
                            .minimalOutput()
                            .equals(reusable.get().minimalOutput())) {
                        throw new WorkflowConfigurationException(
                                "AI_REUSED_OUTPUT_MUTATION_INVALID");
                    }
                    AgentStepSnapshot reused = stepCheckpointPort.reuse(
                            startCommand(
                                    run,
                                    claimed.claimToken(),
                                    stepDefinition,
                                    prompt,
                                    input,
                                    inputHash,
                                    executionContext.contextSnapshot().modelPolicyVersion(),
                                    1),
                            reusable.get());
                    domainApply(
                            domainCompletion.genericDomainApply(),
                            run,
                            reused,
                            inputHash,
                            domainCompletion.minimalOutput());
                    completion.set(domainCompletion);
                });
            } catch (RuntimeException exception) {
                AiExecutionException failure = completionFailure(exception);
                AgentRunSnapshot afterRollback = current(run.userId(), run.id());
                if (completeCancellationIfRequested(
                        afterRollback, claimed.claimToken())) {
                    return StepResult.terminal();
                }
                AgentStepSnapshot failed = startOrResumePending(
                        afterRollback,
                        claimed.claimToken(),
                        stepDefinition,
                        prompt,
                        input,
                        inputHash,
                        executionContext.contextSnapshot().modelPolicyVersion(),
                        1);
                checkpointFailure(
                        afterRollback,
                        claimed.claimToken(),
                        failed,
                        failure);
                throw failure;
            }
            DomainStepCompletion domainCompletion = completion.get();
            return new StepResult(
                    domainCompletion.minimalOutput(),
                    executor.ephemeralOutputFromMinimal(
                            domainCompletion.minimalOutput()),
                    domainCompletion.partialResult(),
                    null,
                    false);
        }

        if (input.waitsForUser()) {
            AgentStepSnapshot waitingStep = startOrResumePending(
                    run,
                    claimed.claimToken(),
                    stepDefinition,
                    prompt,
                    input,
                    inputHash,
                    executionContext.contextSnapshot().modelPolicyVersion(),
                    1);
            stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                    run.userId(),
                    run.id(),
                    waitingStep.id(),
                    claimed.claimToken(),
                    AgentStepStatus.WAITING_USER,
                    null,
                    null,
                    null,
                    null,
                    null,
                    clock.instant()));
            return StepResult.waiting(null, null, input.requiredUserAction());
        }

        return executeWithAttempts(
                claimed,
                definition,
                stepDefinition,
                executor,
                prompt,
                input,
                inputHash,
                executionContext);
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
        String correctionGuidance = null;
        int semanticCorrections = 0;
        int transportRetries = 0;

        for (int attempt = firstAttempt; attempt <= MAX_ATTEMPTS; attempt++) {
            run = current(run.userId(), run.id());
            if (completeCancellationIfRequested(run, claimed.claimToken())) return StepResult.terminal();
            boolean providerRequired = stepDefinition.requiresProvider()
                    && executor.requiresProvider(executionContext);
            ModelRoute route = modelRouter.route(new RoutingRequest(
                    run.workflowType(), stepDefinition.stepKey(), run.requestedQualityMode(),
                    stepDefinition.toolAllowlist().isEmpty() ? run.requestedModel() : null,
                    stepDefinition.preferredTier(), providerRequired,
                    executionContext.contextSnapshot().highQualityEnabled(),
                    executionContext.contextSnapshot().budgetReservationConfirmed(),
                    attempt, previousTier, previousFailure));
            AgentStepSnapshot step = startOrResumePending(
                    run, claimed.claimToken(), stepDefinition, prompt, input, inputHash,
                    executionContext.contextSnapshot().modelPolicyVersion(), attempt);
            boolean activeStep = true;
            String currentCorrectionGuidance = correctionGuidance;
            try {
                AgentRunSnapshot beforeCall = current(run.userId(), run.id());
                budgetGuard.ensureNextCallCovered(
                        beforeCall,
                        callCostEstimator.maximumCallCost(beforeCall, route, prompt, input),
                        clock.instant());
                AiGatewayResponse response = leaseHeartbeatPort.maintain(
                        run.userId(), run.id(), claimed.claimToken(),
                        () -> executor.invoke(new GatewayInvocation(
                                input,
                                route,
                                withCorrectionGuidance(prompt, currentCorrectionGuidance),
                                chatGateway,
                                embeddingGateway,
                                webSearchGateway,
                                executionContext)));
                recordUsages(run, claimed.claimToken(), step, route, response.usages());
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) {
                    return StepResult.terminal();
                }
                Object validated = validate(executor, response.rawJson(), executionContext);
                JsonNode minimalOutput = minimalOutput(executor, validated, executionContext);
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
                            executorEphemeralOutput(executor, validated, executionContext),
                            requiredAction.get());
                }
                Object ephemeralOutput =
                        executorEphemeralOutput(executor, validated, executionContext);
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) {
                    return StepResult.terminal();
                }
                AgentRunSnapshot completionRun = run;
                AtomicReference<DomainStepCompletion> completion = new AtomicReference<>();
                try {
                    stepCompletionTransaction.execute(() -> {
                        DomainStepCompletion domainCompletion = executorCompleteFresh(
                                executor,
                                validated,
                                minimalOutput,
                                executionContext);
                        domainApply(
                                domainCompletion.genericDomainApply(),
                                completionRun,
                                step,
                                inputHash,
                                domainCompletion.minimalOutput());
                        stepCheckpointPort.checkpoint(new StepCheckpointCommand(
                                completionRun.userId(),
                                completionRun.id(),
                                step.id(),
                                claimed.claimToken(),
                                AgentStepStatus.SUCCEEDED,
                                sha256(domainCompletion.minimalOutput().toString()),
                                domainCompletion.minimalOutput(),
                                route.tier(),
                                null,
                                null,
                                clock.instant()));
                        completion.set(domainCompletion);
                    });
                } catch (RuntimeException exception) {
                    throw completionFailure(exception);
                }
                activeStep = false;
                DomainStepCompletion domainCompletion = completion.get();
                return new StepResult(
                        domainCompletion.minimalOutput(),
                        ephemeralOutput,
                        domainCompletion.partialResult(),
                        null,
                        false);
            } catch (AiExecutionException exception) {
                recordUsages(
                        run,
                        claimed.claimToken(),
                        step,
                        route,
                        exception.incurredUsages());
                if (completeCancellationIfRequested(current(run.userId(), run.id()), claimed.claimToken())) {
                    return StepResult.terminal();
                }
                if (activeStep) checkpointFailure(run, claimed.claimToken(), step, exception);
                previousTier = route.tier();
                previousFailure = exception.failureKind();
                if (exception.correctionGuidance() != null) {
                    correctionGuidance = exception.correctionGuidance();
                }
                boolean retryClassAllowed = exception.retryable()
                        && stepDefinition.retryableFailures().contains(exception.failureKind());
                boolean retryStateAvailable;
                if (exception.isSemanticCorrectionFailure()) {
                    retryStateAvailable = semanticCorrections
                            < exception.maxAutomaticAttempts() - 1;
                    if (retryStateAvailable) {
                        semanticCorrections++;
                    }
                } else if (exception.isTransportFailure()) {
                    retryStateAvailable = transportRetries
                            < exception.maxAutomaticAttempts() - 1;
                    if (retryStateAvailable) {
                        transportRetries++;
                    }
                } else {
                    retryStateAvailable = false;
                }
                boolean canRetry = retryClassAllowed
                        && retryStateAvailable
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

    private void recordUsages(
            AgentRunSnapshot run,
            UUID claimToken,
            AgentStepSnapshot step,
            ModelRoute route,
            java.util.List<AiUsage> usages) {
        if (usages == null || usages.isEmpty()) return;
        BigDecimal totalCost = usages.stream()
                .map(AiUsage::costUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        AgentRunSnapshot current = current(run.userId(), run.id());
        budgetGuard.ensureNextCallCovered(current, totalCost, clock.instant());
        for (AiUsage usage : usages) {
            usageRecorderPort.record(new UsageRecordCommand(
                    run.userId(), run.id(), step.id(), claimToken, run.workflowType().name(),
                    usage.usageType(), usage.providerKey(), usage.productKey(), route.tier(),
                    usage.inputUnits(), usage.cachedInputUnits(), usage.outputUnits(),
                    usage.embeddingUnits(), usage.searchUnits(), usage.priceVersion(), usage.priceItemId(),
                    usage.costUsd(), usage.durationMs(), usage.providerCallId(), clock.instant()));
        }
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
        int bounded = Math.min(progress, 99);
        if (bounded <= current.progressPercent()) {
            return;
        }
        runStatePort.updateProgress(userId, runId, claimToken, current.stateVersion(),
                stepKey, bounded, clock.instant());
    }

    private void validateFanOut(
            StepDefinition definition, List<StepInput> inputs) {
        if (inputs == null
                || inputs.size() > definition.maxFanOut()
                || (definition.maxFanOut() == 1 && inputs.size() != 1)
                || (definition.maxFanOut() > 1
                        && inputs.stream().anyMatch(input -> input.scopeKey() == null))) {
            throw new WorkflowConfigurationException("AI_WORKFLOW_FANOUT_INVALID");
        }
        Set<String> scopes = new java.util.HashSet<>();
        if (inputs.stream().anyMatch(input -> !scopes.add(input.scopeKey()))) {
            throw new WorkflowConfigurationException("AI_WORKFLOW_FANOUT_SCOPE_DUPLICATE");
        }
    }

    private void updateFanOutProgress(
            AgentRunSnapshot run,
            UUID claimToken,
            StepDefinition definition,
            BigDecimal weightBeforeStep,
            int completedScopes,
            int totalScopes) {
        updateProgress(
                run.userId(),
                run.id(),
                claimToken,
                definition.stepKey(),
                fanOutProgress(
                        weightBeforeStep,
                        definition.progressWeight(),
                        completedScopes,
                        totalScopes));
    }

    private int fanOutProgress(
            BigDecimal weightBeforeStep,
            BigDecimal stepWeight,
            int completedScopes,
            int totalScopes) {
        if (totalScopes < 1 || completedScopes < 0 || completedScopes > totalScopes) {
            throw new WorkflowConfigurationException("AI_WORKFLOW_FANOUT_PROGRESS_INVALID");
        }
        return weightBeforeStep
                .add(stepWeight
                        .multiply(BigDecimal.valueOf(completedScopes))
                        .divide(
                                BigDecimal.valueOf(totalScopes),
                                6,
                                java.math.RoundingMode.DOWN))
                .intValue();
    }

    private Set<String> resumedFailedScopes(
            WorkflowDefinition definition, AgentRunSnapshot run) {
        Set<String> fanOutSteps = definition.steps().stream()
                .filter(step -> step.maxFanOut() > 1)
                .map(StepDefinition::stepKey)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, AgentStepSnapshot> latestByStepAndScope = new HashMap<>();
        run.steps().stream()
                .filter(step -> step.scopeKey() != null
                        && fanOutSteps.contains(step.stepKey()))
                .forEach(step -> latestByStepAndScope.merge(
                        step.stepKey() + "\u001f" + step.scopeKey(),
                        step,
                        (left, right) -> left.attempt() >= right.attempt()
                                ? left
                                : right));
        LinkedHashSet<String> failed = new LinkedHashSet<>();
        latestByStepAndScope.values().stream()
                .filter(step -> step.status() == AgentStepStatus.FAILED)
                .sorted(java.util.Comparator.comparing(AgentStepSnapshot::scopeKey))
                .forEach(step -> failed.add(step.scopeKey()));
        return failed;
    }

    private void completePartialResult(
            AgentRunSnapshot run,
            UUID claimToken,
            PartialAccumulator partial,
            TerminalPartialPolicy policy) {
        PartialResult partialResult = partial.valueOrNull();
        TerminalPartialPolicy.Decision decision =
                policy.decide(partialResult, partial.retryable());
        if (decision.outcome() == TerminalPartialPolicy.Outcome.SUCCEEDED) {
            budgetGuard.settleSuccess(run, clock.instant());
            AgentRunSnapshot settled = current(run.userId(), run.id());
            runStatePort.transition(new AgentRunTransitionCommand(
                    settled.userId(),
                    settled.id(),
                    claimToken,
                    settled.stateVersion(),
                    AgentRunStatus.SUCCEEDED,
                    settled.currentStep(),
                    100,
                    settled.highestModelTierUsed(),
                    settled.actualCostUsd(),
                    false,
                    null,
                    null,
                    partialResult,
                    clock.instant()));
            return;
        }
        budgetGuard.releaseUnused(run, clock.instant());
        AgentRunSnapshot released = current(run.userId(), run.id());
        runStatePort.transition(new AgentRunTransitionCommand(
                released.userId(),
                released.id(),
                claimToken,
                released.stateVersion(),
                AgentRunStatus.FAILED,
                released.currentStep(),
                100,
                released.highestModelTierUsed(),
                released.actualCostUsd(),
                decision.retryable(),
                null,
                new SafeError(
                        decision.safeErrorCode(),
                        decision.safeMessage()),
                partialResult,
                clock.instant()));
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

    private AiExecutionException completionFailure(RuntimeException exception) {
        if (exception instanceof AiExecutionException aiExecutionException) {
            return aiExecutionException;
        }
        if (exception instanceof BusinessException businessException) {
            return mapBusiness(businessException);
        }
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_DOMAIN_APPLY_FAILED",
                "AI 결과를 안전하게 적용하지 못했습니다.");
    }

    private void validatePromptContract(StepDefinition step, PromptDefinition prompt) {
        if (!prompt.outputSchemaVersion().equals(step.outputSchemaVersion())
                || !prompt.toolAllowlist().equals(step.toolAllowlist())
                || prompt.maxModelCalls() != step.maxModelCalls()) {
            throw new WorkflowConfigurationException("AI_PROMPT_STEP_CONTRACT_MISMATCH");
        }
    }

    private PromptDefinition withCorrectionGuidance(
            PromptDefinition prompt, String correctionGuidance) {
        if (correctionGuidance == null) return prompt;
        return new PromptDefinition(
                prompt.key(),
                prompt.promptVersion(),
                prompt.inputType(),
                prompt.outputType(),
                prompt.outputSchemaVersion(),
                prompt.toolAllowlist(),
                prompt.maxInputTokens(),
                prompt.maxOutputTokens(),
                prompt.maxModelCalls(),
                prompt.instructions()
                        + "\nCorrection for this bounded retry:\n"
                        + correctionGuidance);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object validate(
            WorkflowStepExecutor executor, String rawJson, StepExecutionContext context) {
        return outputValidator.validate(rawJson, executor.outputContract(context));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private JsonNode minimalOutput(
            WorkflowStepExecutor executor,
            Object value,
            StepExecutionContext context) {
        JsonNode output = executor.minimalOutput(value, objectMapper, context);
        if (output == null || !output.isObject()) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    "AI_MINIMAL_OUTPUT_INVALID",
                    "AI 결과 참조를 저장할 수 없습니다.");
        }
        return output;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private DomainStepCompletion executorCompleteFresh(
            WorkflowStepExecutor executor,
            Object value,
            JsonNode minimalOutput,
            StepExecutionContext context) {
        return executor.completeFresh(value, minimalOutput, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private DomainStepCompletion executorCompleteReused(
            WorkflowStepExecutor executor,
            JsonNode minimalOutput,
            StepExecutionContext context) {
        return executor.completeReused(minimalOutput, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object executorEphemeralOutput(
            WorkflowStepExecutor executor,
            Object value,
            StepExecutionContext context) {
        return executor.ephemeralOutput(value, context);
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
    private Optional<PartialResult> executorPartialResultFromMinimal(
            WorkflowStepExecutor executor,
            JsonNode minimalOutput,
            StepExecutionContext context) {
        return executor.partialResultFromMinimal(minimalOutput, context);
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

    private static final class PartialAccumulator {
        private final LinkedHashSet<String> succeeded = new LinkedHashSet<>();
        private final LinkedHashSet<String> failed = new LinkedHashSet<>();
        private final LinkedHashMap<String, ResourceReference> resultRefs =
                new LinkedHashMap<>();
        private boolean retryable = true;

        private void merge(PartialResult value) {
            if (value == null) return;
            for (String scopeKey : value.succeededScopeKeys()) {
                failed.remove(scopeKey);
                succeeded.add(scopeKey);
            }
            for (String scopeKey : value.failedScopeKeys()) {
                succeeded.remove(scopeKey);
                failed.add(scopeKey);
            }
            for (ResourceReference reference : value.resultRefs()) {
                resultRefs.put(
                        reference.resourceType() + ":" + reference.resourceId(),
                        reference);
            }
        }

        private void fail(String scopeKey, AiExecutionException failure) {
            succeeded.remove(scopeKey);
            failed.add(scopeKey);
            retryable &= failure.retryable();
        }

        private void resumeFailure(String scopeKey) {
            succeeded.remove(scopeKey);
            failed.add(scopeKey);
        }

        private boolean hasFailures() {
            return !failed.isEmpty();
        }

        private boolean retryable() {
            return retryable;
        }

        private PartialResult valueOrNull() {
            if (succeeded.isEmpty() && failed.isEmpty() && resultRefs.isEmpty()) {
                return null;
            }
            return new PartialResult(
                    List.copyOf(succeeded),
                    List.copyOf(failed),
                    List.copyOf(resultRefs.values()));
        }
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
