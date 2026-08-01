package com.hiresemble.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow;
import com.hiresemble.job.application.port.JobPageFetchGateway;
import com.hiresemble.job.application.port.JobPageFetchGateway.FetchResult;
import com.hiresemble.job.application.port.JobPageFetchGateway.PageClassification;
import com.hiresemble.support.PostgresIntegrationTest;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(P5BrowserE2eTest.FakeJobWorkflowConfiguration.class)
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=true")
class P5BrowserE2eTest extends PostgresIntegrationTest {

    private static final String SUCCESS_JOB_URL =
            "https://success.p5-e2e.test/jobs/{nonce}";
    private static final String EMPTY_JOB_URL =
            "https://empty.p5-e2e.test/jobs/{nonce}";

    @LocalServerPort private int backendPort;

    @Autowired private FakeJobPageFetchGateway pageGateway;
    @Autowired private FakeJobChatGateway chatGateway;

    @DynamicPropertySource
    static void p5Environment(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-job-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-job-balanced");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.job.ai-cost.estimated-cost-usd", () -> "0.000000");
        registry.add("hiresemble.job.ai-cost.price-version", () -> "0");
        registry.add("hiresemble.job-page-fetch.min-dom-meaningful-characters", () -> "300");
        registry.add("hiresemble.job-page-fetch.min-description-meaningful-characters", () -> "80");
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "100ms");
        registry.add("hiresemble.agent-runtime.reconciliation-interval", () -> "1s");
        registry.add("hiresemble.agent-runtime.heartbeat-interval", () -> "1s");
        registry.add("hiresemble.job-deadline-scheduler.cron", () -> "*/1 * * * * *");
        registry.add("hiresemble.job-deadline-scheduler.batch-size", () -> "10");
    }

    @BeforeEach
    void resetFakes() {
        pageGateway.reset();
        chatGateway.reset();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void actualSpringPostgresVueJobWorkflowSchedulerSseAndChromiumPipeline()
            throws Exception {
        Path frontend = frontendDirectory();
        int frontendPort = availablePort();
        String corepack = System.getProperty("os.name", "")
                        .toLowerCase(Locale.ROOT)
                        .contains("win")
                ? "corepack.cmd"
                : "corepack";
        ProcessBuilder builder = new ProcessBuilder(
                corepack,
                "pnpm",
                "exec",
                "playwright",
                "test",
                "e2e/jobs.actual.spec.ts",
                "--project=chromium",
                "--workers=1",
                "--reporter=line");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("P5_E2E_ENABLED", "true");
        builder.environment().put("P5_FRONTEND_PORT", Integer.toString(frontendPort));
        builder.environment().put(
                "P5_FRONTEND_BASE_URL", "http://127.0.0.1:" + frontendPort);
        builder.environment().put(
                "VITE_API_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
        builder.environment().put("P5_E2E_SUCCESS_JOB_URL", SUCCESS_JOB_URL);
        builder.environment().put("P5_E2E_EMPTY_JOB_URL", EMPTY_JOB_URL);
        builder.environment().put("P5_E2E_SCHEDULER_TIMEOUT_MS", "60000");
        builder.environment().put("PLAYWRIGHT_HTML_OPEN", "never");

        Process browser = builder.start();
        boolean finished = browser.waitFor(9, TimeUnit.MINUTES);
        if (!finished) {
            browser.destroyForcibly();
            throw new AssertionError("P5 Playwright process exceeded nine minutes");
        }
        assertThat(browser.exitValue()).isZero();

        assertThat(pageGateway.calls()).isGreaterThanOrEqualTo(3);
        assertThat(chatGateway.calls()).isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM job_postings
                WHERE extraction_status='EXTRACTED'
                  AND description_source='AUTO_EXTRACTED'
                  AND deleted_at IS NULL
                """, Long.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM job_postings
                WHERE extraction_status='MANUAL_INPUT_PROVIDED'
                  AND description_source='USER_ENTERED'
                  AND deleted_at IS NULL
                """, Long.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM job_postings
                WHERE closed_reason='DEADLINE_PASSED'
                  AND status='CLOSED'
                  AND deleted_at IS NULL
                """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM job_postings
                WHERE submitted_at IS NOT NULL AND status='IN_PROGRESS'
                  AND closed_at IS NULL AND closed_reason IS NULL
                  AND deleted_at IS NULL
                """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM agent_runs
                WHERE workflow_type='JOB_POSTING_EXTRACTION'
                  AND resource_type='JOB'
                  AND status='SUCCEEDED'
                """, Long.class)).isGreaterThanOrEqualTo(3);
    }

    private Path frontendDirectory() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path direct = working.resolve("frontend");
        if (Files.isRegularFile(direct.resolve("package.json"))) return direct;
        Path sibling = working.resolveSibling("frontend");
        if (Files.isRegularFile(sibling.resolve("package.json"))) return sibling;
        throw new IllegalStateException(
                "frontend/package.json could not be located from " + working);
    }

    private int availablePort() throws java.io.IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeJobWorkflowConfiguration {

        @Bean
        @Primary
        FakeJobPageFetchGateway p5BrowserJobPageFetchGateway() {
            return new FakeJobPageFetchGateway();
        }

        @Bean
        @Primary
        FakeJobChatGateway p5BrowserJobChatGateway(ObjectMapper objectMapper) {
            return new FakeJobChatGateway(objectMapper);
        }

        @Bean
        com.hiresemble.ai.port.EmbeddingGateway p5DisabledEmbeddingGateway() {
            return request -> {
                throw com.hiresemble.ai.execution.AiExecutionException.nonRetryable(
                        com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind.CONFIGURATION,
                        "AI_PROVIDER_DISABLED",
                        "AI 실행 공급자가 활성화되지 않았습니다.");
            };
        }
    }

    static final class FakeJobPageFetchGateway implements JobPageFetchGateway {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public FetchResult fetch(URI uri) {
            calls.incrementAndGet();
            if ("empty.p5-e2e.test".equals(uri.getHost())) {
                return new FetchResult(uri, PageClassification.EMPTY, null, 200);
            }
            if (!"success.p5-e2e.test".equals(uri.getHost())) {
                throw new AssertionError("Unexpected P5 Job fixture URL");
            }
            String html = """
                    <!doctype html>
                    <html lang="ko">
                      <head><title>Hiresemble Fake Backend Engineer</title></head>
                      <body>
                        <main>
                          <h1>Backend Engineer</h1>
                          <p>Spring Boot와 PostgreSQL 기반의 안정적인 API를 설계하고 운영합니다.</p>
                          <p>자동화 테스트, 코드 리뷰, 보안 점검, 관찰 가능성 개선과 장애 대응을 담당합니다.</p>
                          <p>제품 팀과 요구사항을 정리하고 성능 병목을 분석하며 기술 의사결정을 문서화합니다.</p>
                          <p>Java 서비스와 분산 시스템 운영 경험, 명확한 커뮤니케이션 역량이 필요합니다.</p>
                          <p>배포 자동화, 용량 계획, 의존성 업그레이드, 데이터 모델링 경험을 우대합니다.</p>
                          <p>채용 과정은 기술 대화, 실무 검토, 팀 인터뷰 순서로 진행되는 정규직 공고입니다.</p>
                          <p>고객 문제를 주도적으로 해결하고 유지보수 가능한 구조와 측정 가능한 신뢰성을 추구합니다.</p>
                          <p>This deterministic fixture contains enough visible job text for extraction and quality validation.</p>
                        </main>
                      </body>
                    </html>
                    """;
            return new FetchResult(uri, PageClassification.FETCHED, html, 200);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }

    static final class FakeJobChatGateway implements ChatGateway {

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();

        FakeJobChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls.incrementAndGet();
            String sourceText = request.input().path("sourceText").asText();
            if (sourceText.isBlank()) {
                throw new AssertionError("Fake Job Chat requires non-empty source fixture text");
            }
            boolean remoteFixture = sourceText.contains("Backend Engineer");
            var output = new JobPostingExtractionWorkflow.ExtractedJobFields(
                    remoteFixture ? "Hiresemble Fixture" : null,
                    remoteFixture ? "Backend Engineer" : null,
                    remoteFixture ? "Backend Engineer" : null,
                    remoteFixture
                            ? "Spring Boot와 PostgreSQL 기반 API를 설계하고 운영하며 자동화 테스트, 코드 리뷰, 보안 점검, 관찰 가능성 개선, 장애 대응, 성능 분석과 기술 문서화를 제품 팀과 함께 수행합니다. 안정적인 배포와 유지보수 가능한 서비스 구조를 책임집니다."
                            : sourceText,
                    Instant.parse("2026-12-31T15:00:00Z"),
                    new java.math.BigDecimal("0.950"),
                    remoteFixture ? "BACKEND" : null,
                    remoteFixture ? "FULL_TIME" : null,
                    remoteFixture ? "Seoul" : null);
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), java.util.List.of());
            } catch (Exception exception) {
                throw new IllegalStateException("Fake Job Chat serialization failed", exception);
            }
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
