package com.hiresemble.githubsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.githubsource.application.GitHubGatewayModels.AccountDiscovery;
import com.hiresemble.githubsource.application.GitHubGatewayModels.Blob;
import com.hiresemble.githubsource.application.GitHubGatewayModels.CommitMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.ConditionalRepository;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeSnapshot;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubRestGateway;
import com.hiresemble.githubsource.application.GitHubSnapshotStoragePort;
import com.hiresemble.githubsource.application.GitHubSourceWorkflowService;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxWorker;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(GitHubSourceApiIntegrationTest.FakePorts.class)
@TestPropertySource(properties = "hiresemble.github.enabled=true")
class GitHubSourceApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GitHubSourceWorkflowService workflow;
    @Autowired private AgentRunStatePort runState;
    @Autowired private AgentRunQueryPort runQuery;
    @Autowired private BudgetReservationPort budget;
    @Autowired private AgentRunCancellationPort cancellation;
    @Autowired private GitHubSnapshotDeletionOutboxWorker deletionWorker;
    @Autowired private FakeGateway gateway;
    @Autowired private FakeStorage storage;

    @DynamicPropertySource
    static void slowBackgroundWorkers(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.github.deletion-scan-interval", () -> "1h");
    }

    @BeforeEach
    void resetFakes() {
        gateway.reset();
        storage.values.clear();
    }

    @Test
    void registrationListingDetailAndOwnerBoundariesAreSessionAndIdempotencySafe()
            throws Exception {
        Session owner = authenticated("github-api-owner@example.com");
        Session other = authenticated("github-api-other@example.com");

        JsonNode accepted = create(
                owner,
                "https://www.github.com/octo/direct.git",
                "github-api-create-0001",
                202);
        UUID sourceId = UUID.fromString(accepted.get("resourceId").asText());
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());
        assertThat(accepted.get("resourceType").asText()).isEqualTo("GITHUB_SOURCE");
        assertThat(accepted.get("status").asText()).isEqualTo("QUEUED");
        assertThat(accepted.get("replayed").asBoolean()).isFalse();

        JsonNode replay = create(
                owner,
                "https://www.github.com/octo/direct.git",
                "github-api-create-0001",
                202);
        assertThat(replay.get("resourceId").asText()).isEqualTo(sourceId.toString());
        assertThat(replay.get("agentRunId").asText()).isEqualTo(runId.toString());
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        assertThat(create(
                                owner,
                                "https://github.com/octo/changed",
                                "github-api-create-0001",
                                409)
                        .get("code")
                        .asText())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(create(
                                owner,
                                "https://github.com/octo/direct",
                                "github-api-create-0002",
                                409)
                        .get("code")
                        .asText())
                .isEqualTo("GITHUB_SOURCE_ALREADY_EXISTS");

        mockMvc.perform(get("/api/v1/github-sources")
                        .cookie(owner.cookie())
                        .queryParam("sourceKind", "REPOSITORY")
                        .queryParam("sort", "updatedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(sourceId.toString()))
                .andExpect(jsonPath("$.items[0].canonicalUrl")
                        .value("https://github.com/octo/direct"));
        MvcResult detail = mockMvc.perform(get("/api/v1/github-sources/{id}", sourceId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.id").value(sourceId.toString()))
                .andReturn();
        assertNoInternalGitHubValues(detail);

        mockMvc.perform(get("/api/v1/github-sources/{id}", sourceId).cookie(other.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/github-sources/{id}/repositories", sourceId)
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs/{id}", runId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("GITHUB_SOURCE"))
                .andExpect(jsonPath("$.resourceId").value(sourceId.toString()));
        mockMvc.perform(get("/api/v1/agent-runs")
                        .cookie(owner.cookie())
                        .queryParam("resourceType", "GITHUB_SOURCE")
                        .queryParam("resourceId", sourceId.toString())
                        .queryParam("sort", "queuedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/github-sources")
                        .cookie(owner.cookie())
                        .header("Idempotency-Key", "github-api-csrf-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("https://github.com/octo/csrf")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/github-sources")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "github-api-invalid-url-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("https://github.com/octo/direct/issues")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GITHUB_URL_INVALID"));
    }

    @Test
    void repositorySelectionRejectsForeignIdsAndResumesTheSameWaitingRunOnce()
            throws Exception {
        Session owner = authenticated("github-selection-owner@example.com");
        Session other = authenticated("github-selection-other@example.com");
        JsonNode ownerAccepted = create(
                owner, "https://github.com/octo", "github-selection-create-0001", 202);
        JsonNode otherAccepted = create(
                other, "https://github.com/other", "github-selection-create-0002", 202);
        UUID sourceId = UUID.fromString(ownerAccepted.get("resourceId").asText());
        UUID runId = UUID.fromString(ownerAccepted.get("agentRunId").asText());
        UUID otherSourceId = UUID.fromString(otherAccepted.get("resourceId").asText());
        moveAccountToWaiting(owner.userId(), sourceId, runId);
        moveAccountToWaiting(
                other.userId(),
                otherSourceId,
                UUID.fromString(otherAccepted.get("agentRunId").asText()));

        MvcResult repositoryResult = mockMvc.perform(
                        get("/api/v1/github-sources/{id}/repositories", sourceId)
                                .cookie(owner.cookie())
                                .queryParam("sort", "repositoryName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode repositories = json(repositoryResult).get("items");
        UUID first = UUID.fromString(repositories.get(0).get("id").asText());
        UUID second = UUID.fromString(repositories.get(1).get("id").asText());
        assertNoInternalGitHubValues(repositoryResult);
        UUID foreign = UUID.fromString(json(mockMvc.perform(
                                get("/api/v1/github-sources/{id}/repositories", otherSourceId)
                                        .cookie(other.cookie())
                                        .queryParam("sort", "repositoryName,asc"))
                        .andExpect(status().isOk())
                        .andReturn())
                .at("/items/0/id")
                .asText());
        long version = json(mockMvc.perform(get("/api/v1/github-sources/{id}", sourceId)
                                .cookie(owner.cookie()))
                        .andReturn())
                .at("/source/version")
                .asLong();

        select(owner, sourceId, List.of(foreign), version, "github-selection-foreign-0001", 404);
        select(owner, sourceId, List.of(first), version + 1, "github-selection-version-0001", 409);
        JsonNode selected = select(
                owner,
                sourceId,
                List.of(first, second),
                version,
                "github-selection-apply-0001",
                202);
        assertThat(selected.get("agentRunId").asText()).isEqualTo(runId.toString());
        assertThat(selected.get("status").asText()).isEqualTo("QUEUED");
        assertThat(selected.get("replayed").asBoolean()).isFalse();
        JsonNode replay = select(
                owner,
                sourceId,
                List.of(first, second),
                version,
                "github-selection-apply-0001",
                202);
        assertThat(replay.get("agentRunId").asText()).isEqualTo(runId.toString());
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        select(
                owner,
                sourceId,
                List.of(first),
                version,
                "github-selection-apply-0001",
                409);

        mockMvc.perform(get("/api/v1/github-sources/{id}", sourceId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.status").value("QUEUED"))
                .andExpect(jsonPath("$.source.selectedRepositoryCount").value(2))
                .andExpect(jsonPath("$.requiredUserAction").doesNotExist());
        assertThat(runQuery.findByOwner(owner.userId(), runId).orElseThrow().status())
                .isEqualTo(AgentRunStatus.QUEUED);
    }

    @Test
    void refreshDistinguishesUnchangedAndChangedThenDeleteUsesSnapshotOutboxAndKeepsRunHistory()
            throws Exception {
        Session owner = authenticated("github-refresh-owner@example.com");
        JsonNode accepted = create(
                owner,
                "https://github.com/octo/refreshable",
                "github-refresh-create-0001",
                202);
        UUID sourceId = UUID.fromString(accepted.get("resourceId").asText());
        UUID originalRunId = UUID.fromString(accepted.get("agentRunId").asText());
        Source ready = moveRepositoryToReady(owner.userId(), sourceId, originalRunId);
        assertThat(storage.values).hasSize(1);

        refresh(owner, sourceId, ready.version() + 1, "github-refresh-version-0001", 409);
        JsonNode unchanged = refresh(
                owner, sourceId, ready.version(), "github-refresh-unchanged-0001", 200);
        assertThat(unchanged.get("changed").asBoolean()).isFalse();
        assertThat(unchanged.get("run").isNull()).isTrue();
        int callsAfterUnchanged = gateway.repositoryCalls.get();
        JsonNode unchangedReplay = refresh(
                owner, sourceId, ready.version(), "github-refresh-unchanged-0001", 200);
        assertThat(unchangedReplay).isEqualTo(unchanged);
        assertThat(gateway.repositoryCalls).hasValue(callsAfterUnchanged);

        gateway.notModified = false;
        gateway.commitSha = "d".repeat(40);
        JsonNode changed = refresh(
                owner, sourceId, ready.version(), "github-refresh-changed-0001", 202);
        assertThat(changed.get("changed").asBoolean()).isTrue();
        assertThat(changed.at("/run/status").asText()).isEqualTo("QUEUED");
        UUID changedRunId = UUID.fromString(changed.at("/run/agentRunId").asText());
        JsonNode changedReplay = refresh(
                owner, sourceId, ready.version(), "github-refresh-changed-0001", 202);
        assertThat(changedReplay.at("/run/agentRunId").asText())
                .isEqualTo(changedRunId.toString());
        assertThat(changedReplay.at("/run/replayed").asBoolean()).isTrue();
        refresh(owner, sourceId, ready.version() + 1, "github-refresh-changed-0001", 409);

        MvcResult stream = mockMvc.perform(get("/api/v1/agent-runs/{id}/events", changedRunId)
                        .cookie(owner.cookie())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        AgentRunSnapshot queued = runQuery.findByOwner(owner.userId(), changedRunId).orElseThrow();
        cancellation.requestCancellation(
                owner.userId(), changedRunId, queued.stateVersion(), Instant.now());
        String events = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(events)
                .contains("event:snapshot", "event:terminal", "GITHUB_SOURCE", sourceId.toString())
                .doesNotContain("storageKey", "commitSha", "sourceUnit");

        Source failed = source(owner.userId(), sourceId);
        mockMvc.perform(delete("/api/v1/github-sources/{id}", sourceId)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .queryParam("version", Long.toString(failed.version() + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));
        mockMvc.perform(delete("/api/v1/github-sources/{id}", sourceId)
                        .cookie(owner.cookie())
                        .queryParam("version", Long.toString(failed.version())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/github-sources/{id}", sourceId)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .queryParam("version", Long.toString(failed.version())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/github-sources/{id}", sourceId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs/{id}", originalRunId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(sourceId.toString()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM github_snapshot_object_deletion_outbox WHERE user_id=? AND github_source_id=? AND status='PENDING'",
                Long.class,
                owner.userId(),
                sourceId)).isEqualTo(1L);
        assertThat(storage.values).hasSize(1);

        deletionWorker.processDue();

        assertThat(storage.values).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM github_snapshot_object_deletion_outbox WHERE user_id=? AND github_source_id=?",
                String.class,
                owner.userId(),
                sourceId)).isEqualTo("SUCCEEDED");
    }

    @Test
    void refreshMapsBoundedGatewayFailuresToSafePublicErrors() throws Exception {
        Session owner = authenticated("github-errors-owner@example.com");
        JsonNode accepted = create(
                owner,
                "https://github.com/octo/error-source",
                "github-errors-create-0001",
                202);
        UUID sourceId = UUID.fromString(accepted.get("resourceId").asText());
        Source ready = moveRepositoryToReady(
                owner.userId(),
                sourceId,
                UUID.fromString(accepted.get("agentRunId").asText()));

        gateway.failureKind = GitHubGatewayException.Kind.NOT_FOUND;
        refresh(owner, sourceId, ready.version(), "github-errors-not-found-0001", 422);
        assertThat(lastErrorCode).isEqualTo("GITHUB_SOURCE_NOT_ACCESSIBLE");

        gateway.failureKind = GitHubGatewayException.Kind.RESPONSE_LIMIT;
        refresh(owner, sourceId, ready.version(), "github-errors-limit-0001", 422);
        assertThat(lastErrorCode).isEqualTo("GITHUB_SOURCE_LIMIT_EXCEEDED");

        gateway.failureKind = GitHubGatewayException.Kind.UPSTREAM_5XX;
        refresh(owner, sourceId, ready.version(), "github-errors-upstream-0001", 503);
        assertThat(lastErrorCode).isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");

        gateway.failureKind = GitHubGatewayException.Kind.RATE_LIMITED;
        MvcResult rateLimited = refreshResult(
                owner, sourceId, ready.version(), "github-errors-rate-0001", 429);
        assertThat(json(rateLimited).get("code").asText()).isEqualTo("GITHUB_RATE_LIMITED");
        assertThat(rateLimited.getResponse().getHeader("Retry-After")).isEqualTo("30");
        assertNoInternalGitHubValues(rateLimited);
    }

    private String lastErrorCode;

    private void moveAccountToWaiting(UUID userId, UUID sourceId, UUID runId) {
        var claimed = runState.claim(runId, "github-api-wait", Instant.now(), java.time.Duration.ofMinutes(1))
                .orElseThrow();
        workflow.begin(userId, sourceId, runId, Instant.now());
        workflow.discover(userId, sourceId, runId, Instant.now());
        budget.releaseUnused(userId, runId, Instant.now());
        AgentRunSnapshot released = runQuery.findByOwner(userId, runId).orElseThrow();
        runState.transition(new AgentRunTransitionCommand(
                userId,
                runId,
                claimed.claimToken(),
                released.stateVersion(),
                AgentRunStatus.WAITING_USER,
                "WAIT_FOR_REPOSITORY_SELECTION",
                20,
                null,
                BigDecimal.ZERO,
                false,
                new RequiredUserAction(
                        RequiredUserActionType.SELECT_GITHUB_REPOSITORIES,
                        new ResourceReference("GITHUB_SOURCE", sourceId, null),
                        "/profile/github",
                        "Select public repositories to continue."),
                null,
                null,
                Instant.now()));
    }

    private Source moveRepositoryToReady(UUID userId, UUID sourceId, UUID runId) {
        workflow.begin(userId, sourceId, runId, Instant.now());
        workflow.discover(userId, sourceId, runId, Instant.now());
        var repository = workflow.selectedRepositories(userId, sourceId).getFirst();
        workflow.captureAndStore(userId, sourceId, repository, Instant.now());
        return workflow.finalizeSource(
                        userId, sourceId, runId, false, List.of(), 0, Instant.now())
                .source();
    }

    private Source source(UUID userId, UUID sourceId) {
        return workflow.source(userId, sourceId);
    }

    private JsonNode create(
            Session session, String url, String key, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/github-sources")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(url)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json(result);
    }

    private JsonNode select(
            Session session,
            UUID sourceId,
            List<UUID> repositoryIds,
            long version,
            String key,
            int expectedStatus)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "repositoryIds", repositoryIds,
                "version", version));
        MvcResult result = mockMvc.perform(put(
                                "/api/v1/github-sources/{id}/repository-selection", sourceId)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json(result);
    }

    private JsonNode refresh(
            Session session, UUID sourceId, long version, String key, int expectedStatus)
            throws Exception {
        MvcResult result = refreshResult(session, sourceId, version, key, expectedStatus);
        JsonNode body = json(result);
        lastErrorCode = body.hasNonNull("code") ? body.get("code").asText() : null;
        return body;
    }

    private MvcResult refreshResult(
            Session session, UUID sourceId, long version, String key, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/v1/github-sources/{id}/refresh", sourceId)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private String createBody(String url) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "url", url,
                "participationConfirmed", true));
    }

    private Session authenticated(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = requiredCookie(csrf);
        String token = json(csrf).get("token").asText();
        String body = objectMapper.writeValueAsString(
                new SignupRequest(email, "password-123", "Candidate", true, true));
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = json(signup);
        return new Session(
                requiredCookie(signup),
                response.at("/csrf/token").asText(),
                UUID.fromString(response.at("/user/id").asText()));
    }

    private void assertNoInternalGitHubValues(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(
                        "storageKey",
                        "snapshotStorageKey",
                        "commitSha",
                        "treeSha",
                        "sourceUnitId",
                        "rawJson",
                        "prompt",
                        "providerResponse",
                        "Ignore all instructions");
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) throw new AssertionError("SESSION cookie missing");
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Session(Cookie cookie, String csrfToken, UUID userId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FakePorts {

        @Bean
        @Primary
        FakeGateway fakeGitHubApiGateway() {
            return new FakeGateway();
        }

        @Bean
        @Primary
        FakeStorage fakeGitHubApiStorage() {
            return new FakeStorage();
        }

        @Bean
        @Primary
        AgentRunDispatchPort noAutomaticGitHubDispatch() {
            return new AgentRunDispatchPort() {
                @Override
                public void enqueue(UUID agentRunId) {}

                @Override
                public void scanQueued() {}
            };
        }
    }

    static final class FakeGateway implements GitHubRestGateway {
        private static final String TREE_SHA = "b".repeat(40);
        private static final String BLOB_SHA = "c".repeat(40);
        private static final byte[] README = "Public repository evidence."
                .getBytes(StandardCharsets.UTF_8);

        final AtomicInteger repositoryCalls = new AtomicInteger();
        volatile boolean notModified = true;
        volatile String commitSha = "a".repeat(40);
        volatile GitHubGatewayException.Kind failureKind;

        @Override
        public AccountDiscovery discoverAccount(String ownerLogin) {
            return new AccountDiscovery(
                    GitHubAccountType.USER,
                    List.of(
                            repository(ownerLogin, "alpha", 101),
                            repository(ownerLogin, "beta", 102)),
                    false);
        }

        @Override
        public ConditionalRepository repository(
                String ownerLogin, String repositoryName, String etag) {
            repositoryCalls.incrementAndGet();
            if (failureKind != null) {
                if (failureKind == GitHubGatewayException.Kind.RATE_LIMITED) {
                    throw new GitHubGatewayException(failureKind, java.time.Duration.ofSeconds(30));
                }
                throw new GitHubGatewayException(failureKind);
            }
            return new ConditionalRepository(
                    repository(ownerLogin, repositoryName, 200 + repositoryName.hashCode()),
                    etag != null && notModified);
        }

        @Override
        public CommitMetadata defaultBranchCommit(
                String ownerLogin, String repositoryName, String defaultBranch) {
            return new CommitMetadata(commitSha, TREE_SHA, "\"commit-etag\"");
        }

        @Override
        public TreeSnapshot tree(String ownerLogin, String repositoryName, String treeSha) {
            return new TreeSnapshot(
                    TREE_SHA,
                    List.of(new TreeEntry(
                            "README.md", "100644", "blob", README.length, BLOB_SHA)),
                    false,
                    "\"tree-etag\"");
        }

        @Override
        public Map<String, Long> languages(String ownerLogin, String repositoryName) {
            return Map.of("Java", 100L);
        }

        @Override
        public Blob blob(String ownerLogin, String repositoryName, String blobSha) {
            return new Blob(BLOB_SHA, README);
        }

        void reset() {
            repositoryCalls.set(0);
            notModified = true;
            commitSha = "a".repeat(40);
            failureKind = null;
        }

        private RepositoryMetadata repository(String owner, String name, long id) {
            return new RepositoryMetadata(
                    Math.abs(id),
                    "R_" + Math.abs(id),
                    owner,
                    name,
                    "https://github.com/" + owner + "/" + name,
                    "main",
                    false,
                    false,
                    false,
                    "Public repository",
                    List.of("java"),
                    Instant.parse("2026-08-01T00:00:00Z"),
                    "\"repo-etag\"");
        }
    }

    static final class FakeStorage implements GitHubSnapshotStoragePort {
        final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public void upload(String storageKey, byte[] gzipJson, String checksumSha256) {
            values.put(storageKey, gzipJson.clone());
        }

        @Override
        public byte[] read(String storageKey) {
            byte[] value = values.get(storageKey);
            if (value == null) throw new IllegalStateException("snapshot missing");
            return value.clone();
        }

        @Override
        public void delete(String storageKey) {
            values.remove(storageKey);
        }
    }
}
