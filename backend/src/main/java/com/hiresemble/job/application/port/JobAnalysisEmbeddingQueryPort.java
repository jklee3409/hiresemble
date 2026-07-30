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
            long version, int dimension, int generation) {}

    record SimilarEvidenceChunk(
            UUID chunkId,
            UUID documentId,
            String maskedContent,
            double distance) {}
}
