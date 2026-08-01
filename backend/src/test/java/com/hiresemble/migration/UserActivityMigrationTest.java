package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class UserActivityMigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("user_activity_migration_test")
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
        flyway("15").clean();
    }

    @Test
    void populatedV14DatabaseAddsIndependentActivitiesWithoutReclassifyingDocumentEvidence()
            throws Exception {
        assertThat(flyway("14").migrate().success).isTrue();
        UUID owner = UUID.randomUUID();
        UUID activity = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    '%s','activity-migration@example.com','hash','Activity User','USER','ACTIVE',
                    now(),now(),NULL,NULL,now(),now()
                );
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES ('%s','%s',NULL,NULL,'[]','[]','[]',NULL,0,now(),now());
                """.formatted(owner, UUID.randomUUID(), owner));

        Flyway upgraded = flyway("15");
        assertThat(upgraded.migrate().success).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        execute("""
                BEGIN;
                INSERT INTO activities (
                    id,user_id,title,activity_type,organizer,started_at,ended_at,ongoing,
                    role,description,achievements,related_url,use_as_material,
                    version,created_at,updated_at,deleted_at
                ) VALUES (
                    '%s','%s','Community','CLUB','University','2025-03-01',NULL,true,
                    'Lead','Organized sessions',NULL,NULL,true,0,now(),now(),NULL
                );
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    '%s','%s','ACTIVITY','%s',NULL,'ACTIVITY','Community',
                    'Organized sessions','{}',NULL,'VERIFIED',now(),NULL,0,now(),now()
                );
                COMMIT;
                """.formatted(activity, owner, evidence, owner, activity));

        assertThat(queryLong("SELECT count(*) FROM activities WHERE user_id='%s'".formatted(owner)))
                .isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM profile_evidence WHERE source_type='DOCUMENT_CHUNK'"))
                .isZero();
        assertThat(queryLong("SELECT count(*) FROM profile_evidence WHERE source_type='ACTIVITY'"))
                .isEqualTo(1);
    }

    @Test
    void emptyDatabaseMigratesThroughV15WithActivityOwnerAndMaterialConstraints()
            throws Exception {
        Flyway latest = flyway("15");
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(queryLong("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='activities'
                  AND column_name='use_as_material' AND is_nullable='NO'
                """)).isEqualTo(1);
        assertThat(queryLong("SELECT count(*) FROM pg_constraint WHERE conname='activities_user_id_fk'"))
                .isEqualTo(1);
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
