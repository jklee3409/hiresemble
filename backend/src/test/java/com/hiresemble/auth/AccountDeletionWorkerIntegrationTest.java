package com.hiresemble.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.auth.application.service.AccountDeletionWorker;
import com.hiresemble.auth.infrastructure.persistence.AccountDeletionTaskStore;
import com.hiresemble.auth.infrastructure.persistence.AccountPurgeStore;
import com.hiresemble.githubsource.infrastructure.GitHubAppConnectionStore;
import java.time.Duration;
import com.hiresemble.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountDeletionWorkerIntegrationTest extends PostgresIntegrationTest {

    @Autowired private AccountDeletionWorker worker;
    @Autowired private AccountDeletionTaskStore taskStore;
    @Autowired private AccountPurgeStore purgeStore;
    @Autowired private GitHubAppConnectionStore connectionStore;

    @Test
    void accountDeletionPreparationQueriesAcceptAnOwnerWithoutOptionalResources() {
        UUID userId = seedUser("terminal-preparation@example.com", "WITHDRAWN");
        assertThat(purgeStore.cancellableRuns(userId)).isEmpty();
        assertThat(connectionStore.list(userId)).isEmpty();
        assertThat(purgeStore.documentObjectsNeedingDeletion(userId)).isEmpty();
        assertThat(purgeStore.snapshotObjectsNeedingDeletion(userId)).isEmpty();
        assertThat(purgeStore.artifactObjectsNeedingDeletion(userId)).isEmpty();
    }

    @Test
    void purgeStoreFinalTransactionCanDeleteWithdrawnUserAndCompleteFkFreeTask() {
        UUID userId = seedUser("terminal-direct@example.com", "WITHDRAWN");
        UUID taskId = taskStore.enqueue(UUID.randomUUID(), userId, Instant.now());
        var claimed = taskStore.claimDue(Instant.now().plusSeconds(1), Duration.ofMinutes(5))
                .orElseThrow();

        assertThat(purgeStore.terminalState(userId)).isEqualTo(AccountPurgeStore.TerminalState.READY);
        assertThat(purgeStore.finalizePurge(
                        taskId,
                        userId,
                        claimed.claimToken(),
                        claimed.attemptCount(),
                        Instant.now().plusSeconds(2)))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, userId))
                .isZero();
    }

    @Test
    void finalPurgeDeletesOnlyWithdrawnOwnerScrubsSubjectAndReleasesEmailForRegistration() {
        UUID withdrawn = seedUser("terminal-purge@example.com", "WITHDRAWN");
        UUID other = seedUser("terminal-purge-other@example.com", "ACTIVE");
        UUID taskId = taskStore.enqueue(UUID.randomUUID(), withdrawn, Instant.now());

        worker.processDue();

        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status,attempt_count,last_error_code FROM account_deletion_tasks WHERE id=?",
                        taskId))
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("attempt_count", 1)
                .containsEntry("last_error_code", null);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, withdrawn))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, other))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT subject_user_id FROM account_deletion_tasks WHERE id=?",
                        UUID.class,
                        taskId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT completed_at IS NOT NULL FROM account_deletion_tasks WHERE id=?",
                        Boolean.class,
                        taskId))
                .isTrue();
        UUID replacement = seedUser("terminal-purge@example.com", "ACTIVE");
        assertThat(replacement).isNotEqualTo(withdrawn);
    }

    @Test
    void objectDeletionMustReachSucceededBeforePhysicalUserPurge() {
        UUID userId = seedUser("terminal-object@example.com", "WITHDRAWN");
        UUID documentId = seedDocument(userId);
        UUID taskId = taskStore.enqueue(UUID.randomUUID(), userId, Instant.now());

        worker.processDue();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account_deletion_tasks WHERE id=?",
                        String.class,
                        taskId))
                .isEqualTo("RETRY_WAIT");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM object_deletion_outbox WHERE document_id=?",
                        String.class,
                        documentId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, userId))
                .isEqualTo(1L);

        jdbcTemplate.update("""
                UPDATE object_deletion_outbox
                SET status='SUCCEEDED',completed_at=now(),claim_token=NULL,lease_expires_at=NULL
                WHERE document_id=?
                """, documentId);
        makeTaskDue(taskId);
        worker.processDue();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, userId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account_deletion_tasks WHERE id=?",
                        String.class,
                        taskId))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void deadObjectDeletionPreventsPhysicalPurgeAndMakesTerminalFailureVisible() {
        UUID userId = seedUser("terminal-dead@example.com", "WITHDRAWN");
        UUID documentId = seedDocument(userId);
        UUID taskId = taskStore.enqueue(UUID.randomUUID(), userId, Instant.now());
        worker.processDue();

        jdbcTemplate.update("""
                UPDATE object_deletion_outbox
                SET status='DEAD',completed_at=now(),last_error_code='OBJECT_STORAGE_DELETE_FAILED',
                    claim_token=NULL,lease_expires_at=NULL
                WHERE document_id=?
                """, documentId);
        makeTaskDue(taskId);
        worker.processDue();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, userId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account_deletion_tasks WHERE id=?",
                        String.class,
                        taskId))
                .isEqualTo("DEAD");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_error_code FROM account_deletion_tasks WHERE id=?",
                        String.class,
                        taskId))
                .isEqualTo("ACCOUNT_DELETION_CHILD_DEAD");
    }

    @Test
    void githubUninstallMustReachSucceededAndExpiredTaskLeaseIsRecovered() {
        UUID userId = seedUser("terminal-github@example.com", "WITHDRAWN");
        UUID connectionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO github_app_connections (
                  id,user_id,github_installation_id,target_account_id,target_account_login,
                  target_account_type,repository_selection,status,permission_snapshot,version,
                  connected_at,verified_at,last_checked_at,disconnected_at,created_at,updated_at)
                VALUES (?,?,74001,84001,'acme','ORGANIZATION','SELECTED','ACTIVE',
                        '{"metadata":"read","contents":"read"}',0,
                        now(),now(),now(),NULL,now(),now())
                """, connectionId, userId);
        UUID taskId = taskStore.enqueue(UUID.randomUUID(), userId, Instant.now());
        UUID staleClaim = UUID.randomUUID();
        jdbcTemplate.update("""
                UPDATE account_deletion_tasks
                SET status='RUNNING',attempt_count=1,claim_token=?,
                    lease_expires_at=now()-interval '1 second'
                WHERE id=?
                """, staleClaim, taskId);

        worker.processDue();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM github_app_connections WHERE id=?",
                        String.class,
                        connectionId))
                .isEqualTo("DISCONNECTING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM github_installation_revocation_outbox "
                                + "WHERE github_app_connection_id=?",
                        String.class,
                        connectionId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account_deletion_tasks WHERE id=?",
                        String.class,
                        taskId))
                .isEqualTo("RETRY_WAIT");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM account_deletion_tasks WHERE id=?",
                        Integer.class,
                        taskId))
                .isEqualTo(2);

        jdbcTemplate.update("""
                UPDATE github_installation_revocation_outbox
                SET status='SUCCEEDED',completed_at=now(),claim_token=NULL,lease_expires_at=NULL
                WHERE github_app_connection_id=?
                """, connectionId);
        makeTaskDue(taskId);
        worker.processDue();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id=?", Long.class, userId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account_deletion_tasks WHERE id=?",
                        String.class,
                        taskId))
                .isEqualTo("SUCCEEDED");
    }

    private UUID seedUser(String email, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                  id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                  withdrawn_at,created_at,updated_at)
                VALUES (?,?,'fixture-hash','Deletion User','USER',?,now(),now(),
                        CASE WHEN ?='WITHDRAWN' THEN now() ELSE NULL END,now(),now())
                """, id, email, status, status);
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                  id,user_id,legal_name,introduction,desired_roles,desired_industries,
                  desired_locations,expected_graduation_date,version,created_at,updated_at)
                VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), id);
        return id;
    }

    private UUID seedDocument(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO documents (
                  id,user_id,document_type,original_filename,display_name,storage_key,mime_type,
                  file_size_bytes,checksum_sha256,parse_status,evidence_extraction_status,
                  manual_text_provided,source_revision,version,uploaded_at,updated_at,deleted_at)
                VALUES (?,?,'RESUME','fixture.pdf','Fixture document',?,
                        'application/pdf',1,repeat('a',64),'UPLOADED','NOT_STARTED',
                        false,1,0,now(),now(),NULL)
                """, id, userId, "users/" + userId + "/documents/" + id + "/content");
        return id;
    }

    private void makeTaskDue(UUID taskId) {
        jdbcTemplate.update("""
                UPDATE account_deletion_tasks
                SET next_attempt_at=requested_at
                WHERE id=? AND status='RETRY_WAIT'
                """, taskId);
    }
}
