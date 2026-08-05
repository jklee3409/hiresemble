package com.hiresemble.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.EvidenceClaimDraft;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.EvidenceClaimDraftV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocation;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocationOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocationOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocationV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.FactCheckAnswerOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.FactCheckAnswerOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.FactCheckAnswerOutputV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.HeadingPolicy;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.NarrativeFramework;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutputV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ProviderTipTapDocumentOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ProviderTipTapNodeOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionAnalysisOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionAnalysisOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionAnalysisOutputV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlan;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlanV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlanV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionType;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.VerifiedClaimDraft;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.VerifiedClaimDraftV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WrittenAnswerOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WrittenAnswerOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WrittenAnswerOutputV3;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.ClaimType;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionPlan;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionType;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.FactCheckOutput;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.RequirementCheckOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderEligibilityOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderMatchOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderMatchedCriterion;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderSourceRequirement;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderRequirementsOutput;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderStrengthDraft;
import com.hiresemble.ai.workflow.document.DocumentIngestionWorkflow;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(P7BrowserE2eTest.FakeP7AiConfiguration.class)
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=true")
class P7BrowserE2eTest extends PostgresIntegrationTest {

    private static final String ACCESS_KEY = "hiresemble";
    private static final String SECRET_KEY = "hiresemble-local-secret";
    private static final String BUCKET = "hiresemble-p7-browser";
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
                    DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    private static final String MINIO_ENDPOINT;
    private static final S3Client STORAGE_ADMIN;

