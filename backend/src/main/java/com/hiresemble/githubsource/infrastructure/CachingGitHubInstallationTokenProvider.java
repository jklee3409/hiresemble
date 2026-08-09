package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.application.GitHubAppRemoteGateway;
import com.hiresemble.githubsource.application.GitHubInstallationTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public final class CachingGitHubInstallationTokenProvider
        implements GitHubInstallationTokenProvider {

    private final GitHubAppConnectionStore store;
    private final GitHubAppRemoteGateway remote;
    private final GitHubProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<CacheKey, CachedToken> cache = new ConcurrentHashMap<>();

    public CachingGitHubInstallationTokenProvider(
            GitHubAppConnectionStore store,
            GitHubAppRemoteGateway remote,
            GitHubProperties properties,
            Clock clock) {
        this.store = store;
        this.remote = remote;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public AuthorizedToken authorize(
            UUID connectionId, Long externalRepositoryId, boolean contentsRead) {
        if (contentsRead && externalRepositoryId == null) {
            throw new BusinessException(ErrorCode.GITHUB_APP_PERMISSION_MISMATCH);
        }
        var target = store.tokenTarget(connectionId, externalRepositoryId);
        CacheKey key = new CacheKey(connectionId, externalRepositoryId, contentsRead);
        Instant now = clock.instant();
        CachedToken existing = cache.get(key);
        if (existing != null && existing.usableAt(now)) {
            return new AuthorizedToken(existing.value(), existing.expiresAt(), target.targetAccountType());
        }
        cache.remove(key);
        var minted = remote.mintInstallationToken(
                target.installationId(), externalRepositoryId, contentsRead);
        Instant usableUntil = minted.expiresAt().minus(properties.getApp().getTokenExpirySkew());
        if (!usableUntil.isAfter(now)) {
            throw new BusinessException(ErrorCode.GITHUB_UPSTREAM_AUTHENTICATION_FAILED);
        }
        CachedToken cached = new CachedToken(minted.value(), minted.expiresAt(), usableUntil);
        cache.put(key, cached);
        return new AuthorizedToken(cached.value(), cached.expiresAt(), target.targetAccountType());
    }

    @Override
    public void invalidate(UUID connectionId, Long externalRepositoryId) {
        cache.keySet().removeIf(key -> key.connectionId().equals(connectionId)
                && (externalRepositoryId == null
                        || externalRepositoryId.equals(key.externalRepositoryId())));
    }

    private record CacheKey(
            UUID connectionId, Long externalRepositoryId, boolean contentsRead) {}

    private record CachedToken(String value, Instant expiresAt, Instant usableUntil) {
        private boolean usableAt(Instant now) {
            return usableUntil.isAfter(now);
        }
    }
}
