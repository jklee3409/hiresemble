package com.hiresemble.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.ai.execution.AiExecutionException;
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
    void structuredFailureRetriesButDomainValidationDoesNot() {
        Contract<FixtureOutput> invalidSchema = contract(tree -> { throw new IllegalArgumentException(); }, value -> {});
        assertThatThrownBy(() -> validator.validate("{\"resultRef\":\"safe-ref\",\"valid\":true}", invalidSchema))
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.STRUCTURED_OUTPUT);
                    assertThat(failure.retryable()).isTrue();
                    assertThat(failure.safeMessage()).doesNotContain("resultRef", "Exception");
                });

        Contract<FixtureOutput> invalidDomain = contract(tree -> {}, value -> { throw new IllegalArgumentException(); });
        assertThatThrownBy(() -> validator.validate("{\"resultRef\":\"safe-ref\",\"valid\":true}", invalidDomain))
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.DOMAIN_VALIDATION);
                    assertThat(failure.retryable()).isFalse();
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
