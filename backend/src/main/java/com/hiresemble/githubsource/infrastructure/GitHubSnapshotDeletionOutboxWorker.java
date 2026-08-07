package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.document.application.port.ObjectStorageException;
import com.hiresemble.githubsource.application.GitHubSnapshotStoragePort;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxStore.ClaimedDeletion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class GitHubSnapshotDeletionOutboxWorker {

    private static final int MAX_ATTEMPTS = 10;
    private static final int BATCH_SIZE = 20;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(12));

    private final GitHubSnapshotDeletionOutboxStore store;
    private final GitHubSnapshotStoragePort storage;
    private final Clock clock;

    public GitHubSnapshotDeletionOutboxWorker(
            GitHubSnapshotDeletionOutboxStore store,
            GitHubSnapshotStoragePort storage,
            Clock clock) {
        this.store = store;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.github.deletion-scan-interval:30s}")
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
