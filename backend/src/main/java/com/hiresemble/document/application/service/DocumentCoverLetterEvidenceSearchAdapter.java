package com.hiresemble.document.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.port.CoverLetterEvidenceSearchPort;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.document.infrastructure.persistence.DocumentStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCoverLetterEvidenceSearchAdapter
        implements CoverLetterEvidenceSearchPort {

    private final DocumentStore store;

    public DocumentCoverLetterEvidenceSearchAdapter(DocumentStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateChunk> searchMaskedCandidates(
            UUID userId, List<Double> queryVector, int limit) {
        EmbeddingPolicy policy = store.activeEmbeddingPolicy();
        if (userId == null
                || queryVector == null
                || queryVector.size() != policy.dimension()
                || queryVector.stream().anyMatch(value -> value == null || !Double.isFinite(value))
                || limit < 1
                || limit > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return store.exactCosineSearch(
                        userId,
                        queryVector,
                        policy.version(),
                        policy.generation(),
                        limit)
                .stream()
                .map(value -> new CandidateChunk(
                        value.chunkId(),
                        value.documentId(),
                        value.maskedContent(),
                        value.distance()))
                .toList();
    }
}
