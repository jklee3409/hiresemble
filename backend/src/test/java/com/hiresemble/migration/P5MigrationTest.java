package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class P5MigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p5_migration_test")
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
    void emptyDatabaseMigratesThroughV6WithoutP6Tables() throws Exception {
        Flyway latest = flyway(null);
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();

        assertThat(queryStrings(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(queryStrings(
                        "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"))
                .contains(
                        "companies",
                        "job_postings",
                        "job_status_history",
                        "agent_run_resource_links")
                .doesNotContain(
                        "job_analyses",
                        "job_analysis_score_criteria",
                        "cover_letters",
                        "research_runs",
                        "interview_question_sets",
                        "mock_interview_sessions");
        assertThat(queryOne("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name='agent_run_resource_links'
                  AND column_name='document_id'
                """)).isEqualTo("YES");
        assertThat(queryOne("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name='agent_run_resource_links'
                  AND column_name='job_posting_id'
                """)).isEqualTo("YES");
    }

    @Test
    void aDatabaseAlreadyAtV5UpgradesForwardToV6() throws Exception {
        assertThat(flyway("5").migrate().success).isTrue();
        assertThat(queryOne("""
                SELECT count(*)::text FROM information_schema.tables
                WHERE table_schema='public' AND table_name='job_postings'
                """)).isEqualTo("0");

        assertThat(flyway(null).migrate().success).isTrue();
        assertThat(queryStrings(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(queryOne("""
                SELECT count(*)::text FROM information_schema.tables
                WHERE table_schema='public' AND table_name='job_postings'
                """)).isEqualTo("1");
    }

    @Test
    void v1ThroughV5BytesRemainUnchanged() throws Exception {
        assertDigest(
                "db/migration/V1__enable_extensions.sql",
                "9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c");
        assertDigest(
                "db/migration/V2__create_identity_session_idempotency.sql",
                "c43f2d9a65426e6952d2b47f2908fb2c17c9b6093223f9c8b55ca346f9b21dcf");
        assertDigest(
                "db/migration/V3__create_structured_profiles_and_direct_evidence.sql",
                "6ac81b6a6a55b51e5811b601dcd3b6b2c06d27911bfb539f37d399e071444347");
        assertDigest(
                "db/migration/V4__create_agent_runtime_and_ai_budget.sql",
                "706db49cbd3f39e870c3101eae4f08534236e4954ffbbec0d55dfab48626e01f");
        assertDigest(
                "db/migration/V5__create_documents_evidence_and_storage_outbox.sql",
                "cfae1322bbf7d1412d0be5b7f4535f78ae2702f189b6b095527b6b2186f1dcea");
    }

    @Test
    void ownerChecksCanonicalPartialUniqueTypedLinkAndSchedulerIndexesAreInstalled()
            throws Exception {
        assertThat(flyway(null).migrate().success).isTrue();
        assertThat(queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND constraint_type='FOREIGN KEY'
                ORDER BY constraint_name
                """)).contains(
                "job_postings_user_id_fk",
                "job_postings_latest_run_owner_fk",
                "job_status_history_job_owner_fk",
                "agent_run_resource_links_job_owner_fk");
        assertThat(queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND constraint_type='CHECK'
                ORDER BY constraint_name
                """)).contains(
                "job_postings_status_ck",
                "job_postings_extraction_status_ck",
                "job_postings_closed_shape_ck",
                "agent_run_resource_links_exactly_one_ck",
                "agent_run_resource_links_kind_ck");
        assertThat(queryStrings(
                        "SELECT indexname FROM pg_indexes WHERE schemaname='public' ORDER BY indexname"))
                .contains(
                        "job_postings_active_canonical_url_uk",
                        "job_postings_owner_created_ix",
                        "job_postings_owner_status_ix",
                        "job_postings_owner_extraction_ix",
                        "job_postings_owner_deadline_ix",
                        "job_postings_scheduler_deadline_ix",
                        "job_status_history_job_changed_ix",
                        "agent_run_resource_links_job_ix");
    }

    @Test
    void canonicalUniquenessIsPerOwnerActiveAndHistoryCannotCrossOwners()
            throws Exception {
        assertThat(flyway(null).migrate().success).isTrue();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        UUID firstJob = UUID.randomUUID();
        seedUser(firstUser, "migration-owner-a@example.com");
        seedUser(secondUser, "migration-owner-b@example.com");
        insertJob(firstJob, firstUser, "https://example.com/jobs/1");

        assertThatThrownBy(() ->
                        insertJob(UUID.randomUUID(), firstUser, "https://example.com/jobs/1"))
                .isInstanceOf(SQLException.class);
        insertJob(UUID.randomUUID(), secondUser, "https://example.com/jobs/1");

        execute("UPDATE job_postings SET deleted_at=now(),updated_at=now() WHERE id='"
                + firstJob + "'");
        insertJob(UUID.randomUUID(), firstUser, "https://example.com/jobs/1");

        assertThatThrownBy(() -> execute("""
                INSERT INTO job_status_history (
                    id,user_id,job_posting_id,from_status,to_status,reason,changed_by,changed_at
                ) VALUES (
                    '%s','%s','%s','IN_PROGRESS','CLOSED',
                    'CROSS_OWNER','SYSTEM',now()
                )
                """.formatted(UUID.randomUUID(), secondUser, firstJob)))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                UPDATE job_postings SET status='UNKNOWN',updated_at=now()
                WHERE user_id='%s' AND deleted_at IS NULL
                """.formatted(firstUser)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void jobRunAndTypedLinkMustShareTheSameOwnerAndResource() throws Exception {
        assertThat(flyway(null).migrate().success).isTrue();
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        seedUser(userId, "migration-link@example.com");
        insertJob(jobId, userId, "https://example.com/jobs/link");

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO agent_runs (
                            id,user_id,workflow_type,status,current_step,progress_percent,
                            workflow_version,canonical_input_hash,input_reference_snapshot,
                            budget_policy_version,price_version,requested_quality_mode,
                            highest_model_tier_used,estimated_cost_usd,reserved_cost_usd,
                            actual_cost_usd,resource_type,resource_id,retry_of_run_id,
                            root_run_id,run_attempt_no,retryable_failure,state_version,
                            queued_at,updated_at
                        ) VALUES (
                            '%s','%s','JOB_POSTING_EXTRACTION','QUEUED',NULL,0,
                            'job-posting-extraction-v1','%s','{}',
                            1,NULL,'ECONOMY',NULL,0,0,0,'JOB','%s',NULL,
                            '%s',1,false,0,now(),now()
                        )
                        """.formatted(runId, userId, "0".repeat(64), jobId, runId));
                statement.execute("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,document_id,
                            job_posting_id,primary_resource,created_at
                        ) VALUES (
                            '%s','%s','%s','JOB',NULL,'%s',true,now()
                        )
                        """.formatted(UUID.randomUUID(), userId, runId, jobId));
                statement.execute("""
                        UPDATE job_postings SET latest_agent_run_id='%s',updated_at=now()
                        WHERE user_id='%s' AND id='%s'
                        """.formatted(runId, userId, jobId));
            }
            connection.commit();
        }

        assertThat(queryOne("""
                SELECT resource_kind || ':' || job_posting_id::text
                FROM agent_run_resource_links WHERE agent_run_id='%s'
                """.formatted(runId))).isEqualTo("JOB:" + jobId);
        assertThat(queryOne("""
                SELECT latest_agent_run_id::text FROM job_postings WHERE id='%s'
                """.formatted(jobId))).isEqualTo(runId.toString());
    }

    private void assertDigest(String resource, String expected) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest).isEqualTo(expected);
        }
    }

    private void seedUser(UUID userId, String email) throws Exception {
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    '%s','%s','hash','Migration User','USER','ACTIVE',
                    now(),now(),NULL,NULL,now(),now()
                )
                """.formatted(userId, email));
    }

    private void insertJob(UUID jobId, UUID userId, String canonicalUrl)
            throws Exception {
        execute("""
                INSERT INTO job_postings (
                    id,user_id,company_id,source_url,canonical_url,title,position_name,
                    role_category,employment_type,location,description_text,
                    description_source,deadline_at,deadline_source,deadline_confidence,
                    status,extraction_status,submitted_at,closed_at,closed_reason,
                    content_hash,latest_agent_run_id,company_user_override,
                    title_user_override,position_user_override,deadline_user_override,
                    version,created_at,updated_at,deleted_at
                ) VALUES (
                    '%s','%s',NULL,'%s','%s',NULL,NULL,NULL,NULL,NULL,
                    NULL,NULL,NULL,'UNKNOWN',NULL,'IN_PROGRESS','QUEUED',
                    NULL,NULL,NULL,NULL,NULL,false,false,false,false,0,now(),now(),NULL
                )
                """.formatted(jobId, userId, canonicalUrl, canonicalUrl));
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .cleanDisabled(false)
                .target(target == null ? "6" : target)
                .load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryOne(String sql) throws Exception {
        List<String> values = queryStrings(sql);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private List<String> queryStrings(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) values.add(resultSet.getString(1));
        }
        return values;
    }
}
