package com.hiresemble.githubsource.infrastructure;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public final class GitHubPrivateConnectionCleanupWorker {

    private final GitHubPrivateConnectionCleanupStore store;
    private final Clock clock;

    public GitHubPrivateConnectionCleanupWorker(
            GitHubPrivateConnectionCleanupStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.github.app.cleanup-scan-interval:30s}")
    public void processReady() {
        store.finalizeReady(clock.instant());
    }
}
