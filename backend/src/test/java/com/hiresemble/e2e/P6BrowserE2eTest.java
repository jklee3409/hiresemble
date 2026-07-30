package com.hiresemble.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.EmbeddingValuesOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.EligibilityAssessmentOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ExtractRequirementsOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.MatchEvidenceOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.MatchedCriterion;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementCandidate;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementSection;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.StrengthDraft;
import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.FitCriterionCategory;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.support.PostgresIntegrationTest;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(P6BrowserE2eTest.FakeJobAnalysisConfiguration.class)
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=true")
class P6BrowserE2eTest extends PostgresIntegrationTest {

    @LocalServerPort private int backendPort;

    @Autowired private FakeJobAnalysisChatGateway chatGateway;
    @Autowired private FakeJobAnalysisEmbeddingGateway embeddingGateway;

    @DynamicPropertySource
    static void p6Environment(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-job-analysis-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-job-analysis-balanced");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.job.analysis-ai-cost.estimated-cost-usd", () -> "0.000000");
        registry.add("hiresemble.job.analysis-ai-cost.price-version", () -> "0");
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "100ms");
        registry.add("hiresemble.agent-runtime.reconciliation-interval", () -> "1s");
        registry.add("hiresemble.agent-runtime.heartbeat-interval", () -> "1s");
    }

    @BeforeEach
    void resetFakes() {
        chatGateway.reset();
        embeddingGateway.reset();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void actualSpringPostgresFakeAiVueSseAndChromiumPipeline() throws Exception {
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
                "e2e/job-analysis.actual.spec.ts",
                "--project=chromium",
                "--workers=1",
                "--reporter=line");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("P6_E2E_ENABLED", "true");
        builder.environment().put("P6_FRONTEND_PORT", Integer.toString(frontendPort));
        builder.environment().put(
                "P6_FRONTEND_BASE_URL", "http://127.0.0.1:" + frontendPort);
        builder.environment().put(
                "VITE_API_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
        builder.environment().put("PLAYWRIGHT_HTML_OPEN", "never");

        Process browser = builder.start();
        boolean finished = browser.waitFor(9, TimeUnit.MINUTES);
        if (!finished) {
            browser.destroyForcibly();
            throw new AssertionError("P6 Playwright process exceeded nine minutes");
        }
        assertThat(browser.exitValue()).isZero();

        assertThat(chatGateway.calls()).isEqualTo(7);
        assertThat(embeddingGateway.calls()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM job_analyses", Long.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                        SELECT analysis_version
                        FROM job_analyses
                        ORDER BY analysis_version
                        """, Integer.class))
                .containsExactly(1, 2);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM job_analyses
                        WHERE sealed AND eligibility='ELIGIBLE' AND fit_score=100.00
                        """, Long.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM job_analysis_score_criteria
                        WHERE match_level='MATCHED' AND score=weight
                        """, Long.class))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM job_analysis_evidence_links", Long.class))
                .isGreaterThanOrEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='JOB_ANALYSIS' AND status='SUCCEEDED'
                        """, Long.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='JOB_ANALYSIS' AND status='FAILED'
                          AND safe_error_code='INSUFFICIENT_JOB_DATA'
                        """, Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM agent_run_resource_links
                        WHERE resource_kind='JOB_ANALYSIS'
                        """, Long.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM job_analyses analysis
                        JOIN job_postings job ON job.user_id=analysis.user_id
                          AND job.id=analysis.job_posting_id
                        WHERE job.source_url LIKE 'https://manual.p6-e2e.invalid/insufficient-%'
                        """, Long.class))
                .isZero();
    }

    private Path frontendDirectory() {
        Path working = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();
        Path direct = working.resolve("frontend");
        if (Files.isRegularFile(direct.resolve("package.json"))) {
            return direct;
        }
        Path sibling = working.resolveSibling("frontend");
        if (Files.isRegularFile(sibling.resolve("package.json"))) {
            return sibling;
        }
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
    static class FakeJobAnalysisConfiguration {

        @Bean
        @Primary
        FakeJobAnalysisChatGateway p6BrowserChatGateway(ObjectMapper objectMapper) {
            return new FakeJobAnalysisChatGateway(objectMapper);
        }

        @Bean
        @Primary
        FakeJobAnalysisEmbeddingGateway p6BrowserEmbeddingGateway(
                ObjectMapper objectMapper) {
            return new FakeJobAnalysisEmbeddingGateway(objectMapper);
        }
    }

    static final class FakeJobAnalysisChatGateway implements ChatGateway {

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();

        FakeJobAnalysisChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls.incrementAndGet();
            Object output = switch (request.outputSchemaVersion()) {
                case "job-analysis-requirements-output-v1" -> requirements(request.input());
                case "job-analysis-eligibility-output-v1" -> eligibility(request.input());
                case "job-analysis-match-output-v1" -> matches(request.input());
                default -> throw new AssertionError(
                        "Unexpected P6 chat schema: " + request.outputSchemaVersion());
            };
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), null);
            } catch (Exception exception) {
                throw new IllegalStateException("Fake P6 output serialization failed", exception);
            }
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }

        private ExtractRequirementsOutput requirements(JsonNode input) {
            String description = input.path("untrustedJobPosting")
                    .path("descriptionText")
                    .asText();
            if (description.contains("NO_REQUIREMENTS_FIXTURE")) {
                return new ExtractRequirementsOutput(
                        "job-analysis-requirements-output-v1",
                        false,
                        null,
                        List.of());
            }
            return new ExtractRequirementsOutput(
                    "job-analysis-requirements-output-v1",
                    false,
                    null,
                    List.of(
                            new RequirementCandidate(
                                    RequirementSection.REQUIRED_QUALIFICATION,
                                    FitCriterionCategory.REQUIRED_QUALIFICATION,
                                    "백엔드 개발 경력 3년 이상",
                                    true,
                                    "필수 지원 자격"),
                            new RequirementCandidate(
                                    RequirementSection.RESPONSIBILITY,
                                    FitCriterionCategory.CORE_RESPONSIBILITY_OR_SKILL,
                                    "Spring Boot API 개발",
                                    true,
                                    "주요 업무")));
        }

        private EligibilityAssessmentOutput eligibility(JsonNode input) {
            UUID evidenceId = firstUuid(
                    input.path("approvedProfile").path("verifiedEvidence"), "id");
            return new EligibilityAssessmentOutput(
                    "job-analysis-eligibility-output-v1",
                    false,
                    null,
                    Eligibility.ELIGIBLE,
                    List.of(evidenceId),
                    "승인된 경력 정보에서 필수 지원 자격을 확인했습니다.");
        }

        private MatchEvidenceOutput matches(JsonNode input) {
            UUID evidenceId =
                    firstUuid(input.path("verifiedEvidenceCandidates"), "evidenceId");
            return new MatchEvidenceOutput(
                    "job-analysis-match-output-v1",
                    false,
                    null,
                    List.of(
                            new MatchedCriterion(
                                    0,
                                    MatchLevel.MATCHED,
                                    List.of(evidenceId),
                                    "승인된 경력 기간과 일치합니다.",
                                    null),
                            new MatchedCriterion(
                                    1,
                                    MatchLevel.MATCHED,
                                    List.of(evidenceId),
                                    "승인된 Spring Boot 경험과 일치합니다.",
                                    null)),
                    List.of(new StrengthDraft(
                            "Spring Boot API 개발 경험", 1, List.of(evidenceId))),
                    List.of(),
                    "등록된 정보와 공고 요구사항의 일치도를 분석했습니다.");
        }

        private UUID firstUuid(JsonNode values, String field) {
            if (!values.isArray() || values.isEmpty()) {
                throw new AssertionError("P6 Fake requires one VERIFIED evidence value");
            }
            return UUID.fromString(values.get(0).path(field).asText());
        }
    }

    static final class FakeJobAnalysisEmbeddingGateway implements EmbeddingGateway {

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();

        FakeJobAnalysisEmbeddingGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse embed(EmbeddingRequest request) {
            calls.incrementAndGet();
            if (request.maskedInputs().size() != 1 || request.dimension() < 1) {
                throw new AssertionError("P6 Fake embedding contract is invalid");
            }
            try {
                return new AiGatewayResponse(
                        objectMapper.writeValueAsString(new EmbeddingValuesOutput(
                                List.of(Collections.nCopies(
                                        request.dimension(), 0.125d)))),
                        null);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Fake P6 embedding serialization failed", exception);
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
