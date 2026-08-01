package com.hiresemble.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.ai.prompt.CanonicalPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.OpenAiStrictSchemaCompatibilityValidator.StrictSchemaCompatibilityException;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.document.DocumentIngestionWorkflow;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.ObjectMapper;

class OpenAiStrictSchemaCompatibilityValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OpenAiStrictSchemaCompatibilityValidator VALIDATOR =
            new OpenAiStrictSchemaCompatibilityValidator(OBJECT_MAPPER);

    @Test
    void rejectsBareObjectGeneratedForArbitraryMap() {
        String schema = new BeanOutputConverter<>(ArbitraryMapOutput.class).getJsonSchema();

        assertThatThrownBy(() -> VALIDATOR.validate(schema))
                .isInstanceOfSatisfying(StrictSchemaCompatibilityException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo("OBJECT_PROPERTIES_REQUIRED");
                    assertThat(exception.schemaPath()).contains("metadata");
                });
    }

    @Test
    void rejectsAKeywordOutsideTheVerifiedProviderSubset() {
        String schema = """
                {
                  "type": "object",
                  "properties": {"value": {"type": "string", "minProperties": 1}},
                  "required": ["value"],
                  "additionalProperties": false
                }
                """;

        assertThatThrownBy(() -> VALIDATOR.validate(schema))
                .isInstanceOfSatisfying(StrictSchemaCompatibilityException.class, exception ->
                        assertThat(exception.reason()).isEqualTo("KEYWORD_NOT_VERIFIED_MINPROPERTIES"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strictOutputDefinitions")
    void everyRegisteredStrictOutputUsesTheProviderCompatibleRuntimeSchema(
            PromptRegistry.PromptDefinition definition) {
        String schema = new StrictStructuredOutputSchemaGenerator(OBJECT_MAPPER)
                .generate(definition.outputType());

        VALIDATOR.validate(schema);
    }

    @Test
    void evidenceWarningIsRequiredAndNullableInTheGeneratedSchema() throws Exception {
        String schema = new StrictStructuredOutputSchemaGenerator(OBJECT_MAPPER)
                .generate(DocumentIngestionWorkflow.EvidenceCandidateBatch.class);
        var root = OBJECT_MAPPER.readTree(schema);
        var candidate = root.path("properties").path("candidates").path("items");
        var warning = candidate.path("properties").path("validationWarning");

        assertThat(candidate.path("required").toString()).contains("validationWarning");
        assertThat(warning.path("type").toString()).contains("string", "null");
    }

    @Test
    void canonicalRegistryCannotOmitAnImplementedStrictChatStep() {
        PromptRegistry registry = new PromptRegistry(CanonicalPromptDefinitions.all());
        Set<String> expected = CanonicalWorkflowDefinitions.all().stream()
                .filter(workflow -> workflow.type() != WorkflowType.MOCK_INTERVIEW_FEEDBACK)
                .flatMap(workflow -> workflow.steps().stream()
                        .filter(step -> step.maxModelCalls() > 0)
                        .filter(step -> step.toolAllowlist().isEmpty())
                        .map(step -> workflow.type() + "/" + workflow.version() + "/" + step.stepKey()))
                .collect(Collectors.toSet());
        Set<String> actual = registry.strictStructuredOutputDefinitions().stream()
                .map(definition -> definition.key().workflowType() + "/"
                        + definition.key().workflowVersion() + "/" + definition.key().stepKey())
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void registeredSchemaAndFingerprintAreDeterministic() {
        PromptRegistry promptRegistry = new PromptRegistry(CanonicalPromptDefinitions.all());
        StrictStructuredOutputSchemaGenerator generator =
                new StrictStructuredOutputSchemaGenerator(OBJECT_MAPPER);
        StrictStructuredOutputSchemaRegistry first = new StrictStructuredOutputSchemaRegistry(
                promptRegistry, generator, VALIDATOR);
        StrictStructuredOutputSchemaRegistry second = new StrictStructuredOutputSchemaRegistry(
                promptRegistry, generator, VALIDATOR);

        assertThat(first.schemas()).isEqualTo(second.schemas());
        assertThat(first.schemas().values()).allSatisfy(schema -> {
            assertThat(schema.contractName()).isNotBlank();
            assertThat(schema.version()).isNotBlank();
            assertThat(schema.hash()).matches("[0-9a-f]{64}");
            assertThat(schema.schema()).isNotBlank();
        });
    }

    private static Stream<Arguments> strictOutputDefinitions() {
        PromptRegistry registry = new PromptRegistry(CanonicalPromptDefinitions.all());
        return registry.strictStructuredOutputDefinitions().stream()
                .map(definition -> Arguments.of(Named.of(
                        definition.key().workflowType() + "/" + definition.key().stepKey(),
                        definition)));
    }

    private record ArbitraryMapOutput(Map<String, Object> metadata) {}
}
