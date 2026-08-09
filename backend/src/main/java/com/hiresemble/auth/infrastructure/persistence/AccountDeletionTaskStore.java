package com.hiresemble.auth.infrastructure.persistence;

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
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AccountDeletionTaskStore {

    public static final String POLICY_VERSION = "terminal-purge-v1";

    private final JdbcClient jdbc;

    public AccountDeletionTaskStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID enqueue(UUID requestId, UUID userId, Instant requestedAt) {
        return jdbc.sql("""
                        INSERT INTO account_deletion_tasks (
                            id,subject_user_id,status,policy_version,attempt_count,next_attempt_at,
                            claim_token,lease_expires_at,purge_by,last_error_code,requested_at,completed_at
                        ) VALUES (
                            :id,:userId,'QUEUED',:policyVersion,0,:requestedAt,
                            NULL,NULL,:purgeBy,NULL,:requestedAt,NULL
                        )
                        ON CONFLICT (subject_user_id)
                            WHERE subject_user_id IS NOT NULL AND status<>'SUCCEEDED'
                        DO UPDATE SET next_attempt_at=LEAST(
                            account_deletion_tasks.next_attempt_at,EXCLUDED.next_attempt_at)
                        RETURNING id
                        """)
                .param("id", requestId)
                .param("userId", userId)
                .param("policyVersion", POLICY_VERSION)
                .param("requestedAt", utc(requestedAt))
                .param("purgeBy", utc(requestedAt.plus(Duration.ofHours(24))))
                .query(UUID.class)
                .single();
    }

    @Transactional
    public Optional<ClaimedTask> claimDue(Instant now, Duration lease) {
        jdbc.sql("""
                        UPDATE account_deletion_tasks
                        SET status='RETRY_WAIT',claim_token=NULL,lease_expires_at=NULL,
                            next_attempt_at=:now,last_error_code='ACCOUNT_DELETION_LEASE_EXPIRED'
                        WHERE status='RUNNING' AND lease_expires_at<=:now
                        """)
                .param("now", utc(now))
                .update();
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                        WITH candidate AS (
                            SELECT id FROM account_deletion_tasks
                            WHERE status IN ('QUEUED','RETRY_WAIT') AND next_attempt_at<=:now
                            ORDER BY next_attempt_at,id
                            FOR UPDATE SKIP LOCKED LIMIT 1
                        )
                        UPDATE account_deletion_tasks target
                        SET status='RUNNING',attempt_count=attempt_count+1,
                            claim_token=:token,lease_expires_at=:leaseExpiresAt
                        FROM candidate
                        WHERE target.id=candidate.id
                        RETURNING target.*
                        """)
                .param("now", utc(now))
                .param("token", token)
                .param("leaseExpiresAt", utc(now.plus(lease)))
                .query(this::task)
                .optional();
    }

    @Transactional
    public boolean retry(
            UUID id,
            UUID claimToken,
            int attemptCount,
            String safeErrorCode,
            Instant nextAttemptAt) {
        return jdbc.sql("""
                        UPDATE account_deletion_tasks
                        SET status='RETRY_WAIT',claim_token=NULL,lease_expires_at=NULL,
                            next_attempt_at=:nextAttemptAt,last_error_code=:errorCode
                        WHERE id=:id AND status='RUNNING' AND claim_token=:claimToken
                          AND attempt_count=:attemptCount
                        """)
                .param("nextAttemptAt", utc(nextAttemptAt))
                .param("errorCode", safeErrorCode)
                .param("id", id)
                .param("claimToken", claimToken)
                .param("attemptCount", attemptCount)
                .update() == 1;
    }

    @Transactional
    public boolean dead(
            UUID id, UUID claimToken, int attemptCount, String safeErrorCode, Instant now) {
        return jdbc.sql("""
                        UPDATE account_deletion_tasks
                        SET status='DEAD',claim_token=NULL,lease_expires_at=NULL,
                            last_error_code=:errorCode,completed_at=:now
                        WHERE id=:id AND status='RUNNING' AND claim_token=:claimToken
                          AND attempt_count=:attemptCount
                        """)
                .param("errorCode", safeErrorCode)
                .param("now", utc(now))
                .param("id", id)
                .param("claimToken", claimToken)
                .param("attemptCount", attemptCount)
                .update() == 1;
    }

    @Transactional
    public void cleanupCompletedBefore(Instant cutoff) {
        jdbc.sql("""
                        DELETE FROM account_deletion_tasks
                        WHERE status='SUCCEEDED' AND completed_at<:cutoff
                        """)
                .param("cutoff", utc(cutoff))
                .update();
    }

    private ClaimedTask task(ResultSet rs, int row) throws SQLException {
        return new ClaimedTask(
                rs.getObject("id", UUID.class),
                rs.getObject("subject_user_id", UUID.class),
                rs.getInt("attempt_count"),
                rs.getObject("claim_token", UUID.class),
                instant(rs, "purge_by"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record ClaimedTask(
            UUID id, UUID userId, int attemptCount, UUID claimToken, Instant purgeBy) {}
}
