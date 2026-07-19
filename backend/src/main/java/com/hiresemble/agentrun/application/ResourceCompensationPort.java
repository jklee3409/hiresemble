package com.hiresemble.agentrun.application;

import java.util.UUID;

public interface ResourceCompensationPort {
    default boolean supports(String resourceType) {
        return true;
    }

    void compensate(UUID userId, UUID agentRunId, String resourceType, UUID resourceId);
}
