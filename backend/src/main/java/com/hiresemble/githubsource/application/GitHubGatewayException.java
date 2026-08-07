package com.hiresemble.githubsource.application;

import java.time.Duration;

public final class GitHubGatewayException extends RuntimeException {

    private final Kind kind;
    private final Duration retryAfter;

    public GitHubGatewayException(Kind kind) {
        this(kind, null, null);
    }

    public GitHubGatewayException(Kind kind, Duration retryAfter) {
        this(kind, retryAfter, null);
    }

    public GitHubGatewayException(Kind kind, Throwable cause) {
        this(kind, null, cause);
    }

    private GitHubGatewayException(Kind kind, Duration retryAfter, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public enum Kind {
        NOT_FOUND,
        RATE_LIMITED,
        UPSTREAM_5XX,
        TIMEOUT,
        INVALID_RESPONSE,
        RESPONSE_LIMIT
    }
}
