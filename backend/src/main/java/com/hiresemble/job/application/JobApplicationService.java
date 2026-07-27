package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.job.application.model.JobApplicationResults.JobCreationAccepted;
import com.hiresemble.job.application.model.JobApplicationResults.RunAccepted;
import com.hiresemble.job.domain.JobCommands.CreateJob;
import com.hiresemble.job.domain.JobCommands.JobListQuery;
import com.hiresemble.job.domain.JobCommands.UpdateJob;
import com.hiresemble.job.domain.JobPolicy;
import com.hiresemble.job.domain.JobRecords.JobPage;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.domain.JobUrlCanonicalizer;
import com.hiresemble.job.infrastructure.JobStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JobApplicationService {

    private static final Set<String> SORTS =
            Set.of("createdAt,desc", "deadlineAt,asc", "updatedAt,desc");
    private final JobStore store;
    private final JobCreationService creationService;
    private final JobMutationService mutationService;
    private final JobStatusService statusService;
    private final JobUrlCanonicalizer canonicalizer;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public JobApplicationService(
            JobStore store,
            JobCreationService creationService,
            JobMutationService mutationService,
            JobStatusService statusService,
            JobUrlCanonicalizer canonicalizer,
            IdempotencyService idempotency,
            Clock clock) {
        this.store = store;
        this.creationService = creationService;
        this.mutationService = mutationService;
        this.statusService = statusService;
        this.canonicalizer = canonicalizer;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentResponse<JobCreationAccepted> create(
            UUID userId,
            String sourceUrl,
            String companyName,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            String idempotencyKey) {
        String canonicalUrl = canonicalizer.canonicalize(sourceUrl);
        String normalizedCompany = JobPolicy.optionalName(companyName, 200);
        String normalizedPosition = JobPolicy.optionalName(positionName, 300);
        String normalizedDescription = JobPolicy.optionalText(descriptionText);
        String canonicalRequest = String.join(
                "|",
                canonicalUrl,
                nullSafe(normalizedCompany),
                nullSafe(normalizedPosition),
                nullSafe(deadlineAt),
                nullSafe(JobPolicy.contentHash(normalizedDescription)));
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/jobs",
                IdempotencyScope.ROOT_SCOPE_ID,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                canonicalRequest,
                JobCreationAccepted.class,
                () -> Boolean.TRUE,
                ignored -> {
                    UUID jobId = UUID.randomUUID();
                    var created = creationService.create(new CreateJob(
                            jobId,
                            userId,
                            sourceUrl.trim(),
                            canonicalUrl,
                            normalizedCompany,
                            normalizedPosition,
                            normalizedDescription,
                            deadlineAt));
                    JobRecord job = created.job();
                    JobCreationAccepted response = new JobCreationAccepted(
                            job.id(),
                            job.status(),
                            job.extractionStatus(),
                            created.run() == null ? null : created.run().agentRunId());
                    int status = created.run() == null ? 201 : 202;
                    return new OriginalResponse<>(
                            status,
                            response,
                            "JOB",
                            job.id(),
                            created.run() == null ? null : created.run().agentRunId());
                },
                ignored -> {});
    }

    public JobPage list(UUID userId, JobListQuery requested) {
        if (requested.page() < 0
                || requested.size() < 1
                || requested.size() > 100
                || !SORTS.contains(requested.sort())
                || (requested.deadlineWithinDays() != null
                        && (requested.deadlineFrom() != null || requested.deadlineTo() != null))
                || (requested.deadlineWithinDays() != null
                        && (requested.deadlineWithinDays() < 1
                                || requested.deadlineWithinDays() > 30))
                || (requested.deadlineFrom() != null
                        && requested.deadlineTo() != null
                        && requested.deadlineFrom().isAfter(requested.deadlineTo()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String query = requested.query() == null ? null : requested.query().trim();
        if (query != null
                && (query.isBlank()
                        || query.length() > 200
                        || query.chars().anyMatch(Character::isISOControl))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        JobListQuery normalized = new JobListQuery(
                requested.status(),
                requested.extractionStatus(),
                query,
                requested.deadlineFrom(),
                requested.deadlineTo(),
                requested.deadlineWithinDays(),
                requested.page(),
                requested.size(),
                requested.sort());
        Instant relativeFrom =
                requested.deadlineWithinDays() == null ? null : clock.instant();
        Instant relativeTo = relativeFrom == null
                ? null
                : relativeFrom.plus(requested.deadlineWithinDays(), ChronoUnit.DAYS);
        return store.list(userId, normalized, relativeFrom, relativeTo);
    }

    public JobRecord detail(UUID userId, UUID jobId) {
        return store.findActive(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public JobRecord update(UUID userId, UUID jobId, UpdateJob command) {
        return mutationService.update(userId, jobId, command);
    }

    public JobRecord changeStatus(
            UUID userId, UUID jobId, com.hiresemble.job.domain.JobStatus status, long version) {
        return statusService.change(userId, jobId, status, version);
    }

    public IdempotentResponse<RunAccepted> retryExtraction(
            UUID userId,
            UUID jobId,
            long version,
            String idempotencyKey) {
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/jobs/{jobId}/retry-extraction",
                jobId,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                Long.toString(version),
                RunAccepted.class,
                () -> Boolean.TRUE,
                ignored -> {
                    WorkflowLaunchResult result =
                            mutationService.retryExtraction(userId, jobId, version);
                    RunAccepted response = new RunAccepted(
                            result.agentRunId(),
                            result.status(),
                            result.resourceType(),
                            result.resourceId());
                    return new OriginalResponse<>(
                            202,
                            response,
                            result.resourceType(),
                            result.resourceId(),
                            result.agentRunId());
                },
                ignored -> {});
    }

    public void delete(UUID userId, UUID jobId, long version) {
        mutationService.delete(userId, jobId, version);
    }

    private String nullSafe(Object value) {
        return value == null ? "-" : value.toString();
    }
}
