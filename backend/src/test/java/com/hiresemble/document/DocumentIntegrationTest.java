package com.hiresemble.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.auth.api.SignupRequest;
import com.hiresemble.agentrun.application.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.BudgetReservationPort;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.document.application.DocumentEvidenceCandidate;
import com.hiresemble.document.application.DocumentWorkflowService;
import com.hiresemble.document.application.ObjectDeletionOutboxService;
import com.hiresemble.document.application.ObjectStorageException;
import com.hiresemble.document.application.ObjectStoragePort;
import com.hiresemble.document.domain.DocumentParseStatus;
import com.hiresemble.document.domain.DocumentRecords.DocumentChunkRecord;
import com.hiresemble.document.domain.DocumentType;
import com.hiresemble.document.domain.EvidenceExtractionStatus;
import com.hiresemble.document.infrastructure.DocumentFileInspector;
import com.hiresemble.document.infrastructure.DocumentStore;
import com.hiresemble.profile.application.EvidenceReferenceQueryPort;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(DocumentIntegrationTest.FakePorts.class)
class DocumentIntegrationTest extends PostgresIntegrationTest {

    private static final long FAKE_PRICE_VERSION = 900_000_001L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FakeStorage storage;
    @Autowired private FakeReferences references;
    @Autowired private DocumentWorkflowService workflow;
    @Autowired private DocumentStore documentStore;
    @Autowired private ObjectDeletionOutboxService outbox;
    @Autowired private BudgetReservationPort budgetReservations;

    @DynamicPropertySource
    static void slowerBackgroundScans(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.object-deletion-outbox.scan-interval", () -> "1h");
        registry.add("hiresemble.document.ai-cost.estimated-cost-usd", () -> "0.100000");
        registry.add("hiresemble.document.ai-cost.price-version", () -> FAKE_PRICE_VERSION);
    }

    @BeforeEach
    void resetFakes() {
        storage.reset();
        references.referenced.set(false);
        jdbcTemplate.update("""
                INSERT INTO ai_price_versions (
                    id,version,catalog_key,effective_from,effective_to,created_at
                ) VALUES (?,?,?,now(),NULL,now())
                ON CONFLICT (version) DO NOTHING
                """, UUID.randomUUID(), FAKE_PRICE_VERSION, "fake-document-p4");
    }

    @Test
    void uploadIsIdempotentOwnerScopedTypedAndDownloadUsesFiveMinutePresign() throws Exception {
        Session owner = authenticated("document-owner@example.com");
        Session other = authenticated("document-other@example.com");
        byte[] content = longText("owner@example.com").getBytes(StandardCharsets.UTF_8);

        JsonNode accepted = upload(owner, "document-upload-key-01", "resume.txt", content, 202);
        UUID documentId = UUID.fromString(accepted.get("documentId").asText());
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());
        assertThat(accepted.get("parseStatus").asText()).isEqualTo("UPLOADED");
        assertThat(accepted.get("evidenceExtractionStatus").asText()).isEqualTo("NOT_STARTED");
        assertThat(accepted.get("status").asText()).isEqualTo("QUEUED");

