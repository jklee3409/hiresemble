package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class P8_5UpgradeMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p8_5_upgrade_migration_test")
            .withUsername("hiresemble")
            .withPassword("migration-test-password");

    private static final Map<String, String> IMMUTABLE_MIGRATION_SHA256 = Map.ofEntries(
            Map.entry("V1__enable_extensions.sql", "9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c"),
            Map.entry("V2__create_identity_session_idempotency.sql", "c43f2d9a65426e6952d2b47f2908fb2c17c9b6093223f9c8b55ca346f9b21dcf"),
            Map.entry("V3__create_structured_profiles_and_direct_evidence.sql", "6ac81b6a6a55b51e5811b601dcd3b6b2c06d27911bfb539f37d399e071444347"),
            Map.entry("V4__create_agent_runtime_and_ai_budget.sql", "706db49cbd3f39e870c3101eae4f08534236e4954ffbbec0d55dfab48626e01f"),
            Map.entry("V5__create_documents_evidence_and_storage_outbox.sql", "cfae1322bbf7d1412d0be5b7f4535f78ae2702f189b6b095527b6b2186f1dcea"),
            Map.entry("V6__create_job_postings_and_extend_agent_resources.sql", "08248409866a7b396b3f83285ef0b06d30160e1d2a81777ec4264400d80a7d88"),
            Map.entry("V7__create_job_analyses_and_provenance.sql", "7d7b0088a0559626bbbf3aaa52c70a701c2e392d62da75381cd1b3fc55eb217c"),
            Map.entry("V8__create_cover_letters_versions_and_verifications.sql", "4f549aa30f2b24a0d08e70b3105dd0a73c2df91f26ba873bfaacda65c0a466b8"),
            Map.entry("V9__exclude_education_evidence_and_soft_delete_agent_runs.sql", "258a3ffa881a8fbb6f7ece7721039268abfaa866a9f21a0421cb411dfe50f282"),
            Map.entry("V10__exclude_document_education_evidence.sql", "97459fa2eda415f8713d7db1644560106bf663f79761ede5a2481be46cadda27"),
            Map.entry("V11__derive_final_education.sql", "eba74b4e91fc4d9b00ad8b7b32cd892b61b604b632c0c2d64b6d1f34d336d06e"),
            Map.entry("V12__create_interview_research_questions_and_feedback.sql", "c7bc2332e5bcdfb112c91debe94f8cb98cebd6108dee5f96744c3ea17537c23f"));

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
        flyway("13").clean();
    }

    @Test
    void populatedV12DatabasePreservesExistingCatalogWhenUpgradedToV13() throws Exception {
        assertThat(flyway("12").migrate().success).isTrue();
        execute("""
                INSERT INTO ai_price_versions (
                    id,version,catalog_key,effective_from,effective_to,created_at
                ) VALUES (
                    '84000000-0000-4000-8000-000000000001',8400000001,
                    'pre-v13-test-catalog',now(),NULL,now()
                );
                INSERT INTO ai_price_items (
                    id,price_version,provider_key,product_key,unit,
                    unit_size,unit_price_usd,created_at
                ) VALUES (
                    '84000000-0000-4000-8000-000000000002',8400000001,
                    'fake','fixture','CHAT_INPUT_TOKEN',1,0.000000,now()
                );
                """);

        Flyway upgraded = flyway("13");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("SELECT count(*) FROM ai_price_items WHERE price_version=8400000001"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM ai_price_items WHERE price_version=2026073101"))
                .isEqualTo(6);
        assertThat(queryLong("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='ai_usage_records'
                  AND column_name='provider_call_id' AND is_nullable='YES'
                """))
                .isEqualTo(1);
    }

    @Test
    void v1ThroughV12MigrationResourcesKeepApprovedSha256() throws Exception {
        for (Map.Entry<String, String> entry : IMMUTABLE_MIGRATION_SHA256.entrySet()) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                    "db/migration/" + entry.getKey())) {
                assertThat(stream).as(entry.getKey()).isNotNull();
                String actual = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                assertThat(actual).as(entry.getKey()).isEqualTo(entry.getValue());
            }
        }
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

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .target(target)
                .load();
    }
}
