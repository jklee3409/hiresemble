package com.hiresemble.ai.workflow.document;

import static org.assertj.core.api.Assertions.assertThat;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.ai.orchestration.AgentOrchestrator;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.document.application.service.DocumentApplicationService;
import com.hiresemble.document.application.port.ObjectStorageException;
import com.hiresemble.document.application.port.ObjectStoragePort;
import com.hiresemble.document.domain.model.DocumentParseStatus;
import com.hiresemble.document.domain.model.DocumentType;
import com.hiresemble.document.domain.model.EvidenceExtractionStatus;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@Import(DocumentIngestionOrchestratorIntegrationTest.FakePorts.class)
@TestPropertySource(properties = {
        "hiresemble.ai.runtime.enabled=true",
        "hiresemble.document.ai-cost.estimated-cost-usd=0.300000",
        "hiresemble.document.ai-cost.price-version=2026073101"
})
class DocumentIngestionOrchestratorIntegrationTest extends PostgresIntegrationTest {

    @Autowired private DocumentApplicationService documentService;
    @Autowired private AgentRunStatePort runState;
    @Autowired private AgentRunQueryPort runQuery;
    @Autowired private AgentOrchestrator orchestrator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FakeStorage storage;
    @Autowired private FakeEmbeddingGateway embeddingGateway;
    @Autowired private FakeChatGateway chatGateway;

    private UUID userId;

