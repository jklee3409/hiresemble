package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.ai.context.ContextBuilder.ContextRef;
import com.hiresemble.ai.context.ContextBuilder.ContextRequest;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.context.ContextBuilder.ResourceSnapshotRef;
import com.hiresemble.ai.context.ContextBuilder.TruncationSummary;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.document.application.DocumentWorkflowQueryPort;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/** Owner-scoped, provenance-only context for DOCUMENT_INGESTION. */
public final class DocumentIngestionContextBuilder implements ContextBuilder {

    private final DocumentWorkflowQueryPort documentQuery;
    private final long modelPolicyVersion;

    public DocumentIngestionContextBuilder(
            DocumentWorkflowQueryPort documentQuery, long modelPolicyVersion) {
        if (modelPolicyVersion < 1) throw new IllegalArgumentException("model policy is invalid");
        this.documentQuery = documentQuery;
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.DOCUMENT_INGESTION
                || !"DOCUMENT".equals(run.resourceType()) || run.resourceId() == null) {
            throw configurationFailure();
        }
        UUID inputDocumentId = parseDocumentId(run);
        if (!run.resourceId().equals(inputDocumentId)) throw ownerFailure();
        DocumentRecord document = documentQuery.snapshot(run.userId(), inputDocumentId);
        if (document.deletedAt() != null || documentQuery.isDeleted(run.userId(), inputDocumentId)) {
            throw ownerFailure();
        }
        List<ContextRef> chunkRefs = documentQuery
                .chunks(run.userId(), document.id(), document.sourceRevision())
                .stream()
                .map(chunk -> new ContextRef(
                        "DOCUMENT_CHUNK", chunk.id(), chunk.sourceRevision(), "UNVERIFIED"))
                .toList();
        String contextHash = sha256(String.join("|",
                run.userId().toString(),
                document.id().toString(),
                Long.toString(document.sourceRevision()),
                document.checksumSha256(),
                document.documentType().name(),
                chunkRefs.stream().map(value -> value.referenceId().toString())
                        .sorted().collect(java.util.stream.Collectors.joining(","))));
        return new ContextSnapshot(
                run.userId(),
                List.of(new ResourceSnapshotRef(
                        "DOCUMENT", document.id(), document.sourceRevision(),
                        document.checksumSha256())),
                List.of(),
                chunkRefs,
                new TruncationSummary(chunkRefs.size(), 0, List.of()),
                contextHash,
                "UNVERIFIED_DOCUMENT",
                modelPolicyVersion,
                false,
                true);
    }

    private UUID parseDocumentId(AgentRunSnapshot run) {
        try {
            return UUID.fromString(run.inputReferenceSnapshot().path("documentId").asText());
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private String sha256(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                "RESOURCE_NOT_FOUND",
                "요청한 문서를 찾을 수 없습니다.");
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "AI 실행 구성이 준비되지 않았습니다.");
    }
}
