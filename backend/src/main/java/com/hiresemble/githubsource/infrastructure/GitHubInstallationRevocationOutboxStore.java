package com.hiresemble.githubsource.infrastructure;

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
public class GitHubInstallationRevocationOutboxStore {

    private final JdbcClient jdbc;

    public GitHubInstallationRevocationOutboxStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<ClaimedRevocation> claimDue(Instant now, Duration lease) {
        jdbc.sql("""
                        UPDATE github_installation_revocation_outbox
                        SET status='RETRY_WAIT',claim_token=NULL,lease_expires_at=NULL,
                            next_attempt_at=:now
                        WHERE status='RUNNING' AND lease_expires_at<=:now
                        """)
                .param("now", utc(now))
                .update();
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                        WITH candidate AS (
                            SELECT id FROM github_installation_revocation_outbox
                            WHERE status IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=:now
                            ORDER BY next_attempt_at,id
                            FOR UPDATE SKIP LOCKED LIMIT 1
                        )
                        UPDATE github_installation_revocation_outbox target
                        SET status='RUNNING',attempt_count=attempt_count+1,
                            claim_token=:token,lease_expires_at=:leaseExpiresAt
                        FROM candidate
                        WHERE target.id=candidate.id
                        RETURNING target.*
                        """)
                .param("now", utc(now))
                .param("token", token)
                .param("leaseExpiresAt", utc(now.plus(lease)))
                .query(this::claim)
                .optional();
    }

    @Transactional
    public boolean markSucceeded(UUID id, UUID claimToken, Instant now) {
        return jdbc.sql("""
                        UPDATE github_installation_revocation_outbox
                        SET status='SUCCEEDED',claim_token=NULL,lease_expires_at=NULL,
                            last_error_code=NULL,completed_at=:now
                        WHERE id=:id AND status='RUNNING' AND claim_token=:claimToken
                        """)
                .param("now", utc(now))
                .param("id", id)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    @Transactional
    public boolean markFailed(
            UUID id,
            UUID claimToken,
            int attemptCount,
            Instant nextAttemptAt,
            boolean dead,
            Instant now) {
        return jdbc.sql("""
                        UPDATE github_installation_revocation_outbox
                        SET status=:status,claim_token=NULL,lease_expires_at=NULL,
                            next_attempt_at=:nextAttemptAt,
                            last_error_code='GITHUB_INSTALLATION_UNINSTALL_FAILED',
                            completed_at=:completedAt
                        WHERE id=:id AND status='RUNNING' AND claim_token=:claimToken
                          AND attempt_count=:attemptCount
                        """)
                .param("status", dead ? "DEAD" : "RETRY_WAIT")
                .param("nextAttemptAt", utc(nextAttemptAt))
                .param("completedAt", dead ? utc(now) : null)
                .param("id", id)
                .param("claimToken", claimToken)
                .param("attemptCount", attemptCount)
                .update() == 1;
    }

    private ClaimedRevocation claim(ResultSet rs, int row) throws SQLException {
        return new ClaimedRevocation(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("github_app_connection_id", UUID.class),
                rs.getLong("github_installation_id"),
                rs.getInt("attempt_count"),
                rs.getObject("claim_token", UUID.class));
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record ClaimedRevocation(
            UUID id,
            UUID userId,
            UUID connectionId,
            long installationId,
            int attemptCount,
            UUID claimToken) {}
}
