package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.AgentRunCommittedEvent;

public interface AgentRunEventPublisher {
    void publishAfterCommit(AgentRunCommittedEvent event);
}
