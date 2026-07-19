package com.hiresemble.document.application;

import com.hiresemble.document.infrastructure.ObjectDeletionOutboxProperties;
import com.hiresemble.document.infrastructure.ObjectDeletionOutboxStore;
import com.hiresemble.document.infrastructure.ObjectDeletionOutboxStore.ClaimedDeletion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ObjectDeletionOutboxService {

    private static final int MAX_ATTEMPTS = 10;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(12));

    private final ObjectDeletionOutboxStore store;
    private final ObjectStoragePort storage;
    private final ObjectDeletionOutboxProperties properties;
    private final ObjectProvider<ObjectDeletionAlertPort> alertPorts;
    private final Clock clock;

    public ObjectDeletionOutboxService(
            ObjectDeletionOutboxStore store,
            ObjectStoragePort storage,
            ObjectDeletionOutboxProperties properties,
            ObjectProvider<ObjectDeletionAlertPort> alertPorts,
            Clock clock) {
        this.store = store;
        this.storage = storage;
        this.properties = properties;
        this.alertPorts = alertPorts;
        this.clock = clock;
    }

    public UUID enqueueDocument(UUID userId, UUID documentId, String storageKey, Instant now) {
        return store.enqueueDocument(userId, documentId, storageKey, now);
    }

    public UUID enqueueOrphan(UUID userId, String storageKey, Instant now) {
        return store.enqueueOrphan(userId, storageKey, now);
    }

    @Scheduled(fixedDelayString = "${hiresemble.object-deletion-outbox.scan-interval:30s}")
    public void processDue() {
        for (int index = 0; index < properties.getBatchSize(); index++) {
            Instant now = clock.instant();
            ClaimedDeletion deletion = store.claimDue(now, properties.getLeaseDuration()).orElse(null);
            if (deletion == null) return;
            process(deletion, now);
        }
    }

    private void process(ClaimedDeletion deletion, Instant now) {
        try {
            storage.delete(deletion.storageKey());
            store.markSucceeded(deletion.id(), deletion.claimToken(), now);
        } catch (ObjectStorageException exception) {
            boolean dead = deletion.attemptCount() >= MAX_ATTEMPTS;
            Duration delay = delay(deletion.attemptCount());
            if (store.markFailed(
                            deletion.id(), deletion.claimToken(), deletion.attemptCount(),
                            now.plus(delay), dead, now)
                    && dead) {
                ObjectDeletionAlertPort alert = alertPorts.getIfAvailable();
                if (alert != null) alert.deadLetter(deletion.id(), deletion.userId());
            }
        }
    }

    static Duration delay(int attempt) {
        if (attempt <= RETRY_DELAYS.size()) return RETRY_DELAYS.get(attempt - 1);
        return Duration.ofHours(24);
    }
}
