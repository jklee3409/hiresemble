package com.hiresemble.job.infrastructure;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.job.application.model.JobAnalysisModels.EvidenceReference;
import com.hiresemble.job.application.model.JobAnalysisModels.EvidenceUsage;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisPage;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSnapshot;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSummary;
import com.hiresemble.job.application.model.JobAnalysisModels.PersistJobAnalysis;
import com.hiresemble.job.application.model.JobAnalysisModels.RequirementItem;
import com.hiresemble.job.application.model.JobAnalysisModels.StructuredProfileFact;
import com.hiresemble.job.application.model.JobAnalysisModels.ScoreCriterion;
import com.hiresemble.job.application.model.JobAnalysisModels.VerifiedEvidence;
import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.FitCriterionCategory;
import com.hiresemble.job.domain.JobAnalysisEvidenceUsageType;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.job.domain.JobFitScoringPolicy.ScoreResult;
import com.hiresemble.job.domain.JobFitScoringPolicy.ScoredCriterion;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JobAnalysisStore {

    private static final TypeReference<List<RequirementItem>> REQUIREMENTS =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final String ANALYSIS_COLUMNS = """
            id,user_id,job_posting_id,analysis_version,job_version,job_content_hash,
            profile_snapshot_hash,evidence_snapshot_hash,context_hash,eligibility,fit_score,
            analysis_coverage,
            responsibilities::text AS responsibilities_json,
            required_qualifications::text AS required_qualifications_json,
            preferred_qualifications::text AS preferred_qualifications_json,
            strengths::text AS strengths_json,gaps::text AS gaps_json,analysis_summary,
            rubric_version,workflow_version,quality_mode,embedding_policy_version,
            embedding_generation,retrieval_policy_version,agent_run_id,created_at
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JobAnalysisStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public JobAnalysisDetail insert(
            JobAnalysisSnapshot snapshot,
            UUID agentRunId,
            PersistJobAnalysis command,
            ScoreResult score,
            Instant createdAt) {
        UUID analysisId = UUID.randomUUID();
        int analysisVersion = nextAnalysisVersion(snapshot.userId(), snapshot.jobId());
        jdbc.sql("""
                        INSERT INTO job_analyses (
                            id,user_id,job_posting_id,analysis_version,job_version,
                            job_content_hash,profile_snapshot_hash,evidence_snapshot_hash,
                            context_hash,eligibility,fit_score,analysis_coverage,responsibilities,
                            required_qualifications,preferred_qualifications,strengths,gaps,
                            analysis_summary,rubric_version,workflow_version,quality_mode,
                            embedding_policy_version,embedding_generation,
                            retrieval_policy_version,agent_run_id,created_at
                        ) VALUES (
                            :id,:userId,:jobId,:analysisVersion,:jobVersion,
                            :jobContentHash,:profileHash,:evidenceHash,:contextHash,
                            :eligibility,:fitScore,:analysisCoverage,CAST(:responsibilities AS jsonb),
                            CAST(:requiredQualifications AS jsonb),
                            CAST(:preferredQualifications AS jsonb),CAST(:strengths AS jsonb),
                            CAST(:gaps AS jsonb),:analysisSummary,:rubricVersion,
                            :workflowVersion,:qualityMode,:embeddingPolicyVersion,
                            :embeddingGeneration,:retrievalPolicyVersion,:agentRunId,:createdAt
                        )
                        """)
                .param("id", analysisId)
                .param("userId", snapshot.userId())
                .param("jobId", snapshot.jobId())
                .param("analysisVersion", analysisVersion)
                .param("jobVersion", snapshot.jobVersion())
                .param("jobContentHash", snapshot.jobContentHash())
                .param("profileHash", snapshot.profileSnapshotHash())
                .param("evidenceHash", snapshot.evidenceSnapshotHash())
                .param("contextHash", snapshot.contextHash())
                .param("eligibility", command.eligibility().name())
                .param("fitScore", score.totalScore())
                .param("analysisCoverage", score.analysisCoverage())
                .param("responsibilities", json(command.responsibilities()))
                .param("requiredQualifications", json(command.requiredQualifications()))
                .param("preferredQualifications", json(command.preferredQualifications()))
                .param("strengths", json(command.strengths()))
                .param("gaps", json(command.gaps()))
                .param("analysisSummary", command.analysisSummary())
                .param("rubricVersion", snapshot.rubricVersion())
                .param("workflowVersion", snapshot.workflowVersion())
                .param("qualityMode", snapshot.qualityMode().name())
                .param("embeddingPolicyVersion", snapshot.embeddingPolicyVersion())
                .param("embeddingGeneration", snapshot.embeddingGeneration())
                .param("retrievalPolicyVersion", snapshot.retrievalPolicyVersion())
                .param("agentRunId", agentRunId)
                .param("createdAt", utc(createdAt))
                .update();

        Map<UUID, VerifiedEvidence> evidence = snapshot.verifiedEvidence().stream()
                .collect(java.util.stream.Collectors.toMap(VerifiedEvidence::id, value -> value));
        Map<String, StructuredProfileFact> facts = snapshot.profile().structuredFacts().stream()
                .collect(java.util.stream.Collectors.toMap(
                        StructuredProfileFact::reference, value -> value));
        int criterionOrder = 0;
        for (ScoredCriterion criterion : score.criteria()) {
            var criterionDraft = command.criteria().get(criterionOrder);
            UUID criterionId = UUID.randomUUID();
            jdbc.sql("""
                            INSERT INTO job_analysis_score_criteria (
                                id,user_id,job_analysis_id,category,criterion,weight,
                                match_level,score,explanation,source_location,criterion_order
                            ) VALUES (
                                :id,:userId,:analysisId,:category,:criterion,:weight,
                                :matchLevel,:score,:explanation,:sourceLocation,:criterionOrder
                            )
                            """)
                    .param("id", criterionId)
                    .param("userId", snapshot.userId())
                    .param("analysisId", analysisId)
                    .param("category", criterion.category().name())
                    .param("criterion", criterion.criterion())
                    .param("weight", criterion.weight())
                    .param("matchLevel", criterion.matchLevel().name())
                    .param("score", criterion.score())
                    .param("explanation", criterion.explanation())
                    .param("sourceLocation", criterion.sourceLocation())
                    .param("criterionOrder", criterion.order())
                    .update();
            for (UUID evidenceId : new LinkedHashSet<>(criterion.evidenceIds())) {
                insertEvidenceLink(
                        snapshot.userId(),
                        analysisId,
                        criterionId,
                        evidence.get(evidenceId),
                        JobAnalysisEvidenceUsageType.CRITERION_MATCH,
                        createdAt);
            }
            for (String factReference : new LinkedHashSet<>(criterionDraft.structuredFactRefs())) {
                insertStructuredFactLink(
                        snapshot.userId(),
                        analysisId,
                        criterionId,
                        facts.get(factReference),
                        JobAnalysisEvidenceUsageType.CRITERION_MATCH,
                        createdAt);
            }
            criterionOrder++;
        }
        for (EvidenceUsage usage : new LinkedHashSet<>(command.additionalEvidenceUsages())) {
            insertEvidenceLink(
                    snapshot.userId(),
                    analysisId,
                    null,
                    evidence.get(usage.evidenceId()),
                    usage.usageType(),
                    createdAt);
        }
        for (var usage : new LinkedHashSet<>(command.additionalStructuredFactUsages())) {
            insertStructuredFactLink(
                    snapshot.userId(),
                    analysisId,
                    null,
                    facts.get(usage.reference()),
                    usage.usageType(),
                    createdAt);
        }
        int sealed = jdbc.sql("""
                        UPDATE job_analyses SET sealed=true
                        WHERE user_id=:userId AND id=:analysisId AND NOT sealed
                        """)
                .param("userId", snapshot.userId())
                .param("analysisId", analysisId)
                .update();
        if (sealed != 1) {
            throw new IllegalStateException("job analysis could not be sealed");
        }
        attachAnalysis(snapshot.userId(), agentRunId, analysisId, createdAt);
        return findDetail(snapshot.userId(), snapshot.jobId(), analysisId).orElseThrow();
    }

    public void attachAnalysis(
            UUID userId, UUID agentRunId, UUID analysisId, Instant createdAt) {
        jdbc.sql("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,job_analysis_id,
                            primary_resource,created_at
                        ) VALUES (
                            :id,:userId,:agentRunId,'JOB_ANALYSIS',:analysisId,false,:createdAt
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .param("analysisId", analysisId)
                .param("createdAt", utc(createdAt))
                .update();
    }

    public Optional<JobAnalysisDetail> findReusable(
            UUID userId,
            UUID jobId,
            String contextHash,
            AiQualityMode qualityMode) {
        return jdbc.sql("""
                        SELECT id
                        FROM job_analyses
                        WHERE user_id=:userId AND job_posting_id=:jobId
                          AND context_hash=:contextHash AND quality_mode=:qualityMode
                        ORDER BY analysis_version DESC,id DESC
                        LIMIT 1
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .param("contextHash", contextHash)
                .param("qualityMode", qualityMode.name())
                .query(UUID.class)
                .optional()
                .flatMap(id -> findDetail(userId, jobId, id));
    }

    public JobAnalysisPage list(
            UUID userId, UUID jobId, int page, int size, String sort) {
        String order = switch (sort) {
            case "analysisVersion,desc" -> "analysis_version DESC,id DESC";
            case "createdAt,desc" -> "created_at DESC,id DESC";
            default -> throw new IllegalArgumentException("unsupported analysis sort");
        };
        long count = jdbc.sql("""
                        SELECT count(*) FROM job_analyses
                        WHERE user_id=:userId AND job_posting_id=:jobId
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query(Long.class)
                .single();
        List<JobAnalysisSummary> items = jdbc.sql("""
                        SELECT %s
                        FROM job_analyses
                        WHERE user_id=:userId AND job_posting_id=:jobId
                        ORDER BY %s
                        LIMIT :limit OFFSET :offset
                        """.formatted(ANALYSIS_COLUMNS, order))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, row) -> summary(rs))
                .list();
        int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new JobAnalysisPage(items, page, size, count, totalPages);
    }

    public Optional<JobAnalysisDetail> findLatest(UUID userId, UUID jobId) {
        return jdbc.sql("""
                        SELECT id FROM job_analyses
                        WHERE user_id=:userId AND job_posting_id=:jobId
                        ORDER BY analysis_version DESC,id DESC
                        LIMIT 1
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query(UUID.class)
                .optional()
                .flatMap(id -> findDetail(userId, jobId, id));
    }

    public Optional<JobAnalysisDetail> findDetail(
            UUID userId, UUID jobId, UUID analysisId) {
        Optional<StoredAnalysis> stored = jdbc.sql("""
                        SELECT %s
                        FROM job_analyses
                        WHERE user_id=:userId AND job_posting_id=:jobId AND id=:analysisId
                        """.formatted(ANALYSIS_COLUMNS))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("analysisId", analysisId)
                .query((rs, row) -> stored(rs))
                .optional();
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        List<StoredEvidenceLink> links = evidenceLinks(userId, analysisId);
        Map<UUID, List<EvidenceReference>> criterionEvidence = new HashMap<>();
        LinkedHashMap<UUID, EvidenceReference> allEvidence = new LinkedHashMap<>();
        for (StoredEvidenceLink link : links) {
            allEvidence.putIfAbsent(link.reference().id(), link.reference());
            if (link.criterionId() != null
                    && link.usageType() == JobAnalysisEvidenceUsageType.CRITERION_MATCH) {
                criterionEvidence
                        .computeIfAbsent(link.criterionId(), ignored -> new ArrayList<>())
                        .add(link.reference());
            }
        }
        List<ScoreCriterion> criteria = jdbc.sql("""
                        SELECT id,category,criterion,weight,match_level,score,
                               explanation,source_location
                        FROM job_analysis_score_criteria
                        WHERE user_id=:userId AND job_analysis_id=:analysisId
                        ORDER BY criterion_order,id
                        """)
                .param("userId", userId)
                .param("analysisId", analysisId)
                .query((rs, row) -> {
                    UUID criterionId = rs.getObject("id", UUID.class);
                    return new ScoreCriterion(
                            criterionId,
                            FitCriterionCategory.valueOf(rs.getString("category")),
                            rs.getString("criterion"),
                            rs.getBigDecimal("weight"),
                            MatchLevel.valueOf(rs.getString("match_level")),
                            rs.getBigDecimal("score"),
                            criterionEvidence.getOrDefault(criterionId, List.of()),
                            rs.getString("explanation"),
                            rs.getString("source_location"));
                })
                .list();
        StoredAnalysis analysis = stored.get();
        return Optional.of(new JobAnalysisDetail(
                analysis.summary(),
                criteria,
                read(analysis.requiredQualificationsJson(), REQUIREMENTS),
                read(analysis.preferredQualificationsJson(), REQUIREMENTS),
                read(analysis.responsibilitiesJson(), REQUIREMENTS),
                read(analysis.strengthsJson(), STRINGS),
                read(analysis.gapsJson(), STRINGS),
                List.copyOf(allEvidence.values()),
                analysis.analysisSummary()));
    }

    public boolean isEvidenceReferenced(UUID userId, UUID evidenceId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM job_analysis_evidence_links
                            WHERE user_id=:userId AND profile_evidence_id=:evidenceId
                        )
                        """)
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .query(Boolean.class)
                .single();
    }

    private int nextAnalysisVersion(UUID userId, UUID jobId) {
        return jdbc.sql("""
                        SELECT COALESCE(max(analysis_version),0)+1
                        FROM job_analyses
                        WHERE user_id=:userId AND job_posting_id=:jobId
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query(Integer.class)
                .single();
    }

    private void insertEvidenceLink(
            UUID userId,
            UUID analysisId,
            UUID criterionId,
            VerifiedEvidence evidence,
            JobAnalysisEvidenceUsageType usageType,
            Instant createdAt) {
        if (evidence == null) {
            throw new IllegalArgumentException("analysis evidence is not allowlisted");
        }
        jdbc.sql("""
                        INSERT INTO job_analysis_evidence_links (
                            id,user_id,job_analysis_id,score_criterion_id,
                            profile_evidence_id,evidence_version,evidence_hash,usage_type,created_at
                        ) VALUES (
                            :id,:userId,:analysisId,:criterionId,:evidenceId,
                            :evidenceVersion,:evidenceHash,:usageType,:createdAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("analysisId", analysisId)
                .param("criterionId", criterionId)
                .param("evidenceId", evidence.id())
                .param("evidenceVersion", evidence.version())
                .param("evidenceHash", evidence.evidenceHash())
                .param("usageType", usageType.name())
                .param("createdAt", utc(createdAt))
                .update();
    }

    private void insertStructuredFactLink(
            UUID userId,
            UUID analysisId,
            UUID criterionId,
            StructuredProfileFact fact,
            JobAnalysisEvidenceUsageType usageType,
            Instant createdAt) {
        if (fact == null) {
            throw new IllegalArgumentException("analysis structured fact is not allowlisted");
        }
        jdbc.sql("""
                        INSERT INTO job_analysis_structured_fact_links (
                            id,user_id,job_analysis_id,score_criterion_id,source_entity_id,
                            source_entity_version,fact_type,fact_hash,usage_type,created_at
                        ) VALUES (
                            :id,:userId,:analysisId,:criterionId,:sourceEntityId,
                            :sourceEntityVersion,:factType,:factHash,:usageType,:createdAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("analysisId", analysisId)
                .param("criterionId", criterionId)
                .param("sourceEntityId", fact.sourceEntityId())
                .param("sourceEntityVersion", fact.sourceEntityVersion())
                .param("factType", fact.factType().name())
                .param("factHash", fact.factHash())
                .param("usageType", usageType.name())
                .param("createdAt", utc(createdAt))
                .update();
    }

    private List<StoredEvidenceLink> evidenceLinks(UUID userId, UUID analysisId) {
        return jdbc.sql("""
                        SELECT link.score_criterion_id,link.usage_type,
                               evidence.id,evidence.title,evidence.evidence_category,
                               evidence.verification_status,evidence.source_type,
                               evidence.source_deleted_at
                        FROM job_analysis_evidence_links link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=link.user_id
                         AND evidence.id=link.profile_evidence_id
                        WHERE link.user_id=:userId AND link.job_analysis_id=:analysisId
                        ORDER BY link.created_at,link.id
                        """)
                .param("userId", userId)
                .param("analysisId", analysisId)
                .query((rs, row) -> new StoredEvidenceLink(
                        rs.getObject("score_criterion_id", UUID.class),
                        JobAnalysisEvidenceUsageType.valueOf(rs.getString("usage_type")),
                        new EvidenceReference(
                                rs.getObject("id", UUID.class),
                                rs.getString("title"),
                                rs.getString("evidence_category"),
                                EvidenceVerificationStatus.valueOf(
                                        rs.getString("verification_status")),
                                EvidenceSourceType.valueOf(rs.getString("source_type")),
                                rs.getObject("source_deleted_at", OffsetDateTime.class) != null)))
                .list();
    }

    private JobAnalysisSummary summary(ResultSet rs) throws SQLException {
        return new JobAnalysisSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("job_posting_id", UUID.class),
                rs.getInt("analysis_version"),
                Eligibility.valueOf(rs.getString("eligibility")),
                rs.getBigDecimal("fit_score"),
                rs.getBigDecimal("analysis_coverage"),
                false,
                List.of(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("agent_run_id", UUID.class),
                rs.getString("job_content_hash").trim(),
                rs.getString("profile_snapshot_hash").trim(),
                rs.getString("evidence_snapshot_hash").trim(),
                rs.getString("context_hash").trim(),
                AiQualityMode.valueOf(rs.getString("quality_mode")));
    }

    private StoredAnalysis stored(ResultSet rs) throws SQLException {
        return new StoredAnalysis(
                summary(rs),
                rs.getString("responsibilities_json"),
                rs.getString("required_qualifications_json"),
                rs.getString("preferred_qualifications_json"),
                rs.getString("strengths_json"),
                rs.getString("gaps_json"),
                rs.getString("analysis_summary"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("job analysis value could not be serialized", exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored job analysis JSON is invalid", exception);
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record StoredAnalysis(
            JobAnalysisSummary summary,
            String responsibilitiesJson,
            String requiredQualificationsJson,
            String preferredQualificationsJson,
            String strengthsJson,
            String gapsJson,
            String analysisSummary) {}

    private record StoredEvidenceLink(
            UUID criterionId,
            JobAnalysisEvidenceUsageType usageType,
            EvidenceReference reference) {}
}
