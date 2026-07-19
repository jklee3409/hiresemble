package com.hiresemble.common.idempotency;

import java.util.UUID;

record IdempotencyRecord(
        UUID id,
        String requestHash,
        int hashKeyVersion,
        String state,
        Integer responseStatus,
        String responseJson) {

    boolean completed() {
        return "COMPLETED".equals(state);
    }
}
