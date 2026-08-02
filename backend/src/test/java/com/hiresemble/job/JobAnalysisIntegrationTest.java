package com.hiresemble.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.job.application.JobAnalysisApplicationService;
import com.hiresemble.job.application.model.JobAnalysisModels.CriterionDraft;
import com.hiresemble.job.application.model.JobAnalysisModels.EvidenceUsage;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSnapshot;
import com.hiresemble.job.application.model.JobAnalysisModels.PersistJobAnalysis;
import com.hiresemble.job.application.model.JobAnalysisModels.RequirementItem;
import com.hiresemble.job.domain.CriterionSupportType;
import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.FitCriterionCategory;
import com.hiresemble.job.domain.JobAnalysisEvidenceUsageType;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(JobAnalysisIntegrationTest.TestPorts.class)
class JobAnalysisIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobAnalysisApplicationService analysisService;
    @Autowired private ProfileStore profileStore;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void backgroundWorkersStayDeterministic(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.job-deadline-scheduler.cron", () -> "0 0 0 1 1 *");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
    }

    @Test
    void structuredEducationProvenancePersistsAndProfileChangeInvalidatesReuse() throws Exception {
        Session owner = authenticated("analysis-education-owner@example.com");
        JsonNode education = json(mockMvc.perform(post("/api/v1/profile/educations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Owner University","major":"Computer Science",
                                 "degree":"Bachelor","educationLevel":"BACHELOR",
                                 "educationStatus":"EXPECTED_GRADUATION","admissionDate":"2022-03-01",
                                 "graduationDate":"2026-08-25","gpa":null,"gpaScale":null,
                                 "description":null}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        UUID jobId = manualJob(owner, "analysis-education-job", "analysis/education");
        UUID runId = UUID.fromString(json(analyze(
                        owner, jobId, 0, "ECONOMY", false, "analysis-education-run", 202))
                .get("agentRunId").asText());
        JobAnalysisSnapshot snapshot =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        var educationFact = snapshot.profile().structuredFacts().stream()
                .filter(value -> value.factType().name().equals("PRIMARY_EDUCATION"))
                .findFirst()
                .orElseThrow();
        var requirement = new RequirementItem(
                FitCriterionCategory.EDUCATION_CERTIFICATION_LANGUAGE,
                "4년제 대학 졸업 또는 졸업 예정",
                true,
                "지원 자격");
        markRunning(runId);
        var persisted = analysisService.persist(
                owner.userId(),
                runId,
                new PersistJobAnalysis(
                        snapshot.jobId(), snapshot.jobVersion(), snapshot.jobContentHash(),
                        snapshot.profileSnapshotHash(), snapshot.evidenceSnapshotHash(),
                        snapshot.contextHash(), snapshot.qualityMode(), Eligibility.ELIGIBLE,
                        List.of(new CriterionDraft(
                                FitCriterionCategory.EDUCATION_CERTIFICATION_LANGUAGE,
                                requirement.text(), MatchLevel.MATCHED,
                                "구조화된 대표 학력이 학사 졸업 예정 조건과 일치합니다.",
                                requirement.sourceLocation(), List.of(),
                                List.of(educationFact.reference()), CriterionSupportType.EDUCATION)),
                        List.of(), List.of(requirement), List.of(), List.of(), List.of(),
                        List.of(), List.of(), "구조화된 학력 기준 분석입니다."));

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT fact_type || ':' || source_entity_id::text || ':' || source_entity_version
                        FROM job_analysis_structured_fact_links
                        WHERE user_id=? AND job_analysis_id=? AND usage_type='CRITERION_MATCH'
                        """,
                        String.class,
                        owner.userId(),
                        persisted.summary().id()))
                .isEqualTo("PRIMARY_EDUCATION:" + education.get("id").asText() + ":1");

        mockMvc.perform(put("/api/v1/profile/educations/" + education.get("id").asText())
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Owner Graduate School","major":"Computer Science",
                                 "degree":"Master","educationLevel":"MASTER",
                                 "educationStatus":"GRADUATED","admissionDate":"2022-03-01",
                                 "graduationDate":"2026-08-26","gpa":null,"gpaScale":null,
                                 "description":null,"version":1}
                                """))
                .andExpect(status().isOk());

        JobAnalysisSnapshot changed =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        assertThat(changed.profileSnapshotHash()).isNotEqualTo(snapshot.profileSnapshotHash());
        assertThat(changed.contextHash()).isNotEqualTo(snapshot.contextHash());
        assertThat(changed.reusableAnalysisId()).isNull();
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses/latest").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisOutdated").value(true))
                .andExpect(jsonPath("$.outdatedReasons[0]").value("PROFILE_CHANGED"));
    }

    @Test
    void analysisApiPersistsDeterministicResultAndProjectsOwnerScopedLatestHistory()
            throws Exception {
        Session owner = authenticated("analysis-owner@example.com");
        Session other = authenticated("analysis-other@example.com");
        UUID otherVerified =
                evidence(other.userId(), "VERIFIED", "Other owner evidence", "Must stay isolated.");
        UUID verified = evidence(owner.userId(), "VERIFIED", "Spring delivery", "Built Spring APIs.");
        evidence(owner.userId(), "PENDING", "Pending claim", "Must not be used.");
        evidence(owner.userId(), "REJECTED", "Rejected claim", "Must not be used.");
        evidence(owner.userId(), "SOURCE_DELETED", "Deleted claim", "Must not be used.");
        UUID jobId = manualJob(owner, "analysis-job-key-0001", "analysis/one");

        MvcResult accepted = analyze(
                owner, jobId, 0, "ECONOMY", false, "analysis-run-key-0001", 202);
        UUID runId = UUID.fromString(json(accepted).get("agentRunId").asText());
        JobAnalysisSnapshot snapshot =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        assertThat(snapshot.verifiedEvidence())
                .extracting(value -> value.id())
                .containsExactly(verified);
        assertThat(snapshot.profile().introduction()).isNull();

        markRunning(runId);
        var persisted = analysisService.persist(
                owner.userId(), runId, command(snapshot, Eligibility.INELIGIBLE, verified));
        assertThat(persisted.summary().fitScore()).isEqualByComparingTo("100.00");
        assertThat(persisted.summary().eligibility()).isEqualTo(Eligibility.INELIGIBLE);
        assertThat(persisted.scoreBreakdown()).hasSize(1);
        assertThat(persisted.scoreBreakdown().getFirst().evidenceReferences())
                .extracting(value -> value.id())
                .containsExactly(verified);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses/latest")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisVersion").value(1))
                .andExpect(jsonPath("$.eligibility").value("INELIGIBLE"))
                .andExpect(jsonPath("$.fitScore").value(100.00))
                .andExpect(jsonPath("$.analysisOutdated").value(false))
                .andExpect(jsonPath("$.scoreBreakdown[0].category")
                        .value("REQUIRED_QUALIFICATION"))
                .andExpect(jsonPath("$.matchedEvidenceRefs[0].id")
                        .value(verified.toString()));
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses")
                        .cookie(owner.cookie())
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sort", "analysisVersion,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].agentRunId").value(runId.toString()));
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestFitScore").value(100.00))
                .andExpect(jsonPath("$.latestAnalysis.id").value(persisted.summary().id().toString()));
        mockMvc.perform(get("/api/v1/jobs")
                        .cookie(owner.cookie())
                        .queryParam("query", "backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].latestFitScore").value(100.00));

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses/latest")
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses")
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM agent_run_resource_links
                WHERE user_id=? AND agent_run_id=? AND resource_kind='JOB_ANALYSIS'
                  AND job_analysis_id=? AND NOT primary_resource
                """,
                Long.class,
                owner.userId(),
                runId,
                persisted.summary().id())).isEqualTo(1L);
        assertThat(analysisService.isReferenced(owner.userId(), verified)).isTrue();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE job_analyses SET fit_score=0 WHERE id=?",
                        persisted.summary().id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO job_analysis_score_criteria (
                            id,user_id,job_analysis_id,category,criterion,weight,
                            match_level,score,explanation,source_location,criterion_order
                        ) VALUES (?,?,?,'REQUIRED_QUALIFICATION','late',1,'MATCHED',1,
                                  'late mutation',NULL,99)
                        """,
                        UUID.randomUUID(),
                        owner.userId(),
                        persisted.summary().id()))
                .isInstanceOf(DataAccessException.class);

        String evidenceOwnerForeignKey = jdbcTemplate.queryForObject(
                """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname='job_analysis_evidence_links_evidence_owner_fk'
                """,
                String.class);
        assertThat(evidenceOwnerForeignKey)
                .contains("FOREIGN KEY (user_id, profile_evidence_id)")
                .contains("REFERENCES profile_evidence(user_id, id)");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(ignored -> {
                            UUID stagingId = UUID.randomUUID();
                            insertAnalysisCopy(
                                    persisted.summary().id(),
                                    stagingId,
                                    owner.userId(),
                                    2,
                                    "ELIGIBLE");
                            UUID criterionId = insertStagingCriterion(owner.userId(), stagingId);
                            jdbcTemplate.update(
                                    """
                                    INSERT INTO job_analysis_evidence_links (
                                        id,user_id,job_analysis_id,score_criterion_id,
                                        profile_evidence_id,evidence_version,evidence_hash,
                                        usage_type,created_at
                                    ) VALUES (?,?,?,?,?,0,?,'CRITERION_MATCH',?)
                                    """,
                                    UUID.randomUUID(),
                                    owner.userId(),
                                    stagingId,
                                    criterionId,
                                    otherVerified,
                                    "0".repeat(64),
                                    java.sql.Timestamp.from(NOW));
                        }))
                .hasStackTraceContaining("job analysis provenance requires verified active evidence");
        assertThatThrownBy(() -> insertAnalysisCopy(
                        persisted.summary().id(),
                        UUID.randomUUID(),
                        owner.userId(),
                        2,
                        "INVALID"))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("job_analyses_eligibility_ck");
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(ignored -> insertAnalysisCopy(
                                persisted.summary().id(),
                                UUID.randomUUID(),
                                owner.userId(),
                                2,
                                "ELIGIBLE")))
                .hasStackTraceContaining("a persisted job analysis requires at least one criterion");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM job_analyses WHERE user_id=? AND job_posting_id=?",
                        Long.class,
                        owner.userId(),
                        jobId))
                .isEqualTo(1L);
    }

    @Test
    void historicalAnalysisRemainsReadableWhenVerifiedEvidenceIsRejectedThenSourceDeleted()
            throws Exception {
        Session owner = authenticated("analysis-history-owner@example.com");
        Session other = authenticated("analysis-history-other@example.com");
        UUID verified = evidence(
                owner.userId(),
                "VERIFIED",
                "Historical Spring delivery",
                "Delivered production Spring systems.");
        UUID jobId = manualJob(owner, "analysis-history-job-01", "analysis/history");
        UUID runId = UUID.fromString(json(analyze(
                        owner, jobId, 0, "ECONOMY", false, "analysis-history-run-01", 202))
                .get("agentRunId")
                .asText());
        JobAnalysisSnapshot snapshot =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        markRunning(runId);
        var persisted = analysisService.persist(
                owner.userId(), runId, command(snapshot, Eligibility.ELIGIBLE, verified));

        jdbcTemplate.update(
                """
                UPDATE profile_evidence
                SET verification_status='REJECTED',verified_at=NULL,version=1,updated_at=?
                WHERE user_id=? AND id=?
                """,
                java.sql.Timestamp.from(NOW.plusSeconds(1)),
                owner.userId(),
                verified);

        assertHistoricalEvidenceProjection(
                owner,
                jobId,
                persisted.summary().id(),
                verified,
                "REJECTED",
                false,
                "Historical Spring delivery");
        assertHistoricalOwnerScope(other, jobId);

        profileStore.tombstoneEvidence(owner.userId(), verified, NOW.plusSeconds(2));

        assertHistoricalEvidenceProjection(
                owner,
                jobId,
                persisted.summary().id(),
                verified,
                "SOURCE_DELETED",
                true,
                null);
        assertHistoricalOwnerScope(other, jobId);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT evidence_version
                        FROM job_analysis_evidence_links
                        WHERE user_id=? AND job_analysis_id=? AND profile_evidence_id=?
                          AND usage_type='CRITERION_MATCH'
                        """,
                        Long.class,
                        owner.userId(),
                        persisted.summary().id(),
                        verified))
                .isZero();
    }

    @Test
    void idempotencyReplaysAfterJobChangesAndValidatesQualityOwnershipAndInput()
            throws Exception {
        Session owner = authenticated("analysis-idempotent@example.com");
        Session other = authenticated("analysis-idempotent-other@example.com");
        UUID jobId = manualJob(owner, "analysis-job-key-0002", "analysis/idempotency");
        MvcResult first = analyze(
                owner, jobId, 0, "ECONOMY", false, "analysis-replay-key-01", 202);
        String runId = json(first).get("agentRunId").asText();

        mockMvc.perform(put("/api/v1/jobs/" + jobId)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "descriptionText":"Changed job content after the accepted analysis request.",
                                  "version":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        analyze(owner, jobId, 0, "ECONOMY", false, "analysis-replay-key-01", 202)
                .getResponse();
        MvcResult replay = analyze(
                owner, jobId, 0, "ECONOMY", false, "analysis-replay-key-01", 202);
        assertThat(json(replay).get("agentRunId").asText()).isEqualTo(runId);
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");

        MvcResult reusedDifferentBody = analyze(
                owner, jobId, 0, "BALANCED", false, "analysis-replay-key-01", 409);
        assertThat(json(reusedDifferentBody).get("code").asText())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        MvcResult stale = analyze(
                owner, jobId, 0, "ECONOMY", false, "analysis-stale-key-01", 409);
        assertThat(json(stale).get("code").asText())
                .isEqualTo("RESOURCE_VERSION_CONFLICT");
        MvcResult high = analyze(
                owner, jobId, 1, "HIGH_QUALITY", false, "analysis-high-key-001", 400);
        assertThat(json(high).get("code").asText())
                .isEqualTo("QUALITY_MODE_NOT_SUPPORTED");
        analyze(other, jobId, 1, "ECONOMY", false, "analysis-owner-key-01", 404);

        UUID emptyJob = urlOnlyJob(owner, "analysis-empty-job-01", "analysis/empty");
        MvcResult insufficient = analyze(
                owner, emptyJob, 0, "ECONOMY", false, "analysis-empty-run-01", 409);
        assertThat(json(insufficient).get("code").asText())
                .isEqualTo("INSUFFICIENT_JOB_DATA");
        mockMvc.perform(get("/api/v1/jobs/" + emptyJob + "/analyses/latest")
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_ANALYSIS_NOT_FOUND"));
    }

    @Test
    void exactSnapshotCanReuseAndForceCreatesNewVersionWhileOutdatedReasonsKeepHistory()
            throws Exception {
        Session owner = authenticated("analysis-reuse@example.com");
        UUID verified = evidence(
                owner.userId(), "VERIFIED", "Reliable delivery", "Delivered production systems.");
        UUID jobId = manualJob(owner, "analysis-job-key-0003", "analysis/reuse");
        UUID firstRun = UUID.fromString(json(analyze(
                        owner, jobId, 0, "ECONOMY", false, "analysis-first-key-01", 202))
                .get("agentRunId")
                .asText());
        JobAnalysisSnapshot firstSnapshot =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        markRunning(firstRun);
        var first = analysisService.persist(
                owner.userId(), firstRun, command(firstSnapshot, Eligibility.ELIGIBLE, verified));

        UUID reuseRun = UUID.fromString(json(analyze(
                        owner, jobId, 0, "ECONOMY", false, "analysis-reuse-key-01", 202))
                .get("agentRunId")
                .asText());
        JobAnalysisSnapshot reusable =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        assertThat(reusable.reusableAnalysisId()).isEqualTo(first.summary().id());
        markRunning(reuseRun);
        analysisService.attachReusable(
                owner.userId(),
                reuseRun,
                jobId,
                reusable.reusableAnalysisId(),
                reusable.contextHash());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_analyses WHERE user_id=? AND job_posting_id=?",
                Long.class,
                owner.userId(),
                jobId)).isEqualTo(1L);

        UUID forceRun = UUID.fromString(json(analyze(
                        owner, jobId, 0, "ECONOMY", true, "analysis-force-key-01", 202))
                .get("agentRunId")
                .asText());
        JobAnalysisSnapshot forced =
                analysisService.loadSnapshot(owner.userId(), jobId, 0, AiQualityMode.ECONOMY, null);
        markRunning(forceRun);
        var second = analysisService.persist(
                owner.userId(), forceRun, command(forced, Eligibility.ELIGIBLE, verified));
        assertThat(second.summary().analysisVersion()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_analyses WHERE user_id=? AND job_posting_id=?",
                Long.class,
                owner.userId(),
                jobId)).isEqualTo(2L);

        jdbcTemplate.update(
                """
                UPDATE user_profiles SET introduction='Profile changed',version=version+1,
                    updated_at=? WHERE user_id=?
                """,
                java.sql.Timestamp.from(NOW.plusSeconds(1)),
                owner.userId());
        jdbcTemplate.update(
                """
                UPDATE profile_evidence SET content='Approved evidence changed',
                    version=version+1,updated_at=? WHERE user_id=? AND id=?
                """,
                java.sql.Timestamp.from(NOW.plusSeconds(2)),
                owner.userId(),
                verified);
        mockMvc.perform(put("/api/v1/jobs/" + jobId)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "descriptionText":"The job content changed after immutable analyses.",
                                  "version":0
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses/latest")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisVersion").value(2))
                .andExpect(jsonPath("$.analysisOutdated").value(true))
                .andExpect(jsonPath("$.outdatedReasons[0]").value("JOB_CONTENT_CHANGED"))
                .andExpect(jsonPath("$.outdatedReasons[1]").value("PROFILE_CHANGED"))
                .andExpect(jsonPath("$.outdatedReasons[2]").value("EVIDENCE_CHANGED"));
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].analysisVersion").value(2))
                .andExpect(jsonPath("$.items[1].analysisVersion").value(1));

        MvcResult replay = analyze(
                owner, jobId, 0, "ECONOMY", true, "analysis-force-key-01", 202);
        assertThat(json(replay).get("agentRunId").asText()).isEqualTo(forceRun.toString());
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
    }

    private PersistJobAnalysis command(
            JobAnalysisSnapshot snapshot, Eligibility eligibility, UUID evidenceId) {
        var requirement = new RequirementItem(
                FitCriterionCategory.REQUIRED_QUALIFICATION,
                "Spring backend delivery",
                true,
                "requirements");
        return new PersistJobAnalysis(
                snapshot.jobId(),
                snapshot.jobVersion(),
                snapshot.jobContentHash(),
                snapshot.profileSnapshotHash(),
                snapshot.evidenceSnapshotHash(),
                snapshot.contextHash(),
                snapshot.qualityMode(),
                eligibility,
                List.of(new CriterionDraft(
                        FitCriterionCategory.REQUIRED_QUALIFICATION,
                        "Spring backend delivery",
                        MatchLevel.MATCHED,
                        "The approved evidence matches this requirement.",
                        "requirements",
                        List.of(evidenceId))),
                List.of(requirement),
                List.of(requirement),
                List.of(),
                List.of("Approved evidence demonstrates relevant delivery."),
                List.of("The posting has additional areas to confirm."),
                List.of(new EvidenceUsage(evidenceId, JobAnalysisEvidenceUsageType.STRENGTH)),
                "Deterministic analysis summary.");
    }

    private void assertHistoricalEvidenceProjection(
            Session owner,
            UUID jobId,
            UUID analysisId,
            UUID evidenceId,
            String verificationStatus,
            boolean sourceDeleted,
            String expectedTitle)
            throws Exception {
        var latest = mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses/latest")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId.toString()))
                .andExpect(jsonPath("$.analysisVersion").value(1))
                .andExpect(jsonPath("$.analysisOutdated").value(true))
                .andExpect(jsonPath("$.outdatedReasons[0]").value("EVIDENCE_CHANGED"))
                .andExpect(jsonPath("$.scoreBreakdown[0].evidenceRefs[0].id")
                        .value(evidenceId.toString()))
                .andExpect(jsonPath("$.scoreBreakdown[0].evidenceRefs[0].evidenceCategory")
                        .value("EXPERIENCE"))
                .andExpect(jsonPath("$.scoreBreakdown[0].evidenceRefs[0].verificationStatus")
                        .value(verificationStatus))
                .andExpect(jsonPath("$.scoreBreakdown[0].evidenceRefs[0].sourceType")
                        .value("MANUAL"))
                .andExpect(jsonPath("$.scoreBreakdown[0].evidenceRefs[0].sourceDeleted")
                        .value(sourceDeleted))
                .andExpect(jsonPath("$.matchedEvidenceRefs[0].id")
                        .value(evidenceId.toString()))
                .andExpect(jsonPath("$.matchedEvidenceRefs[0].verificationStatus")
                        .value(verificationStatus))
                .andExpect(jsonPath("$.matchedEvidenceRefs[0].sourceDeleted")
                        .value(sourceDeleted));
        if (expectedTitle == null) {
            latest.andExpect(jsonPath("$.matchedEvidenceRefs[0].title").isNotEmpty());
        } else {
            latest.andExpect(jsonPath("$.matchedEvidenceRefs[0].title").value(expectedTitle));
        }
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses")
                        .cookie(owner.cookie())
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sort", "analysisVersion,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(analysisId.toString()))
                .andExpect(jsonPath("$.items[0].analysisOutdated").value(true))
                .andExpect(jsonPath("$.items[0].outdatedReasons[0]")
                        .value("EVIDENCE_CHANGED"));
    }

    private void assertHistoricalOwnerScope(Session other, UUID jobId) throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses/latest")
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/analyses")
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound());
    }

    private MvcResult analyze(
            Session session,
            UUID jobId,
            long version,
            String qualityMode,
            boolean force,
            String key,
            int expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/jobs/" + jobId + "/analysis")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qualityMode":"%s",
                                  "forceReanalyze":%s,
                                  "jobVersion":%d
                                }
                                """.formatted(qualityMode, force, version)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return result;
    }

    private UUID manualJob(Session owner, String key, String path) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/jobs")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl":"https://jobs.example.com/%s",
                                  "companyName":"Analysis Company",
                                  "positionName":"Backend Engineer",
                                  "descriptionText":"Build reliable Spring backend services and collaborate with product teams."
                                }
                                """.formatted(path)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("jobId").asText());
    }

    private UUID urlOnlyJob(Session owner, String key, String path) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/jobs")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceUrl":"https://jobs.example.com/%s"}
                                """.formatted(path)))
                .andExpect(status().isAccepted())
                .andReturn();
        return UUID.fromString(json(result).get("jobId").asText());
    }

    private UUID evidence(
            UUID userId, String status, String title, String content) {
        UUID id = UUID.randomUUID();
        Instant sourceDeleted = "SOURCE_DELETED".equals(status) ? NOW : null;
        Instant verified = "VERIFIED".equals(status) ? NOW : null;
        jdbcTemplate.update(
                """
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    ?,?,'MANUAL',NULL,NULL,'EXPERIENCE',?,?,CAST('{}' AS jsonb),NULL,?,?,?,0,?,?
                )
                """,
                id,
                userId,
                title,
                content,
                status,
                verified == null ? null : java.sql.Timestamp.from(verified),
                sourceDeleted == null ? null : java.sql.Timestamp.from(sourceDeleted),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));
        return id;
    }

    private int insertAnalysisCopy(
            UUID sourceId,
            UUID analysisId,
            UUID userId,
            int analysisVersion,
            String eligibility) {
        return jdbcTemplate.update(
                """
                INSERT INTO job_analyses (
                    id,user_id,job_posting_id,analysis_version,job_version,
                    job_content_hash,profile_snapshot_hash,evidence_snapshot_hash,
                    context_hash,eligibility,fit_score,responsibilities,
                    required_qualifications,preferred_qualifications,strengths,gaps,
                    analysis_summary,rubric_version,workflow_version,quality_mode,
                    embedding_policy_version,embedding_generation,retrieval_policy_version,
                    agent_run_id,sealed,created_at
                )
                SELECT ?,?,job_posting_id,?,job_version,
                    job_content_hash,profile_snapshot_hash,evidence_snapshot_hash,
                    context_hash,?,fit_score,responsibilities,
                    required_qualifications,preferred_qualifications,strengths,gaps,
                    analysis_summary,rubric_version,workflow_version,quality_mode,
                    embedding_policy_version,embedding_generation,retrieval_policy_version,
                    agent_run_id,false,created_at
                FROM job_analyses
                WHERE id=?
                """,
                analysisId,
                userId,
                analysisVersion,
                eligibility,
                sourceId);
    }

    private UUID insertStagingCriterion(UUID userId, UUID analysisId) {
        UUID criterionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_analysis_score_criteria (
                    id,user_id,job_analysis_id,category,criterion,weight,
                    match_level,score,explanation,source_location,criterion_order
                ) VALUES (?,?,?,'REQUIRED_QUALIFICATION','Owner scope',100,
                          'MATCHED',100,'Owner-scoped verified evidence',NULL,0)
                """,
                criterionId,
                userId,
                analysisId);
        return criterionId;
    }

    private void markRunning(UUID runId) {
        jdbcTemplate.update(
                """
                UPDATE agent_runs
                SET status='RUNNING',claim_token=?,claimed_by='p6-test',
                    lease_expires_at=?,heartbeat_at=?,started_at=?,updated_at=?,
                    state_version=state_version+1
                WHERE id=?
                """,
                UUID.randomUUID(),
                java.sql.Timestamp.from(NOW.plusSeconds(60)),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                runId);
    }

    private Session authenticated(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = requiredCookie(csrf);
        String token = json(csrf).get("token").asText();
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password-123", "Candidate", true, true))))
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
        if (cookie == null) {
            throw new AssertionError("SESSION cookie missing");
        }
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
        Clock p6Clock() {
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
