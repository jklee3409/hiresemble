package com.hiresemble.ai.prompt;

import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import java.util.ArrayList;
import java.util.List;

/** Single enumeration point for every implemented workflow prompt definition. */
public final class CanonicalPromptDefinitions {

    private CanonicalPromptDefinitions() {}

    public static List<PromptDefinition> all() {
        var prompts = new ArrayList<PromptDefinition>();
        prompts.addAll(DocumentIngestionPromptDefinitions.all());
        prompts.addAll(JobPostingExtractionPromptDefinitions.all());
        prompts.addAll(JobAnalysisPromptDefinitions.all());
        prompts.addAll(CoverLetterGenerationPromptDefinitions.all());
        prompts.addAll(CoverLetterVerificationPromptDefinitions.all());
        prompts.addAll(InterviewPreparationPromptDefinitions.all());
        prompts.addAll(InterviewAnswerFeedbackPromptDefinitions.all());
        return List.copyOf(prompts);
    }
}
