package com.hiresemble.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.prompt.CanonicalPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JobAnalysisStrictStructuredOutputContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OpenAiStrictSchemaCompatibilityValidator VALIDATOR =
            new OpenAiStrictSchemaCompatibilityValidator(OBJECT_MAPPER);

    @Test
    void registrySchemasExposeOnlyModelOwnedNullableFields() throws Exception {
        PromptRegistry promptRegistry = new PromptRegistry(CanonicalPromptDefinitions.all());
        StrictStructuredOutputSchemaGenerator generator =
                new StrictStructuredOutputSchemaGenerator(OBJECT_MAPPER);
        StrictStructuredOutputSchemaRegistry registry = new StrictStructuredOutputSchemaRegistry(
                promptRegistry, generator, VALIDATOR);

        JsonNode requirements = schema(registry.require(
                JobAnalysisWorkflow.ProviderRequirementsOutput.class,
                "job-analysis-requirements-source-output-v5"));
        JsonNode eligibility = schema(registry.require(
                JobAnalysisWorkflow.ProviderEligibilityOutput.class,
                "job-analysis-eligibility-output-v3"));
        JsonNode match = schema(registry.require(
                JobAnalysisWorkflow.ProviderMatchOutput.class,
                "job-analysis-match-output-v3"));

        assertServerFieldsAbsent(requirements);
        assertServerFieldsAbsent(eligibility);
        assertServerFieldsAbsent(match);

        JsonNode requirement = resolve(
                requirements,
                requirements.path("properties").path("requirements").path("items"));
        JsonNode sourceLocation = requirement.path("properties").path("sourceLocation");
        JsonNode sourceSection = requirement.path("properties").path("sourceSection");
        assertThat(requirement.path("required").toString())
                .contains("sourceSection", "sourceText", "sourceLocation", "sourceOrdinal");
        assertThat(sourceLocation.path("type").toString()).contains("string", "null");
        assertThat(sourceSection.path("type").toString()).contains("string", "null");
        assertThat(requirement.path("properties").path("sourceText").path("type").asText())
                .isEqualTo("string");
        assertThat(requirement.path("properties").propertyNames())
                .doesNotContain("category", "supportType", "requiredByDate", "reusable");

        JsonNode criterion = resolve(
                match,
                match.path("properties").path("criteria").path("items"));
        JsonNode missingReason = criterion.path("properties").path("missingReason");
        assertThat(criterion.path("required").toString()).contains("missingReason");
        assertThat(missingReason.path("type").toString()).contains("string", "null");
        assertThat(match.path("properties").path("analysisSummary").path("type").asText())
                .isEqualTo("string");
        assertThat(eligibility.path("properties").path("explanation").path("type").asText())
                .isEqualTo("string");

        VALIDATOR.validate(requirements.toString());
        VALIDATOR.validate(eligibility.toString());
        VALIDATOR.validate(match.toString());
    }

    private static JsonNode schema(
            StrictStructuredOutputSchemaRegistry.ValidatedSchema schema) throws Exception {
        return OBJECT_MAPPER.readTree(schema.schema());
    }

    private static void assertServerFieldsAbsent(JsonNode schema) {
        assertThat(schema.path("properties").has("reusable")).isFalse();
        assertThat(schema.path("properties").has("reusableAnalysisId")).isFalse();
    }

    private static JsonNode resolve(JsonNode root, JsonNode schema) {
        JsonNode reference = schema.path("$ref");
        if (!reference.isTextual()) {
            return schema;
        }
        String prefix = "#/$defs/";
        assertThat(reference.asText()).startsWith(prefix);
        return root.path("$defs").path(reference.asText().substring(prefix.length()));
    }
}
