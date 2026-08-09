package com.hiresemble.githubsource.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.application.GitHubAppRemoteGateway;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.TokenTarget;
import com.hiresemble.githubsource.domain.GitHubAppConnectionStatus;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GitHubAppSecurityBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void blankAppIdBindsSafelyWhileThePrivateFeatureIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("hiresemble.github.private-enabled", "false")
                .withProperty("hiresemble.github.app.app-id", "");
        GitHubProperties properties = Binder.get(environment)
                .bind("hiresemble.github", Bindable.of(GitHubProperties.class))
                .orElseGet(GitHubProperties::new);

        assertThat(properties.getApp().getAppId()).isNull();
        properties.afterPropertiesSet();
    }

    @Test
    void privateFeatureFailsConfigurationValidationWhenCredentialsAreMissingOrHostIsUnsafe()
            throws Exception {
        GitHubProperties missing = new GitHubProperties();
        missing.setPrivateEnabled(true);
        assertThatThrownBy(missing::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GitHub ingestion configuration is invalid");

        GitHubProperties configured = configuredProperties(generateKeyPair());
        configured.afterPropertiesSet();
        configured.getApp().setFrontendBaseUrl(
                java.net.URI.create("https://frontend.example/path-from-input"));
        assertThatThrownBy(configured::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appJwtUsesRs256ExpectedAppIdentityAndAWindowShorterThanTenMinutes()
            throws Exception {
        KeyPair keyPair = generateKeyPair();
        GitHubAppJwtSigner signer = new GitHubAppJwtSigner(
                42L, privateKeyPem(keyPair), Clock.fixed(NOW, ZoneOffset.UTC));

        String token = signer.create();
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode header = objectMapper.readTree(decode(parts[0]));
        JsonNode claims = objectMapper.readTree(decode(parts[1]));
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        assertThat(claims.get("iss").asLong()).isEqualTo(42L);
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.minusSeconds(60).getEpochSecond());
        assertThat(claims.get("exp").asLong()).isEqualTo(NOW.plusSeconds(540).getEpochSecond());

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(keyPair.getPublic());
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void installationTokenCacheIsRepositoryScopedExpiresAtSafetySkewAndChecksStateEveryTime() {
        UUID connectionId = UUID.randomUUID();
        long repositoryId = 7101L;
        GitHubAppConnectionStore store = mock(GitHubAppConnectionStore.class);
        GitHubAppRemoteGateway remote = mock(GitHubAppRemoteGateway.class);
        MutableClock clock = new MutableClock(NOW);
        when(store.tokenTarget(connectionId, repositoryId))
                .thenReturn(new TokenTarget(
                        connectionId, 91L, GitHubAccountType.ORGANIZATION,
                        GitHubAppConnectionStatus.ACTIVE));
        when(remote.mintInstallationToken(91L, repositoryId, true))
                .thenReturn(new GitHubAppRemoteGateway.InstallationToken(
                        "memory-only-fixture", NOW.plusSeconds(3600)))
                .thenReturn(new GitHubAppRemoteGateway.InstallationToken(
                        "rotated-memory-only-fixture", NOW.plusSeconds(7200)));
        CachingGitHubInstallationTokenProvider provider =
                new CachingGitHubInstallationTokenProvider(
                        store, remote, configuredPropertiesUnchecked(), clock);

        assertThat(provider.authorize(connectionId, repositoryId, true).value())
                .isEqualTo("memory-only-fixture");
        assertThat(provider.authorize(connectionId, repositoryId, true).value())
                .isEqualTo("memory-only-fixture");
        verify(remote, times(1)).mintInstallationToken(91L, repositoryId, true);

        clock.set(NOW.plusSeconds(3480));
        assertThat(provider.authorize(connectionId, repositoryId, true).value())
                .isEqualTo("rotated-memory-only-fixture");
        verify(remote, times(2)).mintInstallationToken(91L, repositoryId, true);

        when(store.tokenTarget(connectionId, repositoryId))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_INSTALLATION_REVOKED));
        assertThatThrownBy(() -> provider.authorize(connectionId, repositoryId, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(ErrorCode.GITHUB_INSTALLATION_REVOKED));
        assertThatThrownBy(() -> provider.authorize(connectionId, null, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(ErrorCode.GITHUB_APP_PERMISSION_MISMATCH));
        verify(remote, times(2)).mintInstallationToken(eq(91L), eq(repositoryId), eq(true));
    }

    private GitHubProperties configuredPropertiesUnchecked() {
        try {
            return configuredProperties(generateKeyPair());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private GitHubProperties configuredProperties(KeyPair keyPair) {
        GitHubProperties properties = new GitHubProperties();
        properties.setPrivateEnabled(true);
        properties.getApp().setAppId(42L);
        properties.getApp().setSlug("hiresemble-fixture");
        properties.getApp().setClientId("fixture_client_123");
        properties.getApp().setClientSecret("fixture-client-secret-value");
        properties.getApp().setPrivateKey(privateKeyPem(keyPair));
        properties.getApp().setStateSecret("fixture-state-secret-with-at-least-32-bytes");
        return properties;
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String privateKeyPem(KeyPair keyPair) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static final class MutableClock extends Clock {
        private Instant value;

        private MutableClock(Instant value) {
            this.value = value;
        }

        private void set(Instant value) {
            this.value = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return value;
        }
    }
}
