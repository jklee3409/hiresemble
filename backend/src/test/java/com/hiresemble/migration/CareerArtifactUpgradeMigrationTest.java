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

class CareerArtifactUpgradeMigrationTest {

    private static final String V26_SHA256 =
            "414a063a773b994084468dd04975c37461142452741dbd54dbfa3ac0d7dc8908";
    private static final String V27_SHA256 =
            "5754a6410887b93b4cb50d09cdf2162ea64266a400b8211a73359725dbdee666";
    private static final int V26_FLYWAY_CHECKSUM = -1033897172;
    private static final int V27_FLYWAY_CHECKSUM = 1516904878;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("career_artifact_upgrade_test")
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
        flyway("28").clean();
    }

    @Test
    void populatedV27UpgradesToV28WithoutRewritingCanonicalOrGitHubData() throws Exception {
        assertThat(flyway("27").migrate().success).isTrue();
        assertThat(queryLong("""
                SELECT checksum FROM flyway_schema_history WHERE version='26' AND success
                """)).isEqualTo(V26_FLYWAY_CHECKSUM);
        assertThat(queryLong("""
                SELECT checksum FROM flyway_schema_history WHERE version='27' AND success
                """)).isEqualTo(V27_FLYWAY_CHECKSUM);

        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,created_at,updated_at
                ) VALUES (
                    '94000000-0000-4000-8000-000000000001','upgrade-v27@example.com',
                    'fixture-password-hash','Upgrade Fixture','USER','ACTIVE',
                    now(),now(),now(),now()
                );
                INSERT INTO experience_items (
                    id,user_id,canonical_evidence_id,evidence_category,title,content,
                    verification_status,match_kind,match_policy_version,canonical_fingerprint,
                    version,created_at,updated_at
                ) VALUES (
                    '94000000-0000-4000-8000-000000000011',
                    '94000000-0000-4000-8000-000000000001',
                    '94000000-0000-4000-8000-000000000012','PROJECT',
                    'Preserved project','Preserved canonical project content',
                    'VERIFIED','NEW','experience-semantic-v1',repeat('a',64),0,now(),now()
                );
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    '94000000-0000-4000-8000-000000000012',
                    '94000000-0000-4000-8000-000000000001','EXPERIENCE',
                    '94000000-0000-4000-8000-000000000011',NULL,'PROJECT',
                    'Preserved project','Preserved canonical project content',
                    '{}',1.0,'VERIFIED',now(),NULL,0,now(),now()
                );
                INSERT INTO github_sources (
                    id,user_id,source_kind,account_type,original_url,canonical_url,owner_login,
                    repository_name,source_status,created_at,updated_at
                ) VALUES (
                    '94000000-0000-4000-8000-000000000021',
                    '94000000-0000-4000-8000-000000000001','REPOSITORY',NULL,
                    'https://github.com/octo/preserved','https://github.com/octo/preserved',
                    'octo','preserved','READY',now(),now()
                );
                INSERT INTO github_repositories (
                    id,user_id,external_repository_id,node_id,owner_login,repository_name,
                    canonical_url,default_branch,is_private,is_fork,is_archived,topics,
                    created_at,updated_at
                ) VALUES (
                    '94000000-0000-4000-8000-000000000022',
                    '94000000-0000-4000-8000-000000000001',9001,'node-9001','octo',
                    'preserved','https://github.com/octo/preserved','main',false,false,false,
                    '[]',now(),now()
                );
                INSERT INTO github_source_repository_links (
                    id,user_id,github_source_id,github_repository_id,available,selected,
                    selection_order,discovered_at,updated_at
                ) VALUES (
                    gen_random_uuid(),'94000000-0000-4000-8000-000000000001',
                    '94000000-0000-4000-8000-000000000021',
                    '94000000-0000-4000-8000-000000000022',true,true,1,now(),now()
                );
                """);

        Flyway upgraded = flyway("28");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("SELECT count(*) FROM users")).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM experience_items")).isEqualTo(1);
        assertThat(queryLong("""
                SELECT count(*) FROM profile_evidence WHERE source_type='EXPERIENCE'
                """)).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM github_sources")).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM github_repositories")).isEqualTo(1);
        assertThat(queryLong("""
                SELECT checksum FROM flyway_schema_history WHERE version='26' AND success
                """)).isEqualTo(V26_FLYWAY_CHECKSUM);
        assertThat(queryLong("""
                SELECT checksum FROM flyway_schema_history WHERE version='27' AND success
                """)).isEqualTo(V27_FLYWAY_CHECKSUM);
        assertThat(queryLong("""
                SELECT count(*) FROM flyway_schema_history WHERE version='28' AND success
                """)).isEqualTo(1);
    }

    @Test
    void v26AndV27MigrationResourcesKeepApprovedWorkingTreeSha256() throws Exception {
        assertResourceSha("db/migration/V26__create_canonical_experience_library.sql", V26_SHA256);
        assertResourceSha("db/migration/V27__create_github_source_ingestion.sql", V27_SHA256);
    }

    private void assertResourceSha(String path, String expected) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            assertThat(actual).isEqualTo(expected);
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
