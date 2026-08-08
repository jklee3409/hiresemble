package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class GitHubSourceMigrationTest extends PostgresIntegrationTest {

    private static final UUID USER_A = UUID.fromString("91000000-0000-4000-8000-000000000001");
    private static final UUID USER_B = UUID.fromString("91000000-0000-4000-8000-000000000002");
    private static final UUID SOURCE_A = UUID.fromString("91000000-0000-4000-8000-000000000011");
    private static final UUID SOURCE_B = UUID.fromString("91000000-0000-4000-8000-000000000012");
    private static final UUID REPOSITORY_A = UUID.fromString("91000000-0000-4000-8000-000000000021");
    private static final UUID REPOSITORY_A2 = UUID.fromString("91000000-0000-4000-8000-000000000022");
    private static final UUID REPOSITORY_B = UUID.fromString("91000000-0000-4000-8000-000000000023");
    private static final UUID SNAPSHOT_A = UUID.fromString("91000000-0000-4000-8000-000000000031");
    private static final UUID UNIT_A = UUID.fromString("91000000-0000-4000-8000-000000000041");

    @Autowired private DataSource dataSource;

    @BeforeEach
    void insertFixtureGraph() {
        insertUser(USER_A, "github-a@example.com");
        insertUser(USER_B, "github-b@example.com");
        insertSource(USER_A, SOURCE_A, "octo-a", "ACCOUNT", null, "USER", "WAITING_USER");
        insertSource(USER_B, SOURCE_B, "octo-b/repo-b", "REPOSITORY", "repo-b", null, "READY");
        insertRepository(USER_A, REPOSITORY_A, 101, "octo-a", "repo-a");
        insertRepository(USER_A, REPOSITORY_A2, 102, "octo-a", "repo-a2");
        insertRepository(USER_B, REPOSITORY_B, 201, "octo-b", "repo-b");
        insertLink(USER_A, SOURCE_A, REPOSITORY_A, true, 1);
        insertLink(USER_A, SOURCE_A, REPOSITORY_A2, false, null);
        insertLink(USER_B, SOURCE_B, REPOSITORY_B, true, 1);
        insertSnapshot(USER_A, SOURCE_A, REPOSITORY_A, SNAPSHOT_A);
        insertUnit(USER_A, SNAPSHOT_A, UNIT_A);
    }

    @Test
    void freshSchemaContainsV27TablesOwnerKeysAndGitHubEnums() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT max(version::int) FROM flyway_schema_history WHERE success",
                        Integer.class))
                .isEqualTo(28);
        assertThat(jdbcTemplate.queryForList("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema='public' AND table_name LIKE 'github_%'
                        ORDER BY table_name
                        """, String.class))
                .contains(
                        "github_sources",
                        "github_repositories",
                        "github_source_repository_links",
                        "github_repository_snapshots",
                        "github_source_units",
                        "github_evidence_unit_links",
                        "github_snapshot_object_deletion_outbox");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM pg_constraint
                        WHERE connamespace='public'::regnamespace
                          AND conname IN (
                            'github_sources_user_id_id_uk',
                            'github_repositories_user_id_id_uk',
                            'github_source_repository_links_user_id_id_uk',
                            'github_repository_snapshots_user_id_id_uk',
                            'github_source_units_user_id_id_uk',
                            'github_evidence_unit_links_user_id_id_uk',
                            'github_snapshot_object_deletion_outbox_user_id_id_uk')
                        """, Integer.class))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM agent_runs
                        WHERE workflow_type='GITHUB_INGESTION'
                        """, Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE connamespace='public'::regnamespace
                          AND conname='agent_runs_waiting_action_ck'
                        """, String.class))
                .contains("SELECT_GITHUB_REPOSITORIES");
    }

    @Test
    void ownerSelectionIdentityAndImmutableSnapshotConstraintsRejectInvalidRows() {
        assertThatThrownBy(() -> insertSource(
                        USER_A,
                        UUID.randomUUID(),
                        "octo-a",
                        "ACCOUNT",
                        null,
                        "USER",
                        "WAITING_USER"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertLink(USER_A, SOURCE_A, REPOSITORY_B, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLink(USER_A, SOURCE_A, REPOSITORY_A2, true, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE github_source_repository_links
                        SET selected=true,selection_order=11
                        WHERE user_id=? AND github_source_id=? AND github_repository_id=?
                        """, USER_A, SOURCE_A, REPOSITORY_A2))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertSnapshot(
                        USER_A, SOURCE_A, REPOSITORY_A, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE github_repository_snapshots SET selection_complete=false WHERE id=?
                        """, SNAPSHOT_A))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO github_source_units (
                            id,user_id,snapshot_id,unit_type,repository_path,blob_sha,language,
                            line_start,line_end,content_hash,excerpt,snapshot_ordinal,created_at
                        ) VALUES (?, ?, ?, 'SOURCE', '../secret', NULL, 'Java', 5, 4,
                            repeat('d',64), 'masked excerpt', 2, now())
                        """, UUID.randomUUID(), USER_A, SNAPSHOT_A))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void githubEvidenceRequiresOwnerMatchedPrimaryUnitAndDeduplicatesRawClaim() throws Exception {
        UUID evidence = UUID.fromString("91000000-0000-4000-8000-000000000051");
        executeTransaction("""
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at,
                    github_source_id,github_repository_id,github_snapshot_id,github_claim_key
                ) VALUES (
                    '%s','%s','GITHUB_REPOSITORY','%s',NULL,'PROJECT',
                    'Repository project','Grounded project evidence','{}',0.900,'PENDING',NULL,
                    NULL,0,now(),now(),'%s','%s','%s',repeat('e',64)
                );
                INSERT INTO github_evidence_unit_links (
                    id,user_id,profile_evidence_id,source_unit_id,relation_kind,created_at
                ) VALUES (gen_random_uuid(),'%s','%s','%s','PRIMARY',now());
                """.formatted(
                evidence, USER_A, REPOSITORY_A, SOURCE_A, REPOSITORY_A, SNAPSHOT_A,
                USER_A, evidence, UNIT_A));

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM github_evidence_unit_links
                        WHERE user_id=? AND profile_evidence_id=? AND relation_kind='PRIMARY'
                        """, Integer.class, USER_A, evidence))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,confidence,verification_status,version,
                            created_at,updated_at,github_source_id,github_repository_id,
                            github_snapshot_id,github_claim_key
                        ) VALUES (?,?,'GITHUB_REPOSITORY',?,NULL,'PROJECT','Duplicate',
                            'Duplicate raw claim','{}',0.8,'PENDING',0,now(),now(),?,?,?,repeat('e',64))
                        """, UUID.randomUUID(), USER_A, REPOSITORY_A,
                        SOURCE_A, REPOSITORY_A, SNAPSHOT_A))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,verification_status,version,created_at,updated_at,
                            github_source_id,github_repository_id,github_snapshot_id,github_claim_key
                        ) VALUES (?,?,'MANUAL',NULL,NULL,'PROJECT','Manual','Manual evidence','{}',
                            'PENDING',0,now(),now(),?,?,?,repeat('f',64))
                        """, UUID.randomUUID(), USER_A, SOURCE_A, REPOSITORY_A, SNAPSHOT_A))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void agentRunParityOutboxUniquenessAndTwoUserIsolationAreEnforced() throws Exception {
        UUID run = UUID.fromString("91000000-0000-4000-8000-000000000061");
        executeTransaction("""
                INSERT INTO agent_runs (
                    id,user_id,workflow_type,status,workflow_version,canonical_input_hash,
                    input_reference_snapshot,budget_policy_version,resource_type,resource_id,
                    root_run_id,queued_at,updated_at
                ) VALUES (
                    '%s','%s','GITHUB_INGESTION','QUEUED','github-ingestion-v1',repeat('a',64),
                    '{}',1,'GITHUB_SOURCE','%s','%s',now(),now()
                );
                INSERT INTO agent_run_resource_links (
                    id,user_id,agent_run_id,resource_kind,primary_resource,created_at,github_source_id
                ) VALUES (gen_random_uuid(),'%s','%s','GITHUB_SOURCE',true,now(),'%s');
                """.formatted(run, USER_A, SOURCE_A, run, USER_A, run, SOURCE_A));
        jdbcTemplate.update("""
                UPDATE agent_runs
                SET status='WAITING_USER',
                    waiting_action_type='SELECT_GITHUB_REPOSITORIES',
                    waiting_action_route='/profile/github',
                    waiting_action_message='Select public repositories.'
                WHERE id=?
                """, run);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT waiting_action_type FROM agent_runs WHERE id=?",
                String.class,
                run)).isEqualTo("SELECT_GITHUB_REPOSITORIES");

        assertThatThrownBy(() -> executeTransaction("""
                        UPDATE agent_runs SET workflow_type='DOCUMENT_INGESTION' WHERE id='%s';
                        """.formatted(run)))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE agent_run_resource_links SET github_source_id=NULL WHERE agent_run_id=?
                        """, run))
                .isInstanceOf(DataIntegrityViolationException.class);

        String storageKey = storageKey(USER_A, SOURCE_A, SNAPSHOT_A);
        insertOutbox(USER_A, SOURCE_A, SNAPSHOT_A, storageKey);
        assertThatThrownBy(() -> insertOutbox(USER_A, SOURCE_A, SNAPSHOT_A, storageKey))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM github_sources WHERE user_id=? AND id=?",
                        Integer.class, USER_B, SOURCE_A))
                .isZero();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO github_snapshot_object_deletion_outbox (
                            id,user_id,github_source_id,snapshot_id,storage_key,reason,status,
                            attempt_count,next_attempt_at,created_at
                        ) VALUES (gen_random_uuid(),?,?,?,?, 'SOURCE_DELETE','PENDING',0,now(),now())
                        """, USER_B, SOURCE_A, SNAPSHOT_A, storageKey))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,created_at,updated_at
                ) VALUES (?,?,'fixture-password-hash','Fixture','USER','ACTIVE',now(),now(),now(),now())
                """, id, email);
    }

    private void insertSource(
            UUID userId,
            UUID sourceId,
            String identity,
            String kind,
            String repository,
            String accountType,
            String status) {
        String canonical = "https://github.com/" + identity;
        String owner = identity.split("/", 2)[0];
        jdbcTemplate.update("""
                INSERT INTO github_sources (
                    id,user_id,source_kind,account_type,original_url,canonical_url,owner_login,
                    repository_name,source_status,created_at,updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,now(),now())
                """, sourceId, userId, kind, accountType, canonical, canonical, owner, repository, status);
    }

    private void insertRepository(
            UUID userId, UUID repositoryId, long externalId, String owner, String name) {
        jdbcTemplate.update("""
                INSERT INTO github_repositories (
                    id,user_id,external_repository_id,node_id,owner_login,repository_name,
                    canonical_url,default_branch,is_private,is_fork,is_archived,topics,
                    created_at,updated_at
                ) VALUES (?,?,?,'node-' || ?,?,?,?,'main',false,false,false,'[]',now(),now())
                """, repositoryId, userId, externalId, externalId, owner, name,
                "https://github.com/" + owner + "/" + name);
    }

    private void insertLink(
            UUID userId, UUID sourceId, UUID repositoryId, boolean selected, Integer order) {
        jdbcTemplate.update("""
                INSERT INTO github_source_repository_links (
                    id,user_id,github_source_id,github_repository_id,available,selected,
                    selection_order,discovered_at,updated_at
                ) VALUES (gen_random_uuid(),?,?,?,?,?,?,now(),now())
                """, userId, sourceId, repositoryId, true, selected, order);
    }

    private void insertSnapshot(
            UUID userId, UUID sourceId, UUID repositoryId, UUID snapshotId) {
        jdbcTemplate.update("""
                INSERT INTO github_repository_snapshots (
                    id,user_id,github_repository_id,commit_sha,tree_sha,github_api_version,
                    retrieval_policy_version,selection_complete,upstream_truncated,
                    snapshot_storage_key,checksum_sha256,sanitized_bytes,captured_at
                ) VALUES (?,?,?,repeat('a',40),repeat('b',40),'2026-03-10','github-public-v1',
                    true,false,?,repeat('c',64),1024,now())
                """, snapshotId, userId, repositoryId, storageKey(userId, sourceId, snapshotId));
    }

    private void insertUnit(UUID userId, UUID snapshotId, UUID unitId) {
        jdbcTemplate.update("""
                INSERT INTO github_source_units (
                    id,user_id,snapshot_id,unit_type,repository_path,blob_sha,language,
                    line_start,line_end,content_hash,excerpt,snapshot_ordinal,created_at
                ) VALUES (?, ?, ?, 'README', 'README.md', repeat('b',40), 'Markdown', 1, 3,
                    repeat('d',64), 'sanitized repository excerpt', 1, now())
                """, unitId, userId, snapshotId);
    }

    private void insertOutbox(UUID userId, UUID sourceId, UUID snapshotId, String storageKey) {
        jdbcTemplate.update("""
                INSERT INTO github_snapshot_object_deletion_outbox (
                    id,user_id,github_source_id,snapshot_id,storage_key,reason,status,
                    attempt_count,next_attempt_at,created_at
                ) VALUES (gen_random_uuid(),?,?,?,?,'SOURCE_DELETE','PENDING',0,now(),now())
                """, userId, sourceId, snapshotId, storageKey);
    }

    private String storageKey(UUID userId, UUID sourceId, UUID snapshotId) {
        return "users/" + userId + "/github-sources/" + sourceId
                + "/snapshots/" + snapshotId + "/snapshot.json.gz";
    }

    private void executeTransaction(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute(sql);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
