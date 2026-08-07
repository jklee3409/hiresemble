package com.hiresemble.githubsource.application;

import com.hiresemble.agentrun.application.port.AgentRunResourceOwnerResolver;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.application.port.DocumentWorkflowQueryPort;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator.ValidationResult;
import com.hiresemble.githubsource.application.GitHubGatewayModels.Blob;
import com.hiresemble.githubsource.application.GitHubGatewayModels.ConditionalRepository;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeSnapshot;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.RawFile;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.RawRepository;
import com.hiresemble.githubsource.application.GitHubSanitizerModels.SanitizedRepository;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.ApplySummary;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.Discovery;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.FinalSummary;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.RawCapture;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.SnapshotBundle;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.SourceUnitContent;
import com.hiresemble.githubsource.domain.GitHubSourceKind;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Snapshot;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.SourceUnit;
import com.hiresemble.githubsource.domain.GitHubSourceStatus;
import com.hiresemble.githubsource.infrastructure.GitHubProperties;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotDeletionOutboxStore;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotPayloadCodec;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotPayloadCodec.StoredSnapshot;
import com.hiresemble.githubsource.infrastructure.GitHubSnapshotPayloadCodec.StoredUnit;
import com.hiresemble.githubsource.infrastructure.GitHubSourceStore;
import com.hiresemble.githubsource.infrastructure.GitHubSourceStore.SourceUnitDraft;
import com.hiresemble.profile.application.service.GitHubCanonicalEvidenceService;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GitHubSourceWorkflowService
        implements GitHubWorkflowQueryPort, GitHubWorkflowCommandPort,
                AgentRunResourceOwnerResolver, ResourceCompensationPort {

    private static final String RESOURCE_TYPE = "GITHUB_SOURCE";

    private final GitHubSourceStore store;
    private final GitHubRestGateway gateway;
    private final GitHubSourceSanitizerPort sanitizer;
    private final GitHubSnapshotStoragePort storage;
    private final GitHubSnapshotPayloadCodec codec;
    private final GitHubSnapshotDeletionOutboxStore deletionOutbox;
    private final GitHubCandidateProvenanceValidator candidateValidator;
    private final GitHubCanonicalEvidenceService canonicalEvidenceService;
    private final DocumentWorkflowQueryPort documentQueryPort;
    private final GitHubProperties properties;
    private final Clock clock;

    public GitHubSourceWorkflowService(
            GitHubSourceStore store,
            GitHubRestGateway gateway,
            GitHubSourceSanitizerPort sanitizer,
            GitHubSnapshotStoragePort storage,
            GitHubSnapshotPayloadCodec codec,
            GitHubSnapshotDeletionOutboxStore deletionOutbox,
            GitHubCandidateProvenanceValidator candidateValidator,
            GitHubCanonicalEvidenceService canonicalEvidenceService,
            DocumentWorkflowQueryPort documentQueryPort,
            GitHubProperties properties,
            Clock clock) {
        this.store = store;
        this.gateway = gateway;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.codec = codec;
        this.deletionOutbox = deletionOutbox;
        this.candidateValidator = candidateValidator;
        this.canonicalEvidenceService = canonicalEvidenceService;
        this.documentQueryPort = documentQueryPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Source source(UUID userId, UUID sourceId) {
        return store.findActive(userId, sourceId).orElseThrow(this::notFound);
    }

    @Override
    public List<Repository> selectedRepositories(UUID userId, UUID sourceId) {
        source(userId, sourceId);
        return store.selectedRepositories(userId, sourceId);
    }

    @Override
    public SnapshotBundle snapshotBundle(UUID userId, UUID sourceId, UUID repositoryId) {
        Repository repository = selectedRepositories(userId, sourceId).stream()
                .filter(value -> value.id().equals(repositoryId))
                .findFirst()
                .orElseThrow(this::notFound);
        Snapshot snapshot = store.latestSnapshot(
                        userId, repositoryId, properties.getRetrievalPolicyVersion())
                .orElseThrow(this::notFound);
        return loadBundle(userId, repository, snapshot, true);
    }

    @Override
    public EmbeddingPolicy activeEmbeddingPolicy() {
        return documentQueryPort.activeEmbeddingPolicy();
    }

    @Override
    public Source begin(UUID userId, UUID sourceId, UUID runId, Instant now) {
        Source source = source(userId, sourceId);
        if (source.status() == GitHubSourceStatus.DISCOVERING) {
            return source;
        }
        return store.markRunning(userId, sourceId, runId, now);
    }

    @Override
    public Discovery discover(UUID userId, UUID sourceId, UUID runId, Instant now) {
        Source source = source(userId, sourceId);
        if (source.sourceKind() == GitHubSourceKind.ACCOUNT) {
            var discovery = gateway.discoverAccount(source.ownerLogin());
            Source updated = store.applyAccountDiscovery(
                    userId,
                    sourceId,
                    runId,
                    discovery.accountType(),
                    discovery.repositories(),
                    discovery.truncated(),
                    now);
            return new Discovery(updated, store.selectedRepositories(userId, sourceId));
        }
        ConditionalRepository response = gateway.repository(
                source.ownerLogin(), source.repositoryName(), null);
        if (response.notModified() || response.repository() == null) {
            throw new GitHubGatewayException(GitHubGatewayException.Kind.INVALID_RESPONSE);
        }
        Source updated = store.applyRepositoryDiscovery(
                userId, sourceId, runId, response.repository(), now);
        return new Discovery(updated, store.selectedRepositories(userId, sourceId));
    }

    @Override
    public RawCapture capture(UUID userId, UUID sourceId, Repository repository) {
        requireSelected(userId, sourceId, repository.id());
        var commit = gateway.defaultBranchCommit(
                repository.ownerLogin(), repository.repositoryName(), repository.defaultBranch());
        Snapshot reusable = store.findSnapshot(
                        userId,
                        repository.id(),
                        commit.commitSha(),
                        properties.getRetrievalPolicyVersion())
                .orElse(null);
        if (reusable != null) {
            return new RawCapture(repository, reusable, null);
        }
        TreeSnapshot tree = gateway.tree(
                repository.ownerLogin(), repository.repositoryName(), commit.treeSha());
        Map<String, Long> languages = gateway.languages(
                repository.ownerLogin(), repository.repositoryName());
        List<TreeEntry> selected = sanitizer.selectCandidateFiles(tree.entries());
        List<RawFile> files = new ArrayList<>();
        boolean complete = !tree.truncated();
        for (TreeEntry entry : selected) {
            try {
                Blob blob = gateway.blob(
                        repository.ownerLogin(), repository.repositoryName(), entry.sha());
                if (!entry.sha().equals(blob.sha())) {
                    throw new GitHubGatewayException(GitHubGatewayException.Kind.INVALID_RESPONSE);
                }
                files.add(new RawFile(entry, blob.content()));
            } catch (GitHubGatewayException exception) {
                if (exception.kind() != GitHubGatewayException.Kind.NOT_FOUND
                        && exception.kind() != GitHubGatewayException.Kind.RESPONSE_LIMIT
                        && exception.kind() != GitHubGatewayException.Kind.INVALID_RESPONSE) {
                    throw exception;
                }
                complete = false;
            }
        }
        RawRepository raw = new RawRepository(
                metadata(repository),
                commit.commitSha(),
                commit.treeSha(),
                tree.truncated(),
                languages,
                files,
                complete && files.size() == selected.size(),
                selected.size());
        return new RawCapture(repository, null, raw);
    }

    @Override
    public SnapshotBundle captureAndStore(
            UUID userId, UUID sourceId, Repository repository, Instant now) {
        return sanitizeAndStore(userId, sourceId, capture(userId, sourceId, repository), now);
    }

    @Override
    public SnapshotBundle sanitizeAndStore(
            UUID userId, UUID sourceId, RawCapture capture, Instant now) {
        if (capture.reused()) {
            return loadBundle(userId, capture.repository(), capture.reusableSnapshot(), true);
        }
        SanitizedRepository sanitized = sanitizer.sanitize(capture.rawRepository());
        var encoded = codec.encode(sanitized);
        UUID snapshotId = UUID.randomUUID();
        String storageKey = "users/%s/github-sources/%s/snapshots/%s/snapshot.json.gz"
                .formatted(userId, sourceId, snapshotId);
        storage.upload(storageKey, encoded.bytes(), encoded.checksumSha256());
        try {
            List<SourceUnitDraft> units = sanitized.units().stream()
                    .map(unit -> new SourceUnitDraft(
                            UUID.randomUUID(),
                            unit.unitType(),
                            unit.repositoryPath(),
                            unit.blobSha(),
                            unit.language(),
                            unit.lineStart(),
                            unit.lineEnd(),
                            unit.contentHash(),
                            unit.excerpt()))
                    .toList();
            var inserted = store.insertSnapshot(
                    snapshotId,
                    userId,
                    capture.repository().id(),
                    sanitized.commitSha(),
                    sanitized.treeSha(),
                    properties.getApiVersion(),
                    properties.getRetrievalPolicyVersion(),
                    sanitized.selectionComplete(),
                    sanitized.upstreamTruncated(),
                    storageKey,
                    encoded.checksumSha256(),
                    encoded.uncompressedBytes(),
                    units,
                    now);
            if (!inserted.created()) {
                compensateObject(userId, storageKey, now);
            }
            return inserted.created()
                    ? bundle(capture.repository(), inserted.snapshot(), sanitized, false)
                    : loadBundle(userId, capture.repository(), inserted.snapshot(), true);
        } catch (RuntimeException exception) {
            compensateObject(userId, storageKey, now);
            throw exception;
        }
    }

    @Override
    public ValidationResult validateCandidates(
            UUID userId,
            UUID sourceId,
            long sourceRevision,
            SnapshotBundle bundle,
            List<GitHubEvidenceCandidate> candidates) {
        Map<String, GitHubCandidateProvenanceValidator.AllowedSourceUnit> allowlist =
                new LinkedHashMap<>();
        bundle.units().forEach(unit -> allowlist.put(
                unit.opaqueReference(),
                new GitHubCandidateProvenanceValidator.AllowedSourceUnit(
                        unit.unit().id(), unit.content())));
        return candidateValidator.validate(
                userId,
                sourceId,
                bundle.repository().id(),
                bundle.snapshot().id(),
                sourceRevision,
                allowlist,
                candidates);
    }

    @Override
    public ApplySummary applyCandidates(
            UUID userId,
            UUID sourceId,
            SnapshotBundle bundle,
            ValidationResult validation,
            EmbeddingPolicy embeddingPolicy,
            Instant now) {
        var result = canonicalEvidenceService.apply(
                userId,
                sourceId,
                bundle.repository().id(),
                bundle.snapshot().id(),
                validation.accepted(),
                embeddingPolicy,
                now);
        int newCount = result.experienceMatchCounts().getOrDefault(ExperienceMatchKind.NEW, 0);
        int corroborated = result.experienceMatchCounts()
                .getOrDefault(ExperienceMatchKind.SAME_EXPERIENCE, 0);
        int review = result.experienceMatchCounts()
                .getOrDefault(ExperienceMatchKind.RELATED_DIFFERENT, 0)
                + result.experienceMatchCounts().getOrDefault(ExperienceMatchKind.CONFLICT, 0);
        int commonRejected = result.rejectionReasonCounts().values().stream()
                .mapToInt(Integer::intValue).sum();
        return new ApplySummary(
                bundle.repository().id(),
                bundle.snapshot().id(),
                result,
                validation.rejectedCount() + commonRejected,
                newCount,
                corroborated,
                review);
    }

    @Override
    public FinalSummary finalizeSource(
            UUID userId,
            UUID sourceId,
            UUID runId,
            boolean partial,
            List<ApplySummary> summaries,
            int extraRejectedCount,
            Instant now) {
        int newCount = summaries.stream().mapToInt(ApplySummary::newCount).sum();
        int corroborated = summaries.stream().mapToInt(ApplySummary::corroboratedCount).sum();
        int review = summaries.stream().mapToInt(ApplySummary::reviewRequiredCount).sum();
        int rejected = extraRejectedCount
                + summaries.stream().mapToInt(ApplySummary::rejectedCount).sum();
        Source finalized = store.finalizeSource(
                userId,
                sourceId,
                runId,
                partial,
                newCount,
                corroborated,
                review,
                rejected,
                now);
        return new FinalSummary(finalized, partial);
    }

    @Override
    public void fail(UUID userId, UUID sourceId, UUID runId, Instant now) {
        store.fail(userId, sourceId, runId, now);
    }

    @Override
    public boolean supports(String resourceType) {
        return RESOURCE_TYPE.equals(resourceType);
    }

    @Override
    public void requireActiveOwner(UUID userId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        source(userId, resourceId);
    }

    @Override
    public void compensate(UUID userId, UUID agentRunId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        fail(userId, resourceId, agentRunId, clock.instant());
    }

    private SnapshotBundle loadBundle(
            UUID userId, Repository repository, Snapshot snapshot, boolean reused) {
        byte[] bytes = storage.read(snapshot.storageKey());
        if (!snapshot.checksumSha256().equals(sha256(bytes))) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        StoredSnapshot stored = codec.decode(bytes);
        if (!stored.commitSha().equals(snapshot.commitSha())) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        List<SourceUnit> units = store.sourceUnits(userId, snapshot.id());
        if (units.size() != stored.units().size()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        List<SourceUnitContent> contents = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            StoredUnit storedUnit = stored.units().get(index);
            SourceUnit unit = units.get(index);
            if (!unit.contentHash().equals(storedUnit.contentHash())) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            contents.add(new SourceUnitContent("U" + (index + 1), unit, storedUnit.content()));
        }
        return new SnapshotBundle(
                repository,
                snapshot,
                contents,
                !snapshot.selectionComplete() || snapshot.upstreamTruncated(),
                reused);
    }

    private SnapshotBundle bundle(
            Repository repository, Snapshot snapshot, SanitizedRepository sanitized, boolean reused) {
        List<SourceUnit> units = store.sourceUnits(snapshot.userId(), snapshot.id());
        if (units.size() != sanitized.units().size()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        List<SourceUnitContent> contents = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            contents.add(new SourceUnitContent(
                    "U" + (index + 1), units.get(index), sanitized.units().get(index).content()));
        }
        return new SnapshotBundle(
                repository,
                snapshot,
                contents,
                !sanitized.selectionComplete() || sanitized.upstreamTruncated(),
                reused);
    }

    private RepositoryMetadata metadata(Repository repository) {
        return new RepositoryMetadata(
                repository.externalRepositoryId(),
                repository.nodeId(),
                repository.ownerLogin(),
                repository.repositoryName(),
                repository.canonicalUrl(),
                repository.defaultBranch(),
                false,
                repository.fork(),
                repository.archived(),
                repository.description(),
                repository.topics(),
                repository.pushedAt(),
                repository.metadataEtag());
    }

    private void requireSelected(UUID userId, UUID sourceId, UUID repositoryId) {
        boolean selected = store.selectedRepositories(userId, sourceId).stream()
                .anyMatch(repository -> repository.id().equals(repositoryId));
        if (!selected) throw notFound();
    }

    private void compensateObject(UUID userId, String storageKey, Instant now) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException deletionFailure) {
            deletionOutbox.enqueueOrphan(userId, storageKey, now);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
