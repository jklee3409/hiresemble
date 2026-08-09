package com.hiresemble.githubsource.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public final class GitHubConnectionAttemptCleanupWorker {

    private static final Duration AUDIT_RETENTION = Duration.ofHours(24);

    private final GitHubAppConnectionStore store;
    private final Clock clock;

    public GitHubConnectionAttemptCleanupWorker(GitHubAppConnectionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.github.app.attempt-cleanup-interval:1h}")
    public void cleanup() {
        store.cleanupAttemptsBefore(clock.instant().minus(AUDIT_RETENTION));
    }
}
