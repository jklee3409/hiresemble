package com.hiresemble.careerartifact.application;

import java.util.List;

public record OfficeValidation(
        boolean valid,
        String mimeType,
        long sizeBytes,
        String checksumSha256,
        int contentUnitCount,
        List<String> warnings) {

    public OfficeValidation {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
