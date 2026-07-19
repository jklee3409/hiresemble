package com.hiresemble.document.application;

import com.hiresemble.document.domain.DocumentRecords.ChunkDraft;
import com.hiresemble.document.domain.DocumentRecords.DocumentChunkRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentTextRecord;
import com.hiresemble.document.domain.DocumentRecords.EmbeddingPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DocumentWorkflowCommandPort {

    DocumentRecord beginParsing(UUID userId, UUID documentId, UUID agentRunId);

    DocumentTextRecord extractOrAcceptText(UUID userId, UUID documentId, UUID agentRunId);

    DocumentTextRecord maskText(UUID userId, UUID documentId, UUID agentRunId);

    List<DocumentChunkRecord> chunkText(UUID userId, UUID documentId, UUID agentRunId);

    List<DocumentChunkRecord> replaceChunks(
            UUID userId,
            UUID documentId,
            UUID agentRunId,
            List<ChunkDraft> chunks);

    void storeEmbeddings(
            UUID userId,
            UUID documentId,
            UUID agentRunId,
            EmbeddingPolicy policy,
            Map<UUID, List<Double>> embeddings);

    void beginEvidenceExtraction(UUID userId, UUID documentId, UUID agentRunId);

    EvidenceApplyResult applyEvidenceCandidates(
            UUID userId,
            UUID documentId,
            UUID agentRunId,
            List<DocumentEvidenceCandidate> candidates);

    DocumentRecord finalizeDocument(UUID userId, UUID documentId, UUID agentRunId);

    void failEvidenceExtraction(
            UUID userId, UUID documentId, UUID agentRunId, String safeErrorCode);

    void compensateToStableState(UUID userId, UUID documentId, UUID agentRunId);

    record EvidenceApplyResult(List<UUID> appliedEvidenceIds, int rejectedCount) {
        public EvidenceApplyResult {
            appliedEvidenceIds = List.copyOf(appliedEvidenceIds);
        }
    }
}
