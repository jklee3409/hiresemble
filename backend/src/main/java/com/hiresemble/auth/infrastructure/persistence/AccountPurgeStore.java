package com.hiresemble.auth.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AccountPurgeStore {

    private final JdbcClient jdbc;

    public AccountPurgeStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ActiveRun> cancellableRuns(UUID userId) {
        return jdbc.sql("""
                        SELECT id,state_version
                        FROM agent_runs
                        WHERE user_id=:userId
                          AND status IN ('QUEUED','RUNNING','WAITING_USER')
                          AND cancel_requested_at IS NULL
                        ORDER BY queued_at,id
                        """)
                .param("userId", userId)
                .query((rs, row) -> new ActiveRun(
                        rs.getObject("id", UUID.class), rs.getLong("state_version")))
                .list();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveRuns(UUID userId) {
        return count("""
                SELECT count(*) FROM agent_runs
                WHERE user_id=:userId AND status IN ('QUEUED','RUNNING','WAITING_USER')
                """, userId) > 0;
    }

    @Transactional(readOnly = true)
    public List<DocumentObject> documentObjectsNeedingDeletion(UUID userId) {
        return jdbc.sql("""
                        SELECT document.id,document.storage_key
                        FROM documents document
                        WHERE document.user_id=:userId
                          AND NOT EXISTS (
                              SELECT 1 FROM object_deletion_outbox deletion
                              WHERE deletion.user_id=document.user_id
                                AND deletion.storage_key=document.storage_key
                          )
                        """)
                .param("userId", userId)
                .query((rs, row) -> new DocumentObject(
                        rs.getObject("id", UUID.class), rs.getString("storage_key")))
                .list();
    }

    @Transactional(readOnly = true)
    public List<ArtifactObject> artifactObjectsNeedingDeletion(UUID userId) {
        return jdbc.sql("""
                        SELECT version.career_artifact_id,version.id,version.storage_key
                        FROM career_artifact_versions version
                        WHERE version.user_id=:userId
                          AND NOT EXISTS (
                              SELECT 1 FROM career_artifact_object_deletion_outbox deletion
                              WHERE deletion.user_id=version.user_id
                                AND deletion.storage_key=version.storage_key
                          )
                        """)
                .param("userId", userId)
                .query((rs, row) -> new ArtifactObject(
                        rs.getObject("career_artifact_id", UUID.class),
                        rs.getObject("id", UUID.class),
                        rs.getString("storage_key")))
                .list();
    }

    @Transactional(readOnly = true)
    public List<SnapshotObject> snapshotObjectsNeedingDeletion(UUID userId) {
        return jdbc.sql("""
                        SELECT snapshot.id,snapshot.snapshot_storage_key
                        FROM github_repository_snapshots snapshot
                        WHERE snapshot.user_id=:userId
                          AND NOT EXISTS (
                              SELECT 1 FROM github_snapshot_object_deletion_outbox deletion
                              WHERE deletion.user_id=snapshot.user_id
                                AND deletion.storage_key=snapshot.snapshot_storage_key
                          )
                        """)
                .param("userId", userId)
                .query((rs, row) -> new SnapshotObject(
                        rs.getObject("id", UUID.class),
                        rs.getString("snapshot_storage_key")))
                .list();
    }

    @Transactional(readOnly = true)
    public TerminalState terminalState(UUID userId) {
        boolean dead = count("""
                        SELECT (
                            (SELECT count(*) FROM object_deletion_outbox
                             WHERE user_id=:userId AND status='DEAD')
                          + (SELECT count(*) FROM github_snapshot_object_deletion_outbox
                             WHERE user_id=:userId AND status='DEAD')
                          + (SELECT count(*) FROM career_artifact_object_deletion_outbox
                             WHERE user_id=:userId AND status='DEAD')
                          + (SELECT count(*) FROM github_installation_revocation_outbox
                             WHERE user_id=:userId AND status='DEAD')
                        )
                        """, userId) > 0;
        if (dead) return TerminalState.DEAD;
        long pending = count("""
                        SELECT (
                            (SELECT count(*) FROM documents document
                             WHERE document.user_id=:userId AND NOT EXISTS (
                                 SELECT 1 FROM object_deletion_outbox deletion
                                 WHERE deletion.user_id=document.user_id
                                   AND deletion.storage_key=document.storage_key
                                   AND deletion.status='SUCCEEDED'))
                          + (SELECT count(*) FROM github_repository_snapshots snapshot
                             WHERE snapshot.user_id=:userId AND NOT EXISTS (
                                 SELECT 1 FROM github_snapshot_object_deletion_outbox deletion
                                 WHERE deletion.user_id=snapshot.user_id
                                   AND deletion.storage_key=snapshot.snapshot_storage_key
                                   AND deletion.status='SUCCEEDED'))
                          + (SELECT count(*) FROM career_artifact_versions version
                             WHERE version.user_id=:userId AND NOT EXISTS (
                                 SELECT 1 FROM career_artifact_object_deletion_outbox deletion
                                 WHERE deletion.user_id=version.user_id
                                   AND deletion.storage_key=version.storage_key
                                   AND deletion.status='SUCCEEDED'))
                          + (SELECT count(*) FROM github_app_connections connection
                             WHERE connection.user_id=:userId
                               AND connection.status<>'DISCONNECTED'
                               AND NOT EXISTS (
                                   SELECT 1 FROM github_installation_revocation_outbox revocation
                                   WHERE revocation.user_id=connection.user_id
                                     AND revocation.github_app_connection_id=connection.id
                                     AND revocation.status='SUCCEEDED'))
                        )
                        """, userId);
        return pending == 0 ? TerminalState.READY : TerminalState.PENDING;
    }

    @Transactional
    public boolean finalizePurge(
            UUID taskId, UUID userId, UUID claimToken, int attemptCount, Instant now) {
        UUID lockedTask = jdbc.sql("""
                        SELECT id FROM account_deletion_tasks
                        WHERE id=:taskId AND subject_user_id=:userId
                          AND status='RUNNING' AND claim_token=:claimToken
                          AND attempt_count=:attemptCount
                        FOR UPDATE
                        """)
                .param("taskId", taskId)
                .param("userId", userId)
                .param("claimToken", claimToken)
                .param("attemptCount", attemptCount)
                .query(UUID.class)
                .optional()
                .orElse(null);
        if (lockedTask == null
                || hasActiveRuns(userId)
                || terminalState(userId) != TerminalState.READY) {
            return false;
        }

        jdbc.sql("""
                        DELETE FROM github_evidence_unit_links link
                        USING profile_evidence evidence
                        WHERE link.user_id=evidence.user_id
                          AND link.profile_evidence_id=evidence.id
                          AND evidence.user_id=:userId
                          AND evidence.source_type='GITHUB_REPOSITORY'
                        """)
                .param("userId", userId)
                .update();
        jdbc.sql("""
                        UPDATE profile_evidence
                        SET source_entity_id=NULL,document_id=NULL,title='[SOURCE DELETED]',
                            content='[SOURCE DELETED]',metadata='{}'::jsonb,confidence=NULL,
                            verification_status='SOURCE_DELETED',verified_at=NULL,
                            source_deleted_at=COALESCE(source_deleted_at,:now),
                            github_source_id=NULL,github_repository_id=NULL,
                            github_snapshot_id=NULL,github_claim_key=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND source_type='GITHUB_REPOSITORY'
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM github_source_units WHERE user_id=:userId")
                .param("userId", userId)
                .update();
        jdbc.sql("""
                        UPDATE github_snapshot_object_deletion_outbox
                        SET snapshot_id=NULL
                        WHERE user_id=:userId AND status='SUCCEEDED'
                        """)
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM github_repository_snapshots WHERE user_id=:userId")
                .param("userId", userId)
                .update();

        jdbc.sql("DELETE FROM spring_session WHERE principal_name=:principalName")
                .param("principalName", userId.toString())
                .update();
        jdbc.sql("DELETE FROM github_installation_revocation_outbox WHERE user_id=:userId")
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM career_artifact_object_deletion_outbox WHERE user_id=:userId")
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM github_snapshot_object_deletion_outbox WHERE user_id=:userId")
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM object_deletion_outbox WHERE user_id=:userId")
                .param("userId", userId)
                .update();
        jdbc.sql("DELETE FROM users WHERE id=:userId AND status='WITHDRAWN'")
                .param("userId", userId)
                .update();
        return jdbc.sql("""
                        UPDATE account_deletion_tasks
                        SET status='SUCCEEDED',subject_user_id=NULL,claim_token=NULL,
                            lease_expires_at=NULL,last_error_code=NULL,completed_at=:now
                        WHERE id=:taskId AND status='RUNNING' AND claim_token=:claimToken
                          AND attempt_count=:attemptCount
                        """)
                .param("now", utc(now))
                .param("taskId", taskId)
                .param("claimToken", claimToken)
                .param("attemptCount", attemptCount)
                .update() == 1;
    }

    private long count(String sql, UUID userId) {
        return jdbc.sql(sql).param("userId", userId).query(Long.class).single();
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public enum TerminalState {
        READY,
        PENDING,
        DEAD
    }

    public record ActiveRun(UUID id, long stateVersion) {}

    public record DocumentObject(UUID documentId, String storageKey) {}

    public record ArtifactObject(UUID artifactId, UUID versionId, String storageKey) {}

    public record SnapshotObject(UUID snapshotId, String storageKey) {}
}
