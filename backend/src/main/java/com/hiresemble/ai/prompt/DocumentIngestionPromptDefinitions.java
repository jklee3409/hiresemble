package com.hiresemble.ai.prompt;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.prompt.PromptRegistry.PromptKey;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.ai.workflow.document.DocumentEvidenceOutputPolicy;
import com.hiresemble.ai.workflow.document.DocumentIngestionWorkflow;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Versioned P4 prompt/schema metadata. Only evidence extraction contains model instructions. */
public final class DocumentIngestionPromptDefinitions {

    public static final String PROMPT_VERSION = "document-ingestion-v3";

    private DocumentIngestionPromptDefinitions() {}

    public static List<PromptDefinition> all() {
        var workflow = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == WorkflowType.DOCUMENT_INGESTION)
                .findFirst()
                .orElseThrow();
        List<PromptDefinition> prompts = new ArrayList<>();
        for (StepDefinition step : workflow.steps()) {
            prompts.add(new PromptDefinition(
                    new PromptKey(
                            WorkflowType.DOCUMENT_INGESTION,
                            CanonicalWorkflowDefinitions.VERSION,
                            step.stepKey()),
                    PROMPT_VERSION,
                    JsonNode.class,
                    outputType(step.stepKey()),
                    step.outputSchemaVersion(),
                    step.toolAllowlist(),
                    step.requiresProvider() ? 24_000 : 1,
                    maxOutputTokens(step),
                    step.maxModelCalls(),
                    instructions(step.stepKey())));
        }
        return List.copyOf(prompts);
    }

    private static int maxOutputTokens(StepDefinition step) {
        if (DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES.equals(step.stepKey())) {
            return DocumentEvidenceOutputPolicy.MAX_OUTPUT_TOKENS;
        }
        return step.requiresProvider() ? 12_000 : 1;
    }

    private static Class<?> outputType(String stepKey) {
        return switch (stepKey) {
            case DocumentIngestionWorkflow.LOAD_DOCUMENT_SOURCE ->
                    DocumentIngestionWorkflow.SourceOutput.class;
            case DocumentIngestionWorkflow.EXTRACT_OR_ACCEPT_TEXT ->
                    DocumentIngestionWorkflow.TextOutput.class;
            case DocumentIngestionWorkflow.MASK_TEXT ->
                    DocumentIngestionWorkflow.MaskOutput.class;
            case DocumentIngestionWorkflow.CHUNK_TEXT ->
                    DocumentIngestionWorkflow.ChunkOutput.class;
            case DocumentIngestionWorkflow.EMBED_CHUNKS ->
                    DocumentIngestionWorkflow.EmbeddingBatch.class;
            case DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES ->
                    DocumentIngestionWorkflow.EvidenceCandidateBatch.class;
            case DocumentIngestionWorkflow.APPLY_EVIDENCE_CANDIDATES ->
                    DocumentIngestionWorkflow.EvidenceApplyOutput.class;
            case DocumentIngestionWorkflow.FINALIZE_DOCUMENT ->
                    DocumentIngestionWorkflow.FinalDocumentOutput.class;
            default -> throw new IllegalArgumentException("unknown document step");
        };
    }

    private static String instructions(String stepKey) {
        if (DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES.equals(stepKey)) {
            return DocumentEvidenceOutputPolicy.instructions();
        }
        if (DocumentIngestionWorkflow.EMBED_CHUNKS.equals(stepKey)) {
            return "Embed only the supplied masked inputs with the active immutable policy.";
        }
        return "Execute the deterministic document step and return only safe references and hashes.";
    }
}
