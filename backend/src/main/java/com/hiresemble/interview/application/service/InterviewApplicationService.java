package com.hiresemble.interview.application.service;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunResourceOwnerResolver;
import com.hiresemble.agentrun.application.port.AiPreferenceQueryPort;
import com.hiresemble.agentrun.application.port.ResourceCompensationPort;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.interview.application.model.InterviewModels.AcceptedFeedback;
import com.hiresemble.interview.application.model.InterviewModels.AcceptedPreparation;
import com.hiresemble.interview.application.model.InterviewModels.AnswerVersionRow;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackContext;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackResult;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackRow;
import com.hiresemble.interview.application.model.InterviewModels.PageSlice;
import com.hiresemble.interview.application.model.InterviewModels.InterviewJobProjection;
import com.hiresemble.interview.application.model.InterviewModels.PreparationContext;
import com.hiresemble.interview.application.model.InterviewModels.PreparedPreparation;
import com.hiresemble.interview.application.model.InterviewModels.QuestionSetRow;
import com.hiresemble.interview.application.model.InterviewModels.QuestionSetView;
import com.hiresemble.interview.application.model.InterviewModels.QuestionView;
import com.hiresemble.interview.application.model.InterviewModels.GeneratedQuestion;
import com.hiresemble.interview.application.port.InterviewWorkflowCommandPort;
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.interview.infrastructure.InterviewStore;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchResult;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.ResearchRunStatus;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
import com.hiresemble.research.domain.SourceCoverage;
import com.hiresemble.research.infrastructure.ResearchStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class InterviewApplicationService
        implements AgentRunResourceOwnerResolver,
                ResourceCompensationPort,
                EvidenceReferenceQueryPort,
                InterviewWorkflowQueryPort,
                InterviewWorkflowCommandPort {

    public static final String PREPARATION_WORKFLOW_VERSION = "interview-preparation-v1";
    public static final String FEEDBACK_WORKFLOW_VERSION = "interview-answer-feedback-v1";
    public static final String QUESTION_SET_RESOURCE = "QUESTION_SET";
    public static final String ANSWER_VERSION_RESOURCE = "INTERVIEW_ANSWER_VERSION";

    private static final Set<String> QUESTION_SET_SORTS =
            Set.of("updatedAt,desc", "createdAt,desc");
    private static final Set<String> ANSWER_SORTS =
            Set.of("versionNo,desc", "createdAt,desc");

    private final InterviewStore store;
    private final ResearchStore researchStore;
    private final WorkflowLauncher workflowLauncher;
    private final AiPreferenceQueryPort preferenceQuery;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InterviewApplicationService(
            InterviewStore store,
            ResearchStore researchStore,
            WorkflowLauncher workflowLauncher,
            AiPreferenceQueryPort preferenceQuery,
            IdempotencyService idempotency,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.researchStore = researchStore;
        this.workflowLauncher = workflowLauncher;
        this.preferenceQuery = preferenceQuery;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IdempotentResponse<AcceptedPreparation> prepare(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            ResearchQuality researchQuality,
            AiQualityMode qualityMode,
            List<InterviewQuestionType> questionTypes,
            int questionCount,
            String idempotencyKey) {
        List<InterviewQuestionType> normalizedTypes =
                validatePreparation(researchQuality, qualityMode, questionTypes, questionCount);
        String canonicalRequest = coverLetterId
                + "|research="
                + researchQuality
                + "|quality="
                + qualityMode
                + "|types="
                + normalizedTypes.stream().map(Enum::name).sorted().toList()
                + "|count="
                + questionCount;
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/jobs/{jobId}/interview-preparations",
                jobId,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                canonicalRequest,
                AcceptedPreparation.class,
                () -> prepareSnapshot(
                        userId,
                        jobId,
                        coverLetterId,
                        researchQuality,
                        qualityMode,
                        normalizedTypes,
                        questionCount,
                        null),
                prepared -> {
                    Instant now = clock.instant();
                    researchStore.createQueued(
                            prepared.researchRunId(),
                            userId,
                            jobId,
                            coverLetterId,
                            prepared.retryOfResearchRunId(),
                            researchQuality,
                            prepared.agentRunId(),
                            now);
                    store.createQuestionSet(
                            prepared.questionSetId(),
                            userId,
                            jobId,
                            coverLetterId,
                            prepared.researchRunId(),
                            questionSetTitle(prepared.context()),
                            generationConfig(prepared),
                            prepared.agentRunId(),
                            now);
                    WorkflowLaunchResult launched = launchPreparation(prepared);
                    researchStore.attachSecondaryRunLink(
                            userId, launched.agentRunId(), prepared.researchRunId(), now);
                    AcceptedPreparation body = new AcceptedPreparation(
                            prepared.questionSetId(),
                            prepared.researchRunId(),
                            launched.agentRunId());
                    return new OriginalResponse<>(
                            202,
                            body,
                            QUESTION_SET_RESOURCE,
                            prepared.questionSetId(),
                            launched.agentRunId());
                },
                ignored -> {});
    }

    @Transactional(readOnly = true)
    public PageSlice<QuestionSetRow> listQuestionSets(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            String query,
            SourceCoverage sourceCoverage,
            ResearchRunStatus researchStatus,
            int page,
            int size,
            String sort) {
        if (page < 0
                || size < 1
                || size > 100
                || !QUESTION_SET_SORTS.contains(sort)
                || (query != null && query.length() > 200)) {
            throw invalid();
        }
        if (jobId != null && !store.activeJobExists(userId, jobId)) {
            throw notFound();
        }
        if (coverLetterId != null && !store.coverExists(userId, coverLetterId)) {
            throw notFound();
        }
        String order = "updatedAt,desc".equals(sort)
                ? "question_set.updated_at DESC,question_set.id DESC"
                : "question_set.created_at DESC,question_set.id DESC";
        String normalizedQuery =
                query == null || query.isBlank() ? null : query.strip();
        return store.listQuestionSets(
                userId,
                jobId,
                coverLetterId,
                normalizedQuery,
                sourceCoverage,
                researchStatus == null ? null : researchStatus.name(),
                page,
                size,
                order);
    }

    @Transactional(readOnly = true)
    public InterviewJobProjection projectionForJob(UUID userId, UUID jobId) {
        return store.projectionForJob(userId, jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public PreparationContext loadPreparationContext(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            String expectedContextHash) {
        PreparationContext context = store.loadPreparationContext(userId, jobId, coverLetterId)
                .orElseThrow(this::notFound);
        if (expectedContextHash == null
                || !secureEquals(expectedContextHash, contextHash(context))) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return context;
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackContext loadFeedbackContext(
            UUID userId, UUID answerVersionId, String expectedContextHash) {
        FeedbackContext context = store.loadFeedbackContext(userId, answerVersionId)
                .orElseThrow(this::notFound);
        if (expectedContextHash == null
                || !secureEquals(expectedContextHash, feedbackContextHash(context))) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return context;
    }

    @Override
    @Transactional(readOnly = true)
    public ResearchRunRow researchRun(UUID userId, UUID researchRunId) {
        return researchStore.findRun(userId, researchRunId).orElseThrow(this::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResearchSourceRow> researchSources(UUID userId, UUID researchRunId) {
        researchRun(userId, researchRunId);
        return researchStore.allSources(userId, researchRunId);
    }

    @Override
    @Transactional
    public void markPreparationRunning(UUID userId, UUID researchRunId) {
        researchRun(userId, researchRunId);
        researchStore.markRunning(userId, researchRunId, clock.instant());
    }

    @Override
    @Transactional
    public void persistPreparation(
            UUID userId,
            UUID agentRunId,
            UUID researchRunId,
            UUID questionSetId,
            int expectedQuestionCount,
            ResearchResult research,
            List<GeneratedQuestion> questions) {
        ResearchRunRow run = researchRun(userId, researchRunId);
        QuestionSetRow questionSet =
                store.findQuestionSet(userId, questionSetId).orElseThrow(this::notFound);
        if (!run.agentRunId().equals(agentRunId)
                || !questionSet.agentRunId().equals(agentRunId)
                || !questionSet.researchRunId().equals(researchRunId)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        validateResearchResult(research);
        validateGeneratedQuestions(
                userId,
                expectedQuestionCount,
                questions,
                research.sources().stream()
                        .map(source -> source.id())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        if (run.status() == ResearchRunStatus.SUCCEEDED) {
            List<UUID> existing = store.questionIds(userId, questionSetId);
            List<UUID> requested = questions.stream()
                    .sorted(Comparator.comparingInt(GeneratedQuestion::questionOrder))
                    .map(GeneratedQuestion::id)
                    .toList();
            if (existing.equals(requested)) {
                return;
            }
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        researchStore.persistResult(userId, researchRunId, research, now);
        store.persistQuestions(userId, questionSetId, questions, now);
    }

    @Override
    @Transactional
    public FeedbackRow persistFeedback(
            UUID userId,
            UUID agentRunId,
            UUID answerVersionId,
            FeedbackResult feedback) {
        store.findAnswer(userId, answerVersionId).orElseThrow(this::notFound);
        validateFeedback(feedback);
        return store.persistFeedback(
                UUID.nameUUIDFromBytes(
                        ("interview-feedback|" + agentRunId)
                                .getBytes(StandardCharsets.UTF_8)),
                userId,
                answerVersionId,
                agentRunId,
                feedback,
                clock.instant());
    }

    @Override
    @Transactional
    public void failPreparation(
            UUID userId,
            UUID researchRunId,
            String safeErrorCode,
            boolean retryable) {
        researchStore.fail(
                userId,
                researchRunId,
                safeErrorCode == null || safeErrorCode.isBlank()
                        ? ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.code()
                        : safeErrorCode,
                retryable,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public QuestionSetView questionSet(UUID userId, UUID questionSetId) {
        QuestionSetRow summary =
                store.findQuestionSet(userId, questionSetId).orElseThrow(this::notFound);
        ResearchRunRow research = researchStore
                .findRun(userId, summary.researchRunId())
                .orElseThrow(this::notFound);
        List<QuestionView> questions = store.listQuestions(userId, questionSetId).stream()
                .map(question -> {
                    AnswerVersionRow current =
                            store.currentAnswer(userId, question.id()).orElse(null);
                    FeedbackRow feedback = current == null
                            ? null
                            : store.latestFeedback(userId, current.id()).orElse(null);
                    return new QuestionView(
                            question,
                            store.evidenceRefs(userId, question.id()),
                            store.sourceRefs(userId, question.id()),
                            current,
                            feedback);
                })
                .toList();
        return new QuestionSetView(summary, research, questions);
    }

    @Transactional(readOnly = true)
    public QuestionView question(UUID userId, UUID questionId) {
        var question = store.findQuestion(userId, questionId).orElseThrow(this::notFound);
        AnswerVersionRow current = store.currentAnswer(userId, questionId).orElse(null);
        FeedbackRow feedback =
                current == null ? null : store.latestFeedback(userId, current.id()).orElse(null);
        return new QuestionView(
                question,
                store.evidenceRefs(userId, questionId),
                store.sourceRefs(userId, questionId),
                current,
                feedback);
    }

    @Transactional(readOnly = true)
    public PageSlice<AnswerVersionRow> answerVersions(
            UUID userId, UUID questionId, int page, int size, String sort) {
        store.findQuestion(userId, questionId).orElseThrow(this::notFound);
        if (page < 0 || size < 1 || size > 100 || !ANSWER_SORTS.contains(sort)) {
            throw invalid();
        }
        String order = "versionNo,desc".equals(sort)
                ? "version_no DESC,id DESC"
                : "created_at DESC,id DESC";
        return store.listAnswers(userId, questionId, page, size, order);
    }

    @Transactional
    public AnswerVersionRow saveAnswer(
            UUID userId, UUID questionId, String content, UUID parentVersionId) {
        if (content == null
                || content.isBlank()
                || content.length() > 20000
                || content.indexOf('\0') >= 0) {
            throw invalid();
        }
        return store.insertAnswer(userId, questionId, parentVersionId, content, clock.instant());
    }

    public IdempotentResponse<AcceptedFeedback> requestFeedback(
            UUID userId,
            UUID answerVersionId,
            AiQualityMode qualityMode,
            String idempotencyKey) {
        store.findAnswer(userId, answerVersionId).orElseThrow(this::notFound);
        requireFeedbackQuality(userId, qualityMode);
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/interview-answer-versions/{id}/feedback",
                answerVersionId,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                qualityMode.name(),
                AcceptedFeedback.class,
                () -> {
                    FeedbackContext context = store.loadFeedbackContext(userId, answerVersionId)
                            .orElseThrow(this::notFound);
                    return new PreparedFeedback(UUID.randomUUID(), context, qualityMode);
                },
                prepared -> {
                    WorkflowLaunchResult launched = launchFeedback(prepared);
                    AcceptedFeedback body = new AcceptedFeedback(
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

    @Transactional(readOnly = true)
    public PageSlice<FeedbackRow> feedbacks(
            UUID userId, UUID answerVersionId, int page, int size, String sort) {
        store.findAnswer(userId, answerVersionId).orElseThrow(this::notFound);
        if (page < 0
                || size < 1
                || size > 100
                || !"createdAt,desc".equals(sort)) {
            throw invalid();
        }
        return store.listFeedbacks(userId, answerVersionId, page, size);
    }

    public PreparedPreparation prepareSnapshot(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            ResearchQuality researchQuality,
            AiQualityMode qualityMode,
            List<InterviewQuestionType> questionTypes,
            int questionCount,
            UUID retryOfResearchRunId) {
        PreparationContext context = store.loadPreparationContext(userId, jobId, coverLetterId)
                .orElseThrow(this::notFound);
        if (context.jobAnalysisId() == null) {
            throw new BusinessException(ErrorCode.JOB_ANALYSIS_NOT_FOUND);
        }
        if (context.coverLetterStatus() == CoverLetterStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.COVER_LETTER_ARCHIVED);
        }
        if (context.coverAnswers().isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return new PreparedPreparation(
                userId,
                jobId,
                coverLetterId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                researchQuality,
                qualityMode,
                questionTypes,
                questionCount,
                context,
                retryOfResearchRunId);
    }

    @Override
    public boolean supports(String resourceType) {
        return QUESTION_SET_RESOURCE.equals(resourceType)
                || ANSWER_VERSION_RESOURCE.equals(resourceType);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveOwner(UUID userId, String resourceType, UUID resourceId) {
        boolean exists = switch (resourceType) {
            case QUESTION_SET_RESOURCE -> store.questionSetExists(userId, resourceId);
            case ANSWER_VERSION_RESOURCE -> store.findAnswer(userId, resourceId).isPresent();
            default -> false;
        };
        if (!exists) {
            throw notFound();
        }
    }

    @Override
    @Transactional
    public void compensate(
            UUID userId, UUID agentRunId, String resourceType, UUID resourceId) {
        if (!supports(resourceType)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (QUESTION_SET_RESOURCE.equals(resourceType)) {
            if (!store.questionSetExists(userId, resourceId)) {
                throw notFound();
            }
            researchStore.cancelByAgentRun(userId, agentRunId, clock.instant());
            return;
        }
        store.findAnswer(userId, resourceId).orElseThrow(this::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(UUID userId, UUID evidenceId) {
        return store.evidenceIsReferenced(userId, evidenceId);
    }

    private WorkflowLaunchResult launchPreparation(PreparedPreparation prepared) {
        return workflowLauncher.launch(preparationLaunchCommand(prepared));
    }

    public WorkflowLaunchCommand preparationLaunchCommand(PreparedPreparation prepared) {
        String contextHash = contextHash(prepared.context());
        var input = objectMapper.createObjectNode()
                .put("jobId", prepared.jobId().toString())
                .put("coverLetterId", prepared.coverLetterId().toString())
                .put("researchRunId", prepared.researchRunId().toString())
                .put("questionSetId", prepared.questionSetId().toString())
                .put("researchQuality", prepared.researchQuality().name())
                .put("qualityMode", prepared.qualityMode().name())
                .put("questionCount", prepared.questionCount())
                .put("contextHash", contextHash);
        var types = input.putArray("questionTypes");
        prepared.questionTypes().forEach(type -> types.add(type.name()));
        var answers = input.putArray("coverLetterAnswerVersionIds");
        prepared.context().coverAnswers().forEach(answer -> answers.add(answer.answerVersionId().toString()));
        var evidence = input.putArray("evidenceIds");
        prepared.context().evidence().forEach(item -> evidence.add(item.id().toString()));
        if (prepared.context().profile().finalEducation() != null) {
            input.put(
                    "finalEducationId",
                    prepared.context().profile().finalEducation().id().toString());
        }
        return new WorkflowLaunchCommand(
                prepared.agentRunId(),
                prepared.userId(),
                WorkflowType.INTERVIEW_PREPARATION,
                PREPARATION_WORKFLOW_VERSION,
                sha256(contextHash
                        + "|research="
                        + prepared.researchQuality()
                        + "|types="
                        + prepared.questionTypes()
                        + "|count="
                        + prepared.questionCount()),
                input,
                prepared.qualityMode(),
                new ResourceReference(
                        QUESTION_SET_RESOURCE,
                        prepared.questionSetId(),
                        questionSetTitle(prepared.context())));
    }

    private WorkflowLaunchResult launchFeedback(PreparedFeedback prepared) {
        FeedbackContext context = prepared.context();
        String contextHash = feedbackContextHash(context);
        var input = objectMapper.createObjectNode()
                .put("answerVersionId", context.answerVersionId().toString())
                .put("questionId", context.questionId().toString())
                .put("contextHash", contextHash)
                .put("qualityMode", prepared.qualityMode().name());
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                prepared.agentRunId(),
                context.userId(),
                WorkflowType.INTERVIEW_ANSWER_FEEDBACK,
                FEEDBACK_WORKFLOW_VERSION,
                sha256(context.answerVersionId() + "|" + contextHash),
                input,
                prepared.qualityMode(),
                new ResourceReference(
                        ANSWER_VERSION_RESOURCE,
                        context.answerVersionId(),
                        "면접 답변 피드백")));
    }

    private List<InterviewQuestionType> validatePreparation(
            ResearchQuality researchQuality,
            AiQualityMode qualityMode,
            List<InterviewQuestionType> questionTypes,
            int questionCount) {
        if (researchQuality == null
                || qualityMode == null
                || qualityMode == AiQualityMode.HIGH_QUALITY
                || questionTypes == null
                || questionTypes.isEmpty()
                || questionTypes.size() > 7
                || questionCount < 1
                || questionCount > 20
                || questionTypes.stream().anyMatch(type -> type == null
                        || type == InterviewQuestionType.FOLLOW_UP)
                || new HashSet<>(questionTypes).size() != questionTypes.size()) {
            if (qualityMode == AiQualityMode.HIGH_QUALITY) {
                throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
            }
            throw invalid();
        }
        return List.copyOf(questionTypes);
    }

    private void requireFeedbackQuality(UUID userId, AiQualityMode qualityMode) {
        if (qualityMode == null) {
            throw invalid();
        }
        if (qualityMode == AiQualityMode.HIGH_QUALITY
                && !preferenceQuery.activePreference(userId).highQualityEnabled()) {
            throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
        }
    }

    public String generationConfig(PreparedPreparation prepared) {
        var value = objectMapper.createObjectNode()
                .put("researchQuality", prepared.researchQuality().name())
                .put("qualityMode", prepared.qualityMode().name())
                .put("questionCount", prepared.questionCount());
        var types = value.putArray("questionTypes");
        prepared.questionTypes().forEach(type -> types.add(type.name()));
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("interview generation config is invalid", exception);
        }
    }

    public String questionSetTitle(PreparationContext context) {
        String company = context.companyName() == null || context.companyName().isBlank()
                ? "채용 공고"
                : context.companyName().strip();
        String title = company + " 면접 예상 질문";
        return title.length() <= 300 ? title : title.substring(0, 300);
    }

    private String contextHash(PreparationContext context) {
        List<String> components = new ArrayList<>();
        components.add(context.jobId().toString());
        components.add(Long.toString(context.jobVersion()));
        components.add(context.jobAnalysisId().toString());
        components.add(context.coverLetterId().toString());
        context.coverAnswers().stream()
                .sorted(Comparator.comparing(value -> value.answerVersionId().toString()))
                .forEach(value -> components.add(value.answerVersionId() + ":" + sha256(value.answerText())));
        context.evidence().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .forEach(value -> components.add(value.id() + ":" + sha256(value.content())));
        components.add("profile:"
                + sha256(nullable(context.profile().introduction())
                        + "|roles="
                        + context.profile().desiredRoles()
                        + "|industries="
                        + context.profile().desiredIndustries()
                        + "|locations="
                        + context.profile().desiredLocations()));
        if (context.profile().finalEducation() != null) {
            var education = context.profile().finalEducation();
            components.add("education:"
                    + sha256(education.id()
                            + "|"
                            + nullable(education.schoolName())
                            + "|"
                            + nullable(education.major())
                            + "|"
                            + nullable(education.degree())
                            + "|"
                            + education.educationLevel()
                            + "|"
                            + education.educationStatus()
                            + "|"
                            + education.graduationDate()));
        }
        return sha256(String.join("|", components));
    }

    private String feedbackContextHash(FeedbackContext context) {
        return sha256(context.questionText()
                + "|intent="
                + nullable(context.intent())
                + "|points="
                + context.evaluationPoints()
                + "|guide="
                + nullable(context.answerGuide())
                + "|job="
                + context.jobId()
                + "|company="
                + nullable(context.companyName())
                + "|position="
                + nullable(context.positionName())
                + "|cover="
                + context.coverLetterId()
                + "|answer="
                + context.answerContent());
    }

    private void validateResearchResult(ResearchResult result) {
        if (result == null
                || result.coverage() == null
                || result.topics() == null
                || result.topics().isEmpty()
                || result.topics().size() > 4
                || result.sources() == null
                || result.sources().size() > 32
                || result.missingCoverageTopics().size() > 20
                || result.missingCoverageTopics().stream()
                        .anyMatch(value -> !validText(value, 200))
                || result.summary() == null
                || result.summary().isBlank()
                || result.summary().length() > 10000) {
            throw invalid();
        }
        Set<UUID> topicIds =
                new HashSet<>(result.topics().stream().map(value -> value.id()).toList());
        Set<Integer> topicOrders =
                new HashSet<>(result.topics().stream().map(value -> value.topicOrder()).toList());
        Set<ResearchTopic> availableTopics =
                new HashSet<>(result.topics().stream().map(value -> value.topic()).toList());
        if (topicIds.size() != result.topics().size()
                || topicOrders.size() != result.topics().size()
                || !topicOrders.equals(new HashSet<>(java.util.stream.IntStream
                        .rangeClosed(1, result.topics().size())
                        .boxed()
                        .toList()))
                || result.topics().stream().anyMatch(value -> value.id() == null
                        || value.topic() == null
                        || !validText(value.queryText(), 500))) {
            throw invalid();
        }
        Set<UUID> sourceIds =
                new HashSet<>(result.sources().stream().map(value -> value.id()).toList());
        Set<String> sourceUrls =
                new HashSet<>(result.sources().stream().map(value -> value.sourceUrl()).toList());
        if (sourceIds.size() != result.sources().size()
                || sourceUrls.size() != result.sources().size()
                || result.sources().stream().anyMatch(value -> value.id() == null
                        || value.topic() == null
                        || value.topics() == null
                        || value.topics().isEmpty()
                        || !value.topics().contains(value.topic())
                        || new HashSet<>(value.topics()).size() != value.topics().size()
                        || !availableTopics.containsAll(value.topics())
                        || !validUrl(value.sourceUrl())
                        || !validNullableText(value.title(), 500)
                        || value.sourceType() == null
                        || value.retrievedAt() == null
                        || !validNullableText(value.snippet(), 2000)
                        || !validText(value.reliabilityNotice(), 500)
                        || value.providerRank() < 1
                        || value.contentHash() == null
                        || !value.contentHash().matches("[0-9a-f]{64}"))) {
            throw invalid();
        }
        if (result.coverage() == SourceCoverage.NONE && !result.sources().isEmpty()) {
            throw invalid();
        }
        if (result.coverage() != SourceCoverage.NONE && result.sources().isEmpty()) {
            throw invalid();
        }
        if (result.coverage() != deterministicCoverage(result)) {
            throw invalid();
        }
    }

    private SourceCoverage deterministicCoverage(ResearchResult result) {
        if (result.sources().isEmpty()) {
            return SourceCoverage.NONE;
        }
        boolean authoritative = result.sources().stream().anyMatch(source ->
                source.sourceType() == ResearchSourceType.OFFICIAL
                        || source.sourceType() == ResearchSourceType.TECH_BLOG);
        long domains = result.sources().stream()
                .map(source -> URI.create(source.sourceUrl()).getHost())
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .count();
        long categories =
                result.sources().stream().map(value -> value.sourceType()).distinct().count();
        return result.sources().size() >= 3
                        && authoritative
                        && domains >= 2
                        && categories >= 2
                ? SourceCoverage.SUFFICIENT
                : SourceCoverage.LIMITED;
    }

    private void validateGeneratedQuestions(
            UUID userId,
            int expectedQuestionCount,
            List<GeneratedQuestion> questions,
            Set<UUID> allowedResearchSourceIds) {
        if (questions == null
                || questions.size() != expectedQuestionCount
                || questions.size() > 20
                || new HashSet<>(questions.stream().map(GeneratedQuestion::id).toList()).size()
                        != questions.size()
                || !new HashSet<>(questions.stream()
                                .map(GeneratedQuestion::questionOrder)
                                .toList())
                        .equals(new HashSet<>(java.util.stream.IntStream
                                .rangeClosed(1, expectedQuestionCount)
                                .boxed()
                                .toList()))) {
            throw invalid();
        }
        List<UUID> evidenceIds = new ArrayList<>();
        List<UUID> sourceIds = new ArrayList<>();
        for (GeneratedQuestion question : questions) {
            if (question.questionType() == null
                    || !validText(question.questionText(), 2000)
                    || !validNullableText(question.intent(), 2000)
                    || !validNullableText(question.answerGuide(), 10000)
                    || question.evaluationPoints().size() > 20
                    || question.evaluationPoints().stream()
                            .anyMatch(value -> !validText(value, 500))
                    || question.followUpQuestions().size() > 10
                    || question.followUpQuestions().stream()
                            .anyMatch(value -> !validText(value, 2000))
                    || question.evidenceIds().size() > 20
                    || question.sourceIds().size() > 50
                    || new HashSet<>(question.evidenceIds()).size()
                            != question.evidenceIds().size()
                    || new HashSet<>(question.sourceIds()).size()
                            != question.sourceIds().size()) {
                throw invalid();
            }
            evidenceIds.addAll(question.evidenceIds());
            sourceIds.addAll(question.sourceIds());
        }
        Set<UUID> distinctEvidence = Set.copyOf(evidenceIds);
        Set<UUID> distinctSources = Set.copyOf(sourceIds);
        if (!store.verifiedEvidenceIds(userId, List.copyOf(distinctEvidence))
                        .equals(distinctEvidence)
                || !allowedResearchSourceIds.containsAll(distinctSources)) {
            throw invalid();
        }
    }

    private void validateFeedback(FeedbackResult feedback) {
        if (feedback == null
                || feedback.scores().isEmpty()
                || feedback.scores().size() > 20
                || feedback.scores().stream().anyMatch(score -> score == null
                        || !validText(score.criterion(), 100)
                        || score.score() == null
                        || score.score().signum() < 0
                        || score.score().compareTo(new java.math.BigDecimal("100")) > 0
                        || !validNullableText(score.explanation(), 1000))
                || !validTexts(feedback.strengths(), 20, 1000)
                || !validTexts(feedback.weaknesses(), 20, 1000)
                || !validTexts(feedback.suggestions(), 20, 1000)
                || !validNullableText(feedback.revisedExample(), 10000)) {
            throw invalid();
        }
    }

    private boolean validTexts(List<String> values, int maxItems, int maxLength) {
        return values != null
                && values.size() <= maxItems
                && values.stream().allMatch(value -> validText(value, maxLength));
    }

    private boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private boolean validNullableText(String value, int maxLength) {
        return value == null || (value.length() <= maxLength && !value.isBlank());
    }

    private boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return value.length() <= 2000
                    && Set.of("http", "https").contains(
                            uri.getScheme().toLowerCase(java.util.Locale.ROOT))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean secureEquals(String left, String right) {
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

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private record PreparedFeedback(
            UUID agentRunId, FeedbackContext context, AiQualityMode qualityMode) {}
}
