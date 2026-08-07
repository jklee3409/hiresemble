package com.hiresemble.githubsource.application;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunResumePort;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.domain.GitHubUrl;
import com.hiresemble.githubsource.infrastructure.GitHubProperties;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxStore;
import com.hiresemble.githubsource.infrastructure.GitHubSourceStore;
import com.hiresemble.profile.application.service.GitHubCanonicalEvidenceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GitHubSourceMutationService {

    public static final String RESOURCE_TYPE = "GITHUB_SOURCE";

    private final GitHubSourceStore store;
    private final WorkflowLauncher launcher;
    private final AgentRunQueryPort runQuery;
    private final AgentRunResumePort resumePort;
    private final GitHubSnapshotDeletionOutboxStore outbox;
    private final GitHubProperties properties;
    private final GitHubCanonicalEvidenceService canonicalEvidenceService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GitHubSourceMutationService(
            GitHubSourceStore store,
            WorkflowLauncher launcher,
            AgentRunQueryPort runQuery,
            AgentRunResumePort resumePort,
            GitHubSnapshotDeletionOutboxStore outbox,
            GitHubProperties properties,
            GitHubCanonicalEvidenceService canonicalEvidenceService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.launcher = launcher;
        this.runQuery = runQuery;
        this.resumePort = resumePort;
        this.outbox = outbox;
        this.properties = properties;
        this.canonicalEvidenceService = canonicalEvidenceService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public WorkflowLaunchResult register(UUID userId, GitHubUrl url) {
        Instant now = clock.instant();
        UUID sourceId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Source source = store.create(sourceId, userId, url, now);
        WorkflowLaunchResult launched = launcher.launch(command(runId, source, List.of()));
        store.attachLatestRun(userId, sourceId, launched.agentRunId(), now);
        return launched;
    }

    @Transactional
    public WorkflowLaunchResult selectRepositories(
            UUID userId,
            UUID sourceId,
            List<UUID> repositoryIds,
            long expectedSourceVersion) {
        Source source = store.findActive(userId, sourceId).orElseThrow(this::notFound);
        if (source.latestAgentRunId() == null) throw stateConflict();
        AgentRunSnapshot waiting = runQuery.findByOwner(userId, source.latestAgentRunId())
                .orElseThrow(this::notFound);
        store.replaceSelection(
                userId,
                sourceId,
                expectedSourceVersion,
                repositoryIds,
                waiting.id(),
                clock.instant());
        AgentRunSnapshot resumed = resumePort.resume(
                userId, waiting.id(), waiting.stateVersion(), clock.instant());
        return new WorkflowLaunchResult(
                resumed.id(), resumed.status(), RESOURCE_TYPE, sourceId, false);
    }

    @Transactional
    public WorkflowLaunchResult refresh(
            UUID userId, UUID sourceId, long expectedVersion) {
        Source source = store.findActive(userId, sourceId).orElseThrow(this::notFound);
        if (source.version() != expectedVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        List<Repository> repositories = store.selectedRepositories(userId, sourceId);
        if (repositories.isEmpty()) {
            throw new BusinessException(ErrorCode.GITHUB_REPOSITORY_SELECTION_REQUIRED);
        }
        UUID runId = UUID.randomUUID();
        WorkflowLaunchResult launched = launcher.launch(command(runId, source, repositories));
        store.queueRefresh(userId, sourceId, expectedVersion, launched.agentRunId(), clock.instant());
        return launched;
    }

    @Transactional
    public void delete(UUID userId, UUID sourceId, long expectedVersion) {
        Instant now = clock.instant();
        List<GitHubSourceStore.SnapshotObject> objects =
                store.snapshotObjectsForExclusiveSource(userId, sourceId);
        canonicalEvidenceService.retireSource(userId, sourceId, now);
        store.softDelete(userId, sourceId, expectedVersion, now);
        for (GitHubSourceStore.SnapshotObject object : objects) {
            outbox.enqueueSource(userId, sourceId, object.snapshotId(), object.storageKey(), now);
        }
    }

    private WorkflowLaunchCommand command(
            UUID runId, Source source, List<Repository> repositories) {
        ObjectNode input = objectMapper.createObjectNode()
                .put("githubSourceId", source.id().toString())
                .put("sourceRevision", source.sourceRevision())
                .put("sourceKind", source.sourceKind().name())
                .put("canonicalUrl", source.canonicalUrl())
                .put("retrievalPolicyVersion", properties.getRetrievalPolicyVersion())
                .put("githubApiVersion", properties.getApiVersion());
        var selected = input.putArray("selectedRepositoryIds");
        repositories.forEach(repository -> selected.add(repository.id().toString()));
        String hash = sha256(source.userId() + "|" + source.id() + "|"
                + source.sourceRevision() + "|" + source.canonicalUrl() + "|"
                + properties.getRetrievalPolicyVersion() + "|"
                + repositories.stream().map(value -> value.id().toString()).sorted().toList());
        return new WorkflowLaunchCommand(
                runId,
                source.userId(),
                WorkflowType.GITHUB_INGESTION,
                CanonicalWorkflowDefinitions.GITHUB_INGESTION_VERSION,
                hash,
                input,
                AiQualityMode.BALANCED,
                new ResourceReference(RESOURCE_TYPE, source.id(), source.canonicalUrl()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private BusinessException stateConflict() {
        return new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
    }
}
