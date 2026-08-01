package com.hiresemble.job.infrastructure;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.job.application.model.JobAutoAnalysisModels.AutoAnalysisRequest;
import com.hiresemble.job.domain.JobAutoAnalysisStatus;
import com.hiresemble.job.domain.JobRecords.JobRecord;
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
public class JobAutoAnalysisStore {

    private final JdbcClient jdbc;

    public JobAutoAnalysisStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public AutoAnalysisRequest enqueue(JobRecord job, Instant now) {
        UUID requestId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO job_auto_analysis_requests (
                            id,user_id,job_posting_id,job_version,job_content_hash,
                            quality_mode,status,attempt_count,claim_token,lease_expires_at,
                            next_attempt_at,agent_run_id,error_code,error_message_safe,
                            created_at,updated_at,completed_at
                        ) VALUES (
                            :id,:userId,:jobId,:jobVersion,:contentHash,
                            'BALANCED','PENDING',0,NULL,NULL,
                            :now,NULL,NULL,NULL,:now,:now,NULL
                        )
                        ON CONFLICT (user_id, job_posting_id, job_version) DO NOTHING
                        """)
                .param("id", requestId)
                .param("userId", job.userId())
                .param("jobId", job.id())
                .param("jobVersion", job.version())
                .param("contentHash", job.contentHash())
                .param("now", utc(now))
                .update();
        return findForRevision(job.userId(), job.id(), job.version()).orElseThrow();
    }

    public Optional<AutoAnalysisRequest> find(UUID requestId) {
        return jdbc.sql("SELECT * FROM job_auto_analysis_requests WHERE id=:id")
                .param("id", requestId)
                .query(this::request)
                .optional();
    }

    public Optional<AutoAnalysisRequest> findForRevision(
            UUID userId, UUID jobId, long jobVersion) {
        return jdbc.sql("""
                        SELECT * FROM job_auto_analysis_requests
                        WHERE user_id=:userId AND job_posting_id=:jobId
                          AND job_version=:jobVersion
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .param("jobVersion", jobVersion)
                .query(this::request)
                .optional();
    }

    @Transactional
    public Optional<AutoAnalysisRequest> claim(
            UUID requestId, Instant now, Duration leaseDuration) {
        UUID claimToken = UUID.randomUUID();
        Optional<UUID> claimed = jdbc.sql("""
                        UPDATE job_auto_analysis_requests
                        SET status='CLAIMED', claim_token=:claimToken,
                            lease_expires_at=:leaseExpiresAt,
                            attempt_count=attempt_count+1, updated_at=:now
                        WHERE id=:id
                          AND (
                            (status='PENDING' AND next_attempt_at<=:now)
                            OR (status='CLAIMED' AND lease_expires_at<=:now)
                          )
                        RETURNING id
                        """)
                .param("claimToken", claimToken)
                .param("leaseExpiresAt", utc(now.plus(leaseDuration)))
                .param("now", utc(now))
                .param("id", requestId)
                .query(UUID.class)
                .optional();
        return claimed.flatMap(this::find);
    }

    @Transactional
    public Optional<AutoAnalysisRequest> claimNext(Instant now, Duration leaseDuration) {
        UUID claimToken = UUID.randomUUID();
        Optional<UUID> claimed = jdbc.sql("""
                        UPDATE job_auto_analysis_requests
                        SET status='CLAIMED', claim_token=:claimToken,
                            lease_expires_at=:leaseExpiresAt,
                            attempt_count=attempt_count+1, updated_at=:now
                        WHERE id = (
                            SELECT id FROM job_auto_analysis_requests
                            WHERE (status='PENDING' AND next_attempt_at<=:now)
                               OR (status='CLAIMED' AND lease_expires_at<=:now)
                            ORDER BY next_attempt_at, created_at, id
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        RETURNING id
                        """)
                .param("claimToken", claimToken)
                .param("leaseExpiresAt", utc(now.plus(leaseDuration)))
                .param("now", utc(now))
                .query(UUID.class)
                .optional();
        return claimed.flatMap(this::find);
    }

    @Transactional
    public boolean markLaunched(
            UUID requestId, UUID claimToken, UUID agentRunId, Instant now) {
        return jdbc.sql("""
                        UPDATE job_auto_analysis_requests
                        SET status='LAUNCHED', claim_token=NULL, lease_expires_at=NULL,
                            agent_run_id=:agentRunId, completed_at=:now, updated_at=:now
                        WHERE id=:id AND status='CLAIMED' AND claim_token=:claimToken
                        """)
                .param("agentRunId", agentRunId)
                .param("now", utc(now))
                .param("id", requestId)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    @Transactional
    public boolean markBlocked(
            UUID requestId, UUID claimToken, SafeError error, Instant now) {
        return jdbc.sql("""
                        UPDATE job_auto_analysis_requests
                        SET status='BLOCKED', claim_token=NULL, lease_expires_at=NULL,
                            error_code=:errorCode, error_message_safe=:errorMessage,
                            completed_at=:now, updated_at=:now
                        WHERE id=:id AND status='CLAIMED' AND claim_token=:claimToken
                        """)
                .param("errorCode", error.code())
                .param("errorMessage", error.message())
                .param("now", utc(now))
                .param("id", requestId)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    @Transactional
    public boolean markSuperseded(UUID requestId, UUID claimToken, Instant now) {
        return jdbc.sql("""
                        UPDATE job_auto_analysis_requests
                        SET status='SUPERSEDED', claim_token=NULL, lease_expires_at=NULL,
                            completed_at=:now, updated_at=:now
                        WHERE id=:id AND status='CLAIMED' AND claim_token=:claimToken
                        """)
                .param("now", utc(now))
                .param("id", requestId)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    @Transactional
    public boolean releaseForRetry(
            UUID requestId, UUID claimToken, Instant nextAttemptAt, Instant now) {
        return jdbc.sql("""
                        UPDATE job_auto_analysis_requests
                        SET status='PENDING', claim_token=NULL, lease_expires_at=NULL,
                            next_attempt_at=:nextAttemptAt, updated_at=:now
                        WHERE id=:id AND status='CLAIMED' AND claim_token=:claimToken
                        """)
                .param("nextAttemptAt", utc(nextAttemptAt))
                .param("now", utc(now))
                .param("id", requestId)
                .param("claimToken", claimToken)
                .update() == 1;
    }

    private AutoAnalysisRequest request(ResultSet resultSet, int rowNumber) throws SQLException {
        String errorCode = resultSet.getString("error_code");
        return new AutoAnalysisRequest(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("job_posting_id", UUID.class),
                resultSet.getLong("job_version"),
                resultSet.getString("job_content_hash"),
                AiQualityMode.valueOf(resultSet.getString("quality_mode")),
                JobAutoAnalysisStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("claim_token", UUID.class),
                instant(resultSet, "lease_expires_at"),
                instant(resultSet, "next_attempt_at"),
                resultSet.getObject("agent_run_id", UUID.class),
                errorCode == null
                        ? null
                        : new SafeError(errorCode, resultSet.getString("error_message_safe")),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                instant(resultSet, "completed_at"));
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
