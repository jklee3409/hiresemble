package com.hiresemble.profile.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DirectEvidenceData(
        EvidenceSourceType sourceType,
        String evidenceCategory,
        String title,
        String content,
        Map<String, Object> metadata) {

    public DirectEvidenceData {
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
