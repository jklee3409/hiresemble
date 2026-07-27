package com.hiresemble.job.application.port;

public final class JobPageFetchException extends RuntimeException {

    private final String safeErrorCode;
    private final boolean retryable;

    public JobPageFetchException(String safeErrorCode, boolean retryable) {
        this(safeErrorCode, retryable, null);
    }

    public JobPageFetchException(String safeErrorCode, boolean retryable, Throwable cause) {
        super(safeErrorCode, cause);
        if (safeErrorCode == null || safeErrorCode.isBlank() || safeErrorCode.length() > 100) {
            throw new IllegalArgumentException("safe job fetch error code is invalid");
        }
        this.safeErrorCode = safeErrorCode;
        this.retryable = retryable;
    }

    public String safeErrorCode() {
        return safeErrorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
