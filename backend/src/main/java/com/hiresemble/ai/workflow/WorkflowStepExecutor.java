package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Testable executable contribution boundary; production workflows add these only in their own phase. */
public interface WorkflowStepExecutor<T> {

    StepInput prepare(StepExecutionContext context);

    AiGatewayResponse invoke(GatewayInvocation invocation);

    Contract<T> outputContract();

    JsonNode minimalOutput(T validatedOutput, ObjectMapper objectMapper);

    default Optional<DomainApplyPlan> domainApply(
            T validatedOutput, JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    default Optional<DomainApplyPlan> domainApplyFromMinimal(
            JsonNode minimalOutput, StepExecutionContext context) {
        return Optional.empty();
    }

    record StepExecutionContext(
            AgentRunSnapshot run,
            ContextSnapshot contextSnapshot,
            Map<String, JsonNode> upstreamOutputs) {
        public StepExecutionContext {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(contextSnapshot, "contextSnapshot");
            upstreamOutputs = upstreamOutputs == null ? Map.of() : Map.copyOf(upstreamOutputs);
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
            WebSearchGateway webSearchGateway) {
        public GatewayInvocation {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(modelRoute, "modelRoute");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(chatGateway, "chatGateway");
            Objects.requireNonNull(embeddingGateway, "embeddingGateway");
            Objects.requireNonNull(webSearchGateway, "webSearchGateway");
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
}