    static {
        MINIO.start();
        MINIO_ENDPOINT = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        STORAGE_ADMIN = S3Client.builder()
                .endpointOverride(java.net.URI.create(MINIO_ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        STORAGE_ADMIN.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @LocalServerPort private int backendPort;

    @Autowired private FakeP7ChatGateway chatGateway;
    @Autowired private FakeP7EmbeddingGateway embeddingGateway;

    @DynamicPropertySource
    static void p7Environment(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.object-storage.endpoint", () -> MINIO_ENDPOINT);
        registry.add("hiresemble.object-storage.access-key", () -> ACCESS_KEY);
        registry.add("hiresemble.object-storage.secret-key", () -> SECRET_KEY);
        registry.add("hiresemble.object-storage.bucket", () -> BUCKET);
        registry.add("hiresemble.object-storage.region", () -> "us-east-1");
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-p7-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-p7-balanced");
        registry.add("hiresemble.ai.model-high-quality", () -> "fake-p7-high-quality");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.document.ai-cost.estimated-cost-usd", () -> "0.000000");
        registry.add("hiresemble.document.ai-cost.price-version", () -> "0");
        registry.add("hiresemble.job.analysis-ai-cost.estimated-cost-usd", () -> "0.000000");
        registry.add("hiresemble.job.analysis-ai-cost.price-version", () -> "0");
        registry.add(
                "hiresemble.cover-letter.ai-cost.generation-estimated-cost-usd",
                () -> "0.000000");
        registry.add(
                "hiresemble.cover-letter.ai-cost.generation-price-version",
                () -> "0");
        registry.add(
                "hiresemble.cover-letter.ai-cost.verification-estimated-cost-usd",
                () -> "0.000000");
        registry.add(
                "hiresemble.cover-letter.ai-cost.verification-price-version",
                () -> "0");
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "100ms");
        registry.add("hiresemble.agent-runtime.reconciliation-interval", () -> "1s");
        registry.add("hiresemble.agent-runtime.heartbeat-interval", () -> "1s");
        registry.add("hiresemble.object-deletion-outbox.scan-interval", () -> "100ms");
    }

    @BeforeEach
    void resetFakes() {
        chatGateway.reset();
        embeddingGateway.reset();
    }

    @AfterAll
    static void stopIsolatedStorage() {
        STORAGE_ADMIN.close();
        MINIO.stop();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void actualP7VerticalSliceAndDatabaseAssertions() throws Exception {
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
                "e2e/cover-letter.actual.spec.ts",
                "--project=chromium",
                "--workers=1",
                "--reporter=line",
                "--output=../output/playwright/p7");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("P7_E2E_ENABLED", "true");
        builder.environment().put("P7_FRONTEND_PORT", Integer.toString(frontendPort));
        builder.environment().put(
                "P7_FRONTEND_BASE_URL", "http://127.0.0.1:" + frontendPort);
        builder.environment().put(
                "VITE_API_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
        builder.environment().put("PLAYWRIGHT_HTML_OPEN", "never");

        Process browser = builder.start();
        boolean finished = browser.waitFor(14, TimeUnit.MINUTES);
        if (!finished) {
            browser.destroyForcibly();
            throw new AssertionError("P7 Playwright process exceeded fourteen minutes");
        }
        assertThat(browser.exitValue()).isZero();

        assertThat(chatGateway.calls()).isPositive();
        assertThat(embeddingGateway.calls()).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='COVER_LETTER_GENERATION'
                          AND workflow_version='cover-letter-generation-v3'
                        """,
                        Long.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='COVER_LETTER_VERIFICATION'
                          AND workflow_version='cover-letter-verification-v3'
                        """,
                        Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type IN (
                            'COVER_LETTER_GENERATION','COVER_LETTER_VERIFICATION')
                          AND workflow_version IN (
                            'cover-letter-generation-v1','cover-letter-generation-v2',
                            'cover-letter-verification-v1','cover-letter-verification-v2')
                        """,
                        Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM agent_runs retry
                        JOIN agent_runs predecessor
                          ON predecessor.user_id=retry.user_id
                         AND predecessor.id=retry.retry_of_run_id
                        WHERE retry.workflow_type='COVER_LETTER_GENERATION'
                          AND retry.workflow_version=predecessor.workflow_version
                          AND retry.workflow_version='cover-letter-generation-v3'
                        """,
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='COVER_LETTER_GENERATION'
                          AND status='FAILED'
                          AND error_code='COVER_LETTER_GENERATION_PARTIAL_FAILURE'
                        """,
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='COVER_LETTER_GENERATION'
                          AND status='SUCCEEDED' AND run_attempt_no=2
                          AND jsonb_array_length(partial_result_json->'succeededScopeKeys')=2
                          AND jsonb_array_length(partial_result_json->'failedScopeKeys')=0
                          AND jsonb_array_length(partial_result_json->'resultRefs')=2
                        """,
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_run_resource_links link
                        JOIN agent_runs run ON run.user_id=link.user_id
                          AND run.id=link.agent_run_id
                        WHERE run.workflow_type='COVER_LETTER_GENERATION'
                          AND run.run_attempt_no=2
                          AND link.resource_kind='COVER_LETTER_ANSWER_VERSION'
                        """,
                        Long.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_answer_versions
                        WHERE source_type='AI_REVISED' AND parent_version_id IS NOT NULL
                        """,
                        Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM cover_letter_evidence_links link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=link.user_id
                         AND evidence.id=link.profile_evidence_id
                        WHERE evidence.source_type='ACTIVITY'
                        """,
                        Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_answer_versions
                        WHERE source_type='RESTORED'
                          AND parent_version_id IS NOT NULL
                          AND restored_from_version_id IS NOT NULL
                          AND is_current
                        """,
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_verifications
                        WHERE status='PENDING'
                        """,
                        Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_verifications
                        WHERE status='WARNING'
                        """,
                        Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_verifications
                        WHERE status='PASSED'
                        """,
                        Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM cover_letter_evidence_links link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=link.user_id
                         AND evidence.id=link.profile_evidence_id
                        WHERE evidence.source_deleted_at IS NOT NULL
                        """,
                        Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letters
                        WHERE status IN ('DRAFT','FINALIZED') AND deleted_at IS NULL
                        """,
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letters
                        WHERE status='ARCHIVED' AND deleted_at IS NULL
                        """,
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM object_deletion_outbox
                        WHERE reason='DOCUMENT_DELETE' AND status='SUCCEEDED'
                        """,
                        Long.class))
                .isPositive();

        UUID immutableAnswerId = jdbcTemplate.queryForObject(
                "SELECT id FROM cover_letter_answer_versions ORDER BY created_at LIMIT 1",
                UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        UPDATE cover_letter_answer_versions
                        SET content_text='mutated'
                        WHERE id=?
                        """,
                        immutableAnswerId))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID immutableVerificationId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM cover_letter_verifications
                WHERE status <> 'PENDING'
                ORDER BY created_at LIMIT 1
                """,
                UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        UPDATE cover_letter_verifications
                        SET suggestions='[]'::jsonb
                        WHERE id=?
                        """,
                        immutableVerificationId))
                .isInstanceOf(DataIntegrityViolationException.class);
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
    static class FakeP7AiConfiguration {

        @Bean
        @Primary
        FakeP7ChatGateway p7BrowserChatGateway(ObjectMapper objectMapper) {
            return new FakeP7ChatGateway(objectMapper);
        }

        @Bean
        @Primary
        FakeP7EmbeddingGateway p7BrowserEmbeddingGateway(ObjectMapper objectMapper) {
            return new FakeP7EmbeddingGateway(objectMapper);
        }
    }

