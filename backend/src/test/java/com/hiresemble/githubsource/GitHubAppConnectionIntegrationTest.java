package com.hiresemble.githubsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.githubsource.application.GitHubAppConnectionService;
import com.hiresemble.githubsource.application.GitHubAppRemoteGateway;
import com.hiresemble.githubsource.application.GitHubAccessContext;
import com.hiresemble.githubsource.application.GitHubGatewayModels.AccountDiscovery;
import com.hiresemble.githubsource.application.GitHubGatewayModels.Blob;
import com.hiresemble.githubsource.application.GitHubGatewayModels.CommitMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.ConditionalRepository;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeSnapshot;
import com.hiresemble.githubsource.application.GitHubEvidenceCandidate;
import com.hiresemble.githubsource.application.GitHubRestGateway;
import com.hiresemble.githubsource.application.GitHubSnapshotStoragePort;
import com.hiresemble.githubsource.application.GitHubSourceApplicationService;
import com.hiresemble.githubsource.application.GitHubSourceWorkflowService;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubAccessMode;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.VerifiedInstallation;
import com.hiresemble.githubsource.domain.GitHubRepositorySelectionKind;
import com.hiresemble.githubsource.infrastructure.GitHubInstallationRevocationOutboxWorker;
import com.hiresemble.githubsource.infrastructure.GitHubPrivateConnectionCleanupWorker;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxWorker;
import com.hiresemble.profile.application.service.ExperienceApplicationService;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceVerification;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.support.PostgresIntegrationTest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(GitHubAppConnectionIntegrationTest.FakeGatewayConfiguration.class)
@AutoConfigureMockMvc
class GitHubAppConnectionIntegrationTest extends PostgresIntegrationTest {

    private static final String PRIVATE_KEY_PEM = generatedPrivateKeyPem();

    @Autowired private GitHubAppConnectionService service;
    @Autowired private FakeGitHubAppGateway remote;
    @Autowired private GitHubInstallationRevocationOutboxWorker revocationWorker;
    @Autowired private GitHubPrivateConnectionCleanupWorker cleanupWorker;
    @Autowired private GitHubSourceApplicationService sourceService;
    @Autowired private GitHubSourceWorkflowService sourceWorkflow;
    @Autowired private FakePrivateGitHubRestGateway privateRestGateway;
    @Autowired private FakePrivateSnapshotStorage privateSnapshotStorage;
    @Autowired private GitHubSnapshotDeletionOutboxWorker snapshotDeletionWorker;
    @Autowired private ExperienceApplicationService experienceService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @DynamicPropertySource
    static void githubAppProperties(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.github.enabled", () -> "true");
        registry.add("hiresemble.github.private-enabled", () -> "true");
        registry.add("hiresemble.github.app.app-id", () -> "42");
        registry.add("hiresemble.github.app.slug", () -> "hiresemble-fixture");
        registry.add("hiresemble.github.app.client-id", () -> "fixture_client_123");
        registry.add("hiresemble.github.app.client-secret", () -> "fixture-client-secret-value");
        registry.add("hiresemble.github.app.private-key", () -> PRIVATE_KEY_PEM);
        registry.add(
                "hiresemble.github.app.state-secret",
                () -> "fixture-state-secret-with-at-least-32-bytes");
        registry.add("hiresemble.github.app.backend-base-url", () -> "http://localhost:8080");
        registry.add("hiresemble.github.app.frontend-base-url", () -> "http://localhost:5173");
    }

    @BeforeEach
    void resetFake() {
        remote.reset();
        privateRestGateway.reset();
        privateSnapshotStorage.values.clear();
    }

