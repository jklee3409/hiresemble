package com.hiresemble.ai.workflow.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.DocumentIngestionPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StructuredOutputValidationException;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry;
import com.hiresemble.ai.workflow.TerminalPartialPolicy;
import com.hiresemble.document.application.port.DocumentWorkflowCommandPort;
import com.hiresemble.document.application.port.DocumentWorkflowQueryPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    void documentContributionRejectsUnexpectedFailedScopes() {
        var contribution = new DocumentIngestionWorkflow(
                        mock(DocumentWorkflowQueryPort.class),
                        mock(DocumentWorkflowCommandPort.class),
                        new ObjectMapper())
                .contribution();

        assertThat(contribution.terminalPartialPolicy().outcome())
                .isEqualTo(TerminalPartialPolicy.Outcome.FAILED);
        assertThat(contribution.terminalPartialPolicy().safeErrorCode())
                .isEqualTo("AI_UNEXPECTED_PARTIAL_RESULT");
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
    void evidencePromptAndPolicyShareTheCompleteSemanticContract() {
        PromptRegistry prompts = new PromptRegistry(DocumentIngestionPromptDefinitions.all());
        String instructions = prompts.require(
                        WorkflowType.DOCUMENT_INGESTION,
                        CanonicalWorkflowDefinitions.VERSION,
                        DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES)
                .instructions();

        assertThat(instructions)
                .isEqualTo(DocumentEvidenceOutputPolicy.instructions())
                .contains(
                        "exactly one object with a candidates array",
                        "maxCandidates",
                        "unique references",
                        "validationWarning must be null",
                        "Do not invent",
                        "Do not extract education",
                        "Do not output document IDs",
                        "metadata")
                .doesNotContain("sourceChunkIds", "sourceRevision");
        var prompt = prompts.require(
                WorkflowType.DOCUMENT_INGESTION,
                CanonicalWorkflowDefinitions.VERSION,
                DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES);
        assertThat(prompt.maxOutputTokens())
                .isEqualTo(DocumentEvidenceOutputPolicy.MAX_OUTPUT_TOKENS);
    }

    @Test
    void candidateCapScalesWithChunkCountAndStopsAtTheAbsoluteLimit() {
        assertThat(DocumentEvidenceOutputPolicy.maxCandidates(1)).isEqualTo(2);
        assertThat(DocumentEvidenceOutputPolicy.maxCandidates(4)).isEqualTo(8);
        assertThat(DocumentEvidenceOutputPolicy.maxCandidates(20)).isEqualTo(12);
    }

    @Test
    void localRefsResolveOnlyThroughTheTrustedSameRevisionMap() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(DocumentEvidenceOutputPolicy.resolveSourceChunkRefs(
                        List.of("C2", "C1"), Map.of("C1", first, "C2", second)))
                .containsExactly(second, first);

        assertThatThrownBy(() -> DocumentEvidenceOutputPolicy.resolveSourceChunkRefs(
                        List.of("C3"), Map.of("C1", first, "C2", second)))
                .isInstanceOfSatisfying(
                        StructuredOutputValidationException.class,
                        error -> assertThat(error.safeReason())
                                .isEqualTo("AI_SO_WORKFLOW_DOCUMENT_SOURCE_REF_UNKNOWN"));
    }

    @Test
    void semanticPolicyRejectsDuplicateBlankUnknownAndOutOfRangeFieldsWithoutValues() {
        var valid = candidate(List.of("C1"), null);
        DocumentEvidenceOutputPolicy.validateBatch(
                new DocumentIngestionWorkflow.EvidenceCandidateBatch(List.of(valid)), 2);

        assertReason(candidate(List.of("C1", "C1"), null),
                "AI_SO_RECORD_DOCUMENT_SOURCE_REF_INVALID");
        assertReason(candidate(List.of(""), null),
                "AI_SO_RECORD_DOCUMENT_SOURCE_REF_INVALID");
        assertReason(candidate(List.of("C1"), " "),
                "AI_SO_RECORD_DOCUMENT_WARNING_INVALID");
        assertThatThrownBy(() -> DocumentEvidenceOutputPolicy.validateBatch(
                        new DocumentIngestionWorkflow.EvidenceCandidateBatch(List.of(valid, valid, valid)),
                        2))
                .isInstanceOfSatisfying(
                        StructuredOutputValidationException.class,
                        error -> assertThat(error.safeReason())
                                .isEqualTo("AI_SO_RECORD_DOCUMENT_CANDIDATE_LIMIT_EXCEEDED"));
    }

    @Test
    void handWrittenJsonCoversEmptyBatchMultipleRefsNullableWarningAndSemanticFailure()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var empty = mapper.readValue(
                "{\"candidates\":[]}",
                DocumentIngestionWorkflow.EvidenceCandidateBatch.class);
        DocumentEvidenceOutputPolicy.validateBatch(empty, 2);

        var valid = mapper.readValue("""
                {"candidates":[{
                  "evidenceCategory":"PROJECT",
                  "title":"Synthetic title",
                  "content":"Synthetic grounded evidence.",
                  "confidence":0.95,
                  "sourceChunkRefs":["C1","C2"],
                  "validationWarning":null
                }]}
                """, DocumentIngestionWorkflow.EvidenceCandidateBatch.class);
        DocumentEvidenceOutputPolicy.validateBatch(valid, 2);

        var invalid = mapper.readValue("""
                {"candidates":[{
                  "evidenceCategory":"PROJECT",
                  "title":"Synthetic title",
                  "content":"Synthetic grounded evidence.",
                  "confidence":1.01,
                  "sourceChunkRefs":["C1"],
                  "validationWarning":null
                }]}
                """, DocumentIngestionWorkflow.EvidenceCandidateBatch.class);
        assertThatThrownBy(() -> DocumentEvidenceOutputPolicy.validateBatch(invalid, 2))
                .isInstanceOfSatisfying(
                        StructuredOutputValidationException.class,
                        error -> assertThat(error.safeReason())
                                .isEqualTo("AI_SO_RECORD_DOCUMENT_CONFIDENCE_INVALID"));
    }

    private void assertReason(
            DocumentIngestionWorkflow.EvidenceCandidatePayload candidate, String reason) {
        assertThatThrownBy(() -> DocumentEvidenceOutputPolicy.validateBatch(
                        new DocumentIngestionWorkflow.EvidenceCandidateBatch(List.of(candidate)), 2))
                .isInstanceOfSatisfying(
                        StructuredOutputValidationException.class,
                        error -> {
                            assertThat(error.safeReason()).isEqualTo(reason);
                            assertThat(error.getMessage()).doesNotContain("C1");
                        });
    }

    private DocumentIngestionWorkflow.EvidenceCandidatePayload candidate(
            List<String> refs, String warning) {
        return new DocumentIngestionWorkflow.EvidenceCandidatePayload(
                "PROJECT", "Synthetic title", "Synthetic grounded evidence.",
                new BigDecimal("0.9"), refs, warning);
    }
}
