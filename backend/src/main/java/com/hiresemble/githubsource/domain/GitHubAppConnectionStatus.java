package com.hiresemble.githubsource.domain;

public enum GitHubAppConnectionStatus {
    ACTIVE,
    SUSPENDED,
    DISCONNECTING,
    DISCONNECTED,
    REVOKED;

    public boolean allowsTokenMint() {
        return this == ACTIVE;
    }
}
