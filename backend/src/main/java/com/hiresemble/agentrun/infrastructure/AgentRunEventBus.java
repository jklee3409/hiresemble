package com.hiresemble.agentrun.infrastructure;

import com.hiresemble.agentrun.application.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.AgentRunEventPublisher;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AgentRunEventBus implements AgentRunEventPublisher {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Consumer<AgentRunCommittedEvent>>>
            subscribers = new ConcurrentHashMap<>();

    @Override
    public void publishAfterCommit(AgentRunCommittedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            return;
        }
        publish(event);
    }

    public Subscription subscribe(UUID agentRunId, Consumer<AgentRunCommittedEvent> consumer) {
        CopyOnWriteArrayList<Consumer<AgentRunCommittedEvent>> runSubscribers =
                subscribers.computeIfAbsent(agentRunId, ignored -> new CopyOnWriteArrayList<>());
        runSubscribers.add(consumer);
        return () -> {
            runSubscribers.remove(consumer);
            if (runSubscribers.isEmpty()) {
                subscribers.remove(agentRunId, runSubscribers);
            }
        };
    }

    public int subscriberCount(UUID agentRunId) {
        return subscribers.getOrDefault(agentRunId, new CopyOnWriteArrayList<>()).size();
    }

    private void publish(AgentRunCommittedEvent event) {
        for (Consumer<AgentRunCommittedEvent> consumer :
                subscribers.getOrDefault(event.run().id(), new CopyOnWriteArrayList<>())) {
            try {
                consumer.accept(event);
            } catch (RuntimeException ignored) {
                // A best-effort transport failure must never affect durable run state.
            }
        }
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
