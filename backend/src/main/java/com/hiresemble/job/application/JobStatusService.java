package com.hiresemble.job.application;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.domain.ClosedReason;
import com.hiresemble.job.domain.JobHistoryActor;
import com.hiresemble.job.domain.JobPolicy;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.domain.JobRecords.StatusChange;
import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.job.infrastructure.JobStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobStatusService {

    private final JobStore store;
    private final Clock clock;

    public JobStatusService(JobStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public JobRecord change(
            UUID userId, UUID jobId, JobStatus target, long expectedVersion) {
        JobRecord current = activeForUpdate(userId, jobId);
        requireVersion(current, expectedVersion);
        Instant now = clock.instant();
        StatusChange change = JobPolicy.transition(
                current.status(),
                target,
                current.submittedAt(),
                now,
                target == JobStatus.CLOSED ? ClosedReason.USER_CLOSED : null);
        return store.applyStatus(
                        userId,
                        jobId,
                        expectedVersion,
                        change,
                        target == JobStatus.CLOSED ? "USER_CLOSED" : "USER_STATUS_CHANGE",
                        JobHistoryActor.USER,
                        now)
                .orElseThrow(this::versionConflict);
    }

    @Transactional
    public boolean closeExpired(UUID jobId, Instant cutoff) {
        Optional<JobRecord> candidate = store.lockDue(jobId, cutoff);
        if (candidate.isEmpty()) {
            return false;
        }
        JobRecord current = candidate.get();
        StatusChange change = JobPolicy.transition(
                current.status(),
                JobStatus.CLOSED,
                current.submittedAt(),
                cutoff,
                ClosedReason.DEADLINE_PASSED);
        return store.applyStatus(
                        current.userId(),
                        current.id(),
                        current.version(),
                        change,
                        "DEADLINE_PASSED",
                        JobHistoryActor.SCHEDULER,
                        cutoff)
                .isPresent();
    }

    private JobRecord activeForUpdate(UUID userId, UUID jobId) {
        return store.lockActive(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireVersion(JobRecord job, long expectedVersion) {
        if (expectedVersion < 0 || job.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", "version", "reason", "STALE"),
                null);
    }
}
