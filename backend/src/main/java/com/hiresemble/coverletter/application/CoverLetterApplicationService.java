package com.hiresemble.coverletter.application;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunResourceOwnerResolver;
import com.hiresemble.agentrun.application.port.AiPreferenceQueryPort;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AppliedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Detail;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceUse;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationQuestion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.HistoricalEvidence;
import com.hiresemble.coverletter.application.model.CoverLetterModels.JobContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.JobReference;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Page;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistVerification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Question;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RequirementContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RunAccepted;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Summary;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedEvidence;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterEvidenceSearchPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.TipTapCanonicalizer;
import com.hiresemble.coverletter.domain.TipTapCanonicalizer.CanonicalContent;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.coverletter.infrastructure.CoverLetterAiCostProperties;
import com.hiresemble.coverletter.infrastructure.CoverLetterStore;
import com.hiresemble.coverletter.infrastructure.CoverLetterStore.CoverRow;
import com.hiresemble.coverletter.infrastructure.CoverLetterStore.QuestionRow;
import com.hiresemble.job.application.JobAnalysisApplicationService;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail;
import com.hiresemble.job.application.model.JobAnalysisModels.RequirementItem;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.infrastructure.JobStore;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisEvidence;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class CoverLetterApplicationService
        implements CoverLetterQueryPort,
                CoverLetterCommandPort,
                AgentRunResourceOwnerResolver,
                ResourceCompensationPort,
                EvidenceReferenceQueryPort {

    public static final String GENERATION_WORKFLOW_VERSION = "cover-letter-generation-v1";
    public static final String VERIFICATION_WORKFLOW_VERSION = "cover-letter-verification-v1";
    public static final String RESOURCE_TYPE = "COVER_LETTER";
    private static final Set<String> LIST_SORTS =
            Set.of("updatedAt,desc", "createdAt,desc", "title,asc");
    private static final Set<String> VERSION_SORTS =
            Set.of("versionNo,desc", "createdAt,desc");

    private final CoverLetterStore store;
    private final JobStore jobStore;
    private final JobAnalysisApplicationService jobAnalysis;
    private final ProfileAnalysisQueryPort profileQuery;
    private final CoverLetterEvidenceSearchPort evidenceSearch;
    private final WorkflowLauncher workflowLauncher;
    private final AgentRunQueryPort runQuery;
    private final AiPreferenceQueryPort preferenceQuery;
    private final IdempotencyService idempotency;
    private final CoverLetterAiCostProperties aiCost;
    private final TipTapCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(UUID userId, UUID evidenceId) {
        return store.isEvidenceReferenced(userId, evidenceId);
    }

    public CoverLetterApplicationService(
            CoverLetterStore store,
            JobStore jobStore,
            JobAnalysisApplicationService jobAnalysis,
            ProfileAnalysisQueryPort profileQuery,
            CoverLetterEvidenceSearchPort evidenceSearch,
            WorkflowLauncher workflowLauncher,
            AgentRunQueryPort runQuery,
            AiPreferenceQueryPort preferenceQuery,
            IdempotencyService idempotency,
            CoverLetterAiCostProperties aiCost,
            TipTapCanonicalizer canonicalizer,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.jobStore = jobStore;
        this.jobAnalysis = jobAnalysis;
        this.profileQuery = profileQuery;
        this.evidenceSearch = evidenceSearch;
        this.workflowLauncher = workflowLauncher;
        this.runQuery = runQuery;
        this.preferenceQuery = preferenceQuery;
        this.idempotency = idempotency;
        this.aiCost = aiCost;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IdempotentResponse<Detail> create(
            UUID userId, UUID jobId, String title, String idempotencyKey) {
        String normalized = requiredText(title, 300);
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/jobs/{jobId}/cover-letter",
                jobId,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                normalized,
                Detail.class,
                () -> {
                    activeJob(userId, jobId);
                    return normalized;
                },
                prepared -> {
                    CoverRow created = store.create(userId, jobId, prepared, clock.instant());
                    Detail body = detail(userId, created.id());
                    return new OriginalResponse<>(201, body, RESOURCE_TYPE, created.id(), null);
                },
                ignored -> {});
    }

    @Transactional(readOnly = true)
    public Page list(
            UUID userId,
            UUID jobId,
            CoverLetterStatus status,
            String query,
            int page,
            int size,
            String sort) {
        if (page < 0
                || size < 1
                || size > 100
                || !LIST_SORTS.contains(sort)
                || (query != null && query.length() > 200)) {
            throw invalid();
        }
        if (jobId != null) {
            activeJob(userId, jobId);
        }
        String order = switch (sort) {
            case "updatedAt,desc" -> "cl.updated_at DESC,cl.id DESC";
            case "createdAt,desc" -> "cl.created_at DESC,cl.id DESC";
            case "title,asc" -> "lower(cl.title) ASC,cl.id ASC";
            default -> throw invalid();
        };
        long total = store.count(userId, jobId, status, query);
        List<Summary> items = store.list(userId, jobId, status, query, page, size, order)
                .stream()
                .map(row -> summary(userId, row))
                .toList();
        return new Page(
                items,
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID userId, UUID coverLetterId) {
        CoverRow cover = requireCover(userId, coverLetterId);
        return new Detail(
                summary(userId, cover),
                store.findQuestions(userId, coverLetterId, true).stream()
                        .map(row -> question(userId, row))
                        .toList());
    }

    @Transactional
    public Detail updateTitle(
            UUID userId, UUID coverLetterId, String title, long version) {
        CoverRow updated = store.updateTitle(
                userId, coverLetterId, version, requiredText(title, 300), clock.instant());
        return detail(userId, updated.id());
    }

    @Transactional
    public Question addQuestion(
            UUID userId,
            UUID coverLetterId,
            int order,
            String text,
            Integer maxLength,
            String memo,
            long coverLetterVersion) {
        validateQuestion(order, text, maxLength, memo);
        CoverRow cover = lockMutableCover(userId, coverLetterId, coverLetterVersion);
        if (store.activeQuestionCount(userId, coverLetterId) >= 20) {
            throw invalid();
        }
        QuestionRow created = store.insertQuestion(
                userId,
                cover.id(),
                order,
                requiredText(text, 2000),
                maxLength,
                optionalText(memo, 2000),
                clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
        return question(userId, created);
    }

    @Transactional
    public Question updateQuestion(
            UUID userId,
            UUID coverLetterId,
            UUID questionId,
            int order,
            String text,
            Integer maxLength,
            String memo,
            long questionVersion) {
        validateQuestion(order, text, maxLength, memo);
        CoverRow cover = lockMutableCover(userId, coverLetterId, null);
        QuestionRow current = store.lockQuestion(userId, questionId)
                .filter(value -> value.coverLetterId().equals(coverLetterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        QuestionRow updated = store.updateQuestion(
                userId,
                current.id(),
                questionVersion,
                order,
                requiredText(text, 2000),
                maxLength,
                optionalText(memo, 2000),
                clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
        return question(userId, updated);
    }

    @Transactional
    public void deleteQuestion(
            UUID userId,
            UUID coverLetterId,
            UUID questionId,
            long questionVersion) {
        CoverRow cover = lockMutableCover(userId, coverLetterId, null);
        QuestionRow question = store.lockQuestion(userId, questionId)
                .filter(value -> value.coverLetterId().equals(coverLetterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        store.deleteQuestion(userId, question.id(), questionVersion, clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
    }

    @Transactional
    public Detail reorderQuestions(
            UUID userId,
            UUID coverLetterId,
            List<UUID> questionIds,
            long coverLetterVersion) {
        if (questionIds == null
                || questionIds.isEmpty()
                || questionIds.size() > 20
                || questionIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(questionIds).size() != questionIds.size()) {
            throw invalid();
        }
        CoverRow cover = lockMutableCover(userId, coverLetterId, coverLetterVersion);
        Set<UUID> active = store.findQuestions(userId, coverLetterId, false).stream()
                .map(QuestionRow::id)
                .collect(Collectors.toSet());
        if (!active.equals(new HashSet<>(questionIds))) {
            throw invalid();
        }
        store.reorderQuestions(userId, cover.id(), questionIds, clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
        return detail(userId, cover.id());
    }

    @Transactional
    public AnswerVersion saveUserVersion(
            UUID userId,
            UUID questionId,
            TipTapDocumentDto document,
            UUID parentVersionId) {
        QuestionRow question = store.findQuestion(userId, questionId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        CoverRow cover = lockMutableCover(userId, question.coverLetterId(), null);
        QuestionRow locked = store.lockQuestion(userId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AnswerVersion current = store.currentAnswer(userId, questionId).orElse(null);
        requireCurrentCas(current, parentVersionId);
        CanonicalContent content = canonicalizer.canonicalize(document);
        requireMaxLength(locked, content.characterCount());
        AnswerVersion created = store.insertAnswer(
                userId,
                questionId,
                current == null ? null : current.id(),
                null,
                content.document(),
                content.plainText(),
                content.characterCount(),
                CoverLetterVersionSource.USER_EDITED,
                AnswerCreatedBy.USER,
                clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
        return created;
    }

    @Transactional
    public AnswerVersion restoreVersion(
            UUID userId,
            UUID questionId,
            UUID versionId,
            UUID expectedCurrentVersionId) {
        QuestionRow question = store.findQuestion(userId, questionId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        CoverRow cover = lockMutableCover(userId, question.coverLetterId(), null);
        QuestionRow locked = store.lockQuestion(userId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AnswerVersion target = store.findAnswer(userId, versionId)
                .filter(value -> value.questionId().equals(questionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AnswerVersion current = store.currentAnswer(userId, questionId).orElse(null);
        requireCurrentCas(current, expectedCurrentVersionId);
        requireMaxLength(locked, target.characterCount());
        AnswerVersion restored = store.insertAnswer(
                userId,
                questionId,
                current == null ? null : current.id(),
                target.id(),
                target.contentJson(),
                target.plainText(),
                target.characterCount(),
                CoverLetterVersionSource.RESTORED,
                AnswerCreatedBy.USER,
                clock.instant());
        store.copyEvidenceLinks(userId, target.id(), restored.id(), clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
        return restored;
    }

    @Transactional(readOnly = true)
    public PageAnswerVersions listVersions(
            UUID userId, UUID questionId, int page, int size, String sort) {
        store.findQuestion(userId, questionId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (page < 0 || size < 1 || size > 100 || !VERSION_SORTS.contains(sort)) {
            throw invalid();
        }
        String order = "versionNo,desc".equals(sort)
                ? "version_no DESC,id DESC"
                : "created_at DESC,id DESC";
        long total = store.countAnswers(userId, questionId);
        return new PageAnswerVersions(
                store.listAnswers(userId, questionId, page, size, order),
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public PageVerifications listVerifications(
            UUID userId, UUID answerVersionId, int page, int size, String sort) {
        store.findAnswer(userId, answerVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (page < 0
                || size < 1
                || size > 100
                || !"createdAt,desc".equals(sort)) {
            throw invalid();
        }
        long total = store.countVerifications(userId, answerVersionId);
        return new PageVerifications(
                store.listVerifications(userId, answerVersionId, page, size),
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    public IdempotentResponse<RunAccepted> acceptGeneration(
            UUID userId,
            UUID coverLetterId,
            List<UUID> questionIds,
            List<UUID> preferredEvidenceIds,
            AiQualityMode qualityMode,
            boolean avoidExperienceDuplication,
            long coverLetterVersion,
            String idempotencyKey) {
        validateGenerationInput(questionIds, preferredEvidenceIds, qualityMode);
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/cover-letters/{id}/generate",
                coverLetterId,
                idempotencyKey);
        String canonicalRequest = canonicalIds(questionIds)
                + "|preferred="
                + canonicalIds(preferredEvidenceIds)
                + "|quality="
                + qualityMode
                + "|avoid="
                + avoidExperienceDuplication
                + "|version="
                + coverLetterVersion;
        return idempotency.executePrepared(
                scope,
                canonicalRequest,
                RunAccepted.class,
                () -> loadGenerationSnapshot(
                        userId,
                        coverLetterId,
                        coverLetterVersion,
                        questionIds,
                        preferredEvidenceIds,
                        avoidExperienceDuplication,
                        qualityMode,
                        null),
                snapshot -> {
                    WorkflowLaunchResult launched = launchGeneration(snapshot);
                    RunAccepted body = new RunAccepted(
                            launched.agentRunId(),
                            launched.status(),
                            launched.resourceType(),
                            launched.resourceId());
                    return new OriginalResponse<>(
                            202,
                            body,
                            launched.resourceType(),
                            launched.resourceId(),
                            launched.agentRunId());
                },
                ignored -> {});
    }

    public IdempotentResponse<RunAccepted> acceptVerification(
            UUID userId,
            UUID answerVersionId,
            AiQualityMode qualityMode,
            String idempotencyKey) {
        requireQuality(userId, qualityMode);
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/cover-letter-answer-versions/{id}/verify",
                answerVersionId,
                idempotencyKey);
        String canonicalRequest = qualityMode.name();
        return idempotency.executePrepared(
                scope,
                canonicalRequest,
                RunAccepted.class,
                () -> new PreparedVerification(
                        UUID.randomUUID(),
                        loadVerificationSnapshot(
                                userId, answerVersionId, qualityMode, null)),
                prepared -> {
                    WorkflowLaunchResult launched = launchVerification(prepared);
                    store.attachAnswerToRun(
                            userId,
                            launched.agentRunId(),
                            answerVersionId,
                            clock.instant());
                    store.insertPendingVerification(
                            prepared.verificationId(),
                            userId,
                            answerVersionId,
                            launched.agentRunId(),
                            clock.instant());
                    RunAccepted body = new RunAccepted(
                            launched.agentRunId(),
                            launched.status(),
                            launched.resourceType(),
                            launched.resourceId());
                    return new OriginalResponse<>(
                            202,
                            body,
                            launched.resourceType(),
                            launched.resourceId(),
                            launched.agentRunId());
                },
                ignored -> {});
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationSnapshot loadGenerationSnapshot(
            UUID userId,
            UUID coverLetterId,
            long expectedCoverLetterVersion,
            List<UUID> questionIds,
            List<UUID> preferredEvidenceIds,
            boolean avoidExperienceDuplication,
            AiQualityMode qualityMode,
            String expectedSnapshotHash) {
        requireQuality(userId, qualityMode);
        CoverRow cover = requireMutableCover(userId, coverLetterId);
        if (cover.version() != expectedCoverLetterVersion) {
            throw versionConflict("coverLetterVersion");
        }
        List<QuestionRow> activeQuestions = store.findQuestions(userId, coverLetterId, false);
        if (questionIds == null
                || questionIds.isEmpty()
                || questionIds.size() > 20
                || new HashSet<>(questionIds).size() != questionIds.size()) {
            throw invalid();
        }
        Map<UUID, QuestionRow> byId = activeQuestions.stream()
                .collect(Collectors.toMap(QuestionRow::id, value -> value));
        if (!byId.keySet().containsAll(questionIds)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<GenerationQuestion> questions = questionIds.stream()
                .map(byId::get)
                .sorted(Comparator.comparingInt(QuestionRow::questionOrder)
                        .thenComparing(QuestionRow::id))
                .map(row -> {
                    AnswerVersion current = store.currentAnswer(userId, row.id()).orElse(null);
                    return new GenerationQuestion(
                            row.id(),
                            row.questionOrder(),
                            row.questionText(),
                            row.maxLength(),
                            current == null ? null : current.id(),
                            current == null ? null : current.plainText());
                })
                .toList();
        List<VerifiedEvidence> evidence = verifiedEvidence(userId);
        Set<UUID> allowed = evidence.stream()
                .map(VerifiedEvidence::id)
                .collect(Collectors.toSet());
        List<UUID> preferred = preferredEvidenceIds == null
                ? List.of()
                : List.copyOf(preferredEvidenceIds);
        if (preferred.size() > 50
                || new HashSet<>(preferred).size() != preferred.size()
                || !allowed.containsAll(preferred)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        JobContext job = jobContext(userId, cover.jobId());
        String snapshotHash = generationHash(
                cover.id(),
                cover.version(),
                job,
                questions,
                evidence,
                preferred,
                qualityMode,
                avoidExperienceDuplication);
        requireHash(expectedSnapshotHash, snapshotHash);
        return new GenerationSnapshot(
                userId,
                cover.id(),
                cover.version(),
                cover.title(),
                job,
                questions,
                evidence,
                preferred,
                avoidExperienceDuplication,
                qualityMode,
                snapshotHash);
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationSnapshot loadGenerationRetrySnapshot(
            UUID userId, UUID agentRunId, String expectedSnapshotHash) {
        AgentRunSnapshot run = runQuery.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (run.workflowType() != WorkflowType.COVER_LETTER_GENERATION
                || run.retryOfRunId() == null
                || !RESOURCE_TYPE.equals(run.resourceType())
                || run.resourceId() == null
                || run.status() != AgentRunStatus.RUNNING
                || run.cancelRequestedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        var input = run.inputReferenceSnapshot();
        long originalCoverVersion = input.path("coverLetterVersion").asLong(-1);
        List<UUID> requestedQuestionIds =
                uuidArray(input.path("questionIds"), 1, 20);
        List<UUID> requestedPreferredIds =
                uuidArray(input.path("preferredEvidenceIds"), 0, 50);
        AiQualityMode qualityMode = parseQuality(input.path("qualityMode").asText(null));
        boolean avoidDuplication = input.path("avoidExperienceDuplication").asBoolean(false);
        requireQuality(userId, qualityMode);

        CoverRow cover = requireMutableCover(userId, run.resourceId());
        int priorApplied = store.countPriorLineageAppliedAnswers(userId, agentRunId);
        int attemptApplied = store.countRunAppliedAnswers(userId, agentRunId);
        long attemptBaseVersion = originalCoverVersion + priorApplied;
        if (originalCoverVersion < 0
                || cover.version() != attemptBaseVersion + attemptApplied) {
            throw versionConflict("coverLetterVersion");
        }

        Map<UUID, QuestionRow> activeQuestions = store.findQuestions(userId, cover.id(), false)
                .stream()
                .collect(Collectors.toMap(QuestionRow::id, value -> value));
        List<GenerationQuestion> targetQuestions = new ArrayList<>();
        for (UUID questionId : requestedQuestionIds) {
            QuestionRow question = activeQuestions.get(questionId);
            if (question == null) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            Optional<AnswerVersion> linked =
                    store.findRunAnswer(userId, agentRunId, questionId);
            Optional<AnswerVersion> applied =
                    store.findRunAppliedAnswer(userId, agentRunId, questionId);
            if (linked.isPresent() && applied.isEmpty()) {
                continue;
            }
            AnswerVersion baseline;
            if (applied.isPresent()) {
                UUID parentId = applied.get().parentVersionId();
                baseline = parentId == null
                        ? null
                        : store.findAnswer(userId, parentId)
                                .orElseThrow(() -> new BusinessException(
                                        ErrorCode.RESOURCE_STATE_CONFLICT));
            } else {
                baseline = store.currentAnswer(userId, questionId).orElse(null);
            }
            targetQuestions.add(new GenerationQuestion(
                    question.id(),
                    question.questionOrder(),
                    question.questionText(),
                    question.maxLength(),
                    baseline == null ? null : baseline.id(),
                    baseline == null ? null : baseline.plainText()));
        }
        targetQuestions.sort(Comparator.comparingInt(GenerationQuestion::questionOrder)
                .thenComparing(GenerationQuestion::questionId));

        List<VerifiedEvidence> evidence = verifiedEvidence(userId);
        Set<UUID> activeEvidenceIds = evidence.stream()
                .map(VerifiedEvidence::id)
                .collect(Collectors.toSet());
        List<UUID> preferred = requestedPreferredIds.stream()
                .filter(activeEvidenceIds::contains)
                .toList();
        JobContext job = jobContext(userId, cover.jobId());
        String snapshotHash = generationHash(
                cover.id(),
                attemptBaseVersion,
                job,
                targetQuestions,
                evidence,
                preferred,
                qualityMode,
                avoidDuplication);
        requireHash(expectedSnapshotHash, snapshotHash);
        return new GenerationSnapshot(
                userId,
                cover.id(),
                attemptBaseVersion,
                cover.title(),
                job,
                targetQuestions,
                evidence,
                preferred,
                avoidDuplication,
                qualityMode,
                snapshotHash);
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationSnapshot loadVerificationSnapshot(
            UUID userId,
            UUID answerVersionId,
            AiQualityMode qualityMode,
            String expectedSnapshotHash) {
        requireQuality(userId, qualityMode);
        AnswerVersion answer = store.findAnswer(userId, answerVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        QuestionRow questionRow = store.findQuestion(userId, answer.questionId(), true)
                .filter(value -> value.deletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        CoverRow cover = requireMutableCover(userId, questionRow.coverLetterId());
        Question question = question(userId, questionRow);
        List<HistoricalEvidence> historical =
                store.historicalEvidence(userId, answerVersionId);
        List<VerifiedEvidence> current = verifiedEvidence(userId);
        JobContext job = jobContext(userId, cover.jobId());
        String snapshotHash = verificationHash(
                cover, question, answer, job, historical, current, qualityMode);
        requireHash(expectedSnapshotHash, snapshotHash);
        return new VerificationSnapshot(
                userId,
                cover.id(),
                cover.version(),
                question,
                answer,
                job,
                historical,
                current,
                qualityMode,
                snapshotHash);
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationSnapshot loadVerificationRetrySnapshot(
            UUID userId, UUID agentRunId, String expectedSnapshotHash) {
        AgentRunSnapshot run = runQuery.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (run.workflowType() != WorkflowType.COVER_LETTER_VERIFICATION
                || run.retryOfRunId() == null
                || !RESOURCE_TYPE.equals(run.resourceType())
                || run.status() != AgentRunStatus.RUNNING
                || run.cancelRequestedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        UUID answerVersionId;
        try {
            answerVersionId = UUID.fromString(
                    run.inputReferenceSnapshot().path("answerVersionId").asText());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT, exception);
        }
        if (!store.runHasAnswerLink(userId, agentRunId, answerVersionId)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        VerificationSnapshot snapshot = loadVerificationSnapshot(
                userId, answerVersionId, run.requestedQualityMode(), null);
        requireHash(expectedSnapshotHash, snapshot.snapshotHash());
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CandidateChunk> searchEvidenceCandidates(
            UUID userId, List<Double> queryVector, int limit) {
        return evidenceSearch.searchMaskedCandidates(userId, queryVector, limit);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AppliedAnswer applyGeneratedAnswer(
            UUID userId, UUID agentRunId, PersistGeneratedAnswer command) {
        if (command == null || command.coverLetterId() == null || command.questionId() == null) {
            throw invalid();
        }
        AgentRunSnapshot run = requireRun(
                userId, agentRunId, WorkflowType.COVER_LETTER_GENERATION, command.coverLetterId());
        GenerationSnapshot acceptedSnapshot = run.retryOfRunId() == null
                ? null
                : loadGenerationRetrySnapshot(
                        userId, agentRunId, command.expectedSnapshotHash());
        if (acceptedSnapshot == null) {
            requireRunHash(run, command.expectedSnapshotHash());
        }
        long acceptedCoverVersion =
                acceptedSnapshot == null
                        ? run.inputReferenceSnapshot().path("coverLetterVersion").asLong(-1)
                        : acceptedSnapshot.coverLetterVersion();
        if (acceptedCoverVersion < 0
                || acceptedCoverVersion != command.expectedCoverLetterVersion()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Optional<AnswerVersion> existing =
                store.findRunAnswer(userId, agentRunId, command.questionId());
        if (existing.isPresent()) {
            AnswerVersion answer = existing.get();
            Verification verification = store.latestVerification(userId, answer.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
            long coverVersion = requireCover(userId, command.coverLetterId()).version();
            return new AppliedAnswer(answer, verification, coverVersion);
        }
        boolean requestedQuestion = false;
        for (var value : run.inputReferenceSnapshot().path("questionIds")) {
            if (command.questionId().toString().equals(value.asText())) {
                requestedQuestion = true;
                break;
            }
        }
        if (!requestedQuestion) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        CoverRow cover = lockMutableCover(userId, command.coverLetterId(), null);
        int appliedCount = store.countRunAppliedAnswers(userId, agentRunId);
        if (cover.version() != acceptedCoverVersion + appliedCount) {
            throw versionConflict("coverLetterVersion");
        }
        QuestionRow question = store.lockQuestion(userId, command.questionId())
                .filter(value -> value.coverLetterId().equals(cover.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AnswerVersion current = store.currentAnswer(userId, question.id()).orElse(null);
        requireCurrentCas(current, command.expectedCurrentVersionId());
        CanonicalContent content = canonicalizer.canonicalize(command.contentJson());
        requireMaxLength(question, content.characterCount());
        Set<UUID> allowedEvidence = verifiedEvidence(userId).stream()
                .map(VerifiedEvidence::id)
                .collect(Collectors.toSet());
        validateEvidenceUses(command.evidenceUses(), allowedEvidence);
        validateVerification(command.factCheck(), allowedEvidence, false);
        CoverLetterVersionSource source = current == null
                ? CoverLetterVersionSource.AI_GENERATED
                : CoverLetterVersionSource.AI_REVISED;
        AnswerVersion answer = store.insertAnswer(
                userId,
                question.id(),
                current == null ? null : current.id(),
                null,
                content.document(),
                content.plainText(),
                content.characterCount(),
                source,
                AnswerCreatedBy.AI,
                clock.instant());
        store.insertEvidenceLinks(
                userId, answer.id(), command.evidenceUses(), clock.instant());
        store.attachAnswerToRun(userId, agentRunId, answer.id(), clock.instant());
        Verification verification = store.insertVerification(
                UUID.randomUUID(),
                userId,
                answer.id(),
                command.factCheck(),
                agentRunId,
                clock.instant());
        store.touchDraft(userId, cover.id(), clock.instant());
        return new AppliedAnswer(
                answer,
                verification,
                requireCover(userId, cover.id()).version());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Verification persistVerification(
            UUID userId, UUID agentRunId, PersistVerification command) {
        if (command == null
                || command.verificationId() == null
                || command.answerVersionId() == null) {
            throw invalid();
        }
        AnswerVersion answer = store.findAnswer(userId, command.answerVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        QuestionRow question = store.findQuestion(userId, answer.questionId(), true)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        AgentRunSnapshot run = requireRun(
                userId,
                agentRunId,
                WorkflowType.COVER_LETTER_VERIFICATION,
                question.coverLetterId());
        if (!store.runHasAnswerLink(userId, agentRunId, answer.id())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        VerificationSnapshot snapshot;
        if (run.retryOfRunId() == null) {
            requireRunHash(run, command.expectedSnapshotHash());
            snapshot = loadVerificationSnapshot(
                    userId,
                    answer.id(),
                    run.requestedQualityMode(),
                    command.expectedSnapshotHash());
        } else {
            snapshot = loadVerificationRetrySnapshot(
                    userId, agentRunId, command.expectedSnapshotHash());
            if (!snapshot.answerVersion().id().equals(answer.id())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
        }
        Set<UUID> allowed = new HashSet<>();
        snapshot.historicalEvidence().forEach(value -> allowed.add(value.id()));
        snapshot.currentVerifiedEvidence().forEach(value -> allowed.add(value.id()));
        validateVerification(command.result(), allowed, false);
        Verification pending = store.findVerification(userId, command.verificationId())
                .filter(value -> value.agentRunId().equals(agentRunId))
                .filter(value -> value.answerVersionId().equals(answer.id()))
                .filter(value -> value.status() == VerificationStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
        return store.completePendingVerification(
                userId, pending.id(), agentRunId, command.result());
    }

    @Override
    @Transactional
    public void failPendingVerification(UUID userId, UUID agentRunId) {
        store.failPendingVerification(userId, agentRunId);
    }

    @Transactional
    public Detail finalizeCover(
            UUID userId,
            UUID coverLetterId,
            long version,
            List<UUID> acknowledgedWarnings) {
        CoverRow cover = lockMutableCover(userId, coverLetterId, version);
        if (cover.status() != CoverLetterStatus.DRAFT) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_FINALIZABLE);
        }
        List<QuestionRow> questions = store.findQuestions(userId, coverLetterId, false);
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_FINALIZABLE);
        }
        Set<UUID> warningIds = new LinkedHashSet<>();
        for (QuestionRow question : questions) {
            AnswerVersion current = store.currentAnswer(userId, question.id())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.COVER_LETTER_NOT_FINALIZABLE));
            requireMaxLengthForFinalize(question, current.characterCount());
            Verification latest = store.latestVerification(userId, current.id())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.COVER_LETTER_NOT_FINALIZABLE));
            if (latest.status() == VerificationStatus.PENDING
                    || latest.status() == VerificationStatus.FAILED) {
                throw new BusinessException(ErrorCode.COVER_LETTER_NOT_FINALIZABLE);
            }
            if (latest.status() == VerificationStatus.WARNING) {
                warningIds.add(latest.id());
            }
        }
        Set<UUID> acknowledged = acknowledgedWarnings == null
                ? Set.of()
                : new LinkedHashSet<>(acknowledgedWarnings);
        if (acknowledgedWarnings != null
                && acknowledged.size() != acknowledgedWarnings.size()) {
            throw invalid();
        }
        if (!warningIds.equals(acknowledged)) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_FINALIZABLE);
        }
        Instant now = clock.instant();
        warningIds.forEach(id -> store.acknowledge(userId, coverLetterId, id, now));
        store.finalizeCover(userId, coverLetterId, version, now);
        return detail(userId, coverLetterId);
    }

    @Transactional
    public Detail archive(UUID userId, UUID coverLetterId, long version) {
        store.archive(userId, coverLetterId, version, clock.instant());
        return detail(userId, coverLetterId);
    }

    @Transactional
    public Detail unarchive(UUID userId, UUID coverLetterId, long version) {
        store.unarchive(userId, coverLetterId, version, clock.instant());
        return detail(userId, coverLetterId);
    }

    @Override
    public boolean supports(String resourceType) {
        return RESOURCE_TYPE.equals(resourceType);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveOwner(UUID userId, UUID resourceId) {
        requireCover(userId, resourceId);
    }

    @Override
    @Transactional
    public void compensate(
            UUID userId, UUID agentRunId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        requireCover(userId, resourceId);
        store.failPendingVerification(userId, agentRunId);
    }

    @Transactional(readOnly = true)
    public Optional<CoverLetterStatusProjection> activeStatusForJob(
            UUID userId, UUID jobId) {
        return store.findActiveForJob(userId, jobId)
                .map(value -> new CoverLetterStatusProjection(value.id(), value.status()));
    }

    private WorkflowLaunchResult launchGeneration(GenerationSnapshot snapshot) {
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", snapshot.coverLetterId().toString())
                .put("coverLetterVersion", snapshot.coverLetterVersion())
                .put("snapshotHash", snapshot.snapshotHash())
                .put("qualityMode", snapshot.qualityMode().name())
                .put("avoidExperienceDuplication", snapshot.avoidExperienceDuplication());
        var questionIds = input.putArray("questionIds");
        snapshot.questions().forEach(value -> questionIds.add(value.questionId().toString()));
        var preferred = input.putArray("preferredEvidenceIds");
        snapshot.preferredEvidenceIds().forEach(value -> preferred.add(value.toString()));
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                snapshot.userId(),
                WorkflowType.COVER_LETTER_GENERATION,
                GENERATION_WORKFLOW_VERSION,
                sha256(snapshot.snapshotHash()
                        + "|questions="
                        + canonicalIds(snapshot.questions().stream()
                                .map(GenerationQuestion::questionId)
                                .toList())),
                input,
                snapshot.qualityMode(),
                aiCost.generationEstimatedCostUsd(),
                aiCost.generationPriceVersion(),
                new ResourceReference(
                        RESOURCE_TYPE, snapshot.coverLetterId(), snapshot.title())));
    }

    private WorkflowLaunchResult launchVerification(PreparedVerification prepared) {
        VerificationSnapshot snapshot = prepared.snapshot();
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", snapshot.coverLetterId().toString())
                .put("coverLetterVersion", snapshot.coverLetterVersion())
                .put("answerVersionId", snapshot.answerVersion().id().toString())
                .put("verificationId", prepared.verificationId().toString())
                .put("snapshotHash", snapshot.snapshotHash())
                .put("qualityMode", snapshot.qualityMode().name());
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                snapshot.userId(),
                WorkflowType.COVER_LETTER_VERIFICATION,
                VERIFICATION_WORKFLOW_VERSION,
                sha256(snapshot.snapshotHash()
                        + "|answer="
                        + snapshot.answerVersion().id()),
                input,
                snapshot.qualityMode(),
                aiCost.verificationEstimatedCostUsd(),
                aiCost.verificationPriceVersion(),
                new ResourceReference(
                        RESOURCE_TYPE, snapshot.coverLetterId(), "자기소개서 검증")));
    }

    private Summary summary(UUID userId, CoverRow cover) {
        List<QuestionRow> active = store.findQuestions(userId, cover.id(), false);
        int answered = 0;
        int warnings = 0;
        Verification latestOverall = null;
        boolean baseFinalizable = !active.isEmpty();
        for (QuestionRow question : active) {
            AnswerVersion current = store.currentAnswer(userId, question.id()).orElse(null);
            if (current == null) {
                baseFinalizable = false;
                continue;
            }
            answered++;
            if (question.maxLength() != null
                    && current.characterCount() > question.maxLength()) {
                baseFinalizable = false;
            }
            Verification latest = store.latestVerification(userId, current.id()).orElse(null);
            if (latest == null
                    || latest.status() == VerificationStatus.PENDING
                    || latest.status() == VerificationStatus.FAILED) {
                baseFinalizable = false;
            }
            if (latest != null && latest.status() == VerificationStatus.WARNING) {
                warnings++;
            }
            if (latest != null
                    && (latestOverall == null
                            || latest.createdAt().isAfter(latestOverall.createdAt()))) {
                latestOverall = latest;
            }
        }
        boolean archived = cover.status() == CoverLetterStatus.ARCHIVED;
        return new Summary(
                cover.id(),
                cover.userId(),
                cover.jobId(),
                new JobReference(
                        cover.jobId(),
                        cover.companyName(),
                        cover.positionName(),
                        cover.jobTitle()),
                cover.title(),
                cover.status(),
                active.size(),
                answered,
                latestOverall == null ? null : latestOverall.status(),
                warnings,
                !archived,
                !archived,
                archived && !store.existsActiveForJob(userId, cover.jobId()),
                cover.status() == CoverLetterStatus.DRAFT && baseFinalizable,
                cover.version(),
                cover.finalizedAt(),
                cover.archivedAt(),
                cover.createdAt(),
                cover.updatedAt());
    }

    private Question question(UUID userId, QuestionRow row) {
        AnswerVersion current = store.currentAnswer(userId, row.id()).orElse(null);
        Verification latest = current == null
                ? null
                : store.latestVerification(userId, current.id()).orElse(null);
        return new Question(
                row.id(),
                row.userId(),
                row.coverLetterId(),
                row.questionOrder(),
                row.questionText(),
                row.maxLength(),
                row.memo(),
                current,
                latest,
                row.version(),
                row.createdAt(),
                row.updatedAt(),
                row.deletedAt());
    }

    private JobContext jobContext(UUID userId, UUID jobId) {
        JobRecord job = activeJob(userId, jobId);
        JobAnalysisDetail analysis = jobAnalysis.latest(userId, jobId);
        List<RequirementContext> requirements = new ArrayList<>();
        requirements.addAll(requirements(analysis.requiredQualifications()));
        requirements.addAll(requirements(analysis.preferredQualifications()));
        requirements.addAll(requirements(analysis.responsibilities()));
        return new JobContext(
                job.id(),
                job.version(),
                job.companyName(),
                job.title(),
                job.positionName(),
                job.descriptionText(),
                analysis.summary().id(),
                analysis.summary().analysisVersion(),
                analysis.summary().analysisOutdated(),
                requirements);
    }

    private List<RequirementContext> requirements(List<RequirementItem> values) {
        return values.stream()
                .map(value -> new RequirementContext(
                        value.category().name(),
                        value.text(),
                        value.required(),
                        value.sourceLocation()))
                .toList();
    }

    private List<VerifiedEvidence> verifiedEvidence(UUID userId) {
        return profileQuery.loadAnalysisSnapshot(userId).verifiedEvidence().stream()
                .filter(value -> value.verificationStatus()
                        == EvidenceVerificationStatus.VERIFIED)
                .filter(value -> !value.sourceDeleted())
                .map(this::verifiedEvidence)
                .toList();
    }

    private VerifiedEvidence verifiedEvidence(AnalysisEvidence value) {
        return new VerifiedEvidence(
                value.id(),
                value.sourceType(),
                value.sourceEntityId(),
                value.documentId(),
                value.evidenceCategory(),
                value.title(),
                value.content(),
                value.version());
    }

    private String generationHash(
            UUID coverLetterId,
            long coverLetterVersion,
            JobContext job,
            List<GenerationQuestion> questions,
            List<VerifiedEvidence> evidence,
            List<UUID> preferred,
            AiQualityMode quality,
        boolean avoidDuplication) {
        StringBuilder value = new StringBuilder()
                .append(coverLetterId).append('|').append(coverLetterVersion)
                .append('|').append(job.analysisId()).append('|').append(job.analysisVersion())
                .append('|').append(job.jobVersion()).append('|').append(job.analysisOutdated())
                .append('|').append(quality).append('|').append(avoidDuplication);
        questions.forEach(item -> value.append("|q:")
                .append(item.questionId()).append(':').append(item.questionOrder())
                .append(':').append(item.currentAnswerVersionId()));
        evidence.forEach(item -> value.append("|e:")
                .append(item.id()).append(':').append(item.version()));
        preferred.stream().sorted().forEach(item -> value.append("|p:").append(item));
        return sha256(value.toString());
    }

    private String verificationHash(
            CoverRow cover,
            Question question,
            AnswerVersion answer,
            JobContext job,
            List<HistoricalEvidence> historical,
            List<VerifiedEvidence> current,
            AiQualityMode quality) {
        StringBuilder value = new StringBuilder()
                .append(cover.id()).append('|').append(cover.version())
                .append('|').append(question.id()).append('|').append(answer.id())
                .append('|').append(job.analysisId()).append('|').append(job.analysisVersion())
                .append('|').append(job.jobVersion()).append('|').append(quality);
        historical.forEach(item -> value.append("|h:")
                .append(item.id()).append(':').append(item.currentStatus())
                .append(':').append(item.sourceDeleted()));
        current.forEach(item -> value.append("|e:")
                .append(item.id()).append(':').append(item.version()));
        return sha256(value.toString());
    }

    private void validateGenerationInput(
            List<UUID> questionIds,
            List<UUID> evidenceIds,
            AiQualityMode qualityMode) {
        if (questionIds == null
                || questionIds.isEmpty()
                || questionIds.size() > 20
                || questionIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(questionIds).size() != questionIds.size()
                || (evidenceIds != null
                        && (evidenceIds.size() > 50
                                || evidenceIds.stream().anyMatch(Objects::isNull)
                                || new HashSet<>(evidenceIds).size() != evidenceIds.size()))) {
            throw invalid();
        }
        if (qualityMode == null) {
            throw invalid();
        }
    }

    private void validateEvidenceUses(List<EvidenceUse> uses, Set<UUID> allowed) {
        if (uses == null || uses.size() > 100) {
            throw invalid();
        }
        Set<String> unique = new HashSet<>();
        for (EvidenceUse use : uses) {
            if (use == null
                    || use.evidenceId() == null
                    || !allowed.contains(use.evidenceId())
                    || use.usageType() == null
                    || use.claimText() == null
                    || use.claimText().isBlank()
                    || use.claimText().length() > 2000
                    || !unique.add(use.evidenceId() + "|" + use.usageType() + "|" + use.claimText())) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
        }
    }

    private void validateVerification(
            VerificationResult result, Set<UUID> allowedEvidence, boolean pendingAllowed) {
        if (result == null
                || result.status() == null
                || (!pendingAllowed && result.status() == VerificationStatus.PENDING)
                || result.issues().size() > 100
                || result.suggestions().size() > 20
                || result.verifiedClaims().size() > 100) {
            throw invalid();
        }
        result.issues().forEach(issue -> {
            if (issue == null
                    || issue.code() == null
                    || issue.severity() == null
                    || issue.message() == null
                    || issue.message().isBlank()
                    || issue.message().length() > 1000
                    || (issue.relatedText() != null && issue.relatedText().length() > 1000)
                    || issue.evidenceIds().size() > 20
                    || !allowedEvidence.containsAll(issue.evidenceIds())) {
                throw invalid();
            }
        });
        result.suggestions().forEach(value -> {
            if (value == null || value.isBlank() || value.length() > 1000) {
                throw invalid();
            }
        });
        result.verifiedClaims().forEach(claim -> {
            if (claim == null
                    || claim.claim() == null
                    || claim.claim().isBlank()
                    || claim.claim().length() > 2000
                    || claim.evidenceIds().size() > 20
                    || !allowedEvidence.containsAll(claim.evidenceIds())) {
                throw invalid();
            }
        });
    }

    private void requireQuality(UUID userId, AiQualityMode qualityMode) {
        if (qualityMode == null) {
            throw invalid();
        }
        if (qualityMode == AiQualityMode.HIGH_QUALITY
                && !preferenceQuery.activePreference(userId).highQualityEnabled()) {
            throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
        }
    }

    private AgentRunSnapshot requireRun(
            UUID userId, UUID runId, WorkflowType workflowType, UUID coverLetterId) {
        AgentRunSnapshot run = runQuery.findByOwner(userId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (run.workflowType() != workflowType
                || !RESOURCE_TYPE.equals(run.resourceType())
                || !coverLetterId.equals(run.resourceId())
                || run.status() != AgentRunStatus.RUNNING
                || run.cancelRequestedAt() != null
                || run.requestedQualityMode() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return run;
    }

    private void requireRunHash(AgentRunSnapshot run, String expectedHash) {
        String stored = run.inputReferenceSnapshot().path("snapshotHash").asText();
        if (!secureEquals(stored, expectedHash)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }

    private CoverRow lockMutableCover(
            UUID userId, UUID coverLetterId, Long expectedVersion) {
        CoverRow cover = store.lock(userId, coverLetterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (cover.status() == CoverLetterStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.COVER_LETTER_ARCHIVED);
        }
        if (expectedVersion != null && cover.version() != expectedVersion) {
            throw versionConflict("coverLetterVersion");
        }
        return cover;
    }

    private CoverRow requireMutableCover(UUID userId, UUID coverLetterId) {
        CoverRow cover = requireCover(userId, coverLetterId);
        if (cover.status() == CoverLetterStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.COVER_LETTER_ARCHIVED);
        }
        return cover;
    }

    private CoverRow requireCover(UUID userId, UUID coverLetterId) {
        return store.find(userId, coverLetterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private JobRecord activeJob(UUID userId, UUID jobId) {
        return jobStore.findActive(userId, jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireCurrentCas(AnswerVersion current, UUID expectedCurrentVersionId) {
        if ((current == null && expectedCurrentVersionId != null)
                || (current != null && !current.id().equals(expectedCurrentVersionId))) {
            throw versionConflict("parentVersionId");
        }
    }

    private void requireMaxLength(QuestionRow question, int count) {
        if (question.maxLength() != null && count > question.maxLength()) {
            throw invalid();
        }
    }

    private void requireMaxLengthForFinalize(QuestionRow question, int count) {
        if (question.maxLength() != null && count > question.maxLength()) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_FINALIZABLE);
        }
    }

    private void validateQuestion(
            int order, String text, Integer maxLength, String memo) {
        if (order < 1
                || order > 20
                || maxLength != null && (maxLength < 1 || maxLength > 10000)) {
            throw invalid();
        }
        requiredText(text, 2000);
        optionalText(memo, 2000);
    }

    private String requiredText(String value, int maxLength) {
        if (value == null) {
            throw invalid();
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalid();
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalid();
        }
        return normalized;
    }

    private String canonicalIds(List<UUID> values) {
        if (values == null) {
            return "";
        }
        return values.stream().sorted().map(UUID::toString).collect(Collectors.joining(","));
    }

    private List<UUID> uuidArray(JsonNode node, int minimum, int maximum) {
        if (node == null || !node.isArray() || node.size() < minimum || node.size() > maximum) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        List<UUID> values = new ArrayList<>(node.size());
        for (JsonNode value : node) {
            try {
                values.add(UUID.fromString(value.asText()));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT, exception);
            }
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return List.copyOf(values);
    }

    private AiQualityMode parseQuality(String value) {
        try {
            return AiQualityMode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT, exception);
        }
    }

    private void requireHash(String expected, String actual) {
        if (expected != null && !secureEquals(expected, actual)) {
            throw versionConflict("snapshot");
        }
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(left), HexFormat.of().parseHex(right));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private BusinessException versionConflict(String field) {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", field, "reason", "STALE"),
                null);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    public record PageAnswerVersions(
            List<AnswerVersion> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public PageAnswerVersions {
            items = List.copyOf(items);
        }
    }

    public record PageVerifications(
            List<Verification> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public PageVerifications {
            items = List.copyOf(items);
        }
    }

    public record CoverLetterStatusProjection(
            UUID coverLetterId, CoverLetterStatus status) {}

    private record PreparedVerification(
            UUID verificationId, VerificationSnapshot snapshot) {}
}
