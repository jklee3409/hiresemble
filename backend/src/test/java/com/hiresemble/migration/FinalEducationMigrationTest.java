package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class FinalEducationMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("final_education_migration_test")
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
        flyway("11").clean();
    }

    @Test
    void populatedV10DatabaseBackfillsLevelsAndRecomputesFinalEducation() throws Exception {
        assertThat(flyway("10").migrate().success).isTrue();
        UUID owner = UUID.randomUUID();
        UUID highSchool = UUID.randomUUID();
        UUID bachelor = UUID.randomUUID();
        UUID master = UUID.randomUUID();
        UUID doctorate = UUID.randomUUID();

        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    '%s','final-education@example.com','hash','Migration User','USER','ACTIVE',
                    now(),now(),NULL,NULL,now(),now()
                )
                """.formatted(owner));
        insertEducation(highSchool, owner, "한빛고등학교", null, true, "2020-02-01");
        insertEducation(bachelor, owner, "한국대학교", "Bachelor", false, "2024-02-01");
        insertEducation(master, owner, "한국대학교 대학원", "석사", false, "2026-02-01");
        insertEducation(doctorate, owner, "한국대학교 대학원", "Ph.D.", false, "2028-02-01");

        Flyway upgraded = flyway("11");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();

        assertThat(educationLevels())
                .containsEntry(highSchool, "HIGH_SCHOOL")
                .containsEntry(bachelor, "BACHELOR")
                .containsEntry(master, "MASTER")
                .containsEntry(doctorate, "DOCTORATE");
        assertThat(queryUuid(
                        "SELECT id FROM educations WHERE user_id='%s' AND is_primary"
                                .formatted(owner)))
                .isEqualTo(doctorate);
    }

    @Test
    void emptyDatabaseMigratesThroughV11WithFinalEducationConstraint() throws Exception {
        Flyway latest = flyway("11");
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryString("""
                        SELECT is_nullable
                        FROM information_schema.columns
                        WHERE table_schema='public'
                          AND table_name='educations'
                          AND column_name='education_level'
                        """))
                .isEqualTo("NO");
        assertThat(queryString("""
                        SELECT count(*)::text
                        FROM pg_constraint
                        WHERE conname='educations_education_level_ck'
                        """))
                .isEqualTo("1");
    }

    private void insertEducation(
            UUID id,
            UUID owner,
            String schoolName,
            String degree,
            boolean primary,
            String graduationDate)
            throws Exception {
        String degreeValue = degree == null ? "NULL" : "'" + degree.replace("'", "''") + "'";
        execute("""
                INSERT INTO educations (
                    id,user_id,school_name,major,degree,education_status,
                    admission_date,graduation_date,gpa,gpa_scale,is_primary,
                    description,version,created_at,updated_at,deleted_at
                ) VALUES (
                    '%s','%s','%s',NULL,%s,'GRADUATED',
                    NULL,'%s',NULL,NULL,%s,
                    NULL,0,now(),now(),NULL
                )
                """.formatted(
                id,
                owner,
                schoolName.replace("'", "''"),
                degreeValue,
                graduationDate,
                primary));
    }

    private Map<UUID, String> educationLevels() throws Exception {
        Map<UUID, String> values = new LinkedHashMap<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT id, education_level FROM educations")) {
            while (resultSet.next()) {
                values.put(resultSet.getObject("id", UUID.class), resultSet.getString("education_level"));
            }
        }
        return values;
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private UUID queryUuid(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getObject(1, UUID.class);
        }
    }

    private String queryString(String sql) throws Exception {
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
        return Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .cleanDisabled(false)
                .target(target)
                .load();
    }
}
