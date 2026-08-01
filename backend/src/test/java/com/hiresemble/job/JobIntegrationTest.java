package com.hiresemble.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.application.JobDeadlineScheduler;
import com.hiresemble.job.application.JobExtractionMutationService;
import com.hiresemble.job.application.JobStatusService;
import com.hiresemble.job.domain.JobCommands.ExtractedFields;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.job.infrastructure.JobStore;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(JobIntegrationTest.TestPorts.class)
class JobIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobExtractionMutationService extraction;
    @Autowired private JobDeadlineScheduler scheduler;
    @Autowired private JobStatusService statusService;
    @Autowired private JobStore store;
    @Autowired private BudgetReservationPort budgetReservations;

    @DynamicPropertySource
    static void backgroundWorkersStayDeterministic(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.job-deadline-scheduler.cron", () -> "0 0 0 1 1 *");
        registry.add("hiresemble.job-deadline-scheduler.batch-size", () -> "2");
    }

    @Test
    void manualCreateIs201IdempotentCanonicalDuplicateSafeAndReusableAfterDelete()
            throws Exception {
        Session owner = authenticated("job-manual-owner@example.com");
        String body = createBody(
                "HTTPS://EXAMPLE.COM:443/a/../jobs//42?utm_source=mail&b=2&a=1#apply",
                "ACME / Research",
                "Platform / Backend",
                "Build reliable Spring services with a strong engineering team.",
                NOW.plusSeconds(86_400));

        MvcResult first = create(owner, "manual-job-key-0001", body, 201);
        JsonNode accepted = json(first);
        UUID jobId = UUID.fromString(accepted.get("jobId").asText());
        assertThat(accepted.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(accepted.get("extractionStatus").asText())
                .isEqualTo("MANUAL_INPUT_PROVIDED");
        assertThat(accepted.get("agentRunId").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=?",
                Long.class,
                owner.userId())).isZero();

        MvcResult replay = create(owner, "manual-job-key-0001", body, 201);
        assertThat(json(replay).get("jobId").asText()).isEqualTo(jobId.toString());
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");

        String changed = createBody(
                "https://example.com/jobs/42?a=1&b=2",
                "Different Company",
                null,
                "A different manual body that must conflict with the reused key.",
                null);
        create(owner, "manual-job-key-0001", changed, 409);

        MvcResult duplicate = create(
                owner,
                "manual-job-key-0002",
                createBody(
                        "https://example.com/jobs/42?b=2&a=1&fbclid=tracking",
                        null,
                        null,
                        "Duplicate canonical URL body.",
                        null),
                409);
        assertThat(json(duplicate).get("code").asText()).isEqualTo("DUPLICATE_JOB_URL");

        mockMvc.perform(delete("/api/v1/jobs/" + jobId)
                        .queryParam("version", "0")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());

        MvcResult replacement = create(
                owner,
                "manual-job-key-0002",
                createBody(
                        "https://example.com/jobs/42?a=1&b=2",
                        null,
                        null,
                        "The failed reservation was abandoned and can now succeed.",
                        null),
                201);
        assertThat(json(replacement).get("jobId").asText()).isNotEqualTo(jobId.toString());
    }

    @Test
    void urlOnlyCreateIs202WithOwnerScopedTypedRunAndExtractionApplyHonorsOverrides()
            throws Exception {
        Session owner = authenticated("job-auto-owner@example.com");
        Session other = authenticated("job-auto-other@example.com");
        Instant userDeadline = NOW.plusSeconds(172_800);
        JsonNode accepted = json(create(
                owner,
                "automatic-job-key-01",
                createBody(
                        "https://jobs.example.com/openings/7",
                        "User Company",
                        "User Position",
                        null,
                        userDeadline),
                202));
        UUID jobId = UUID.fromString(accepted.get("jobId").asText());
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());
        assertThat(accepted.get("extractionStatus").asText()).isEqualTo("QUEUED");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM agent_run_resource_links
                WHERE user_id=? AND agent_run_id=? AND job_posting_id=?
                  AND document_id IS NULL AND resource_kind='JOB' AND primary_resource
                """,
                Long.class,
                owner.userId(),
                runId,
                jobId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT resource_type || ':' || resource_id FROM agent_runs WHERE id=?",
                String.class,
                runId)).isEqualTo("JOB:" + jobId);

        mockMvc.perform(get("/api/v1/agent-runs")
                        .cookie(owner.cookie())
                        .queryParam("resourceType", "JOB")
                        .queryParam("resourceId", jobId.toString())
                        .queryParam("sort", "queuedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(runId.toString()));
        mockMvc.perform(get("/api/v1/agent-runs/" + runId).cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs")
                        .cookie(other.cookie())
                        .queryParam("resourceType", "JOB")
                        .queryParam("resourceId", jobId.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(other.cookie()))
                .andExpect(status().isNotFound());

        var snapshot = extraction.snapshot(owner.userId(), jobId);
        assertThat(snapshot.latestAgentRunId()).isEqualTo(runId);
        assertThat(snapshot.userOverrides().companyName()).isEqualTo("User Company");
        assertThat(snapshot.userOverrides().positionName()).isEqualTo("User Position");
        assertThat(snapshot.userOverrides().deadlineAt()).isEqualTo(userDeadline);
        var extracting = extraction.markExtracting(
                owner.userId(), jobId, runId, snapshot.version());
        var applied = extraction.applyExtraction(
                owner.userId(),
                jobId,
                runId,
                extracting.version(),
                new ExtractedFields(
                        "AI Company",
                        "Extracted Posting Title",
                        "AI Position",
                        "This extracted description is sufficiently useful for the candidate.",
                        NOW.plusSeconds(259_200),
                        new BigDecimal("0.875"),
                        "SOFTWARE_ENGINEERING",
                        "FULL_TIME",
                        "Seoul"));
        assertThat(applied.companyName()).isEqualTo("User Company");
        assertThat(applied.positionName()).isEqualTo("User Position");
        assertThat(applied.deadlineAt()).isEqualTo(userDeadline);
        assertThat(applied.title()).isEqualTo("Extracted Posting Title");
        assertThat(applied.descriptionText()).contains("sufficiently useful");
        assertThat(applied.extractionStatus()).isEqualTo(JobExtractionStatus.EXTRACTED);
        assertThat(applied.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(applied.contentHash()).hasSize(64);

        mockMvc.perform(delete("/api/v1/jobs/" + jobId)
                        .queryParam("version", Long.toString(applied.version()))
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancel_requested_at IS NOT NULL FROM agent_runs WHERE id=?",
                Boolean.class,
                runId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_run_resource_links WHERE agent_run_id=?",
                Long.class,
                runId)).isEqualTo(1L);
    }

    @Test
    void waitingUserManualUpdateResumesSameRunAndTerminalRetryCreatesOneSuccessor()
            throws Exception {
        Session owner = authenticated("job-resume-owner@example.com");
        JsonNode waitingAccepted = json(create(
                owner,
                "waiting-job-key-0001",
                createBody("https://jobs.example.com/waiting", null, null, null, null),
                202));
        UUID waitingJob = UUID.fromString(waitingAccepted.get("jobId").asText());
        UUID waitingRun = UUID.fromString(waitingAccepted.get("agentRunId").asText());
        var waitingJobState = extraction.markNeedsManualInput(
                owner.userId(), waitingJob, waitingRun, 0);
        budgetReservations.releaseUnused(owner.userId(), waitingRun, NOW);
        jdbcTemplate.update(
                """
                UPDATE agent_runs SET status='WAITING_USER',
                    waiting_action_type='PROVIDE_JOB_TEXT',
                    waiting_action_route=?,
                    waiting_action_message='Provide the posting text.',
                    state_version=state_version+1,updated_at=?
                WHERE user_id=? AND id=?
                """,
                "/jobs/" + waitingJob + "/overview",
                java.sql.Timestamp.from(NOW),
                owner.userId(),
                waitingRun);

        mockMvc.perform(put("/api/v1/jobs/" + waitingJob)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(
                                "Manual Company",
                                "Manual Title",
                                "Manual Position",
                                "The user supplied the complete posting body after extraction stopped.",
                                null,
                                waitingJobState.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionStatus").value("MANUAL_INPUT_PROVIDED"))
                .andExpect(jsonPath("$.descriptionSource").value("USER_ENTERED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_runs WHERE id=?", String.class, waitingRun))
                .isEqualTo("QUEUED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT latest_agent_run_id FROM job_postings WHERE id=?",
                UUID.class,
                waitingJob)).isEqualTo(waitingRun);

        JsonNode failedAccepted = json(create(
                owner,
                "failed-job-key-0001",
                createBody("https://jobs.example.com/failed", null, null, null, null),
                202));
        UUID failedJob = UUID.fromString(failedAccepted.get("jobId").asText());
        UUID failedRun = UUID.fromString(failedAccepted.get("agentRunId").asText());
        var failedState = extraction.markFailed(owner.userId(), failedJob, failedRun, 0);
        budgetReservations.releaseUnused(owner.userId(), failedRun, NOW);
        jdbcTemplate.update(
                """
                UPDATE agent_runs SET status='FAILED',completed_at=?,
                    error_code='JOB_PAGE_TIMEOUT',error_message_safe='Try extraction again.',
                    retryable_failure=true,state_version=state_version+1,updated_at=?
                WHERE user_id=? AND id=?
                """,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                owner.userId(),
                failedRun);

        MvcResult retried = retry(
                owner, failedJob, failedState.version(), "failed-job-retry-01", 202);
        UUID successor = UUID.fromString(json(retried).get("agentRunId").asText());
        assertThat(successor).isNotEqualTo(failedRun);
        assertThat(json(retried).get("resourceType").asText()).isEqualTo("JOB");
        assertThat(json(retried).get("resourceId").asText()).isEqualTo(failedJob.toString());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT retry_of_run_id FROM agent_runs WHERE id=?", UUID.class, successor))
                .isEqualTo(failedRun);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT latest_agent_run_id FROM job_postings WHERE id=?",
                UUID.class,
                failedJob)).isEqualTo(successor);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT extraction_status FROM job_postings WHERE id=?",
                String.class,
                failedJob)).isEqualTo("QUEUED");

        MvcResult replay = retry(
                owner, failedJob, failedState.version(), "failed-job-retry-01", 202);
        assertThat(json(replay).get("agentRunId").asText()).isEqualTo(successor.toString());
        assertThat(json(replay).get("replayed").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=? AND root_run_id=?",
                Long.class,
                owner.userId(),
                failedRun)).isEqualTo(2L);
    }

    @Test
    void statusHistoryPreservesSubmissionAcrossCloseReopenAndOwnerMutationsAre404()
            throws Exception {
        Session owner = authenticated("job-state-owner@example.com");
        Session other = authenticated("job-state-other@example.com");
        UUID jobId = UUID.fromString(json(create(
                        owner,
                        "state-job-key-0001",
                        createBody(
                                "https://example.com/status-job",
                                "State Company",
                                "Engineer",
                                "A manual posting body used for status transition coverage.",
                                null),
                        201))
                .get("jobId")
                .asText());

        JsonNode submitted = changeStatus(owner, jobId, "SUBMITTED", 0, 200);
        String submittedAt = submitted.get("submittedAt").asText();
        JsonNode closed = changeStatus(owner, jobId, "CLOSED", 1, 200);
        assertThat(closed.get("closedReason").asText()).isEqualTo("USER_CLOSED");
        JsonNode reopened = changeStatus(owner, jobId, "IN_PROGRESS", 2, 200);
        assertThat(reopened.get("submittedAt").asText()).isEqualTo(submittedAt);
        assertThat(reopened.get("closedAt").isNull()).isTrue();
        assertThat(reopened.get("closedReason").isNull()).isTrue();

        MvcResult forbidden = changeStatusResult(owner, jobId, "IN_PROGRESS", 3, 409);
        assertThat(json(forbidden).get("code").asText()).isEqualTo("RESOURCE_STATE_CONFLICT");
        MvcResult stale = mockMvc.perform(put("/api/v1/jobs/" + jobId)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(
                                null,
                                null,
                                null,
                                "Stale replacement.",
                                null,
                                0)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(json(stale).get("code").asText()).isEqualTo("RESOURCE_VERSION_CONFLICT");
        assertThat(json(stale).get("fieldErrors").toString()).contains("version", "STALE");

        mockMvc.perform(put("/api/v1/jobs/" + jobId)
                        .cookie(other.cookie())
                        .header("X-CSRF-TOKEN", other.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, null, null, "Other user's edit.", null, 3)))
                .andExpect(status().isNotFound());
        changeStatusResult(other, jobId, "CLOSED", 3, 404);
        mockMvc.perform(delete("/api/v1/jobs/" + jobId)
                        .queryParam("version", "3")
                        .cookie(other.cookie())
                        .header("X-CSRF-TOKEN", other.csrfToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/jobs/" + jobId)
                        .queryParam("version", "3")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_status_history WHERE job_posting_id=?",
                Long.class,
                jobId)).isEqualTo(4L);
    }

    @Test
    void listFiltersValidateRangesCountAndP5ProjectionDefaults() throws Exception {
        Session owner = authenticated("job-list-owner@example.com");
        create(
                owner,
                "list-job-key-0001",
                createBody(
                        "https://example.com/list/one",
                        "Alpha Company",
                        "Backend Engineer",
                        "Manual body for the first searchable posting.",
                        NOW.plusSeconds(86_400)),
                201);
        create(
                owner,
                "list-job-key-0002",
                createBody(
                        "https://example.com/list/two",
                        "Beta Company",
                        "Data Engineer",
                        "Manual body for the second searchable posting.",
                        NOW.plusSeconds(864_000)),
                201);

        mockMvc.perform(get("/api/v1/jobs")
                        .cookie(owner.cookie())
                        .queryParam("query", "backend")
                        .queryParam("extractionStatus", "MANUAL_INPUT_PROVIDED")
                        .queryParam("deadlineWithinDays", "3")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .queryParam("sort", "deadlineAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].companyName").value("Alpha Company"))
                .andExpect(jsonPath("$.items[0].latestFitScore").isEmpty())
                .andExpect(jsonPath("$.items[0].analysisOutdated").value(false))
                .andExpect(jsonPath("$.items[0].outdatedReasons.length()").value(0))
                .andExpect(jsonPath("$.items[0].coverLetterStatus").isEmpty())
                .andExpect(jsonPath("$.items[0].interviewPreparationCount").value(0));

        assertValidationError(get("/api/v1/jobs")
                .cookie(owner.cookie())
                .queryParam("deadlineFrom", "2026-08-02T00:00:00Z")
                .queryParam("deadlineTo", "2026-08-01T00:00:00Z"));
        assertValidationError(get("/api/v1/jobs")
                .cookie(owner.cookie())
                .queryParam("deadlineWithinDays", "7")
                .queryParam("deadlineFrom", NOW.toString()));
        assertValidationError(get("/api/v1/jobs")
                .cookie(owner.cookie())
                .queryParam("sort", "title,asc"));
        assertValidationError(get("/api/v1/jobs")
                .cookie(owner.cookie())
                .queryParam("unknown", "value"));
    }

    @Test
    void schedulerAndUserRaceCreateExactlyOneCloseHistoryAndNeverDuplicateIt()
            throws Exception {
        Session owner = authenticated("job-scheduler-owner@example.com");
        UUID jobId = UUID.fromString(json(create(
                        owner,
                        "scheduler-job-key-01",
                        createBody(
                                "https://example.com/scheduler-job",
                                "Scheduler Company",
                                "Engineer",
                                "A submitted posting whose deadline has already passed.",
                                NOW.minusSeconds(1)),
                        201))
                .get("jobId")
                .asText());
        changeStatus(owner, jobId, "SUBMITTED", 0, 200);
        assertThat(store.historyCount(owner.userId(), jobId)).isEqualTo(2);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var scheduled = executor.submit(() -> {
                start.await();
                return scheduler.closeExpiredJobs();
            });
            var user = executor.submit(() -> {
                start.await();
                try {
                    statusService.change(owner.userId(), jobId, JobStatus.CLOSED, 1);
                    return true;
                } catch (BusinessException failure) {
                    assertThat(failure.errorCode())
                            .isIn(
                                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                                    ErrorCode.RESOURCE_STATE_CONFLICT);
                    return false;
                }
            });
            start.countDown();
            assertThat(scheduled.get(10, TimeUnit.SECONDS)).isBetween(0, 1);
            user.get(10, TimeUnit.SECONDS);
        }

        var closed = store.findActive(owner.userId(), jobId).orElseThrow();
        assertThat(closed.status()).isEqualTo(JobStatus.CLOSED);
        assertThat(closed.submittedAt()).isEqualTo(NOW);
        assertThat(closed.closedAt()).isEqualTo(NOW);
        assertThat(store.historyCount(owner.userId(), jobId)).isEqualTo(3);
        assertThat(scheduler.closeExpiredJobs()).isZero();
        assertThat(store.historyCount(owner.userId(), jobId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM job_status_history
                WHERE job_posting_id=? AND to_status='CLOSED'
                """,
                Long.class,
                jobId)).isEqualTo(1L);
    }

    private MvcResult create(Session session, String key, String body, int expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/jobs")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        if (result.getResponse().getStatus() != expectedStatus
                && result.getResolvedException() != null) {
            throw new AssertionError(
                    "unexpected create failure", result.getResolvedException());
        }
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return result;
    }

    private MvcResult retry(
            Session session, UUID jobId, long version, String key, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/v1/jobs/" + jobId + "/retry-extraction")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private JsonNode changeStatus(
            Session session, UUID jobId, String target, long version, int expectedStatus)
            throws Exception {
        return json(changeStatusResult(session, jobId, target, version, expectedStatus));
    }

    private MvcResult changeStatusResult(
            Session session, UUID jobId, String target, long version, int expectedStatus)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/jobs/" + jobId + "/status")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\",\"version\":" + version + "}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private void assertValidationError(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode body = json(result);
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("fieldErrors")).isNotNull();
        assertThat(body.get("requestId").asText()).isNotBlank();
        assertThat(body.get("timestamp").asText()).isNotBlank();
    }

    private String createBody(
            String sourceUrl,
            String companyName,
            String positionName,
            String descriptionText,
            Instant deadlineAt)
            throws Exception {
        var body = objectMapper.createObjectNode().put("sourceUrl", sourceUrl);
        if (companyName != null) body.put("companyName", companyName);
        if (positionName != null) body.put("positionName", positionName);
        if (descriptionText != null) body.put("descriptionText", descriptionText);
        if (deadlineAt != null) body.put("deadlineAt", deadlineAt.toString());
        return objectMapper.writeValueAsString(body);
    }

    private String updateBody(
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            long version)
            throws Exception {
        var body = objectMapper.createObjectNode().put("version", version);
        if (companyName != null) body.put("companyName", companyName);
        if (title != null) body.put("title", title);
        if (positionName != null) body.put("positionName", positionName);
        if (descriptionText != null) body.put("descriptionText", descriptionText);
        if (deadlineAt != null) body.put("deadlineAt", deadlineAt.toString());
        return objectMapper.writeValueAsString(body);
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
    static class TestPorts {

        @Bean
        @Primary
        Clock p5Clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
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
}
