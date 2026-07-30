package com.hiresemble.job.application;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.job.application.model.JobAnalysisModels.EvidenceUsage;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisPage;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSnapshot;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSummary;
import com.hiresemble.job.application.model.JobAnalysisModels.PersistJobAnalysis;
import com.hiresemble.job.application.model.JobAnalysisModels.ProfileContext;
import com.hiresemble.job.application.model.JobAnalysisModels.RequirementItem;
import com.hiresemble.job.application.model.JobAnalysisModels.RetrievedVerifiedEvidence;
import com.hiresemble.job.application.model.JobAnalysisModels.VerifiedEvidence;
import com.hiresemble.job.application.model.JobApplicationResults.RunAccepted;
import com.hiresemble.job.application.port.JobAnalysisCommandPort;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import com.hiresemble.job.application.port.JobAnalysisQueryPort;
import com.hiresemble.job.domain.JobAnalysisEvidenceUsageType;
import com.hiresemble.job.domain.JobAnalysisHashing;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.job.domain.JobFitScoringPolicy;
import com.hiresemble.job.domain.JobFitScoringPolicy.CriterionInput;
import com.hiresemble.job.domain.JobPolicy;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.domain.OutdatedReason;
import com.hiresemble.job.infrastructure.JobAnalysisAiCostProperties;
import com.hiresemble.job.infrastructure.JobAnalysisStore;
import com.hiresemble.job.infrastructure.JobStore;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisEvidence;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisProfileSnapshot;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobAnalysisApplicationService
        implements JobAnalysisQueryPort, JobAnalysisCommandPort, EvidenceReferenceQueryPort {

    public static final String WORKFLOW_VERSION = "job-analysis-v1";
    public static final String RUBRIC_VERSION = "job-fit-rubric-v1";
    public static final String RETRIEVAL_POLICY_VERSION = "verified-evidence-rag-v1";
    private static final Set<String> SORTS =
            Set.of("analysisVersion,desc", "createdAt,desc");

    private final JobStore jobStore;
    private final JobAnalysisStore analysisStore;
    private final ProfileAnalysisQueryPort profileQuery;
    private final JobAnalysisEmbeddingQueryPort embeddingQuery;
    private final WorkflowLauncher workflowLauncher;
    private final AgentRunQueryPort runQuery;
    private final IdempotencyService idempotency;
    private final JobAnalysisAiCostProperties aiCost;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobAnalysisApplicationService(
            JobStore jobStore,
            JobAnalysisStore analysisStore,
            ProfileAnalysisQueryPort profileQuery,
            JobAnalysisEmbeddingQueryPort embeddingQuery,
            WorkflowLauncher workflowLauncher,
            AgentRunQueryPort runQuery,
            IdempotencyService idempotency,
            JobAnalysisAiCostProperties aiCost,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jobStore = jobStore;
        this.analysisStore = analysisStore;
        this.profileQuery = profileQuery;
        this.embeddingQuery = embeddingQuery;
        this.workflowLauncher = workflowLauncher;
        this.runQuery = runQuery;
        this.idempotency = idempotency;
        this.aiCost = aiCost;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IdempotentResponse<RunAccepted> accept(
            UUID userId,
            UUID jobId,
            AiQualityMode qualityMode,
            boolean forceReanalyze,
            long jobVersion,
            String idempotencyKey) {
        requireSupportedQuality(qualityMode);
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/jobs/{jobId}/analysis",
                jobId,
                idempotencyKey);
        String canonicalRequest =
                qualityMode.name() + "|" + forceReanalyze + "|" + jobVersion;
        return idempotency.executePrepared(
                scope,
                canonicalRequest,
                RunAccepted.class,
                () -> prepare(userId, jobId, jobVersion, qualityMode, forceReanalyze),
                prepared -> {
                    WorkflowLaunchResult run = launch(prepared);
                    RunAccepted response = new RunAccepted(
                            run.agentRunId(),
                            run.status(),
                            run.resourceType(),
                            run.resourceId());
                    return new OriginalResponse<>(
                            202,
                            response,
                            run.resourceType(),
                            run.resourceId(),
                            run.agentRunId());
                },
                ignored -> {});
    }

    public JobAnalysisPage list(
            UUID userId, UUID jobId, int page, int size, String sort) {
        activeJob(userId, jobId);
        if (page < 0 || size < 1 || size > 100 || !SORTS.contains(sort)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        CurrentHashes current = currentHashes(userId, jobId);
        JobAnalysisPage stored = analysisStore.list(userId, jobId, page, size, sort);
        return new JobAnalysisPage(
                stored.items().stream()
                        .map(item -> project(item, current))
                        .toList(),
                stored.page(),
                stored.size(),
                stored.totalElements(),
                stored.totalPages());
    }

    public JobAnalysisDetail latest(UUID userId, UUID jobId) {
        activeJob(userId, jobId);
        CurrentHashes current = currentHashes(userId, jobId);
        JobAnalysisDetail detail = analysisStore.findLatest(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_ANALYSIS_NOT_FOUND));
        return project(detail, current);
    }

    @Transactional(readOnly = true)
    public Optional<JobAnalysisSummary> latestSummary(UUID userId, UUID jobId) {
        activeJob(userId, jobId);
        CurrentHashes current = currentHashes(userId, jobId);
        return analysisStore.findLatest(userId, jobId)
                .map(JobAnalysisDetail::summary)
                .map(summary -> project(summary, current));
    }

    @Override
    @Transactional(readOnly = true)
    public JobAnalysisSnapshot loadSnapshot(
            UUID userId,
            UUID jobId,
            long expectedJobVersion,
            AiQualityMode qualityMode,
            String expectedContextHash) {
        JobAnalysisSnapshot snapshot =
                buildSnapshot(userId, jobId, expectedJobVersion, qualityMode);
        if (expectedContextHash != null
                && !MessageDigestSupport.equals(snapshot.contextHash(), expectedContextHash)) {
            throw versionConflict("snapshot");
        }
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobAnalysisDetail> findReusable(
            UUID userId,
            UUID jobId,
            String contextHash,
            AiQualityMode qualityMode) {
        requireSupportedQuality(qualityMode);
        activeJob(userId, jobId);
        return analysisStore.findReusable(userId, jobId, contextHash, qualityMode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedVerifiedEvidence> searchVerifiedEvidence(
            UUID userId,
            UUID jobId,
            long expectedJobVersion,
            AiQualityMode qualityMode,
            String expectedContextHash,
            String queryText,
            List<Double> queryVector,
            long embeddingPolicyVersion,
            int embeddingGeneration,
            int limit) {
        JobAnalysisSnapshot snapshot = loadSnapshot(
                userId,
                jobId,
                expectedJobVersion,
                qualityMode,
                expectedContextHash);
        if (embeddingPolicyVersion != snapshot.embeddingPolicyVersion()
                || embeddingGeneration != snapshot.embeddingGeneration()
                || queryText == null
                || queryText.isBlank()
                || queryText.length() > 2000
                || limit < 1
                || limit > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Map<UUID, AnalysisEvidence> sourceById = profileQuery
                .loadAnalysisSnapshot(userId)
                .verifiedEvidence()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        AnalysisEvidence::id, value -> value));
        Map<UUID, VerifiedEvidence> allowlist = snapshot.verifiedEvidence().stream()
                .collect(java.util.stream.Collectors.toMap(
                        VerifiedEvidence::id, value -> value));
        Map<UUID, VerifiedEvidence> byChunk = snapshot.verifiedEvidence().stream()
                .filter(value -> value.sourceEntityId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        VerifiedEvidence::sourceEntityId,
                        value -> value,
                        (left, right) -> left));
        List<RetrievedVerifiedEvidence> result = new ArrayList<>();
        Set<UUID> selected = new HashSet<>();
        for (var chunk : embeddingQuery.exactCosineSearch(
                userId,
                queryVector,
                embeddingPolicyVersion,
                embeddingGeneration,
                Math.min(100, Math.max(limit, limit * 3)))) {
            VerifiedEvidence evidence = byChunk.get(chunk.chunkId());
            if (evidence == null || !selected.add(evidence.id())) {
                continue;
            }
            AnalysisEvidence source = sourceById.get(evidence.id());
            if (source == null
                    || source.verificationStatus() != EvidenceVerificationStatus.VERIFIED
                    || source.version() != evidence.version()
                    || !MessageDigestSupport.equals(
                            JobAnalysisHashing.evidenceHash(userId, source),
                            evidence.evidenceHash())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            result.add(new RetrievedVerifiedEvidence(
                    evidence,
                    source.content(),
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.maskedContent(),
                    chunk.distance()));
            if (result.size() == limit) {
                return List.copyOf(result);
            }
        }
        Set<String> terms = java.util.Arrays.stream(
                        queryText.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+"))
                .filter(term -> term.length() >= 2)
                .collect(java.util.stream.Collectors.toSet());
        sourceById.values().stream()
                .filter(value -> allowlist.containsKey(value.id()) && !selected.contains(value.id()))
                .map(value -> Map.entry(value, lexicalScore(value, terms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<AnalysisEvidence, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(entry -> entry.getKey().id()))
                .limit(limit - result.size())
                .forEach(entry -> {
                    AnalysisEvidence source = entry.getKey();
                    VerifiedEvidence evidence = allowlist.get(source.id());
                    if (source.version() != evidence.version()
                            || !MessageDigestSupport.equals(
                                    JobAnalysisHashing.evidenceHash(userId, source),
                                    evidence.evidenceHash())) {
                        throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
                    }
                    result.add(new RetrievedVerifiedEvidence(
                            evidence,
                            source.content(),
                            null,
                            source.documentId(),
                            null,
                            null));
                });
        return List.copyOf(result);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public JobAnalysisDetail persist(
            UUID userId, UUID agentRunId, PersistJobAnalysis command) {
        if (command == null || command.jobId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        AgentRunSnapshot run = requireAnalysisRun(userId, agentRunId, command.jobId());
        if (run.requestedQualityMode() != command.qualityMode()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        JobRecord locked = jobStore.lockActive(userId, command.jobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireVersion(locked, command.expectedJobVersion());
        JobAnalysisSnapshot snapshot = loadSnapshot(
                userId,
                command.jobId(),
                command.expectedJobVersion(),
                command.qualityMode(),
                command.expectedContextHash());
        requireExpectedHashes(snapshot, command);
        ValidatedAnalysis validated = validate(command, snapshot);
        return analysisStore.insert(
                snapshot,
                agentRunId,
                validated.command(),
                JobFitScoringPolicy.score(validated.criteria()),
                clock.instant());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public JobAnalysisDetail attachReusable(
            UUID userId,
            UUID agentRunId,
            UUID jobId,
            UUID analysisId,
            String expectedContextHash) {
        AgentRunSnapshot run = requireAnalysisRun(userId, agentRunId, jobId);
        JobRecord locked = jobStore.lockActive(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        JobAnalysisSnapshot snapshot = loadSnapshot(
                userId,
                jobId,
                locked.version(),
                run.requestedQualityMode(),
                expectedContextHash);
        JobAnalysisDetail reusable = analysisStore.findReusable(
                        userId, jobId, snapshot.contextHash(), run.requestedQualityMode())
                .filter(detail -> detail.summary().id().equals(analysisId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
        analysisStore.attachAnalysis(userId, agentRunId, analysisId, clock.instant());
        return reusable;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(UUID userId, UUID evidenceId) {
        return analysisStore.isEvidenceReferenced(userId, evidenceId);
    }

    private PreparedAnalysis prepare(
            UUID userId,
            UUID jobId,
            long jobVersion,
            AiQualityMode qualityMode,
            boolean forceReanalyze) {
        JobAnalysisSnapshot snapshot =
                buildSnapshot(userId, jobId, jobVersion, qualityMode);
        return new PreparedAnalysis(
                snapshot,
                forceReanalyze,
                forceReanalyze ? UUID.randomUUID().toString() : null);
    }

    private WorkflowLaunchResult launch(PreparedAnalysis prepared) {
        JobAnalysisSnapshot snapshot = prepared.snapshot();
        var input = objectMapper.createObjectNode()
                .put("jobId", snapshot.jobId().toString())
                .put("jobVersion", snapshot.jobVersion())
                .put("jobContentHash", snapshot.jobContentHash())
                .put("profileSnapshotHash", snapshot.profileSnapshotHash())
                .put("evidenceSnapshotHash", snapshot.evidenceSnapshotHash())
                .put("contextHash", snapshot.contextHash())
                .put("rubricVersion", snapshot.rubricVersion())
                .put("workflowVersion", snapshot.workflowVersion())
                .put("qualityMode", snapshot.qualityMode().name())
                .put("embeddingPolicyVersion", snapshot.embeddingPolicyVersion())
                .put("embeddingGeneration", snapshot.embeddingGeneration())
                .put("retrievalPolicyVersion", snapshot.retrievalPolicyVersion())
                .put("forceReanalyze", prepared.forceReanalyze());
        if (!prepared.forceReanalyze() && snapshot.reusableAnalysisId() != null) {
            input.put("reusableAnalysisId", snapshot.reusableAnalysisId().toString());
        }
        String canonicalInputHash = JobAnalysisHashing.sha256(
                snapshot.contextHash()
                        + "|force="
                        + prepared.forceReanalyze()
                        + "|nonce="
                        + (prepared.forceNonce() == null ? "-" : prepared.forceNonce()));
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                snapshot.userId(),
                WorkflowType.JOB_ANALYSIS,
                WORKFLOW_VERSION,
                canonicalInputHash,
                input,
                snapshot.qualityMode(),
                aiCost.estimatedCostUsd(),
                aiCost.priceVersion(),
                new ResourceReference(
                        "JOB",
                        snapshot.jobId(),
                        snapshot.positionName() == null ? snapshot.title() : snapshot.positionName())));
    }

    private JobAnalysisSnapshot buildSnapshot(
            UUID userId,
            UUID jobId,
            long expectedJobVersion,
            AiQualityMode qualityMode) {
        requireSupportedQuality(qualityMode);
        JobRecord job = activeJob(userId, jobId);
        requireVersion(job, expectedJobVersion);
        requireUsableJob(job);
        AnalysisProfileSnapshot sourceProfile = profileQuery.loadAnalysisSnapshot(userId);
        var embedding = embeddingQuery.activePolicy();
        List<AnalysisEvidence> sourceEvidence = sourceProfile.verifiedEvidence().stream()
                .filter(item -> item.verificationStatus() == EvidenceVerificationStatus.VERIFIED)
                .sorted(Comparator.comparing(AnalysisEvidence::id))
                .toList();
        String profileHash = JobAnalysisHashing.profileHash(userId, sourceProfile);
        String evidenceHash =
                JobAnalysisHashing.evidenceSnapshotHash(userId, sourceEvidence);
        String contextHash = JobAnalysisHashing.contextHash(
                userId,
                jobId,
                job.version(),
                job.contentHash(),
                profileHash,
                evidenceHash,
                RUBRIC_VERSION,
                WORKFLOW_VERSION,
                qualityMode,
                embedding.version(),
                embedding.generation(),
                RETRIEVAL_POLICY_VERSION);
        List<VerifiedEvidence> verifiedEvidence = sourceEvidence.stream()
                .map(item -> new VerifiedEvidence(
                        item.id(),
                        item.sourceType(),
                        item.sourceEntityId(),
                        item.documentId(),
                        item.evidenceCategory(),
                        item.title(),
                        item.verificationStatus(),
                        item.sourceDeleted(),
                        item.version(),
                        JobAnalysisHashing.evidenceHash(userId, item)))
                .toList();
        UUID reusableAnalysisId = analysisStore.findReusable(
                        userId, jobId, contextHash, qualityMode)
                .map(value -> value.summary().id())
                .orElse(null);
        return new JobAnalysisSnapshot(
                userId,
                jobId,
                job.version(),
                job.companyName(),
                job.title(),
                job.positionName(),
                job.roleCategory(),
                job.employmentType(),
                job.location(),
                job.descriptionText(),
                job.deadlineAt(),
                job.contentHash(),
                new ProfileContext(
                        sourceProfile.profileId(),
                        sourceProfile.version(),
                        sourceProfile.introduction(),
                        sourceProfile.desiredRoles(),
                        sourceProfile.desiredIndustries(),
                        sourceProfile.desiredLocations(),
                        sourceProfile.expectedGraduationDate()),
                verifiedEvidence,
                profileHash,
                evidenceHash,
                contextHash,
                RUBRIC_VERSION,
                WORKFLOW_VERSION,
                qualityMode,
                embedding.version(),
                embedding.generation(),
                RETRIEVAL_POLICY_VERSION,
                reusableAnalysisId);
    }

    private int lexicalScore(AnalysisEvidence evidence, Set<String> terms) {
        String searchable = (evidence.title() + "\n" + evidence.content())
                .toLowerCase(java.util.Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (searchable.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private ValidatedAnalysis validate(
            PersistJobAnalysis command, JobAnalysisSnapshot snapshot) {
        if (command.eligibility() == null
                || command.criteria() == null
                || command.criteria().isEmpty()
                || command.criteria().size() > 100) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_JOB_DATA);
        }
        validateRequirements(command.responsibilities());
        validateRequirements(command.requiredQualifications());
        validateRequirements(command.preferredQualifications());
        validateStrings(command.strengths(), 20, 1000);
        validateStrings(command.gaps(), 20, 1000);
        String summary = command.analysisSummary();
        if (summary != null) {
            summary = summary.trim();
            if (summary.isBlank() || summary.length() > 10000) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
        Set<UUID> allowedEvidence = snapshot.verifiedEvidence().stream()
                .map(VerifiedEvidence::id)
                .collect(java.util.stream.Collectors.toSet());
        List<CriterionInput> criteria = new ArrayList<>();
        for (var criterion : command.criteria()) {
            if (criterion == null
                    || criterion.matchLevel() == null
                    || criterion.category() == null
                    || criterion.evidenceIds() == null
                    || !allowedEvidence.containsAll(criterion.evidenceIds())
                    || ((criterion.matchLevel() == MatchLevel.MISSING
                                    || criterion.matchLevel() == MatchLevel.UNKNOWN)
                            && !criterion.evidenceIds().isEmpty())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            criteria.add(new CriterionInput(
                    criterion.category(),
                    criterion.criterion(),
                    criterion.matchLevel(),
                    criterion.explanation(),
                    criterion.sourceLocation(),
                    criterion.evidenceIds()));
        }
        Set<EvidenceUsage> uniqueUsages = new HashSet<>();
        for (EvidenceUsage usage : command.additionalEvidenceUsages()) {
            if (usage == null
                    || usage.usageType() == null
                    || usage.usageType() == JobAnalysisEvidenceUsageType.CRITERION_MATCH
                    || !allowedEvidence.contains(usage.evidenceId())
                    || !uniqueUsages.add(usage)) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
        }
        return new ValidatedAnalysis(
                new PersistJobAnalysis(
                        command.jobId(),
                        command.expectedJobVersion(),
                        command.expectedJobContentHash(),
                        command.expectedProfileSnapshotHash(),
                        command.expectedEvidenceSnapshotHash(),
                        command.expectedContextHash(),
                        command.qualityMode(),
                        command.eligibility(),
                        command.criteria(),
                        command.responsibilities(),
                        command.requiredQualifications(),
                        command.preferredQualifications(),
                        command.strengths(),
                        command.gaps(),
                        command.additionalEvidenceUsages(),
                        summary),
                criteria);
    }

    private void validateRequirements(List<RequirementItem> items) {
        if (items == null || items.size() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        for (RequirementItem item : items) {
            if (item == null
                    || item.category() == null
                    || item.text() == null
                    || item.text().isBlank()
                    || item.text().length() > 2000
                    || (item.sourceLocation() != null
                            && (item.sourceLocation().isBlank()
                                    || item.sourceLocation().length() > 500))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private void validateStrings(List<String> values, int maximumCount, int maximumLength) {
        if (values == null || values.size() > maximumCount) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > maximumLength) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private void requireExpectedHashes(
            JobAnalysisSnapshot snapshot, PersistJobAnalysis command) {
        if (!MessageDigestSupport.equals(
                        snapshot.jobContentHash(), command.expectedJobContentHash())
                || !MessageDigestSupport.equals(
                        snapshot.profileSnapshotHash(), command.expectedProfileSnapshotHash())
                || !MessageDigestSupport.equals(
                        snapshot.evidenceSnapshotHash(), command.expectedEvidenceSnapshotHash())
                || !MessageDigestSupport.equals(
                        snapshot.contextHash(), command.expectedContextHash())) {
            throw versionConflict("snapshot");
        }
    }

    private AgentRunSnapshot requireAnalysisRun(
            UUID userId, UUID agentRunId, UUID jobId) {
        AgentRunSnapshot run = runQuery.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (run.workflowType() != WorkflowType.JOB_ANALYSIS
                || !"JOB".equals(run.resourceType())
                || !jobId.equals(run.resourceId())
                || run.requestedQualityMode() == null
                || run.status() != AgentRunStatus.RUNNING
                || run.cancelRequestedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return run;
    }

    private CurrentHashes currentHashes(UUID userId, UUID jobId) {
        JobRecord job = activeJob(userId, jobId);
        AnalysisProfileSnapshot profile = profileQuery.loadAnalysisSnapshot(userId);
        return new CurrentHashes(
                job.contentHash(),
                JobAnalysisHashing.profileHash(userId, profile),
                JobAnalysisHashing.evidenceSnapshotHash(
                        userId,
                        profile.verifiedEvidence().stream()
                                .filter(item -> item.verificationStatus()
                                        == EvidenceVerificationStatus.VERIFIED)
                                .toList()));
    }

    private JobAnalysisSummary project(
            JobAnalysisSummary summary, CurrentHashes current) {
        List<OutdatedReason> reasons = new ArrayList<>(3);
        if (!Objects.equals(summary.jobContentHash(), current.jobContentHash())) {
            reasons.add(OutdatedReason.JOB_CONTENT_CHANGED);
        }
        if (!Objects.equals(summary.profileSnapshotHash(), current.profileSnapshotHash())) {
            reasons.add(OutdatedReason.PROFILE_CHANGED);
        }
        if (!Objects.equals(summary.evidenceSnapshotHash(), current.evidenceSnapshotHash())) {
            reasons.add(OutdatedReason.EVIDENCE_CHANGED);
        }
        return new JobAnalysisSummary(
                summary.id(),
                summary.userId(),
                summary.jobId(),
                summary.analysisVersion(),
                summary.eligibility(),
                summary.fitScore(),
                !reasons.isEmpty(),
                reasons,
                summary.createdAt(),
                summary.agentRunId(),
                summary.jobContentHash(),
                summary.profileSnapshotHash(),
                summary.evidenceSnapshotHash(),
                summary.contextHash(),
                summary.qualityMode());
    }

    private JobAnalysisDetail project(
            JobAnalysisDetail detail, CurrentHashes current) {
        return new JobAnalysisDetail(
                project(detail.summary(), current),
                detail.scoreBreakdown(),
                detail.requiredQualifications(),
                detail.preferredQualifications(),
                detail.responsibilities(),
                detail.strengths(),
                detail.gaps(),
                detail.matchedEvidenceReferences(),
                detail.analysisSummary());
    }

    private JobRecord activeJob(UUID userId, UUID jobId) {
        return jobStore.findActive(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireUsableJob(JobRecord job) {
        if (!JobPolicy.hasUsableText(job.descriptionText()) || job.contentHash() == null) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_JOB_DATA);
        }
    }

    private void requireVersion(JobRecord job, long expectedVersion) {
        if (expectedVersion < 0 || job.version() != expectedVersion) {
            throw versionConflict("jobVersion");
        }
    }

    private void requireSupportedQuality(AiQualityMode qualityMode) {
        if (qualityMode != AiQualityMode.ECONOMY
                && qualityMode != AiQualityMode.BALANCED) {
            throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
        }
    }

    private BusinessException versionConflict(String field) {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", field, "reason", "STALE"),
                null);
    }

    private record PreparedAnalysis(
            JobAnalysisSnapshot snapshot, boolean forceReanalyze, String forceNonce) {}

    private record CurrentHashes(
            String jobContentHash,
            String profileSnapshotHash,
            String evidenceSnapshotHash) {}

    private record ValidatedAnalysis(
            PersistJobAnalysis command, List<CriterionInput> criteria) {}

    private static final class MessageDigestSupport {
        private MessageDigestSupport() {}

        static boolean equals(String left, String right) {
            if (left == null || right == null) {
                return false;
            }
            try {
                return java.security.MessageDigest.isEqual(
                        java.util.HexFormat.of().parseHex(left),
                        java.util.HexFormat.of().parseHex(right));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
