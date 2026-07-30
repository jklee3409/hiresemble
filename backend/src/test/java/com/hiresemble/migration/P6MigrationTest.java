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

class P6MigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p6_migration_test")
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
        flyway("7").clean();
    }

    @Test
    void emptyDatabaseMigratesThroughV7WithOnlyP6AnalysisTables() throws Exception {
        Flyway latest = flyway("7");
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();

        assertThat(queryStrings(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(queryStrings(
                        "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"))
                .contains(
                        "job_analyses",
                        "job_analysis_score_criteria",
                        "job_analysis_evidence_links")
                .doesNotContain(
                        "cover_letters",
                        "research_runs",
                        "interview_question_sets",
                        "mock_interview_sessions");
        assertThat(queryOne("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name='agent_run_resource_links'
                  AND column_name='job_analysis_id'
                """)).isEqualTo("YES");
        assertThat(queryStrings("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema='public'
                  AND table_name IN (
                    'job_analyses',
                    'job_analysis_score_criteria',
                    'job_analysis_evidence_links',
                    'agent_run_resource_links'
                  )
                ORDER BY constraint_name
                """)).contains(
                "job_analyses_job_owner_fk",
                "job_analyses_run_owner_fk",
                "job_analysis_score_criteria_analysis_owner_fk",
                "job_analysis_evidence_links_evidence_owner_fk",
                "agent_run_resource_links_analysis_owner_fk",
                "agent_run_resource_links_analysis_secondary_ck");
    }

    @Test
    void populatedV6DatabaseUpgradesForwardAndKeepsTypedJobResources() throws Exception {
        assertThat(flyway("6").migrate().success).isTrue();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID ownerEvidence = UUID.randomUUID();
        UUID otherEvidence = UUID.randomUUID();
        seedUser(owner, "p6-upgrade-owner@example.com");
        seedUser(other, "p6-upgrade-other@example.com");
        seedJob(jobId, owner);
        seedVerifiedEvidence(ownerEvidence, owner, "Owner evidence");
        seedVerifiedEvidence(otherEvidence, other, "Other evidence");
        seedAnalysisRun(runId, owner, jobId);

        assertThat(queryOne(
                        "SELECT count(*)::text FROM agent_run_resource_links WHERE agent_run_id='"
                                + runId + "'"))
                .isEqualTo("1");

        Flyway upgraded = flyway("7");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryStrings(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(queryOne("""
                SELECT resource_kind || ':' || job_posting_id::text
                FROM agent_run_resource_links
                WHERE agent_run_id='%s' AND primary_resource
                """.formatted(runId))).isEqualTo("JOB:" + jobId);

        UUID analysisId = UUID.randomUUID();
        UUID criterionId = UUID.randomUUID();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                insertAnalysis(statement, analysisId, owner, jobId, runId);
                statement.execute("""
                        INSERT INTO job_analysis_score_criteria (
                            id,user_id,job_analysis_id,category,criterion,weight,
                            match_level,score,explanation,source_location,criterion_order
                        ) VALUES (
                            '%s','%s','%s','REQUIRED_QUALIFICATION',
                            'Java 21',100.00,'MATCHED',100.00,
                            'Verified owner evidence',NULL,0
                        )
                        """.formatted(criterionId, owner, analysisId));
                statement.execute("""
                        INSERT INTO job_analysis_evidence_links (
                            id,user_id,job_analysis_id,score_criterion_id,
                            profile_evidence_id,evidence_version,evidence_hash,
                            usage_type,created_at
                        ) VALUES (
                            '%s','%s','%s','%s','%s',0,'%s',
                            'CRITERION_MATCH',now()
                        )
                        """.formatted(
                                UUID.randomUUID(),
                                owner,
                                analysisId,
                                criterionId,
                                ownerEvidence,
                                "1".repeat(64)));
                statement.execute("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,document_id,
                            job_posting_id,job_analysis_id,primary_resource,created_at
                        ) VALUES (
                            '%s','%s','%s','JOB_ANALYSIS',NULL,NULL,'%s',false,now()
                        )
                        """.formatted(UUID.randomUUID(), owner, runId, analysisId));
                statement.execute("""
                        UPDATE job_analyses SET sealed=true
                        WHERE user_id='%s' AND id='%s'
                        """.formatted(owner, analysisId));
            }
            connection.commit();
        }

        assertThat(queryOne("""
                SELECT analysis_version::text || ':' || fit_score::text
                FROM job_analyses WHERE id='%s'
                """.formatted(analysisId))).isEqualTo("1:100.00");
        assertThat(queryOne("""
                SELECT resource_kind || ':' || primary_resource::text
                FROM agent_run_resource_links WHERE job_analysis_id='%s'
                """.formatted(analysisId))).isEqualTo("JOB_ANALYSIS:false");

        assertThatThrownBy(() -> execute("""
                INSERT INTO job_analysis_evidence_links (
                    id,user_id,job_analysis_id,score_criterion_id,
                    profile_evidence_id,evidence_version,evidence_hash,
                    usage_type,created_at
                ) VALUES (
                    '%s','%s','%s','%s','%s',0,'%s',
                    'CRITERION_MATCH',now()
                )
                """.formatted(
                        UUID.randomUUID(),
                        owner,
                        analysisId,
                        criterionId,
                        otherEvidence,
                        "2".repeat(64))))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(
                        "UPDATE job_analyses SET fit_score=0 WHERE id='" + analysisId + "'"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO job_analyses (
                    id,user_id,job_posting_id,analysis_version,job_version,
                    job_content_hash,profile_snapshot_hash,evidence_snapshot_hash,
                    context_hash,eligibility,fit_score,responsibilities,
                    required_qualifications,preferred_qualifications,strengths,gaps,
                    analysis_summary,rubric_version,workflow_version,quality_mode,
                    embedding_policy_version,embedding_generation,retrieval_policy_version,
                    agent_run_id,sealed,created_at
                ) VALUES (
                    '%s','%s','%s',2,0,'%s','%s','%s','%s',
                    'ELIGIBLE',101.00,'[]','[]','[]','[]','[]',NULL,
                    'job-fit-rubric-v1','job-analysis-v1','ECONOMY',1,1,
                    'verified-evidence-rag-v1','%s',false,now()
                )
                """.formatted(
                        UUID.randomUUID(),
                        owner,
                        jobId,
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        "d".repeat(64),
                        runId)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void v1ThroughV6BytesRemainUnchanged() throws Exception {
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
        assertDigest(
                "db/migration/V6__create_job_postings_and_extend_agent_resources.sql",
                "08248409866a7b396b3f83285ef0b06d30160e1d2a81777ec4264400d80a7d88");
    }

    private void insertAnalysis(
            Statement statement,
            UUID analysisId,
            UUID owner,
            UUID jobId,
            UUID runId)
            throws SQLException {
        statement.execute("""
                INSERT INTO job_analyses (
                    id,user_id,job_posting_id,analysis_version,job_version,
                    job_content_hash,profile_snapshot_hash,evidence_snapshot_hash,
                    context_hash,eligibility,fit_score,responsibilities,
                    required_qualifications,preferred_qualifications,strengths,gaps,
                    analysis_summary,rubric_version,workflow_version,quality_mode,
                    embedding_policy_version,embedding_generation,retrieval_policy_version,
                    agent_run_id,sealed,created_at
                ) VALUES (
                    '%s','%s','%s',1,0,'%s','%s','%s','%s',
                    'INELIGIBLE',100.00,'[]','[]','[]','[]','[]',
                    'Eligibility remains separate from fit score',
                    'job-fit-rubric-v1','job-analysis-v1','ECONOMY',1,1,
                    'verified-evidence-rag-v1','%s',false,now()
                )
                """.formatted(
                        analysisId,
                        owner,
                        jobId,
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        "d".repeat(64),
                        runId));
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

    private void seedJob(UUID jobId, UUID owner) throws Exception {
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
                    '%s','%s',NULL,'https://example.com/p6','https://example.com/p6',
                    'Backend Engineer','Backend Engineer',NULL,NULL,NULL,
                    'Build Java services.','USER_ENTERED',NULL,'UNKNOWN',NULL,
                    'IN_PROGRESS','MANUAL_INPUT_PROVIDED',NULL,NULL,NULL,
                    '%s',NULL,false,false,false,false,0,now(),now(),NULL
                )
                """.formatted(jobId, owner, "a".repeat(64)));
    }

    private void seedVerifiedEvidence(UUID evidenceId, UUID owner, String title)
            throws Exception {
        execute("""
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,
                    evidence_category,title,content,metadata,confidence,
                    verification_status,verified_at,source_deleted_at,
                    version,created_at,updated_at
                ) VALUES (
                    '%s','%s','MANUAL',NULL,NULL,'EXPERIENCE','%s',
                    'Built Java services.','{}',NULL,'VERIFIED',now(),NULL,
                    0,now(),now()
                )
                """.formatted(evidenceId, owner, title));
    }

    private void seedAnalysisRun(UUID runId, UUID owner, UUID jobId)
            throws Exception {
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
                            '%s','%s','JOB_ANALYSIS','QUEUED',NULL,0,
                            'job-analysis-v1','%s','{}',1,NULL,'ECONOMY',NULL,
                            0,0,0,'JOB','%s',NULL,'%s',1,false,0,now(),now()
                        )
                        """.formatted(runId, owner, "0".repeat(64), jobId, runId));
                statement.execute("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,document_id,
                            job_posting_id,primary_resource,created_at
                        ) VALUES (
                            '%s','%s','%s','JOB',NULL,'%s',true,now()
                        )
                        """.formatted(UUID.randomUUID(), owner, runId, jobId));
            }
            connection.commit();
        }
    }

    private void assertDigest(String resource, String expected) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest).isEqualTo(expected);
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .cleanDisabled(false)
                .target(target)
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
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }
}
