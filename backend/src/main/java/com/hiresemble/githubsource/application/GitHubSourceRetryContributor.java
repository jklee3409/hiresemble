package com.hiresemble.githubsource.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowRetryOptions;
import com.hiresemble.agentrun.application.port.AgentRunCreationPort;
import com.hiresemble.agentrun.application.port.AgentRunRetryContributor;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class GitHubSourceRetryContributor implements AgentRunRetryContributor {

    private final AgentRunCreationPort creationPort;

    public GitHubSourceRetryContributor(AgentRunCreationPort creationPort) {
        this.creationPort = creationPort;
    }

    @Override
    public boolean supports(WorkflowType workflowType) {
        return workflowType == WorkflowType.GITHUB_INGESTION;
    }

    @Override
    public AgentRunSnapshot createRetry(
            UUID proposedId,
            AgentRunSnapshot predecessor,
            WorkflowRetryOptions options,
            long budgetPolicyVersion,
            long priceVersion,
            Instant queuedAt) {
        if (!GitHubSourceMutationService.RESOURCE_TYPE.equals(predecessor.resourceType())
                || predecessor.resourceId() == null
                || options.qualityMode() != null
                || !options.values().isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return creationPort.createRetry(
                proposedId, predecessor, budgetPolicyVersion, priceVersion, queuedAt);
    }
}
