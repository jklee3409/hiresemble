package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class P1MigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("migration_test")
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
    void emptyDatabaseMigratesFromV1ThroughTheP1Schema() throws Exception {
        Flyway flyway = flyway(null);
        assertThat(flyway.migrate().success).isTrue();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        assertThat(publicTables())
                .contains(
                        "flyway_schema_history",
                        "users",
                        "user_profiles",
                        "spring_session",
                        "spring_session_attributes",
                        "idempotency_records")
                .doesNotContain("account_deletion_tasks", "documents", "agent_runs");
        assertThat(constraintNames("users"))
                .contains(
                        "users_pk",
                        "users_email_uk",
                        "users_email_normalized_ck",
                        "users_role_ck",
                        "users_status_ck");
        assertThat(constraintNames("user_profiles"))
                .contains("user_profiles_pk", "user_profiles_user_id_uk", "user_profiles_user_id_fk");
        assertThat(constraintNames("idempotency_records"))
                .contains(
                        "idempotency_records_pk",
                        "idempotency_records_user_id_id_uk",
                        "idempotency_records_scope_key_uk",
                        "idempotency_records_state_ck",
                        "idempotency_records_state_payload_ck");
        assertThat(indexNames("spring_session"))
                .contains("spring_session_ix1", "spring_session_ix2", "spring_session_ix3");
        assertThat(indexNames("idempotency_records"))
                .contains("idempotency_records_expires_at_ix", "idempotency_records_user_state_ix");
    }

    @Test
    void databaseAlreadyAtV1UpgradesWithoutChangingV1() throws Exception {
        Flyway v1 = flyway("1");
        assertThat(v1.migrate().success).isTrue();
        assertThat(publicTables()).contains("flyway_schema_history").doesNotContain("users");

        Flyway latest = flyway(null);
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(publicTables())
                .contains("users", "user_profiles", "spring_session", "idempotency_records");
        assertThat(appliedVersions()).containsExactly("1", "2");
    }

    @Test
    void v1ExtensionMigrationBytesRemainUnchanged() throws Exception {
        try (InputStream input = new ClassPathResource(
                        "db/migration/V1__enable_extensions.sql")
                .getInputStream()) {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest)
                    .isEqualTo("9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c");
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Set<String> publicTables() throws Exception {
        return queryStrings("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """);
    }

    private Set<String> constraintNames(String tableName) throws Exception {
        return queryStrings("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = '%s'
                ORDER BY constraint_name
                """.formatted(tableName));
    }

    private Set<String> indexNames(String tableName) throws Exception {
        return queryStrings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = '%s'
                ORDER BY indexname
                """.formatted(tableName));
    }

    private Set<String> appliedVersions() throws Exception {
        return queryStrings("""
                SELECT version
                FROM flyway_schema_history
                WHERE success
                ORDER BY installed_rank
                """);
    }

    private Set<String> queryStrings(String sql) throws Exception {
        Set<String> values = new LinkedHashSet<>();
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }
}
