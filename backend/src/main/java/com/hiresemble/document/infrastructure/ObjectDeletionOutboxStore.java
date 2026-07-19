package com.hiresemble.document.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ObjectDeletionOutboxStore {

    private final JdbcClient jdbc;

    public ObjectDeletionOutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID enqueueDocument(
            UUID userId, UUID documentId, String storageKey, Instant now) {
        return enqueue(userId, documentId, storageKey, "DOCUMENT_DELETE", now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID enqueueOrphan(UUID userId, String storageKey, Instant now) {
        return enqueue(userId, null, storageKey, "ORPHAN_UPLOAD_COMPENSATION", now);
    }

    private UUID enqueue(
            UUID userId, UUID documentId, String storageKey, String reason, Instant now) {
        UUID id = UUID.randomUUID();
        return jdbc.sql("""
                        INSERT INTO object_deletion_outbox (
                            id,user_id,document_id,storage_key,reason,status,attempt_count,
                            next_attempt_at,claim_token,lease_expires_at,last_error_code,
                            created_at,completed_at
                        ) VALUES (
                            :id,:userId,:documentId,:storageKey,:reason,'PENDING',0,
                            :now,NULL,NULL,NULL,:now,NULL
                        )
                        ON CONFLICT (
                            COALESCE(document_id, '00000000-0000-0000-0000-000000000000'::uuid),
                            storage_key, reason
                        ) WHERE status IN ('PENDING','PROCESSING')
                        DO UPDATE SET next_attempt_at=LEAST(object_deletion_outbox.next_attempt_at,EXCLUDED.next_attempt_at)
                        RETURNING id
                        """)
                .param("id", id).param("userId", userId).param("documentId", documentId)
                .param("storageKey", storageKey).param("reason", reason).param("now", utc(now))
                .query(UUID.class).single();
    }

    @Transactional
    public Optional<ClaimedDeletion> claimDue(Instant now, Duration lease) {
        jdbc.sql("""
                        UPDATE object_deletion_outbox SET status='PENDING',claim_token=NULL,
                            lease_expires_at=NULL,next_attempt_at=:now
                        WHERE status='PROCESSING' AND lease_expires_at<=:now
                        """)
                .param("now", utc(now)).update();
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                        WITH candidate AS (
                            SELECT id FROM object_deletion_outbox
                            WHERE status='PENDING' AND next_attempt_at<=:now
                            ORDER BY next_attempt_at,id
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        UPDATE object_deletion_outbox target SET
                            status='PROCESSING',attempt_count=attempt_count+1,
                            claim_token=:token,lease_expires_at=:leaseExpiresAt
                        FROM candidate
                        WHERE target.id=candidate.id
                        RETURNING target.*
                        """)
                .param("now", utc(now)).param("token", token)
                .param("leaseExpiresAt", utc(now.plus(lease)))
                .query(this::claim).optional();
    }

    @Transactional
    public boolean markSucceeded(UUID id, UUID token, Instant now) {
        return jdbc.sql("""
                        UPDATE object_deletion_outbox SET status='SUCCEEDED',claim_token=NULL,
                            lease_expires_at=NULL,last_error_code=NULL,completed_at=:now
                        WHERE id=:id AND status='PROCESSING' AND claim_token=:token
                        """)
                .param("now", utc(now)).param("id", id).param("token", token).update() == 1;
    }

    @Transactional
    public boolean markFailed(
            UUID id, UUID token, int attempt, Instant nextAttempt, boolean dead, Instant now) {
        return jdbc.sql("""
                        UPDATE object_deletion_outbox SET status=:status,claim_token=NULL,
                            lease_expires_at=NULL,last_error_code='OBJECT_STORAGE_DELETE_FAILED',
                            next_attempt_at=:nextAttempt,completed_at=:completedAt
                        WHERE id=:id AND status='PROCESSING' AND claim_token=:token
                          AND attempt_count=:attempt
                        """)
                .param("status", dead ? "DEAD" : "PENDING")
                .param("nextAttempt", utc(nextAttempt))
                .param("completedAt", dead ? utc(now) : null)
                .param("id", id).param("token", token).param("attempt", attempt).update() == 1;
    }

    private ClaimedDeletion claim(ResultSet rs, int row) throws SQLException {
        return new ClaimedDeletion(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("storage_key"), rs.getInt("attempt_count"),
                rs.getObject("claim_token", UUID.class));
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record ClaimedDeletion(
            UUID id, UUID userId, String storageKey, int attemptCount, UUID claimToken) {}
}
