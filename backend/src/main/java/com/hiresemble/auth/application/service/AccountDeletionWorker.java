package com.hiresemble.auth.application.service;

import com.hiresemble.agentrun.application.port.AgentRunCancellationPort;
import com.hiresemble.auth.infrastructure.persistence.AccountDeletionTaskStore;
import com.hiresemble.auth.infrastructure.persistence.AccountPurgeStore;
import com.hiresemble.auth.infrastructure.persistence.AccountPurgeStore.TerminalState;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactObjectDeletionOutboxStore;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.infrastructure.persistence.ObjectDeletionOutboxStore;
import com.hiresemble.githubsource.application.GitHubInstallationTokenProvider;
import com.hiresemble.githubsource.domain.GitHubAppConnectionStatus;
import com.hiresemble.githubsource.infrastructure.GitHubAppConnectionStore;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public final class AccountDeletionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountDeletionWorker.class);
    private static final int MAX_ATTEMPTS = 20;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6),
            Duration.ofHours(12));

    private final AccountDeletionTaskStore taskStore;
    private final AccountPurgeStore purgeStore;
    private final AgentRunCancellationPort cancellationPort;
    private final GitHubAppConnectionStore connectionStore;
    private final ObjectProvider<GitHubInstallationTokenProvider> tokenProviders;
    private final ObjectDeletionOutboxStore documentOutbox;
    private final GitHubSnapshotDeletionOutboxStore snapshotOutbox;
    private final CareerArtifactObjectDeletionOutboxStore artifactOutbox;
    private final Clock clock;

    public AccountDeletionWorker(
            AccountDeletionTaskStore taskStore,
            AccountPurgeStore purgeStore,
            AgentRunCancellationPort cancellationPort,
            GitHubAppConnectionStore connectionStore,
            ObjectProvider<GitHubInstallationTokenProvider> tokenProviders,
            ObjectDeletionOutboxStore documentOutbox,
            GitHubSnapshotDeletionOutboxStore snapshotOutbox,
            CareerArtifactObjectDeletionOutboxStore artifactOutbox,
            Clock clock) {
        this.taskStore = taskStore;
        this.purgeStore = purgeStore;
        this.cancellationPort = cancellationPort;
        this.connectionStore = connectionStore;
        this.tokenProviders = tokenProviders;
        this.documentOutbox = documentOutbox;
        this.snapshotOutbox = snapshotOutbox;
        this.artifactOutbox = artifactOutbox;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hiresemble.account-deletion.scan-interval:30s}")
    public void processDue() {
        for (int index = 0; index < 10; index++) {
            Instant now = clock.instant();
            var task = taskStore.claimDue(now, LEASE).orElse(null);
            if (task == null) return;
            process(task, now);
        }
    }

    private void process(AccountDeletionTaskStore.ClaimedTask task, Instant now) {
        try {
            cancelRuns(task.userId(), now);
            prepareConnections(task.userId(), now);
            enqueueObjects(task.userId(), now);
            if (purgeStore.hasActiveRuns(task.userId())) {
                retry(task, "ACCOUNT_DELETION_RUNS_ACTIVE", now);
                return;
            }
            TerminalState terminal = purgeStore.terminalState(task.userId());
            if (terminal == TerminalState.DEAD) {
                taskStore.dead(
                        task.id(),
                        task.claimToken(),
                        task.attemptCount(),
                        "ACCOUNT_DELETION_CHILD_DEAD",
                        now);
                return;
            }
            if (terminal == TerminalState.PENDING) {
                retry(task, "ACCOUNT_DELETION_CLEANUP_PENDING", now);
                return;
            }
            if (!purgeStore.finalizePurge(
                    task.id(), task.userId(), task.claimToken(), task.attemptCount(), now)) {
                retry(task, "ACCOUNT_DELETION_STATE_CHANGED", now);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Account deletion attempt will be retried; failureType={}",
                    exception.getClass().getName());
            if (task.attemptCount() >= MAX_ATTEMPTS) {
                taskStore.dead(
                        task.id(),
                        task.claimToken(),
                        task.attemptCount(),
                        "ACCOUNT_DELETION_FAILED",
                        now);
            } else {
                retry(task, "ACCOUNT_DELETION_RETRYABLE_FAILURE", now);
            }
        }
    }

    private void cancelRuns(java.util.UUID userId, Instant now) {
        for (AccountPurgeStore.ActiveRun run : purgeStore.cancellableRuns(userId)) {
            try {
                cancellationPort.requestCancellation(userId, run.id(), run.stateVersion(), now);
            } catch (BusinessException exception) {
                if (exception.errorCode() != ErrorCode.RESOURCE_STATE_CONFLICT
                        && exception.errorCode() != ErrorCode.RESOURCE_NOT_FOUND) {
                    throw exception;
                }
            }
        }
    }

    private void prepareConnections(java.util.UUID userId, Instant now) {
        for (var connection : connectionStore.list(userId)) {
            if (connection.status() == GitHubAppConnectionStatus.ACTIVE
                    || connection.status() == GitHubAppConnectionStatus.SUSPENDED
                    || connection.status() == GitHubAppConnectionStatus.REVOKED) {
                connectionStore.beginDisconnect(
                        userId,
                        connection.id(),
                        connection.version(),
                        "ACCOUNT_DELETION",
                        now);
                tokenProviders.orderedStream()
                        .forEach(provider -> provider.invalidate(connection.id(), null));
            }
        }
    }

    private void enqueueObjects(java.util.UUID userId, Instant now) {
        purgeStore.documentObjectsNeedingDeletion(userId).forEach(object ->
                documentOutbox.enqueueDocument(
                        userId, object.documentId(), object.storageKey(), now));
        purgeStore.snapshotObjectsNeedingDeletion(userId).forEach(object ->
                snapshotOutbox.enqueueAccountDeletion(
                        userId, object.snapshotId(), object.storageKey(), now));
        purgeStore.artifactObjectsNeedingDeletion(userId).forEach(object ->
                artifactOutbox.enqueueArtifact(
                        userId,
                        object.artifactId(),
                        object.versionId(),
                        object.storageKey(),
                        now));
    }

    private void retry(AccountDeletionTaskStore.ClaimedTask task, String code, Instant now) {
        if (task.attemptCount() >= MAX_ATTEMPTS) {
            taskStore.dead(
                    task.id(), task.claimToken(), task.attemptCount(), code, now);
            return;
        }
        taskStore.retry(
                task.id(),
                task.claimToken(),
                task.attemptCount(),
                code,
                now.plus(delay(task.attemptCount())));
    }

    static Duration delay(int attempt) {
        if (attempt >= 1 && attempt <= RETRY_DELAYS.size()) {
            return RETRY_DELAYS.get(attempt - 1);
        }
        return Duration.ofHours(24);
    }

    @Scheduled(cron = "${hiresemble.account-deletion.cleanup-cron:0 20 4 * * *}")
    public void cleanupCompletedMetadata() {
        taskStore.cleanupCompletedBefore(clock.instant().minus(Duration.ofDays(30)));
    }
}
