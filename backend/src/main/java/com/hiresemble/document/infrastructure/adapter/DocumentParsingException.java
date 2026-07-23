package com.hiresemble.document.infrastructure.adapter;

public final class DocumentParsingException extends RuntimeException {

    private final String safeCode;

    public DocumentParsingException(String safeCode) {
        super(safeCode);
        this.safeCode = safeCode;
    }

    public DocumentParsingException(String safeCode, Throwable cause) {
        super(safeCode, cause);
        this.safeCode = safeCode;
    }

    public String safeCode() {
        return safeCode;
    }
}
