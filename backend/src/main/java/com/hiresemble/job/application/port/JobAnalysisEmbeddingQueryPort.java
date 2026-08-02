package com.hiresemble.job.application.port;

import java.util.List;
import java.util.UUID;

public interface JobAnalysisEmbeddingQueryPort {

    EmbeddingPolicySnapshot activePolicy();

    List<SimilarEvidenceChunk> exactCosineSearch(
            UUID userId,
            List<Double> queryVector,
            long policyVersion,
            int generation,
            int limit);

    record EmbeddingPolicySnapshot(
            long version,
            String providerKey,
            String productKey,
            int dimension,
            int generation) {
        public EmbeddingPolicySnapshot {
            if (version < 1
                    || providerKey == null
                    || providerKey.isBlank()
                    || productKey == null
                    || productKey.isBlank()
                    || dimension < 1
                    || generation < 1) {
                throw new IllegalArgumentException("embedding policy snapshot is invalid");
            }
        }
    }

    record SimilarEvidenceChunk(
            UUID chunkId,
            UUID documentId,
            String maskedContent,
            double distance) {}
}
