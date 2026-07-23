package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Registry of immutable workflow contracts and separately supplied executable contributions. */
public final class WorkflowRegistry {

    private final Map<WorkflowKey, WorkflowDefinition> definitions;
    private final Map<WorkflowKey, ExecutableWorkflowContribution> contributions;

    public WorkflowRegistry(
            List<WorkflowDefinition> definitions,
            List<ExecutableWorkflowContribution> contributions) {
        this.definitions = uniqueDefinitions(definitions);
        assertCanonicalCoverage(this.definitions);
        this.contributions = uniqueContributions(contributions, this.definitions);
    }

    public WorkflowDefinition definition(WorkflowType type, String version) {
        WorkflowDefinition definition = definitions.get(new WorkflowKey(type, version));
        if (definition == null) {
            throw new WorkflowConfigurationException("AI_WORKFLOW_DEFINITION_MISSING");
        }
        return definition;
    }

    public Optional<ExecutableWorkflowContribution> executable(WorkflowType type, String version) {
        return Optional.ofNullable(contributions.get(new WorkflowKey(type, version)));
    }

    public List<WorkflowDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    private static Map<WorkflowKey, WorkflowDefinition> uniqueDefinitions(
            List<WorkflowDefinition> source) {
        Objects.requireNonNull(source, "definitions");
        Map<WorkflowKey, WorkflowDefinition> result = new LinkedHashMap<>();
        for (WorkflowDefinition definition : source) {
            WorkflowKey key = new WorkflowKey(definition.type(), definition.version());
            if (result.putIfAbsent(key, definition) != null) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_DEFINITION_DUPLICATE");
            }
        }
        return Map.copyOf(result);
    }

    private static void assertCanonicalCoverage(Map<WorkflowKey, WorkflowDefinition> definitions) {
        EnumSet<WorkflowType> covered = EnumSet.noneOf(WorkflowType.class);
        long canonicalCount = definitions.values().stream().filter(WorkflowDefinition::canonical)
                .peek(value -> covered.add(value.type())).count();
        if (canonicalCount != WorkflowType.values().length
                || !covered.equals(EnumSet.allOf(WorkflowType.class))) {
            throw new WorkflowConfigurationException("AI_WORKFLOW_CANONICAL_COVERAGE_INVALID");
        }
    }

    private static Map<WorkflowKey, ExecutableWorkflowContribution> uniqueContributions(
            List<ExecutableWorkflowContribution> source,
            Map<WorkflowKey, WorkflowDefinition> definitions) {
        Objects.requireNonNull(source, "contributions");
        Map<WorkflowKey, ExecutableWorkflowContribution> result = new LinkedHashMap<>();
        for (ExecutableWorkflowContribution contribution : source) {
            WorkflowKey key = new WorkflowKey(contribution.type(), contribution.version());
            WorkflowDefinition definition = definitions.get(key);
            if (definition == null) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_EXECUTABLE_WITHOUT_DEFINITION");
            }
            List<String> expected = definition.steps().stream().map(StepDefinition::stepKey).toList();
            List<String> actual = contribution.steps().stream().map(ExecutableWorkflowStep::stepKey).toList();
            if (!expected.equals(actual)) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_EXECUTABLE_SEQUENCE_MISMATCH");
            }
            if (result.putIfAbsent(key, contribution) != null) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_EXECUTABLE_DUPLICATE");
            }
        }
        return Map.copyOf(result);
    }

    public record WorkflowKey(WorkflowType type, String version) {
        public WorkflowKey {
            Objects.requireNonNull(type, "type");
            requireText(version, 50, "version");
        }
    }

    public record WorkflowDefinition(
            WorkflowType type,
            String version,
            boolean canonical,
            Set<AiQualityMode> allowedQualityModes,
            List<StepDefinition> steps) {

        public WorkflowDefinition {
            Objects.requireNonNull(type, "type");
            requireText(version, 50, "version");
            allowedQualityModes = allowedQualityModes == null
                    ? Set.of() : Set.copyOf(allowedQualityModes);
            if (allowedQualityModes.isEmpty()) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_QUALITY_ALLOWLIST_EMPTY");
            }
            steps = steps == null ? List.of() : List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_STEPS_EMPTY");
            }
            Set<String> keys = new java.util.HashSet<>();
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (StepDefinition step : steps) {
                if (!keys.add(step.stepKey())) {
                    throw new WorkflowConfigurationException("AI_WORKFLOW_STEP_KEY_DUPLICATE");
                }
                totalWeight = totalWeight.add(step.progressWeight());
            }
            if (totalWeight.compareTo(new BigDecimal("100")) != 0) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_PROGRESS_WEIGHT_INVALID");
            }
        }
    }

    public record StepDefinition(
            String stepKey,
            String agentName,
            String inputSchemaVersion,
            String outputSchemaVersion,
            Set<String> toolAllowlist,
            int maxModelCalls,
            int maxFanOut,
            ModelTier preferredTier,
            Set<FailureKind> retryableFailures,
            BigDecimal progressWeight) {

        private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");

        public StepDefinition {
            if (stepKey == null || !KEY.matcher(stepKey).matches()) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_STEP_KEY_INVALID");
            }
            requireText(agentName, 150, "agentName");
            requireText(inputSchemaVersion, 50, "inputSchemaVersion");
            requireText(outputSchemaVersion, 50, "outputSchemaVersion");
            toolAllowlist = toolAllowlist == null ? Set.of() : Set.copyOf(toolAllowlist);
            if (toolAllowlist.stream().anyMatch(tool -> tool == null || !KEY.matcher(tool).matches())) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_TOOL_ALLOWLIST_INVALID");
            }
            if (maxModelCalls < 0 || maxModelCalls > 3 || maxFanOut < 1 || maxFanOut > 100) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_CALL_OR_FANOUT_INVALID");
            }
            if (maxModelCalls == 0 && !toolAllowlist.isEmpty()) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_TOOL_WITHOUT_CALL_INVALID");
            }
            Objects.requireNonNull(preferredTier, "preferredTier");
            retryableFailures = retryableFailures == null ? Set.of() : Set.copyOf(retryableFailures);
            if (!retryableFailures.stream().allMatch(FailureKind::automaticallyRetryable)) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_RETRY_CLASSIFICATION_INVALID");
            }
            if (progressWeight == null || progressWeight.signum() <= 0
                    || progressWeight.compareTo(new BigDecimal("100")) > 0) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_PROGRESS_WEIGHT_INVALID");
            }
        }

        public boolean requiresProvider() {
            return maxModelCalls > 0;
        }
    }

    public record ExecutableWorkflowContribution(
            WorkflowType type,
            String version,
            List<ExecutableWorkflowStep> steps) {
        public ExecutableWorkflowContribution {
            Objects.requireNonNull(type, "type");
            requireText(version, 50, "version");
            steps = steps == null ? List.of() : List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_EXECUTABLE_EMPTY");
            }
            Set<String> keys = new java.util.HashSet<>();
            if (steps.stream().anyMatch(step -> !keys.add(step.stepKey()))) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_EXECUTABLE_STEP_DUPLICATE");
            }
        }
    }

    public record ExecutableWorkflowStep(String stepKey, WorkflowStepExecutor<?> executor) {
        public ExecutableWorkflowStep {
            if (stepKey == null || stepKey.isBlank()) {
                throw new WorkflowConfigurationException("AI_WORKFLOW_EXECUTABLE_STEP_KEY_INVALID");
            }
            Objects.requireNonNull(executor, "executor");
        }
    }

    public enum FailureKind {
        RATE_LIMIT(true),
        PROVIDER_5XX(true),
        NETWORK(true),
        TIMEOUT(true),
        STRUCTURED_OUTPUT(true),
        OWNER(false),
        REQUEST_VALIDATION(false),
        DOMAIN_VALIDATION(false),
        SAFETY(false),
        CONFIGURATION(false),
        BUDGET(false),
        CANCELLATION(false),
        INTERRUPTION(false);

        private final boolean automaticallyRetryable;

        FailureKind(boolean automaticallyRetryable) {
            this.automaticallyRetryable = automaticallyRetryable;
        }

        public boolean automaticallyRetryable() {
            return automaticallyRetryable;
        }
    }

    public static final class WorkflowConfigurationException extends RuntimeException {
        private final String safeCode;

        public WorkflowConfigurationException(String safeCode) {
            super(safeCode);
            this.safeCode = safeCode;
        }

        public String safeCode() {
            return safeCode;
        }
    }

    private static void requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new WorkflowConfigurationException("AI_WORKFLOW_" + field.toUpperCase() + "_INVALID");
        }
    }

    static List<BigDecimal> distributedWeights(int count) {
        int base = 100 / count;
        List<BigDecimal> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(BigDecimal.valueOf(index == count - 1 ? 100L - (long) base * (count - 1) : base));
        }
        return result;
    }
}
