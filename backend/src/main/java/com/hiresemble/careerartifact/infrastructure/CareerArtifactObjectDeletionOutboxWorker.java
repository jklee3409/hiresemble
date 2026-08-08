package com.hiresemble.careerartifact.infrastructure;

import com.hiresemble.careerartifact.infrastructure.CareerArtifactObjectDeletionOutboxStore.ClaimedDeletion;
import com.hiresemble.document.application.port.ObjectStorageException;
import com.hiresemble.document.application.port.ObjectStoragePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class CareerArtifactObjectDeletionOutboxWorker {

    private static final int MAX_ATTEMPTS = 10;
    private static final int BATCH_SIZE = 20;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(12));

    private final CareerArtifactObjectDeletionOutboxStore store;
    private final ObjectStoragePort storage;
    private final Clock clock;

    public CareerArtifactObjectDeletionOutboxWorker(
            CareerArtifactObjectDeletionOutboxStore store,
            ObjectStoragePort storage,
            Clock clock) {
        this.store = store;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.career-artifact.deletion-scan-interval:30s}")
    public void processDue() {
        for (int index = 0; index < BATCH_SIZE; index++) {
            Instant now = clock.instant();
            ClaimedDeletion deletion = store.claimDue(now, LEASE).orElse(null);
            if (deletion == null) return;
            try {
                storage.delete(deletion.storageKey());
                store.markSucceeded(deletion.id(), deletion.claimToken(), now);
            } catch (ObjectStorageException exception) {
                boolean dead = deletion.attemptCount() >= MAX_ATTEMPTS;
                store.markFailed(
                        deletion.id(), deletion.claimToken(), deletion.attemptCount(),
                        now.plus(delay(deletion.attemptCount())), dead, now);
            }
        }
    }

    static Duration delay(int attempt) {
        if (attempt >= 1 && attempt <= RETRY_DELAYS.size()) {
            return RETRY_DELAYS.get(attempt - 1);
        }
        return Duration.ofHours(24);
    }
}
