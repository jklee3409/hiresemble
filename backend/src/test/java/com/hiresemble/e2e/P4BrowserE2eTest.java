package com.hiresemble.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.domain.UsageType;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.workflow.document.DocumentIngestionWorkflow;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
@Import(P4BrowserE2eTest.FakeAiConfiguration.class)
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=true")
class P4BrowserE2eTest extends PostgresIntegrationTest {

    private static final String ACCESS_KEY = "hiresemble";
    private static final String SECRET_KEY = "hiresemble-local-secret";
    private static final String BUCKET = "hiresemble-p4-browser";
    private static final long FAKE_PRICE_VERSION = 900_000_002L;
    private static final UUID FAKE_EMBEDDING_PRICE_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-900000000201");
    private static final UUID FAKE_CHAT_PRICE_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-900000000202");
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

    @Autowired private FakeEmbeddingGateway embeddingGateway;
    @Autowired private FakeChatGateway chatGateway;

    @DynamicPropertySource
    static void p4Environment(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.object-storage.endpoint", () -> MINIO_ENDPOINT);
        registry.add("hiresemble.object-storage.access-key", () -> ACCESS_KEY);
        registry.add("hiresemble.object-storage.secret-key", () -> SECRET_KEY);
        registry.add("hiresemble.object-storage.bucket", () -> BUCKET);
        registry.add("hiresemble.object-storage.region", () -> "us-east-1");
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-document-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-document-balanced");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.document.ai-cost.estimated-cost-usd", () -> "0.100000");
        registry.add("hiresemble.document.ai-cost.price-version", () -> FAKE_PRICE_VERSION);
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "100ms");
        registry.add("hiresemble.agent-runtime.reconciliation-interval", () -> "1s");
        registry.add("hiresemble.agent-runtime.heartbeat-interval", () -> "1s");
        registry.add("hiresemble.object-deletion-outbox.scan-interval", () -> "100ms");
    }

    @BeforeEach
    void seedFakePolicies() {
        jdbcTemplate.update("""
                INSERT INTO ai_price_versions (
                    id,version,catalog_key,effective_from,effective_to,created_at
                ) VALUES (?,?,?,now(),NULL,now())
                ON CONFLICT (version) DO NOTHING
                """, UUID.randomUUID(), FAKE_PRICE_VERSION, "fake-document-browser-p4");
        jdbcTemplate.update("""
                INSERT INTO ai_price_items (
                    id,price_version,provider_key,product_key,unit,unit_size,unit_price_usd,created_at
                ) VALUES (?,?,'fake','fake-document-embedding','EMBEDDING_INPUT_TOKEN',1,0.000000,now())
                ON CONFLICT (price_version,provider_key,product_key,unit) DO NOTHING
                """, FAKE_EMBEDDING_PRICE_ITEM_ID, FAKE_PRICE_VERSION);
        jdbcTemplate.update("""
                INSERT INTO ai_price_items (
                    id,price_version,provider_key,product_key,unit,unit_size,unit_price_usd,created_at
                ) VALUES (?,?,'fake','fake-document-chat','CHAT_INPUT_TOKEN',1,0.000000,now())
                ON CONFLICT (price_version,provider_key,product_key,unit) DO NOTHING
                """, FAKE_CHAT_PRICE_ITEM_ID, FAKE_PRICE_VERSION);
        embeddingGateway.reset();
        chatGateway.reset();
    }

    @AfterAll
    static void stopIsolatedStorage() {
        STORAGE_ADMIN.close();
        MINIO.stop();
    }

    @Test
    @Timeout(value = 9, unit = TimeUnit.MINUTES)
    void actualSpringSseMinioVueAndChromiumPipeline() throws Exception {
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
                "e2e/documents.actual.spec.ts",
                "--project=chromium",
                "--workers=1",
                "--reporter=line");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("P4_E2E_ENABLED", "true");
        builder.environment().put("P4_FRONTEND_PORT", Integer.toString(frontendPort));
        builder.environment().put(
                "P4_FRONTEND_BASE_URL", "http://127.0.0.1:" + frontendPort);
        builder.environment().put(
                "VITE_API_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
        builder.environment().put("PLAYWRIGHT_HTML_OPEN", "never");

        Process browser = builder.start();
        boolean finished = browser.waitFor(8, TimeUnit.MINUTES);
        if (!finished) {
            browser.destroyForcibly();
            throw new AssertionError("P4 Playwright process exceeded eight minutes");
        }
        assertThat(browser.exitValue()).isZero();

        assertThat(embeddingGateway.calls).isPositive();
        assertThat(chatGateway.calls).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM documents
                WHERE parse_status='PARSED' AND evidence_extraction_status='SUCCEEDED'
                  AND manual_text_provided
                """, Long.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM documents
                WHERE parse_status='PARSED' AND evidence_extraction_status='FAILED'
                  AND deleted_at IS NULL
                """, Long.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunks
                WHERE embedding IS NOT NULL AND vector_dims(embedding)=1536
                """, Long.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM object_deletion_outbox
                WHERE reason='DOCUMENT_DELETE' AND status='SUCCEEDED'
                """, Long.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM agent_runs
                WHERE workflow_type='DOCUMENT_INGESTION' AND status='FAILED'
                  AND error_code IS NOT NULL AND error_message_safe IS NOT NULL
                """, Long.class)).isPositive();
    }

    private Path frontendDirectory() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path direct = working.resolve("frontend");
        if (Files.isRegularFile(direct.resolve("package.json"))) return direct;
        Path sibling = working.resolveSibling("frontend");
        if (Files.isRegularFile(sibling.resolve("package.json"))) return sibling;
        throw new IllegalStateException("frontend/package.json could not be located from " + working);
    }

    private int availablePort() throws java.io.IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfiguration {

        @Bean
        @Primary
        FakeEmbeddingGateway p4BrowserEmbeddingGateway(ObjectMapper objectMapper) {
            return new FakeEmbeddingGateway(objectMapper);
        }

        @Bean
        @Primary
        FakeChatGateway p4BrowserChatGateway(ObjectMapper objectMapper) {
            return new FakeChatGateway(objectMapper);
        }
    }

    static final class FakeEmbeddingGateway implements EmbeddingGateway {
        private final ObjectMapper objectMapper;
        private int calls;

        FakeEmbeddingGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public synchronized AiGatewayResponse embed(EmbeddingRequest request) {
            calls++;
            boolean failure = request.maskedInputs().stream()
                    .anyMatch(value -> value.contains("FORCE_EMBEDDING_FAILURE"));
            int dimension = failure ? 32 : request.dimension();
            List<List<Double>> vectors = request.maskedInputs().stream()
                    .map(value -> deterministicVector(value, dimension))
                    .toList();
            try {
                return new AiGatewayResponse(
                        objectMapper.writeValueAsString(
                                new DocumentIngestionWorkflow.EmbeddingValuesOutput(vectors)),
                        usage(UsageType.EMBEDDING, "fake-document-embedding", 1));
            } catch (Exception exception) {
                throw new IllegalStateException("Fake embedding serialization failed", exception);
            }
        }

        private List<Double> deterministicVector(String value, int dimension) {
            List<Double> vector = new ArrayList<>(dimension);
            double first = (Math.floorMod(value.hashCode(), 997) + 1) / 997.0;
            double second = (Math.floorMod(value.length(), 991) + 1) / 991.0;
            for (int index = 0; index < dimension; index++) {
                vector.add(index == 0 ? first : index == 1 ? second : 0.0);
            }
            return List.copyOf(vector);
        }

        synchronized void reset() {
            calls = 0;
        }
    }

    static final class FakeChatGateway implements ChatGateway {
        private final ObjectMapper objectMapper;
        private int calls;

        FakeChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public synchronized AiGatewayResponse chat(ChatRequest request) {
            calls++;
            JsonNode first = request.input().path("maskedChunks").get(0);
            String grounded = first.path("maskedContent").asText();
            if (grounded.length() > 600) grounded = grounded.substring(0, 600);
            var candidate = new DocumentIngestionWorkflow.EvidenceCandidatePayload(
                    "PROJECT",
                    "검토 대기 근거",
                    grounded,
                    Map.of("source", "p4-browser-fake"),
                    new BigDecimal("0.900"),
                    List.of(UUID.fromString(first.path("chunkId").asText())),
                    request.input().path("sourceRevision").asLong(),
                    null);
            var batch = new DocumentIngestionWorkflow.EvidenceCandidateBatch(
                    UUID.fromString(request.input().path("documentId").asText()),
                    request.input().path("sourceRevision").asLong(),
                    List.of(candidate));
            try {
                return new AiGatewayResponse(
                        objectMapper.writeValueAsString(batch),
                        usage(UsageType.CHAT, "fake-document-chat", 1));
            } catch (Exception exception) {
                throw new IllegalStateException("Fake chat serialization failed", exception);
            }
        }

        synchronized void reset() {
            calls = 0;
        }
    }

    private static AiUsage usage(UsageType type, String product, long units) {
        return new AiUsage(
                type,
                "fake",
                product,
                type == UsageType.CHAT ? units : 0,
                0,
                0,
                type == UsageType.EMBEDDING ? units : 0,
                0,
                FAKE_PRICE_VERSION,
                type == UsageType.EMBEDDING ? FAKE_EMBEDDING_PRICE_ITEM_ID : FAKE_CHAT_PRICE_ITEM_ID,
                BigDecimal.ZERO.setScale(6),
                1);
    }
}
