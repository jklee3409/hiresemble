package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.JobPostingExtractionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobPostingExtractionWorkflowContractTest {

    private static final List<String> EXPECTED = List.of(
            JobPostingExtractionWorkflow.FETCH_JOB_PAGE,
            JobPostingExtractionWorkflow.SANITIZE_PAGE_TEXT,
            JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS,
            JobPostingExtractionWorkflow.MERGE_USER_OVERRIDES,
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
                .containsExactly(JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS);
        assertThat(definition.steps())
                .allSatisfy(step -> assertThat(step.toolAllowlist()).isEqualTo(Set.of()));
        assertThat(definition.steps().stream()
                        .mapToInt(WorkflowRegistry.StepDefinition::maxModelCalls)
                        .sum())
                .isEqualTo(1);
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
    }
}
