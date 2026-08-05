package com.hiresemble.ai.prompt;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.prompt.PromptRegistry.PromptKey;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import java.util.ArrayList;
import java.util.List;

/** Stage-specific prompts for active relevance-aware explicit verification v3. */
public final class CoverLetterVerificationV3PromptDefinitions {

    private CoverLetterVerificationV3PromptDefinitions() {}

    public static List<PromptDefinition> all() {
        var workflow = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == WorkflowType.COVER_LETTER_VERIFICATION
                        && CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(
                                value.version()))
                .findFirst()
                .orElseThrow();
        List<PromptDefinition> prompts = new ArrayList<>();
        for (StepDefinition step : workflow.steps()) {
            prompts.add(new PromptDefinition(
                    new PromptKey(
                            WorkflowType.COVER_LETTER_VERIFICATION,
                            CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION,
                            step.stepKey()),
                    "cover-letter-verification-"
                            + step.stepKey().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                            + "-prompt-v3",
                    inputType(step.stepKey()),
                    outputType(step.stepKey()),
                    step.outputSchemaVersion(),
                    step.toolAllowlist(),
                    step.requiresProvider() ? 24_000 : 1,
                    step.requiresProvider() ? 8_000 : 1,
                    step.maxModelCalls(),
                    instructions(step.stepKey())));
        }
        return List.copyOf(prompts);
    }

    private static Class<?> inputType(String stepKey) {
        return switch (stepKey) {
            case CoverLetterVerificationWorkflow.LOAD_ANSWER_VERSION ->
                    CoverLetterVerificationWorkflow.LoadAnswerInput.class;
            case CoverLetterVerificationWorkflow.BUILD_PROVENANCE_CONTEXT ->
                    CoverLetterVerificationWorkflow.BuildProvenanceInput.class;
            case CoverLetterVerificationWorkflow.CHECK_FACTS ->
                    CoverLetterVerificationWorkflow.CheckFactsInputV3.class;
            case CoverLetterVerificationWorkflow.CHECK_REQUIREMENTS_AND_LENGTH ->
                    CoverLetterVerificationWorkflow.CheckRequirementsInputV3.class;
            case CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION ->
                    CoverLetterVerificationWorkflow.AggregateVerificationInput.class;
            case CoverLetterVerificationWorkflow.PERSIST_VERIFICATION ->
                    CoverLetterVerificationWorkflow.PersistVerificationInput.class;
            default -> throw new IllegalArgumentException("unknown cover-letter verification step");
        };
    }

    private static Class<?> outputType(String stepKey) {
        return switch (stepKey) {
            case CoverLetterVerificationWorkflow.LOAD_ANSWER_VERSION ->
                    CoverLetterVerificationWorkflow.LoadAnswerOutput.class;
            case CoverLetterVerificationWorkflow.BUILD_PROVENANCE_CONTEXT ->
                    CoverLetterVerificationWorkflow.ProvenanceContextOutput.class;
            case CoverLetterVerificationWorkflow.CHECK_FACTS ->
                    CoverLetterVerificationWorkflow.FactCheckOutput.class;
            case CoverLetterVerificationWorkflow.CHECK_REQUIREMENTS_AND_LENGTH ->
                    CoverLetterVerificationWorkflow.RequirementCheckOutput.class;
            case CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION ->
                    CoverLetterVerificationWorkflow.AggregatedVerificationOutput.class;
            case CoverLetterVerificationWorkflow.PERSIST_VERIFICATION ->
                    CoverLetterVerificationWorkflow.PersistVerificationRequestOutput.class;
            default -> throw new IllegalArgumentException("unknown cover-letter verification step");
        };
    }

    private static String instructions(String stepKey) {
        return switch (stepKey) {
            case CoverLetterVerificationWorkflow.LOAD_ANSWER_VERSION ->
                    "Load the immutable owner-scoped answer through the fixed Backend boundary.";
            case CoverLetterVerificationWorkflow.BUILD_PROVENANCE_CONTEXT -> """
                    Current VERIFIED evidence is positive support. Historical rejected, pending, or
                    source-deleted evidence is audit context only.
                    """;
            case CoverLetterVerificationWorkflow.CHECK_FACTS -> """
                    Return cover-verification-facts-output-v3. The output locale is ko-KR: issue
                    messages and suggestions are user-friendly Korean while technical names remain
                    unchanged. answer metadata explicitly says whether bounded text is truncated.
                    Use only the relevance-selected current VERIFIED evidence. Positive verified
                    claims must be supported exact answer excerpts with at least one supplied current
                    evidence ID. Unsupported claims become UNVERIFIED_CLAIM or CONTRADICTION issues,
                    never positive provenance. Do not invent facts, IDs, or company information.
                    """;
            case CoverLetterVerificationWorkflow.CHECK_REQUIREMENTS_AND_LENGTH -> """
                    Return cover-verification-requirements-output-v3 in ko-KR. Sibling answer inputs
                    are partial when truncated. Use server characterCount for length. Requirement
                    issues use REQUIREMENT_MISSING or LENGTH_VIOLATION; quality and duplication use
                    OTHER with WARNING only. Style preference alone never produces FAILED.
                    """;
            case CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION ->
                    "Deterministically merge validated issues and derive status from severity.";
            case CoverLetterVerificationWorkflow.PERSIST_VERIFICATION ->
                    "Persist only validated positive provenance through the immutable CAS boundary.";
            default -> throw new IllegalArgumentException("unknown cover-letter verification step");
        };
    }
}
