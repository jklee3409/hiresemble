package com.hiresemble.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.api.AgentRunSseService;
import com.hiresemble.agentrun.application.AgentRunStatePort;
import com.hiresemble.agentrun.application.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.BudgetReservationPort;
import com.hiresemble.agentrun.application.ClaimedAgentRun;
import com.hiresemble.agentrun.application.StepCheckpointCommand;
import com.hiresemble.agentrun.application.StepStartCommand;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.AgentStepStatus;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.agentrun.domain.RequiredUserActionType;
import com.hiresemble.agentrun.domain.SafeError;
import com.hiresemble.agentrun.infrastructure.AgentRunEventBus;
import com.hiresemble.auth.domain.UserRole;
import com.hiresemble.auth.domain.UserStatus;
import com.hiresemble.auth.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class AgentRunApiSseIntegrationTest extends AgentRunIntegrationSupport {

    @Autowired MockMvc mockMvc;
    @Autowired AgentRunStatePort statePort;
    @Autowired AgentRunCancellationPort cancellationPort;
    @Autowired AgentStepCheckpointPort stepPort;
    @Autowired BudgetReservationPort budgetPort;
    @Autowired AgentRunSseService sseService;
    @Autowired AgentRunEventBus eventBus;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void listAndDetailAreOwnerScopedAllowlistedAndDoNotExposeRuntimeInternals() throws Exception {
        UUID owner = seedUser("api-owner@example.com");
        UUID other = seedUser("api-other@example.com");
        UUID runId = launch(owner).agentRunId();

        mockMvc.perform(get("/api/v1/agent-runs")
                        .with(authentication(authenticationFor(owner)))
                        .queryParam("workflowType", "JOB_ANALYSIS", "DOCUMENT_INGESTION")
                        .queryParam("status", "QUEUED")
                        .queryParam("retryable", "false")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sort", "updatedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(runId.toString()))
                .andExpect(jsonPath("$.items[0].resourceType").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
        MvcResult detail = mockMvc.perform(get("/api/v1/agent-runs/{id}", runId)
                        .with(authentication(authenticationFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowType").value("JOB_ANALYSIS"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.runAttemptNo").value(1))
                .andExpect(jsonPath("$.steps").isArray())
                .andReturn();
        assertThat(detail.getResponse().getContentAsString())
                .doesNotContain("claimToken", "claimedBy", "leaseExpiresAt", "heartbeatAt")
                .doesNotContain("canonicalInputHash", "inputReferenceSnapshot", "priceVersion")
                .doesNotContain("provider", "model", "prompt", "outputJson");

        mockMvc.perform(get("/api/v1/agent-runs/{id}", runId)
                        .with(authentication(authenticationFor(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/agent-runs")
                        .with(authentication(authenticationFor(owner)))
                        .queryParam("resourceType", "DOCUMENT"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/agent-runs")
                        .with(authentication(authenticationFor(owner)))
                        .queryParam("resourceType", "DOCUMENT")
                        .queryParam("resourceId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs")
                        .with(authentication(authenticationFor(owner)))
                        .queryParam("sort", "actualCostUsd,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/agent-runs")
                        .with(authentication(authenticationFor(owner)))
                        .queryParam("workflowType", "FREE_LOOP"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryAndCancelHttpContractsUseIdempotencyAndStateVersion() throws Exception {
        UUID owner = seedUser("api-mutations@example.com");
        UUID predecessor = failRetryableRun(owner);

        MvcResult first = mockMvc.perform(post("/api/v1/agent-runs/{id}/retry", predecessor)
                        .with(authentication(authenticationFor(owner)))
                        .with(csrf())
                        .header("Idempotency-Key", "http-retry-key-01"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        String successorId = objectMapper.readTree(first.getResponse().getContentAsByteArray())
                .get("agentRunId").asText();
        mockMvc.perform(post("/api/v1/agent-runs/{id}/retry", predecessor)
                        .with(authentication(authenticationFor(owner)))
                        .with(csrf())
                        .header("Idempotency-Key", "http-retry-key-01"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.agentRunId").value(successorId))
                .andExpect(jsonPath("$.replayed").value(true));

        UUID cancelId = launch(owner).agentRunId();
        mockMvc.perform(post("/api/v1/agent-runs/{id}/cancel", cancelId)
                        .with(authentication(authenticationFor(owner)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/agent-runs/{id}/cancel", cancelId)
                        .with(authentication(authenticationFor(owner)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateVersion\":0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellable").value(false));
    }

    @Test
    void sseIsSnapshotFirstOrdersCommittedEventsIgnoresLastEventIdAndClosesAtTerminal()
            throws Exception {
        UUID owner = seedUser("sse-owner@example.com");
        UUID other = seedUser("sse-other@example.com");
        UUID runId = launch(owner).agentRunId();

        mockMvc.perform(get("/api/v1/agent-runs/{id}/events", runId)
                        .with(authentication(authenticationFor(other)))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        MvcResult stream = mockMvc.perform(get("/api/v1/agent-runs/{id}/events", runId)
                        .with(authentication(authenticationFor(owner)))
                        .header("Last-Event-ID", "999")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        sseService.sendTransportHeartbeats();
        ClaimedAgentRun claim = statePort.claim(
                runId, "sse-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        budgetPort.releaseUnused(owner, runId, Instant.now());
        statePort.transition(new AgentRunTransitionCommand(
                owner, runId, claim.claimToken(), claim.run().stateVersion(),
                AgentRunStatus.SUCCEEDED, "APPLY_FIXTURE", 100, ModelTier.LOW_COST,
                BigDecimal.ZERO, false, null, null, null, Instant.now()));

        MvcResult completed = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        String events = completed.getResponse().getContentAsString();
        assertThat(events).contains("event:snapshot", "event:heartbeat", "event:progress", "event:terminal");
        assertThat(events.indexOf("event:snapshot")).isLessThan(events.indexOf("event:progress"));
        assertThat(events.indexOf("event:progress")).isLessThan(events.indexOf("event:terminal"));
        assertThat(events).contains("id:0").doesNotContain("id:999");
        assertThat(eventBus.subscriberCount(runId)).isZero();
    }

    @Test
    void subscriberFailureNeverChangesDurableRunState() {
        UUID owner = seedUser("sse-failure@example.com");
        UUID runId = launch(owner).agentRunId();
        AgentRunEventBus.Subscription failing = eventBus.subscribe(runId, ignored -> {
            throw new IllegalStateException("injected transport failure");
        });
        try {
            assertThat(statePort.claim(
                    runId, "durable-worker", Instant.now(), Duration.ofSeconds(60))).isPresent();
            assertThat(run(owner, runId).status()).isEqualTo(AgentRunStatus.RUNNING);
        } finally {
            failing.close();
        }
    }

    @Test
    void stepAndWaitingUserEventsCarrySafeProjectionsBeforeTerminalCleanup() throws Exception {
        UUID owner = seedUser("sse-step-waiting@example.com");
        UUID runId = launch(owner).agentRunId();
        MvcResult stream = mockMvc.perform(get("/api/v1/agent-runs/{id}/events", runId)
                        .with(authentication(authenticationFor(owner)))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        ClaimedAgentRun claim = statePort.claim(
                runId, "waiting-sse-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        long modelPolicy = seedModelPolicy();
        var step = stepPort.start(new StepStartCommand(
                owner, runId, claim.claimToken(), "TRANSFORM_FIXTURE", null, 2,
                "FixtureAgent", 1, 3, "b".repeat(64),
                objectMapper.createObjectNode().put("fixtureRef", "safe-fixture"),
                "fixture-output-v1", modelPolicy, "fixture-prompt-v1",
                AiQualityMode.ECONOMY, Instant.now()));
        stepPort.checkpoint(new StepCheckpointCommand(
                owner, runId, step.id(), claim.claimToken(), AgentStepStatus.WAITING_USER,
                null, null, null, null, null, Instant.now()));
        budgetPort.releaseUnused(owner, runId, Instant.now());
        AgentRunSnapshot waiting = statePort.transition(new AgentRunTransitionCommand(
                owner, runId, claim.claimToken(), run(owner, runId).stateVersion(),
                AgentRunStatus.WAITING_USER, "TRANSFORM_FIXTURE", 40, null,
                BigDecimal.ZERO, false,
                new RequiredUserAction(
                        RequiredUserActionType.PROVIDE_JOB_TEXT, null,
                        "/agent-runs/" + runId, "Provide the required text to continue."),
                null, null, Instant.now()));
        cancellationPort.requestCancellation(owner, runId, waiting.stateVersion(), Instant.now());

        String events = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(events).contains("event:step", "event:waiting_user", "event:terminal");
        assertThat(events.indexOf("event:step")).isLessThan(events.indexOf("event:waiting_user"));
        assertThat(events).contains("PROVIDE_JOB_TEXT", "/agent-runs/" + runId);
        assertThat(events).doesNotContain("inputHash", "outputJson", "reusedStepId", "promptVersion");
        assertThat(eventBus.subscriberCount(runId)).isZero();
    }

    @Test
    void reconnectStartsFromFreshDatabaseSnapshotAndDisconnectCleansOldSubscriber() throws Exception {
        UUID owner = seedUser("sse-reconnect@example.com");
        UUID runId = launch(owner).agentRunId();
        MvcResult first = mockMvc.perform(get("/api/v1/agent-runs/{id}/events", runId)
                        .with(authentication(authenticationFor(owner)))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        first.getRequest().getAsyncContext().complete();
        assertThat(eventBus.subscriberCount(runId)).isZero();

        ClaimedAgentRun claim = statePort.claim(
                runId, "reconnect-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        var progressed = statePort.updateProgress(
                owner, runId, claim.claimToken(), claim.run().stateVersion(),
                "TRANSFORM_FIXTURE", 30, Instant.now());
        MvcResult second = mockMvc.perform(get("/api/v1/agent-runs/{id}/events", runId)
                        .with(authentication(authenticationFor(owner)))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        var requested = cancellationPort.requestCancellation(
                owner, runId, progressed.stateVersion(), Instant.now());
        assertThat(requested.status()).isEqualTo(AgentRunStatus.RUNNING);
        cancellationPort.completeCancellation(owner, runId, claim.claimToken(), Instant.now());

        String events = mockMvc.perform(asyncDispatch(second))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(events.indexOf("event:snapshot")).isLessThan(events.indexOf("event:progress"));
        assertThat(events).contains("\"progressPercent\":30", "\"stateVersion\":2");
        assertThat(eventBus.subscriberCount(runId)).isZero();
    }

    @Test
    void stateEventsPublishOnlyAfterTransactionCommitAndRollbackPublishesNothing() {
        UUID owner = seedUser("sse-commit@example.com");
        UUID runId = launch(owner).agentRunId();
        List<Long> versions = new CopyOnWriteArrayList<>();
        AgentRunEventBus.Subscription subscription = eventBus.subscribe(
                runId, event -> versions.add(event.run().stateVersion()));
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(statePort.claim(
                        runId, "rollback-worker", Instant.now(), Duration.ofSeconds(60))).isPresent();
                assertThat(versions).isEmpty();
                status.setRollbackOnly();
            });
            assertThat(versions).isEmpty();
            assertThat(run(owner, runId).status()).isEqualTo(AgentRunStatus.QUEUED);

            assertThat(statePort.claim(
                    runId, "commit-worker", Instant.now(), Duration.ofSeconds(60))).isPresent();
            assertThat(versions).containsExactly(1L);
        } finally {
            subscription.close();
        }
    }

    private UUID failRetryableRun(UUID userId) {
        UUID runId = launch(userId).agentRunId();
        ClaimedAgentRun claim = statePort.claim(
                runId, "api-failure-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        budgetPort.releaseUnused(userId, runId, Instant.now());
        statePort.transition(new AgentRunTransitionCommand(
                userId, runId, claim.claimToken(), claim.run().stateVersion(),
                AgentRunStatus.FAILED, "TRANSFORM_FIXTURE", 50, ModelTier.LOW_COST,
                BigDecimal.ZERO, true, null,
                new SafeError("FIXTURE_TRANSIENT_FAILURE", "The fixture run could not finish."),
                null, Instant.now()));
        return runId;
    }

    private UsernamePasswordAuthenticationToken authenticationFor(UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user-" + userId + "@example.com", "Agent User",
                UserRole.USER, UserStatus.ACTIVE);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
