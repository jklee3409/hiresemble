package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowRetryOptions;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import java.time.Instant;
import java.util.UUID;

public interface AgentRunRetryContributor {

    boolean supports(WorkflowType workflowType);

    AgentRunSnapshot createRetry(
            UUID proposedId,
            AgentRunSnapshot predecessor,
            WorkflowRetryOptions options,
            long budgetPolicyVersion,
            Instant queuedAt);
}
