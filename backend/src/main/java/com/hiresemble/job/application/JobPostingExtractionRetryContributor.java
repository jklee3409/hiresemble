package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowRetryOptions;
import com.hiresemble.agentrun.application.port.AgentRunCreationPort;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunRetryContributor;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Upgrades terminal legacy Job extraction retries to the current canonical workflow. */
@Component
public final class JobPostingExtractionRetryContributor implements AgentRunRetryContributor {

    private final JobStore store;
    private final JobExtractionLaunchFactory launchFactory;
    private final AgentRunCreationPort creationPort;
    private final AgentRunQueryPort queryPort;

    public JobPostingExtractionRetryContributor(
            JobStore store,
            JobExtractionLaunchFactory launchFactory,
            AgentRunCreationPort creationPort,
            AgentRunQueryPort queryPort) {
        this.store = store;
        this.launchFactory = launchFactory;
        this.creationPort = creationPort;
        this.queryPort = queryPort;
    }

    @Override
    public boolean supports(WorkflowType workflowType) {
        return workflowType == WorkflowType.JOB_POSTING_EXTRACTION;
    }

    @Override
    public AgentRunSnapshot createRetry(
            UUID proposedId,
            AgentRunSnapshot predecessor,
            WorkflowRetryOptions options,
            long budgetPolicyVersion,
            long priceVersion,
            Instant queuedAt) {
        if (!"JOB".equals(predecessor.resourceType()) || predecessor.resourceId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (!options.values().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        AiQualityMode qualityMode = options.qualityMode() == null
                ? predecessor.requestedQualityMode() : options.qualityMode();
        if (qualityMode == null || qualityMode == AiQualityMode.HIGH_QUALITY) {
            throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
        }
        JobRecord job = store.lockActive(predecessor.userId(), predecessor.resourceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.latestAgentRunId() != null) {
            AgentRunSnapshot existing = queryPort.findByOwner(
                            predecessor.userId(), job.latestAgentRunId())
                    .orElse(null);
            if (existing != null && predecessor.id().equals(existing.retryOfRunId())) {
                long snapshottedJobVersion = existing.inputReferenceSnapshot() == null
                        ? -1L : existing.inputReferenceSnapshot().path("jobVersion").asLong(-1L);
                boolean compatible = existing.workflowType() == WorkflowType.JOB_POSTING_EXTRACTION
                        && CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION
                                .equals(existing.workflowVersion())
                        && "JOB".equals(existing.resourceType())
                        && job.id().equals(existing.resourceId())
                        && existing.requestedQualityMode() == qualityMode
                        && job.version() == snapshottedJobVersion + 1L;
                if (compatible) return existing;
                throw new BusinessException(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED);
            }
        }
        var command = launchFactory.command(proposedId, job, qualityMode);
        return creationPort.createRetry(
                proposedId, predecessor, command, budgetPolicyVersion, priceVersion, queuedAt);
    }
}
