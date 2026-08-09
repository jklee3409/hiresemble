package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class Phase5UpgradeMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("phase5_upgrade_test")
            .withUsername("hiresemble")
            .withPassword("migration-test-password");

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanSchema() {
        flyway(null).clean();
    }

    @Test
    void populatedV28UpgradesToV30WithoutRewritingPublicGitHubCanonicalArtifactOrSessionData()
            throws Exception {
        assertThat(flyway("28").migrate().success).isTrue();
        execute("""
                INSERT INTO users (
                  id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                  created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000001','phase5-upgrade@example.com',
                  'fixture-password-hash','Phase 5 Upgrade','USER','ACTIVE',now(),now(),now(),now()
                );
                INSERT INTO user_profiles (
                  id,user_id,legal_name,introduction,desired_roles,desired_industries,
                  desired_locations,expected_graduation_date,version,created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000002',
                  '95000000-0000-4000-8000-000000000001',NULL,NULL,'[]','[]','[]',NULL,0,now(),now()
                );
                INSERT INTO experience_items (
                  id,user_id,canonical_evidence_id,evidence_category,title,content,
                  verification_status,match_kind,match_policy_version,canonical_fingerprint,
                  version,created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000011',
                  '95000000-0000-4000-8000-000000000001',
                  '95000000-0000-4000-8000-000000000012','PROJECT','Preserved canonical',
                  'Preserved canonical content','VERIFIED','NEW','experience-semantic-v1',
                  repeat('a',64),0,now(),now()
                );
                INSERT INTO profile_evidence (
                  id,user_id,source_type,source_entity_id,document_id,evidence_category,title,
                  content,metadata,confidence,verification_status,verified_at,source_deleted_at,
                  version,created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000012',
                  '95000000-0000-4000-8000-000000000001','EXPERIENCE',
                  '95000000-0000-4000-8000-000000000011',NULL,'PROJECT','Preserved canonical',
                  'Preserved canonical content','{}',1.0,'VERIFIED',now(),NULL,0,now(),now()
                );
                INSERT INTO github_sources (
                  id,user_id,source_kind,account_type,original_url,canonical_url,owner_login,
                  repository_name,source_status,created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000021',
                  '95000000-0000-4000-8000-000000000001','REPOSITORY',NULL,
                  'https://github.com/acme/preserved','https://github.com/acme/preserved',
                  'acme','preserved','READY',now(),now()
                );
                INSERT INTO github_repositories (
                  id,user_id,external_repository_id,node_id,owner_login,repository_name,
                  canonical_url,default_branch,is_private,is_fork,is_archived,topics,
                  created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000022',
                  '95000000-0000-4000-8000-000000000001',9001,'node-9001','acme','preserved',
                  'https://github.com/acme/preserved','main',false,false,false,'[]',now(),now()
                );
                INSERT INTO github_source_repository_links (
                  id,user_id,github_source_id,github_repository_id,available,selected,
                  selection_order,discovered_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000023',
                  '95000000-0000-4000-8000-000000000001',
                  '95000000-0000-4000-8000-000000000021',
                  '95000000-0000-4000-8000-000000000022',true,true,1,now(),now()
                );
                INSERT INTO agent_runs (
                  id,user_id,workflow_type,status,workflow_version,canonical_input_hash,
                  input_reference_snapshot,budget_policy_version,resource_type,resource_id,
                  root_run_id,queued_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000031',
                  '95000000-0000-4000-8000-000000000001','RESUME_GENERATION','QUEUED',
                  'resume-generation-v1',repeat('b',64),'{}',1,'CAREER_ARTIFACT',
                  '95000000-0000-4000-8000-000000000032',
                  '95000000-0000-4000-8000-000000000031',now(),now()
                );
                INSERT INTO career_artifacts (
                  id,user_id,artifact_type,title,lifecycle_status,current_version_id,
                  latest_agent_run_id,version,created_at,updated_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000032',
                  '95000000-0000-4000-8000-000000000001','RESUME','Preserved resume','ACTIVE',
                  NULL,'95000000-0000-4000-8000-000000000031',0,now(),now()
                );
                INSERT INTO agent_run_resource_links (
                  id,user_id,agent_run_id,resource_kind,career_artifact_id,
                  primary_resource,created_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000034',
                  '95000000-0000-4000-8000-000000000001',
                  '95000000-0000-4000-8000-000000000031','CAREER_ARTIFACT',
                  '95000000-0000-4000-8000-000000000032',true,now()
                );
                INSERT INTO career_artifact_generation_requests (
                  id,user_id,career_artifact_id,agent_run_id,target_version_id,
                  render_profile_snapshot,render_profile_hash,created_at,consumed_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000035',
                  '95000000-0000-4000-8000-000000000001',
                  '95000000-0000-4000-8000-000000000032',
                  '95000000-0000-4000-8000-000000000031',
                  '95000000-0000-4000-8000-000000000033','{}',repeat('d',64),now(),NULL
                );
                INSERT INTO career_artifact_versions (
                  id,user_id,career_artifact_id,version_no,content_schema_version,content_json,
                  template_key,template_version,model_id,agent_run_id,render_profile_snapshot,
                  storage_key,mime_type,size_bytes,checksum_sha256,created_at)
                VALUES (
                  '95000000-0000-4000-8000-000000000033',
                  '95000000-0000-4000-8000-000000000001',
                  '95000000-0000-4000-8000-000000000032',1,'resume-content-v1','{}',
                  'resume-ats-v1','1','gpt-5.6-terra',
                  '95000000-0000-4000-8000-000000000031','{}',
                  'users/95000000-0000-4000-8000-000000000001/career-artifacts/95000000-0000-4000-8000-000000000032/versions/95000000-0000-4000-8000-000000000033/content.docx',
                  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                  1024,repeat('c',64),now()
                );
                UPDATE career_artifacts
                SET current_version_id='95000000-0000-4000-8000-000000000033',version=1
                WHERE id='95000000-0000-4000-8000-000000000032';
                UPDATE career_artifact_generation_requests SET consumed_at=now()
                WHERE id='95000000-0000-4000-8000-000000000035';
                INSERT INTO spring_session (
                  primary_id,session_id,creation_time,last_access_time,max_inactive_interval,
                  expiry_time,principal_name)
                VALUES (
                  '95000000-0000-4000-8000-000000000041',
                  '95000000-0000-4000-8000-000000000042',
                  1,1,1800,1800001,'95000000-0000-4000-8000-000000000001'
                );
                """);

        Flyway upgraded = flyway(null);
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("SELECT max(version::int) FROM flyway_schema_history WHERE success"))
                .isEqualTo(30L);
        assertThat(queryLong("SELECT count(*) FROM users")).isEqualTo(1L);
        assertThat(queryLong("SELECT count(*) FROM spring_session")).isEqualTo(1L);
        assertThat(queryText("""
                SELECT title FROM experience_items
                WHERE id='95000000-0000-4000-8000-000000000011'
                """)).isEqualTo("Preserved canonical");
        assertThat(queryText("""
                SELECT access_mode FROM github_sources
                WHERE id='95000000-0000-4000-8000-000000000021'
                """)).isEqualTo("PUBLIC");
        assertThat(queryText("""
                SELECT visibility FROM github_repositories
                WHERE id='95000000-0000-4000-8000-000000000022'
                """)).isEqualTo("PUBLIC");
        assertThat(queryText("""
                SELECT storage_key FROM career_artifact_versions
                WHERE id='95000000-0000-4000-8000-000000000033'
                """)).isEqualTo(
                        "users/95000000-0000-4000-8000-000000000001/career-artifacts/"
                                + "95000000-0000-4000-8000-000000000032/versions/"
                                + "95000000-0000-4000-8000-000000000033/content.docx");
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private String queryText(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
