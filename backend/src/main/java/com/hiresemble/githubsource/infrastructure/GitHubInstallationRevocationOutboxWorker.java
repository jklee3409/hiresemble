package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.githubsource.application.GitHubAppRemoteGateway;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public final class GitHubInstallationRevocationOutboxWorker {

    private static final int MAX_ATTEMPTS = 10;
    private static final int BATCH_SIZE = 20;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));

    private final GitHubInstallationRevocationOutboxStore store;
    private final GitHubAppRemoteGateway remote;
    private final Clock clock;

    public GitHubInstallationRevocationOutboxWorker(
            GitHubInstallationRevocationOutboxStore store,
            GitHubAppRemoteGateway remote,
            Clock clock) {
        this.store = store;
        this.remote = remote;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.github.app.revocation-scan-interval:30s}")
    public void processDue() {
        for (int index = 0; index < BATCH_SIZE; index++) {
            Instant now = clock.instant();
            var revocation = store.claimDue(now, LEASE).orElse(null);
            if (revocation == null) return;
            try {
                remote.uninstall(revocation.installationId());
                store.markSucceeded(revocation.id(), revocation.claimToken(), now);
            } catch (GitHubGatewayException exception) {
                boolean transientFailure = exception.kind() == GitHubGatewayException.Kind.TIMEOUT
                        || exception.kind() == GitHubGatewayException.Kind.RATE_LIMITED
                        || exception.kind() == GitHubGatewayException.Kind.UPSTREAM_5XX;
                boolean dead = !transientFailure || revocation.attemptCount() >= MAX_ATTEMPTS;
                store.markFailed(
                        revocation.id(),
                        revocation.claimToken(),
                        revocation.attemptCount(),
                        now.plus(delay(revocation.attemptCount())),
                        dead,
                        now);
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
