package com.hiresemble.interview;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class InterviewMigrationUpgradeTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p8_migration_test")
            .withUsername("hiresemble")
            .withPassword("migration-test-password");

    private static final Map<String, String> BASELINE_DIGESTS = Map.ofEntries(
            Map.entry(
                    "V1__enable_extensions.sql",
                    "9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c"),
            Map.entry(
                    "V2__create_identity_session_idempotency.sql",
                    "c43f2d9a65426e6952d2b47f2908fb2c17c9b6093223f9c8b55ca346f9b21dcf"),
            Map.entry(
                    "V3__create_structured_profiles_and_direct_evidence.sql",
                    "6ac81b6a6a55b51e5811b601dcd3b6b2c06d27911bfb539f37d399e071444347"),
            Map.entry(
                    "V4__create_agent_runtime_and_ai_budget.sql",
                    "706db49cbd3f39e870c3101eae4f08534236e4954ffbbec0d55dfab48626e01f"),
            Map.entry(
                    "V5__create_documents_evidence_and_storage_outbox.sql",
                    "cfae1322bbf7d1412d0be5b7f4535f78ae2702f189b6b095527b6b2186f1dcea"),
            Map.entry(
                    "V6__create_job_postings_and_extend_agent_resources.sql",
                    "08248409866a7b396b3f83285ef0b06d30160e1d2a81777ec4264400d80a7d88"),
            Map.entry(
                    "V7__create_job_analyses_and_provenance.sql",
                    "7d7b0088a0559626bbbf3aaa52c70a701c2e392d62da75381cd1b3fc55eb217c"),
            Map.entry(
                    "V8__create_cover_letters_versions_and_verifications.sql",
                    "4f549aa30f2b24a0d08e70b3105dd0a73c2df91f26ba873bfaacda65c0a466b8"),
            Map.entry(
                    "V9__exclude_education_evidence_and_soft_delete_agent_runs.sql",
                    "258a3ffa881a8fbb6f7ece7721039268abfaa866a9f21a0421cb411dfe50f282"),
            Map.entry(
                    "V10__exclude_document_education_evidence.sql",
                    "97459fa2eda415f8713d7db1644560106bf663f79761ede5a2481be46cadda27"),
            Map.entry(
                    "V11__derive_final_education.sql",
                    "eba74b4e91fc4d9b00ad8b7b32cd892b61b604b632c0c2d64b6d1f34d336d06e"));

    private static final String V12_DIGEST =
            "c7bc2332e5bcdfb112c91debe94f8cb98cebd6108dee5f96744c3ea17537c23f";

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
        flyway("12").clean();
    }

    @Test
    void emptyDatabaseMigratesFromV1ThroughV12() throws Exception {
        Flyway latest = flyway("12");
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryStrings(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly(
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(queryStrings(
                        """
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname='public'
                          AND (
                            tablename LIKE 'research_%'
                            OR tablename LIKE 'interview_%'
                          )
                        ORDER BY tablename
                        """))
                .containsExactly(
                        "interview_answer_feedbacks",
                        "interview_answer_versions",
                        "interview_question_evidence_links",
                        "interview_question_sets",
                        "interview_question_source_links",
                        "interview_questions",
                        "research_runs",
                        "research_sources",
                        "research_topic_source_links",
                        "research_topics");
    }

    @Test
    void populatedV11DatabaseUpgradesWithoutChangingProfileJobOrCoverLetterRows()
            throws Exception {
        assertThat(flyway("11").migrate().success).isTrue();
        UUID owner = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID coverLetter = UUID.randomUUID();
        UUID education = UUID.randomUUID();
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    '%s','p8-upgrade@example.com','hash','P8 Upgrade','USER','ACTIVE',
                    now(),now(),NULL,NULL,now(),now()
                )
                """.formatted(owner));
        execute("""
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,version,created_at,updated_at
                ) VALUES (
                    '%s','%s','https://jobs.example.com/p8','https://jobs.example.com/p8',
                    'Backend Engineer','Backend Engineer','Build services.',
                    'USER_ENTERED','UNKNOWN','IN_PROGRESS','MANUAL_INPUT_PROVIDED',
                    0,now(),now()
                )
                """.formatted(job, owner));
        execute("""
                INSERT INTO cover_letters (
                    id,user_id,job_posting_id,title,status,version,created_at,updated_at
                ) VALUES (
                    '%s','%s','%s','Application','DRAFT',0,now(),now()
                )
                """.formatted(coverLetter, owner, job));
        execute("""
                INSERT INTO educations (
                    id,user_id,school_name,education_level,education_status,is_primary,
                    version,created_at,updated_at
                ) VALUES (
                    '%s','%s','Example University','BACHELOR','GRADUATED',true,
                    0,now(),now()
                )
                """.formatted(education, owner));

        Flyway upgraded = flyway("12");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();

        assertThat(queryOne("SELECT title FROM job_postings WHERE id='" + job + "'"))
                .isEqualTo("Backend Engineer");
        assertThat(queryOne(
                        "SELECT title FROM cover_letters WHERE id='" + coverLetter + "'"))
                .isEqualTo("Application");
        assertThat(queryOne(
                        "SELECT education_level FROM educations WHERE id='" + education + "'"))
                .isEqualTo("BACHELOR");
        assertThat(queryOne(
                        """
                        SELECT count(*)::text
                        FROM information_schema.columns
                        WHERE table_schema='public'
                          AND table_name='agent_run_resource_links'
                          AND column_name IN (
                            'research_run_id','question_set_id','interview_answer_version_id'
                          )
                        """))
                .isEqualTo("3");
    }

    @Test
    void v1ThroughV11BytesAreUnchangedAndV12DigestIsRecorded() throws Exception {
        for (Map.Entry<String, String> entry : BASELINE_DIGESTS.entrySet()) {
            assertDigest(entry.getKey(), entry.getValue());
        }
        assertDigest(
                "V12__create_interview_research_questions_and_feedback.sql",
                V12_DIGEST);
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    private void assertDigest(String fileName, String expected) throws Exception {
        try (InputStream input =
                new ClassPathResource("db/migration/" + fileName).getInputStream()) {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest).as(fileName).isEqualTo(expected);
        }
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

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
