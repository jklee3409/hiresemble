package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentRunApplicationService {

    private final AgentRunQueryPort queryPort;
    private final List<AgentRunResourceOwnerResolver> resourceResolvers;

    public AgentRunApplicationService(
            AgentRunQueryPort queryPort,
            List<AgentRunResourceOwnerResolver> resourceResolvers) {
        this.queryPort = queryPort;
        this.resourceResolvers = List.copyOf(resourceResolvers);
    }

    public AgentRunPage list(
            UUID userId,
            List<WorkflowType> workflowTypes,
            List<AgentRunStatus> statuses,
            String resourceType,
            UUID resourceId,
            Boolean retryable,
            int page,
            int size,
            String sort) {
        if (page < 0 || size < 1 || size > 100 || (resourceType == null) != (resourceId == null)
                || (resourceType != null && (resourceType.isBlank() || resourceType.length() > 50))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        AgentRunSort parsedSort = switch (sort) {
            case "queuedAt,desc" -> AgentRunSort.QUEUED_AT_DESC;
            case "updatedAt,desc" -> AgentRunSort.UPDATED_AT_DESC;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
        if (resourceType != null) {
            AgentRunResourceOwnerResolver resolver = resourceResolvers.stream()
                    .filter(candidate -> candidate.supports(resourceType))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            resolver.requireActiveOwner(userId, resourceId);
        }
        return queryPort.findPage(new AgentRunListCriteria(
                userId,
                workflowTypes == null ? List.of() : workflowTypes,
                statuses == null ? List.of() : statuses,
                resourceType,
                resourceId,
                retryable,
                page,
                size,
                parsedSort));
    }

    public AgentRunSnapshot detail(UUID userId, UUID agentRunId) {
        return queryPort.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
