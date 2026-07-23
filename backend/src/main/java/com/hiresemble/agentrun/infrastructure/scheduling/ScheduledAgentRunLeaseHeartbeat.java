package com.hiresemble.agentrun.infrastructure.scheduling;

import com.hiresemble.agentrun.infrastructure.config.AgentRuntimeProperties;
import com.hiresemble.agentrun.application.port.AgentRunLeaseHeartbeatPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAgentRunLeaseHeartbeat implements AgentRunLeaseHeartbeatPort {

    private final AgentRunStatePort statePort;
    private final TaskScheduler scheduler;
    private final AgentRuntimeProperties properties;
    private final Clock clock;

    public ScheduledAgentRunLeaseHeartbeat(
            AgentRunStatePort statePort,
            @Qualifier("agentLeaseHeartbeatTaskScheduler") TaskScheduler scheduler,
            AgentRuntimeProperties properties,
            Clock clock) {
        this.statePort = statePort;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public <T> T maintain(
            UUID userId,
            UUID agentRunId,
            UUID claimToken,
            Supplier<T> blockingCall) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(agentRunId, "agentRunId");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(blockingCall, "blockingCall");

        Duration interval = properties.getHeartbeatInterval();
        Duration lease = properties.getLeaseDuration();
        heartbeat(userId, agentRunId, claimToken, lease);
        Object pulseMonitor = new Object();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicReference<RuntimeException> scheduledFailure = new AtomicReference<>();
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> pulse(
                        userId,
                        agentRunId,
                        claimToken,
                        lease,
                        pulseMonitor,
                        active,
                        scheduledFailure),
                clock.instant().plus(interval),
                interval);
        T result;
        try {
            result = blockingCall.get();
        } finally {
            synchronized (pulseMonitor) {
                active.set(false);
            }
            task.cancel(false);
        }
        RuntimeException failure = scheduledFailure.get();
        if (failure != null) {
            throw failure;
        }
        heartbeat(userId, agentRunId, claimToken, lease);
        return result;
    }

    private void pulse(
            UUID userId,
            UUID agentRunId,
            UUID claimToken,
            Duration lease,
            Object pulseMonitor,
            AtomicBoolean active,
            AtomicReference<RuntimeException> failure) {
        synchronized (pulseMonitor) {
            if (!active.get() || failure.get() != null) {
                return;
            }
            try {
                heartbeat(userId, agentRunId, claimToken, lease);
            } catch (RuntimeException exception) {
                failure.compareAndSet(null, exception);
            }
        }
    }

    private void heartbeat(UUID userId, UUID agentRunId, UUID claimToken, Duration lease) {
        statePort.heartbeat(userId, agentRunId, claimToken, clock.instant(), lease);
    }
}
