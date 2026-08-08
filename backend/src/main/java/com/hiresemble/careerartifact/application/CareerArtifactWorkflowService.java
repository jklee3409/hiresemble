package com.hiresemble.careerartifact.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Artifact;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.GenerationRequest;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.ProfileSectionSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.LifecycleStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ProfileSection;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactObjectDeletionOutboxStore;
import com.hiresemble.careerartifact.infrastructure.CareerArtifactStore;
import com.hiresemble.document.application.port.ObjectStoragePort;
import com.hiresemble.document.application.service.DocumentPrivacyMasker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class CareerArtifactWorkflowService implements CareerArtifactWorkflowPort {

    private static final int MAX_EVIDENCE_CONTENT_CHARS = 6_000;
    private static final int MAX_PROFILE_CONTENT_CHARS = 4_000;
    private static final int MAX_CONTEXT_CHARS = 80_000;
    private static final Pattern CONTEXT_URL =
            Pattern.compile("(?i)https?://[^\\s\\\"'<>]{1,1000}");

    private final CareerArtifactStore store;
    private final ResumeDocumentRenderer resumeRenderer;
    private final PortfolioPresentationRenderer portfolioRenderer;
    private final ObjectStoragePort storage;
    private final DocumentPrivacyMasker privacyMasker;
    private final CareerArtifactObjectDeletionOutboxStore outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentMap<UUID, RenderedArtifact> rendered = new ConcurrentHashMap<>();

    public CareerArtifactWorkflowService(
            CareerArtifactStore store,
            ResumeDocumentRenderer resumeRenderer,
            PortfolioPresentationRenderer portfolioRenderer,
            ObjectStoragePort storage,
            DocumentPrivacyMasker privacyMasker,
            CareerArtifactObjectDeletionOutboxStore outbox,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.resumeRenderer = resumeRenderer;
        this.portfolioRenderer = portfolioRenderer;
        this.storage = storage;
        this.privacyMasker = privacyMasker;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public GenerationState load(AgentRunSnapshot run) {
        ArtifactType type = requireRunShape(run);
        JsonNode input = run.inputReferenceSnapshot();
        UUID artifactId = uuid(input, "careerArtifactId");
        UUID targetVersionId = uuid(input, "targetVersionId");
        long acceptedArtifactVersion = input.path("artifactVersion").asLong(-1);
        String model = text(input, "model");
        String templateKey = text(input, "templateKey");
        String templateVersion = text(input, "templateVersion");
        String profileHash = text(input, "renderProfileHash");
        if (!artifactId.equals(run.resourceId())
                || acceptedArtifactVersion < 0
                || !type.name().equals(input.path("artifactType").asText())
                || !type.templateKey().equals(templateKey)
                || !CareerArtifactTypes.TEMPLATE_VERSION.equals(templateVersion)
                || !profileHash.matches("[0-9a-f]{64}")) {
            throw requestFailure("CAREER_ARTIFACT_REQUEST_INVALID");
        }
        try {
            OpenAiChatModels.requireModel(run.workflowType(), model);
        } catch (IllegalArgumentException exception) {
            throw requestFailure("AI_MODEL_NOT_SUPPORTED");
        }
        Artifact artifact = store.find(run.userId(), artifactId).orElseThrow(this::ownerFailure);
        if (artifact.artifactType() != type || artifact.lifecycleStatus() != LifecycleStatus.ACTIVE
                || !run.id().equals(artifact.latestAgentRunId())) {
            throw ownerFailure();
        }
        Version applied = store.findVersion(run.userId(), artifactId, targetVersionId).orElse(null);
        long expectedBeforeApply = acceptedArtifactVersion + run.runAttemptNo();
        if (applied == null && artifact.version() != expectedBeforeApply) {
            throw versionFailure();
        }
        if (applied != null && (!run.id().equals(applied.agentRunId())
                || !targetVersionId.equals(artifact.currentVersionId()))) {
            throw versionFailure();
        }
        GenerationRequest request = store.generationRequest(run.userId(), run.id())
                .orElseThrow(this::ownerFailure);
        if (!artifactId.equals(request.artifactId())
                || !targetVersionId.equals(request.targetVersionId())
                || !profileHash.equals(request.renderProfileHash())) {
            throw ownerFailure();
        }
        CareerArtifactRenderProfile renderProfile;
        try {
            renderProfile = objectMapper.treeToValue(
                    request.renderProfileSnapshot(), CareerArtifactRenderProfile.class);
        } catch (Exception exception) {
            throw requestFailure("CAREER_ARTIFACT_RENDER_PROFILE_INVALID");
        }
        if (!profileHash.equals(sha256(renderProfile.canonicalDigestMaterial()))) {
            throw requestFailure("CAREER_ARTIFACT_RENDER_PROFILE_INVALID");
        }
        List<AcceptedEvidence> acceptedEvidence = acceptedEvidence(input);
        List<VerifiedEvidence> evidence = store.verifiedEvidence(
                run.userId(), acceptedEvidence.stream()
                        .map(AcceptedEvidence::experienceId).toList());
        if (!evidenceMatches(acceptedEvidence, evidence)) throw ownerFailure();
        List<AcceptedProfile> acceptedProfiles = acceptedProfiles(input);
        Set<ProfileSection> requestedSections = requestedSections(input);
        List<ProfileSectionSnapshot> profile = store.profileSnapshots(
                run.userId(), requestedSections);
        if (!profileMatches(acceptedProfiles, profile)) throw versionFailure();
        ContextMaterial context = context(run, evidence, profile);
        return new GenerationState(
                run, artifact, targetVersionId, model, templateKey, templateVersion,
                profileHash, renderProfile, evidence, profile,
                requestedSections.stream().map(Enum::name).sorted().toList(),
                context.payload(),
                context.hash(), context.included(), context.omitted(), context.omittedKinds());
    }

    @Override
    public RenderedArtifact render(GenerationState state, Object groundedContent) {
        RenderedOfficeFile file;
        if (state.artifact().artifactType() == ArtifactType.RESUME
                && groundedContent instanceof ResumeContent resume) {
            file = resumeRenderer.render(resume, state.renderProfile());
        } else if (state.artifact().artifactType() == ArtifactType.PORTFOLIO
                && groundedContent instanceof PortfolioContent portfolio) {
            file = portfolioRenderer.render(portfolio, state.renderProfile());
        } else {
            throw requestFailure("CAREER_ARTIFACT_CONTENT_TYPE_INVALID");
        }
        RenderedArtifact value = new RenderedArtifact(
                state, groundedContent, file, null, null);
        rendered.put(state.run().id(), value);
        return value;
    }

    @Override
    public RenderedArtifact validate(UUID agentRunId) {
        RenderedArtifact value = requireRendered(agentRunId);
        OfficeValidation validation = value.state().artifact().artifactType() == ArtifactType.RESUME
                ? resumeRenderer.validate(value.file().bytes())
                : portfolioRenderer.validate(value.file().bytes());
        if (!validation.valid()
                || !value.file().mimeType().equals(validation.mimeType())
                || value.file().sizeBytes() != validation.sizeBytes()
                || !value.file().checksumSha256().equals(validation.checksumSha256())) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    "CAREER_ARTIFACT_FILE_VALIDATION_FAILED",
                    "The generated Office file did not pass server validation.");
        }
        RenderedArtifact validated = new RenderedArtifact(
                value.state(), value.groundedContent(), value.file(), validation, null);
        rendered.put(agentRunId, validated);
        return validated;
    }

    @Override
    public PersistPreparation upload(UUID agentRunId) {
        RenderedArtifact value = requireRendered(agentRunId);
        if (value.validation() == null || !value.validation().valid()) {
            throw requestFailure("CAREER_ARTIFACT_FILE_NOT_VALIDATED");
        }
        GenerationState state = value.state();
        Version existing = store.findVersion(
                state.run().userId(), state.artifact().id(), state.targetVersionId())
                .orElse(null);
        if (existing != null) {
            return new PersistPreparation(
                    existing.id(), existing.versionNo(), existing.checksumSha256());
        }
        String key = CareerArtifactStore.storageKey(
                state.run().userId(), state.artifact().id(),
                state.targetVersionId(), state.artifact().artifactType());
        if (key.equals(value.uploadedStorageKey())) {
            return preparation(value);
        }
        boolean uploaded = false;
        try {
            storage.upload(
                    key, value.file().bytes(), value.file().mimeType(),
                    value.file().checksumSha256());
            uploaded = true;
            ObjectStoragePort.ObjectMetadata metadata = storage.metadata(key);
            if (metadata.size() != value.file().sizeBytes()
                    || !value.file().mimeType().equals(metadata.contentType())
                    || !value.file().checksumSha256().equals(metadata.checksumSha256())) {
                throw new IllegalStateException("CAREER_ARTIFACT_UPLOAD_METADATA_MISMATCH");
            }
            RenderedArtifact prepared = new RenderedArtifact(
                    value.state(), value.groundedContent(), value.file(),
                    value.validation(), key);
            rendered.put(agentRunId, prepared);
            return preparation(prepared);
        } catch (RuntimeException failure) {
            if (uploaded) compensateUpload(state, key, failure);
            throw failure;
        }
    }

    @Override
    @Transactional
    public Version apply(UUID agentRunId, PersistPreparation preparation) {
        RenderedArtifact value = requireRendered(agentRunId);
        GenerationState state = value.state();
        Version existing = store.findVersion(
                state.run().userId(), state.artifact().id(), state.targetVersionId())
                .orElse(null);
        if (existing != null) {
            requirePreparation(existing, preparation);
            rendered.remove(agentRunId);
            return existing;
        }
        String key = CareerArtifactStore.storageKey(
                state.run().userId(), state.artifact().id(),
                state.targetVersionId(), state.artifact().artifactType());
        if (value.validation() == null || !value.validation().valid()
                || !key.equals(value.uploadedStorageKey())
                || !state.targetVersionId().equals(preparation.versionId())
                || !value.file().checksumSha256().equals(preparation.checksumSha256())) {
            throw requestFailure("CAREER_ARTIFACT_PERSIST_PREPARATION_INVALID");
        }
        boolean transactionActive =
                TransactionSynchronizationManager.isActualTransactionActive();
        if (transactionActive) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            rendered.remove(agentRunId);
                            if (status == STATUS_ROLLED_BACK) {
                                compensateUpload(state, key, null);
                            }
                        }
                    });
        }
        try {
            try {
                OpenAiChatModels.requireModel(state.run().workflowType(), state.model());
            } catch (IllegalArgumentException exception) {
                throw requestFailure("AI_MODEL_NOT_SUPPORTED");
            }
            if (!state.artifact().artifactType().templateKey().equals(state.templateKey())
                    || !CareerArtifactTypes.TEMPLATE_VERSION.equals(state.templateVersion())) {
                throw requestFailure("CAREER_ARTIFACT_REQUEST_INVALID");
            }
            GenerationRequest currentRequest = store.generationRequest(
                            state.run().userId(), state.run().id())
                    .orElseThrow(this::ownerFailure);
            if (!state.renderProfileHash().equals(currentRequest.renderProfileHash())
                    || !objectMapper.valueToTree(state.renderProfile())
                            .equals(currentRequest.renderProfileSnapshot())) {
                throw requestFailure("CAREER_ARTIFACT_RENDER_PROFILE_INVALID");
            }
            List<ProfileSectionSnapshot> currentProfiles = store.profileSnapshots(
                    state.run().userId(), state.requestedProfileSections().stream()
                            .map(ProfileSection::valueOf)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
            if (!profileMatches(
                    state.profileSnapshots().stream()
                            .map(profile -> new AcceptedProfile(
                                    profile.section(), profile.id(), profile.version()))
                            .toList(),
                    currentProfiles)) {
                throw versionFailure();
            }
            JsonNode content = objectMapper.valueToTree(value.groundedContent());
            Version applied = store.applyVersion(
                    state.run().userId(), state.artifact().id(), state.run().id(),
                    state.targetVersionId(), state.artifact().artifactType(), state.model(),
                    state.templateKey(), state.templateVersion(), content,
                    state.artifact().artifactType() == ArtifactType.RESUME
                            ? "resume-content-v1" : "portfolio-content-v1",
                    value.file(), key, state.evidence(), state.artifact().version(),
                    clock.instant());
            requirePreparation(applied, preparation);
            if (!transactionActive) rendered.remove(agentRunId);
            return applied;
        } catch (RuntimeException failure) {
            if (!transactionActive) {
                rendered.remove(agentRunId);
                compensateUpload(state, key, failure);
            }
            throw failure;
        }
    }

    @Override
    public void discard(UUID agentRunId) {
        rendered.remove(agentRunId);
    }

    private void compensateUpload(
            GenerationState state, String storageKey, RuntimeException failure) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException cleanupFailure) {
            if (failure != null) failure.addSuppressed(cleanupFailure);
            try {
                outbox.enqueueOrphan(
                        state.run().userId(), state.artifact().id(), null,
                        storageKey, clock.instant());
            } catch (RuntimeException outboxFailure) {
                if (failure != null) failure.addSuppressed(outboxFailure);
            }
        }
    }

    private PersistPreparation preparation(RenderedArtifact value) {
        Integer current = value.state().artifact().currentVersionNo();
        return new PersistPreparation(
                value.state().targetVersionId(), current == null ? 1 : current + 1,
                value.file().checksumSha256());
    }

    private void requirePreparation(Version version, PersistPreparation preparation) {
        if (preparation == null
                || !version.id().equals(preparation.versionId())
                || version.versionNo() != preparation.versionNo()
                || !version.checksumSha256().equals(preparation.checksumSha256())) {
            throw requestFailure("CAREER_ARTIFACT_PERSIST_PREPARATION_INVALID");
        }
    }

    private RenderedArtifact requireRendered(UUID runId) {
        RenderedArtifact value = rendered.get(runId);
        if (value == null) throw requestFailure("CAREER_ARTIFACT_RENDER_STATE_MISSING");
        return value;
    }

    private ArtifactType requireRunShape(AgentRunSnapshot run) {
        if (run == null || !CareerArtifactTypes.RESOURCE_TYPE.equals(run.resourceType())
                || run.resourceId() == null) throw ownerFailure();
        if (run.workflowType() == WorkflowType.RESUME_GENERATION
                && CanonicalWorkflowDefinitions.RESUME_GENERATION_VERSION.equals(
                        run.workflowVersion())) return ArtifactType.RESUME;
        if (run.workflowType() == WorkflowType.PORTFOLIO_GENERATION
                && CanonicalWorkflowDefinitions.PORTFOLIO_GENERATION_VERSION.equals(
                        run.workflowVersion())) return ArtifactType.PORTFOLIO;
        throw requestFailure("CAREER_ARTIFACT_WORKFLOW_INVALID");
    }

    private List<AcceptedEvidence> acceptedEvidence(JsonNode input) {
        JsonNode values = input.path("selectedEvidence");
        if (!values.isArray() || values.isEmpty() || values.size() > 20) {
            throw requestFailure("CAREER_ARTIFACT_EVIDENCE_INVALID");
        }
        List<AcceptedEvidence> result = new ArrayList<>();
        values.forEach(value -> result.add(new AcceptedEvidence(
                uuid(value, "experienceItemId"),
                value.path("experienceVersion").asLong(-1),
                uuid(value, "evidenceId"),
                value.path("evidenceVersion").asLong(-1))));
        if (result.stream().anyMatch(value -> value.experienceVersion() < 0
                        || value.evidenceVersion() < 0)
                || result.stream().map(AcceptedEvidence::experienceId).distinct().count()
                        != result.size()) {
            throw requestFailure("CAREER_ARTIFACT_EVIDENCE_INVALID");
        }
        return List.copyOf(result);
    }

    private List<AcceptedProfile> acceptedProfiles(JsonNode input) {
        JsonNode values = input.path("profileSectionRefs");
        if (!values.isArray() || values.size() > 350) {
            throw requestFailure("CAREER_ARTIFACT_PROFILE_REFS_INVALID");
        }
        List<AcceptedProfile> result = new ArrayList<>();
        try {
            values.forEach(value -> result.add(new AcceptedProfile(
                    ProfileSection.valueOf(text(value, "section")).name(),
                    uuid(value, "id"), value.path("version").asLong(-1))));
        } catch (RuntimeException exception) {
            throw requestFailure("CAREER_ARTIFACT_PROFILE_REFS_INVALID");
        }
        if (result.stream().anyMatch(value -> value.version() < 0)) {
            throw requestFailure("CAREER_ARTIFACT_PROFILE_REFS_INVALID");
        }
        return List.copyOf(result);
    }

    private Set<ProfileSection> requestedSections(JsonNode input) {
        JsonNode values = input.path("includeProfileSections");
        if (!values.isArray() || values.size() > ProfileSection.values().length) {
            throw requestFailure("CAREER_ARTIFACT_PROFILE_REFS_INVALID");
        }
        LinkedHashSet<ProfileSection> result = new LinkedHashSet<>();
        try {
            values.forEach(value -> result.add(ProfileSection.valueOf(value.asText())));
        } catch (RuntimeException exception) {
            throw requestFailure("CAREER_ARTIFACT_PROFILE_REFS_INVALID");
        }
        if (result.size() != values.size()) {
            throw requestFailure("CAREER_ARTIFACT_PROFILE_REFS_INVALID");
        }
        return Set.copyOf(result);
    }

    private boolean evidenceMatches(
            List<AcceptedEvidence> accepted, List<VerifiedEvidence> current) {
        if (accepted.size() != current.size()) return false;
        return accepted.stream().allMatch(value -> current.stream().anyMatch(candidate ->
                value.experienceId().equals(candidate.experienceItemId())
                        && value.experienceVersion() == candidate.experienceVersion()
                        && value.evidenceId().equals(candidate.evidenceId())
                        && value.evidenceVersion() == candidate.evidenceVersion()));
    }

    private boolean profileMatches(
            List<AcceptedProfile> accepted, List<ProfileSectionSnapshot> current) {
        if (accepted.size() != current.size()) return false;
        return accepted.stream().allMatch(value -> current.stream().anyMatch(candidate ->
                value.section().equals(candidate.section())
                        && value.id().equals(candidate.id())
                        && value.version() == candidate.version()));
    }

    private ContextMaterial context(
            AgentRunSnapshot run,
            List<VerifiedEvidence> evidence,
            List<ProfileSectionSnapshot> profile) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode evidenceArray = payload.putArray("verifiedCanonicalExperiences");
        int remaining = MAX_CONTEXT_CHARS;
        int omitted = 0;
        List<String> omittedKinds = new ArrayList<>();
        for (VerifiedEvidence value : evidence) {
            String safeTitle = contextSafe(value.title());
            String content = contextSafe(value.content());
            int allowed = Math.min(MAX_EVIDENCE_CONTENT_CHARS, Math.max(0, remaining));
            String bounded = content.length() <= allowed ? content : content.substring(0, allowed);
            if (bounded.length() < content.length()) {
                omitted++;
                if (!omittedKinds.contains("EVIDENCE_CONTENT")) {
                    omittedKinds.add("EVIDENCE_CONTENT");
                }
            }
            remaining -= bounded.length();
            evidenceArray.addObject()
                    .put("experienceItemId", value.experienceItemId().toString())
                    .put("experienceVersion", value.experienceVersion())
                    .put("evidenceId", value.evidenceId().toString())
                    .put("evidenceVersion", value.evidenceVersion())
                    .put("category", value.category())
                    .put("title", safeTitle)
                    .put("content", bounded);
        }
        ArrayNode profileArray = payload.putArray("selectedStructuredProfileSections");
        for (ProfileSectionSnapshot value : profile) {
            String safe = contextSafe(canonicalJson(value.safeContent()));
            int allowed = Math.min(MAX_PROFILE_CONTENT_CHARS, Math.max(0, remaining));
            String bounded = safe.length() <= allowed ? safe : safe.substring(0, allowed);
            if (bounded.length() < safe.length()) {
                omitted++;
                if (!omittedKinds.contains("PROFILE_SECTION_CONTENT")) {
                    omittedKinds.add("PROFILE_SECTION_CONTENT");
                }
            }
            remaining -= bounded.length();
            profileArray.addObject()
                    .put("section", value.section())
                    .put("id", value.id().toString())
                    .put("version", value.version())
                    .put("safeStructuredContent", bounded);
        }
        String identity = evidence.stream()
                .sorted(Comparator.comparing(value -> value.experienceItemId().toString()))
                .map(value -> value.experienceItemId() + ":" + value.experienceVersion()
                        + ":" + value.evidenceId() + ":" + value.evidenceVersion()
                        + ":" + sha256(value.title() + "|" + value.content()))
                .reduce((left, right) -> left + "|" + right).orElse("")
                + "|profiles=" + profile.stream()
                        .sorted(Comparator.comparing(ProfileSectionSnapshot::section)
                                .thenComparing(value -> value.id().toString()))
                        .map(value -> value.section() + ":" + value.id() + ":"
                                + value.version() + ":" + sha256(canonicalJson(value.safeContent())))
                        .reduce((left, right) -> left + "|" + right).orElse("");
        return new ContextMaterial(
                payload,
                sha256(run.canonicalInputHash() + "|" + identity),
                evidence.size() + profile.size(),
                omitted,
                List.copyOf(omittedKinds));
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asText());
        } catch (RuntimeException exception) {
            throw requestFailure("CAREER_ARTIFACT_REQUEST_INVALID");
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw requestFailure("CAREER_ARTIFACT_REQUEST_INVALID");
        return value;
    }

    private String canonicalJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("canonical JSON unavailable", exception);
        }
    }

    private String contextSafe(String value) {
        String masked = privacyMasker.mask(value);
        return CONTEXT_URL.matcher(masked).replaceAll("[link omitted]");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                "RESOURCE_NOT_FOUND",
                "The requested Career Artifact could not be found.");
    }

    private AiExecutionException versionFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                "RESOURCE_VERSION_CONFLICT",
                "The Career Artifact input version is no longer current.");
    }

    private AiExecutionException requestFailure(String code) {
        return AiExecutionException.nonRetryable(
                FailureKind.REQUEST_VALIDATION,
                code,
                "The Career Artifact generation request is invalid.");
    }

    private record AcceptedEvidence(
            UUID experienceId,
            long experienceVersion,
            UUID evidenceId,
            long evidenceVersion) {}

    private record AcceptedProfile(String section, UUID id, long version) {}

    private record ContextMaterial(
            JsonNode payload,
            String hash,
            int included,
            int omitted,
            List<String> omittedKinds) {}
}
