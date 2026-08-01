package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunResumePort;
import com.hiresemble.agentrun.application.service.AgentRunRetryTransaction;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.domain.JobCommands.UpdateJob;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobPolicy;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobMutationService {

    private final JobStore store;
    private final AgentRunQueryPort runQuery;
    private final AgentRunResumePort resumePort;
    private final AgentRunCancellationPort cancellationPort;
    private final AgentRunRetryTransaction retryTransaction;
    private final JobAutoAnalysisRequestService autoAnalysis;
    private final Clock clock;

    public JobMutationService(
            JobStore store,
            AgentRunQueryPort runQuery,
            AgentRunResumePort resumePort,
            AgentRunCancellationPort cancellationPort,
            AgentRunRetryTransaction retryTransaction,
            JobAutoAnalysisRequestService autoAnalysis,
            Clock clock) {
        this.store = store;
        this.runQuery = runQuery;
        this.resumePort = resumePort;
        this.cancellationPort = cancellationPort;
        this.retryTransaction = retryTransaction;
        this.autoAnalysis = autoAnalysis;
        this.clock = clock;
    }

    @Transactional
    public JobRecord update(UUID userId, UUID jobId, UpdateJob command) {
        JobRecord current = lockActive(userId, jobId);
        requireVersion(current, command.version());
        String companyName = JobPolicy.optionalName(command.companyName(), 200);
        String title = JobPolicy.optionalName(command.title(), 300);
        String positionName = JobPolicy.optionalName(command.positionName(), 300);
        String description = JobPolicy.optionalText(command.descriptionText());
        AgentRunSnapshot latest = latestRun(current);
        boolean waitingForJobText = latest != null
                && latest.status() == AgentRunStatus.WAITING_USER
                && latest.requiredUserAction() != null
                && latest.requiredUserAction().type() == RequiredUserActionType.PROVIDE_JOB_TEXT;
        JobExtractionStatus extractionStatus = description == null
                ? JobExtractionStatus.NEEDS_MANUAL_INPUT
                : JobExtractionStatus.MANUAL_INPUT_PROVIDED;
        JobRecord updated = store.updateUserFields(
                        userId,
                        jobId,
                        command.version(),
                        companyName,
                        title,
                        positionName,
                        description,
                        command.deadlineAt(),
                        extractionStatus,
                        clock.instant())
                .orElseThrow(this::versionConflict);
        if (description != null && waitingForJobText) {
            resumePort.resume(userId, latest.id(), latest.stateVersion(), clock.instant());
        } else if (description != null && latest != null && latest.cancellable()) {
            cancellationPort.requestCancellation(
                    userId, latest.id(), latest.stateVersion(), clock.instant());
        }
        JobRecord currentResult = store.findActive(userId, jobId).orElse(updated);
        if (description != null && !waitingForJobText) {
            autoAnalysis.enqueue(currentResult);
        }
        return currentResult;
    }

    @Transactional
    public WorkflowLaunchResult retryExtraction(
            UUID userId, UUID jobId, long expectedVersion) {
        JobRecord current = lockActive(userId, jobId);
        requireVersion(current, expectedVersion);
        AgentRunSnapshot latest = latestRun(current);
        if (latest == null
                || latest.workflowType() != WorkflowType.JOB_POSTING_EXTRACTION
                || !"JOB".equals(latest.resourceType())
                || !jobId.equals(latest.resourceId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        if (latest.status() == AgentRunStatus.WAITING_USER) {
            if (!JobPolicy.hasUsableText(current.descriptionText())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            store.queueExistingRun(userId, jobId, latest.id(), expectedVersion, now)
                    .orElseThrow(this::versionConflict);
            AgentRunSnapshot resumed =
                    resumePort.resume(userId, latest.id(), latest.stateVersion(), now);
            return new WorkflowLaunchResult(
                    resumed.id(),
                    resumed.status(),
                    resumed.resourceType(),
                    resumed.resourceId(),
                    false);
        }
        if ((latest.status() == AgentRunStatus.QUEUED
                        || latest.status() == AgentRunStatus.RUNNING)
                && latest.retryOfRunId() != null) {
            AgentRunSnapshot predecessor = runQuery.findByOwner(userId, latest.retryOfRunId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED));
            long snapshottedJobVersion = latest.inputReferenceSnapshot() == null
                    ? -1L : latest.inputReferenceSnapshot().path("jobVersion").asLong(-1L);
            if (predecessor.workflowType() == WorkflowType.JOB_POSTING_EXTRACTION
                    && predecessor.retryable()
                    && latest.requestedQualityMode() == predecessor.requestedQualityMode()
                    && current.version() == snapshottedJobVersion + 1L) {
                return new WorkflowLaunchResult(
                        latest.id(), latest.status(), latest.resourceType(), latest.resourceId(), false);
            }
            throw new BusinessException(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED);
        }
        if (latest.status() != AgentRunStatus.FAILED
                && latest.status() != AgentRunStatus.INTERRUPTED) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return retryTransaction.retry(userId, latest.id());
    }

    @Transactional
    public void delete(UUID userId, UUID jobId, long expectedVersion) {
        JobRecord current = lockActive(userId, jobId);
        requireVersion(current, expectedVersion);
        AgentRunSnapshot latest = latestRun(current);
        Instant now = clock.instant();
        if (!store.softDelete(userId, jobId, expectedVersion, now)) {
            throw versionConflict();
        }
        if (latest != null && latest.cancellable()) {
            cancellationPort.requestCancellation(
                    userId, latest.id(), latest.stateVersion(), now);
        }
    }

    private AgentRunSnapshot latestRun(JobRecord job) {
        return job.latestAgentRunId() == null
                ? null
                : runQuery.findByOwner(job.userId(), job.latestAgentRunId()).orElse(null);
    }

    private JobRecord lockActive(UUID userId, UUID jobId) {
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
