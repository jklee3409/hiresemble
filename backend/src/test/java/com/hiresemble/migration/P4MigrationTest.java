package com.hiresemble.migration;

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

class P4MigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p4_migration_test")
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
    void emptyDatabaseMigratesThroughV5WithOnlyP4TablesAndExactVectorPolicy() throws Exception {
        Flyway latest = flyway(null);
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();

        assertThat(queryStrings("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(queryStrings("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"))
                .contains("documents", "document_texts", "document_chunks",
                        "object_deletion_outbox", "agent_run_resource_links")
                .doesNotContain("job_postings", "cover_letters", "research_sources",
                        "interview_sessions", "mock_interview_turns");
        assertThat(queryOne("""
                SELECT format_type(attribute.atttypid,attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class relation ON relation.oid=attribute.attrelid
                WHERE relation.relname='document_chunks' AND attribute.attname='embedding'
                """)).isEqualTo("vector(1536)");
        assertThat(queryOne("SELECT count(*)::text FROM pg_indexes WHERE indexdef ILIKE '%USING hnsw%'"))
                .isEqualTo("0");
        assertThat(queryOne("""
                SELECT provider_key || ':' || product_key || ':' || dimension || ':' ||
                       distance_metric || ':' || generation
                FROM embedding_policy_versions WHERE enabled
                """)).isEqualTo("OpenAI:text-embedding-3-small:1536:COSINE:1");
        assertThat(queryOne("""
                SELECT version || ':' || active::text || ':' ||
                       (policy_json->>'providerEnabled') || ':' ||
                       (policy_json->>'providerKey')
                FROM ai_model_policies WHERE active
                """)).isEqualTo("1:true:false:none");
        assertThat(queryOne("SELECT count(*)::text FROM ai_price_versions")).isEqualTo("0");
    }

    @Test
    void everyEarlierProductionSchemaUpgradesForwardToV5() throws Exception {
        for (String target : List.of("1", "2", "3", "4")) {
            flyway(null).clean();
            assertThat(flyway(target).migrate().success).isTrue();
            assertThat(flyway(null).migrate().success).isTrue();
            assertThat(queryStrings("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                    .containsExactly("1", "2", "3", "4", "5");
            assertThat(queryOne("SELECT count(*)::text FROM information_schema.tables "
                    + "WHERE table_schema='public' AND table_name='documents'"))
                    .isEqualTo("1");
        }
    }

    @Test
    void v1ThroughV4BytesRemainUnchanged() throws Exception {
        assertDigest("db/migration/V1__enable_extensions.sql",
                "9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c");
        assertDigest("db/migration/V2__create_identity_session_idempotency.sql",
                "c43f2d9a65426e6952d2b47f2908fb2c17c9b6093223f9c8b55ca346f9b21dcf");
        assertDigest("db/migration/V3__create_structured_profiles_and_direct_evidence.sql",
                "6ac81b6a6a55b51e5811b601dcd3b6b2c06d27911bfb539f37d399e071444347");
        assertDigest("db/migration/V4__create_agent_runtime_and_ai_budget.sql",
                "706db49cbd3f39e870c3101eae4f08534236e4954ffbbec0d55dfab48626e01f");
    }

    @Test
    void ownerForeignKeysTypedLinkAndOutboxUniquenessAreInstalled() throws Exception {
        assertThat(flyway(null).migrate().success).isTrue();
        assertThat(queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND constraint_type='FOREIGN KEY'
                ORDER BY constraint_name
                """)).contains(
                "document_texts_document_owner_fk",
                "document_chunks_document_owner_fk",
                "documents_latest_run_owner_fk",
                "agent_run_resource_links_run_owner_fk",
                "agent_run_resource_links_document_owner_fk",
                "profile_evidence_document_owner_fk",
                "profile_evidence_chunk_owner_fk",
                "certifications_document_owner_fk",
                "language_scores_document_owner_fk",
                "awards_document_owner_fk");
        assertThat(queryStrings("SELECT indexname FROM pg_indexes WHERE schemaname='public' ORDER BY indexname"))
                .contains("object_deletion_outbox_active_uk",
                        "agent_run_resource_links_primary_run_uk",
                        "documents_owner_checksum_ix")
                .noneMatch(name -> name.toLowerCase().contains("hnsw"));
        assertThat(queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND constraint_type='CHECK'
                ORDER BY constraint_name
                """)).contains(
                "documents_file_size_ck", "documents_storage_key_ck",
                "document_chunks_embedding_shape_ck", "document_chunks_page_pair_ck",
                "object_deletion_outbox_attempt_ck", "agent_run_resource_links_kind_ck");
    }

    @Test
    void v4ProspectiveDocumentReferencesUpgradeToSafeNonInventedState() throws Exception {
        assertThat(flyway("4").migrate().success).isTrue();
        UUID userId = UUID.randomUUID();
        UUID certificationId = UUID.randomUUID();
        UUID prospectiveDocumentId = UUID.randomUUID();
        UUID documentEvidenceId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO users (
                            id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                            last_login_at,withdrawn_at,created_at,updated_at
                        ) VALUES ('%s','legacy@example.com','hash','Legacy','USER','ACTIVE',
                                  now(),now(),NULL,NULL,now(),now())
                        """.formatted(userId));
                statement.execute("""
                        INSERT INTO user_profiles (
                            id,user_id,legal_name,introduction,desired_roles,desired_industries,
                            desired_locations,expected_graduation_date,version,created_at,updated_at
                        ) VALUES ('%s','%s',NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                        """.formatted(UUID.randomUUID(), userId));
                statement.execute("""
                        INSERT INTO certifications (
                            id,user_id,name,issuer,credential_number,acquired_date,expires_at,
                            description,evidence_document_id,version,created_at,updated_at,deleted_at
                        ) VALUES ('%s','%s','Legacy Cert',NULL,NULL,NULL,NULL,NULL,'%s',0,now(),now(),NULL)
                        """.formatted(certificationId, userId, prospectiveDocumentId));
                statement.execute("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,confidence,verification_status,verified_at,
                            source_deleted_at,version,created_at,updated_at
                        ) VALUES ('%s','%s','CERTIFICATION','%s',NULL,'CERTIFICATION',
                                  'Legacy Cert','Legacy Cert','{}',NULL,'VERIFIED',now(),NULL,0,now(),now())
                        """.formatted(UUID.randomUUID(), userId, certificationId));
                statement.execute("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,confidence,verification_status,verified_at,
                            source_deleted_at,version,created_at,updated_at
                        ) VALUES ('%s','%s','DOCUMENT_CHUNK','%s','%s','EXPERIENCE',
                                  'Prospective evidence','Prospective content','{"legacy":true}',0.5,
                                  'PENDING',NULL,NULL,0,now(),now())
                        """.formatted(documentEvidenceId, userId, UUID.randomUUID(), prospectiveDocumentId));
            }
            connection.commit();
        }

        assertThat(flyway(null).migrate().success).isTrue();

        assertThat(queryOne("SELECT evidence_document_id IS NULL FROM certifications WHERE id='"
                + certificationId + "'")).isEqualTo("t");
        assertThat(queryOne("""
                SELECT verification_status || ':' || (document_id IS NULL)::text || ':' ||
                       (source_entity_id IS NULL)::text || ':' || metadata::text
                FROM profile_evidence WHERE id='%s'
                """.formatted(documentEvidenceId)))
                .isEqualTo("SOURCE_DELETED:true:true:{}");
    }

    private void assertDigest(String resource, String expected) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest).isEqualTo(expected);
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .target(target == null ? "5" : target);
        return configuration.load();
    }

    private String queryOne(String sql) throws Exception {
        List<String> values = queryStrings(sql);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private List<String> queryStrings(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) values.add(resultSet.getString(1));
        }
        return values;
    }
}
