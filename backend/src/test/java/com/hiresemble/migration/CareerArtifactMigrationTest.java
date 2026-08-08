package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CareerArtifactMigrationTest extends PostgresIntegrationTest {

    private static final UUID USER_A = UUID.fromString("93000000-0000-4000-8000-000000000001");
    private static final UUID USER_B = UUID.fromString("93000000-0000-4000-8000-000000000002");
    private static final UUID ARTIFACT_A = UUID.fromString("93000000-0000-4000-8000-000000000011");
    private static final UUID RUN_A = UUID.fromString("93000000-0000-4000-8000-000000000021");
    private static final UUID TARGET_A = UUID.fromString("93000000-0000-4000-8000-000000000031");
    private static final UUID EXPERIENCE_A = UUID.fromString("93000000-0000-4000-8000-000000000041");
    private static final UUID EVIDENCE_A = UUID.fromString("93000000-0000-4000-8000-000000000042");

    @Autowired private DataSource dataSource;

    @Test
    void freshSchemaContainsV28CareerArtifactTablesAndWorkflowTypes() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT max(version::int) FROM flyway_schema_history WHERE success",
                        Integer.class))
                .isEqualTo(28);
        assertThat(jdbcTemplate.queryForList("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema='public' AND table_name LIKE 'career_artifact%'
                        ORDER BY table_name
                        """, String.class))
                .containsExactly(
                        "career_artifact_evidence_links",
                        "career_artifact_generation_requests",
                        "career_artifact_object_deletion_outbox",
                        "career_artifact_versions",
                        "career_artifacts");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE connamespace='public'::regnamespace
                          AND conname='agent_runs_workflow_type_ck'
                        """, String.class))
                .contains("RESUME_GENERATION", "PORTFOLIO_GENERATION");
    }

    @Test
    void validResumeGraphEnforcesCanonicalProvenanceAndImmutableVersion() throws Exception {
        insertUser(USER_A, "artifact-a@example.com");
        insertCanonicalExperience(USER_A, EXPERIENCE_A, EVIDENCE_A);
        insertPendingGeneration(
                USER_A, ARTIFACT_A, "RESUME", RUN_A, "RESUME_GENERATION", TARGET_A);
        insertSuccessfulResumeVersion(
                USER_A, ARTIFACT_A, RUN_A, TARGET_A, EXPERIENCE_A, EVIDENCE_A);

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT current_version_id FROM career_artifacts
                        WHERE user_id=? AND id=?
                        """, UUID.class, USER_A, ARTIFACT_A))
                .isEqualTo(TARGET_A);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM career_artifact_generation_requests
                        WHERE user_id=? AND agent_run_id=? AND consumed_at IS NOT NULL
                        """, Integer.class, USER_A, RUN_A))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE career_artifact_versions SET model_id='gpt-5.5'
                        WHERE user_id=? AND id=?
                        """, USER_A, TARGET_A))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE career_artifact_versions SET render_profile_snapshot='{}'::jsonb
                        WHERE user_id=? AND id=?
                        """, USER_A, TARGET_A))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                UPDATE career_artifacts SET deleted_at=now()
                WHERE user_id=? AND id=?
                """, USER_A, ARTIFACT_A);
        assertThat(jdbcTemplate.update("""
                        UPDATE career_artifact_versions SET render_profile_snapshot='{}'::jsonb
                        WHERE user_id=? AND id=?
                        """, USER_A, TARGET_A))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE career_artifact_versions SET model_id='gpt-5.5'
                        WHERE user_id=? AND id=?
                        """, USER_A, TARGET_A))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ownerWorkflowTypeCurrentVersionAndRequestParityRejectInvalidGraphs() throws Exception {
        insertUser(USER_A, "artifact-owner-a@example.com");
        insertUser(USER_B, "artifact-owner-b@example.com");

        assertThatThrownBy(() -> insertPendingGeneration(
                        USER_A,
                        ARTIFACT_A,
                        "RESUME",
                        RUN_A,
                        "PORTFOLIO_GENERATION",
                        TARGET_A))
                .isInstanceOf(Exception.class);
        insertPendingGeneration(
                USER_A, ARTIFACT_A, "RESUME", RUN_A, "RESUME_GENERATION", TARGET_A);

        UUID artifactB = UUID.fromString("93000000-0000-4000-8000-000000000012");
        UUID runB = UUID.fromString("93000000-0000-4000-8000-000000000022");
        UUID targetB = UUID.fromString("93000000-0000-4000-8000-000000000032");
        insertPendingGeneration(
                USER_B, artifactB, "PORTFOLIO", runB, "PORTFOLIO_GENERATION", targetB);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE career_artifacts SET current_version_id=?
                        WHERE user_id=? AND id=?
                        """, targetB, USER_A, ARTIFACT_A))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID orphanRun = UUID.fromString("93000000-0000-4000-8000-000000000023");
        assertThatThrownBy(() -> executeTransaction("""
                        INSERT INTO career_artifacts (
                            id,user_id,artifact_type,title,lifecycle_status,version,created_at,updated_at
                        ) VALUES (
                            '93000000-0000-4000-8000-000000000013',
                            '93000000-0000-4000-8000-000000000001',
                            'RESUME','Missing request','ACTIVE',0,now(),now()
                        );
                        INSERT INTO agent_runs (
                            id,user_id,workflow_type,status,workflow_version,canonical_input_hash,
                            input_reference_snapshot,budget_policy_version,resource_type,resource_id,
                            root_run_id,queued_at,updated_at
                        ) VALUES (
                            '%s','%s','RESUME_GENERATION','QUEUED','resume-generation-v1',
                            repeat('a',64),'{}',1,'CAREER_ARTIFACT',
                            '93000000-0000-4000-8000-000000000013','%s',now(),now()
                        );
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,career_artifact_id,
                            primary_resource,created_at
                        ) VALUES (
                            gen_random_uuid(),'%s','%s','CAREER_ARTIFACT',
                            '93000000-0000-4000-8000-000000000013',true,now()
                        );
                        """.formatted(orphanRun, USER_A, orphanRun, USER_A, orphanRun)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void versionStorageKeyMustMatchItsOwnerArtifactAndPreallocatedVersion() throws Exception {
        insertUser(USER_A, "artifact-storage-key@example.com");
        insertCanonicalExperience(USER_A, EXPERIENCE_A, EVIDENCE_A);
        insertPendingGeneration(
                USER_A, ARTIFACT_A, "RESUME", RUN_A, "RESUME_GENERATION", TARGET_A);
        String wrongKey = "users/%s/career-artifacts/%s/versions/%s/content.docx"
                .formatted(USER_A, UUID.randomUUID(), TARGET_A);

        assertThatThrownBy(() -> insertSuccessfulResumeVersion(
                        USER_A,
                        ARTIFACT_A,
                        RUN_A,
                        TARGET_A,
                        EXPERIENCE_A,
                        EVIDENCE_A,
                        wrongKey))
                .isInstanceOf(Exception.class);
    }

    @Test
    void immutableHistoricalProvenanceDoesNotBlockANewVerifiedSnapshot() throws Exception {
        insertUser(USER_A, "artifact-history@example.com");
        insertCanonicalExperience(USER_A, EXPERIENCE_A, EVIDENCE_A);
        insertPendingGeneration(
                USER_A, ARTIFACT_A, "RESUME", RUN_A, "RESUME_GENERATION", TARGET_A);
        insertSuccessfulResumeVersion(
                USER_A, ARTIFACT_A, RUN_A, TARGET_A, EXPERIENCE_A, EVIDENCE_A);

        jdbcTemplate.update(
                "UPDATE experience_items SET version=version+1 WHERE user_id=? AND id=?",
                USER_A,
                EXPERIENCE_A);
        jdbcTemplate.update(
                "UPDATE profile_evidence SET version=version+1 WHERE user_id=? AND id=?",
                USER_A,
                EVIDENCE_A);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE career_artifact_evidence_links SET title_snapshot='Changed'
                        WHERE user_id=? AND artifact_version_id=?
                        """, USER_A, TARGET_A))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID experienceB = UUID.fromString("93000000-0000-4000-8000-000000000043");
        UUID evidenceB = UUID.fromString("93000000-0000-4000-8000-000000000044");
        UUID artifactB = UUID.fromString("93000000-0000-4000-8000-000000000014");
        UUID runB = UUID.fromString("93000000-0000-4000-8000-000000000024");
        UUID targetB = UUID.fromString("93000000-0000-4000-8000-000000000034");
        insertCanonicalExperience(USER_A, experienceB, evidenceB);
        insertPendingGeneration(
                USER_A, artifactB, "RESUME", runB, "RESUME_GENERATION", targetB);
        insertSuccessfulResumeVersion(
                USER_A, artifactB, runB, targetB, experienceB, evidenceB);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM career_artifact_evidence_links WHERE user_id=?",
                        Integer.class,
                        USER_A))
                .isEqualTo(2);
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,created_at,updated_at
                ) VALUES (?,?,'fixture-password-hash','Fixture','USER','ACTIVE',
                    now(),now(),now(),now())
                """, userId, email);
    }

    private void insertCanonicalExperience(UUID userId, UUID experienceId, UUID evidenceId)
            throws Exception {
        String fingerprint = experienceId.toString().replace("-", "").repeat(2);
        executeTransaction("""
                INSERT INTO experience_items (
                    id,user_id,canonical_evidence_id,evidence_category,title,content,
                    verification_status,match_kind,match_policy_version,canonical_fingerprint,
                    version,created_at,updated_at
                ) VALUES (
                    '%s','%s','%s','PROJECT','Grounded project','Built a grounded project',
                    'VERIFIED','NEW','experience-semantic-v1','%s',0,now(),now()
                );
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    '%s','%s','EXPERIENCE','%s',NULL,'PROJECT','Grounded project',
                    'Built a grounded project','{}',1.0,'VERIFIED',now(),NULL,0,now(),now()
                );
                UPDATE experience_items SET canonical_evidence_id='%s'
                WHERE user_id='%s' AND id='%s';
                """.formatted(
                experienceId, userId, evidenceId, fingerprint, evidenceId, userId, experienceId,
                evidenceId, userId, experienceId));
    }

    private void insertPendingGeneration(
            UUID userId,
            UUID artifactId,
            String artifactType,
            UUID runId,
            String workflowType,
            UUID targetVersionId) throws Exception {
        String title = artifactType.equals("RESUME") ? "Backend resume" : "Interview portfolio";
        executeTransaction("""
                INSERT INTO career_artifacts (
                    id,user_id,artifact_type,title,lifecycle_status,current_version_id,
                    latest_agent_run_id,version,created_at,updated_at
                ) VALUES ('%s','%s','%s','%s','ACTIVE',NULL,NULL,0,now(),now());
                INSERT INTO agent_runs (
                    id,user_id,workflow_type,status,workflow_version,canonical_input_hash,
                    input_reference_snapshot,budget_policy_version,resource_type,resource_id,
                    root_run_id,queued_at,updated_at
                ) VALUES (
                    '%s','%s','%s','QUEUED','%s',repeat('b',64),
                    '{"model":"gpt-5.6-terra"}',1,'CAREER_ARTIFACT','%s','%s',now(),now()
                );
                INSERT INTO agent_run_resource_links (
                    id,user_id,agent_run_id,resource_kind,career_artifact_id,
                    primary_resource,created_at
                ) VALUES (gen_random_uuid(),'%s','%s','CAREER_ARTIFACT','%s',true,now());
                INSERT INTO career_artifact_generation_requests (
                    id,user_id,career_artifact_id,agent_run_id,target_version_id,
                    render_profile_snapshot,render_profile_hash,created_at
                ) VALUES (
                    gen_random_uuid(),'%s','%s','%s','%s',
                    '{"displayName":"Fixture","includeContact":false}',repeat('c',64),now()
                );
                UPDATE career_artifacts SET latest_agent_run_id='%s',version=1,updated_at=now()
                WHERE user_id='%s' AND id='%s';
                """.formatted(
                artifactId, userId, artifactType, title,
                runId, userId, workflowType,
                workflowType.equals("RESUME_GENERATION")
                        ? "resume-generation-v1" : "portfolio-generation-v1",
                artifactId, runId,
                userId, runId, artifactId,
                userId, artifactId, runId, targetVersionId,
                runId, userId, artifactId));
    }

    private void insertSuccessfulResumeVersion(
            UUID userId,
            UUID artifactId,
            UUID runId,
            UUID versionId,
            UUID experienceId,
            UUID evidenceId) throws Exception {
        String key = "users/%s/career-artifacts/%s/versions/%s/content.docx"
                .formatted(userId, artifactId, versionId);
        insertSuccessfulResumeVersion(
                userId, artifactId, runId, versionId, experienceId, evidenceId, key);
    }

    private void insertSuccessfulResumeVersion(
            UUID userId,
            UUID artifactId,
            UUID runId,
            UUID versionId,
            UUID experienceId,
            UUID evidenceId,
            String key) throws Exception {
        executeTransaction("""
                INSERT INTO career_artifact_versions (
                    id,user_id,career_artifact_id,version_no,content_schema_version,content_json,
                    template_key,template_version,model_id,agent_run_id,render_profile_snapshot,
                    storage_key,mime_type,size_bytes,checksum_sha256,created_at
                ) VALUES (
                    '%s','%s','%s',1,'resume-content-v1',
                    '{"headline":"Backend engineer","sections":[{"type":"EXPERIENCE"}]}',
                    'resume-ats-v1','1','gpt-5.6-terra','%s',
                    '{"displayName":"Fixture","includeContact":false}','%s',
                    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                    1024,repeat('d',64),now()
                );
                INSERT INTO career_artifact_evidence_links (
                    id,user_id,artifact_version_id,experience_item_id,profile_evidence_id,
                    experience_version,evidence_version,usage_type,title_snapshot,
                    content_snapshot,snapshot_hash,created_at
                ) VALUES (
                    gen_random_uuid(),'%s','%s','%s','%s',0,0,'PRIMARY_EXPERIENCE',
                    'Grounded project','Built a grounded project',repeat('e',64),now()
                );
                UPDATE career_artifact_generation_requests SET consumed_at=now()
                WHERE user_id='%s' AND agent_run_id='%s';
                UPDATE career_artifacts SET current_version_id='%s',version=version+1,updated_at=now()
                WHERE user_id='%s' AND id='%s';
                """.formatted(
                versionId, userId, artifactId, runId, key,
                userId, versionId, experienceId, evidenceId,
                userId, runId,
                versionId, userId, artifactId));
    }

    private void executeTransaction(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute(sql);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
