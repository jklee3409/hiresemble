package com.hiresemble.common.idempotency;

public record OriginalResponse<T>(int status, T body) {

    public OriginalResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("response status must be a valid HTTP status");
        }
    }
}
