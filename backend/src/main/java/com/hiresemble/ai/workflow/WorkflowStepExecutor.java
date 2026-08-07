package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Testable executable contribution boundary; production workflows add these only in their own phase. */
public interface WorkflowStepExecutor<T> {

    StepInput prepare(StepExecutionContext context);

    /**
     * Returns the deterministic, bounded inputs for this fixed step.
     *
     * <p>The default preserves the original one-step/one-input contract. A canonical fan-out
     * executor returns its already validated scope list in stable domain order; the orchestrator,
     * not a model, owns iteration and the registry's {@code maxFanOut} bound.
     */
    default List<StepInput> prepareInputs(StepExecutionContext context) {
        return List.of(prepare(context));
    }

    AiGatewayResponse invoke(GatewayInvocation invocation);

    Contract<T> outputContract();

    default Contract<T> outputContract(StepExecutionContext context) {
        return outputContract();
    }

    JsonNode minimalOutput(T validatedOutput, ObjectMapper objectMapper);

    /**
     * Builds the persisted minimal output when a validated provider value must first cross a
     * server-owned mapping boundary. Existing executors keep the original context-free behavior.
     */
    default JsonNode minimalOutput(
            T validatedOutput,
            ObjectMapper objectMapper,
            StepExecutionContext context) {
        return minimalOutput(validatedOutput, objectMapper);
    }

