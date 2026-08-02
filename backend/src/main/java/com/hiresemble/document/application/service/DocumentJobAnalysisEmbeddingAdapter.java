package com.hiresemble.document.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.document.infrastructure.persistence.DocumentStore;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentJobAnalysisEmbeddingAdapter
        implements JobAnalysisEmbeddingQueryPort {

    private final DocumentStore store;

    public DocumentJobAnalysisEmbeddingAdapter(DocumentStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public EmbeddingPolicySnapshot activePolicy() {
        EmbeddingPolicy policy = store.activeEmbeddingPolicy();
        return new EmbeddingPolicySnapshot(
                policy.version(),
                policy.provider(),
                policy.model(),
                policy.dimension(),
                policy.generation());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimilarEvidenceChunk> exactCosineSearch(
            UUID userId,
            List<Double> queryVector,
            long policyVersion,
            int generation,
            int limit) {
        EmbeddingPolicy policy = store.activeEmbeddingPolicy();
        if (userId == null
                || policy.version() != policyVersion
                || policy.generation() != generation
                || queryVector == null
                || queryVector.size() != policy.dimension()
                || queryVector.stream()
                        .anyMatch(value -> value == null || !Double.isFinite(value))
                || limit < 1
                || limit > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return store.exactCosineSearch(
                        userId, queryVector, policyVersion, generation, limit)
                .stream()
                .map(value -> new SimilarEvidenceChunk(
                        value.chunkId(),
                        value.documentId(),
                        value.maskedContent(),
                        value.distance()))
                .toList();
    }
}