    @Test
    void twoStageConnectionBindsSessionUsesOneTimeStatesAndPersistsOnlyVerifiedInstallation()
            throws Exception {
        UUID userId = seedUser("github-app-flow@example.com");
        String sessionId = "fixture-session-" + UUID.randomUUID();

        var started = service.start(userId, sessionId);
        String installationState = query(started.installationUrl()).get("state");
        assertThat(installationState).hasSize(43);
        Map<String, Object> storedInstall = jdbcTemplate.queryForMap(
                "SELECT * FROM github_connection_attempts");
        assertThat(storedInstall.get("phase")).isEqualTo("INSTALL_PENDING");
        assertThat(storedInstall.get("state_digest").toString())
                .hasSize(64)
                .isNotEqualTo(installationState);
        assertThat(storedInstall.get("session_binding_digest").toString())
                .hasSize(64)
                .doesNotContain(sessionId);

        assertInvalidState(() -> service.setupCallback(
                userId, "different-session", installationState, 71001L, "install"));
        URI authorization = service.setupCallback(
                userId, sessionId, installationState, 71001L, "install");
        assertInvalidState(() -> service.setupCallback(
                userId, sessionId, installationState, 71001L, "install"));
        String oauthState = query(authorization).get("state");
        assertThat(oauthState).hasSize(43).isNotEqualTo(installationState);
        assertThat(remote.lastChallenge).matches("[A-Za-z0-9_-]{43}");
        Map<String, Object> storedOauth = jdbcTemplate.queryForMap(
                "SELECT * FROM github_connection_attempts");
        assertThat(storedOauth.get("phase")).isEqualTo("OAUTH_PENDING");
        assertThat(storedOauth.get("pending_installation_id")).isEqualTo(71001L);
        assertThat(storedOauth.get("state_digest").toString())
                .isNotEqualTo(oauthState)
                .doesNotContain(oauthState);

        var connected = service.oauthCallback(userId, sessionId, oauthState, "fixture-code");
        assertThat(connected.status().name()).isEqualTo("ACTIVE");
        assertThat(connected.targetAccountLogin()).isEqualTo("acme");
        assertThat(connected.repositorySelection())
                .isEqualTo(GitHubRepositorySelectionKind.SELECTED);
        assertThat(remote.lastCodeVerifier).matches("[A-Za-z0-9_-]{43}");
        assertThat(pkceChallenge(remote.lastCodeVerifier)).isEqualTo(remote.lastChallenge);
        assertInvalidState(() ->
                service.oauthCallback(userId, sessionId, oauthState, "replayed-code"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT permission_snapshot::text FROM github_app_connections WHERE id=?",
                        String.class,
                        connected.id()))
                .isEqualTo("{\"contents\": \"read\", \"metadata\": \"read\"}");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT visibility FROM github_repositories WHERE user_id=? ORDER BY visibility",
                        String.class,
                        userId))
                .containsExactly("PRIVATE", "PUBLIC");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM github_app_connection_repository_access WHERE user_id=?",
                        Long.class,
                        userId))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema='public' AND table_name LIKE 'github%'
                          AND column_name IN (
                            'access_token','refresh_token','oauth_code','state','pkce_verifier','app_jwt'
                          )
                        """, Long.class))
                .isZero();
        assertThat(service.frontendResult("connected").toString())
                .isEqualTo("http://localhost:5173/profile/github?githubAppResult=connected");
        assertThat(service.frontendResult("unsafe-input").toString())
                .isEqualTo("http://localhost:5173/profile/github?githubAppResult=unavailable");
    }

    @Test
    void privateFeatureOnOpenApiHasSevenAdditivePathsAndNoCredentialValues() throws Exception {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        int[] operations = {0};
        handlerMapping.getHandlerMethods().forEach((mapping, method) -> {
            java.util.Set<String> apiPaths = new java.util.LinkedHashSet<>();
            mapping.getPatternValues().stream()
                    .filter(path -> path.startsWith("/api/v1/"))
                    .forEach(apiPaths::add);
            paths.addAll(apiPaths);
            operations[0] += apiPaths.size()
                    * mapping.getMethodsCondition().getMethods().size();
        });
        assertThat(paths).hasSize(97);
        assertThat(operations[0]).isEqualTo(128);
        assertThat(paths).contains(
                "/api/v1/github-app-connections/capability",
                "/api/v1/github-app-connections/installation-requests",
                "/api/v1/github-app-connections/setup/callback",
                "/api/v1/github-app-connections/oauth/callback",
                "/api/v1/github-app-connections",
                "/api/v1/github-app-connections/{connectionId}/refresh",
                "/api/v1/github-app-connections/{connectionId}");

        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(document.get("paths").size()).isEqualTo(97);
        assertThat(operationCount(document.get("paths"))).isEqualTo(128);
        assertThat(document.at("/paths/~1api~1v1~1github-app-connections~1installation-requests/post/security/0/sessionCookie")
                        .isArray())
                .isTrue();
        assertThat(document.at("/paths/~1api~1v1~1github-app-connections~1installation-requests/post/security/0/csrfToken")
                        .isArray())
                .isTrue();
        assertThat(document.at("/paths/~1api~1v1~1github-app-connections~1setup~1callback/get/operationId")
                        .asText())
                .isEqualTo("handleGitHubAppSetupCallback");
        assertThat(document.toString()).doesNotContain(
                "fixture-client-secret-value",
                "fixture-state-secret-with-at-least-32-bytes",
                PRIVATE_KEY_PEM,
                "ephemeral-user-fixture",
                "ephemeral-installation-fixture");
    }

    private int operationCount(JsonNode paths) {
        int count = 0;
        for (JsonNode path : paths) {
            for (String method : List.of("get", "post", "put", "patch", "delete")) {
                if (path.has(method)) count++;
            }
        }
        return count;
    }

    @Test
    void expiredOrInaccessibleInstallationIsRejectedAndOauthAttemptCannotBeReplayed() {
        UUID userId = seedUser("github-app-negative@example.com");
        String sessionId = "fixture-session-" + UUID.randomUUID();
        var expired = service.start(userId, sessionId);
        String expiredState = query(expired.installationUrl()).get("state");
        jdbcTemplate.update(
                """
                UPDATE github_connection_attempts
                SET created_at=now()-interval '2 minutes',
                    updated_at=now()-interval '2 minutes',
                    expires_at=now()-interval '1 second'
                """);
        assertInvalidState(() -> service.setupCallback(
                userId, sessionId, expiredState, 71001L, "install"));

        var inaccessible = service.start(userId, sessionId);
        String installState = query(inaccessible.installationUrl()).get("state");
        URI authorization = service.setupCallback(
                userId, sessionId, installState, 99999L, "install");
        String oauthState = query(authorization).get("state");
        assertThatThrownBy(() ->
                        service.oauthCallback(userId, sessionId, oauthState, "fixture-code"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE));
        assertInvalidState(() ->
                service.oauthCallback(userId, sessionId, oauthState, "replayed-code"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM github_app_connections", Long.class))
                .isZero();
    }

    @Test
    void oneExternalInstallationCannotBeLinkedToTwoHiresembleUsers() {
        UUID first = seedUser("github-install-owner@example.com");
        UUID second = seedUser("github-install-other@example.com");
        connect(first, "first-session", 71001L);

        assertThatThrownBy(() -> connect(second, "second-session", 71001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM github_app_connections", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void disconnectBlocksConnectionImmediatelyThenUninstallAndCleanupReachTerminalSuccess() {
        UUID userId = seedUser("github-disconnect@example.com");
        connect(userId, "disconnect-session", 71001L);
        var active = service.list(userId).getFirst();

        var disconnecting = service.disconnect(userId, active.id(), active.version(), true);
        assertThat(disconnecting.status().name()).isEqualTo("DISCONNECTING");
        assertThatThrownBy(() -> service.disconnect(
                        userId, active.id(), disconnecting.version(), true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(ErrorCode.RESOURCE_STATE_CONFLICT));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM github_installation_revocation_outbox "
                                + "WHERE github_app_connection_id=?",
                        String.class,
                        active.id()))
                .isEqualTo("PENDING");

        revocationWorker.processDue();
        assertThat(remote.uninstallAttempts).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM github_installation_revocation_outbox "
                                + "WHERE github_app_connection_id=?",
                        String.class,
                        active.id()))
                .isEqualTo("SUCCEEDED");
        cleanupWorker.processReady();
        assertThat(service.list(userId).getFirst().status().name()).isEqualTo("DISCONNECTED");
    }

    @Test
    void uninstallTransientFailureRetriesWithSafeCodeAndTenthFailureBecomesDead() {
        UUID userId = seedUser("github-disconnect-retry@example.com");
        connect(userId, "disconnect-retry-session", 71001L);
        var active = service.list(userId).getFirst();
        service.disconnect(userId, active.id(), active.version(), true);
        remote.uninstallFailure = GitHubGatewayException.Kind.UPSTREAM_5XX;

        revocationWorker.processDue();

        Map<String, Object> retry = jdbcTemplate.queryForMap("""
                SELECT status,attempt_count,last_error_code,
                       EXTRACT(EPOCH FROM (next_attempt_at-created_at)) AS delay_seconds
                FROM github_installation_revocation_outbox
                WHERE github_app_connection_id=?
                """, active.id());
        assertThat(retry.get("status")).isEqualTo("RETRY_WAIT");
        assertThat(retry.get("attempt_count")).isEqualTo(1);
        assertThat(retry.get("last_error_code"))
                .isEqualTo("GITHUB_INSTALLATION_UNINSTALL_FAILED");
        assertThat(((Number) retry.get("delay_seconds")).doubleValue()).isBetween(58d, 62d);

        jdbcTemplate.update("""
                UPDATE github_installation_revocation_outbox
                SET status='PENDING',attempt_count=9,next_attempt_at=now()-interval '1 second'
                WHERE github_app_connection_id=?
                """, active.id());
        revocationWorker.processDue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM github_installation_revocation_outbox "
                                + "WHERE github_app_connection_id=?",
                        String.class,
                        active.id()))
                .isEqualTo("DEAD");
        assertThat(service.list(userId).getFirst().status().name()).isEqualTo("DISCONNECTING");
    }

    @Test
    void privateRepositoryUsesExistingIngestionPipelineAndRunSnapshotOmitsConnectionSecrets()
            throws Exception {
        UUID userId = seedUser("github-private-ingestion@example.com");
        connect(userId, "private-ingestion-session", 71001L);
        var connection = service.list(userId).getFirst();

        var accepted = sourceService.register(
                userId,
                "https://github.com/acme/private-repo",
                true,
                GitHubAccessMode.GITHUB_APP,
                connection.id(),
                UUID.randomUUID().toString());
        var source = sourceService.detail(userId, accepted.body().resourceId());
        assertThat(source.accessMode()).isEqualTo(GitHubAccessMode.GITHUB_APP);
        assertThat(source.githubAppConnectionId()).isEqualTo(connection.id());
        String runInput = jdbcTemplate.queryForObject(
                "SELECT input_reference_snapshot::text FROM agent_runs WHERE id=?",
                String.class,
                accepted.body().agentRunId());
        assertThat(runInput)
                .contains("\"accessMode\": \"GITHUB_APP\"")
                .doesNotContain(
                        "private-repo",
                        connection.id().toString(),
                        "71001",
                        "canonicalUrl",
                        "token",
                        "code",
                        "state");

        Instant now = Instant.now();
        sourceWorkflow.begin(userId, source.id(), accepted.body().agentRunId(), now);
        var discovery = sourceWorkflow.discover(
                userId, source.id(), accepted.body().agentRunId(), Instant.now());
        assertThat(discovery.repositories()).singleElement().satisfies(repository -> {
            assertThat(repository.visibility().name()).isEqualTo("PRIVATE");
            assertThat(repository.externalRepositoryId()).isEqualTo(8123L);
        });
        var bundle = sourceWorkflow.captureAndStore(
                userId, source.id(), discovery.repositories().getFirst(), Instant.now());
        assertThat(bundle.snapshot().accessMode()).isEqualTo(GitHubAccessMode.GITHUB_APP);
        assertThat(bundle.snapshot().githubAppConnectionId()).isEqualTo(connection.id());
        assertThat(bundle.snapshot().selectionComplete()).isTrue();
        assertThat(bundle.units()).isNotEmpty();
        assertThat(privateSnapshotStorage.values)
                .containsKey(bundle.snapshot().storageKey());
        assertThat(privateRestGateway.repositoryReads.get()).isEqualTo(1);
        assertThat(privateRestGateway.contentReads.get()).isEqualTo(4);
        assertThat(privateRestGateway.lastConnectionId).isEqualTo(connection.id());
        assertThat(privateRestGateway.lastExternalRepositoryId).isEqualTo(8123L);

        var unit = bundle.units().getFirst();
        var candidate = new GitHubEvidenceCandidate(
                "PROJECT",
                "Private repository project",
                "Private repository evidence.",
                Map.of(),
                new BigDecimal("0.900"),
                List.of(unit.opaqueReference()),
                embedding());
        var validation = sourceWorkflow.validateCandidates(
                userId,
                source.id(),
                sourceService.detail(userId, source.id()).sourceRevision(),
                bundle,
                List.of(candidate));
        var firstApply = sourceWorkflow.applyCandidates(
                userId,
                source.id(),
                bundle,
                validation,
                sourceWorkflow.activeEmbeddingPolicy(),
                Instant.now());
        assertThat(firstApply.result().experienceMatchCounts())
                .containsOnly(Map.entry(ExperienceMatchKind.NEW, 1));
        var repeated = sourceWorkflow.applyCandidates(
                userId,
                source.id(),
                bundle,
                validation,
                sourceWorkflow.activeEmbeddingPolicy(),
                Instant.now());
        assertThat(repeated.result().experienceMatchCounts())
                .containsOnly(Map.entry(ExperienceMatchKind.SAME_EXPERIENCE, 1));
        UUID experienceId = jdbcTemplate.queryForObject(
                "SELECT id FROM experience_items WHERE user_id=?",
                UUID.class,
                userId);
        long experienceVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM experience_items WHERE user_id=? AND id=?",
                Long.class,
                userId,
                experienceId);
        experienceService.verify(
                userId,
                experienceId,
                new ExperienceVerification(
                        EvidenceVerificationStatus.VERIFIED, experienceVersion));

        service.disconnect(userId, connection.id(), connection.version(), true);
        snapshotDeletionWorker.processDue();
        revocationWorker.processDue();
        cleanupWorker.processReady();
        assertThat(privateSnapshotStorage.values).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM github_app_connections WHERE id=?",
                        String.class,
                        connection.id()))
                .isEqualTo("DISCONNECTED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT verification_status FROM experience_items WHERE user_id=? AND id=?",
                        String.class,
                        userId,
                        experienceId))
                .isEqualTo("VERIFIED");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM profile_evidence
                        WHERE user_id=? AND source_type='EXPERIENCE'
                          AND verification_status='VERIFIED'
                        """, Long.class, userId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM profile_evidence
                        WHERE user_id=? AND source_type='GITHUB_REPOSITORY'
                          AND verification_status='SOURCE_DELETED'
                          AND github_snapshot_id IS NULL
                        """, Long.class, userId))
                .isEqualTo(1L);
    }

    private List<Double> embedding() {
        List<Double> values = new ArrayList<>(Collections.nCopies(1536, 0d));
        values.set(0, 1d);
        return List.copyOf(values);
    }

    private void connect(UUID userId, String sessionId, long installationId) {
        String installState = query(service.start(userId, sessionId).installationUrl()).get("state");
        String oauthState = query(service.setupCallback(
                        userId, sessionId, installState, installationId, "install"))
                .get("state");
        service.oauthCallback(userId, sessionId, oauthState, "fixture-code");
    }

    private UUID seedUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,'fixture-hash','GitHub App User','USER','ACTIVE',now(),now(),
                          NULL,NULL,now(),now())
                """, userId, email);
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), userId);
        return userId;
    }

    private void assertInvalidState(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(ErrorCode.GITHUB_CONNECTION_STATE_INVALID_OR_EXPIRED));
    }

    private Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private String pkceChallenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String generatedPrivateKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
            String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
            return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    @TestConfiguration
    static class FakeGatewayConfiguration {
        @Bean
        @Primary
        FakeGitHubAppGateway fakeGitHubAppGateway() {
            return new FakeGitHubAppGateway();
        }

        @Bean
        @Primary
        FakePrivateGitHubRestGateway fakePrivateGitHubRestGateway() {
            return new FakePrivateGitHubRestGateway();
        }

        @Bean
        @Primary
        FakePrivateSnapshotStorage fakePrivateSnapshotStorage() {
            return new FakePrivateSnapshotStorage();
        }

        @Bean
        @Primary
        AgentRunDispatchPort noAutomaticDispatch() {
            return new AgentRunDispatchPort() {
                @Override
                public void enqueue(UUID agentRunId) {}

                @Override
                public void scanQueued() {}
            };
        }
    }

    static final class FakeGitHubAppGateway implements GitHubAppRemoteGateway {
        private String lastChallenge;
        private String lastCodeVerifier;
        private GitHubGatewayException.Kind uninstallFailure;
        private int uninstallAttempts;

        void reset() {
            lastChallenge = null;
            lastCodeVerifier = null;
            uninstallFailure = null;
            uninstallAttempts = 0;
        }

        @Override
        public URI installationUrl(String state) {
            return URI.create("https://github.com/apps/hiresemble-fixture/installations/new?state="
                    + state);
        }

        @Override
        public URI oauthAuthorizationUrl(String state, String codeChallenge) {
            lastChallenge = codeChallenge;
            return URI.create("https://github.com/login/oauth/authorize?client_id=fixture_client_123"
                    + "&state=" + state + "&code_challenge=" + codeChallenge
                    + "&code_challenge_method=S256");
        }

        @Override
        public UserAuthorization exchangeUserCode(String code, String codeVerifier) {
            lastCodeVerifier = codeVerifier;
            return new UserAuthorization("ephemeral-user-fixture");
        }

        @Override
        public VerifiedInstallation verifyUserInstallation(
                String userAccessToken, long installationId) {
            if (installationId != 71001L) {
                throw new GitHubGatewayException(GitHubGatewayException.Kind.NOT_FOUND);
            }
            return verified(installationId);
        }

        @Override
        public VerifiedInstallation inspectInstallation(long installationId) {
            return verified(installationId);
        }

        @Override
        public InstallationToken mintInstallationToken(
                long installationId, Long externalRepositoryId, boolean contentsRead) {
            return new InstallationToken(
                    "ephemeral-installation-fixture", Instant.now().plusSeconds(3600));
        }

        @Override
        public void uninstall(long installationId) {
            uninstallAttempts++;
            if (uninstallFailure != null) {
                throw new GitHubGatewayException(uninstallFailure);
            }
        }

        private VerifiedInstallation verified(long installationId) {
            return new VerifiedInstallation(
                    installationId,
                    801L,
                    "acme",
                    GitHubAccountType.ORGANIZATION,
                    GitHubRepositorySelectionKind.SELECTED,
                    false,
                    List.of(repository(8123L, "private-repo", true),
                            repository(8124L, "public-repo", false)));
        }

        private RepositoryMetadata repository(long id, String name, boolean privateRepository) {
            return new RepositoryMetadata(
                    id,
                    "node-" + id,
                    "acme",
                    name,
                    "https://github.com/acme/" + name,
                    "main",
                    privateRepository,
                    false,
                    false,
                    "Fixture repository",
                    List.of("java"),
                    Instant.parse("2026-08-01T00:00:00Z"),
                    null);
        }
    }

    static final class FakePrivateGitHubRestGateway implements GitHubRestGateway {
        private static final String COMMIT_SHA = "a".repeat(40);
        private static final String TREE_SHA = "b".repeat(40);
        private static final String BLOB_SHA = "c".repeat(40);
        private static final byte[] README = "Private repository evidence."
                .getBytes(StandardCharsets.UTF_8);

        private final AtomicInteger repositoryReads = new AtomicInteger();
        private final AtomicInteger contentReads = new AtomicInteger();
        private UUID lastConnectionId;
        private Long lastExternalRepositoryId;

        private void reset() {
            repositoryReads.set(0);
            contentReads.set(0);
            lastConnectionId = null;
            lastExternalRepositoryId = null;
        }

        @Override
        public AccountDiscovery discoverAccount(String ownerLogin) {
            throw new AssertionError("public gateway path must not be used");
        }

        @Override
        public ConditionalRepository repository(
                String ownerLogin, String repositoryName, String etag) {
            throw new AssertionError("public gateway path must not be used");
        }

        @Override
        public CommitMetadata defaultBranchCommit(
                String ownerLogin, String repositoryName, String defaultBranch) {
            throw new AssertionError("public gateway path must not be used");
        }

        @Override
        public TreeSnapshot tree(String ownerLogin, String repositoryName, String treeSha) {
            throw new AssertionError("public gateway path must not be used");
        }

        @Override
        public Map<String, Long> languages(String ownerLogin, String repositoryName) {
            throw new AssertionError("public gateway path must not be used");
        }

        @Override
        public Blob blob(String ownerLogin, String repositoryName, String blobSha) {
            throw new AssertionError("public gateway path must not be used");
        }

        @Override
        public ConditionalRepository repository(
                GitHubAccessContext access,
                String ownerLogin,
                String repositoryName,
                String etag) {
            record(access, true);
            return new ConditionalRepository(metadata(), false);
        }

        @Override
        public CommitMetadata defaultBranchCommit(
                GitHubAccessContext access,
                String ownerLogin,
                String repositoryName,
                String defaultBranch) {
            record(access, false);
            return new CommitMetadata(COMMIT_SHA, TREE_SHA, "\"commit-fixture\"");
        }

        @Override
        public TreeSnapshot tree(
                GitHubAccessContext access,
                String ownerLogin,
                String repositoryName,
                String treeSha) {
            record(access, false);
            return new TreeSnapshot(
                    TREE_SHA,
                    List.of(new TreeEntry(
                            "README.md", "100644", "blob", README.length, BLOB_SHA)),
                    false,
                    "\"tree-fixture\"");
        }

        @Override
        public Map<String, Long> languages(
                GitHubAccessContext access, String ownerLogin, String repositoryName) {
            record(access, false);
            return Map.of("Java", 42L);
        }

        @Override
        public Blob blob(
                GitHubAccessContext access,
                String ownerLogin,
                String repositoryName,
                String blobSha) {
            record(access, false);
            return new Blob(BLOB_SHA, README);
        }

        private void record(GitHubAccessContext access, boolean repository) {
            assertThat(access.mode()).isEqualTo(GitHubAccessMode.GITHUB_APP);
            lastConnectionId = access.connectionId();
            lastExternalRepositoryId = access.externalRepositoryId();
            if (repository) repositoryReads.incrementAndGet();
            else contentReads.incrementAndGet();
        }

        private RepositoryMetadata metadata() {
            return new RepositoryMetadata(
                    8123L,
                    "node-8123",
                    "acme",
                    "private-repo",
                    "https://github.com/acme/private-repo",
                    "main",
                    true,
                    false,
                    false,
                    "Private fixture",
                    List.of("java"),
                    Instant.parse("2026-08-01T00:00:00Z"),
                    "\"metadata-fixture\"");
        }
    }

    static final class FakePrivateSnapshotStorage implements GitHubSnapshotStoragePort {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public void upload(String storageKey, byte[] gzipJson, String checksumSha256) {
            values.put(storageKey, gzipJson.clone());
        }

        @Override
        public byte[] read(String storageKey) {
            byte[] value = values.get(storageKey);
            if (value == null) throw new IllegalStateException("fixture object missing");
            return value.clone();
        }

        @Override
        public void delete(String storageKey) {
            values.remove(storageKey);
        }
    }
}
