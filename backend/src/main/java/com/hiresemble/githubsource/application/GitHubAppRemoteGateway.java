package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.VerifiedInstallation;
import java.net.URI;
import java.time.Instant;

public interface GitHubAppRemoteGateway {

    URI installationUrl(String state);

    URI oauthAuthorizationUrl(String state, String codeChallenge);

    UserAuthorization exchangeUserCode(String code, String codeVerifier);

    VerifiedInstallation verifyUserInstallation(String userAccessToken, long installationId);

    VerifiedInstallation inspectInstallation(long installationId);

    InstallationToken mintInstallationToken(
            long installationId, Long externalRepositoryId, boolean contentsRead);

    void uninstall(long installationId);

    record UserAuthorization(String accessToken) {}

    record InstallationToken(String value, Instant expiresAt) {}
}
