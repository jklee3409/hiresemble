package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class DashboardMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("dashboard_migration_test")
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
        flyway("17").clean();
    }

    @Test
    void populatedV16DatabaseUpgradesWithoutChangingUserJobs() throws Exception {
        assertThat(flyway("16").migrate().success).isTrue();
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    '17000000-0000-4000-8000-000000000100',
                    'dashboard-upgrade@example.com','hash','Dashboard User','USER','ACTIVE',
                    now(),now(),NULL,NULL,now(),now()
                );
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (
                    '17000000-0000-4000-8000-000000000101',
                    '17000000-0000-4000-8000-000000000100',NULL,NULL,'[]','[]','[]',NULL,0,now(),now()
                );
                """);

        Flyway upgraded = flyway("17");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("SELECT count(*) FROM users WHERE email='dashboard-upgrade@example.com'"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM career_guide_posts WHERE status='PUBLISHED'"))
                .isEqualTo(5);
    }

    @Test
    void emptyDatabaseAddsGuideConstraintsAndStableInitialOrder() throws Exception {
        Flyway latest = flyway("17");
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("SELECT count(*) FROM career_guide_posts"))
                .isEqualTo(5);
        assertThat(queryLong("SELECT min(display_order) FROM career_guide_posts"))
                .isEqualTo(10);
        assertThat(queryLong("SELECT max(version) FROM career_guide_posts"))
                .isEqualTo(1);

        assertThatThrownBy(() -> execute("""
                INSERT INTO career_guide_posts (
                    id,slug,status,display_order,category,title,summary,body,
                    published_at,version,created_at,updated_at
                ) VALUES (
                    gen_random_uuid(),'invalid-published','PUBLISHED',60,'검증','제목','요약','본문',
                    NULL,0,now(),now()
                )
                """))
                .hasMessageContaining("career_guide_posts_publish_state_ck");
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
