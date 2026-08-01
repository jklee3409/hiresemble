package com.hiresemble.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StructuredOutputValidatorTest {

    private final StructuredOutputValidator validator = new StructuredOutputValidator(new ObjectMapper());

    @Test
    void validationStagesRunInStrictOrder() {
        List<String> order = new ArrayList<>();
        Contract<FixtureOutput> contract = new Contract<>(
                FixtureOutput.class,
                "fixture-v1",
                tree -> { order.add("schema"); if (!tree.has("resultRef")) throw new IllegalArgumentException(); },
                value -> { order.add("record"); if (value.resultRef().isBlank()) throw new IllegalArgumentException(); },
                value -> order.add("workflow"),
                value -> order.add("domain"));

        FixtureOutput output = validator.validate("{\"resultRef\":\"safe-ref\",\"valid\":true}", contract);

        assertThat(output.resultRef()).isEqualTo("safe-ref");
        assertThat(order).containsExactly("schema", "record", "workflow", "domain");
    }

    @Test
    void parseShapeAndBindingFailuresAreDistinctAndDeterministic() {
        assertFailure("{not-json", validContract(), "AI_SO_JSON_NOT_PARSEABLE",
                ValidationPhase.JSON_PARSE, false, 1);
        assertFailure(
                "{\"resultRef\":\"safe-ref\",\"valid\":true}",
                contract(tree -> { throw new IllegalArgumentException("raw field value"); }, value -> {}),
                "AI_SO_SCHEMA_SHAPE_INVALID", ValidationPhase.SCHEMA_SHAPE, false, 1);
        assertFailure(
                "{\"resultRef\":[],\"valid\":true}",
                contract(tree -> {}, value -> {}),
                "AI_SO_JAVA_BINDING_FAILED", ValidationPhase.JAVA_BINDING, false, 1);
    }

    @Test
    void recordAndWorkflowFailuresAreRepairableOnceWithValueFreeGuidance() {
        Contract<FixtureOutput> invalidRecord = new Contract<>(
                FixtureOutput.class, "fixture-v1", tree -> {},
                value -> { throw StructuredOutputValidationException.repairable(
                        ValidationPhase.JAVA_RECORD, "AI_SO_RECORD_FIXTURE_INVALID",
                        "Previous output violated the fixture rule. Return a compliant object."); },
                value -> {}, value -> {});
        assertFailure(
                "{\"resultRef\":\"private-ref\",\"valid\":true}", invalidRecord,
                "AI_SO_RECORD_FIXTURE_INVALID", ValidationPhase.JAVA_RECORD, true, 2);

        Contract<FixtureOutput> invalidWorkflow = new Contract<>(
                FixtureOutput.class, "fixture-v1", tree -> {}, value -> {},
                value -> { throw new IllegalArgumentException("unknown private-ref"); }, value -> {});
        assertFailure(
                "{\"resultRef\":\"private-ref\",\"valid\":true}", invalidWorkflow,
                "AI_SO_WORKFLOW_CONTEXT_INVALID", ValidationPhase.WORKFLOW_CONTEXT, true, 2);
    }

    @Test
    void domainCommandFailureKeepsItsExistingNonStructuredClassification() {
        Contract<FixtureOutput> invalidDomain = contract(
                tree -> {}, value -> { throw new IllegalArgumentException("private-ref"); });

        assertThatThrownBy(() -> validator.validate(
                        "{\"resultRef\":\"private-ref\",\"valid\":true}", invalidDomain))
                .isInstanceOfSatisfying(AiExecutionException.class, failure -> {
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.DOMAIN_VALIDATION);
                    assertThat(failure.safeCode()).isEqualTo("AI_DOMAIN_COMMAND_INVALID");
                    assertThat(failure.retryable()).isFalse();
                    assertThat(failure.safeMessage()).doesNotContain("private-ref", "Exception");
                });
    }

    private Contract<FixtureOutput> validContract() {
        return contract(tree -> {}, value -> {});
    }

    private void assertFailure(
            String json,
            Contract<FixtureOutput> contract,
            String code,
            ValidationPhase phase,
            boolean retryable,
            int maxAttempts) {
        assertThatThrownBy(() -> validator.validate(json, contract))
                .isInstanceOfSatisfying(AiExecutionException.class, failure -> {
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.STRUCTURED_OUTPUT);
                    assertThat(failure.safeCode()).isEqualTo(code);
                    assertThat(failure.validationPhase()).isEqualTo(phase);
                    assertThat(failure.retryable()).isEqualTo(retryable);
                    assertThat(failure.maxAutomaticAttempts()).isEqualTo(maxAttempts);
                    assertThat(failure.safeMessage())
                            .doesNotContain("private-ref", "raw field value", "Exception");
                    if (retryable) {
                        assertThat(failure.correctionGuidance())
                                .isNotBlank()
                                .doesNotContain("private-ref");
                    }
                });
    }

    private Contract<FixtureOutput> contract(
            StructuredOutputValidator.SchemaValidator schema,
            java.util.function.Consumer<FixtureOutput> domain) {
        return new Contract<>(FixtureOutput.class, "fixture-v1", schema,
                value -> {}, value -> {}, domain);
    }

    private record FixtureOutput(String resultRef, boolean valid) {}
}
