package com.hiresemble.agentrun.application.port;

import java.util.UUID;

public interface AgentRunDispatchPort {
    void enqueue(UUID agentRunId);
    void scanQueued();
}
