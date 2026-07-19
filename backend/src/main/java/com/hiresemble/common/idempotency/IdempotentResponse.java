package com.hiresemble.common.idempotency;

public record IdempotentResponse<T>(int status, T body, boolean replayed) {}
