package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AiQualityMode;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunQueryPort {
    Optional<AgentRunSnapshot> findByOwner(UUID userId, UUID agentRunId);
    AgentRunPage findPage(AgentRunListCriteria criteria);
    Optional<ReusableStepSnapshot> findReusableStep(
            UUID userId,
            String stepKey,
            String scopeKey,
            String inputHash,
            AiQualityMode requestedQualityMode);
}
