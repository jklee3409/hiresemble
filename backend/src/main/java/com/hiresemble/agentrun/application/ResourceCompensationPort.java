package com.hiresemble.agentrun.application;

import java.util.UUID;

public interface ResourceCompensationPort {
    void compensate(UUID userId, UUID agentRunId, String resourceType, UUID resourceId);
}
