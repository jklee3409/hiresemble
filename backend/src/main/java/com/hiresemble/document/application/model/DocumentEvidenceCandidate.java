package com.hiresemble.document.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentEvidenceCandidate(
        String evidenceCategory,
        String title,
        String content,
        Map<String, Object> metadata,
        BigDecimal confidence,
        List<UUID> sourceChunkIds,
        long sourceRevision,
        String validationWarning,
        List<Double> embedding) {

    public DocumentEvidenceCandidate {
        metadata = metadata == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
        sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
    }

    public DocumentEvidenceCandidate(
            String evidenceCategory,
            String title,
            String content,
            Map<String, Object> metadata,
            BigDecimal confidence,
            List<UUID> sourceChunkIds,
            long sourceRevision,
            String validationWarning) {
        this(
                evidenceCategory,
                title,
                content,
                metadata,
                confidence,
                sourceChunkIds,
                sourceRevision,
                validationWarning,
                List.of());
    }
}
