package com.hiresemble.document.application.port;

import com.hiresemble.document.domain.model.DocumentRecords.DocumentChunkRecord;
import com.hiresemble.document.domain.model.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.model.DocumentRecords.DocumentTextRecord;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.document.domain.model.DocumentRecords.SimilarChunk;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentWorkflowQueryPort {

    DocumentRecord snapshot(UUID userId, UUID documentId);

    byte[] source(UUID userId, UUID documentId);

    Optional<DocumentTextRecord> text(UUID userId, UUID documentId, long sourceRevision);

    List<DocumentChunkRecord> chunks(UUID userId, UUID documentId, long sourceRevision);

    EmbeddingPolicy activeEmbeddingPolicy();

    List<SimilarChunk> exactCosineSearch(
            UUID userId,
            List<Double> queryVector,
            long policyVersion,
            int generation,
            int limit);

    boolean isDeleted(UUID userId, UUID documentId);

    String displayLabel(UUID userId, UUID documentId);
}
