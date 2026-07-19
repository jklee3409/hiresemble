package com.hiresemble.common.idempotency;

import java.util.UUID;

public record OriginalResponse<T>(
        int status,
        T body,
        String resourceType,
        UUID resourceId,
        UUID agentRunId) {

    public OriginalResponse(int status, T body) {
        this(status, body, null, null, null);
    }

    public OriginalResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("response status must be a valid HTTP status");
        }
        if ((resourceType == null) != (resourceId == null)) {
            throw new IllegalArgumentException("resource metadata must be a pair");
        }
    }
}
