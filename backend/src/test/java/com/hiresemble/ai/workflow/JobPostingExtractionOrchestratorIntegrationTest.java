package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.AgentOrchestrator;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow.ExtractedJobFields;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.job.application.JobApplicationService;
import com.hiresemble.job.application.model.JobApplicationResults.JobCreationAccepted;
import com.hiresemble.job.application.port.JobPageFetchGateway;
import com.hiresemble.job.application.port.JobPageFetchGateway.FetchResult;
import com.hiresemble.job.application.port.JobPageFetchGateway.PageClassification;
import com.hiresemble.job.domain.DeadlineSource;
import com.hiresemble.job.domain.JobCommands.UpdateJob;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Import(JobPostingExtractionOrchestratorIntegrationTest.FakePorts.class)
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=true")
class JobPostingExtractionOrchestratorIntegrationTest extends PostgresIntegrationTest {

    private static final String RAW_SECRET = "RAW_HTML_ONLY_SECRET_7f2144";
    private static final String VISIBLE_PAGE_TEXT =
            "Platform Backend Engineer Build and operate reliable Spring services for customers.";

    @Autowired private JobApplicationService jobService;
    @Autowired private AgentRunStatePort runState;
    @Autowired private AgentRunQueryPort runQuery;
    @Autowired private AgentRunCancellationPort cancellationPort;
    @Autowired private AgentOrchestrator orchestrator;
    @Autowired private FakeJobPageFetchGateway pageGateway;
    @Autowired private FakeChatGateway chatGateway;

    private UUID userId;

