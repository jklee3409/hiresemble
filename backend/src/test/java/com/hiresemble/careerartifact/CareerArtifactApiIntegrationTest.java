package com.hiresemble.careerartifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort.GenerationState;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort.PersistPreparation;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.EvidenceRef;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioSlide;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeItem;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeSection;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioSlideType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioVisualType;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactObjectDeletionOutboxWorker;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactStore;
import com.hiresemble.document.application.port.ObjectStorageException;
import com.hiresemble.document.application.port.ObjectStoragePort;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@AutoConfigureMockMvc
@Import(CareerArtifactApiIntegrationTest.FakePorts.class)
@TestPropertySource(properties = "hiresemble.career-artifact.enabled=true")
class CareerArtifactApiIntegrationTest extends PostgresIntegrationTest {

    private static final String MODEL =
            OpenAiChatModels.modelsFor(com.hiresemble.agentrun.domain.model.WorkflowType.RESUME_GENERATION)
                    .getFirst()
                    .id();
    private static final String EMAIL = "render-only@example.invalid";
    private static final String PHONE = "010-0000-0000";
    private static final String LINK = "https://example.invalid/profile";
    private static final String CONTEXT_EMAIL = "context-leak@example.invalid";
    private static final String CONTEXT_PHONE = "010-1234-5678";
    private static final String CONTEXT_LINK = "https://private.example.invalid/profile";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgentRunQueryPort runQuery;
    @Autowired private AgentRunStatePort runState;
    @Autowired private BudgetReservationPort budget;
    @Autowired private CareerArtifactWorkflowPort workflow;
    @Autowired private CareerArtifactObjectDeletionOutboxWorker deletionWorker;
    @Autowired private FakeStorage storage;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void slowWorkers(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.career-artifact.deletion-scan-interval", () -> "1h");
    }

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void readinessCreateIdempotencyPrivacyAndOwnerBoundariesAreEnforced() throws Exception {
        Session owner = authenticated("career-artifact-owner@example.com");
        Session other = authenticated("career-artifact-other@example.com");

        mockMvc.perform(get("/api/v1/career-artifacts/readiness").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedExperienceCount").value(0))
                .andExpect(jsonPath("$.canGenerateResume").value(false))
                .andExpect(jsonPath("$.warnings.length()").value(3));

        Experience project = seedVerifiedExperience(
                owner.userId(), "PROJECT", "결제 파이프라인 개선",
                "조직: Hiresemble\n역할: Backend Engineer\n기간: 2024-01\n처리 시간을 42% 개선");
        attachGitHubProvenance(owner.userId(), project);
        seedUploadedDocument(owner.userId(), "RESUME");
        seedUploadedDocument(owner.userId(), "PORTFOLIO");

        mockMvc.perform(get("/api/v1/career-artifacts/readiness").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUploadedResume").value(true))
                .andExpect(jsonPath("$.hasUploadedPortfolio").value(true))
                .andExpect(jsonPath("$.verifiedExperienceCount").value(1))
                .andExpect(jsonPath("$.verifiedGitHubExperienceCount").value(1))
                .andExpect(jsonPath("$.verifiedStrengthCount").value(0))
                .andExpect(jsonPath("$.canGeneratePortfolio").value(true))
                .andExpect(jsonPath("$.warnings.length()").value(2));

        Experience secondProject = seedVerifiedExperience(
                owner.userId(), "경력", "운영 안정성 개선", "역할: Backend Engineer\n장애 대응 절차 개선");
        Experience strength = seedVerifiedExperience(
                owner.userId(), "역량", "근거 중심 문제 해결", "복잡한 장애 원인을 근거로 추적");
        mockMvc.perform(get("/api/v1/career-artifacts/readiness").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedExperienceCount").value(3))
                .andExpect(jsonPath("$.verifiedStrengthCount").value(1))
                .andExpect(jsonPath("$.warnings.length()").value(0));

        mockMvc.perform(get("/api/v1/career-artifacts/ai-models")
                        .cookie(owner.cookie()).queryParam("type", "RESUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(MODEL))
                .andExpect(jsonPath("$[0].recommended").isBoolean());

        String key = "career-create-idempotency-0001";
        JsonNode accepted = create(owner, List.of(project.id(), secondProject.id(), strength.id()),
                key, EMAIL, 202);
        UUID artifactId = UUID.fromString(accepted.path("resourceId").asText());
        UUID runId = UUID.fromString(accepted.path("agentRunId").asText());
        assertThat(accepted.path("resourceType").asText()).isEqualTo("CAREER_ARTIFACT");
        assertThat(accepted.path("replayed").asBoolean()).isFalse();

        JsonNode replay = create(owner, List.of(strength.id(), project.id(), secondProject.id()),
                key, EMAIL, 202);
        assertThat(replay.path("resourceId").asText()).isEqualTo(artifactId.toString());
        assertThat(replay.path("agentRunId").asText()).isEqualTo(runId.toString());
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(create(owner, List.of(project.id(), secondProject.id(), strength.id()),
                        key, "changed-profile@example.invalid", 409)
                .path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        String runInput = jdbcTemplate.queryForObject(
                "SELECT input_reference_snapshot::text FROM agent_runs WHERE id=?",
                String.class, runId);
        assertThat(runInput)
                .contains("renderProfileHash", "selectedEvidence", MODEL, "resume-ats-v1")
                .doesNotContain(EMAIL, PHONE, LINK, "displayName");
        String privateRequest = jdbcTemplate.queryForObject(
                "SELECT render_profile_snapshot::text FROM career_artifact_generation_requests WHERE agent_run_id=?",
                String.class, runId);
        assertThat(privateRequest).contains(EMAIL, PHONE, LINK, "테스트 지원자");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM agent_run_resource_links
                        WHERE agent_run_id=? AND resource_kind='CAREER_ARTIFACT'
                          AND career_artifact_id=? AND primary_resource
                        """, Long.class, runId, artifactId)).isEqualTo(1L);

        mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId).cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/career-artifacts")
                        .cookie(owner.cookie()).queryParam("sort", "title,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/agent-runs")
                        .cookie(owner.cookie())
                        .queryParam("resourceType", "CAREER_ARTIFACT")
                        .queryParam("resourceId", artifactId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].workflowType").value("RESUME_GENERATION"));
        mockMvc.perform(post("/api/v1/career-artifacts")
                        .cookie(owner.cookie()).header("Idempotency-Key", "career-no-csrf-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(List.of(project.id()), EMAIL)))
                .andExpect(status().isForbidden());

        ObjectNode invalidTitle = (ObjectNode) objectMapper.readTree(
                createBody(List.of(project.id()), EMAIL));
        invalidTitle.put("title", "unsafe\ntitle");
        mockMvc.perform(post("/api/v1/career-artifacts")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "career-invalid-title-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalidTitle)))
                .andExpect(status().isBadRequest());

        Session noEvidence = authenticated("career-artifact-empty@example.com");
        Experience pending = seedExperience(
                noEvidence.userId(), "PROJECT", "검토 중 경험", "아직 승인되지 않은 근거", false);
        assertThat(create(noEvidence, List.of(pending.id()), "career-no-evidence-0001", EMAIL, 409)
                .path("code").asText()).isEqualTo("INSUFFICIENT_VERIFIED_EXPERIENCE");
        assertThat(create(noEvidence, List.of(project.id()),
                "career-foreign-without-own-evidence-0001", EMAIL, 404)
                .path("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(create(owner, List.of(pending.id()), "career-foreign-evidence-0001", EMAIL, 404)
                .path("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void deterministicVersionDownloadLifecycleAndObjectDeletionPreserveTheCurrentVersion()
            throws Exception {
        Session owner = authenticated("career-artifact-lifecycle@example.com");
        Session other = authenticated("career-artifact-lifecycle-other@example.com");
        Experience experience = seedVerifiedExperience(
                owner.userId(), "PROJECT", "결제 파이프라인 개선",
                "조직: Hiresemble\n역할: Backend Engineer\n기간: 2024-01\n처리 시간을 42% 개선"
                        + "\n참고 연락: " + CONTEXT_EMAIL + " " + CONTEXT_PHONE + " " + CONTEXT_LINK);
        JsonNode accepted = create(owner, List.of(experience.id()),
                "career-lifecycle-create-0001", EMAIL, 202);
        UUID artifactId = uuid(accepted, "resourceId");
        UUID runId = uuid(accepted, "agentRunId");
        AgentRunSnapshot run = runQuery.findByOwner(owner.userId(), runId).orElseThrow();

        GenerationState state = workflow.load(run);
        assertThat(state.model()).isEqualTo(MODEL);
        assertThat(state.boundedContext().toString())
                .contains(experience.id().toString(), "처리 시간을 42% 개선")
                .contains("[link omitted]")
                .doesNotContain(
                        EMAIL, PHONE, LINK,
                        CONTEXT_EMAIL, CONTEXT_PHONE, CONTEXT_LINK,
                        "테스트 지원자", "github_source_units");
        EvidenceRef ref = new EvidenceRef(
                experience.id(), experience.evidenceId(), state.evidence().getFirst().usageType(),
                experience.title());
        ResumeContent content = new ResumeContent(
                "근거 중심 백엔드 엔지니어",
                "승인된 경험을 바탕으로 작성한 이력서 초안",
                List.of("Java", "Spring Boot"),
                List.of(new ResumeSection("EXPERIENCE", "주요 경험", List.of(
                        new ResumeItem(
                                experience.title(), "Backend Engineer", "2024-01",
                                List.of("처리 시간을 42% 개선"), List.of(ref))))),
                List.of());
        workflow.render(state, content);
        workflow.validate(runId);
        Version version = persist(runId);
        assertThat(version.versionNo()).isEqualTo(1);
        assertThat(version.storageKey()).isEqualTo(CareerArtifactStore.storageKey(
                owner.userId(), artifactId, version.id(), ArtifactType.RESUME));
        assertThat(storage.values).containsKey(version.storageKey());

        MvcResult detailResult = mockMvc.perform(
                        get("/api/v1/career-artifacts/{id}", artifactId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion.id").value(version.id().toString()))
                .andExpect(jsonPath("$.preview.headline").value("근거 중심 백엔드 엔지니어"))
                .andReturn();
        String publicDetail = detailResult.getResponse().getContentAsString();
        assertThat(publicDetail).doesNotContain(
                "storageKey", "checksum", "renderProfile", EMAIL, PHONE, LINK);

        mockMvc.perform(get("/api/v1/career-artifacts/{id}/versions", artifactId)
                        .cookie(owner.cookie()).queryParam("sort", "versionNo,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].versionNo").value(1));
        mockMvc.perform(post("/api/v1/career-artifacts/{id}/versions/{versionId}/download-url",
                                artifactId, version.id())
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("근거 이력서.docx"));
        assertThat(storage.lastPresignTtl).isEqualTo(Duration.ofMinutes(5));
        assertThat(storage.lastFilename).isEqualTo("근거 이력서.docx");
        mockMvc.perform(post("/api/v1/career-artifacts/{id}/versions/{versionId}/download-url",
                                artifactId, version.id())
                        .cookie(other.cookie()).header("X-CSRF-TOKEN", other.csrfToken()))
                .andExpect(status().isNotFound());
        storage.corruptMetadata.set(true);
        mockMvc.perform(post("/api/v1/career-artifacts/{id}/versions/{versionId}/download-url",
                                artifactId, version.id())
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAREER_ARTIFACT_VERSION_NOT_READY"));
        storage.corruptMetadata.set(false);

        long activeVersion = detail(owner, artifactId).at("/artifact/version").asLong();
        mutateLifecycle(owner, artifactId, "archive", activeVersion, 409,
                "CAREER_ARTIFACT_GENERATION_IN_PROGRESS");
        cancel(owner, runId);
        JsonNode archived = mutateLifecycle(owner, artifactId, "archive", activeVersion, 200, null);
        long archivedVersion = archived.at("/artifact/version").asLong();
        assertThat(archived.at("/artifact/lifecycleStatus").asText()).isEqualTo("ARCHIVED");
        mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId).cookie(owner.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/career-artifacts/{id}/versions", artifactId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk());
        generate(owner, artifactId, List.of(experience.id()), archivedVersion,
                "career-archived-generation-0001", 409)
                .path("code").asText();
        mockMvc.perform(delete("/api/v1/career-artifacts/{id}", artifactId)
                        .queryParam("version", Long.toString(archivedVersion))
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAREER_ARTIFACT_ARCHIVED"));

        JsonNode active = mutateLifecycle(owner, artifactId, "unarchive", archivedVersion, 200, null);
        long versionBeforeRegeneration = active.at("/artifact/version").asLong();
        JsonNode regeneration = generate(
                owner, artifactId, List.of(experience.id()), versionBeforeRegeneration,
                "career-regeneration-0001", 202);
        UUID regenerationRunId = uuid(regeneration, "agentRunId");
        JsonNode duringRegeneration = detail(owner, artifactId);
        assertThat(duringRegeneration.at("/currentVersion/id").asText())
                .isEqualTo(version.id().toString());
        cancel(owner, regenerationRunId);
        JsonNode afterFailure = detail(owner, artifactId);
        assertThat(afterFailure.at("/currentVersion/id").asText())
                .isEqualTo(version.id().toString());

        long deleteVersion = afterFailure.at("/artifact/version").asLong();
        mockMvc.perform(delete("/api/v1/career-artifacts/{id}", artifactId)
                        .queryParam("version", Long.toString(deleteVersion))
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT render_profile_snapshot::text
                        FROM career_artifact_versions
                        WHERE user_id=? AND id=?
                        """, String.class, owner.userId(), version.id()))
                .isEqualTo("{}");
        assertThat(jdbcTemplate.queryForList("""
                        SELECT render_profile_snapshot::text
                        FROM career_artifact_generation_requests
                        WHERE user_id=? AND career_artifact_id=?
                        """, String.class, owner.userId(), artifactId))
                .allMatch("{}"::equals);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM career_artifact_object_deletion_outbox WHERE storage_key=?",
                String.class, version.storageKey())).isEqualTo("PENDING");
        deletionWorker.processDue();
        assertThat(storage.values).doesNotContainKey(version.storageKey());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM career_artifact_object_deletion_outbox WHERE storage_key=?",
                String.class, version.storageKey())).isEqualTo("SUCCEEDED");
    }

