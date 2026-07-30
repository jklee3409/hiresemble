package com.hiresemble.coverletter;

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
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class CoverLetterMigrationUpgradeTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p7_migration_test")
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
        flyway("8").clean();
    }

    @Test
    void populatedV7DatabaseUpgradesForwardWithoutChangingExistingRows() throws Exception {
        assertThat(flyway("7").migrate().success).isTrue();
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    '%s','p7-upgrade@example.com','hash','P7 Upgrade','USER','ACTIVE',
                    now(),now(),NULL,NULL,now(),now()
                )
                """.formatted(owner));
        execute("""
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,version,created_at,updated_at
                ) VALUES (
                    '%s','%s','https://example.com/p7','https://example.com/p7',
                    'Backend Engineer','Backend Engineer','Build Java services.',
                    'USER_ENTERED','UNKNOWN','IN_PROGRESS','MANUAL_INPUT_PROVIDED',
                    0,now(),now()
                )
                """.formatted(jobId, owner));

        Flyway upgraded = flyway("8");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();

        assertThat(queryStrings(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(queryOne(
                        "SELECT title FROM job_postings WHERE id='" + jobId + "'"))
                .isEqualTo("Backend Engineer");
        assertThat(queryStrings(
                        """
                        SELECT tablename FROM pg_tables
                        WHERE schemaname='public'
                          AND tablename LIKE 'cover_letter%'
                        ORDER BY tablename
                        """))
                .containsExactly(
                        "cover_letter_answer_versions",
                        "cover_letter_evidence_links",
                        "cover_letter_questions",
                        "cover_letter_verification_acknowledgements",
                        "cover_letter_verifications",
                        "cover_letters");
    }

    @Test
    void v1ThroughV7MigrationBytesRemainUnchanged() throws Exception {
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
        assertDigest(
                "db/migration/V7__create_job_analyses_and_provenance.sql",
                "7d7b0088a0559626bbbf3aaa52c70a701c2e392d62da75381cd1b3fc55eb217c");
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

    private void assertDigest(String resource, String expected) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest).isEqualTo(expected);
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
