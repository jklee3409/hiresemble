package com.hiresemble.githubsource.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict model output shape. Ownership and database identifiers are supplied by the server envelope. */
public record GitHubEvidenceCandidate(
        String evidenceCategory,
        String title,
        String content,
        Map<String, Object> metadata,
        BigDecimal confidence,
        List<String> sourceUnitReferences,
        List<Double> embedding) {

    public GitHubEvidenceCandidate {
        metadata = metadata == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        sourceUnitReferences = sourceUnitReferences == null ? List.of() : List.copyOf(sourceUnitReferences);
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
    }
}
