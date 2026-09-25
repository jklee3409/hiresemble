package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.JobPostingExtractionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaGenerator;
import java.time.Instant;
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

        var fieldsPrompt = prompts.require(
                WorkflowType.JOB_POSTING_EXTRACTION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS);
        assertThat(fieldsPrompt.promptVersion())
                .isEqualTo("job-posting-extraction-fields-prompt-v4");
        assertThat(fieldsPrompt.outputSchemaVersion()).isEqualTo("job-fields-output-v4");
        assertThat(fieldsPrompt.outputType())
                .isEqualTo(JobPostingExtractionWorkflow.ExtractedJobFieldsOutput.class);
        assertThat(fieldsPrompt.instructions())
                .contains(
                        "local wall-clock time",
                        "never convert it to UTC",
                        "Never guess an offset")
                .doesNotContain("deadlineAt");
        assertThat(prompts.require(
                        WorkflowType.JOB_POSTING_EXTRACTION,
                        CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                        JobPostingExtractionWorkflow.MERGE_USER_OVERRIDES)
                .promptVersion())
                .isEqualTo(JobPostingExtractionPromptDefinitions.PROMPT_VERSION);

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

    @Test
    void fieldsV4StrictSchemaKeepsDeadlineAsNullableLocalText() {
        String schema = new StrictStructuredOutputSchemaGenerator(new ObjectMapper())
                .generate(JobPostingExtractionWorkflow.ExtractedJobFieldsOutput.class);

        assertThat(schema)
                .contains("\"deadlineDate\"", "\"deadlineTime\"", "\"deadlineUtcOffset\"")
                .doesNotContain("\"deadlineAt\"", "date-time")
                .contains("\"additionalProperties\" : false")
                .containsPattern(
                        "(?s)\"deadlineTime\"\\s*:\\s*\\{[^}]*\"type\"\\s*:\\s*\\[\\s*\"string\"\\s*,\\s*\"null\"");
    }

    @Test
    void unzonedPostingDeadlineResolvesAsSeoulWallClockTime() {
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "17:00", null))
                .isEqualTo(Instant.parse("2026-09-28T08:00:00Z"));
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "17:00:30", null))
                .isEqualTo(Instant.parse("2026-09-28T08:00:30Z"));
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", null, null))
                .isEqualTo(Instant.parse("2026-09-28T14:59:59Z"));
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "24:00", null))
                .isEqualTo(Instant.parse("2026-09-28T15:00:00Z"));
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline(null, null, null)).isNull();
    }

    @Test
    void explicitPostingOffsetIsHonoredAndMalformedDeadlinesAreRejected() {
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "17:00", "Z"))
                .isEqualTo(Instant.parse("2026-09-28T17:00:00Z"));
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "17:00", "+09:00"))
                .isEqualTo(Instant.parse("2026-09-28T08:00:00Z"));
        assertThat(JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "09:00", "-05:00"))
                .isEqualTo(Instant.parse("2026-09-28T14:00:00Z"));

        assertThatThrownBy(() -> JobPostingExtractionWorkflow.resolvePostingDeadline(null, "17:00", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobPostingExtractionWorkflow.resolvePostingDeadline(null, null, "Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobPostingExtractionWorkflow.resolvePostingDeadline("9.28", "17:00", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobPostingExtractionWorkflow.resolvePostingDeadline("2026-09-28", "5pm", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobPostingExtractionWorkflow.resolvePostingDeadline(
                        "2026-09-28", "17:00", "KST"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobPostingExtractionWorkflow.resolvePostingDeadline(
                        "2026-09-28T17:00:00Z", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
