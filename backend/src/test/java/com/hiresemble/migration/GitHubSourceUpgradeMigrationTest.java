package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class GitHubSourceUpgradeMigrationTest {

    private static final String V26_SHA256 =
            "414a063a773b994084468dd04975c37461142452741dbd54dbfa3ac0d7dc8908";
    private static final int V26_FLYWAY_CHECKSUM = -1033897172;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("github_source_upgrade_test")
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
        flyway("27").clean();
    }

    @Test
    void populatedV26CanonicalLibraryUpgradesToV27WithoutRewritingHistory()
            throws Exception {
        assertThat(flyway("26").migrate().success).isTrue();
        assertThat(queryLong("""
                SELECT checksum FROM flyway_schema_history WHERE version='26' AND success
                """)).isEqualTo(V26_FLYWAY_CHECKSUM);

        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,created_at,updated_at
                ) VALUES (
                    '92000000-0000-4000-8000-000000000001','upgrade-v26@example.com',
                    'fixture-password-hash','Upgrade Fixture','USER','ACTIVE',
                    now(),now(),now(),now()
                );
                INSERT INTO experience_items (
                    id,user_id,canonical_evidence_id,evidence_category,title,content,
                    verification_status,match_kind,match_policy_version,canonical_fingerprint,
                    version,created_at,updated_at
                ) VALUES (
                    '92000000-0000-4000-8000-000000000011',
                    '92000000-0000-4000-8000-000000000001',
                    '92000000-0000-4000-8000-000000000012','PROJECT',
                    'Preserved canonical project','Preserved canonical project content',
                    'VERIFIED','NEW','experience-semantic-v1',repeat('a',32),0,now(),now()
                );
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    '92000000-0000-4000-8000-000000000012',
                    '92000000-0000-4000-8000-000000000001','EXPERIENCE',
                    '92000000-0000-4000-8000-000000000011',NULL,'PROJECT',
                    'Preserved canonical project','Preserved canonical project content',
                    '{}',1.0,'VERIFIED',now(),NULL,0,now(),now()
                );
                """);

        Flyway upgraded = flyway("27");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("SELECT count(*) FROM experience_items")).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM profile_evidence WHERE source_type='EXPERIENCE'"))
                .isEqualTo(1);
        assertThat(queryLong("""
                SELECT count(*) FROM profile_evidence
                WHERE github_source_id IS NULL AND github_repository_id IS NULL
                  AND github_snapshot_id IS NULL AND github_claim_key IS NULL
                """)).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM github_sources")).isZero();
        assertThat(queryLong("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name='career_artifacts'
                """)).isZero();
        assertThat(queryLong("""
                SELECT checksum FROM flyway_schema_history WHERE version='26' AND success
                """)).isEqualTo(V26_FLYWAY_CHECKSUM);
        assertThat(queryLong("""
                SELECT count(*) FROM flyway_schema_history WHERE version='27' AND success
                """)).isEqualTo(1);
        assertThat(queryLong("""
                SELECT count(*) FROM pg_constraint
                WHERE connamespace='public'::regnamespace
                  AND conname='agent_runs_waiting_action_ck'
                  AND pg_get_constraintdef(oid) LIKE '%SELECT_GITHUB_REPOSITORIES%'
                """)).isEqualTo(1);
    }

    @Test
    void v26MigrationResourceKeepsTheApprovedWorkingTreeSha256() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V26__create_canonical_experience_library.sql")) {
            assertThat(stream).isNotNull();
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            assertThat(actual).isEqualTo(V26_SHA256);
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
