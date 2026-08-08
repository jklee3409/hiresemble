package com.hiresemble.careerartifact.infrastructure;

import com.hiresemble.careerartifact.application.RenderedOfficeFile;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Artifact;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.GenerationRequest;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Page;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.ProfileSectionSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Readiness;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.EvidenceUsageType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.GenerationStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.LifecycleStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ProfileSection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CareerArtifactStore {

    private static final String ARTIFACT_SELECT = """
            SELECT artifact.*,
                   current_version.version_no AS current_version_no,
                   CASE
                     WHEN run.status='WAITING_USER' THEN 'RUNNING'
                     ELSE COALESCE(run.status, 'NOT_STARTED')
                   END AS generation_status
            FROM career_artifacts artifact
            LEFT JOIN career_artifact_versions current_version
              ON current_version.user_id=artifact.user_id
             AND current_version.id=artifact.current_version_id
            LEFT JOIN agent_runs run
              ON run.user_id=artifact.user_id
             AND run.id=artifact.latest_agent_run_id
             AND run.deleted_at IS NULL
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public CareerArtifactStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Readiness readiness(UUID userId) {
        ReadinessCounts counts = jdbc.sql("""
                        SELECT
                          EXISTS (SELECT 1 FROM documents
                            WHERE user_id=:userId AND deleted_at IS NULL
                              AND document_type='RESUME') AS uploaded_resume,
                          EXISTS (SELECT 1 FROM documents
                            WHERE user_id=:userId AND deleted_at IS NULL
                              AND document_type='PORTFOLIO') AS uploaded_portfolio,
                          EXISTS (SELECT 1 FROM career_artifacts
                            WHERE user_id=:userId AND deleted_at IS NULL
                              AND artifact_type='RESUME' AND current_version_id IS NOT NULL)
                            AS generated_resume,
                          EXISTS (SELECT 1 FROM career_artifacts
                            WHERE user_id=:userId AND deleted_at IS NULL
                              AND artifact_type='PORTFOLIO' AND current_version_id IS NOT NULL)
                            AS generated_portfolio,
                          (SELECT count(*) FROM experience_items experience
                            JOIN profile_evidence evidence
                              ON evidence.user_id=experience.user_id
                             AND evidence.id=experience.canonical_evidence_id
                            WHERE experience.user_id=:userId
                              AND experience.deleted_at IS NULL
                              AND experience.verification_status='VERIFIED'
                              AND evidence.source_type='EXPERIENCE'
                              AND evidence.source_entity_id=experience.id
                              AND evidence.verification_status='VERIFIED') AS verified_count,
                          (SELECT count(*) FROM experience_items experience
                            JOIN profile_evidence canonical
                              ON canonical.user_id=experience.user_id
                             AND canonical.id=experience.canonical_evidence_id
                            WHERE experience.user_id=:userId
                              AND experience.deleted_at IS NULL
                              AND experience.verification_status='VERIFIED'
                              AND canonical.source_type='EXPERIENCE'
                              AND canonical.verification_status='VERIFIED'
                              AND EXISTS (
                                SELECT 1 FROM experience_evidence_links link
                                JOIN profile_evidence source
                                  ON source.user_id=link.user_id
                                 AND source.id=link.profile_evidence_id
                                JOIN github_sources github_source
                                  ON github_source.user_id=source.user_id
                                 AND github_source.id=source.github_source_id
                                WHERE link.user_id=experience.user_id
                                  AND link.experience_item_id=experience.id
                                  AND source.source_type='GITHUB_REPOSITORY'
                                  AND source.source_deleted_at IS NULL
                                  AND github_source.deleted_at IS NULL)) AS github_count,
                          (SELECT count(*) FROM experience_items experience
                            JOIN profile_evidence evidence
                              ON evidence.user_id=experience.user_id
                             AND evidence.id=experience.canonical_evidence_id
                            WHERE experience.user_id=:userId
                              AND experience.deleted_at IS NULL
                              AND experience.verification_status='VERIFIED'
                              AND evidence.verification_status='VERIFIED'
                              AND upper(regexp_replace(experience.evidence_category,
                                    '[[:space:]_-]+', '', 'g')) IN ('STRENGTH','강점','역량'))
                            AS strength_count,
                          (SELECT count(*) FROM experience_items experience
                            JOIN profile_evidence evidence
                              ON evidence.user_id=experience.user_id
                             AND evidence.id=experience.canonical_evidence_id
                            WHERE experience.user_id=:userId
                              AND experience.deleted_at IS NULL
                              AND experience.verification_status='VERIFIED'
                              AND evidence.verification_status='VERIFIED'
                              AND upper(regexp_replace(experience.evidence_category,
                                    '[[:space:]_-]+', '', 'g')) IN
                                    ('PROJECT','프로젝트','CAREER','경력')) AS project_career_count
                        """)
                .param("userId", userId)
                .query((rs, row) -> new ReadinessCounts(
                        rs.getBoolean("uploaded_resume"),
                        rs.getBoolean("uploaded_portfolio"),
                        rs.getBoolean("generated_resume"),
                        rs.getBoolean("generated_portfolio"),
                        rs.getInt("verified_count"),
                        rs.getInt("github_count"),
                        rs.getInt("strength_count"),
                        rs.getInt("project_career_count")))
                .single();
        List<String> warnings = new ArrayList<>();
        if (counts.verifiedCount() == 0) {
            warnings.add("승인된 경력 근거가 한 개 이상 필요합니다.");
        }
        if (counts.projectCareerCount() < 2) {
            warnings.add("프로젝트 또는 경력 근거가 두 개 미만입니다.");
        }
        if (counts.strengthCount() < 1) {
            warnings.add("강점 또는 역량 근거가 아직 없습니다.");
        }
        boolean canGenerate = counts.verifiedCount() > 0;
        return new Readiness(
                counts.uploadedResume(), counts.uploadedPortfolio(),
                counts.generatedResume(), counts.generatedPortfolio(),
                counts.verifiedCount(), counts.githubCount(), counts.strengthCount(),
                canGenerate, canGenerate, warnings);
    }

    @Transactional(readOnly = true)
    public List<VerifiedEvidence> verifiedEvidence(
            UUID userId, List<UUID> experienceIds) {
        if (experienceIds == null || experienceIds.isEmpty()) return List.of();
        return jdbc.sql("""
                        SELECT experience.id AS experience_id,
                               experience.version AS experience_version,
                               evidence.id AS evidence_id,
                               evidence.version AS evidence_version,
                               experience.evidence_category,
                               experience.title,
                               experience.content,
                               EXISTS (
                                 SELECT 1 FROM experience_evidence_links link
                                 JOIN profile_evidence source
                                   ON source.user_id=link.user_id
                                  AND source.id=link.profile_evidence_id
                                 JOIN github_sources github_source
                                   ON github_source.user_id=source.user_id
                                  AND github_source.id=source.github_source_id
                                 WHERE link.user_id=experience.user_id
                                   AND link.experience_item_id=experience.id
                                   AND source.source_type='GITHUB_REPOSITORY'
                                   AND source.source_deleted_at IS NULL
                                   AND github_source.deleted_at IS NULL
                               ) AS github_provenance
                        FROM experience_items experience
                        JOIN profile_evidence evidence
                          ON evidence.user_id=experience.user_id
                         AND evidence.id=experience.canonical_evidence_id
                        WHERE experience.user_id=:userId
                          AND experience.id IN (:experienceIds)
                          AND experience.deleted_at IS NULL
                          AND experience.verification_status='VERIFIED'
                          AND evidence.source_type='EXPERIENCE'
                          AND evidence.source_entity_id=experience.id
                          AND evidence.verification_status='VERIFIED'
                        ORDER BY experience.id
                        """)
                .param("userId", userId)
                .param("experienceIds", experienceIds)
                .query((rs, row) -> new VerifiedEvidence(
                        rs.getObject("experience_id", UUID.class),
                        rs.getLong("experience_version"),
                        rs.getObject("evidence_id", UUID.class),
                        rs.getLong("evidence_version"),
                        rs.getString("evidence_category"),
                        rs.getString("title"),
                        rs.getString("content"),
                        usage(rs.getString("evidence_category")),
                        rs.getBoolean("github_provenance")))
                .list();
    }

    @Transactional(readOnly = true)
    public int activeOwnedExperienceCount(
            UUID userId, List<UUID> experienceIds) {
        if (experienceIds == null || experienceIds.isEmpty()) return 0;
        return jdbc.sql("""
                        SELECT count(*) FROM experience_items
                        WHERE user_id=:userId AND id IN (:experienceIds)
                          AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("experienceIds", experienceIds)
                .query(Integer.class)
                .single();
    }

    @Transactional(readOnly = true)
    public List<ProfileSectionSnapshot> profileSnapshots(
            UUID userId, Set<ProfileSection> sections) {
        List<ProfileSectionSnapshot> result = new ArrayList<>();
        for (ProfileSection section : ProfileSection.values()) {
            if (sections.contains(section)) result.addAll(profileSection(userId, section));
        }
        return List.copyOf(result);
    }

    private List<ProfileSectionSnapshot> profileSection(
            UUID userId, ProfileSection section) {
        String sql = switch (section) {
            case PROFILE -> """
                    SELECT id,version,jsonb_build_object(
                      'introduction',introduction,
                      'desiredRoles',desired_roles,
                      'desiredIndustries',desired_industries,
                      'desiredLocations',desired_locations,
                      'expectedGraduationDate',expected_graduation_date)::text safe_content
                    FROM user_profiles WHERE user_id=:userId
                    """;
            case EDUCATIONS -> """
                    SELECT id,version,jsonb_build_object(
                      'schoolName',school_name,'major',major,'degree',degree,
                      'educationStatus',education_status,'admissionDate',admission_date,
                      'graduationDate',graduation_date,'gpa',gpa,'gpaScale',gpa_scale,
                      'description',description)::text safe_content
                    FROM educations WHERE user_id=:userId AND deleted_at IS NULL
                    ORDER BY is_primary DESC,graduation_date DESC NULLS LAST,id LIMIT 50
                    """;
            case CERTIFICATIONS -> """
                    SELECT id,version,jsonb_build_object(
                      'name',name,'issuer',issuer,'acquiredDate',acquired_date,
                      'expiresAt',expires_at,'description',description)::text safe_content
                    FROM certifications WHERE user_id=:userId AND deleted_at IS NULL
                    ORDER BY acquired_date DESC NULLS LAST,id LIMIT 50
                    """;
            case LANGUAGE_SCORES -> """
                    SELECT id,version,jsonb_build_object(
                      'testName',test_name,'score',score,'grade',grade,
                      'testedAt',tested_at,'expiresAt',expires_at)::text safe_content
                    FROM language_scores WHERE user_id=:userId AND deleted_at IS NULL
                    ORDER BY tested_at DESC NULLS LAST,id LIMIT 50
                    """;
            case AWARDS -> """
                    SELECT id,version,jsonb_build_object(
                      'name',name,'organizer',organizer,'awardedAt',awarded_at,
                      'description',description)::text safe_content
                    FROM awards WHERE user_id=:userId AND deleted_at IS NULL
                    ORDER BY awarded_at DESC NULLS LAST,id LIMIT 50
                    """;
            case CAREERS -> """
                    SELECT id,version,jsonb_build_object(
                      'organization',organization,'position',position,
                      'employmentType',employment_type,'startedAt',started_at,
                      'endedAt',ended_at,'current',is_current,
                      'responsibilities',responsibilities,'achievements',achievements)::text safe_content
                    FROM careers WHERE user_id=:userId AND deleted_at IS NULL
                    ORDER BY is_current DESC,started_at DESC NULLS LAST,id LIMIT 50
                    """;
            case ACTIVITIES -> """
                    SELECT id,version,jsonb_build_object(
                      'title',title,'activityType',activity_type,'organizer',organizer,
                      'startedAt',started_at,'endedAt',ended_at,'ongoing',ongoing,
                      'role',role,'description',description,'achievements',achievements)::text safe_content
                    FROM activities WHERE user_id=:userId AND deleted_at IS NULL
                      AND use_as_material
                    ORDER BY ongoing DESC,started_at DESC NULLS LAST,id LIMIT 50
                    """;
        };
        return jdbc.sql(sql)
                .param("userId", userId)
                .query((rs, row) -> new ProfileSectionSnapshot(
                        section.name(),
                        rs.getObject("id", UUID.class),
                        rs.getLong("version"),
                        readTree(rs.getString("safe_content"))))
                .list();
    }

    @Transactional
    public Artifact create(
            UUID id, UUID userId, ArtifactType type, String title, Instant now) {
        jdbc.sql("""
                        INSERT INTO career_artifacts (
                          id,user_id,artifact_type,title,lifecycle_status,
                          current_version_id,latest_agent_run_id,version,
                          created_at,updated_at,deleted_at
                        ) VALUES (
                          :id,:userId,:type,:title,'ACTIVE',NULL,NULL,0,:now,:now,NULL)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("type", type.name())
                .param("title", title)
                .param("now", utc(now))
                .update();
        return find(userId, id).orElseThrow();
    }

    @Transactional
    public void insertGenerationRequest(
            UUID id,
            UUID userId,
            UUID artifactId,
            UUID agentRunId,
            UUID targetVersionId,
            JsonNode renderProfile,
            String renderProfileHash,
            Instant now) {
        int inserted = jdbc.sql("""
                        INSERT INTO career_artifact_generation_requests (
                          id,user_id,career_artifact_id,agent_run_id,target_version_id,
                          render_profile_snapshot,render_profile_hash,created_at,consumed_at
                        ) VALUES (
                          :id,:userId,:artifactId,:runId,:targetVersionId,
                          CAST(:renderProfile AS jsonb),:renderProfileHash,:now,NULL)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("runId", agentRunId)
                .param("targetVersionId", targetVersionId)
                .param("renderProfile", write(renderProfile))
                .param("renderProfileHash", renderProfileHash)
                .param("now", utc(now))
                .update();
        if (inserted != 1) throw new IllegalStateException("generation request was not created");
    }

    @Transactional
    public Artifact attachLatestRun(
            UUID userId, UUID artifactId, UUID runId, long expectedVersion, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE career_artifacts
                        SET latest_agent_run_id=:runId,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:artifactId AND deleted_at IS NULL
                          AND lifecycle_status='ACTIVE' AND version=:expectedVersion
                        """)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) throw new IllegalStateException("artifact generation attach conflicted");
        return find(userId, artifactId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<Artifact> find(UUID userId, UUID artifactId) {
        return jdbc.sql(ARTIFACT_SELECT + """
                        WHERE artifact.user_id=:userId AND artifact.id=:artifactId
                          AND artifact.deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .query(this::artifact)
                .optional();
    }

    @Transactional
    public Optional<Artifact> lock(UUID userId, UUID artifactId) {
        jdbc.sql("""
                        SELECT id FROM career_artifacts
                        WHERE user_id=:userId AND id=:artifactId AND deleted_at IS NULL
                        FOR UPDATE
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .query(UUID.class)
                .optional();
        return find(userId, artifactId);
    }

    @Transactional(readOnly = true)
    public Page<Artifact> list(
            UUID userId,
            ArtifactType type,
            LifecycleStatus lifecycle,
            int page,
            int size,
            String order) {
        String filter = " WHERE artifact.user_id=:userId AND artifact.deleted_at IS NULL"
                + (type == null ? "" : " AND artifact.artifact_type=:type")
                + (lifecycle == null ? "" : " AND artifact.lifecycle_status=:lifecycle");
        JdbcClient.StatementSpec countSpec = jdbc.sql(
                        "SELECT count(*) FROM career_artifacts artifact" + filter)
                .param("userId", userId);
        if (type != null) countSpec = countSpec.param("type", type.name());
        if (lifecycle != null) countSpec = countSpec.param("lifecycle", lifecycle.name());
        long total = countSpec.query(Long.class).single();
        JdbcClient.StatementSpec listSpec = jdbc.sql(
                        ARTIFACT_SELECT + filter + " ORDER BY " + order
                                + " LIMIT :limit OFFSET :offset")
                .param("userId", userId)
                .param("limit", size)
                .param("offset", page * size);
        if (type != null) listSpec = listSpec.param("type", type.name());
        if (lifecycle != null) listSpec = listSpec.param("lifecycle", lifecycle.name());
        List<Artifact> items = listSpec.query(this::artifact).list();
        return new Page<>(items, page, size, total, pages(total, size));
    }

    @Transactional(readOnly = true)
    public Optional<Version> findVersion(
            UUID userId, UUID artifactId, UUID versionId) {
        return jdbc.sql("""
                        SELECT * FROM career_artifact_versions
                        WHERE user_id=:userId AND career_artifact_id=:artifactId AND id=:versionId
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("versionId", versionId)
                .query(this::version)
                .optional();
    }

    @Transactional(readOnly = true)
    public Page<Version> versions(
            UUID userId, UUID artifactId, int page, int size, String order) {
        long total = jdbc.sql("""
                        SELECT count(*) FROM career_artifact_versions
                        WHERE user_id=:userId AND career_artifact_id=:artifactId
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .query(Long.class)
                .single();
        List<Version> items = jdbc.sql("""
                        SELECT * FROM career_artifact_versions
                        WHERE user_id=:userId AND career_artifact_id=:artifactId
                        ORDER BY %s LIMIT :limit OFFSET :offset
                        """.formatted(order))
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("limit", size)
                .param("offset", page * size)
                .query(this::version)
                .list();
        return new Page<>(items, page, size, total, pages(total, size));
    }

    @Transactional(readOnly = true)
    public List<EvidenceProjection> evidence(UUID userId, UUID versionId) {
        return jdbc.sql("""
                        SELECT experience_item_id,profile_evidence_id,usage_type,title_snapshot
                        FROM career_artifact_evidence_links
                        WHERE user_id=:userId AND artifact_version_id=:versionId
                        ORDER BY created_at,id
                        """)
                .param("userId", userId)
                .param("versionId", versionId)
                .query((rs, row) -> new EvidenceProjection(
                        rs.getObject("experience_item_id", UUID.class),
                        rs.getObject("profile_evidence_id", UUID.class),
                        EvidenceUsageType.valueOf(rs.getString("usage_type")),
                        rs.getString("title_snapshot")))
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<GenerationRequest> generationRequest(UUID userId, UUID runId) {
        return jdbc.sql("""
                        SELECT * FROM career_artifact_generation_requests
                        WHERE user_id=:userId AND agent_run_id=:runId
                        """)
                .param("userId", userId)
                .param("runId", runId)
                .query(this::generationRequest)
                .optional();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveGeneration(UUID userId, UUID artifactId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                          SELECT 1 FROM agent_run_resource_links link
                          JOIN agent_runs run
                            ON run.user_id=link.user_id AND run.id=link.agent_run_id
                          WHERE link.user_id=:userId
                            AND link.career_artifact_id=:artifactId
                            AND link.resource_kind='CAREER_ARTIFACT'
                            AND link.primary_resource
                            AND run.deleted_at IS NULL
                            AND run.status IN ('QUEUED','RUNNING','WAITING_USER'))
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .query(Boolean.class)
                .single();
    }

    @Transactional
    public Artifact changeLifecycle(
            UUID userId,
            UUID artifactId,
            LifecycleStatus expected,
            LifecycleStatus target,
            long expectedVersion,
            Instant now) {
        int updated = jdbc.sql("""
                        UPDATE career_artifacts
                        SET lifecycle_status=:target,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:artifactId AND deleted_at IS NULL
                          AND lifecycle_status=:expected AND version=:expectedVersion
                        """)
                .param("target", target.name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("expected", expected.name())
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) return null;
        return find(userId, artifactId).orElseThrow();
    }

    @Transactional
    public Version applyVersion(
            UUID userId,
            UUID artifactId,
            UUID runId,
            UUID targetVersionId,
            ArtifactType type,
            String model,
            String templateKey,
            String templateVersion,
            JsonNode content,
            String contentSchemaVersion,
            RenderedOfficeFile file,
            String storageKey,
            List<VerifiedEvidence> selectedEvidence,
            long expectedArtifactVersion,
            Instant now) {
        Version existing = findVersion(userId, artifactId, targetVersionId).orElse(null);
        if (existing != null) return existing;
        Artifact artifact = lock(userId, artifactId).orElseThrow();
        if (artifact.artifactType() != type
                || artifact.lifecycleStatus() != LifecycleStatus.ACTIVE
                || !runId.equals(artifact.latestAgentRunId())
                || artifact.version() != expectedArtifactVersion) {
            throw new IllegalStateException("CAREER_ARTIFACT_APPLY_STATE_INVALID");
        }
        GenerationRequest request = generationRequest(userId, runId).orElseThrow();
        if (!request.artifactId().equals(artifactId)
                || !request.targetVersionId().equals(targetVersionId)
                || request.consumedAt() != null) {
            throw new IllegalStateException("CAREER_ARTIFACT_REQUEST_INVALID");
        }
        List<VerifiedEvidence> current = verifiedEvidence(
                userId, selectedEvidence.stream().map(VerifiedEvidence::experienceItemId).toList());
        if (!sameEvidence(selectedEvidence, current)) {
            throw new IllegalStateException("CAREER_ARTIFACT_EVIDENCE_CHANGED");
        }
        int versionNo = jdbc.sql("""
                        SELECT COALESCE(max(version_no),0)+1
                        FROM career_artifact_versions
                        WHERE user_id=:userId AND career_artifact_id=:artifactId
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .query(Integer.class)
                .single();
        jdbc.sql("""
                        INSERT INTO career_artifact_versions (
                          id,user_id,career_artifact_id,version_no,content_schema_version,
                          content_json,template_key,template_version,model_id,agent_run_id,
                          render_profile_snapshot,storage_key,mime_type,size_bytes,
                          checksum_sha256,created_at
                        ) VALUES (
                          :id,:userId,:artifactId,:versionNo,:contentSchemaVersion,
                          CAST(:content AS jsonb),:templateKey,:templateVersion,:model,:runId,
                          CAST(:renderProfile AS jsonb),:storageKey,:mimeType,:sizeBytes,
                          :checksum,:now)
                        """)
                .param("id", targetVersionId)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("versionNo", versionNo)
                .param("contentSchemaVersion", contentSchemaVersion)
                .param("content", write(content))
                .param("templateKey", templateKey)
                .param("templateVersion", templateVersion)
                .param("model", model)
                .param("runId", runId)
                .param("renderProfile", write(request.renderProfileSnapshot()))
                .param("storageKey", storageKey)
                .param("mimeType", file.mimeType())
                .param("sizeBytes", file.sizeBytes())
                .param("checksum", file.checksumSha256())
                .param("now", utc(now))
                .update();
        for (VerifiedEvidence evidence : selectedEvidence) {
            jdbc.sql("""
                            INSERT INTO career_artifact_evidence_links (
                              id,user_id,artifact_version_id,experience_item_id,
                              profile_evidence_id,experience_version,evidence_version,
                              usage_type,title_snapshot,content_snapshot,snapshot_hash,created_at
                            ) VALUES (
                              :id,:userId,:versionId,:experienceId,:evidenceId,
                              :experienceVersion,:evidenceVersion,:usageType,:title,:content,
                              :snapshotHash,:now)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("userId", userId)
                    .param("versionId", targetVersionId)
                    .param("experienceId", evidence.experienceItemId())
                    .param("evidenceId", evidence.evidenceId())
                    .param("experienceVersion", evidence.experienceVersion())
                    .param("evidenceVersion", evidence.evidenceVersion())
                    .param("usageType", evidence.usageType().name())
                    .param("title", evidence.title())
                    .param("content", evidence.content())
                    .param("snapshotHash", sha256(evidence.title() + "|" + evidence.content()))
                    .param("now", utc(now))
                    .update();
        }
        jdbc.sql("""
                        UPDATE career_artifact_generation_requests
                        SET consumed_at=:now
                        WHERE user_id=:userId AND agent_run_id=:runId AND consumed_at IS NULL
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("runId", runId)
                .update();
        jdbc.sql("""
                        UPDATE career_artifacts
                        SET current_version_id=:versionId,latest_agent_run_id=:runId,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:artifactId AND deleted_at IS NULL
                        """)
                .param("versionId", targetVersionId)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("artifactId", artifactId)
                .update();
        return findVersion(userId, artifactId, targetVersionId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<StoredObject> storedObjects(UUID userId, UUID artifactId) {
        return jdbc.sql("""
                        SELECT id,storage_key FROM career_artifact_versions
                        WHERE user_id=:userId AND career_artifact_id=:artifactId
                        ORDER BY version_no
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .query((rs, row) -> new StoredObject(
                        rs.getObject("id", UUID.class), rs.getString("storage_key")))
                .list();
    }

    @Transactional
    public void softDelete(
            UUID userId, UUID artifactId, long expectedVersion, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE career_artifacts
                        SET deleted_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:artifactId AND deleted_at IS NULL
                          AND version=:expectedVersion
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) throw new IllegalStateException("artifact delete version conflicted");
        jdbc.sql("""
                        UPDATE career_artifact_generation_requests
                        SET render_profile_snapshot='{}'::jsonb
                        WHERE user_id=:userId AND career_artifact_id=:artifactId
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .update();
        jdbc.sql("""
                        UPDATE career_artifact_versions
                        SET render_profile_snapshot='{}'::jsonb
                        WHERE user_id=:userId AND career_artifact_id=:artifactId
                          AND render_profile_snapshot<>'{}'::jsonb
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .update();
    }

    @Transactional
    public CleanupTarget cleanupTarget(UUID userId, UUID runId) {
        return jdbc.sql("""
                        SELECT request.career_artifact_id,request.target_version_id,
                               artifact.current_version_id,
                               artifact.deleted_at IS NOT NULL AS artifact_deleted,
                               EXISTS (SELECT 1 FROM career_artifact_versions version
                                 WHERE version.user_id=request.user_id
                                   AND version.id=request.target_version_id) AS target_exists,
                               run.workflow_type
                        FROM career_artifact_generation_requests request
                        JOIN career_artifacts artifact
                          ON artifact.user_id=request.user_id
                         AND artifact.id=request.career_artifact_id
                        JOIN agent_runs run
                          ON run.user_id=request.user_id AND run.id=request.agent_run_id
                        WHERE request.user_id=:userId AND request.agent_run_id=:runId
                        FOR UPDATE OF artifact,request
                        """)
                .param("userId", userId)
                .param("runId", runId)
                .query((rs, row) -> {
                    UUID artifactId = rs.getObject("career_artifact_id", UUID.class);
                    UUID versionId = rs.getObject("target_version_id", UUID.class);
                    ArtifactType type = "RESUME_GENERATION".equals(rs.getString("workflow_type"))
                            ? ArtifactType.RESUME : ArtifactType.PORTFOLIO;
                    return new CleanupTarget(
                            artifactId,
                            versionId,
                            storageKey(userId, artifactId, versionId, type),
                            rs.getObject("current_version_id", UUID.class),
                            rs.getBoolean("artifact_deleted"),
                            rs.getBoolean("target_exists"));
                })
                .optional()
                .orElse(null);
    }

    @Transactional
    public void compensateFailedGeneration(
            UUID userId, UUID runId, UUID artifactId, Instant now) {
        // The durable request must remain intact for terminal retry. Object cleanup is handled
        // outside this transaction; a failed generation never changes current_version_id.
    }

    @Transactional
    public void compensateHistoryDeletion(UUID userId, UUID runId, Instant now) {
        Optional<GenerationRequest> request = generationRequest(userId, runId);
        if (request.isEmpty()) return;
        UUID artifactId = request.get().artifactId();
        boolean successfulTarget = findVersion(
                userId, artifactId, request.get().targetVersionId()).isPresent();
        if (!successfulTarget) {
            jdbc.sql("""
                            UPDATE career_artifact_generation_requests
                            SET render_profile_snapshot='{}'::jsonb
                            WHERE user_id=:userId AND agent_run_id=:runId
                              AND consumed_at IS NULL
                            """)
                    .param("userId", userId)
                    .param("runId", runId)
                    .update();
            boolean hasVersion = jdbc.sql("""
                            SELECT EXISTS (SELECT 1 FROM career_artifact_versions
                              WHERE user_id=:userId AND career_artifact_id=:artifactId)
                            """)
                    .param("userId", userId)
                    .param("artifactId", artifactId)
                    .query(Boolean.class)
                    .single();
            boolean hasOtherRun = jdbc.sql("""
                            SELECT EXISTS (
                              SELECT 1 FROM agent_run_resource_links link
                              JOIN agent_runs run
                                ON run.user_id=link.user_id AND run.id=link.agent_run_id
                              WHERE link.user_id=:userId
                                AND link.career_artifact_id=:artifactId
                                AND link.resource_kind='CAREER_ARTIFACT'
                                AND run.id<>:runId AND run.deleted_at IS NULL)
                            """)
                    .param("userId", userId)
                    .param("artifactId", artifactId)
                    .param("runId", runId)
                    .query(Boolean.class)
                    .single();
            if (!hasVersion && !hasOtherRun) {
                jdbc.sql("""
                                UPDATE career_artifacts
                                SET deleted_at=COALESCE(deleted_at,:now),updated_at=:now,
                                    version=version+1
                                WHERE user_id=:userId AND id=:artifactId
                                  AND deleted_at IS NULL
                                """)
                        .param("now", utc(now))
                        .param("userId", userId)
                        .param("artifactId", artifactId)
                        .update();
            }
        }
        UUID replacement = jdbc.sql("""
                        SELECT run.id
                        FROM agent_run_resource_links link
                        JOIN agent_runs run
                          ON run.user_id=link.user_id AND run.id=link.agent_run_id
                        WHERE link.user_id=:userId AND link.career_artifact_id=:artifactId
                          AND link.resource_kind='CAREER_ARTIFACT' AND link.primary_resource
                          AND run.id<>:runId AND run.deleted_at IS NULL
                        ORDER BY run.queued_at DESC,run.id DESC LIMIT 1
                        """)
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("runId", runId)
                .query(UUID.class)
                .optional()
                .orElse(null);
        jdbc.sql("""
                        UPDATE career_artifacts
                        SET latest_agent_run_id=:replacement,updated_at=:now,version=version+1
                        WHERE user_id=:userId AND id=:artifactId
                          AND latest_agent_run_id=:runId
                        """)
                .param("replacement", replacement)
                .param("now", utc(now))
                .param("userId", userId)
                .param("artifactId", artifactId)
                .param("runId", runId)
                .update();
    }

    public static String storageKey(
            UUID userId, UUID artifactId, UUID versionId, ArtifactType type) {
        return "users/%s/career-artifacts/%s/versions/%s/content.%s"
                .formatted(userId, artifactId, versionId, type.extension());
    }

    private Artifact artifact(ResultSet rs, int row) throws SQLException {
        return new Artifact(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                ArtifactType.valueOf(rs.getString("artifact_type")),
                rs.getString("title"),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                GenerationStatus.valueOf(rs.getString("generation_status")),
                rs.getObject("current_version_id", UUID.class),
                (Integer) rs.getObject("current_version_no"),
                rs.getObject("latest_agent_run_id", UUID.class),
                rs.getLong("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Version version(ResultSet rs, int row) throws SQLException {
        return new Version(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("career_artifact_id", UUID.class),
                rs.getInt("version_no"),
                rs.getString("content_schema_version"),
                readTree(rs.getString("content_json")),
                rs.getString("template_key"),
                rs.getString("template_version"),
                rs.getString("model_id"),
                rs.getObject("agent_run_id", UUID.class),
                rs.getString("storage_key"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("checksum_sha256"),
                instant(rs, "created_at"));
    }

    private GenerationRequest generationRequest(ResultSet rs, int row) throws SQLException {
        return new GenerationRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("career_artifact_id", UUID.class),
                rs.getObject("agent_run_id", UUID.class),
                rs.getObject("target_version_id", UUID.class),
                readTree(rs.getString("render_profile_snapshot")),
                rs.getString("render_profile_hash"),
                instant(rs, "created_at"),
                instantNullable(rs, "consumed_at"));
    }

    private boolean sameEvidence(
            List<VerifiedEvidence> accepted, List<VerifiedEvidence> current) {
        if (accepted.size() != current.size()) return false;
        Map<UUID, VerifiedEvidence> values = new LinkedHashMap<>();
        current.forEach(value -> values.put(value.experienceItemId(), value));
        return accepted.stream().allMatch(value -> {
            VerifiedEvidence candidate = values.get(value.experienceItemId());
            return candidate != null
                    && candidate.evidenceId().equals(value.evidenceId())
                    && candidate.experienceVersion() == value.experienceVersion()
                    && candidate.evidenceVersion() == value.evidenceVersion();
        });
    }

    private EvidenceUsageType usage(String category) {
        String normalized = category.replaceAll("[\\s_-]", "").toUpperCase(java.util.Locale.ROOT);
        return Set.of("STRENGTH", "강점", "역량").contains(normalized)
                ? EvidenceUsageType.STRENGTH : EvidenceUsageType.PRIMARY_EXPERIENCE;
    }

    private int pages(long total, int size) {
        return total == 0 ? 0 : (int) ((total + size - 1) / size);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Career Artifact JSON could not be read", exception);
        }
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Career Artifact JSON could not be written", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant instantNullable(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ReadinessCounts(
            boolean uploadedResume,
            boolean uploadedPortfolio,
            boolean generatedResume,
            boolean generatedPortfolio,
            int verifiedCount,
            int githubCount,
            int strengthCount,
            int projectCareerCount) {}

    public record EvidenceProjection(
            UUID experienceItemId,
            UUID evidenceId,
            EvidenceUsageType usageType,
            String title) {}

    public record StoredObject(UUID versionId, String storageKey) {}

    public record CleanupTarget(
            UUID artifactId,
            UUID targetVersionId,
            String storageKey,
            UUID currentVersionId,
            boolean artifactDeleted,
            boolean targetVersionExists) {}
}
