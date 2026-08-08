package com.hiresemble.careerartifact.application;

public record RenderedOfficeFile(
        byte[] bytes,
        String mimeType,
        long sizeBytes,
        String checksumSha256) {

    public RenderedOfficeFile {
        bytes = bytes == null ? null : bytes.clone();
        if (bytes == null || bytes.length == 0 || sizeBytes != bytes.length
                || mimeType == null || mimeType.isBlank()
                || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("rendered office file is invalid");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
