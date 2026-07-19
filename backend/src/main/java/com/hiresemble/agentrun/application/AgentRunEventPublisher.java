package com.hiresemble.agentrun.application;

public interface AgentRunEventPublisher {
    void publishAfterCommit(AgentRunCommittedEvent event);
}
