package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.domain.GitHubAccessMode;
import java.util.UUID;

public record GitHubAccessContext(
        GitHubAccessMode mode, UUID connectionId, Long externalRepositoryId) {

    public GitHubAccessContext {
        if (mode == null
                || (mode == GitHubAccessMode.PUBLIC
                        && (connectionId != null || externalRepositoryId != null))
                || (mode == GitHubAccessMode.GITHUB_APP && connectionId == null)
                || (externalRepositoryId != null && externalRepositoryId <= 0)) {
            throw new IllegalArgumentException("invalid GitHub access context");
        }
    }

    public static GitHubAccessContext publicAccess() {
        return new GitHubAccessContext(GitHubAccessMode.PUBLIC, null, null);
    }

    public static GitHubAccessContext appDiscovery(UUID connectionId) {
        return new GitHubAccessContext(GitHubAccessMode.GITHUB_APP, connectionId, null);
    }

    public static GitHubAccessContext appRepository(UUID connectionId, long externalRepositoryId) {
        return new GitHubAccessContext(
                GitHubAccessMode.GITHUB_APP, connectionId, externalRepositoryId);
    }
}