    default Optional<DomainApplyPlan> domainApply(
            T validatedOutput, JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    default Optional<DomainApplyPlan> domainApplyFromMinimal(
            JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    /**
     * Completes a fresh step inside the checkpoint transaction.
     *
     * <p>Workflows whose Backend command returns the durable result identifier may override this
     * method and return a reference-only checkpoint output built from that command result. Existing
     * workflows continue through {@link #domainApply(Object, JsonNode, StepExecutionContext)}.
     */
    default DomainStepCompletion completeFresh(
            T validatedOutput, JsonNode minimalOutput, StepExecutionContext context) {
        return new DomainStepCompletion(
                minimalOutput,
                domainApply(validatedOutput, minimalOutput, context),
                partialResult(validatedOutput, minimalOutput, context).orElse(null));
    }

    /** Completes a reused step inside the same atomic checkpoint/domain boundary. */
    default DomainStepCompletion completeReused(
            JsonNode minimalOutput, StepExecutionContext context) {
        return new DomainStepCompletion(
                minimalOutput,
                domainApplyFromMinimal(minimalOutput, context),
                partialResultFromMinimal(minimalOutput, context).orElse(null));
    }

    /**
     * Full validated values may be handed to the immediately following fixed step in memory only.
     * They are never part of an Agent Step checkpoint. A step that needs this handoff must disable
     * persisted reuse because a process restart cannot reconstruct the value from minimal output.
     */
    default Object ephemeralOutput(T validatedOutput) {
        return validatedOutput;
    }

    /** Maps a validated value into the in-memory handoff owned by the current workflow run. */
    default Object ephemeralOutput(T validatedOutput, StepExecutionContext context) {
        return ephemeralOutput(validatedOutput);
    }

    default Object ephemeralOutputFromMinimal(JsonNode minimalOutput) {
        return minimalOutput;
    }

    default boolean reusable() {
        return true;
    }

    /**
     * A statically model-backed step may take a deterministic local branch for an already
     * persisted compatible result. The registry still owns the model-call upper bound; this
     * hook only prevents provider routing for that invocation.
     */
    default boolean requiresProvider(StepExecutionContext context) {
        return true;
    }

    /** Marks a deterministic branch as a durable SKIPPED step without invoking a provider. */
    default boolean skip(StepExecutionContext context) {
        return false;
    }

    /** Allows deterministic inspection to request user input discovered during the step. */
    default Optional<RequiredUserAction> requiredUserAction(
            T validatedOutput, JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    /** Supplies a safe, reference-only partial result for the terminal Run projection. */
    default Optional<PartialResult> partialResult(
            T validatedOutput, JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    /** Reconstructs reference-only partial progress after a committed checkpoint restart/reuse. */
    default Optional<PartialResult> partialResultFromMinimal(
            JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    /**
     * Allows only a bounded scope failure to be isolated from sibling scopes.
     *
     * <p>The default remains fail-fast. P7 generation opts in only for its canonical question
     * fan-out and the terminal Run is still FAILED when any isolated scope remains failed.
     */
    default boolean continueAfterScopeFailure(
            com.hiresemble.ai.execution.AiExecutionException failure,
            StepExecutionContext context) {
        return false;
    }

    record StepExecutionContext(
            AgentRunSnapshot run,
            ContextSnapshot contextSnapshot,
            Map<String, JsonNode> upstreamOutputs,
            Map<String, Object> ephemeralOutputs,
            String scopeKey) {
        public StepExecutionContext {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(contextSnapshot, "contextSnapshot");
            upstreamOutputs = upstreamOutputs == null ? Map.of() : Map.copyOf(upstreamOutputs);
            ephemeralOutputs = ephemeralOutputs == null ? Map.of() : Map.copyOf(ephemeralOutputs);
            if (scopeKey != null && (scopeKey.isBlank() || scopeKey.length() > 100)) {
                throw new IllegalArgumentException("scope key is invalid");
            }
        }

        public StepExecutionContext(
                AgentRunSnapshot run,
                ContextSnapshot contextSnapshot,
                Map<String, JsonNode> upstreamOutputs) {
            this(run, contextSnapshot, upstreamOutputs, Map.of(), null);
        }

        public StepExecutionContext(
                AgentRunSnapshot run,
                ContextSnapshot contextSnapshot,
                Map<String, JsonNode> upstreamOutputs,
                Map<String, Object> ephemeralOutputs) {
            this(run, contextSnapshot, upstreamOutputs, ephemeralOutputs, null);
        }

        public StepExecutionContext forScope(String selectedScopeKey) {
            return new StepExecutionContext(
                    run,
                    contextSnapshot,
                    upstreamOutputs,
                    ephemeralOutputs,
                    selectedScopeKey);
        }

        public JsonNode upstream(String stepKey) {
            return upstreamOutputs.get(outputKey(stepKey, null));
        }

        public JsonNode upstream(String stepKey, String selectedScopeKey) {
            return upstreamOutputs.get(outputKey(stepKey, selectedScopeKey));
        }

        public Object ephemeral(String stepKey) {
            return ephemeralOutputs.get(outputKey(stepKey, null));
        }

        public Object ephemeral(String stepKey, String selectedScopeKey) {
            return ephemeralOutputs.get(outputKey(stepKey, selectedScopeKey));
        }

        public Map<String, JsonNode> scopedUpstream(String stepKey) {
            return scopedValues(upstreamOutputs, stepKey);
        }

        public Map<String, Object> scopedEphemeral(String stepKey) {
            return scopedValues(ephemeralOutputs, stepKey);
        }

        public static String outputKey(String stepKey, String selectedScopeKey) {
            if (stepKey == null || stepKey.isBlank()) {
                throw new IllegalArgumentException("step key is invalid");
            }
            return selectedScopeKey == null
                    ? stepKey
                    : stepKey + "\u001f" + selectedScopeKey;
        }

        private static <V> Map<String, V> scopedValues(
                Map<String, V> values, String stepKey) {
            String prefix = stepKey + "\u001f";
            java.util.LinkedHashMap<String, V> selected = new java.util.LinkedHashMap<>();
            values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> selected.put(
                            entry.getKey().substring(prefix.length()),
                            entry.getValue()));
            return Map.copyOf(selected);
        }
    }

    record StepInput(
            String scopeKey,
            JsonNode sanitizedInputRefs,
            String canonicalInputMaterial,
            JsonNode gatewayPayload,
            RequiredUserAction requiredUserAction,
            long expectedResourceVersion) {
        public StepInput {
            if (scopeKey != null && (scopeKey.isBlank() || scopeKey.length() > 100)) {
                throw new IllegalArgumentException("scope key is invalid");
            }
            if (sanitizedInputRefs == null || !sanitizedInputRefs.isObject()
                    || canonicalInputMaterial == null || canonicalInputMaterial.isBlank()
                    || gatewayPayload == null || expectedResourceVersion < 0) {
                throw new IllegalArgumentException("step input is invalid");
            }
        }

        public boolean waitsForUser() {
            return requiredUserAction != null;
        }
    }

    record GatewayInvocation(
            StepInput input,
            ModelRoute modelRoute,
            PromptDefinition prompt,
            ChatGateway chatGateway,
            EmbeddingGateway embeddingGateway,
            WebSearchGateway webSearchGateway,
            StepExecutionContext executionContext) {
        public GatewayInvocation {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(modelRoute, "modelRoute");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(chatGateway, "chatGateway");
            Objects.requireNonNull(embeddingGateway, "embeddingGateway");
            Objects.requireNonNull(webSearchGateway, "webSearchGateway");
            Objects.requireNonNull(executionContext, "executionContext");
        }
    }

    record DomainApplyPlan(String resourceType, UUID resourceId, long expectedResourceVersion) {
        public DomainApplyPlan {
            if (resourceType == null || resourceType.isBlank() || resourceType.length() > 50
                    || resourceId == null || expectedResourceVersion < 0) {
                throw new IllegalArgumentException("domain apply plan is invalid");
            }
        }
    }

    record DomainStepCompletion(
            JsonNode minimalOutput,
            Optional<DomainApplyPlan> genericDomainApply,
            PartialResult partialResult) {
        public DomainStepCompletion {
            if (minimalOutput == null || !minimalOutput.isObject()) {
                throw new IllegalArgumentException("domain completion output is invalid");
            }
            genericDomainApply =
                    genericDomainApply == null ? Optional.empty() : genericDomainApply;
        }
    }
}
