package com.hiresemble.careerartifact.application;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunHistoryDeletionContributor;
import com.hiresemble.agentrun.application.port.AgentRunResourceOwnerResolver;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.careerartifact.application.CareerArtifactCommands.Detail;
import com.hiresemble.careerartifact.application.CareerArtifactCommands.GenerationInput;
import com.hiresemble.careerartifact.application.CareerArtifactCommands.PreparedGeneration;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Artifact;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Page;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.ProfileSectionSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Readiness;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.LifecycleStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ProfileSection;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactObjectDeletionOutboxStore;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactStore;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.document.application.port.ObjectStoragePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class CareerArtifactApplicationService
        implements AgentRunResourceOwnerResolver,
                ResourceCompensationPort,
                AgentRunHistoryDeletionContributor {

    private static final Logger log =
            LoggerFactory.getLogger(CareerArtifactApplicationService.class);
    private static final String CREATE_SCOPE = "/api/v1/career-artifacts";
    private static final String GENERATE_SCOPE =
            "/api/v1/career-artifacts/{id}/generations";
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);
    private static final Set<String> LIST_SORTS =
            Set.of("updatedAt,desc", "createdAt,desc");
    private static final Set<String> VERSION_SORTS =
            Set.of("versionNo,desc", "createdAt,desc");

    private final CareerArtifactStore store;
    private final CareerArtifactObjectDeletionOutboxStore outbox;
    private final CareerArtifactWorkflowPort workflow;
    private final ObjectStoragePort storage;
    private final WorkflowLauncher launcher;
    private final AgentRunQueryPort runQuery;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CareerArtifactApplicationService(
            CareerArtifactStore store,
            CareerArtifactObjectDeletionOutboxStore outbox,
            CareerArtifactWorkflowPort workflow,
            ObjectStoragePort storage,
            WorkflowLauncher launcher,
            AgentRunQueryPort runQuery,
            IdempotencyService idempotency,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.outbox = outbox;
        this.workflow = workflow;
        this.storage = storage;
        this.launcher = launcher;
        this.runQuery = runQuery;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Readiness readiness(UUID userId) {
        return store.readiness(userId);
    }

    public List<OpenAiChatModels.Model> models(ArtifactType type) {
        if (type == null) throw validation();
        return OpenAiChatModels.modelsFor(type.workflowType());
    }

    public IdempotentResponse<WorkflowLaunchResult> create(
            UUID userId, GenerationInput input, String idempotencyKey) {
        GenerationInput normalized = normalize(input, true);
        PreparedGeneration prepared = prepare(
                userId, UUID.randomUUID(), normalized, 0L);
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                CREATE_SCOPE,
                IdempotencyScope.ROOT_SCOPE_ID,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                prepared.canonicalMaterial(),
                WorkflowLaunchResult.class,
                () -> prepared,
                snapshot -> {
                    Instant now = clock.instant();
                    store.create(
                            snapshot.artifactId(), userId, snapshot.artifactType(),
                            snapshot.title(), now);
                    WorkflowLaunchResult run = launch(snapshot);
                    store.insertGenerationRequest(
                            UUID.randomUUID(), userId, snapshot.artifactId(), run.agentRunId(),
                            snapshot.targetVersionId(), tree(snapshot.renderProfile()),
                            snapshot.renderProfileHash(), now);
                    store.attachLatestRun(
                            userId, snapshot.artifactId(), run.agentRunId(), 0, now);
                    return new OriginalResponse<>(
                            202, run, CareerArtifactTypes.RESOURCE_TYPE,
                            snapshot.artifactId(), run.agentRunId());
                },
                ignored -> {});
    }

    public IdempotentResponse<WorkflowLaunchResult> regenerate(
            UUID userId,
            UUID artifactId,
            GenerationInput input,
            String idempotencyKey) {
        Artifact snapshot = activeArtifact(userId, artifactId);
        if (input == null) throw validation();
        GenerationInput normalized = normalize(new GenerationInput(
                snapshot.artifactType(),
                snapshot.title(),
                input.experienceItemIds(),
                input.model(),
                input.templateKey(),
                input.includeProfileSections(),
                input.renderProfile(),
                input.artifactVersion()), false);
        if (normalized.artifactVersion() == null) throw validation();
        PreparedGeneration prepared = prepare(
                userId, artifactId, normalized, normalized.artifactVersion());
        IdempotencyScope scope = new IdempotencyScope(
                userId, "POST", GENERATE_SCOPE, artifactId, idempotencyKey);
        return idempotency.executePrepared(
                scope,
                prepared.canonicalMaterial(),
                WorkflowLaunchResult.class,
                () -> prepared,
                generation -> {
                    Artifact locked = requireLockedActive(userId, artifactId);
                    requireVersion(locked, generation.acceptedArtifactVersion());
                    requireNoActiveGeneration(userId, artifactId);
                    requireGenerationIdentity(generation);
                    WorkflowLaunchResult run = launch(generation);
                    Instant now = clock.instant();
                    store.insertGenerationRequest(
                            UUID.randomUUID(), userId, artifactId, run.agentRunId(),
                            generation.targetVersionId(), tree(generation.renderProfile()),
                            generation.renderProfileHash(), now);
                    store.attachLatestRun(
                            userId, artifactId, run.agentRunId(),
                            generation.acceptedArtifactVersion(), now);
                    return new OriginalResponse<>(
                            202, run, CareerArtifactTypes.RESOURCE_TYPE,
                            artifactId, run.agentRunId());
                },
                ignored -> {});
    }

    @Transactional(readOnly = true)
    public Page<Artifact> list(
            UUID userId,
            ArtifactType type,
            LifecycleStatus lifecycle,
            int page,
            int size,
            String sort) {
        if (page < 0 || size < 1 || size > 100 || !LIST_SORTS.contains(sort)) {
            throw validation();
        }
        String order = switch (sort) {
            case "updatedAt,desc" -> "artifact.updated_at DESC,artifact.id DESC";
            case "createdAt,desc" -> "artifact.created_at DESC,artifact.id DESC";
            default -> throw validation();
        };
        return store.list(userId, type, lifecycle, page, size, order);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID userId, UUID artifactId) {
        Artifact artifact = artifact(userId, artifactId);
        Version version = artifact.currentVersionId() == null
                ? null
                : store.findVersion(userId, artifactId, artifact.currentVersionId()).orElseThrow();
        AgentRunSnapshot run = artifact.latestAgentRunId() == null
                ? null
                : runQuery.findByOwner(userId, artifact.latestAgentRunId()).orElse(null);
        return new Detail(artifact, version, run);
    }

    @Transactional(readOnly = true)
    public Page<Version> versions(
            UUID userId, UUID artifactId, int page, int size, String sort) {
        artifact(userId, artifactId);
        if (page < 0 || size < 1 || size > 100 || !VERSION_SORTS.contains(sort)) {
            throw validation();
        }
        String order = switch (sort) {
            case "versionNo,desc" -> "version_no DESC,id DESC";
            case "createdAt,desc" -> "created_at DESC,id DESC";
            default -> throw validation();
        };
        return store.versions(userId, artifactId, page, size, order);
    }

    @Transactional
    public Detail archive(UUID userId, UUID artifactId, long expectedVersion) {
        Artifact artifact = requireLockedActive(userId, artifactId);
        requireVersion(artifact, expectedVersion);
        requireNoActiveGeneration(userId, artifactId);
        Artifact changed = store.changeLifecycle(
                userId, artifactId, LifecycleStatus.ACTIVE,
                LifecycleStatus.ARCHIVED, expectedVersion, clock.instant());
        if (changed == null) throw versionConflict();
        return detail(userId, artifactId);
    }

    @Transactional
    public Detail unarchive(UUID userId, UUID artifactId, long expectedVersion) {
        Artifact artifact = store.lock(userId, artifactId).orElseThrow(this::notFound);
        if (artifact.lifecycleStatus() != LifecycleStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        requireVersion(artifact, expectedVersion);
        requireNoActiveGeneration(userId, artifactId);
        Artifact changed = store.changeLifecycle(
                userId, artifactId, LifecycleStatus.ARCHIVED,
                LifecycleStatus.ACTIVE, expectedVersion, clock.instant());
        if (changed == null) throw versionConflict();
        return detail(userId, artifactId);
    }

    @Transactional
    public void delete(UUID userId, UUID artifactId, long expectedVersion) {
        Artifact artifact = store.lock(userId, artifactId).orElseThrow(this::notFound);
        if (artifact.lifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.CAREER_ARTIFACT_ARCHIVED);
        }
        requireVersion(artifact, expectedVersion);
        requireNoActiveGeneration(userId, artifactId);
        Instant now = clock.instant();
        for (CareerArtifactStore.StoredObject object : store.storedObjects(userId, artifactId)) {
            outbox.enqueueArtifact(
                    userId, artifactId, object.versionId(), object.storageKey(), now);
        }
        try {
            store.softDelete(userId, artifactId, expectedVersion, now);
        } catch (IllegalStateException exception) {
            throw versionConflict();
        }
    }

    @Transactional(readOnly = true)
    public Download download(UUID userId, UUID artifactId, UUID versionId) {
        Artifact artifact = artifact(userId, artifactId);
        Version version = store.findVersion(userId, artifactId, versionId)
                .orElseThrow(this::notFound);
        ObjectStoragePort.ObjectMetadata metadata = storage.metadata(version.storageKey());
        if (metadata.size() != version.sizeBytes()
                || !version.mimeType().equals(metadata.contentType())
                || !version.checksumSha256().equals(metadata.checksumSha256())) {
            throw new BusinessException(ErrorCode.CAREER_ARTIFACT_VERSION_NOT_READY);
        }
        String filename = attachmentFilename(artifact.title(), artifact.artifactType());
        ObjectStoragePort.PresignedObject signed =
                storage.presignGet(version.storageKey(), DOWNLOAD_TTL, filename);
        return new Download(signed.uri().toString(), signed.expiresAt(), filename);
    }

    @Override
    public boolean supports(String resourceType) {
        return CareerArtifactTypes.RESOURCE_TYPE.equals(resourceType);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveOwner(UUID userId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) throw notFound();
        Artifact value = artifact(userId, resourceId);
        if (value.lifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.CAREER_ARTIFACT_ARCHIVED);
        }
    }

    @Override
    public void compensate(
            UUID userId, UUID agentRunId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        workflow.discard(agentRunId);
        CareerArtifactStore.CleanupTarget target = store.cleanupTarget(userId, agentRunId);
        if (target == null || target.targetVersionExists()) return;
        store.compensateFailedGeneration(
                userId, agentRunId, resourceId, clock.instant());
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                cleanupOrphan(userId, target);
                            } catch (RuntimeException exception) {
                                log.error(
                                        "Career Artifact orphan cleanup scheduling failed"
                                                + " userId={} artifactId={} agentRunId={}"
                                                + " code=CAREER_ARTIFACT_OBJECT_DELETE_FAILED",
                                        userId,
                                        target.artifactId(),
                                        agentRunId);
                            }
                        }
                    });
        } else {
            cleanupOrphan(userId, target);
        }
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return run != null && supports(run.resourceType());
    }

    @Override
    public void beforeDelete(AgentRunSnapshot run, Instant deletedAt) {
        compensate(run.userId(), run.id(), run.resourceType(), run.resourceId());
        store.compensateHistoryDeletion(run.userId(), run.id(), deletedAt);
    }

    private void cleanupOrphan(
            UUID userId, CareerArtifactStore.CleanupTarget target) {
        try {
            storage.delete(target.storageKey());
        } catch (RuntimeException exception) {
            outbox.enqueueOrphan(
                    userId,
                    target.artifactId(),
                    null,
                    target.storageKey(),
                    clock.instant());
        }
    }

    private PreparedGeneration prepare(
            UUID userId,
            UUID artifactId,
            GenerationInput input,
            long acceptedVersion) {
        requireModel(input.artifactType(), input.model());
        List<VerifiedEvidence> evidence = store.verifiedEvidence(
                userId, input.experienceItemIds());
        if (evidence.size() != input.experienceItemIds().size()) {
            if (store.activeOwnedExperienceCount(userId, input.experienceItemIds())
                    == input.experienceItemIds().size()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_VERIFIED_EXPERIENCE);
            }
            throw notFound();
        }
        List<ProfileSectionSnapshot> profile = store.profileSnapshots(
                userId, input.includeProfileSections());
        String profileHash = sha256(input.renderProfile().canonicalDigestMaterial());
        UUID runId = UUID.randomUUID();
        UUID targetVersionId = UUID.randomUUID();
        String canonical = canonicalMaterial(
                artifactId, input, acceptedVersion, evidence, profile, profileHash);
        return new PreparedGeneration(
                userId, artifactId, runId, targetVersionId, input.artifactType(),
                input.title(), acceptedVersion, input.model(), input.templateKey(),
                CareerArtifactTypes.TEMPLATE_VERSION, input.includeProfileSections(),
                evidence, profile, input.renderProfile(), profileHash, canonical);
    }

    private WorkflowLaunchResult launch(PreparedGeneration generation) {
        requireGenerationIdentity(generation);
        ObjectNode input = objectMapper.createObjectNode()
                .put("careerArtifactId", generation.artifactId().toString())
                .put("artifactType", generation.artifactType().name())
                .put("artifactVersion", generation.acceptedArtifactVersion())
                .put("targetVersionId", generation.targetVersionId().toString())
                .put("model", generation.model())
                .put("templateKey", generation.templateKey())
                .put("templateVersion", generation.templateVersion())
                .put("renderProfileHash", generation.renderProfileHash());
        ArrayNode evidence = input.putArray("selectedEvidence");
        generation.evidence().forEach(value -> evidence.addObject()
                .put("experienceItemId", value.experienceItemId().toString())
                .put("experienceVersion", value.experienceVersion())
                .put("evidenceId", value.evidenceId().toString())
                .put("evidenceVersion", value.evidenceVersion()));
        ArrayNode sections = input.putArray("profileSectionRefs");
        generation.profileSnapshots().forEach(value -> sections.addObject()
                .put("section", value.section())
                .put("id", value.id().toString())
                .put("version", value.version()));
        ArrayNode includedSections = input.putArray("includeProfileSections");
        generation.includeProfileSections().stream().map(Enum::name).sorted()
                .forEach(includedSections::add);
        return launcher.launch(new WorkflowLaunchCommand(
                generation.agentRunId(),
                generation.userId(),
                generation.artifactType().workflowType(),
                generation.artifactType() == ArtifactType.RESUME
                        ? CanonicalWorkflowDefinitions.RESUME_GENERATION_VERSION
                        : CanonicalWorkflowDefinitions.PORTFOLIO_GENERATION_VERSION,
                sha256(generation.canonicalMaterial()),
                input,
                null,
                new ResourceReference(
                        CareerArtifactTypes.RESOURCE_TYPE,
                        generation.artifactId(),
                        generation.title())));
    }

    private void requireGenerationIdentity(PreparedGeneration generation) {
        requireModel(generation.artifactType(), generation.model());
        if (!generation.artifactType().templateKey().equals(generation.templateKey())
                || !CareerArtifactTypes.TEMPLATE_VERSION.equals(generation.templateVersion())) {
            throw validation();
        }
        List<VerifiedEvidence> current = store.verifiedEvidence(
                generation.userId(), generation.evidence().stream()
                        .map(VerifiedEvidence::experienceItemId).toList());
        if (!sameEvidence(generation.evidence(), current)) throw notFound();
        List<ProfileSectionSnapshot> profile = store.profileSnapshots(
                generation.userId(), generation.includeProfileSections());
        if (!sameProfile(generation.profileSnapshots(), profile)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
    }

    private GenerationInput normalize(GenerationInput input, boolean create) {
        if (input == null || input.artifactType() == null || input.renderProfile() == null) {
            throw validation();
        }
        String title = normalizeText(input.title(), 120);
        if (!create && (title == null || title.isBlank())) throw validation();
        List<UUID> ids = input.experienceItemIds() == null
                ? List.of() : input.experienceItemIds().stream()
                        .sorted(Comparator.comparing(UUID::toString)).toList();
        if (ids.isEmpty() || ids.size() > 20
                || new LinkedHashSet<>(ids).size() != ids.size()) throw validation();
        Set<ProfileSection> sections = input.includeProfileSections() == null
                ? Set.of() : new LinkedHashSet<>(input.includeProfileSections());
        if (sections.size() > 7
                || input.includeProfileSections() != null
                        && sections.size() != input.includeProfileSections().size()) {
            throw validation();
        }
        String model = normalizeText(input.model(), 64);
        String template = normalizeText(input.templateKey(), 80);
        if (!input.artifactType().templateKey().equals(template)) throw validation();
        requireModel(input.artifactType(), model);
        return new GenerationInput(
                input.artifactType(), title, ids, model, template,
                Set.copyOf(sections), input.renderProfile(), input.artifactVersion());
    }

    private String canonicalMaterial(
            UUID artifactId,
            GenerationInput input,
            long acceptedVersion,
            List<VerifiedEvidence> evidence,
            List<ProfileSectionSnapshot> profile,
            String renderProfileHash) {
        String evidenceIdentity = evidence.stream()
                .sorted(Comparator.comparing(value -> value.experienceItemId().toString()))
                .map(value -> value.experienceItemId() + ":" + value.experienceVersion()
                        + ":" + value.evidenceId() + ":" + value.evidenceVersion())
                .reduce((left, right) -> left + "," + right).orElse("");
        String sectionIdentity = profile.stream()
                .sorted(Comparator.comparing(ProfileSectionSnapshot::section)
                        .thenComparing(value -> value.id().toString()))
                .map(value -> value.section() + ":" + value.id() + ":" + value.version())
                .reduce((left, right) -> left + "," + right).orElse("");
        String includes = input.includeProfileSections().stream().map(Enum::name)
                .sorted().reduce((left, right) -> left + "," + right).orElse("");
        return String.join(
                "|",
                input.artifactType().name(),
                input.title(),
                input.artifactVersion() == null ? "new" : artifactId.toString(),
                Long.toString(acceptedVersion),
                evidenceIdentity,
                input.model(),
                input.templateKey(),
                CareerArtifactTypes.TEMPLATE_VERSION,
                includes,
                sectionIdentity,
                renderProfileHash);
    }

    private boolean sameEvidence(
            List<VerifiedEvidence> accepted, List<VerifiedEvidence> current) {
        if (accepted.size() != current.size()) return false;
        return accepted.stream().allMatch(value -> current.stream().anyMatch(candidate ->
                value.experienceItemId().equals(candidate.experienceItemId())
                        && value.experienceVersion() == candidate.experienceVersion()
                        && value.evidenceId().equals(candidate.evidenceId())
                        && value.evidenceVersion() == candidate.evidenceVersion()));
    }

    private boolean sameProfile(
            List<ProfileSectionSnapshot> accepted,
            List<ProfileSectionSnapshot> current) {
        if (accepted.size() != current.size()) return false;
        return accepted.stream().allMatch(value -> current.stream().anyMatch(candidate ->
                value.section().equals(candidate.section())
                        && value.id().equals(candidate.id())
                        && value.version() == candidate.version()));
    }

    private Artifact activeArtifact(UUID userId, UUID artifactId) {
        Artifact artifact = artifact(userId, artifactId);
        if (artifact.lifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.CAREER_ARTIFACT_ARCHIVED);
        }
        return artifact;
    }

    private Artifact requireLockedActive(UUID userId, UUID artifactId) {
        Artifact artifact = store.lock(userId, artifactId).orElseThrow(this::notFound);
        if (artifact.lifecycleStatus() == LifecycleStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.CAREER_ARTIFACT_ARCHIVED);
        }
        return artifact;
    }

    private Artifact artifact(UUID userId, UUID artifactId) {
        return store.find(userId, artifactId).orElseThrow(this::notFound);
    }

    private void requireVersion(Artifact artifact, long expectedVersion) {
        if (expectedVersion < 0 || artifact.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private void requireNoActiveGeneration(UUID userId, UUID artifactId) {
        if (store.hasActiveGeneration(userId, artifactId)) {
            throw new BusinessException(ErrorCode.CAREER_ARTIFACT_GENERATION_IN_PROGRESS);
        }
    }

    private void requireModel(ArtifactType type, String model) {
        try {
            OpenAiChatModels.requireModel(type.workflowType(), model);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_SUPPORTED);
        }
    }

    private String attachmentFilename(String title, ArtifactType type) {
        String base = title.replace('\r', ' ').replace('\n', ' ')
                .replace('/', '-').replace('\\', '-').strip();
        base = base.replaceAll("[\\p{Cc}]", "");
        if (base.isBlank()) base = "career-artifact";
        String suffix = "." + type.extension();
        int maxBase = 255 - suffix.length();
        if (base.length() > maxBase) base = base.substring(0, maxBase);
        return base + suffix;
    }

    private String normalizeText(String value, int max) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isEmpty() || normalized.length() > max
                || normalized.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
            throw validation();
        }
        return normalized;
    }

    private JsonNode tree(Object value) {
        return objectMapper.valueToTree(value);
    }

    private String canonicalJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("canonical JSON unavailable", exception);
        }
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

    private BusinessException validation() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException versionConflict() {
        return new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
    }

    public record Download(String url, Instant expiresAt, String filename) {}
}