    @DynamicPropertySource
    static void workflowProperties(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-balanced");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.object-deletion-outbox.scan-interval", () -> "1h");
    }

    @BeforeEach
    void setUpDocumentFixture() {
        jdbcTemplate.update("""
                INSERT INTO ai_model_policies (id,version,policy_json,active,created_at)
                VALUES ('00000000-0000-0000-0000-000000000501',1,'{}',true,now())
                ON CONFLICT (version) DO NOTHING
                """);
        userId = seedUser();
        storage.values.clear();
        embeddingGateway.reset();
        chatGateway.reset();
    }

    @Test
    void actualEightStepRunUsesOnlyMaskedGatewayInputsAndCreatesPendingEvidence() {
        String raw = longDocument("owner@example.com", "api_key=super-secret-value");
        var accepted = upload(raw, "document-ai-success-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        var document = documentService.detail(userId, accepted.documentId());
        assertThat(run.status()).withFailMessage(run::toString)
                .isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run.progressPercent()).isEqualTo(100);
        assertThat(run.steps()).extracting(step -> step.stepKey())
                .containsExactly(
                        DocumentIngestionWorkflow.LOAD_DOCUMENT_SOURCE,
                        DocumentIngestionWorkflow.EXTRACT_OR_ACCEPT_TEXT,
                        DocumentIngestionWorkflow.MASK_TEXT,
                        DocumentIngestionWorkflow.CHUNK_TEXT,
                        DocumentIngestionWorkflow.EMBED_CHUNKS,
                        DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES,
                        DocumentIngestionWorkflow.APPLY_EVIDENCE_CANDIDATES,
                        DocumentIngestionWorkflow.FINALIZE_DOCUMENT);
        assertThat(document.parseStatus()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(document.evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.SUCCEEDED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id=? AND vector_dims(embedding)=1536",
                Long.class, document.id())).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=? AND verification_status='PENDING' AND verified_at IS NULL",
                Long.class, document.id())).isEqualTo(1L);
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM profile_evidence WHERE document_id=?",
                String.class, document.id());
        assertThat(metadata).isEqualTo("{}").doesNotContain("validationWarning");
        assertThat(run.partialResult()).isNotNull();
        assertThat(run.partialResult().resultRefs()).hasSize(1);

        assertThat(embeddingGateway.capturedMaskedInputs).isNotEmpty()
                .allSatisfy(value -> assertThat(value)
                        .doesNotContain("owner@example.com", "super-secret-value"));
        assertThat(chatGateway.lastInput)
                .doesNotContain(
                        "owner@example.com", "super-secret-value", "api_key=",
                        "documentId", "sourceRevision", "chunkId")
                .contains("chunkRef", "C1", "maxCandidates");
        String checkpoints = jdbcTemplate.queryForObject(
                """
                SELECT coalesce(string_agg(input_refs::text || coalesce(output_json::text,''), ' '),'')
                FROM agent_steps WHERE agent_run_id=?
                """,
                String.class,
                run.id());
        assertThat(checkpoints).doesNotContain(
                "owner@example.com", "super-secret-value", raw);
    }

    @Test
    void partialCandidateRejectionIsSafeFilteringAndRunStillSucceeds() {
        chatGateway.mode = ChatMode.PARTIAL_REJECTION;
        var accepted = upload(largeDocumentForCandidates(), "document-partial-filter-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        var document = documentService.detail(userId, accepted.documentId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run.progressPercent()).isEqualTo(100);
        assertThat(run.safeError()).isNull();
        assertThat(run.steps()).allSatisfy(step ->
                assertThat(step.status().name()).isEqualTo("SUCCEEDED"));
        assertThat(document.parseStatus()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(document.evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.SUCCEEDED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=?",
                Long.class, accepted.documentId())).isEqualTo(4L);
        assertApplySummary(accepted.agentRunId(), 6, 4, 2);
        assertThat(run.partialResult()).isNotNull();
        assertThat(run.partialResult().succeededScopeKeys()).isEmpty();
        assertThat(run.partialResult().failedScopeKeys()).isEmpty();
        assertThat(run.partialResult().resultRefs()).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_json->'rejectionReasonCounts'->>'EDUCATION_CATEGORY' FROM agent_steps WHERE agent_run_id=? AND step_key='APPLY_EVIDENCE_CANDIDATES'",
                String.class, accepted.agentRunId())).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_json->'rejectionReasonCounts'->>'DUPLICATE' FROM agent_steps WHERE agent_run_id=? AND step_key='APPLY_EVIDENCE_CANDIDATES'",
                String.class, accepted.agentRunId())).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE id=? AND error_code='COVER_LETTER_GENERATION_PARTIAL_FAILURE'",
                Long.class, accepted.agentRunId())).isZero();
    }

    @Test
    void allCandidatesAppliedProducesSuccessWithoutRejectionReasons() {
        chatGateway.mode = ChatMode.ALL_APPLIED;
        var accepted = upload(largeDocumentForCandidates(), "document-all-applied-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=?",
                Long.class, accepted.documentId())).isEqualTo(4L);
        assertApplySummary(accepted.agentRunId(), 4, 4, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_json->'rejectionReasonCounts' = '{}'::jsonb FROM agent_steps WHERE agent_run_id=? AND step_key='APPLY_EVIDENCE_CANDIDATES'",
                Boolean.class, accepted.agentRunId())).isTrue();
    }

    @Test
    void allCandidatesRejectedStillCompletesDocumentWithNoEvidence() {
        chatGateway.mode = ChatMode.ALL_REJECTED;
        var accepted = upload(largeDocumentForCandidates(), "document-all-rejected-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        var document = documentService.detail(userId, accepted.documentId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(run.progressPercent()).isEqualTo(100);
        assertThat(run.safeError()).isNull();
        assertThat(document.parseStatus()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(document.evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.SUCCEEDED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=?",
                Long.class, accepted.documentId())).isZero();
        assertApplySummary(accepted.agentRunId(), 3, 0, 3);
        assertThat(run.partialResult()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_json->'rejectionReasonCounts'->>'EDUCATION_CATEGORY' FROM agent_steps WHERE agent_run_id=? AND step_key='APPLY_EVIDENCE_CANDIDATES'",
                String.class, accepted.agentRunId())).isEqualTo("2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_json->'rejectionReasonCounts'->>'UNGROUNDED_NUMBER' FROM agent_steps WHERE agent_run_id=? AND step_key='APPLY_EVIDENCE_CANDIDATES'",
                String.class, accepted.agentRunId())).isEqualTo("1");
    }

    @Test
    void shortTextWaitsWithoutGatewayAndManualTextResumesSameRun() {
        var accepted = upload("짧은 문서", "document-ai-wait-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot waiting = run(accepted.agentRunId());
        assertThat(waiting.status()).isEqualTo(AgentRunStatus.WAITING_USER);
        assertThat(waiting.requiredUserAction().type().name())
                .isEqualTo("PROVIDE_DOCUMENT_TEXT");
        assertThat(documentService.detail(userId, accepted.documentId()).parseStatus())
                .isEqualTo(DocumentParseStatus.NEEDS_MANUAL_TEXT);
        assertThat(embeddingGateway.calls.get()).isZero();
        assertThat(chatGateway.calls.get()).isZero();

        long version = documentService.detail(userId, accepted.documentId()).version();
        var resumed = documentService.manualText(
                userId,
                accepted.documentId(),
                longDocument("manual@example.com", "password=manual-secret"),
                version,
                "document-manual-resume-key").body();
        assertThat(resumed.agentRunId()).isEqualTo(accepted.agentRunId());

        execute(resumed.agentRunId());

        AgentRunSnapshot completed = run(resumed.agentRunId());
        var document = documentService.detail(userId, accepted.documentId());
        assertThat(completed.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(document.sourceRevision()).isEqualTo(2);
        assertThat(document.manualTextProvided()).isTrue();
        assertThat(document.parseStatus()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(document.evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.SUCCEEDED);
        assertThat(completed.steps().stream()
                .filter(step -> step.stepKey().equals(
                        DocumentIngestionWorkflow.EXTRACT_OR_ACCEPT_TEXT)))
                .hasSize(2);
    }

    @Test
    void invalidEmbeddingKeepsParsedTextAndFailsOnlyEvidenceAxisSafely() {
        embeddingGateway.invalidDimension.set(true);
        String raw = longDocument("failure@example.com", "secret=failure-secret-value");
        var accepted = upload(raw, "document-ai-partial-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        var document = documentService.detail(userId, accepted.documentId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.retryable()).isFalse();
        assertThat(run.safeError().message()).doesNotContain(
                "failure@example.com", "failure-secret-value");
        assertThat(document.parseStatus()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(document.evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.FAILED);
        assertThat(documentService.text(userId, document.id()).extractedText()).isEqualTo(raw);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id=?",
                Long.class, document.id())).isPositive();
        assertThat(chatGateway.calls.get()).isZero();
        assertThat(embeddingGateway.calls.get()).isEqualTo(1);
    }

    @Test
    void unparseableOutputStopsAfterOnePaidAttemptAndPreservesDeterministicArtifacts() {
        chatGateway.mode = ChatMode.MALFORMED_JSON;
        var accepted = upload(longDocument("masked@example.com", "secret=masked"),
                "document-invalid-json-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.retryable()).isFalse();
        assertThat(run.safeError().code()).isEqualTo("AI_SO_JSON_NOT_PARSEABLE");
        assertThat(chatGateway.calls.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_usage_records
                WHERE agent_run_id=? AND usage_type='CHAT'
                """, Long.class, accepted.agentRunId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actual_cost_usd FROM agent_runs WHERE id=?",
                BigDecimal.class, accepted.agentRunId())).isEqualByComparingTo("0.001000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_budget_reservations WHERE agent_run_id=?",
                String.class, accepted.agentRunId())).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=?",
                Long.class, accepted.documentId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id=? AND embedding IS NOT NULL",
                Long.class, accepted.documentId())).isPositive();
        assertThat(documentService.text(userId, accepted.documentId()).maskedText()).isNotBlank();
    }

    @Test
    void unknownLocalRefGetsOneGuidedRepairAttemptThenSucceedsWithBothUsages() {
        chatGateway.mode = ChatMode.UNKNOWN_REF_ONCE;
        var accepted = upload(longDocument("repair@example.com", "secret=repair"),
                "document-source-ref-repair-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(chatGateway.calls.get()).isEqualTo(2);
        assertThat(chatGateway.lastInstructions)
                .contains("outside the supplied maskedChunks allowlist")
                .doesNotContain("C999");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_usage_records
                WHERE agent_run_id=? AND usage_type='CHAT'
                """, Long.class, accepted.agentRunId())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actual_cost_usd FROM agent_runs WHERE id=?",
                BigDecimal.class, accepted.agentRunId())).isEqualByComparingTo("0.002000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=?",
                Long.class, accepted.documentId())).isEqualTo(1L);
    }

    @Test
    void repeatedUnknownLocalRefStopsAfterTheSingleRepairAttemptAndKeepsLastReason() {
        chatGateway.mode = ChatMode.UNKNOWN_REF_ALWAYS;
        var accepted = upload(longDocument("bounded@example.com", "secret=bounded"),
                "document-source-ref-bounded-key");

        execute(accepted.agentRunId());

        AgentRunSnapshot run = run(accepted.agentRunId());
        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.safeError().code())
                .isEqualTo("AI_SO_WORKFLOW_DOCUMENT_SOURCE_REF_UNKNOWN");
        assertThat(run.steps().stream()
                .filter(step -> step.stepKey().equals(
                        DocumentIngestionWorkflow.EXTRACT_EVIDENCE_CANDIDATES)))
                .hasSize(2)
                .allSatisfy(step -> assertThat(step.safeError().code())
                        .isEqualTo("AI_SO_WORKFLOW_DOCUMENT_SOURCE_REF_UNKNOWN"));
        assertThat(chatGateway.calls.get()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM ai_usage_records
                WHERE agent_run_id=? AND usage_type='CHAT'
                """, Long.class, accepted.agentRunId())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actual_cost_usd FROM agent_runs WHERE id=?",
                BigDecimal.class, accepted.agentRunId())).isEqualByComparingTo("0.002000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE document_id=?",
                Long.class, accepted.documentId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id=? AND embedding IS NOT NULL",
                Long.class, accepted.documentId())).isPositive();
    }

    @Test
    void providerWarningIsMappedIntoTheExistingMetadataObject() {
        chatGateway.warning = "근거 확인 필요";
        var accepted = upload(longDocument("masked@example.com", "secret=masked"),
                "document-warning-metadata-key");

        execute(accepted.agentRunId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT metadata->>'validationWarning' FROM profile_evidence WHERE document_id=?",
                String.class, accepted.documentId())).isEqualTo("근거 확인 필요");
    }

    private com.hiresemble.document.application.model.DocumentApplicationResults.UploadAccepted upload(
            String text, String key) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                text.getBytes(StandardCharsets.UTF_8));
        return documentService.upload(userId, file, DocumentType.RESUME, "Resume", key).body();
    }

    private void execute(UUID runId) {
        var claimed = runState.claim(
                        runId, "document-test-worker", Instant.now(), Duration.ofSeconds(60))
                .orElseThrow();
        orchestrator.execute(claimed);
    }

    private AgentRunSnapshot run(UUID runId) {
        return runQuery.findByOwner(userId, runId).orElseThrow();
    }

    private void assertApplySummary(
            UUID runId, int candidateCount, int appliedCount, int rejectedCount) {
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT
                    (output_json->>'candidateCount')::integer AS candidate_count,
                    (output_json->>'appliedCount')::integer AS applied_count,
                    (output_json->>'rejectedCount')::integer AS rejected_count
                FROM agent_steps
                WHERE agent_run_id=? AND step_key='APPLY_EVIDENCE_CANDIDATES'
                """, runId);
        assertThat(summary)
                .containsEntry("candidate_count", candidateCount)
                .containsEntry("applied_count", appliedCount)
                .containsEntry("rejected_count", rejectedCount);
    }

    private UUID seedUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,?,'Document AI','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """, id, "document-ai-" + id + "@example.test", "hash");
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

    private String longDocument(String email, String credential) {
        return ("프로젝트에서 백엔드 기능을 설계하고 테스트 자동화를 개선했습니다. "
                + "협업 과정에서 문제를 분석하고 안정적인 결과를 만들었습니다. "
                + email + " " + credential + " ").repeat(4);
    }

    private String largeDocumentForCandidates() {
        return longDocument("candidate@example.test", "safe synthetic material").repeat(12);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakePorts {

        @Bean
        @Primary
        FakeStorage fakeDocumentStorage() {
            return new FakeStorage();
        }

        @Bean
        @Primary
        FakeEmbeddingGateway fakeDocumentEmbedding(ObjectMapper objectMapper) {
            return new FakeEmbeddingGateway(objectMapper);
        }

        @Bean
        @Primary
        FakeChatGateway fakeDocumentChat(ObjectMapper objectMapper) {
            return new FakeChatGateway(objectMapper);
        }

        @Bean
        @Primary
        EvidenceReferenceQueryPort testNoEvidenceReferences() {
            return (userId, evidenceId) -> false;
        }

        @Bean
        @Primary
        AgentRunDispatchPort synchronousTestDispatchBoundary() {
            return new AgentRunDispatchPort() {
                @Override
                public void enqueue(UUID agentRunId) {}

                @Override
                public void scanQueued() {}
            };
        }
    }

    static final class FakeEmbeddingGateway implements EmbeddingGateway {
        private final ObjectMapper objectMapper;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean invalidDimension = new AtomicBoolean();
        final List<String> capturedMaskedInputs = new ArrayList<>();

        FakeEmbeddingGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse embed(EmbeddingRequest request) {
            calls.incrementAndGet();
            capturedMaskedInputs.addAll(request.maskedInputs());
            int dimension = invalidDimension.get() ? 32 : request.dimension();
            List<List<Double>> vectors = request.maskedInputs().stream().map(input -> {
                List<Double> vector = new ArrayList<>(dimension);
                double seed = (Math.floorMod(input.hashCode(), 1000) + 1) / 1000.0;
                for (int index = 0; index < dimension; index++) {
                    vector.add(index == 0 ? seed : 0.0);
                }
                return List.copyOf(vector);
            }).toList();
            try {
                return new AiGatewayResponse(
                        objectMapper.writeValueAsString(
                                new DocumentIngestionWorkflow.EmbeddingValuesOutput(vectors)),
                        usage(UsageType.EMBEDDING, "fake-embedding"));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        void reset() {
            calls.set(0);
            invalidDimension.set(false);
            capturedMaskedInputs.clear();
        }
    }

    static final class FakeChatGateway implements ChatGateway {
        private final ObjectMapper objectMapper;
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastInput = "";
        volatile String lastInstructions = "";
        volatile String warning;
        volatile ChatMode mode = ChatMode.VALID;

        FakeChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            int call = calls.incrementAndGet();
            lastInput = request.input().toString();
            lastInstructions = request.instructions();
            if (mode == ChatMode.MALFORMED_JSON) {
                return new AiGatewayResponse("{not-json", paidChatUsage());
            }
            String sourceRef = request.input().path("maskedChunks").get(0)
                    .path("chunkRef").asText();
            if (mode == ChatMode.UNKNOWN_REF_ALWAYS
                    || (mode == ChatMode.UNKNOWN_REF_ONCE && call == 1)) {
                sourceRef = "C999";
            }
            var candidate = new DocumentIngestionWorkflow.EvidenceCandidatePayload(
                    "PROJECT",
                    "백엔드 프로젝트 수행",
                    "백엔드 기능을 설계하고 테스트 자동화를 개선했습니다.",
                    new BigDecimal("0.900"),
                    List.of(sourceRef),
                    warning);
            List<DocumentIngestionWorkflow.EvidenceCandidatePayload> candidates = switch (mode) {
                case ALL_APPLIED -> validCandidates(sourceRef, 4);
                case PARTIAL_REJECTION -> {
                    List<DocumentIngestionWorkflow.EvidenceCandidatePayload> values =
                            new ArrayList<>(validCandidates(sourceRef, 4));
                    values.add(candidate(
                            sourceRef, "EDUCATION_HISTORY", "학력 근거", "교육 이력"));
                    values.add(values.getFirst());
                    yield List.copyOf(values);
                }
                case ALL_REJECTED -> List.of(
                        candidate(sourceRef, "EDUCATION", "학력 근거 1", "교육 이력"),
                        candidate(sourceRef, "EDUCATION_HISTORY", "학력 근거 2", "학교 이력"),
                        candidate(sourceRef, "PROJECT", "근거 없는 수치", "성과 999"));
                default -> List.of(candidate);
            };
            var output = new DocumentIngestionWorkflow.EvidenceCandidateBatch(candidates);
            try {
                return new AiGatewayResponse(
                        objectMapper.writeValueAsString(output),
                        paidChatUsage());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        private List<DocumentIngestionWorkflow.EvidenceCandidatePayload> validCandidates(
                String sourceRef, int count) {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(index -> candidate(
                            sourceRef,
                            "PROJECT",
                            "프로젝트 근거 " + (char) ('A' + index),
                            "검증 가능한 프로젝트 수행 내용 " + (char) ('A' + index)))
                    .toList();
        }

        private DocumentIngestionWorkflow.EvidenceCandidatePayload candidate(
                String sourceRef, String category, String title, String content) {
            return new DocumentIngestionWorkflow.EvidenceCandidatePayload(
                    category,
                    title,
                    content,
                    new BigDecimal("0.900"),
                    List.of(sourceRef),
                    null);
        }

        void reset() {
            calls.set(0);
            lastInput = "";
            lastInstructions = "";
            warning = null;
            mode = ChatMode.VALID;
        }

        private List<AiUsage> paidChatUsage() {
            return List.of(new AiUsage(
                    UsageType.CHAT, "fake", "fake-chat", 0, 0, 500, 0, 0,
                    2026073101L,
                    UUID.fromString("85000000-0000-4000-8000-000000000103"),
                    new BigDecimal("0.001000"), 1, UUID.randomUUID()));
        }
    }

    enum ChatMode {
        VALID,
        MALFORMED_JSON,
        UNKNOWN_REF_ONCE,
        UNKNOWN_REF_ALWAYS,
        ALL_APPLIED,
        PARTIAL_REJECTION,
        ALL_REJECTED
    }

    static AiUsage usage(UsageType type, String product) {
        return new AiUsage(
                type, "fake", product, 0, 0, 0, type == UsageType.EMBEDDING ? 1 : 0,
                0, null, null, BigDecimal.ZERO.setScale(6), 1);
    }

    static final class FakeStorage implements ObjectStoragePort {
        final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public void upload(String key, byte[] content, String mimeType, String checksum) {
            values.put(key, content.clone());
        }

        @Override
        public byte[] read(String key) {
            byte[] value = values.get(key);
            if (value == null) throw failure();
            return value.clone();
        }

        @Override
        public void delete(String key) {
            values.remove(key);
        }

        @Override
        public ObjectMetadata metadata(String key) {
            byte[] value = values.get(key);
            if (value == null) throw failure();
            return new ObjectMetadata(value.length, "text/plain", "0".repeat(64));
        }

        @Override
        public PresignedObject presignGet(String key, Duration ttl) {
            if (!values.containsKey(key)) throw failure();
            return new PresignedObject(URI.create("https://storage.test/safe"), Instant.now().plus(ttl));
        }

        private ObjectStorageException failure() {
            return new ObjectStorageException(new IllegalStateException("fake unavailable"));
        }
    }
}
