package com.hiresemble.ai.prompt;

import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable prompt metadata registry. Prompt text is retained in memory and is never persisted by this type. */
public final class PromptRegistry {

    private final Map<PromptKey, PromptDefinition> definitions;

    public PromptRegistry(List<PromptDefinition> definitions) {
        Map<PromptKey, PromptDefinition> values = new LinkedHashMap<>();
        for (PromptDefinition definition : definitions == null ? List.<PromptDefinition>of() : definitions) {
            if (values.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("duplicate prompt definition");
            }
        }
        this.definitions = Map.copyOf(values);
    }

    public PromptDefinition require(WorkflowType workflowType, String workflowVersion, String stepKey) {
        PromptDefinition definition = definitions.get(new PromptKey(workflowType, workflowVersion, stepKey));
        if (definition == null) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_PROMPT_NOT_CONFIGURED",
                    "AI 실행 구성이 준비되지 않았습니다.");
        }
        return definition;
    }

    public record PromptKey(WorkflowType workflowType, String workflowVersion, String stepKey) {
        public PromptKey {
            Objects.requireNonNull(workflowType, "workflowType");
            requireText(workflowVersion, 50);
            requireText(stepKey, 100);
        }
    }

    public record PromptDefinition(
            PromptKey key,
            String promptVersion,
            Class<?> inputType,
            Class<?> outputType,
            String outputSchemaVersion,
            Set<String> toolAllowlist,
            int maxInputTokens,
            int maxOutputTokens,
            int maxModelCalls,
            String instructions) {
        public PromptDefinition {
            Objects.requireNonNull(key, "key");
            requireText(promptVersion, 100);
            Objects.requireNonNull(inputType, "inputType");
            Objects.requireNonNull(outputType, "outputType");
            requireText(outputSchemaVersion, 50);
            toolAllowlist = toolAllowlist == null ? Set.of() : Set.copyOf(toolAllowlist);
            if (maxInputTokens < 1 || maxOutputTokens < 1 || maxModelCalls < 0 || maxModelCalls > 3
                    || instructions == null || instructions.isBlank()) {
                throw new IllegalArgumentException("prompt limits are invalid");
            }
        }
    }

    private static void requireText(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("prompt key is invalid");
        }
    }
}
