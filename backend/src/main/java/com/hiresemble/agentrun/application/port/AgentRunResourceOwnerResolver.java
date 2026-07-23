package com.hiresemble.agentrun.application.port;

import java.util.UUID;

public interface AgentRunResourceOwnerResolver {

    boolean supports(String resourceType);

    void requireActiveOwner(UUID userId, UUID resourceId);
}
