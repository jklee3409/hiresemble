package com.hiresemble.agentrun.infrastructure.worker;

import com.hiresemble.agentrun.infrastructure.config.AgentRuntimeProperties;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.model.SafeInterruption;
import com.hiresemble.agentrun.domain.model.SafeError;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentRunReconciler {

    private static final SafeInterruption LEASE_EXPIRED = new SafeInterruption(
            new SafeError("AGENT_RUN_INTERRUPTED", "실행이 중단되었습니다. 다시 시도해 주세요."),
            true);

    private final AgentRunStatePort statePort;
    private final AgentRunDispatchPort dispatchPort;
    private final AgentRuntimeProperties properties;
    private final Clock clock;

    public AgentRunReconciler(
            AgentRunStatePort statePort,
            AgentRunDispatchPort dispatchPort,
            AgentRuntimeProperties properties,
            Clock clock) {
        this.statePort = statePort;
        this.dispatchPort = dispatchPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.agent-runtime.reconciliation-interval:30s}")
    public void reconcile() {
        reconcileOnce();
    }

    public void reconcileOnce() {
        Instant now = clock.instant();
        for (UUID runId : statePort.findExpiredRunningIds(
                now, properties.getReconciliationBatchSize())) {
            try {
                statePort.interruptExpired(runId, now, LEASE_EXPIRED);
            } catch (RuntimeException ignored) {
                // A competing heartbeat/reconciler won; the next DB scan remains authoritative.
            }
        }
        dispatchPort.scanQueued();
    }
}
