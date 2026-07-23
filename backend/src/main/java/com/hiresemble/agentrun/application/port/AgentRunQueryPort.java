package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.AgentRunPage;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.ReusableStepSnapshot;
import com.hiresemble.agentrun.application.query.AgentRunListCriteria;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
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