    static final class FakeP7ChatGateway implements ChatGateway {

        private static final String FAILURE_MARKER = "P7_FORCE_GENERATION_FAILURE";
        private static final String WARNING_MARKER = "P7_FORCE_VERIFICATION_WARNING";
        private static final String PASSED_MARKER = "P7_FORCE_VERIFICATION_PASSED";

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();
        private final Map<UUID, AtomicInteger> forcedFailureAttempts =
                new ConcurrentHashMap<>();

        FakeP7ChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls.incrementAndGet();
            Object output = switch (request.outputSchemaVersion()) {
                case "output-v1" -> documentEvidence(request.input());
                case "document-evidence-provider-output-v2" ->
                        documentEvidence(request.input());
                case "job-analysis-requirements-source-output-v6" ->
                        jobRequirements(request.input());
                case "job-analysis-eligibility-output-v3" ->
                        jobEligibility(request.input());
                case "job-analysis-match-output-v3" ->
                        jobMatches(request.input());
                case "cover-generation-plan-output-v1" ->
                        generationPlan(request.input());
                case "cover-generation-plan-output-v2" ->
                        generationPlanV2(request.input());
                case "cover-generation-plan-output-v3" ->
                        generationPlanV3(request.input());
                case "cover-generation-question-analysis-output-v1" ->
                        questionAnalysis(request.input());
                case "cover-generation-question-analysis-output-v2" ->
                        questionAnalysisV2(request.input());
                case "cover-generation-question-analysis-output-v3" ->
                        questionAnalysisV3(request.input());
                case "cover-generation-allocation-output-v1" ->
                        experienceAllocation(request.input());
                case "cover-generation-allocation-output-v2" ->
                        experienceAllocationV2(request.input());
                case "cover-generation-answer-output-v1" ->
                        writtenAnswer(request.input());
                case "cover-generation-answer-output-v2" ->
                        writtenAnswerV2(request.input());
                case "cover-generation-answer-output-v3" ->
                        writtenAnswerV3(request.input());
                case "cover-generation-fact-check-output-v1" ->
                        generatedAnswerFactCheck(request.input());
                case "cover-generation-fact-check-output-v2" ->
                        generatedAnswerFactCheckV2(request.input());
                case "cover-generation-fact-check-output-v3" ->
                        generatedAnswerFactCheckV3(request.input());
                case "cover-verification-facts-output-v1" ->
                        verificationFacts(request.input());
                case "cover-verification-facts-output-v2" ->
                        verificationFactsV2(request.input());
                case "cover-verification-facts-output-v3" ->
                        verificationFactsV3(request.input());
                case "cover-verification-requirements-output-v1" ->
                        verificationRequirements(request.input());
                case "cover-verification-requirements-output-v2" ->
                        verificationRequirementsV2(request.input());
                case "cover-verification-requirements-output-v3" ->
                        verificationRequirementsV3(request.input());
                default -> throw new AssertionError(
                        "Unexpected P7 chat schema: " + request.outputSchemaVersion());
            };
            if (output instanceof String rawJson) {
                return new AiGatewayResponse(rawJson, java.util.List.of());
            }
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), java.util.List.of());
            } catch (Exception exception) {
                throw new IllegalStateException("Fake P7 output serialization failed", exception);
            }
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
            forcedFailureAttempts.clear();
        }

        private DocumentIngestionWorkflow.EvidenceCandidateBatch documentEvidence(
                JsonNode input) {
            JsonNode first = input.path("maskedChunks").get(0);
            String grounded = first.path("maskedContent").asText();
            if (grounded.length() > 600) {
                grounded = grounded.substring(0, 600);
            }
            var candidate = new DocumentIngestionWorkflow.EvidenceCandidatePayload(
                    "프로젝트",
                    "P7 승인 프로젝트 근거",
                    grounded,
                    new BigDecimal("0.900"),
                    List.of(first.path("chunkRef").asText()),
                    null);
            return new DocumentIngestionWorkflow.EvidenceCandidateBatch(
                    List.of(candidate));
        }

        private ProviderRequirementsOutput jobRequirements(JsonNode input) {
            return new ProviderRequirementsOutput(
                    "job-analysis-requirements-source-output-v6",
                    List.of(
                            sourceRequirement(input, "백엔드 엔지니어링 경험"),
                            sourceRequirement(input, "Spring Boot API 개발")));
        }

        private ProviderSourceRequirement sourceRequirement(JsonNode input, String sourceText) {
            for (JsonNode block : input.path("untrustedJobPosting").path("sourceBlocks")) {
                if (sourceText.equals(block.path("sourceText").asText())) {
                    return new ProviderSourceRequirement(
                            block.path("sourceBlockId").asText(),
                            block.path("sourceText").asText(),
                            block.path("sourceOrdinal").asInt());
                }
            }
            throw new AssertionError("Missing P7 source block for fake requirement");
        }

        private ProviderEligibilityOutput jobEligibility(JsonNode input) {
            UUID evidenceId =
                    firstUuid(input.path("approvedProfile").path("verifiedEvidence"), "id");
            return new ProviderEligibilityOutput(
                    "job-analysis-eligibility-output-v3",
                    Eligibility.ELIGIBLE,
                    List.of(evidenceId),
                    "The approved evidence satisfies the required qualification.");
        }

        private ProviderMatchOutput jobMatches(JsonNode input) {
            UUID evidenceId =
                    firstUuid(input.path("verifiedEvidenceCandidates"), "evidenceId");
            return new ProviderMatchOutput(
                    "job-analysis-match-output-v3",
                    List.of(
                            new ProviderMatchedCriterion(
                                    0,
                                    MatchLevel.MATCHED,
                                    List.of(evidenceId),
                                    "The approved experience supports the qualification.",
                                    null),
                            new ProviderMatchedCriterion(
                                    1,
                                    MatchLevel.MATCHED,
                                    List.of(evidenceId),
                                    "The approved Spring experience supports the responsibility.",
                                    null)),
                    List.of(new ProviderStrengthDraft(
                            "Spring Boot API experience", 1, List.of(evidenceId))),
                    List.of(),
                    "The verified evidence matches the posting requirements.");
        }

        private PlanQuestionsOutput generationPlan(JsonNode input) {
            List<QuestionPlan> plans = new ArrayList<>();
            for (JsonNode question : input.path("questions")) {
                Integer maximum = question.path("maxLength").isNumber()
                        ? question.path("maxLength").asInt()
                        : null;
                plans.add(new QuestionPlan(
                        UUID.fromString(question.path("questionId").asText()),
                        "Answer the requested experience with verified evidence.",
                        List.of("verified experience", "job relevance"),
                        List.of("unsupported claims"),
                        input.path("requirements").isEmpty() ? List.of() : List.of(0),
                        maximum == null ? 400 : Math.min(400, maximum)));
            }
            return new PlanQuestionsOutput(
                    "cover-generation-plan-output-v1",
                    plans,
                    input.path("avoidExperienceDuplication").asBoolean());
        }

        private PlanQuestionsOutputV2 generationPlanV2(JsonNode input) {
            List<QuestionPlanV2> plans = new ArrayList<>();
            int order = 0;
            for (JsonNode question : input.path("questions")) {
                Integer maximum = question.path("maxLength").isNumber()
                        ? question.path("maxLength").asInt()
                        : null;
                plans.add(new QuestionPlanV2(
                        UUID.fromString(question.path("questionId").asText()),
                        QuestionType.ROLE_COMPETENCY,
                        "Verified role competency " + (++order == 1 ? "primary" : "secondary"),
                        NarrativeFramework.COMPETENCY_EVIDENCE_APPLICATION,
                        "Answer directly with verified evidence.",
                        List.of("personal action", "job relevance"),
                        List.of("unsupported claims"),
                        input.path("requirements").isEmpty() ? List.of() : List.of(0),
                        "Connect the applicant action to the role.",
                        "Use only supplied posting context.",
                        List.of("evidence with a decision, action, and result"),
                        maximum == null ? 400 : Math.min(400, maximum),
                        HeadingPolicy.OPTIONAL));
            }
            return new PlanQuestionsOutputV2(
                    "cover-generation-plan-output-v2",
                    plans,
                    input.path("avoidExperienceDuplication").asBoolean());
        }

        private PlanQuestionsOutputV3 generationPlanV3(JsonNode input) {
            List<QuestionPlanV3> plans = new ArrayList<>();
            int order = 0;
            for (JsonNode question : input.path("questions")) {
                int currentOrder = ++order;
                Integer maximum = question.path("maxLength").isNumber()
                        ? question.path("maxLength").asInt()
                        : null;
                boolean technical = currentOrder % 2 == 0;
                NarrativeFramework framework = technical
                        ? NarrativeFramework.TECHNICAL_DECISION_TRADEOFF
                        : NarrativeFramework.COMPETENCY_EVIDENCE_APPLICATION;
                List<NarrativeSectionPlan> sections = technical
                        ? List.of(
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.PROBLEM, "문제를 설명한다.", 25),
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.DECISION, "선택 근거를 설명한다.", 30),
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.TRADEOFF, "트레이드오프를 설명한다.", 20),
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.RESULT, "결과를 설명한다.", 25))
                        : List.of(
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.DIRECT_ANSWER, "역량을 직접 답한다.", 25),
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.PERSONAL_ACTION, "개인 행동을 설명한다.", 45),
                                new NarrativeSectionPlan(
                                        NarrativeSectionType.RESULT, "검증된 결과를 설명한다.", 30));
                plans.add(new QuestionPlanV3(
                        UUID.fromString(question.path("questionId").asText()),
                        technical ? QuestionType.TECHNICAL_PROJECT : QuestionType.ROLE_COMPETENCY,
                        technical ? "기술 선택과 트레이드오프" : "검증된 직무 역량",
                        framework,
                        "검증된 근거로 문항에 직접 답한다.",
                        List.of("개인 행동", "직무 관련성"),
                        List.of("근거 없는 주장"),
                        input.path("requirements").isEmpty() ? List.of() : List.of(0),
                        input.path("contextAvailability").path("roleContextAvailable").asBoolean()
                                ? "개인 행동을 직무와 연결한다."
                                : null,
                        input.path("contextAvailability").path("companyContextAvailable").asBoolean()
                                ? "제공된 회사 정보만 사용한다."
                                : null,
                        List.of("결정, 행동, 결과가 있는 근거"),
                        maximum == null ? 400 : Math.min(400, maximum),
                        HeadingPolicy.OPTIONAL,
                        sections));
            }
            return new PlanQuestionsOutputV3(
                    "cover-generation-plan-output-v3",
                    plans,
                    input.path("avoidExperienceDuplication").asBoolean());
        }

        private Object questionAnalysis(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            if (input.path("questionText").asText().contains(FAILURE_MARKER)
                    && forcedFailureAttempts
                                    .computeIfAbsent(questionId, ignored -> new AtomicInteger())
                                    .incrementAndGet()
                            <= 3) {
                return "{\"schemaVersion\":\"cover-generation-question-analysis-output-v1\"}";
            }
            return new QuestionAnalysisOutput(
                    "cover-generation-question-analysis-output-v1",
                    questionId,
                    "Connect the requested experience to the job with approved evidence.",
                    List.of("verified experience", "specific contribution"),
                    List.of("fabricated numbers"),
                    input.path("jobRequirements").isEmpty() ? List.of() : List.of(0));
        }

        private Object questionAnalysisV2(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            if (input.path("questionText").asText().contains(FAILURE_MARKER)
                    && forcedFailureAttempts
                                    .computeIfAbsent(questionId, ignored -> new AtomicInteger())
                                    .incrementAndGet()
                            <= 3) {
                return "{\"schemaVersion\":\"cover-generation-question-analysis-output-v2\"}";
            }
            JsonNode plan = input.path("plan");
            return new QuestionAnalysisOutputV2(
                    "cover-generation-question-analysis-output-v2",
                    questionId,
                    QuestionType.valueOf(plan.path("questionType").asText()),
                    "Connect verified experience to the requested role.",
                    "Answer with a concrete personal action.",
                    plan.path("coreMessage").asText(),
                    List.of("personal action", "specific contribution"),
                    List.of("fabricated numbers"),
                    NarrativeFramework.valueOf(plan.path("narrativeFramework").asText()),
                    15,
                    45,
                    25,
                    15,
                    "Explain the applicant's own decision and action.",
                    List.of("verified problem, decision, action, and result"),
                    input.path("requirements").isEmpty() ? List.of() : List.of(0),
                    "Relate the action to the role responsibility.",
                    "Use only the supplied posting context.",
                    "End with a grounded contribution direction.",
                    HeadingPolicy.valueOf(plan.path("headingPolicy").asText()));
        }

        private Object questionAnalysisV3(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            if (input.path("questionText").asText().contains(FAILURE_MARKER)
                    && forcedFailureAttempts
                                    .computeIfAbsent(questionId, ignored -> new AtomicInteger())
                                    .incrementAndGet()
                            <= 3) {
                return "{\"schemaVersion\":\"cover-generation-question-analysis-output-v3\"}";
            }
            JsonNode plan = input.path("plan");
            List<NarrativeSectionPlan> sections = new ArrayList<>();
            plan.path("narrativeSections").forEach(section -> sections.add(
                    new NarrativeSectionPlan(
                            NarrativeSectionType.valueOf(section.path("sectionType").asText()),
                            section.path("objective").asText(),
                            section.path("emphasisWeight").asInt())));
            return new QuestionAnalysisOutputV3(
                    "cover-generation-question-analysis-output-v3",
                    questionId,
                    QuestionType.valueOf(plan.path("questionType").asText()),
                    "문항 의도를 근거 중심으로 분석한다.",
                    "첫 문장에서 핵심 답변을 제시한다.",
                    plan.path("coreMessage").asText(),
                    List.of("개인 행동", "구체적 기여"),
                    List.of("근거 없는 수치"),
                    NarrativeFramework.valueOf(plan.path("narrativeFramework").asText()),
                    sections,
                    "지원자가 직접 판단하고 수행한 행동을 강조한다.",
                    List.of("문제, 결정, 행동, 결과가 검증된 근거"),
                    input.path("requirements").isEmpty() ? List.of() : List.of(0),
                    plan.path("roleConnection").isNull()
                            ? null
                            : plan.path("roleConnection").asText(),
                    plan.path("companyConnection").isNull()
                            ? null
                            : plan.path("companyConnection").asText(),
                    "검증된 기여 방향으로 마무리한다.",
                    HeadingPolicy.valueOf(plan.path("headingPolicy").asText()));
        }

        private ExperienceAllocationOutput experienceAllocation(JsonNode input) {
            List<ExperienceAllocation> allocations = new ArrayList<>();
            for (JsonNode candidate : input.path("candidates")) {
                allocations.add(new ExperienceAllocation(
                        UUID.fromString(candidate.path("questionId").asText()),
                        uuids(candidate.path("evidenceIds")),
                        "The same verified project is intentionally viewed from a distinct angle."));
            }
            return new ExperienceAllocationOutput(
                    "cover-generation-allocation-output-v1", allocations);
        }

        private ExperienceAllocationOutputV2 experienceAllocationV2(JsonNode input) {
            List<ExperienceAllocationV2> allocations = new ArrayList<>();
            for (JsonNode candidate : input.path("candidates")) {
                List<UUID> evidenceIds = new ArrayList<>();
                candidate.path("candidateEvidence").forEach(value -> evidenceIds.add(
                        UUID.fromString(value.path("evidenceId").asText())));
                allocations.add(new ExperienceAllocationV2(
                        UUID.fromString(candidate.path("questionId").asText()),
                        List.copyOf(evidenceIds),
                        "The bounded candidate is reused because it is the only verified fit.",
                        "Emphasize a distinct personal action for this question."));
            }
            return new ExperienceAllocationOutputV2(
                    "cover-generation-allocation-output-v2", allocations);
        }

        private WrittenAnswerOutput writtenAnswer(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            List<EvidenceClaimDraft> claims = new ArrayList<>();
            JsonNode firstEvidence = input.path("verifiedEvidence").get(0);
            if (firstEvidence != null) {
                claims.add(new EvidenceClaimDraft(
                        UUID.fromString(firstEvidence.path("id").asText()),
                        "The approved project evidence supports this answer."));
            }
            return new WrittenAnswerOutput(
                    "cover-generation-answer-output-v1",
                    questionId,
                    providerTipTap(
                            "I used the approved project evidence to analyze the role and design a stable API."),
                    claims);
        }

        private WrittenAnswerOutputV2 writtenAnswerV2(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            List<EvidenceClaimDraft> claims = new ArrayList<>();
            JsonNode firstEvidence = input.path("verifiedEvidence").get(0);
            if (firstEvidence != null) {
                claims.add(new EvidenceClaimDraft(
                        UUID.fromString(firstEvidence.path("id").asText()),
                        "The approved project evidence supports this answer."));
            }
            return new WrittenAnswerOutputV2(
                    "cover-generation-answer-output-v2",
                    questionId,
                    providerTipTap(
                            input.path("plan").path("coreMessage").asText()
                                    + ": I directly used the approved evidence to choose and implement a stable API design."),
                    claims);
        }

        private WrittenAnswerOutputV3 writtenAnswerV3(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            boolean technical = "TECHNICAL_PROJECT".equals(
                    input.path("plan").path("questionType").asText());
            String answer = technical
                    ? "기술 프로젝트에서 Spring Boot와 PostgreSQL의 트레이드오프를 검토해 안정적인 API를 구현했습니다."
                    : "검증된 프로젝트에서 요구사항을 분석하고 맡은 API를 안정적으로 구현해 직무 역량을 입증했습니다.";
            JsonNode firstEvidence = input.path("verifiedEvidence").get(0);
            List<EvidenceClaimDraftV3> claims = firstEvidence == null
                    ? List.of()
                    : List.of(new EvidenceClaimDraftV3(
                            UUID.fromString(firstEvidence.path("id").asText()),
                            answer,
                            ClaimType.ACHIEVEMENT));
            return new WrittenAnswerOutputV3(
                    "cover-generation-answer-output-v3",
                    questionId,
                    providerTipTap(answer),
                    claims);
        }

        private FactCheckAnswerOutput generatedAnswerFactCheck(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            List<UUID> evidenceIds = input.path("claims").isEmpty()
                    ? List.of()
                    : List.of(UUID.fromString(
                            input.path("claims").get(0).path("evidenceId").asText()));
            return new FactCheckAnswerOutput(
                    "cover-generation-fact-check-output-v1",
                    questionId,
                    List.of(),
                    List.of(),
                    List.of(new VerifiedClaimDraft(
                            "The answer is supported by approved project evidence.",
                            true,
                            evidenceIds)));
        }

        private FactCheckAnswerOutputV2 generatedAnswerFactCheckV2(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            List<UUID> evidenceIds = input.path("claims").isEmpty()
                    ? List.of()
                    : List.of(UUID.fromString(
                            input.path("claims").get(0).path("evidenceId").asText()));
            return new FactCheckAnswerOutputV2(
                    "cover-generation-fact-check-output-v2",
                    questionId,
                    List.of(),
                    List.of(),
                    List.of(new VerifiedClaimDraft(
                            "The answer is supported by approved project evidence.",
                            true,
                            evidenceIds)));
        }

        private FactCheckAnswerOutputV3 generatedAnswerFactCheckV3(JsonNode input) {
            UUID questionId = UUID.fromString(input.path("questionId").asText());
            JsonNode firstClaim = input.path("claims").get(0);
            List<VerifiedClaimDraftV3> claims = firstClaim == null
                    ? List.of()
                    : List.of(new VerifiedClaimDraftV3(
                            firstClaim.path("exactAnswerExcerpt").asText(),
                            true,
                            List.of(UUID.fromString(firstClaim.path("evidenceId").asText()))));
            return new FactCheckAnswerOutputV3(
                    "cover-generation-fact-check-output-v3",
                    questionId,
                    List.of(),
                    List.of(),
                    claims);
        }

        private FactCheckOutput verificationFacts(JsonNode input) {
            UUID answerVersionId =
                    UUID.fromString(input.path("answerVersionId").asText());
            String answerText = input.path("answerText").asText();
            UUID evidenceId = firstCurrentEvidence(input.path("currentVerifiedEvidence"));
            List<com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow
                            .VerificationIssueDraft>
                    issues;
            List<String> suggestions;
            if (answerText.contains(WARNING_MARKER)
                    && !answerText.contains(PASSED_MARKER)) {
                issues = List.of(new com.hiresemble.ai.workflow
                        .CoverLetterVerificationWorkflow.VerificationIssueDraft(
                        VerificationIssueCode.UNVERIFIED_CLAIM,
                        IssueSeverity.WARNING,
                        "Clarify this sentence with the approved evidence.",
                        WARNING_MARKER,
                        evidenceId == null ? List.of() : List.of(evidenceId)));
                suggestions = List.of(
                        "Rewritten with approved evidence. " + PASSED_MARKER);
            } else {
                issues = List.of();
                suggestions = List.of();
            }
            List<com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow
                            .VerifiedClaimDraft>
                    claims = evidenceId == null
                    ? List.of()
                    : List.of(new com.hiresemble.ai.workflow
                            .CoverLetterVerificationWorkflow.VerifiedClaimDraft(
                            "The current answer is supported by approved evidence.",
                            true,
                            List.of(evidenceId)));
            return new FactCheckOutput(
                    "cover-verification-facts-output-v1",
                    answerVersionId,
                    issues,
                    suggestions,
                    claims);
        }

        private FactCheckOutput verificationFactsV2(JsonNode input) {
            FactCheckOutput v1 = verificationFacts(input);
            return new FactCheckOutput(
                    "cover-verification-facts-output-v2",
                    v1.answerVersionId(),
                    v1.issues(),
                    v1.suggestions(),
                    v1.verifiedClaims());
        }

        private FactCheckOutput verificationFactsV3(JsonNode input) {
            UUID answerVersionId = UUID.fromString(input.path("answerVersionId").asText());
            String answerText = input.path("answer").path("boundedPlainText").asText();
            UUID evidenceId = firstCurrentEvidence(input.path("currentVerifiedEvidence"));
            List<com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.VerificationIssueDraft>
                    issues;
            List<String> suggestions;
            if (answerText.contains(WARNING_MARKER) && !answerText.contains(PASSED_MARKER)) {
                issues = List.of(new com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow
                        .VerificationIssueDraft(
                        VerificationIssueCode.UNVERIFIED_CLAIM,
                        IssueSeverity.WARNING,
                        "승인된 근거로 이 문장을 더 명확하게 설명해 주세요.",
                        WARNING_MARKER,
                        evidenceId == null ? List.of() : List.of(evidenceId)));
                suggestions = List.of("승인된 근거를 반영해 문장을 보완해 주세요. " + PASSED_MARKER);
            } else {
                issues = List.of();
                suggestions = List.of();
            }
            List<com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.VerifiedClaimDraft>
                    claims = evidenceId == null || answerText.isBlank()
                    ? List.of()
                    : List.of(new com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow
                            .VerifiedClaimDraft(answerText, true, List.of(evidenceId)));
            return new FactCheckOutput(
                    "cover-verification-facts-output-v3",
                    answerVersionId,
                    issues,
                    suggestions,
                    claims);
        }

        private RequirementCheckOutput verificationRequirements(JsonNode input) {
            return new RequirementCheckOutput(
                    "cover-verification-requirements-output-v1",
                    UUID.fromString(input.path("answerVersionId").asText()),
                    List.of(),
                    List.of());
        }

        private RequirementCheckOutput verificationRequirementsV2(JsonNode input) {
            return new RequirementCheckOutput(
                    "cover-verification-requirements-output-v2",
                    UUID.fromString(input.path("answerVersionId").asText()),
                    List.of(),
                    List.of());
        }

        private RequirementCheckOutput verificationRequirementsV3(JsonNode input) {
            return new RequirementCheckOutput(
                    "cover-verification-requirements-output-v3",
                    UUID.fromString(input.path("answerVersionId").asText()),
                    List.of(),
                    List.of());
        }

        private UUID firstCurrentEvidence(JsonNode values) {
            if (!values.isArray() || values.isEmpty()) {
                return null;
            }
            return UUID.fromString(values.get(0).path("id").asText());
        }

        private UUID firstUuid(JsonNode values, String field) {
            if (!values.isArray() || values.isEmpty()) {
                throw new AssertionError("P7 Fake requires one VERIFIED evidence value");
            }
            return UUID.fromString(values.get(0).path(field).asText());
        }

        private List<UUID> uuids(JsonNode values) {
            List<UUID> result = new ArrayList<>();
            values.forEach(value -> result.add(UUID.fromString(value.asText())));
            return List.copyOf(result);
        }

        private TipTapDocumentDto tipTap(String text) {
            return new TipTapDocumentDto(
                    "doc",
                    List.of(new TipTapNodeDto(
                            "paragraph",
                            null,
                            List.of(),
                            List.of(new TipTapNodeDto(
                                "text", text, List.of(), List.of())))));
        }

        private ProviderTipTapDocumentOutput providerTipTap(String text) {
            return new ProviderTipTapDocumentOutput(
                    "doc",
                    List.of(new ProviderTipTapNodeOutput(
                            "paragraph",
                            null,
                            List.of(),
                            List.of(new ProviderTipTapNodeOutput(
                                    "text", text, List.of(), List.of())))));
        }
    }

    static final class FakeP7EmbeddingGateway implements EmbeddingGateway {

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();

        FakeP7EmbeddingGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse embed(EmbeddingRequest request) {
            calls.incrementAndGet();
            if (request.maskedInputs().isEmpty() || request.dimension() < 1) {
                throw new AssertionError("P7 Fake embedding contract is invalid");
            }
            List<List<Double>> vectors = request.maskedInputs().stream()
                    .map(value -> deterministicVector(value, request.dimension()))
                    .toList();
            try {
                return new AiGatewayResponse(
                        objectMapper.writeValueAsString(
                                new DocumentIngestionWorkflow.EmbeddingValuesOutput(vectors)),
                        java.util.List.of());
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Fake P7 embedding serialization failed", exception);
            }
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }

        private List<Double> deterministicVector(String value, int dimension) {
            List<Double> vector = new ArrayList<>(dimension);
            double first = (Math.floorMod(value.hashCode(), 997) + 1) / 997.0;
            double second = (Math.floorMod(value.length(), 991) + 1) / 991.0;
            for (int index = 0; index < dimension; index++) {
                vector.add(index == 0 ? first : index == 1 ? second : 0.0);
            }
            return Collections.unmodifiableList(vector);
        }
    }
}