    @Test
    void retryCopiesPrivateIdentityToANewTargetAndHistoryDeletionCompensatesSafely()
            throws Exception {
        Session owner = authenticated("career-artifact-retry@example.com");
        Experience experience = seedVerifiedExperience(
                owner.userId(), "PROJECT", "재시도 가능한 근거", "역할: Backend Engineer\n안전한 재시도 구현");
        JsonNode accepted = create(owner, List.of(experience.id()),
                "career-retry-create-0001", EMAIL, 202);
        UUID artifactId = uuid(accepted, "resourceId");
        UUID predecessorId = uuid(accepted, "agentRunId");
        failRetryable(owner.userId(), predecessorId);

        JsonNode successor = retry(owner, predecessorId, "career-agent-retry-0001", 202);
        UUID successorId = uuid(successor, "agentRunId");
        assertThat(successorId).isNotEqualTo(predecessorId);
        assertThat(retry(owner, predecessorId, "career-agent-retry-0001", 202)
                .path("agentRunId").asText()).isEqualTo(successorId.toString());

        JsonNode predecessorInput = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT input_reference_snapshot::text FROM agent_runs WHERE id=?",
                String.class, predecessorId));
        JsonNode successorInput = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT input_reference_snapshot::text FROM agent_runs WHERE id=?",
                String.class, successorId));
        for (String field : List.of(
                "careerArtifactId", "artifactType", "artifactVersion", "model",
                "templateKey", "templateVersion", "renderProfileHash",
                "selectedEvidence", "profileSectionRefs", "includeProfileSections")) {
            assertThat(successorInput.get(field)).isEqualTo(predecessorInput.get(field));
        }
        assertThat(successorInput.path("targetVersionId").asText())
                .isNotEqualTo(predecessorInput.path("targetVersionId").asText());
        assertThat(successorInput.toString()).doesNotContain(EMAIL, PHONE, LINK, "displayName");
        Map<String, String> requests = jdbcTemplate.queryForMap("""
                SELECT max(render_profile_hash) FILTER (WHERE agent_run_id=?) AS predecessor_hash,
                       max(render_profile_hash) FILTER (WHERE agent_run_id=?) AS successor_hash,
                       max(render_profile_snapshot::text) FILTER (WHERE agent_run_id=?) AS predecessor_profile,
                       max(render_profile_snapshot::text) FILTER (WHERE agent_run_id=?) AS successor_profile
                FROM career_artifact_generation_requests WHERE user_id=?
                """, predecessorId, successorId, predecessorId, successorId, owner.userId())
                .entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
        assertThat(requests.get("predecessor_hash")).isEqualTo(requests.get("successor_hash"));
        assertThat(requests.get("predecessor_profile")).isEqualTo(requests.get("successor_profile"));

        mockMvc.perform(delete("/api/v1/agent-runs/{id}", predecessorId)
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        JsonNode preserved = detail(owner, artifactId);
        assertThat(preserved.at("/artifact/latestAgentRunId").asText())
                .isEqualTo(successorId.toString());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM career_artifacts WHERE id=?",
                Boolean.class, artifactId)).isTrue();

        cancel(owner, successorId);
        mockMvc.perform(delete("/api/v1/agent-runs/{id}", successorId)
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForList("""
                        SELECT render_profile_snapshot::text
                        FROM career_artifact_generation_requests
                        WHERE career_artifact_id=? ORDER BY created_at
                        """, String.class, artifactId))
                .allMatch("{}"::equals);
    }

    @Test
    void bulkHistoryDeletionDoesNotLeaveAnEmptyArtifactShellOrADeletedLatestRun()
            throws Exception {
        Session owner = authenticated("career-artifact-bulk-history@example.com");
        Experience experience = seedVerifiedExperience(
                owner.userId(), "PROJECT", "일괄 삭제 근거",
                "역할: Backend Engineer\nAgent Run 일괄 삭제 보상");
        JsonNode accepted = create(owner, List.of(experience.id()),
                "career-bulk-history-create-0001", EMAIL, 202);
        UUID artifactId = uuid(accepted, "resourceId");
        UUID predecessorId = uuid(accepted, "agentRunId");
        failRetryable(owner.userId(), predecessorId);
        UUID successorId = uuid(
                retry(owner, predecessorId, "career-bulk-history-retry-0001", 202),
                "agentRunId");
        failRetryable(owner.userId(), successorId);

        ObjectNode request = objectMapper.createObjectNode();
        request.putArray("agentRunIds")
                .add(predecessorId.toString())
                .add(successorId.toString());
        mockMvc.perform(post("/api/v1/agent-runs/bulk-delete")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId).cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT deleted_at IS NOT NULL AND latest_agent_run_id IS NULL
                        FROM career_artifacts WHERE user_id=? AND id=?
                        """, Boolean.class, owner.userId(), artifactId))
                .isTrue();
        assertThat(jdbcTemplate.queryForList("""
                        SELECT render_profile_snapshot::text
                        FROM career_artifact_generation_requests
                        WHERE user_id=? AND career_artifact_id=?
                        """, String.class, owner.userId(), artifactId))
                .allMatch("{}"::equals);
    }

    @Test
    void cancellationDiscardsRendererOnlyStateBeforePersistence() throws Exception {
        Session owner = authenticated("career-artifact-render-cancel@example.com");
        Experience experience = seedVerifiedExperience(
                owner.userId(), "PROJECT", "취소 정리 근거",
                "역할: Backend Engineer\n취소 시 메모리 렌더 상태 정리");
        JsonNode accepted = create(owner, List.of(experience.id()),
                "career-render-cancel-0001", EMAIL, 202);
        UUID runId = uuid(accepted, "agentRunId");
        GenerationState state = workflow.load(
                runQuery.findByOwner(owner.userId(), runId).orElseThrow());

        renderMinimalResume(state, experience);
        cancel(owner, runId);

        assertThatThrownBy(() -> workflow.validate(runId))
                .hasMessageContaining("CAREER_ARTIFACT_RENDER_STATE_MISSING");
        assertThat(storage.values).isEmpty();
    }

    @Test
    void uploadedObjectIsDeletedImmediatelyOrQueuedWhenAtomicVersionApplyFails()
            throws Exception {
        Session owner = authenticated("career-artifact-compensation@example.com");
        Experience immediateEvidence = seedVerifiedExperience(
                owner.userId(), "PROJECT", "즉시 정리 근거", "역할: Backend Engineer\n원자 적용 실패 보상");
        JsonNode immediateAccepted = create(owner, List.of(immediateEvidence.id()),
                "career-compensation-immediate-0001", EMAIL, 202);
        UUID immediateRunId = uuid(immediateAccepted, "agentRunId");
        GenerationState immediateState = workflow.load(
                runQuery.findByOwner(owner.userId(), immediateRunId).orElseThrow());
        renderMinimalResume(immediateState, immediateEvidence);
        String immediateKey = CareerArtifactStore.storageKey(
                owner.userId(), uuid(immediateAccepted, "resourceId"),
                immediateState.targetVersionId(), ArtifactType.RESUME);
        jdbcTemplate.update(
                "UPDATE experience_items SET version=version+1,updated_at=now() WHERE id=?",
                immediateEvidence.id());
        PersistPreparation immediatePreparation = workflow.upload(immediateRunId);
        assertThatThrownBy(() -> workflow.apply(immediateRunId, immediatePreparation))
                .hasMessageContaining("CAREER_ARTIFACT_EVIDENCE_CHANGED");
        assertThat(storage.values).doesNotContainKey(immediateKey);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM career_artifact_versions WHERE agent_run_id=?",
                Long.class, immediateRunId)).isZero();

        Experience rollbackEvidence = seedVerifiedExperience(
                owner.userId(), "PROJECT", "트랜잭션 롤백 근거",
                "역할: Backend Engineer\ncheckpoint transaction 롤백 보상");
        JsonNode rollbackAccepted = create(owner, List.of(rollbackEvidence.id()),
                "career-compensation-rollback-0001", EMAIL, 202);
        UUID rollbackArtifactId = uuid(rollbackAccepted, "resourceId");
        UUID rollbackRunId = uuid(rollbackAccepted, "agentRunId");
        GenerationState rollbackState = workflow.load(
                runQuery.findByOwner(owner.userId(), rollbackRunId).orElseThrow());
        renderMinimalResume(rollbackState, rollbackEvidence);
        String rollbackKey = CareerArtifactStore.storageKey(
                owner.userId(), rollbackArtifactId,
                rollbackState.targetVersionId(), ArtifactType.RESUME);
        PersistPreparation rollbackPreparation = workflow.upload(rollbackRunId);
        assertThat(storage.values).containsKey(rollbackKey);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(ignored -> {
                            workflow.apply(rollbackRunId, rollbackPreparation);
                            throw new IllegalStateException("ROLLBACK_FIXTURE");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ROLLBACK_FIXTURE");
        assertThat(storage.values).doesNotContainKey(rollbackKey);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM career_artifact_versions WHERE agent_run_id=?",
                Long.class, rollbackRunId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT current_version_id IS NULL
                        FROM career_artifacts WHERE user_id=? AND id=?
                        """, Boolean.class, owner.userId(), rollbackArtifactId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT consumed_at IS NULL
                        FROM career_artifact_generation_requests
                        WHERE user_id=? AND agent_run_id=?
                        """, Boolean.class, owner.userId(), rollbackRunId))
                .isTrue();

        Experience deferredEvidence = seedVerifiedExperience(
                owner.userId(), "PROJECT", "Outbox 정리 근거", "역할: Backend Engineer\nOutbox 보상 구현");
        JsonNode deferredAccepted = create(owner, List.of(deferredEvidence.id()),
                "career-compensation-outbox-0001", EMAIL, 202);
        UUID deferredRunId = uuid(deferredAccepted, "agentRunId");
        GenerationState deferredState = workflow.load(
                runQuery.findByOwner(owner.userId(), deferredRunId).orElseThrow());
        renderMinimalResume(deferredState, deferredEvidence);
        String deferredKey = CareerArtifactStore.storageKey(
                owner.userId(), uuid(deferredAccepted, "resourceId"),
                deferredState.targetVersionId(), ArtifactType.RESUME);
        jdbcTemplate.update(
                "UPDATE experience_items SET version=version+1,updated_at=now() WHERE id=?",
                deferredEvidence.id());
        storage.failDeletes.set(true);
        PersistPreparation deferredPreparation = workflow.upload(deferredRunId);
        assertThatThrownBy(() -> workflow.apply(deferredRunId, deferredPreparation))
                .hasMessageContaining("CAREER_ARTIFACT_EVIDENCE_CHANGED");
        assertThat(storage.values).containsKey(deferredKey);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT status FROM career_artifact_object_deletion_outbox
                        WHERE storage_key=? AND reason='ORPHAN_UPLOAD_COMPENSATION'
                        """, String.class, deferredKey)).isEqualTo("PENDING");
        storage.failDeletes.set(false);
        deletionWorker.processDue();
        assertThat(storage.values).doesNotContainKey(deferredKey);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT status FROM career_artifact_object_deletion_outbox
                        WHERE storage_key=? AND reason='ORPHAN_UPLOAD_COMPENSATION'
                        """, String.class, deferredKey)).isEqualTo("SUCCEEDED");
    }

    @Test
    void portfolioUsesItsExactTemplateAndPersistsASixteenByNinePptxVersion()
            throws Exception {
        Session owner = authenticated("career-artifact-portfolio@example.com");
        Experience experience = seedVerifiedExperience(
                owner.userId(), "PROJECT", "면접 사례 연구",
                "문제: 복잡한 배포 절차\n역할: Backend Engineer\n행동: 자동화\n결과: 안정성 개선\n강점: 근거 중심 판단");

        ObjectNode invalidTemplate = portfolioCreateBody(List.of(experience.id()));
        invalidTemplate.put("templateKey", "resume-ats-v1");
        mockMvc.perform(post("/api/v1/career-artifacts")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "portfolio-invalid-template-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalidTemplate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        ObjectNode invalidModel = portfolioCreateBody(List.of(experience.id()));
        invalidModel.put("model", "unsupported-model");
        mockMvc.perform(post("/api/v1/career-artifacts")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "portfolio-invalid-model-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalidModel)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_MODEL_NOT_SUPPORTED"));

        MvcResult acceptedResult = mockMvc.perform(post("/api/v1/career-artifacts")
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "portfolio-create-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                portfolioCreateBody(List.of(experience.id())))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resourceType").value("CAREER_ARTIFACT"))
                .andReturn();
        JsonNode accepted = json(acceptedResult);
        UUID artifactId = uuid(accepted, "resourceId");
        UUID runId = uuid(accepted, "agentRunId");
        GenerationState state = workflow.load(
                runQuery.findByOwner(owner.userId(), runId).orElseThrow());
        assertThat(state.artifact().artifactType()).isEqualTo(ArtifactType.PORTFOLIO);
        assertThat(state.templateKey()).isEqualTo("portfolio-interview-v1");
        EvidenceRef ref = new EvidenceRef(
                experience.id(), experience.evidenceId(), state.evidence().getFirst().usageType(),
                experience.title());
        List<PortfolioSlide> slides = new ArrayList<>();
        slides.add(new PortfolioSlide(
                1, PortfolioSlideType.COVER, "면접 포트폴리오", "Backend Engineer",
                List.of("승인 근거 기반"), PortfolioVisualType.NONE, List.of()));
        slides.add(new PortfolioSlide(
                2, PortfolioSlideType.PROFILE_SUMMARY, "역할", null,
                List.of("역할: Backend Engineer"), PortfolioVisualType.NONE, List.of(ref)));
        slides.add(new PortfolioSlide(
                3, PortfolioSlideType.STRENGTH_OVERVIEW, "강점", null,
                List.of("강점: 근거 중심 판단"), PortfolioVisualType.IMPACT_METRICS, List.of(ref)));
        slides.add(new PortfolioSlide(
                4, PortfolioSlideType.PROJECT_CASE_STUDY, "문제와 행동", null,
                List.of("문제: 복잡한 배포 절차", "행동: 자동화"),
                PortfolioVisualType.PROCESS, List.of(ref)));
        slides.add(new PortfolioSlide(
                5, PortfolioSlideType.IMPACT_AND_LEARNING, "결과", null,
                List.of("결과: 안정성 개선"), PortfolioVisualType.TIMELINE, List.of(ref)));
        slides.add(new PortfolioSlide(
                6, PortfolioSlideType.CLOSING, "대화할 주제", null,
                List.of("기술적 판단을 설명하겠습니다"), PortfolioVisualType.NONE, List.of()));
        workflow.render(state, new PortfolioContent(slides, List.of()));
        workflow.validate(runId);
        Version version = persist(runId);
        assertThat(version.mimeType()).isEqualTo(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assertThat(version.storageKey()).endsWith("/content.pptx");
        mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifact.artifactType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.preview.slides.length()").value(6));
        cancel(owner, runId);
        mockMvc.perform(post("/api/v1/career-artifacts/{id}/versions/{versionId}/download-url",
                                artifactId, version.id())
                        .cookie(owner.cookie()).header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("면접 포트폴리오.pptx"));
    }

    private JsonNode create(
            Session session, List<UUID> experienceIds, String key, String email, int expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/career-artifacts")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(experienceIds, email)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json(result);
    }

    private byte[] createBody(List<UUID> experienceIds, String email) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("artifactType", "RESUME")
                .put("title", "근거 이력서")
                .put("model", MODEL)
                .put("templateKey", "resume-ats-v1");
        addIds(body.putArray("experienceItemIds"), experienceIds);
        body.putArray("includeProfileSections").add("PROFILE");
        body.set("renderProfile", renderProfile(email));
        return objectMapper.writeValueAsBytes(body);
    }

    private JsonNode generate(
            Session session,
            UUID artifactId,
            List<UUID> experienceIds,
            long version,
            String key,
            int expectedStatus)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("model", MODEL)
                .put("templateKey", "resume-ats-v1")
                .put("version", version);
        addIds(body.putArray("experienceItemIds"), experienceIds);
        body.putArray("includeProfileSections").add("PROFILE");
        body.set("renderProfile", renderProfile(EMAIL));
        MvcResult result = mockMvc.perform(post("/api/v1/career-artifacts/{id}/generations", artifactId)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json(result);
    }

    private ObjectNode renderProfile(String email) {
        ObjectNode profile = objectMapper.createObjectNode()
                .put("displayName", "테스트 지원자")
                .put("email", email)
                .put("phone", PHONE)
                .put("includeContact", true);
        profile.putArray("links").addObject().put("label", "Portfolio").put("url", LINK);
        return profile;
    }

    private ObjectNode portfolioCreateBody(List<UUID> experienceIds) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("artifactType", "PORTFOLIO")
                .put("title", "면접 포트폴리오")
                .put("model", MODEL)
                .put("templateKey", "portfolio-interview-v1");
        addIds(body.putArray("experienceItemIds"), experienceIds);
        body.putArray("includeProfileSections");
        body.set("renderProfile", renderProfile(EMAIL));
        return body;
    }

    private void addIds(ArrayNode array, List<UUID> ids) {
        ids.forEach(id -> array.add(id.toString()));
    }

    private JsonNode detail(Session session, UUID artifactId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/career-artifacts/{id}", artifactId)
                        .cookie(session.cookie()))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode mutateLifecycle(
            Session session,
            UUID artifactId,
            String action,
            long version,
            int expectedStatus,
            String expectedCode)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("version", version);
        MvcResult result = mockMvc.perform(post("/api/v1/career-artifacts/{id}/{action}",
                                artifactId, action)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        JsonNode response = json(result);
        if (expectedCode != null) assertThat(response.path("code").asText()).isEqualTo(expectedCode);
        return response;
    }

    private JsonNode retry(Session session, UUID runId, String key, int expectedStatus)
            throws Exception {
        return json(mockMvc.perform(post("/api/v1/agent-runs/{id}/retry", runId)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key))
                .andExpect(status().is(expectedStatus))
                .andReturn());
    }

    private void cancel(Session session, UUID runId) throws Exception {
        AgentRunSnapshot run = runQuery.findByOwner(session.userId(), runId).orElseThrow();
        ObjectNode body = objectMapper.createObjectNode().put("stateVersion", run.stateVersion());
        mockMvc.perform(post("/api/v1/agent-runs/{id}/cancel", runId)
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private void failRetryable(UUID userId, UUID runId) {
        var claimed = runState.claim(
                runId, "career-artifact-failure", Instant.now(), Duration.ofMinutes(1))
                .orElseThrow();
        budget.releaseUnused(userId, runId, Instant.now());
        AgentRunSnapshot current = runQuery.findByOwner(userId, runId).orElseThrow();
        runState.transition(new AgentRunTransitionCommand(
                userId, runId, claimed.claimToken(), current.stateVersion(),
                AgentRunStatus.FAILED, "DRAFT_RESUME_CONTENT", 40, ModelTier.LOW_COST,
                BigDecimal.ZERO, true, null,
                new SafeError("EXTERNAL_SERVICE_UNAVAILABLE", "The model provider is temporarily unavailable."),
                null, Instant.now()));
    }

    private void renderMinimalResume(GenerationState state, Experience experience) {
        EvidenceRef ref = new EvidenceRef(
                experience.id(), experience.evidenceId(), state.evidence().getFirst().usageType(),
                experience.title());
        ResumeContent content = new ResumeContent(
                "근거 중심 지원자", "승인 근거 기반 초안", List.of("Java"),
                List.of(new ResumeSection("EXPERIENCE", "경험", List.of(
                        new ResumeItem(experience.title(), "Backend Engineer", null,
                                List.of("안전한 구현"), List.of(ref))))), List.of());
        workflow.render(state, content);
        workflow.validate(state.run().id());
    }

    private Version persist(UUID runId) {
        PersistPreparation preparation = workflow.upload(runId);
        return workflow.apply(runId, preparation);
    }

    private Experience seedVerifiedExperience(
            UUID userId, String category, String title, String content) {
        return seedExperience(userId, category, title, content, true);
    }

    private Experience seedExperience(
            UUID userId, String category, String title, String content, boolean verified) {
        UUID experienceId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        String status = verified ? "VERIFIED" : "PENDING";
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            jdbcTemplate.update("""
                    INSERT INTO profile_evidence (
                      id,user_id,source_type,source_entity_id,document_id,evidence_category,
                      title,content,metadata,confidence,verification_status,verified_at,
                      source_deleted_at,version,created_at,updated_at,
                      github_source_id,github_repository_id,github_snapshot_id,github_claim_key)
                    VALUES (?,?,'EXPERIENCE',?,NULL,?,?,?,'{}'::jsonb,0.900,?,
                            CASE WHEN ?='VERIFIED' THEN now() ELSE NULL END,
                            NULL,0,now(),now(),NULL,NULL,NULL,NULL)
                    """, evidenceId, userId, experienceId, category, title, content, status, status);
            jdbcTemplate.update("""
                    INSERT INTO experience_items (
                      id,user_id,canonical_evidence_id,evidence_category,title,content,
                      verification_status,match_kind,matched_experience_item_id,match_similarity,
                      match_policy_version,canonical_fingerprint,version,created_at,updated_at,deleted_at)
                    VALUES (?,?,?,?,?,?,?,'NEW',NULL,NULL,'experience-semantic-v1',?,0,now(),now(),NULL)
                    """, experienceId, userId, evidenceId, category, title, content, status,
                    UUID.randomUUID().toString().replace("-", ""));
        });
        return new Experience(experienceId, evidenceId, title);
    }

    private void seedUploadedDocument(UUID userId, String type) {
        UUID id = UUID.randomUUID();
        String extension = "RESUME".equals(type) ? "docx" : "pdf";
        jdbcTemplate.update("""
                INSERT INTO documents (
                  id,user_id,document_type,original_filename,display_name,storage_key,
                  mime_type,file_size_bytes,checksum_sha256,parse_status,
                  evidence_extraction_status,manual_text_provided,source_revision,
                  version,uploaded_at,updated_at,deleted_at)
                VALUES (?,?,?,?,?,?,?,1,?,'UPLOADED','NOT_STARTED',false,1,0,now(),now(),NULL)
                """, id, userId, type, "fixture." + extension, "Fixture " + type,
                "users/" + userId + "/documents/" + id + "/content",
                "RESUME".equals(type)
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        : "application/pdf",
                "a".repeat(64));
    }

    private void attachGitHubProvenance(UUID userId, Experience experience) {
        UUID sourceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID sourceEvidenceId = UUID.randomUUID();
        long externalId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        if (externalId == 0) externalId = 1;
        long finalExternalId = externalId;
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            jdbcTemplate.update("""
                    INSERT INTO github_sources (
                      id,user_id,source_kind,account_type,original_url,canonical_url,
                      owner_login,repository_name,source_status,created_at,updated_at)
                    VALUES (?,?,'REPOSITORY',NULL,'https://github.com/fixture/repo',
                            'https://github.com/fixture/repo','fixture','repo','READY',now(),now())
                    """, sourceId, userId);
            jdbcTemplate.update("""
                    INSERT INTO github_repositories (
                      id,user_id,external_repository_id,node_id,owner_login,repository_name,
                      canonical_url,default_branch,is_private,is_fork,is_archived,topics,
                      created_at,updated_at)
                    VALUES (?,?,?,'fixture-node','fixture','repo',
                            'https://github.com/fixture/repo','main',false,false,false,
                            '[]'::jsonb,now(),now())
                    """, repositoryId, userId, finalExternalId);
            jdbcTemplate.update("""
                    INSERT INTO github_source_repository_links (
                      id,user_id,github_source_id,github_repository_id,available,selected,
                      selection_order,discovered_at,updated_at)
                    VALUES (?,?,?,?,true,true,1,now(),now())
                    """, UUID.randomUUID(), userId, sourceId, repositoryId);
            jdbcTemplate.update("""
                    INSERT INTO github_repository_snapshots (
                      id,user_id,github_repository_id,commit_sha,tree_sha,github_api_version,
                      retrieval_policy_version,selection_complete,upstream_truncated,
                      snapshot_storage_key,checksum_sha256,sanitized_bytes,captured_at)
                    VALUES (?,?,?,? ,NULL,'2022-11-28','github-public-v1',true,false,?,?,1,now())
                    """, snapshotId, userId, repositoryId, "b".repeat(40),
                    "users/" + userId + "/github-sources/" + sourceId
                            + "/snapshots/" + snapshotId + "/snapshot.json.gz",
                    "c".repeat(64));
            jdbcTemplate.update("""
                    INSERT INTO profile_evidence (
                      id,user_id,source_type,source_entity_id,document_id,evidence_category,
                      title,content,metadata,confidence,verification_status,verified_at,
                      source_deleted_at,version,created_at,updated_at,
                      github_source_id,github_repository_id,github_snapshot_id,github_claim_key)
                    VALUES (?,?, 'GITHUB_REPOSITORY',?,NULL,'PROJECT','GitHub source',
                            '승인 전 원천 근거','{}'::jsonb,0.800,'VERIFIED',now(),NULL,0,
                            now(),now(),?,?,?,?)
                    """, sourceEvidenceId, userId, repositoryId,
                    sourceId, repositoryId, snapshotId, "d".repeat(64));
            jdbcTemplate.update("""
                    INSERT INTO github_source_units (
                      id,user_id,snapshot_id,unit_type,repository_path,blob_sha,language,
                      line_start,line_end,content_hash,excerpt,snapshot_ordinal,created_at)
                    VALUES (?, ?, ?, 'README', 'README.md', ?, 'Markdown', 1, 1,
                            ?, '승인 전 원천 근거', 1, now())
                    """, unitId, userId, snapshotId, "e".repeat(40), "f".repeat(64));
            jdbcTemplate.update("""
                    INSERT INTO github_evidence_unit_links (
                      id,user_id,profile_evidence_id,source_unit_id,relation_kind,created_at)
                    VALUES (?, ?, ?, ?, 'PRIMARY', now())
                    """, UUID.randomUUID(), userId, sourceEvidenceId, unitId);
            jdbcTemplate.update("""
                    INSERT INTO experience_evidence_links (
                      id,user_id,experience_item_id,profile_evidence_id,relation_kind,
                      similarity,match_policy_version,created_at)
                    VALUES (?,?,?,?,'PRIMARY_SOURCE',NULL,'experience-semantic-v1',now())
                    """, UUID.randomUUID(), userId, experience.id(), sourceEvidenceId);
        });
    }

    private Session authenticated(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = requiredCookie(csrf);
        String token = json(csrf).path("token").asText();
        byte[] body = objectMapper.writeValueAsBytes(
                new SignupRequest(email, "password-123", "Candidate", true, true));
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(cookie).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = json(signup);
        return new Session(
                requiredCookie(signup), response.at("/csrf/token").asText(),
                UUID.fromString(response.at("/user/id").asText()));
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) throw new AssertionError("SESSION cookie missing");
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        byte[] content = result.getResponse().getContentAsByteArray();
        return content.length == 0 ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private record Experience(UUID id, UUID evidenceId, String title) {}

    private record Session(Cookie cookie, String csrfToken, UUID userId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FakePorts {
        @Bean
        @Primary
        FakeStorage fakeCareerArtifactStorage() {
            return new FakeStorage();
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

    static final class FakeStorage implements ObjectStoragePort {
        final Map<String, Stored> values = new ConcurrentHashMap<>();
        final AtomicBoolean corruptMetadata = new AtomicBoolean();
        final AtomicBoolean failDeletes = new AtomicBoolean();
        volatile Duration lastPresignTtl;
        volatile String lastFilename;

        @Override
        public void upload(String key, byte[] content, String mimeType, String checksum) {
            values.put(key, new Stored(content.clone(), mimeType, checksum));
        }

        @Override
        public byte[] read(String key) {
            Stored stored = required(key);
            return stored.content().clone();
        }

        @Override
        public void delete(String key) {
            if (failDeletes.get()) {
                throw new ObjectStorageException(new IllegalStateException("fixture delete unavailable"));
            }
            values.remove(key);
        }

        @Override
        public ObjectMetadata metadata(String key) {
            Stored stored = required(key);
            return new ObjectMetadata(
                    corruptMetadata.get() ? stored.content().length + 1 : stored.content().length,
                    stored.mimeType(), stored.checksum());
        }

        @Override
        public PresignedObject presignGet(String key, Duration ttl) {
            return presignGet(key, ttl, null);
        }

        @Override
        public PresignedObject presignGet(String key, Duration ttl, String filename) {
            required(key);
            lastPresignTtl = ttl;
            lastFilename = filename;
            return new PresignedObject(
                    URI.create("https://storage.test/attachment?signature=fixture"),
                    Instant.now().plus(ttl));
        }

        void reset() {
            values.clear();
            corruptMetadata.set(false);
            failDeletes.set(false);
            lastPresignTtl = null;
            lastFilename = null;
        }

        private Stored required(String key) {
            Stored stored = values.get(key);
            if (stored == null) {
                throw new ObjectStorageException(new IllegalStateException("fixture object absent"));
            }
            return stored;
        }

        private record Stored(byte[] content, String mimeType, String checksum) {}
    }
}