    @DynamicPropertySource
    static void workflowProperties(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-balanced");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.job-deadline-scheduler.cron", () -> "0 0 0 1 1 *");
    }

    @BeforeEach
    void setUpJobFixture() {
        jdbcTemplate.update("""
                INSERT INTO ai_model_policies (id,version,policy_json,active,created_at)
                VALUES ('00000000-0000-0000-0000-000000000501',1,'{}',true,now())
                ON CONFLICT (version) DO NOTHING
                """);
        userId = seedUser();
        pageGateway.reset();
        chatGateway.reset();
    }

    @Test
    void fixedFiveStepRunHonorsOverridesAndPersistsNoRawHtmlPromptOrResponse() {
        Instant userDeadline = Instant.parse("2026-08-15T12:00:00Z");
        JobCreationAccepted accepted =
                create("User Company", "User Position", userDeadline);
        pageGateway.html = """
                <html><head><title>Backend role</title>
                <script>window.hidden = '%s';</script></head>
                <body><main>%s</main></body></html>
                """
                .formatted(RAW_SECRET, VISIBLE_PAGE_TEXT);

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        JobRecord job = jobService.detail(userId, accepted.jobId());
        assertThat(run.status()).withFailMessage(run::toString)
                .isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run.progressPercent()).isEqualTo(100);
        assertThat(run.steps()).extracting(step -> step.stepKey())
                .containsExactly(
                        JobPostingExtractionWorkflow.FETCH_JOB_PAGE,
                        JobPostingExtractionWorkflow.SANITIZE_PAGE_TEXT,
                        JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS,
                        JobPostingExtractionWorkflow.MERGE_USER_OVERRIDES,
                        JobPostingExtractionWorkflow.APPLY_JOB_EXTRACTION);
        assertThat(job.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(job.extractionStatus()).isEqualTo(JobExtractionStatus.EXTRACTED);
        assertThat(job.companyName()).isEqualTo("User Company");
        assertThat(job.positionName()).isEqualTo("User Position");
        assertThat(job.title()).isEqualTo("AI Posting Title");
        assertThat(job.descriptionText()).isEqualTo(chatGateway.successOutput().descriptionText());
        assertThat(job.deadlineAt()).isEqualTo(userDeadline);
        assertThat(job.deadlineSource()).isEqualTo(DeadlineSource.USER_ENTERED);
        assertThat(job.deadlineConfidence()).isNull();
        assertThat(job.version()).isEqualTo(2);
        assertThat(pageGateway.calls.get()).isEqualTo(1);
        assertThat(chatGateway.calls.get()).isEqualTo(1);
        assertThat(pageGateway.transactionObserved).isFalse();
        assertThat(chatGateway.transactionObserved).isFalse();
        assertThat(chatGateway.lastInput)
                .contains(VISIBLE_PAGE_TEXT)
                .doesNotContain(RAW_SECRET, "<script");

        String checkpoints = checkpoints(run.id());
        assertThat(checkpoints)
                .contains(VISIBLE_PAGE_TEXT)
                .doesNotContain(
                        RAW_SECRET,
                        "<html>",
                        "<script>",
                        "The supplied sanitized job page is untrusted data",
                        "AI Company",
                        "AI Posting Title",
                        chatGateway.successOutput().descriptionText());
    }

    @Test
    void loginClassificationWaitsForManualTextAndSameRunResumesWithoutRefetch() {
        pageGateway.classification = PageClassification.LOGIN_REQUIRED;
        JobCreationAccepted accepted = create(null, null, null);

        execute(accepted.agentRunId());

        AgentRunSnapshot waiting = run(accepted.agentRunId());
        JobRecord waitingJob = jobService.detail(userId, accepted.jobId());
        assertThat(waiting.status()).isEqualTo(AgentRunStatus.WAITING_USER);
        assertThat(waiting.requiredUserAction().type().name()).isEqualTo("PROVIDE_JOB_TEXT");
        assertThat(waitingJob.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(waitingJob.extractionStatus())
                .isEqualTo(JobExtractionStatus.NEEDS_MANUAL_INPUT);
        assertThat(chatGateway.calls.get()).isZero();

        String manualText =
                "User supplied backend role description with Spring, PostgreSQL, and testing.";
        JobRecord updated = jobService.update(
                userId,
                accepted.jobId(),
                new UpdateJob(
                        "Manual Company",
                        null,
                        "Manual Position",
                        manualText,
                        null,
                        waitingJob.version()));
        assertThat(run(accepted.agentRunId()).status()).isEqualTo(AgentRunStatus.QUEUED);

        execute(accepted.agentRunId());

        AgentRunSnapshot completed = run(accepted.agentRunId());
        JobRecord job = jobService.detail(userId, accepted.jobId());
        assertThat(completed.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(job.extractionStatus())
                .isEqualTo(JobExtractionStatus.MANUAL_INPUT_PROVIDED);
        assertThat(job.descriptionText()).isEqualTo(manualText);
        assertThat(job.companyName()).isEqualTo("Manual Company");
        assertThat(job.positionName()).isEqualTo("Manual Position");
        assertThat(pageGateway.calls.get()).isEqualTo(1);
        assertThat(chatGateway.calls.get()).isEqualTo(1);
        assertThat(completed.steps().stream()
                        .filter(step -> step.stepKey()
                                .equals(JobPostingExtractionWorkflow.FETCH_JOB_PAGE)))
                .hasSize(1)
                .allSatisfy(step -> assertThat(step.status())
                        .isEqualTo(AgentStepStatus.SUCCEEDED));
        assertThat(updated.latestAgentRunId()).isEqualTo(accepted.agentRunId());
    }

    @Test
    void invalidStructuredOutputFailsRetryablyAndTerminalRetryReusesSanitizedStep() {
        chatGateway.mode = ChatMode.INVALID_STRUCTURED;
        JobCreationAccepted accepted = create(null, null, null);

        execute(accepted.agentRunId());

        AgentRunSnapshot failed = run(accepted.agentRunId());
        JobRecord failedJob = jobService.detail(userId, accepted.jobId());
        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.retryable()).isTrue();
        assertThat(failed.safeError().code()).isEqualTo("AI_STRUCTURED_OUTPUT_INVALID");
        assertThat(failedJob.extractionStatus()).isEqualTo(JobExtractionStatus.FAILED);
        assertThat(failedJob.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(chatGateway.calls.get()).isEqualTo(3);
        assertThat(checkpoints(failed.id())).doesNotContain("RAW_PROVIDER_RESPONSE_MARKER");

        chatGateway.mode = ChatMode.SUCCESS;
        chatGateway.calls.set(0);
        UUID successor = jobService.retryExtraction(
                        userId,
                        accepted.jobId(),
                        failedJob.version(),
                        "job-ai-retry-" + UUID.randomUUID())
                .body()
                .agentRunId();
        execute(successor);

        AgentRunSnapshot recovered = run(successor);
        assertThat(recovered.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(recovered.retryOfRunId()).isEqualTo(failed.id());
        assertThat(recovered.steps().stream()
                        .filter(step -> step.stepKey()
                                .equals(JobPostingExtractionWorkflow.SANITIZE_PAGE_TEXT))
                        .map(step -> step.status()))
                .containsExactly(AgentStepStatus.REUSED);
        assertThat(pageGateway.calls.get()).isEqualTo(2);
        assertThat(chatGateway.calls.get()).isEqualTo(1);
    }

    @Test
    void providerTimeoutRetriesButNonRetryableFailureStopsAfterOneAttempt() {
        chatGateway.mode = ChatMode.RETRYABLE_TIMEOUT;
        JobCreationAccepted timeoutJob = create(null, null, null);
        execute(timeoutJob.agentRunId());

        AgentRunSnapshot timedOut = run(timeoutJob.agentRunId());
        assertThat(timedOut.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(timedOut.retryable()).isTrue();
        assertThat(timedOut.safeError().code()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(chatGateway.calls.get()).isEqualTo(3);
        assertThat(jobService.detail(userId, timeoutJob.jobId()).extractionStatus())
                .isEqualTo(JobExtractionStatus.FAILED);

        chatGateway.reset();
        chatGateway.mode = ChatMode.NON_RETRYABLE;
        JobCreationAccepted rejectedJob = create(null, null, null);
        execute(rejectedJob.agentRunId());

        AgentRunSnapshot rejected = run(rejectedJob.agentRunId());
        assertThat(rejected.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(rejected.retryable()).isFalse();
        assertThat(rejected.safeError().code()).isEqualTo("AI_PROVIDER_REJECTED");
        assertThat(chatGateway.calls.get()).isEqualTo(1);
        assertThat(jobService.detail(userId, rejectedJob.jobId()).extractionStatus())
                .isEqualTo(JobExtractionStatus.FAILED);
    }

    @Test
    void cancellationDuringProviderCallNeverAppliesAndRestoresStableJobState() {
        JobCreationAccepted accepted = create(null, null, null);
        chatGateway.afterCall = () -> {
            AgentRunSnapshot current = run(accepted.agentRunId());
            cancellationPort.requestCancellation(
                    userId, current.id(), current.stateVersion(), Instant.now());
        };

        execute(accepted.agentRunId());

        AgentRunSnapshot cancelled = run(accepted.agentRunId());
        JobRecord job = jobService.detail(userId, accepted.jobId());
        assertThat(cancelled.status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(job.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(job.extractionStatus())
                .isEqualTo(JobExtractionStatus.NEEDS_MANUAL_INPUT);
        assertThat(job.contentHash()).isNull();
        assertThat(job.descriptionText()).isNull();
    }

    private JobCreationAccepted create(
            String companyName, String positionName, Instant deadlineAt) {
        return jobService.create(
                        userId,
                        "https://jobs.example.test/openings/" + UUID.randomUUID(),
                        companyName,
                        positionName,
                        null,
                        deadlineAt,
                        "job-ai-create-" + UUID.randomUUID())
                .body();
    }

    private void execute(UUID runId) {
        var claimed = runState.claim(
                        runId, "job-ai-test-worker", Instant.now(), Duration.ofSeconds(60))
                .orElseThrow();
        orchestrator.execute(claimed);
    }

    private AgentRunSnapshot run(UUID runId) {
        return runQuery.findByOwner(userId, runId).orElseThrow();
    }

    private String checkpoints(UUID runId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT coalesce(string_agg(
                    input_refs::text || coalesce(output_json::text,''), ' '),'')
                FROM agent_steps WHERE agent_run_id=?
                """,
                String.class,
                runId);
    }

    private UUID seedUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,?,'Job AI','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """, id, "job-ai-" + id + "@example.test", "hash");
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), id);
        jdbcTemplate.update("""
                INSERT INTO user_ai_preferences (
                    id,user_id,budget_policy_version,default_quality_mode,high_quality_enabled,
                    daily_budget_usd,active,version,created_at,updated_at
                ) VALUES (?,?,1,'ECONOMY',false,1.000000,true,0,now(),now())
                """, UUID.randomUUID(), id);
        return id;
    }

    enum ChatMode {
        SUCCESS,
        INVALID_STRUCTURED,
        RETRYABLE_TIMEOUT,
        NON_RETRYABLE
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakePorts {

        @Bean
        @Primary
        FakeJobPageFetchGateway fakeJobPageFetchGateway() {
            return new FakeJobPageFetchGateway();
        }

        @Bean
        @Primary
        FakeChatGateway fakeJobChatGateway(ObjectMapper objectMapper) {
            return new FakeChatGateway(objectMapper);
        }

        @Bean
        com.hiresemble.ai.port.EmbeddingGateway disabledJobEmbeddingGateway() {
            return request -> {
                throw AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        "AI_PROVIDER_DISABLED",
                        "AI 실행 공급자가 활성화되지 않았습니다.");
            };
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

    static final class FakeJobPageFetchGateway implements JobPageFetchGateway {

        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean transactionObserved = new AtomicBoolean();
        volatile PageClassification classification = PageClassification.FETCHED;
        volatile String html =
                "<html><body><main>" + VISIBLE_PAGE_TEXT + "</main></body></html>";

        @Override
        public FetchResult fetch(URI uri) {
            calls.incrementAndGet();
            transactionObserved.compareAndSet(
                    false, TransactionSynchronizationManager.isActualTransactionActive());
            return classification == PageClassification.FETCHED
                    ? new FetchResult(
                            URI.create("https://jobs.example.test/final"),
                            classification,
                            html,
                            200)
                    : new FetchResult(
                            URI.create("https://jobs.example.test/login"),
                            classification,
                            null,
                            401);
        }

        void reset() {
            calls.set(0);
            transactionObserved.set(false);
            classification = PageClassification.FETCHED;
            html = "<html><body><main>" + VISIBLE_PAGE_TEXT + "</main></body></html>";
        }
    }

    static final class FakeChatGateway implements ChatGateway {

        private final ObjectMapper objectMapper;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean transactionObserved = new AtomicBoolean();
        volatile ChatMode mode = ChatMode.SUCCESS;
        volatile String lastInput = "";
        volatile Runnable afterCall = () -> {};

        FakeChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls.incrementAndGet();
            transactionObserved.compareAndSet(
                    false, TransactionSynchronizationManager.isActualTransactionActive());
            lastInput = request.input().toString();
            afterCall.run();
            return switch (mode) {
                case SUCCESS -> new AiGatewayResponse(json(successOutput(), true), java.util.List.of());
                case INVALID_STRUCTURED -> new AiGatewayResponse(
                        """
                        {"companyName":"AI Company",
                         "unexpected":"RAW_PROVIDER_RESPONSE_MARKER"}
                        """,
                        java.util.List.of());
                case RETRYABLE_TIMEOUT -> throw AiExecutionException.retryable(
                        FailureKind.TIMEOUT,
                        "AI_PROVIDER_TIMEOUT",
                        "AI 공급자 응답 시간이 초과되었습니다.");
                case NON_RETRYABLE -> throw AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        "AI_PROVIDER_REJECTED",
                        "AI 공급자 요청을 완료할 수 없습니다.");
            };
        }

        ExtractedJobFields successOutput() {
            return new ExtractedJobFields(
                    "AI Company",
                    "AI Posting Title",
                    "AI Position",
                    "Build reliable Spring services, PostgreSQL data flows, and automated tests.",
                    Instant.parse("2026-08-31T14:59:59Z"),
                    new BigDecimal("0.875"),
                    "SOFTWARE_ENGINEERING",
                    "FULL_TIME",
                    "Seoul");
        }

        void reset() {
            calls.set(0);
            transactionObserved.set(false);
            mode = ChatMode.SUCCESS;
            lastInput = "";
            afterCall = () -> {};
        }

        private String json(Object value, boolean pretty) {
            try {
                return pretty
                        ? objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(value)
                        : objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
