package com.hiresemble.agentrun.application;

import java.util.UUID;
import java.util.function.Supplier;

/** Keeps a claimed Run's database lease alive while a blocking external call is in flight. */
public interface AgentRunLeaseHeartbeatPort {

    <T> T maintain(UUID userId, UUID agentRunId, UUID claimToken, Supplier<T> blockingCall);
}