        String expectedKey = "users/" + owner.userId() + "/documents/" + documentId + "/content";
        assertThat(storage.values).containsKey(expectedKey);
        assertThat(storage.uploads.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT storage_key FROM documents WHERE id=?", String.class, documentId))
                .isEqualTo(expectedKey);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT resource_type || ':' || resource_id FROM agent_runs WHERE id=?",
                String.class, runId)).isEqualTo("DOCUMENT:" + documentId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_run_resource_links WHERE user_id=? AND agent_run_id=? "
                        + "AND document_id=? AND resource_kind='DOCUMENT' AND primary_resource",
                Long.class, owner.userId(), runId, documentId)).isEqualTo(1L);

        JsonNode replay = upload(owner, "document-upload-key-01", "resume.txt", content, 202);
        assertThat(replay.get("documentId").asText()).isEqualTo(documentId.toString());
        assertThat(replay.get("agentRunId").asText()).isEqualTo(runId.toString());
        assertThat(storage.uploads.get()).isEqualTo(1);
        upload(owner, "document-upload-key-01", "resume.txt",
                longText("changed@example.com").getBytes(StandardCharsets.UTF_8), 409);

        mockMvc.perform(get("/api/v1/documents").cookie(owner.cookie())
                        .queryParam("sort", "updatedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(documentId.toString()));
        mockMvc.perform(get("/api/v1/documents").cookie(owner.cookie())
                        .queryParam("sort", "displayName,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/agent-runs").cookie(owner.cookie())
                        .queryParam("resourceType", "DOCUMENT")
                        .queryParam("resourceId", documentId.toString())
                        .queryParam("sort", "queuedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(runId.toString()));
        mockMvc.perform(get("/api/v1/agent-runs").cookie(other.cookie())
                        .queryParam("resourceType", "DOCUMENT")
                        .queryParam("resourceId", documentId.toString())
                        .queryParam("sort", "queuedAt,desc"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs").cookie(owner.cookie())
                        .queryParam("resourceType", "DOCUMENT")
                        .queryParam("resourceId", UUID.randomUUID().toString())
                        .queryParam("sort", "queuedAt,desc"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents/" + documentId).cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents/" + documentId + "/text").cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/documents/" + documentId + "/download-url")
                        .cookie(other.cookie()).header("X-CSRF-TOKEN", other.csrfToken()))
                .andExpect(status().isNotFound());

        MvcResult download = mockMvc.perform(post("/api/v1/documents/" + documentId + "/download-url")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(download).get("url").asText()).startsWith("https://storage.test/");
        assertThat(storage.lastPresignTtl).isEqualTo(Duration.ofMinutes(5));

        String certification = """
                {"name":"Document-backed certificate","issuer":"Issuer",
                 "credentialNumber":null,"acquiredDate":null,"expiresAt":null,
                 "description":null,"evidenceDocumentId":"%s"}
                """.formatted(documentId);
        mockMvc.perform(post("/api/v1/profile/certifications")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(certification))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceDocumentId").value(documentId.toString()));
        mockMvc.perform(post("/api/v1/profile/certifications")
                        .cookie(other.cookie()).header("X-CSRF-TOKEN", other.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(certification))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "resume.txt", "text/plain", content))
                        .param("documentType", "RESUME")
                        .header("Idempotency-Key", "csrf-document-key-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentUploadWithTheSameKeyCreatesOneDocumentObjectAndRun() throws Exception {
        Session owner = authenticated("document-concurrent@example.com");
        byte[] content = longText("concurrent@example.com").getBytes(StandardCharsets.UTF_8);
        storage.blockNextUpload();

        JsonNode accepted;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> upload(
                    owner,
                    "document-concurrent-key-01",
                    "concurrent.txt",
                    content,
                    202));
            assertThat(storage.awaitBlockedUpload()).isTrue();

            MvcResult inProgress = performConcurrentUpload(owner, content);
            assertThat(inProgress.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(inProgress).get("code").asText())
                    .isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS");

            storage.releaseBlockedUpload();
            accepted = first.get(10, TimeUnit.SECONDS);
        } finally {
            storage.releaseBlockedUpload();
        }

        JsonNode replay = upload(
                owner,
                "document-concurrent-key-01",
                "concurrent.txt",
                content,
                202);
        assertThat(replay.get("documentId").asText())
                .isEqualTo(accepted.get("documentId").asText());
        assertThat(replay.get("agentRunId").asText())
                .isEqualTo(accepted.get("agentRunId").asText());
        assertThat(storage.uploads.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM documents WHERE user_id=?",
                Long.class, owner.userId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=? AND workflow_type='DOCUMENT_INGESTION'",
                Long.class, owner.userId())).isEqualTo(1L);
    }

    @Test
    void parserMaskChunkEmbeddingAndEvidencePipelineKeepsRawPrivateAndPending() throws Exception {
        Session owner = authenticated("pipeline-owner@example.com");
        Session other = authenticated("pipeline-other@example.com");
        JsonNode accepted = upload(owner, "pipeline-upload-key-01", "career.txt",
                longText("private@example.com").repeat(5).getBytes(StandardCharsets.UTF_8), 202);
        UUID documentId = UUID.fromString(accepted.get("documentId").asText());
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());

        workflow.beginParsing(owner.userId(), documentId, runId);
        var extracted = workflow.extractOrAcceptText(owner.userId(), documentId, runId);
        assertThat(extracted.extractedText()).contains("private@example.com", "성과 42");
        var masked = workflow.maskText(owner.userId(), documentId, runId);
        assertThat(masked.maskedText()).doesNotContain("private@example.com");
        assertThat(masked.maskedText()).hasSameSizeAs(masked.extractedText());
        List<DocumentChunkRecord> chunks = workflow.chunkText(owner.userId(), documentId, runId);
        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk ->
                assertThat(chunk.maskedContent()).doesNotContain("private@example.com"));
        assertThat(workflow.snapshot(owner.userId(), documentId).parseStatus())
                .isEqualTo(DocumentParseStatus.PARSED);
        assertThat(workflow.snapshot(owner.userId(), documentId).evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.QUEUED);

        var policy = workflow.activeEmbeddingPolicy();
        Map<UUID, List<Double>> embeddings = new LinkedHashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            embeddings.put(chunks.get(index).id(), index == 0 ? vector(1d, 0d) : vector(0d, 1d));
        }
        workflow.storeEmbeddings(owner.userId(), documentId, runId, policy, embeddings);
        var ordered = workflow.exactCosineSearch(
                owner.userId(), vector(1d, 0d), policy.version(), policy.generation(), 10);
        assertThat(ordered).isNotEmpty();
        assertThat(ordered.getFirst().chunkId()).isEqualTo(chunks.getFirst().id());
        assertThat(ordered.getFirst().distance()).isLessThan(ordered.getLast().distance());
        assertThat(workflow.exactCosineSearch(
                other.userId(), vector(1d, 0d), policy.version(), policy.generation(), 10)).isEmpty();
        Map<UUID, List<Double>> wrongDimension = new LinkedHashMap<>(embeddings);
        wrongDimension.put(chunks.getFirst().id(), List.of(1d));
        assertThatThrownBy(() -> workflow.storeEmbeddings(
                owner.userId(), documentId, runId, policy, wrongDimension))
                .isInstanceOf(BusinessException.class);
        jdbcTemplate.update(
                "UPDATE document_chunks SET embedding_generation=2 WHERE id=?", chunks.getFirst().id());
        assertThat(workflow.exactCosineSearch(
                owner.userId(), vector(1d, 0d), policy.version(), policy.generation(), 10))
                .noneMatch(value -> value.chunkId().equals(chunks.getFirst().id()));
        jdbcTemplate.update(
                "UPDATE document_chunks SET embedding_generation=1 WHERE id=?", chunks.getFirst().id());

        workflow.beginEvidenceExtraction(owner.userId(), documentId, runId);
        Map<String, Object> nullableMetadata = new LinkedHashMap<>();
        nullableMetadata.put("kind", "fixture");
        nullableMetadata.put("optional", null);
        var result = workflow.applyEvidenceCandidates(owner.userId(), documentId, runId, List.of(
                new DocumentEvidenceCandidate(
                        "ACHIEVEMENT", "Backend improvement", "성과 42",
                        nullableMetadata, new BigDecimal("0.900"),
                        List.of(chunks.getFirst().id()), 1, null),
                new DocumentEvidenceCandidate(
                        "ACHIEVEMENT", "Invented result", "성과 999",
                        Map.of(), new BigDecimal("0.800"),
                        List.of(chunks.getFirst().id()), 1, "not grounded")));
        assertThat(result.appliedEvidenceIds()).hasSize(1);
        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(workflow.finalizeDocument(owner.userId(), documentId, runId).evidenceExtractionStatus())
                .isEqualTo(EvidenceExtractionStatus.SUCCEEDED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verification_status FROM profile_evidence WHERE id=?", String.class,
                result.appliedEvidenceIds().getFirst())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verified_at IS NULL FROM profile_evidence WHERE id=?", Boolean.class,
                result.appliedEvidenceIds().getFirst())).isTrue();

        mockMvc.perform(get("/api/v1/profile/evidence").cookie(owner.cookie())
                        .queryParam("documentId", documentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].verificationStatus").value("PENDING"));
        mockMvc.perform(get("/api/v1/profile/evidence").cookie(other.cookie())
                        .queryParam("documentId", documentId.toString()))
                .andExpect(status().isNotFound());
        var active = documentStore.findActive(owner.userId(), documentId).orElseThrow();
        assertThat(documentStore.softDelete(
                owner.userId(), documentId, active.version(), Instant.now())).isTrue();
        assertThat(workflow.exactCosineSearch(
                owner.userId(), vector(1d, 0d), policy.version(), policy.generation(), 10)).isEmpty();
    }

    @Test
    void partialEvidenceFailurePreservesParsedTextAndDocument() throws Exception {
        Session owner = authenticated("partial-owner@example.com");
        JsonNode accepted = upload(owner, "partial-upload-key-01", "partial.txt",
                longText("partial@example.com").getBytes(StandardCharsets.UTF_8), 202);
        UUID documentId = UUID.fromString(accepted.get("documentId").asText());
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());

        workflow.beginParsing(owner.userId(), documentId, runId);
        workflow.extractOrAcceptText(owner.userId(), documentId, runId);
        workflow.maskText(owner.userId(), documentId, runId);
        workflow.chunkText(owner.userId(), documentId, runId);
        workflow.failEvidenceExtraction(
                owner.userId(), documentId, runId, "FAKE_EMBEDDING_FAILED");

        var document = workflow.snapshot(owner.userId(), documentId);
        assertThat(document.parseStatus()).isEqualTo(DocumentParseStatus.PARSED);
        assertThat(document.evidenceExtractionStatus()).isEqualTo(EvidenceExtractionStatus.FAILED);
        assertThat(document.evidenceErrorCode()).isEqualTo("FAKE_EMBEDDING_FAILED");
        assertThat(workflow.text(owner.userId(), documentId, document.sourceRevision()))
                .isPresent().get().extracting(value -> value.extractedText()).asString()
                .contains("성과 42");
    }

    @Test
    void shortTextManualInputResumesTheSameWaitingRunAndReplaysTheSameKey() throws Exception {
        Session owner = authenticated("manual-owner@example.com");
        JsonNode accepted = upload(owner, "manual-upload-key-01", "short.txt",
                "too short".getBytes(StandardCharsets.UTF_8), 202);
        UUID documentId = UUID.fromString(accepted.get("documentId").asText());
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());

        workflow.beginParsing(owner.userId(), documentId, runId);
        workflow.extractOrAcceptText(owner.userId(), documentId, runId);
        var waitingDocument = documentStore.findActive(owner.userId(), documentId).orElseThrow();
        assertThat(waitingDocument.parseStatus()).isEqualTo(DocumentParseStatus.NEEDS_MANUAL_TEXT);
        budgetReservations.releaseUnused(owner.userId(), runId, Instant.now());
        jdbcTemplate.update("""
                UPDATE agent_runs SET status='WAITING_USER',current_step='EXTRACT_OR_ACCEPT_TEXT',
                    progress_percent=20,reserved_cost_usd=0,error_code=NULL,error_message_safe=NULL,
                    retryable_failure=false,claim_token=NULL,claimed_by=NULL,lease_expires_at=NULL,
                    heartbeat_at=NULL,completed_at=NULL,waiting_action_type='PROVIDE_DOCUMENT_TEXT',
                    waiting_action_route=?,waiting_action_message='Provide document text',
                    state_version=state_version+1,updated_at=GREATEST(now(),queued_at)
                WHERE user_id=? AND id=?
                """, "/documents/" + documentId, owner.userId(), runId);

        String body = "{\"text\":\"" + longText("manual@example.com")
                .replace("\"", "\\\"") + "\",\"version\":" + waitingDocument.version() + "}";
        MvcResult first = mockMvc.perform(put("/api/v1/documents/" + documentId + "/manual-text")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "manual-text-key-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.agentRunId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        MvcResult replay = mockMvc.perform(put("/api/v1/documents/" + documentId + "/manual-text")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "manual-text-key-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.agentRunId").value(runId.toString()))
                .andExpect(jsonPath("$.replayed").value(true))
                .andReturn();
        assertThat(json(first).get("resourceId").asText()).isEqualTo(documentId.toString());
        assertThat(json(replay).get("resourceType").asText()).isEqualTo("DOCUMENT");
        var resumed = documentStore.findActive(owner.userId(), documentId).orElseThrow();
        assertThat(resumed.sourceRevision()).isEqualTo(2);
        assertThat(resumed.manualTextProvided()).isTrue();
        assertThat(workflow.text(owner.userId(), documentId, 2)).isPresent()
                .get().extracting(value -> value.extractedText()).asString().contains("manual@example.com");

        String mismatched = "{\"text\":\"" + longText("different@example.com")
                .replace("\"", "\\\"") + "\",\"version\":" + waitingDocument.version() + "}";
        mockMvc.perform(put("/api/v1/documents/" + documentId + "/manual-text")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "manual-text-key-01")
                        .contentType(MediaType.APPLICATION_JSON).content(mismatched))
                .andExpect(status().isConflict());
    }

    @Test
    void reparseCreatesANewRootRunAndVersionConflictsAreSafe() throws Exception {
        Session owner = authenticated("reparse-owner@example.com");
        JsonNode accepted = upload(owner, "reparse-upload-key-01", "reparse.txt",
                longText("reparse@example.com").getBytes(StandardCharsets.UTF_8), 202);
        UUID documentId = UUID.fromString(accepted.get("documentId").asText());
        UUID originalRun = UUID.fromString(accepted.get("agentRunId").asText());
        mockMvc.perform(put("/api/v1/documents/" + documentId + "/manual-text")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "active-manual-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + longText("active@example.com")
                                + "\",\"version\":0}"))
                .andExpect(status().isConflict());
        assertThat(documentStore.findActive(owner.userId(), documentId).orElseThrow().sourceRevision())
                .isEqualTo(1);
        budgetReservations.releaseUnused(owner.userId(), originalRun, Instant.now());
        jdbcTemplate.update("""
                UPDATE agent_runs SET status='FAILED',completed_at=queued_at,error_code='SAFE_FAILURE',
                    error_message_safe='Safe failure',retryable_failure=true,state_version=state_version+1,
                    updated_at=queued_at WHERE user_id=? AND id=?
                """, owner.userId(), originalRun);
        long version = documentStore.findActive(owner.userId(), documentId).orElseThrow().version();
        String body = "{\"version\":" + version + "}";

        MvcResult first = mockMvc.perform(post("/api/v1/documents/" + documentId + "/reparse")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "reparse-action-key-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn();
        UUID newRun = UUID.fromString(json(first).get("agentRunId").asText());
        assertThat(newRun).isNotEqualTo(originalRun);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT root_run_id=id AND retry_of_run_id IS NULL FROM agent_runs WHERE id=?",
                Boolean.class, newRun)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_run_resource_links WHERE agent_run_id=? AND document_id=?",
                Long.class, newRun, documentId)).isEqualTo(1L);
        assertThat(documentStore.findActive(owner.userId(), documentId).orElseThrow().sourceRevision())
                .isEqualTo(2);

        mockMvc.perform(post("/api/v1/documents/" + documentId + "/reparse")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "reparse-action-key-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.agentRunId").value(newRun.toString()))
                .andExpect(jsonPath("$.replayed").value(true));
        mockMvc.perform(post("/api/v1/documents/" + documentId + "/reparse")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "reparse-action-key-02")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void genericRetryPreservesDocumentTypedLinkAndLineage() throws Exception {
        Session owner = authenticated("document-generic-retry@example.com");
        JsonNode accepted = upload(
                owner,
                "document-retry-upload-01",
                "retry.txt",
                longText("retry@example.com").getBytes(StandardCharsets.UTF_8),
                202);
        UUID documentId = UUID.fromString(accepted.get("documentId").asText());
        UUID predecessorId = UUID.fromString(accepted.get("agentRunId").asText());
        budgetReservations.releaseUnused(owner.userId(), predecessorId, Instant.now());
        jdbcTemplate.update("""
                UPDATE agent_runs SET status='FAILED',completed_at=queued_at,
                    error_code='SAFE_RETRYABLE_FAILURE',error_message_safe='Retry this run',
                    retryable_failure=true,state_version=state_version+1,updated_at=queued_at
                WHERE user_id=? AND id=?
                """, owner.userId(), predecessorId);

        MvcResult retried = mockMvc.perform(post(
                                "/api/v1/agent-runs/" + predecessorId + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "document-generic-retry-01"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resourceType").value("DOCUMENT"))
                .andExpect(jsonPath("$.resourceId").value(documentId.toString()))
                .andReturn();
        UUID successorId = UUID.fromString(json(retried).get("agentRunId").asText());

        assertThat(successorId).isNotEqualTo(predecessorId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT retry_of_run_id=? AND root_run_id=? AND run_attempt_no=2
                FROM agent_runs WHERE user_id=? AND id=?
                """, Boolean.class, predecessorId, predecessorId, owner.userId(), successorId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM agent_run_resource_links
                WHERE user_id=? AND agent_run_id=? AND document_id=?
                  AND resource_kind='DOCUMENT' AND primary_resource
                """, Long.class, owner.userId(), successorId, documentId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT latest_agent_run_id FROM documents WHERE user_id=? AND id=?",
                UUID.class, owner.userId(), documentId)).isEqualTo(successorId);
    }

    @Test
    void uploadCompensatesObjectOnDatabaseFailureAndOutboxesFailedCompensation() throws Exception {
        Session owner = authenticated("compensation-owner@example.com");
        jdbcTemplate.update("DELETE FROM user_ai_preferences WHERE user_id=?", owner.userId());
        byte[] content = longText("compensation@example.com").getBytes(StandardCharsets.UTF_8);

        upload(owner, "compensation-upload-key-01", "compensate.txt", content, 404);
        assertThat(storage.values).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM documents WHERE user_id=?", Long.class, owner.userId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM object_deletion_outbox WHERE user_id=?", Long.class, owner.userId())).isZero();

        storage.failDeletes.set(true);
        upload(owner, "compensation-upload-key-02", "orphan.txt", content, 404);
        assertThat(storage.values).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM documents WHERE user_id=?", Long.class, owner.userId())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM object_deletion_outbox
                WHERE user_id=? AND document_id IS NULL
                  AND reason='ORPHAN_UPLOAD_COMPENSATION' AND status='PENDING'
                """, Long.class, owner.userId())).isEqualTo(1L);

        storage.failDeletes.set(false);
        outbox.processDue();
        assertThat(storage.values).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM object_deletion_outbox WHERE user_id=?", String.class, owner.userId()))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void idempotencyCompletionFailureRollsBackTheDocumentTransactionAndCompensatesTheObject()
            throws Exception {
        Session owner = authenticated("idempotency-atomic-owner@example.com");
        byte[] content = longText("idempotency-atomic@example.com")
                .getBytes(StandardCharsets.UTF_8);
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_document_idempotency_completion()
                RETURNS trigger AS $$
                BEGIN
                    IF NEW.route_scope = '/api/v1/documents' AND NEW.state = 'COMPLETED' THEN
                        RAISE EXCEPTION 'forced document idempotency completion failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_document_idempotency_completion_trigger
                BEFORE UPDATE OF state ON idempotency_records
                FOR EACH ROW EXECUTE FUNCTION fail_document_idempotency_completion()
                """);

        try {
            upload(owner, "idempotency-atomic-key-01", "atomic.txt", content, 500);

            assertThat(storage.values).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM documents WHERE user_id=?", Long.class, owner.userId()))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM agent_runs WHERE user_id=?", Long.class, owner.userId()))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM ai_budget_reservations WHERE user_id=?",
                    Long.class, owner.userId()))
                    .isZero();
        } finally {
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS fail_document_idempotency_completion_trigger
                    ON idempotency_records
                    """);
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_document_idempotency_completion()");
        }
    }

    @Test
    void storageUploadFailureReturnsSafe503WithoutDocumentOrObject() throws Exception {
        Session owner = authenticated("storage-failure-owner@example.com");
        storage.failUploads.set(true);

        JsonNode error = upload(owner, "storage-failure-key-01", "failure.txt",
                longText("storage@example.com").getBytes(StandardCharsets.UTF_8), 503);

        assertThat(error.get("code").asText()).isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
        assertThat(error.toString()).doesNotContain("fake unavailable", "storageKey", "provider");
        assertThat(storage.values).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM documents WHERE user_id=?", Long.class, owner.userId())).isZero();
    }

    @Test
    void uploadReportsSafePayloadMediaAndBudgetErrors() throws Exception {
        Session owner = authenticated("document-safe-errors@example.com");

        JsonNode tooLarge = upload(
                owner,
                "document-too-large-key-01",
                "too-large.txt",
                new byte[(int) DocumentFileInspector.MAX_FILE_SIZE + 1],
                413);
        assertThat(tooLarge.get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");

        JsonNode unsupported = upload(
                owner,
                "document-media-key-01",
                "spoof.pdf",
                "plain text pretending to be PDF".getBytes(StandardCharsets.UTF_8),
                415);
        assertThat(unsupported.get("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(storage.uploads.get()).isZero();

        JsonNode accepted = upload(
                owner,
                "document-budget-source-01",
                "budget.txt",
                longText("budget@example.com").getBytes(StandardCharsets.UTF_8),
                202);
        UUID runId = UUID.fromString(accepted.get("agentRunId").asText());
        budgetReservations.releaseUnused(owner.userId(), runId, Instant.now());
        jdbcTemplate.update(
                "UPDATE user_ai_preferences SET daily_budget_usd=0 WHERE user_id=?",
                owner.userId());
        jdbcTemplate.update("""
                UPDATE agent_runs SET status='FAILED',completed_at=queued_at,
                    error_code='SAFE_RETRYABLE_FAILURE',error_message_safe='Retry this run',
                    retryable_failure=true,
                    state_version=state_version+1,updated_at=queued_at
                WHERE user_id=? AND id=?
                """, owner.userId(), runId);

        MvcResult budget = mockMvc.perform(post("/api/v1/agent-runs/" + runId + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "document-budget-retry-01"))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        assertThat(json(budget).get("code").asText())
                .isEqualTo("RATE_OR_BUDGET_LIMIT_EXCEEDED");
        assertThat(storage.values).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=?", Long.class, owner.userId()))
                .isEqualTo(1L);
    }

    @Test
    void deleteIsImmediateUsesOutboxAndSelectsDeleteOrTombstoneByReference() throws Exception {
        Session owner = authenticated("delete-owner@example.com");
        JsonNode first = upload(owner, "delete-upload-key-01", "first.txt",
                longText("first@example.com").getBytes(StandardCharsets.UTF_8), 202);
        UUID firstId = UUID.fromString(first.get("documentId").asText());
        UUID firstRun = UUID.fromString(first.get("agentRunId").asText());
        UUID firstEvidence = createEvidence(owner, firstId, firstRun);
        long firstVersion = documentStore.findActive(owner.userId(), firstId).orElseThrow().version();

        references.referenced.set(false);
        mockMvc.perform(delete("/api/v1/documents/" + firstId)
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .queryParam("version", Long.toString(firstVersion)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/" + firstId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs").cookie(owner.cookie())
                        .queryParam("resourceType", "DOCUMENT")
                        .queryParam("resourceId", firstId.toString())
                        .queryParam("sort", "queuedAt,desc"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profile_evidence WHERE id=?", Long.class, firstEvidence))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_texts WHERE document_id=?", Long.class, firstId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE document_id=?", Long.class, firstId))
                .isZero();

        String firstKey = "users/" + owner.userId() + "/documents/" + firstId + "/content";
        assertThat(storage.values).containsKey(firstKey);
        outbox.processDue();
        assertThat(storage.values).doesNotContainKey(firstKey);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM object_deletion_outbox WHERE document_id=?", String.class, firstId))
                .isEqualTo("SUCCEEDED");

        JsonNode second = upload(owner, "delete-upload-key-02", "second.txt",
                longText("second@example.com").getBytes(StandardCharsets.UTF_8), 202);
        UUID secondId = UUID.fromString(second.get("documentId").asText());
        UUID secondRun = UUID.fromString(second.get("agentRunId").asText());
        UUID secondEvidence = createEvidence(owner, secondId, secondRun);
        long secondVersion = documentStore.findActive(owner.userId(), secondId).orElseThrow().version();
        references.referenced.set(true);
        mockMvc.perform(delete("/api/v1/documents/" + secondId)
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .queryParam("version", Long.toString(secondVersion)))
                .andExpect(status().isNoContent());
        Map<String, Object> tombstone = jdbcTemplate.queryForMap(
                "SELECT verification_status,title,content,document_id,source_entity_id,confidence,metadata::text "
                        + "FROM profile_evidence WHERE id=?", secondEvidence);
        assertThat(tombstone.get("verification_status")).isEqualTo("SOURCE_DELETED");
        assertThat(tombstone.get("title")).isEqualTo("[삭제된 문서 근거]");
        assertThat(tombstone.get("document_id")).isNull();
        assertThat(tombstone.get("source_entity_id")).isNull();
        assertThat(tombstone.get("confidence")).isNull();
        assertThat(tombstone.get("metadata")).isEqualTo("{}");
    }

    private UUID createEvidence(Session owner, UUID documentId, UUID runId) {
        workflow.beginParsing(owner.userId(), documentId, runId);
        workflow.extractOrAcceptText(owner.userId(), documentId, runId);
        workflow.maskText(owner.userId(), documentId, runId);
        List<DocumentChunkRecord> chunks = workflow.chunkText(owner.userId(), documentId, runId);
        workflow.beginEvidenceExtraction(owner.userId(), documentId, runId);
        return workflow.applyEvidenceCandidates(owner.userId(), documentId, runId, List.of(
                        new DocumentEvidenceCandidate(
                                "EXPERIENCE", "Owned evidence", "성과 42", Map.of(),
                                new BigDecimal("0.750"), List.of(chunks.getFirst().id()), 1, null)))
                .appliedEvidenceIds().getFirst();
    }

    private List<Double> vector(double first, double second) {
        java.util.ArrayList<Double> value = new java.util.ArrayList<>(1536);
        value.add(first);
        value.add(second);
        while (value.size() < 1536) value.add(0d);
        return List.copyOf(value);
    }

    private String longText(String email) {
        return ("Backend engineer " + email + " delivered 성과 42 with deterministic evidence. ").repeat(8);
    }

    private JsonNode upload(
            Session session, String key, String filename, byte[] content, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", filename, "text/plain", content))
                        .param("documentType", DocumentType.RESUME.name())
                        .param("displayName", "Safe resume")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json(result);
    }

    private MvcResult performConcurrentUpload(Session owner, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile(
                                "file", "concurrent.txt", "text/plain", content))
                        .param("documentType", DocumentType.RESUME.name())
                        .param("displayName", "Safe resume")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "document-concurrent-key-01"))
                .andReturn();
    }

    private Session authenticated(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = requiredCookie(csrf);
        String token = json(csrf).get("token").asText();
        String body = objectMapper.writeValueAsString(
                new SignupRequest(email, "password-123", "Candidate", true, true));
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(cookie).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = json(signup);
        return new Session(requiredCookie(signup), response.at("/csrf/token").asText(),
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
    static class FakePorts {
        @Bean
        @Primary
        FakeStorage fakeStorage() {
            return new FakeStorage();
        }

        @Bean
        @Primary
        FakeReferences fakeReferences() {
            return new FakeReferences();
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

    static final class FakeReferences implements EvidenceReferenceQueryPort {
        final AtomicBoolean referenced = new AtomicBoolean();

        @Override
        public boolean isReferenced(UUID userId, UUID evidenceId) {
            return referenced.get();
        }
    }

    static final class FakeStorage implements ObjectStoragePort {
        final Map<String, Stored> values = new ConcurrentHashMap<>();
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicBoolean failUploads = new AtomicBoolean();
        final AtomicBoolean failDeletes = new AtomicBoolean();
        volatile CountDownLatch uploadEntered = new CountDownLatch(0);
        volatile CountDownLatch allowUpload = new CountDownLatch(0);
        volatile boolean blockUpload;
        volatile Duration lastPresignTtl;

        @Override
        public void upload(String key, byte[] content, String mimeType, String checksum) {
            if (failUploads.get()) throw failure();
            if (blockUpload) {
                uploadEntered.countDown();
                try {
                    if (!allowUpload.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("blocked upload timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("blocked upload interrupted", exception);
                } finally {
                    blockUpload = false;
                }
            }
            values.put(key, new Stored(content.clone(), mimeType, checksum));
            uploads.incrementAndGet();
        }

        @Override
        public byte[] read(String key) {
            Stored stored = values.get(key);
            if (stored == null) throw failure();
            return stored.content().clone();
        }

        @Override
        public void delete(String key) {
            if (failDeletes.get()) throw failure();
            values.remove(key);
        }

        @Override
        public ObjectMetadata metadata(String key) {
            Stored stored = values.get(key);
            if (stored == null) throw failure();
            return new ObjectMetadata(stored.content().length, stored.mimeType(), stored.checksum());
        }

        @Override
        public PresignedObject presignGet(String key, Duration ttl) {
            if (!values.containsKey(key)) throw failure();
            lastPresignTtl = ttl;
            return new PresignedObject(
                    URI.create("https://storage.test/" + key + "?signature=safe"),
                    Instant.now().plus(ttl));
        }

        void reset() {
            releaseBlockedUpload();
            values.clear();
            uploads.set(0);
            failUploads.set(false);
            failDeletes.set(false);
            uploadEntered = new CountDownLatch(0);
            allowUpload = new CountDownLatch(0);
            blockUpload = false;
            lastPresignTtl = null;
        }

        void blockNextUpload() {
            uploadEntered = new CountDownLatch(1);
            allowUpload = new CountDownLatch(1);
            blockUpload = true;
        }

        boolean awaitBlockedUpload() throws InterruptedException {
            return uploadEntered.await(10, TimeUnit.SECONDS);
        }

        void releaseBlockedUpload() {
            allowUpload.countDown();
        }

        private ObjectStorageException failure() {
            return new ObjectStorageException(new IllegalStateException("fake unavailable"));
        }

        private record Stored(byte[] content, String mimeType, String checksum) {}
    }
}
