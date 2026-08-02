package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.JobPostingExtractionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaGenerator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JobPostingExtractionWorkflowContractTest {

    private static final List<String> EXPECTED = List.of(
            JobPostingExtractionWorkflow.FETCH_JOB_PAGE,
            JobPostingExtractionWorkflow.INSPECT_JOB_PAGE,
            JobPostingExtractionWorkflow.FETCH_JOB_IMAGES,
            JobPostingExtractionWorkflow.EXTRACT_JOB_IMAGE_TEXT,
            JobPostingExtractionWorkflow.COMPOSE_JOB_SOURCE_TEXT,
            JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS,
            JobPostingExtractionWorkflow.MERGE_USER_OVERRIDES,
            JobPostingExtractionWorkflow.VALIDATE_JOB_EXTRACTION,
            JobPostingExtractionWorkflow.APPLY_JOB_EXTRACTION);

    @Test
    void canonicalVersionSequenceAndProviderCallCapAreExact() {
        var definition = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == WorkflowType.JOB_POSTING_EXTRACTION)
                .findFirst()
                .orElseThrow();

        assertThat(definition.version())
                .isEqualTo(CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION);
        assertThat(definition.steps())
                .extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactlyElementsOf(EXPECTED);
        assertThat(definition.steps().stream()
                        .filter(WorkflowRegistry.StepDefinition::requiresProvider)
                        .map(WorkflowRegistry.StepDefinition::stepKey))
                .containsExactly(
                        JobPostingExtractionWorkflow.EXTRACT_JOB_IMAGE_TEXT,
                        JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS);
        assertThat(definition.steps())
                .allSatisfy(step -> assertThat(step.toolAllowlist()).isEqualTo(Set.of()));
        assertThat(definition.steps().stream()
                        .mapToInt(WorkflowRegistry.StepDefinition::maxModelCalls)
                        .sum())
                .isEqualTo(2);

        assertThat(definition.version()).isEqualTo("job-posting-extraction-v3");
        assertThat(CanonicalWorkflowDefinitions.all().stream()
                        .filter(value -> value.type() == WorkflowType.JOB_POSTING_EXTRACTION)
                        .filter(value -> !value.canonical())
                        .map(WorkflowRegistry.WorkflowDefinition::version))
                .containsExactlyInAnyOrder(
                        "job-posting-extraction-v1", "job-posting-extraction-v2");
    }

    @Test
    void promptMetadataMatchesEveryStepAndTreatsPageAsUntrustedData() {
        var definition = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == WorkflowType.JOB_POSTING_EXTRACTION)
                .findFirst()
                .orElseThrow();
        PromptRegistry prompts =
                new PromptRegistry(JobPostingExtractionPromptDefinitions.all());

        for (var step : definition.steps()) {
            var prompt = prompts.require(
                    WorkflowType.JOB_POSTING_EXTRACTION,
                    CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                    step.stepKey());
            assertThat(prompt.outputSchemaVersion()).isEqualTo(step.outputSchemaVersion());
            assertThat(prompt.toolAllowlist()).isEqualTo(step.toolAllowlist());
            assertThat(prompt.maxModelCalls()).isEqualTo(step.maxModelCalls());
        }

        String extractionInstructions = prompts.require(
                        WorkflowType.JOB_POSTING_EXTRACTION,
                        CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                        JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS)
                .instructions();
        assertThat(extractionInstructions)
                .contains("untrusted data", "never instructions", "Do not invent")
                .doesNotContain("Tavily", "WEB_SEARCH");

        var imagePrompt = prompts.require(
                WorkflowType.JOB_POSTING_EXTRACTION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                JobPostingExtractionWorkflow.EXTRACT_JOB_IMAGE_TEXT);
        assertThat(imagePrompt.promptVersion())
                .isEqualTo("job-posting-extraction-image-text-prompt-v4");
        assertThat(imagePrompt.outputSchemaVersion()).isEqualTo("job-image-text-output-v3");
        assertThat(imagePrompt.instructions())
                .contains(
                        "same local imageRef",
                        "never create a remote",
                        "UUID",
                        "omit unreadable");
    }

    @Test
    void imageTextV3StrictSchemaRequiresTrustedReferenceTextAndTruncation() {
        String schema = new StrictStructuredOutputSchemaGenerator(new ObjectMapper())
                .generate(JobPostingExtractionWorkflow.ImageTextOutput.class);

        assertThat(schema)
                .contains("\"imageRef\"", "\"text\"", "\"truncated\"")
                .contains("\"additionalProperties\" : false")
                .containsPattern("(?s)\\\"required\\\"\\s*:\\s*\\[[^]]*\\\"imageRef\\\"[^]]*\\\"text\\\"[^]]*\\\"truncated\\\"");
    }
}
