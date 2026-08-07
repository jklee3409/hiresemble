package com.hiresemble.githubsource.application;

import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.githubsource.application.GitHubGatewayException.Kind;
import com.hiresemble.githubsource.application.GitHubGatewayModels.ConditionalRepository;
import com.hiresemble.githubsource.domain.GitHubSourceKind;
import com.hiresemble.githubsource.domain.GitHubRepositorySelection;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Page;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Snapshot;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.domain.GitHubSourceStatus;
import com.hiresemble.githubsource.domain.GitHubUrl;
import com.hiresemble.githubsource.infrastructure.GitHubProperties;
import com.hiresemble.githubsource.infrastructure.GitHubSourceStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GitHubSourceApplicationService {

    private static final String CREATE_SCOPE = "/api/v1/github-sources";
    private static final String SELECTION_SCOPE =
            "/api/v1/github-sources/{id}/repository-selection";
    private static final String REFRESH_SCOPE = "/api/v1/github-sources/{id}/refresh";

    private final GitHubSourceStore store;
    private final GitHubSourceMutationService mutation;
    private final GitHubRestGateway gateway;
    private final GitHubProperties properties;
    private final IdempotencyService idempotency;

    public GitHubSourceApplicationService(
            GitHubSourceStore store,
            GitHubSourceMutationService mutation,
            GitHubRestGateway gateway,
            GitHubProperties properties,
            IdempotencyService idempotency) {
        this.store = store;
        this.mutation = mutation;
        this.gateway = gateway;
        this.properties = properties;
        this.idempotency = idempotency;
    }

    public IdempotentResponse<WorkflowLaunchResult> register(
            UUID userId, String url, boolean participationConfirmed, String idempotencyKey) {
        if (!participationConfirmed) throw invalid();
        GitHubUrl parsed = GitHubUrl.parse(url);
        IdempotencyScope scope = new IdempotencyScope(
                userId, "POST", CREATE_SCOPE, IdempotencyScope.ROOT_SCOPE_ID, idempotencyKey);
        return idempotency.executePrepared(
                scope,
                parsed.canonicalUrl() + "|true",
                WorkflowLaunchResult.class,
                () -> parsed,
                prepared -> {
                    WorkflowLaunchResult run = mutation.register(userId, prepared);
                    return new OriginalResponse<>(
                            202,
                            run,
                            GitHubSourceMutationService.RESOURCE_TYPE,
                            run.resourceId(),
                            run.agentRunId());
                },
                ignored -> {});
    }

    public Page<Source> list(
            UUID userId,
            GitHubSourceStatus status,
            GitHubSourceKind kind,
            int page,
            int size,
            String sort) {
        requirePage(page, size);
        return store.list(userId, status, kind, page, size, sort);
    }

    public Source detail(UUID userId, UUID sourceId) {
        return store.findActive(userId, sourceId).orElseThrow(this::notFound);
    }

    public Page<Repository> repositories(
            UUID userId,
            UUID sourceId,
            String query,
            Boolean selected,
            int page,
            int size,
            String sort) {
        detail(userId, sourceId);
        requirePage(page, size);
        if (query != null && query.length() > 200) throw invalid();
        return store.repositories(userId, sourceId, query, selected, page, size, sort);
    }

    public IdempotentResponse<WorkflowLaunchResult> selectRepositories(
            UUID userId,
            UUID sourceId,
            List<UUID> repositoryIds,
            long version,
            String idempotencyKey) {
        List<UUID> selected = validateSelection(repositoryIds);
        String canonical = version + "|" + selected.stream().map(UUID::toString).toList();
        IdempotencyScope scope = new IdempotencyScope(
                userId, "PUT", SELECTION_SCOPE, sourceId, idempotencyKey);
        return idempotency.executePrepared(
                scope,
                canonical,
                WorkflowLaunchResult.class,
                () -> selected,
                prepared -> {
                    WorkflowLaunchResult run = mutation.selectRepositories(
                            userId, sourceId, prepared, version);
                    return new OriginalResponse<>(
                            202,
                            run,
                            GitHubSourceMutationService.RESOURCE_TYPE,
                            sourceId,
                            run.agentRunId());
                },
                ignored -> {});
    }

    public IdempotentResponse<RefreshResult> refresh(
            UUID userId, UUID sourceId, long version, String idempotencyKey) {
        IdempotencyScope scope = new IdempotencyScope(
                userId, "POST", REFRESH_SCOPE, sourceId, idempotencyKey);
        return idempotency.executePrepared(
                scope,
                Long.toString(version),
                RefreshResult.class,
                () -> inspectRefresh(userId, sourceId, version),
                inspection -> {
                    if (!inspection.changed()) {
                        Source source = detail(userId, sourceId);
                        return new OriginalResponse<>(
                                200,
                                new RefreshResult(false, source, null),
                                GitHubSourceMutationService.RESOURCE_TYPE,
                                sourceId,
                                null);
                    }
                    WorkflowLaunchResult run = mutation.refresh(userId, sourceId, version);
                    Source source = detail(userId, sourceId);
                    return new OriginalResponse<>(
                            202,
                            new RefreshResult(true, source, run),
                            GitHubSourceMutationService.RESOURCE_TYPE,
                            sourceId,
                            run.agentRunId());
                },
                ignored -> {});
    }

    public void delete(UUID userId, UUID sourceId, long version) {
        mutation.delete(userId, sourceId, version);
    }

    private RefreshInspection inspectRefresh(UUID userId, UUID sourceId, long version) {
        Source source = detail(userId, sourceId);
        if (source.version() != version) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        if (!source.status().terminalSnapshotState()) throw stateConflict();
        List<Repository> repositories = store.selectedRepositories(userId, sourceId);
        if (repositories.isEmpty()) {
            throw new BusinessException(ErrorCode.GITHUB_REPOSITORY_SELECTION_REQUIRED);
        }
        boolean changed = false;
        for (Repository repository : repositories) {
            try {
                ConditionalRepository current = gateway.repository(
                        repository.ownerLogin(), repository.repositoryName(), repository.metadataEtag());
                if (current.notModified()) continue;
                if (current.repository().privateRepository()) {
                    throw new BusinessException(ErrorCode.GITHUB_SOURCE_NOT_ACCESSIBLE);
                }
                var commit = gateway.defaultBranchCommit(
                        current.repository().ownerLogin(),
                        current.repository().repositoryName(),
                        current.repository().defaultBranch());
                Snapshot latest = store.latestSnapshot(
                                userId, repository.id(), properties.getRetrievalPolicyVersion())
                        .orElse(null);
                if (latest == null || !latest.commitSha().equals(commit.commitSha())) {
                    changed = true;
                }
            } catch (GitHubGatewayException exception) {
                throw gatewayFailure(exception);
            }
        }
        return new RefreshInspection(changed);
    }

    private List<UUID> validateSelection(List<UUID> repositoryIds) {
        GitHubRepositorySelection selection = new GitHubRepositorySelection(repositoryIds);
        if (selection.repositoryIds().size() > properties.getMaxSelectedRepositories()) {
            throw invalid();
        }
        return selection.repositoryIds();
    }

    private void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
    }

    private BusinessException gatewayFailure(GitHubGatewayException exception) {
        if (exception.kind() == Kind.RATE_LIMITED) {
            Duration retry = exception.retryAfter() == null
                    ? Duration.ofMinutes(1) : exception.retryAfter();
            return new BusinessException(
                    ErrorCode.GITHUB_RATE_LIMITED,
                    Map.of("retryAfterSeconds", Long.toString(
                            Math.max(1, Math.min(retry.toSeconds(), 86_400)))),
                    exception);
        }
        if (exception.kind() == Kind.NOT_FOUND) {
            return new BusinessException(ErrorCode.GITHUB_SOURCE_NOT_ACCESSIBLE, exception);
        }
        if (exception.kind() == Kind.RESPONSE_LIMIT) {
            return new BusinessException(ErrorCode.GITHUB_SOURCE_LIMIT_EXCEEDED, exception);
        }
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private BusinessException stateConflict() {
        return new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
    }

    private record RefreshInspection(boolean changed) {}

    public record RefreshResult(boolean changed, Source source, WorkflowLaunchResult run) {}
}
