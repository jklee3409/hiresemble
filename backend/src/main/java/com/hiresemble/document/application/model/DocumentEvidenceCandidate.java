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
        String validationWarning) {

    public DocumentEvidenceCandidate {
        metadata = metadata == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata));
        sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
    }
}
