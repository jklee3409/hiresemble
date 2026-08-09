package com.hiresemble.githubsource.application;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.Connection;
import com.hiresemble.githubsource.infrastructure.GitHubAppConnectionStore;
import com.hiresemble.githubsource.infrastructure.GitHubProperties;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxStore;
import com.hiresemble.githubsource.infrastructure.GitHubSourceStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public class GitHubAppConnectionService {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GitHubAppConnectionStore store;
    private final GitHubSourceStore sourceStore;
    private final GitHubSnapshotDeletionOutboxStore snapshotOutbox;
    private final GitHubAppRemoteGateway remote;
    private final GitHubInstallationTokenProvider tokenProvider;
    private final GitHubProperties properties;
    private final Clock clock;

    public GitHubAppConnectionService(
            GitHubAppConnectionStore store,
            GitHubSourceStore sourceStore,
            GitHubSnapshotDeletionOutboxStore snapshotOutbox,
            GitHubAppRemoteGateway remote,
            GitHubInstallationTokenProvider tokenProvider,
            GitHubProperties properties,
            Clock clock) {
        this.store = store;
        this.sourceStore = sourceStore;
        this.snapshotOutbox = snapshotOutbox;
        this.remote = remote;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.clock = clock;
    }

    public Capability capability() {
        return new Capability(true, true, List.of("metadata:read", "contents:read"));
    }

    @Transactional
    public ConnectionStart start(UUID userId, String sessionId) {
        requireSession(userId, sessionId);
        UUID attemptId = UUID.randomUUID();
        String state = randomState();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getApp().getAttemptTtl());
        store.createAttempt(
                attemptId,
                userId,
                sessionBinding(userId, sessionId),
                digest(state),
                expiresAt,
                now);
        return new ConnectionStart(remote.installationUrl(state), expiresAt);
    }

    @Transactional
    public URI setupCallback(
            UUID userId,
            String sessionId,
            String state,
            Long installationId,
            String setupAction) {
        requireSession(userId, sessionId);
        requireState(state);
        if (installationId == null || installationId <= 0
                || !("install".equals(setupAction) || "update".equals(setupAction))) {
            throw invalidState();
        }
        String oauthState = randomState();
        Instant now = clock.instant();
        var attempt = store.transitionToOAuth(
                        userId,
                        sessionBinding(userId, sessionId),
                        digest(state),
                        installationId,
                        digest(oauthState),
                        now.plus(properties.getApp().getAttemptTtl()),
                        now)
                .orElseThrow(this::invalidState);
        String verifier = pkceVerifier(attempt.id());
        String challenge = BASE64_URL.encodeToString(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return remote.oauthAuthorizationUrl(oauthState, challenge);
    }

    @Transactional
    public Connection oauthCallback(
            UUID userId, String sessionId, String state, String code) {
        requireSession(userId, sessionId);
        requireState(state);
        if (code == null || code.isBlank() || code.length() > 1024
                || code.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidState();
        }
        Instant now = clock.instant();
        var attempt = store.consumeOAuth(
                        userId,
                        sessionBinding(userId, sessionId),
                        digest(state),
                        now)
                .orElseThrow(this::invalidState);
        try {
            var authorization = remote.exchangeUserCode(code, pkceVerifier(attempt.id()));
            var verified = remote.verifyUserInstallation(
                    authorization.accessToken(), attempt.pendingInstallationId());
            return store.createConnection(UUID.randomUUID(), userId, verified, now);
        } catch (GitHubGatewayException exception) {
            throw mapGateway(exception);
        }
    }

    public List<Connection> list(UUID userId) {
        return store.list(userId);
    }

    @Transactional
    public Connection refresh(UUID userId, UUID connectionId, long expectedVersion) {
        Connection current = store.findOwner(userId, connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            var verified = remote.inspectInstallation(current.installationId());
            Connection refreshed = store.refresh(
                    userId, connectionId, expectedVersion, verified, clock.instant());
            tokenProvider.invalidate(connectionId, null);
            return refreshed;
        } catch (GitHubGatewayException exception) {
            if (exception.kind() == GitHubGatewayException.Kind.NOT_FOUND) {
                store.markRevoked(current.installationId(), clock.instant());
                tokenProvider.invalidate(connectionId, null);
                throw new BusinessException(ErrorCode.GITHUB_INSTALLATION_REVOKED, exception);
            }
            throw mapGateway(exception);
        }
    }

    @Transactional
    public Connection disconnect(
            UUID userId, UUID connectionId, long expectedVersion, boolean uninstallConfirmed) {
        if (!uninstallConfirmed) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Instant now = clock.instant();
        Connection connection = store.beginDisconnect(
                userId, connectionId, expectedVersion, "USER_DISCONNECT", now);
        tokenProvider.invalidate(connectionId, null);
        for (GitHubSourceStore.SnapshotObject snapshot
                : sourceStore.snapshotObjectsForConnection(userId, connectionId)) {
            snapshotOutbox.enqueuePrivateConnection(
                    userId, snapshot.snapshotId(), snapshot.storageKey(), now);
        }
        return connection;
    }

    public URI frontendResult(String result) {
        String safe = switch (result) {
            case "connected", "cancelled", "expired", "permission", "unavailable" -> result;
            default -> "unavailable";
        };
        return URI.create(properties.getApp().getFrontendBaseUrl()
                .resolve("/profile/github") + "?githubAppResult=" + safe);
    }

    private void requireSession(UUID userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank() || sessionId.length() > 256) {
            throw invalidState();
        }
    }

    private void requireState(String state) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw invalidState();
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }

    private String sessionBinding(UUID userId, String sessionId) {
        return hmacHex("session|" + userId + "|" + sessionId);
    }

    private String pkceVerifier(UUID attemptId) {
        byte[] value = hmac("pkce|" + attemptId);
        return BASE64_URL.encodeToString(value);
    }

    private String digest(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String hmacHex(String value) {
        return HexFormat.of().formatHex(hmac(value));
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getApp().getStateSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    private BusinessException mapGateway(GitHubGatewayException exception) {
        return switch (exception.kind()) {
            case PERMISSION -> new BusinessException(ErrorCode.GITHUB_APP_PERMISSION_MISMATCH, exception);
            case NOT_FOUND -> new BusinessException(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE, exception);
            case AUTHENTICATION ->
                    new BusinessException(ErrorCode.GITHUB_UPSTREAM_AUTHENTICATION_FAILED, exception);
            case RATE_LIMITED -> new BusinessException(ErrorCode.GITHUB_RATE_LIMITED, exception);
            default -> new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        };
    }

    private BusinessException invalidState() {
        return new BusinessException(ErrorCode.GITHUB_CONNECTION_STATE_INVALID_OR_EXPIRED);
    }

    public record Capability(boolean enabled, boolean configured, List<String> permissions) {
        public Capability {
            permissions = List.copyOf(permissions);
        }
    }

    public record ConnectionStart(URI installationUrl, Instant expiresAt) {}
}
