package com.hiresemble.ai.workflow.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.DocumentIngestionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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

    @Test
    void providerMetadataEntriesPreserveTheExistingScalarMapContract() {
        DocumentIngestionWorkflow workflow = workflow();

        Map<String, Object> metadata = workflow.mapEvidenceMetadata(List.of(
                entry("label", DocumentIngestionWorkflow.EvidenceMetadataValueType.STRING, "project"),
                entry("score", DocumentIngestionWorkflow.EvidenceMetadataValueType.NUMBER, "1.25"),
                entry("active", DocumentIngestionWorkflow.EvidenceMetadataValueType.BOOLEAN, "true"),
                entry("unknown", DocumentIngestionWorkflow.EvidenceMetadataValueType.NULL, "")));

        assertThat(metadata).containsEntry("label", "project")
                .containsEntry("score", new java.math.BigDecimal("1.25"))
                .containsEntry("active", true)
                .containsEntry("unknown", null);
    }

    @Test
    void providerMetadataRejectsDuplicateKeysAndInvalidTaggedValues() {
        DocumentIngestionWorkflow workflow = workflow();

        assertThatThrownBy(() -> workflow.mapEvidenceMetadata(List.of(
                        entry("source", DocumentIngestionWorkflow.EvidenceMetadataValueType.STRING, "document"),
                        entry("source", DocumentIngestionWorkflow.EvidenceMetadataValueType.NUMBER, "1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflow.mapEvidenceMetadata(List.of(
                        entry("active", DocumentIngestionWorkflow.EvidenceMetadataValueType.BOOLEAN, "yes"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflow.mapEvidenceMetadata(List.of(
                        entry("unknown", DocumentIngestionWorkflow.EvidenceMetadataValueType.NULL, "not-empty"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflow.mapEvidenceMetadata(List.of(
                        entry("apiKey", DocumentIngestionWorkflow.EvidenceMetadataValueType.STRING, "redacted"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DocumentIngestionWorkflow workflow() {
        return new DocumentIngestionWorkflow(
                mock(com.hiresemble.document.application.port.DocumentWorkflowQueryPort.class),
                mock(com.hiresemble.document.application.port.DocumentWorkflowCommandPort.class),
                new ObjectMapper());
    }

    private DocumentIngestionWorkflow.EvidenceMetadataEntryOutput entry(
            String key,
            DocumentIngestionWorkflow.EvidenceMetadataValueType type,
            String value) {
        return new DocumentIngestionWorkflow.EvidenceMetadataEntryOutput(key, type, value);
    }
}
