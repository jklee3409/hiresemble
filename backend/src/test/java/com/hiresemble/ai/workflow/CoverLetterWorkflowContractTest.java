package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.CoverLetterGenerationPromptDefinitions;
import com.hiresemble.ai.prompt.CoverLetterGenerationV2PromptDefinitions;
import com.hiresemble.ai.prompt.CoverLetterVerificationPromptDefinitions;
import com.hiresemble.ai.prompt.CoverLetterVerificationV2PromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoverLetterWorkflowContractTest {

    private static final List<String> GENERATION_STEPS = List.of(
            CoverLetterGenerationWorkflow.BUILD_GENERATION_CONTEXT,
            CoverLetterGenerationWorkflow.PLAN_QUESTIONS,
            CoverLetterGenerationWorkflow.ANALYZE_QUESTION,
            CoverLetterGenerationWorkflow.RETRIEVE_EVIDENCE,
            CoverLetterGenerationWorkflow.ALLOCATE_EXPERIENCES,
            CoverLetterGenerationWorkflow.WRITE_ANSWER,
            CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER,
            CoverLetterGenerationWorkflow.APPLY_ANSWER_VERSION);

    private static final List<String> VERIFICATION_STEPS = List.of(
            CoverLetterVerificationWorkflow.LOAD_ANSWER_VERSION,
            CoverLetterVerificationWorkflow.BUILD_PROVENANCE_CONTEXT,
            CoverLetterVerificationWorkflow.CHECK_FACTS,
            CoverLetterVerificationWorkflow.CHECK_REQUIREMENTS_AND_LENGTH,
            CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION,
            CoverLetterVerificationWorkflow.PERSIST_VERIFICATION);

    @Test
    void generationUsesExactEightStepsAndBoundedQuestionFanOut() {
        var definition = definition(WorkflowType.COVER_LETTER_GENERATION);

        assertThat(definition.version())
                .isEqualTo(
                        CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION);
        assertThat(definition.allowedQualityModes())
                .containsExactlyInAnyOrder(
                        AiQualityMode.ECONOMY,
                        AiQualityMode.BALANCED,
                        AiQualityMode.HIGH_QUALITY);
        assertThat(definition.steps())
                .extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactlyElementsOf(GENERATION_STEPS);
        assertThat(definition.steps())
                .filteredOn(step -> step.maxFanOut() == 20)
                .extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactly(
                        CoverLetterGenerationWorkflow.ANALYZE_QUESTION,
                        CoverLetterGenerationWorkflow.RETRIEVE_EVIDENCE,
                        CoverLetterGenerationWorkflow.WRITE_ANSWER,
                        CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER,
                        CoverLetterGenerationWorkflow.APPLY_ANSWER_VERSION);
        assertThat(definition.steps())
                .filteredOn(step -> step.preferredTier() == ModelTier.BALANCED)
                .extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactly(
                        CoverLetterGenerationWorkflow.WRITE_ANSWER,
                        CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER);
        assertThat(definition.steps().stream()
                        .filter(step -> step.stepKey()
                                .equals(CoverLetterGenerationWorkflow
                                        .RETRIEVE_EVIDENCE))
                        .findFirst()
                        .orElseThrow()
                        .toolAllowlist())
                .isEqualTo(Set.of("EMBEDDING"));
        assertThat(definition.steps().stream()
                        .mapToInt(step -> step.maxModelCalls() * step.maxFanOut())
                        .sum())
                .isEqualTo(82);
    }

    @Test
    void verificationUsesExactSixStepsAndOnlyTwoModelCalls() {
        var definition = definition(WorkflowType.COVER_LETTER_VERIFICATION);

        assertThat(definition.version())
                .isEqualTo(
                        CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION);
        assertThat(definition.steps())
                .extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactlyElementsOf(VERIFICATION_STEPS);
        assertThat(definition.steps())
                .filteredOn(WorkflowRegistry.StepDefinition::requiresProvider)
                .extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactly(
                        CoverLetterVerificationWorkflow.CHECK_FACTS,
                        CoverLetterVerificationWorkflow
                                .CHECK_REQUIREMENTS_AND_LENGTH);
        assertThat(definition.steps())
                .allSatisfy(step -> assertThat(step.maxFanOut()).isEqualTo(1));
    }

    @Test
    void v1DefinitionsAndPromptKeysRemainAvailableForDurableRuns() {
        assertThat(CanonicalWorkflowDefinitions.all())
                .filteredOn(value -> value.type() == WorkflowType.COVER_LETTER_GENERATION)
                .extracting(WorkflowRegistry.WorkflowDefinition::version)
                .containsExactly(
                        CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION,
                        CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_LEGACY_VERSION);
        assertThat(CanonicalWorkflowDefinitions.all())
                .filteredOn(value -> value.type() == WorkflowType.COVER_LETTER_VERIFICATION)
                .extracting(WorkflowRegistry.WorkflowDefinition::version)
                .containsExactly(
                        CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION,
                        CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_LEGACY_VERSION);

        PromptRegistry prompts = new PromptRegistry(java.util.stream.Stream.of(
                        CoverLetterGenerationPromptDefinitions.all(),
                        CoverLetterGenerationV2PromptDefinitions.all(),
                        CoverLetterVerificationPromptDefinitions.all(),
                        CoverLetterVerificationV2PromptDefinitions.all())
                .flatMap(List::stream)
                .toList());
        assertThat(prompts.require(
                                WorkflowType.COVER_LETTER_GENERATION,
                                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_LEGACY_VERSION,
                                CoverLetterGenerationWorkflow.WRITE_ANSWER)
                        .promptVersion())
                .isEqualTo("cover-letter-generation-prompt-v1");
        assertThat(prompts.require(
                                WorkflowType.COVER_LETTER_VERIFICATION,
                                CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_LEGACY_VERSION,
                                CoverLetterVerificationWorkflow.CHECK_FACTS)
                        .promptVersion())
                .isEqualTo("cover-letter-verification-prompt-v1");
    }

    @Test
    void promptMetadataMatchesEveryFixedStepAndPreservesEvidenceBoundary() {
        PromptRegistry prompts = new PromptRegistry(java.util.stream.Stream.of(
                        CoverLetterGenerationPromptDefinitions.all(),
                        CoverLetterGenerationV2PromptDefinitions.all(),
                        CoverLetterVerificationPromptDefinitions.all(),
                        CoverLetterVerificationV2PromptDefinitions.all())
                .flatMap(List::stream)
                .toList());

        for (WorkflowType type : List.of(
                WorkflowType.COVER_LETTER_GENERATION,
                WorkflowType.COVER_LETTER_VERIFICATION)) {
            var definition = definition(type);
            for (var step : definition.steps()) {
                var prompt =
                        prompts.require(type, definition.version(), step.stepKey());
                assertThat(prompt.outputSchemaVersion())
                        .isEqualTo(step.outputSchemaVersion());
                assertThat(prompt.toolAllowlist())
                        .isEqualTo(step.toolAllowlist());
                assertThat(prompt.maxModelCalls())
                        .isEqualTo(step.maxModelCalls());
            }
        }

        assertThat(prompts.require(
                                WorkflowType.COVER_LETTER_GENERATION,
                                CanonicalWorkflowDefinitions
                                        .COVER_LETTER_GENERATION_VERSION,
                                CoverLetterGenerationWorkflow.WRITE_ANSWER)
                        .instructions())
                .contains(
                        "current VERIFIED",
                        "currentPlainText",
                        "embedded instructions");
        assertThat(prompts.require(
                                WorkflowType.COVER_LETTER_VERIFICATION,
                                CanonicalWorkflowDefinitions
                                        .COVER_LETTER_VERIFICATION_VERSION,
                                CoverLetterVerificationWorkflow.CHECK_FACTS)
                        .instructions())
                .contains(
                        "current VERIFIED evidence",
                        "historical evidence is audit context",
                        "positive support");
    }

    private WorkflowRegistry.WorkflowDefinition definition(WorkflowType type) {
        return CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == type)
                .findFirst()
                .orElseThrow();
    }
}
