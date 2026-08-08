package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import java.time.Instant;

public interface AgentRunHistoryDeletionContributor {

    boolean supports(AgentRunSnapshot run);

    void beforeDelete(AgentRunSnapshot run, Instant deletedAt);
}
