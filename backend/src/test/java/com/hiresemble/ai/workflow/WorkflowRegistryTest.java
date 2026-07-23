package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.ai.workflow.WorkflowRegistry.WorkflowConfigurationException;
import com.hiresemble.ai.workflow.WorkflowRegistry.WorkflowDefinition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowRegistryTest {

    @Test
    void canonicalRegistryCoversExactlyEightTypesWithoutPretendingTheyAreExecutable() {
        WorkflowRegistry registry = new WorkflowRegistry(CanonicalWorkflowDefinitions.all(), List.of());

        assertThat(registry.definitions()).hasSize(8);
        assertThat(registry.definitions()).extracting(WorkflowDefinition::type)
                .containsExactlyInAnyOrder(WorkflowType.values());
        assertThat(registry.definitions()).allSatisfy(definition -> {
            assertThat(definition.canonical()).isTrue();
            assertThat(definition.steps()).isNotEmpty();
            assertThat(definition.steps()).extracting(StepDefinition::stepKey).doesNotHaveDuplicates();
            assertThat(definition.steps().stream()
                    .map(StepDefinition::progressWeight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
            assertThat(registry.executable(definition.type(), definition.version())).isEmpty();
        });
    }

    @Test
    void duplicateStepWeightAndExecutableSequenceAreRejected() {
        StepDefinition step = step("ONE", new BigDecimal("50"));
        assertThatThrownBy(() -> new WorkflowDefinition(
                WorkflowType.JOB_ANALYSIS, "fixture-invalid", false,
                Set.of(AiQualityMode.ECONOMY), List.of(step, step)))
                .isInstanceOf(WorkflowConfigurationException.class)
                .hasMessage("AI_WORKFLOW_STEP_KEY_DUPLICATE");

        WorkflowDefinition extra = new WorkflowDefinition(
                WorkflowType.JOB_ANALYSIS, "fixture-v1", false,
                Set.of(AiQualityMode.ECONOMY),
                List.of(step("ONE", new BigDecimal("50")), step("TWO", new BigDecimal("50"))));
        WorkflowStepExecutor<FixtureOutput> executor = FixtureExecutors.noop();
        ExecutableWorkflowContribution wrong = new ExecutableWorkflowContribution(
                WorkflowType.JOB_ANALYSIS, "fixture-v1",
                List.of(new ExecutableWorkflowStep("TWO", executor),
                        new ExecutableWorkflowStep("ONE", executor)));

        List<WorkflowDefinition> definitions = new ArrayList<>(CanonicalWorkflowDefinitions.all());
        definitions.add(extra);
        assertThatThrownBy(() -> new WorkflowRegistry(definitions, List.of(wrong)))
                .isInstanceOf(WorkflowConfigurationException.class)
                .hasMessage("AI_WORKFLOW_EXECUTABLE_SEQUENCE_MISMATCH");
    }

    @Test
    void invalidWeightFanoutCallCapAndToolMetadataAreRejected() {
        assertThatThrownBy(() -> new WorkflowDefinition(
                WorkflowType.JOB_ANALYSIS, "bad-weight", false,
                Set.of(AiQualityMode.ECONOMY),
                List.of(step("ONE", new BigDecimal("40")), step("TWO", new BigDecimal("40")))))
                .isInstanceOf(WorkflowConfigurationException.class)
                .hasMessage("AI_WORKFLOW_PROGRESS_WEIGHT_INVALID");

        assertThatThrownBy(() -> new StepDefinition(
                "ONE", "FixtureAgent", "input-v1", "output-v1", Set.of(), 4, 1,
                ModelTier.LOW_COST, Set.of(), new BigDecimal("100")))
                .isInstanceOf(WorkflowConfigurationException.class)
                .hasMessage("AI_WORKFLOW_CALL_OR_FANOUT_INVALID");
        assertThatThrownBy(() -> new StepDefinition(
                "ONE", "FixtureAgent", "input-v1", "output-v1", Set.of(), 1, 101,
                ModelTier.LOW_COST, Set.of(), new BigDecimal("100")))
                .isInstanceOf(WorkflowConfigurationException.class)
                .hasMessage("AI_WORKFLOW_CALL_OR_FANOUT_INVALID");
        assertThatThrownBy(() -> new StepDefinition(
                "ONE", "FixtureAgent", "input-v1", "output-v1", Set.of("web-search"), 1, 1,
                ModelTier.LOW_COST, Set.of(), new BigDecimal("100")))
                .isInstanceOf(WorkflowConfigurationException.class)
                .hasMessage("AI_WORKFLOW_TOOL_ALLOWLIST_INVALID");
    }

    private StepDefinition step(String key, BigDecimal weight) {
        return new StepDefinition(
                key, "FixtureAgent", "input-v1", "output-v1", Set.of(), 0, 1,
                ModelTier.LOW_COST, Set.of(), weight);
    }

    private record FixtureOutput(String resultRef) {}

    /** Only registry metadata is used by this test. */
    private static final class FixtureExecutors {
        private static WorkflowStepExecutor<FixtureOutput> noop() {
            return new WorkflowStepExecutor<>() {
                @Override public StepInput prepare(StepExecutionContext context) { throw new UnsupportedOperationException(); }
                @Override public com.hiresemble.ai.port.AiGatewayResponse invoke(GatewayInvocation invocation) { throw new UnsupportedOperationException(); }
                @Override public com.hiresemble.ai.validation.StructuredOutputValidator.Contract<FixtureOutput> outputContract() { throw new UnsupportedOperationException(); }
                @Override public tools.jackson.databind.JsonNode minimalOutput(FixtureOutput output, tools.jackson.databind.ObjectMapper mapper) { throw new UnsupportedOperationException(); }
            };
        }
    }
}
