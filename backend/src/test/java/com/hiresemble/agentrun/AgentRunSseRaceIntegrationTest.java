package com.hiresemble.agentrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.AgentRunApplicationService;
import com.hiresemble.agentrun.application.AgentRunStatePort;
import com.hiresemble.agentrun.application.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.BudgetReservationPort;
import com.hiresemble.agentrun.application.ClaimedAgentRun;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.auth.domain.UserRole;
import com.hiresemble.auth.domain.UserStatus;
import com.hiresemble.auth.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AgentRunSseRaceIntegrationTest extends AgentRunIntegrationSupport {

    @Autowired MockMvc mockMvc;
    @Autowired AgentRunStatePort statePort;
    @Autowired BudgetReservationPort budgetPort;
    @MockitoSpyBean AgentRunApplicationService applicationService;

    @Test
    void eventCommittedAfterSnapshotReadButBeforeInitializationIsBufferedAfterSnapshot()
            throws Exception {
        UUID owner = seedUser("sse-race@example.com");
        UUID runId = launch(owner).agentRunId();
        CountDownLatch staleSnapshotRead = new CountDownLatch(1);
        CountDownLatch returnSnapshot = new CountDownLatch(1);
        AtomicInteger detailCalls = new AtomicInteger();
        doAnswer(invocation -> {
            Object snapshot = invocation.callRealMethod();
            if (detailCalls.incrementAndGet() == 2) {
                staleSnapshotRead.countDown();
                if (!returnSnapshot.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to return the captured snapshot");
                }
            }
            return snapshot;
        }).when(applicationService).detail(owner, runId);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var opening = executor.submit(() -> mockMvc.perform(
                            get("/api/v1/agent-runs/{id}/events", runId)
                                    .with(authentication(authenticationFor(owner)))
                                    .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted())
                    .andReturn());
            assertThat(staleSnapshotRead.await(2, TimeUnit.SECONDS)).isTrue();
            ClaimedAgentRun claim = statePort.claim(
                    runId, "race-worker", Instant.now(), Duration.ofSeconds(60)).orElseThrow();
            returnSnapshot.countDown();
            MvcResult stream = opening.get(2, TimeUnit.SECONDS);

            budgetPort.releaseUnused(owner, runId, Instant.now());
            statePort.transition(new AgentRunTransitionCommand(
                    owner, runId, claim.claimToken(), claim.run().stateVersion(),
                    AgentRunStatus.SUCCEEDED, "APPLY_FIXTURE", 100, null,
                    BigDecimal.ZERO, false, null, null, null, Instant.now()));
            String events = mockMvc.perform(asyncDispatch(stream))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(events.indexOf("event:snapshot")).isLessThan(events.indexOf("event:progress"));
            assertThat(events.indexOf("event:progress")).isLessThan(events.indexOf("event:terminal"));
            assertThat(count(events, "event:snapshot")).isEqualTo(1);
            assertThat(count(events, "event:progress")).isEqualTo(1);
            assertThat(events).contains("id:0", "id:1", "id:2");
        } finally {
            returnSnapshot.countDown();
        }
    }

    private int count(String text, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private UsernamePasswordAuthenticationToken authenticationFor(UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user-" + userId + "@example.com", "Agent User",
                UserRole.USER, UserStatus.ACTIVE);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
