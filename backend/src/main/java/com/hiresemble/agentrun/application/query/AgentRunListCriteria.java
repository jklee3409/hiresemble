package com.hiresemble.agentrun.application.query;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import java.util.List;
import java.util.UUID;

public record AgentRunListCriteria(
        UUID userId,
        List<WorkflowType> workflowTypes,
        List<AgentRunStatus> statuses,
        String resourceType,
        UUID resourceId,
        Boolean retryable,
        int page,
        int size,
        AgentRunSort sort) {

    public AgentRunListCriteria {
        workflowTypes = workflowTypes == null ? List.of() : List.copyOf(workflowTypes);
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }
}
