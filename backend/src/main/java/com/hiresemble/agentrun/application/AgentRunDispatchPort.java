package com.hiresemble.agentrun.application;

import java.util.UUID;

public interface AgentRunDispatchPort {
    void enqueue(UUID agentRunId);
    void scanQueued();
}
