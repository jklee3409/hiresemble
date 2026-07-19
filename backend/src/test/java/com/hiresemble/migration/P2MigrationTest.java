package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class P2MigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p2_migration_test")
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
    void emptyDatabaseMigratesThroughV3WithOnlyP2TablesAndConstraints() throws Exception {
        Flyway latest = flyway(null);
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();

        assertThat(appliedVersions()).containsExactly("1", "2", "3");
        assertThat(publicTables())
                .contains(
                        "users",
                        "user_profiles",
                        "educations",
                        "certifications",
                        "language_scores",
                        "awards",
                        "careers",
                        "profile_evidence")
                .doesNotContain(
                        "documents",
                        "document_texts",
                        "document_chunks",
                        "agent_runs",
                        "jobs");
        for (String table : Set.of(
                "user_profiles",
                "educations",
                "certifications",
                "language_scores",
                "awards",
                "careers",
                "profile_evidence")) {
            assertThat(constraintNames(table)).contains(table + "_user_id_id_uk");
        }
        assertThat(indexNames("educations")).contains("educations_one_active_primary_ix");
        assertThat(indexNames("profile_evidence"))
                .contains("profile_evidence_one_direct_source_ix");
        assertThat(constraintNames("profile_evidence"))
                .contains(
                        "profile_evidence_source_type_ck",
                        "profile_evidence_verification_status_ck",
                        "profile_evidence_confidence_ck",
                        "profile_evidence_metadata_ck")
                .doesNotContain("profile_evidence_document_id_fk");
        assertThat(queryOne("""
                SELECT provolatile FROM pg_proc WHERE proname='valid_canonical_string_array'
                """))
                .isEqualTo("i");
    }

    @Test
    void v1OnlyDatabaseUpgradesForwardThroughV2AndV3() throws Exception {
        assertThat(flyway("1").migrate().success).isTrue();
        assertThat(flyway(null).migrate().success).isTrue();
        assertThat(appliedVersions()).containsExactly("1", "2", "3");
        assertThat(publicTables()).contains("profile_evidence", "educations");
    }

    @Test
    void v2OnlyDatabaseUpgradesForwardThroughV3() throws Exception {
        assertThat(flyway("2").migrate().success).isTrue();
        assertThat(publicTables()).doesNotContain("profile_evidence", "educations");
        assertThat(flyway(null).migrate().success).isTrue();
        assertThat(appliedVersions()).containsExactly("1", "2", "3");
        assertThat(publicTables()).contains("profile_evidence", "educations");
    }

    @Test
    void profileArraysAreCanonicalBoundedStringArraysInTheDatabase() throws Exception {
        flyway(null).migrate();
        UUID userId = seedUserAndProfile("arrays@example.com");

        execute("UPDATE user_profiles SET desired_roles='[\"Backend\",\"Data\"]' WHERE user_id='" + userId + "'");
        assertSqlFails("UPDATE user_profiles SET desired_roles='{}' WHERE user_id='" + userId + "'", "user_profiles_desired_roles_ck");
        assertSqlFails("UPDATE user_profiles SET desired_roles='[1]' WHERE user_id='" + userId + "'", "user_profiles_desired_roles_ck");
        assertSqlFails("UPDATE user_profiles SET desired_roles='[\" Backend\"]' WHERE user_id='" + userId + "'", "user_profiles_desired_roles_ck");
        assertSqlFails("UPDATE user_profiles SET desired_roles='[\"Backend\",\"backend\"]' WHERE user_id='" + userId + "'", "user_profiles_desired_roles_ck");
        assertSqlFails(
                "UPDATE user_profiles SET desired_roles='[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]' WHERE user_id='"
                        + userId + "'",
                "user_profiles_desired_roles_ck");
    }

    @Test
    void structuredDateGpaStatusAndEvidenceChecksRejectInvalidRows() throws Exception {
        flyway(null).migrate();
        UUID userId = seedUserAndProfile("checks@example.com");
        String common = "', '" + userId + "', 'School', NULL, NULL, ";

        assertSqlFails(
                "INSERT INTO educations VALUES ('" + UUID.randomUUID() + common
                        + "'UNKNOWN',NULL,NULL,NULL,NULL,false,NULL,0,now(),now(),NULL)",
                "educations_education_status_ck");
        assertSqlFails(
                "INSERT INTO educations VALUES ('" + UUID.randomUUID() + common
                        + "'GRADUATED','2025-01-01','2024-01-01',NULL,NULL,false,NULL,0,now(),now(),NULL)",
                "educations_dates_ck");
        assertSqlFails(
                "INSERT INTO educations VALUES ('" + UUID.randomUUID() + common
                        + "'GRADUATED',NULL,NULL,4.0,NULL,false,NULL,0,now(),now(),NULL)",
                "educations_gpa_pair_ck");
        assertSqlFails(
                "INSERT INTO educations VALUES ('" + UUID.randomUUID() + common
                        + "'GRADUATED',NULL,NULL,4.6,4.5,false,NULL,0,now(),now(),NULL)",
                "educations_gpa_ck");
        assertSqlFails(
                "INSERT INTO certifications VALUES ('" + UUID.randomUUID() + "','" + userId
                        + "','Cert',NULL,NULL,'2025-01-01','2024-01-01',NULL,NULL,0,now(),now(),NULL)",
                "certifications_dates_ck");
        assertSqlFails(
                "INSERT INTO language_scores VALUES ('" + UUID.randomUUID() + "','" + userId
                        + "','Test','Score',NULL,'2025-01-01','2024-01-01',NULL,0,now(),now(),NULL)",
                "language_scores_dates_ck");
        assertSqlFails(
                "INSERT INTO careers VALUES ('" + UUID.randomUUID() + "','" + userId
                        + "','Company',NULL,NULL,'2025-01-01','2024-01-01',false,NULL,NULL,0,now(),now(),NULL)",
                "careers_dates_ck");
        assertSqlFails(
                "INSERT INTO careers VALUES ('" + UUID.randomUUID() + "','" + userId
                        + "','Company',NULL,NULL,NULL,'2025-01-01',true,NULL,NULL,0,now(),now(),NULL)",
                "careers_current_ck");

        assertManualEvidenceFails(userId, "'UNKNOWN'", "'VERIFIED'", "NULL", "'{}'", "profile_evidence_source_shape_ck");
        assertManualEvidenceFails(userId, "'MANUAL'", "'UNKNOWN'", "NULL", "'{}'", "profile_evidence_verification_status_ck");
        assertManualEvidenceFails(userId, "'MANUAL'", "'PENDING'", "1.001", "'{}'", "profile_evidence_confidence_ck");
        assertManualEvidenceFails(userId, "'MANUAL'", "'PENDING'", "NULL", "'[]'", "profile_evidence_metadata_ck");
    }

    @Test
    void primaryAndDirectEvidenceInvariantsCoverUniquenessOwnerAndMetadataBoundary()
            throws Exception {
        flyway(null).migrate();
        UUID firstUser = seedUserAndProfile("first@example.com");
        UUID secondUser = seedUserAndProfile("second@example.com");
        UUID sourceId = UUID.randomUUID();

        insertEducationAndEvidence(firstUser, firstUser, sourceId, true);
        assertThat(queryOne("SELECT count(*)::text FROM profile_evidence WHERE source_entity_id='"
                        + sourceId + "'"))
                .isEqualTo("1");

        assertThatThrownBy(() -> insertEducationAndEvidence(
                        firstUser, firstUser, UUID.randomUUID(), true))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("educations_one_active_primary_ix");
        assertThatThrownBy(() -> inTransaction(statement -> statement.execute(
                        directEvidenceSql(UUID.randomUUID(), firstUser, "EDUCATION", sourceId, "'{}'"))))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("profile_evidence_one_direct_source_ix");
        assertThatThrownBy(() -> insertEducationWithoutEvidence(firstUser, UUID.randomUUID()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("active structured profile source must have one owner-matched direct evidence");
        assertThatThrownBy(() -> insertEducationAndEvidence(
                        firstUser, secondUser, UUID.randomUUID(), false))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("active structured profile source must have one owner-matched direct evidence");

        assertThat(queryOne("SELECT octet_length(jsonb_build_object('v', repeat('x',16375))::text)::text"))
                .isEqualTo("16384");
        execute(manualEvidenceSql(
                UUID.randomUUID(), firstUser, "jsonb_build_object('v',repeat('x',16375))"));
        assertSqlFails(
                manualEvidenceSql(
                        UUID.randomUUID(), firstUser, "jsonb_build_object('v',repeat('x',16376))"),
                "profile_evidence_metadata_ck");
    }

    private void assertManualEvidenceFails(
            UUID userId,
            String sourceType,
            String status,
            String confidence,
            String metadata,
            String constraint) {
        String verifiedAt = status.equals("'VERIFIED'") ? "now()" : "NULL";
        assertSqlFails(
                "INSERT INTO profile_evidence VALUES ('" + UUID.randomUUID() + "','" + userId + "',"
                        + sourceType + ",NULL,NULL,'CATEGORY','Title','Content'," + metadata + ","
                        + confidence + "," + status + "," + verifiedAt + ",NULL,0,now(),now())",
                constraint);
    }

    private UUID seedUserAndProfile(String email) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES ('%s','%s','hash','User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now());
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES ('%s','%s',NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """.formatted(userId, email, profileId, userId));
        return userId;
    }

    private void insertEducationAndEvidence(
            UUID sourceUser, UUID evidenceUser, UUID sourceId, boolean primary) throws Exception {
        inTransaction(statement -> {
            statement.execute(educationSql(sourceId, sourceUser, primary));
            statement.execute(directEvidenceSql(
                    UUID.randomUUID(), evidenceUser, "EDUCATION", sourceId, "'{}'"));
        });
    }

    private void insertEducationWithoutEvidence(UUID userId, UUID sourceId) throws Exception {
        inTransaction(statement -> statement.execute(educationSql(sourceId, userId, false)));
    }

    private String educationSql(UUID id, UUID userId, boolean primary) {
        return "INSERT INTO educations VALUES ('" + id + "','" + userId
                + "','School',NULL,NULL,'GRADUATED',NULL,NULL,NULL,NULL," + primary
                + ",NULL,0,now(),now(),NULL)";
    }

    private String directEvidenceSql(
            UUID id, UUID userId, String sourceType, UUID sourceId, String metadata) {
        return "INSERT INTO profile_evidence VALUES ('" + id + "','" + userId + "','"
                + sourceType + "','" + sourceId
                + "',NULL,'CATEGORY','Title','Content'," + metadata
                + ",NULL,'VERIFIED',now(),NULL,0,now(),now())";
    }

    private String manualEvidenceSql(UUID id, UUID userId, String metadata) {
        return "INSERT INTO profile_evidence VALUES ('" + id + "','" + userId
                + "','MANUAL',NULL,NULL,'CATEGORY','Title','Content'," + metadata
                + ",NULL,'PENDING',NULL,NULL,0,now(),now())";
    }

    private void inTransaction(SqlAction action) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                action.execute(statement);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void assertSqlFails(String sql, String constraintName) {
        assertThatThrownBy(() -> execute(sql))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(constraintName);
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryOne(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private Set<String> publicTables() throws Exception {
        return queryStrings("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' ORDER BY table_name
                """);
    }

    private Set<String> constraintNames(String table) throws Exception {
        return queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND table_name='%s' ORDER BY constraint_name
                """.formatted(table));
    }

    private Set<String> indexNames(String table) throws Exception {
        return queryStrings("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname='public' AND tablename='%s' ORDER BY indexname
                """.formatted(table));
    }

    private Set<String> appliedVersions() throws Exception {
        return queryStrings("""
                SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank
                """);
    }

    private Set<String> queryStrings(String sql) throws Exception {
        Set<String> values = new LinkedHashSet<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false);
        configuration.target(target == null ? "3" : target);
        return configuration.load();
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute(Statement statement) throws Exception;
    }
}
