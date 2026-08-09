package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.domain.GitHubAccountType;
import java.time.Instant;
import java.util.UUID;

public interface GitHubInstallationTokenProvider {

    AuthorizedToken authorize(UUID connectionId, Long externalRepositoryId, boolean contentsRead);

    void invalidate(UUID connectionId, Long externalRepositoryId);

    record AuthorizedToken(String value, Instant expiresAt, GitHubAccountType targetAccountType) {}
}
