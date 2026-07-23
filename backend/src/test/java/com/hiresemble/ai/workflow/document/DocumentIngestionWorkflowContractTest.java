package com.hiresemble.ai.workflow.document;

import static org.assertj.core.api.Assertions.assertThat;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.DocumentIngestionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentIngestionWorkflowContractTest {

    private static final List<String> EXPECTED = List.of(
            DocumentIngestionWorkflow.LOAD_DOCUMENT_SOURCE,
            DocumentIngestionWorkflow.EXTRACT_OR_ACCEPT_TEXT,
            DocumentIngestionWorkflow.MASK_TEXT,
            DocumentIngestionWorkflow.CHUNK_TEXT,
            DocumentIngestionWorkflow.EMBED_CHUNKS,
            DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES,
            DocumentIngestionWorkflow.APPLY_EVIDENCE_CANDIDATES,
            DocumentIngestionWorkflow.FINALIZE_DOCUMENT);

    @Test
    void canonicalSequenceIsExactAndOnlyEmbeddingAndEvidenceExtractionNeedProviders() {
        var definition = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == WorkflowType.DOCUMENT_INGESTION)
                .findFirst()
                .orElseThrow();

        assertThat(definition.steps()).extracting(WorkflowRegistry.StepDefinition::stepKey)
                .containsExactlyElementsOf(EXPECTED);
        assertThat(definition.steps().stream()
                        .filter(WorkflowRegistry.StepDefinition::requiresProvider)
                        .map(WorkflowRegistry.StepDefinition::stepKey))
                .containsExactly(
                        DocumentIngestionWorkflow.EMBED_CHUNKS,
                        DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES);
    }

    @Test
    void promptAndStructuredSchemaMetadataMatchEveryCanonicalStep() {
        var definition = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == WorkflowType.DOCUMENT_INGESTION)
                .findFirst()
                .orElseThrow();
        PromptRegistry prompts = new PromptRegistry(DocumentIngestionPromptDefinitions.all());

        for (var step : definition.steps()) {
            var prompt = prompts.require(
                    WorkflowType.DOCUMENT_INGESTION,
                    CanonicalWorkflowDefinitions.VERSION,
                    step.stepKey());
            assertThat(prompt.outputSchemaVersion()).isEqualTo(step.outputSchemaVersion());
            assertThat(prompt.toolAllowlist()).isEqualTo(step.toolAllowlist());
            assertThat(prompt.maxModelCalls()).isEqualTo(step.maxModelCalls());
        }
        assertThat(DocumentIngestionPromptDefinitions.all()).hasSize(8);
    }

    @Test
    void evidencePromptTreatsMaskedChunksAsUntrustedAndForbidsInventedFacts() {
        PromptRegistry prompts = new PromptRegistry(DocumentIngestionPromptDefinitions.all());
        String instructions = prompts.require(
                        WorkflowType.DOCUMENT_INGESTION,
                        CanonicalWorkflowDefinitions.VERSION,
                        DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES)
                .instructions();

        assertThat(instructions)
                .contains("masked chunk", "untrusted", "Do not invent")
                .doesNotContain("API key", "storage key");
    }
}
