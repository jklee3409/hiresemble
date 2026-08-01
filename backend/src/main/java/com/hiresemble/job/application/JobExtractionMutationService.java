package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.port.AgentRunResourceOwnerResolver;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.application.port.JobWorkflowCommandPort;
import com.hiresemble.job.application.port.JobWorkflowQueryPort;
import com.hiresemble.job.domain.JobCommands.ExtractedFields;
import com.hiresemble.job.domain.JobPolicy;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.domain.JobRecords.UserOverrides;
import com.hiresemble.job.domain.JobRecords.WorkflowSnapshot;
import com.hiresemble.job.infrastructure.JobStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExtractionMutationService
        implements JobWorkflowQueryPort,
                JobWorkflowCommandPort,
                AgentRunResourceOwnerResolver,
                ResourceCompensationPort {

    public static final String RESOURCE_TYPE = "JOB";
    private final JobStore store;
    private final JobAutoAnalysisRequestService autoAnalysis;
    private final Clock clock;

    public JobExtractionMutationService(
            JobStore store, JobAutoAnalysisRequestService autoAnalysis, Clock clock) {
        this.store = store;
        this.autoAnalysis = autoAnalysis;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowSnapshot snapshot(UUID userId, UUID jobId) {
        JobRecord job = active(userId, jobId);
        return new WorkflowSnapshot(
                job.id(),
                job.userId(),
                job.latestAgentRunId(),
                job.version(),
                job.sourceUrl(),
                job.canonicalUrl(),
                job.extractionStatus(),
                job.contentHash(),
                new UserOverrides(
                        job.companyUserOverride() ? job.companyName() : null,
                        job.titleUserOverride() ? job.title() : null,
                        job.positionUserOverride() ? job.positionName() : null,
                        job.descriptionSource()
                                        == com.hiresemble.job.domain.JobDescriptionSource.USER_ENTERED
                                ? job.descriptionText()
                                : null,
                        job.deadlineUserOverride() ? job.deadlineAt() : null));
    }

    @Override
    @Transactional
    public JobRecord markExtracting(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion) {
        return store.markExtracting(
                        userId, jobId, agentRunId, expectedJobVersion, clock.instant())
                .orElseThrow(this::staleWorkflow);
    }

    @Override
    @Transactional
    public JobRecord applyExtraction(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion,
            ExtractedFields fields) {
        if (fields == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String description = JobPolicy.optionalText(fields.descriptionText());
        if (!JobPolicy.hasUsableText(description)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        BigDecimal confidence = fields.deadlineConfidence();
        if (fields.deadlineAt() != null
                && (confidence == null
                        || confidence.compareTo(BigDecimal.ZERO) < 0
                        || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (fields.deadlineAt() == null) {
            confidence = null;
        }
        JobRecord extracted = store.applyExtraction(
                        userId,
                        jobId,
                        agentRunId,
                        expectedJobVersion,
                        JobPolicy.optionalName(fields.companyName(), 200),
                        JobPolicy.optionalName(fields.title(), 300),
                        JobPolicy.optionalName(fields.positionName(), 300),
                        description,
                        fields.deadlineAt(),
                        confidence,
                        optionalScalar(fields.roleCategory(), 100),
                        optionalScalar(fields.employmentType(), 100),
                        optionalScalar(fields.location(), 200),
                        clock.instant())
                .orElseThrow(this::staleWorkflow);
        autoAnalysis.enqueue(extracted);
        return extracted;
    }

    @Override
    @Transactional
    public JobRecord markNeedsManualInput(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion) {
        return store.markNeedsManual(
                        userId, jobId, agentRunId, expectedJobVersion, clock.instant())
                .orElseThrow(this::staleWorkflow);
    }

    @Override
    @Transactional
    public JobRecord markFailed(
            UUID userId,
            UUID jobId,
            UUID agentRunId,
            long expectedJobVersion) {
        return store.markFailed(
                        userId, jobId, agentRunId, expectedJobVersion, clock.instant())
                .orElseThrow(this::staleWorkflow);
    }

    @Override
    @Transactional
    public void compensateToStableState(UUID userId, UUID jobId, UUID agentRunId) {
        store.compensateStable(userId, jobId, agentRunId, clock.instant());
    }

    @Override
    public boolean supports(String resourceType) {
        return RESOURCE_TYPE.equals(resourceType);
    }

    @Override
    public void requireActiveOwner(UUID userId, String resourceType, UUID resourceId) {
        active(userId, resourceId);
    }

    @Override
    public void compensate(
            UUID userId, UUID agentRunId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        compensateToStableState(userId, resourceId, agentRunId);
    }

    private JobRecord active(UUID userId, UUID jobId) {
        return store.findActive(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String optionalScalar(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()
                || normalized.length() > maximum
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    private BusinessException staleWorkflow() {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", "version", "reason", "STALE"),
                null);
    }
}
