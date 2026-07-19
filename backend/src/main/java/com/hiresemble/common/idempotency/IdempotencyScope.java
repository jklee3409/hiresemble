package com.hiresemble.common.idempotency;

import java.util.Locale;
import java.util.UUID;

public record IdempotencyScope(
        UUID userId,
        String httpMethod,
        String routeScope,
        UUID resourceScopeId,
        String idempotencyKey) {

    public static final UUID ROOT_SCOPE_ID = new UUID(0L, 0L);

    public IdempotencyScope {
        httpMethod = httpMethod.toUpperCase(Locale.ROOT);
    }
}
