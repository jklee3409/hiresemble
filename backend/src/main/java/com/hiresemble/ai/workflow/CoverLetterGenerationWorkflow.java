package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.validation.ProviderNullable;
import com.hiresemble.ai.validation.KoreanUserFacingTextPolicy;
import com.hiresemble.ai.validation.StructuredOutputValidationException;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.BoundedText;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.ClaimType;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionPlan;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AppliedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceUse;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationQuestion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationIssue;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedClaim;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedEvidence;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.coverletter.domain.CoverLetterEvidenceUsageType;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapMarkDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort.EmbeddingPolicySnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Controlled P7 cover-letter generation workflow with bounded question fan-out. */
public final class CoverLetterGenerationWorkflow {

    public static final String BUILD_GENERATION_CONTEXT = "BUILD_GENERATION_CONTEXT";
    public static final String PLAN_QUESTIONS = "PLAN_QUESTIONS";
    public static final String ANALYZE_QUESTION = "ANALYZE_QUESTION";
    public static final String RETRIEVE_EVIDENCE = "RETRIEVE_EVIDENCE";
    public static final String ALLOCATE_EXPERIENCES = "ALLOCATE_EXPERIENCES";
    public static final String WRITE_ANSWER = "WRITE_ANSWER";
    public static final String FACT_CHECK_ANSWER = "FACT_CHECK_ANSWER";
    public static final String APPLY_ANSWER_VERSION = "APPLY_ANSWER_VERSION";

    public static final String CONTEXT_POLICY_VERSION = "cover-generation-context-v1";
    public static final String RETRIEVAL_POLICY_VERSION = "cover-generation-retrieval-v1";
    public static final String CONTEXT_POLICY_VERSION_V2 = "cover-generation-context-v2";
    public static final String RETRIEVAL_POLICY_VERSION_V2 = "cover-generation-retrieval-v2";
    public static final String CONTEXT_POLICY_VERSION_V3 = "cover-generation-context-v3";
    public static final String RETRIEVAL_POLICY_VERSION_V3 = "cover-generation-retrieval-v3";

    private static final String BUILD_SCHEMA = "cover-generation-build-output-v1";
    private static final String PLAN_SCHEMA = "cover-generation-plan-output-v1";
    private static final String ANALYSIS_SCHEMA =
            "cover-generation-question-analysis-output-v1";
    private static final String RETRIEVAL_SCHEMA =
            "cover-generation-retrieval-output-v1";
    private static final String ALLOCATION_SCHEMA =
            "cover-generation-allocation-output-v1";
    private static final String ANSWER_SCHEMA = "cover-generation-answer-output-v1";
    private static final String FACT_CHECK_SCHEMA =
            "cover-generation-fact-check-output-v1";
    private static final String APPLY_SCHEMA = "cover-generation-apply-output-v1";
    private static final String PLAN_SCHEMA_V2 = "cover-generation-plan-output-v2";
    private static final String ANALYSIS_SCHEMA_V2 =
            "cover-generation-question-analysis-output-v2";
    private static final String ALLOCATION_SCHEMA_V2 =
            "cover-generation-allocation-output-v2";
    private static final String ANSWER_SCHEMA_V2 = "cover-generation-answer-output-v2";
    private static final String FACT_CHECK_SCHEMA_V2 =
            "cover-generation-fact-check-output-v2";
    private static final String PLAN_SCHEMA_V3 = "cover-generation-plan-output-v3";
    private static final String ANALYSIS_SCHEMA_V3 =
            "cover-generation-question-analysis-output-v3";
    private static final String ANSWER_SCHEMA_V3 = "cover-generation-answer-output-v3";
    private static final String FACT_CHECK_SCHEMA_V3 =
            "cover-generation-fact-check-output-v3";
    private static final String INPUT_SCHEMA = "cover-letter-input-v1";
    private static final String INPUT_SCHEMA_V2 = "cover-letter-input-v2";
    private static final String INPUT_SCHEMA_V3 = "cover-letter-input-v3";
    private static final String INPUT_SCHEMA_V4 = "cover-letter-input-v4";
    private static final int MAX_EVIDENCE_PER_QUESTION = 12;
    private static final int MAX_PLANNING_EVIDENCE = 20;
    private static final int MAX_CHUNK_REFS = 8;
    private static final int MAX_TEXT = 20_000;
    private static final int MAX_JOB_DESCRIPTION = 4_000;
    private static final int MAX_EVIDENCE_TITLE = 200;
    private static final int MAX_EVIDENCE_CONTENT = 1_000;
    private static final int MAX_CURRENT_ANSWER = 4_000;
    private static final int MAX_SIBLING_ANSWER = 1_000;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d[\\d,.%]*(?![\\p{L}\\p{N}])");
    private static final Set<String> ALLOWED_NODES = Set.of(
            "doc",
            "paragraph",
            "text",
            "hardBreak",
            "bulletList",
            "orderedList",
            "listItem");
    private static final Set<String> ALLOWED_MARKS = Set.of("bold", "italic");

    private final CoverLetterQueryPort queryPort;
    private final CoverLetterCommandPort commandPort;
    private final JobAnalysisEmbeddingQueryPort embeddingPolicyPort;
    private final ObjectMapper objectMapper;

    public CoverLetterGenerationWorkflow(
            CoverLetterQueryPort queryPort,
            CoverLetterCommandPort commandPort,
            JobAnalysisEmbeddingQueryPort embeddingPolicyPort,
            ObjectMapper objectMapper) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.embeddingPolicyPort = Objects.requireNonNull(embeddingPolicyPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_GENERATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_LEGACY_VERSION,
                TerminalPartialPolicy.fail(
                        "COVER_LETTER_GENERATION_PARTIAL_FAILURE",
                        "일부 자기소개서 문항을 생성하지 못했습니다.",
                        TerminalPartialPolicy.RetryPolicy.INHERIT_FAILURES),
                List.of(
                        step(BUILD_GENERATION_CONTEXT, new BuildContextExecutor()),
                        step(PLAN_QUESTIONS, new PlanQuestionsExecutor()),
                        step(ANALYZE_QUESTION, new AnalyzeQuestionExecutor()),
                        step(RETRIEVE_EVIDENCE, new RetrieveEvidenceExecutor()),
                        step(ALLOCATE_EXPERIENCES, new AllocateExperiencesExecutor()),
                        step(WRITE_ANSWER, new WriteAnswerExecutor()),
                        step(FACT_CHECK_ANSWER, new FactCheckAnswerExecutor()),
                        step(APPLY_ANSWER_VERSION, new ApplyAnswerExecutor())));
    }

    /** Active v2 contribution. The legacy {@link #contribution()} remains executable for durable v1 runs. */
    public ExecutableWorkflowContribution v2Contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_GENERATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V2_VERSION,
                TerminalPartialPolicy.fail(
                        "COVER_LETTER_GENERATION_PARTIAL_FAILURE",
                        "일부 자기소개서 문항을 생성하지 못했습니다.",
                        TerminalPartialPolicy.RetryPolicy.INHERIT_FAILURES),
                List.of(
                        step(BUILD_GENERATION_CONTEXT, new V2BuildContextExecutor()),
                        step(PLAN_QUESTIONS, new V2PlanQuestionsExecutor()),
                        step(ANALYZE_QUESTION, new V2AnalyzeQuestionExecutor()),
                        step(RETRIEVE_EVIDENCE, new V2RetrieveEvidenceExecutor()),
                        step(ALLOCATE_EXPERIENCES, new V2AllocateExperiencesExecutor()),
                        step(WRITE_ANSWER, new V2WriteAnswerExecutor()),
                        step(FACT_CHECK_ANSWER, new V2FactCheckAnswerExecutor()),
                        step(APPLY_ANSWER_VERSION, new V2ApplyAnswerExecutor())));
    }

    /** Durable v3 contribution; v1/v2 contributions remain exact-version durable executors. */
    public ExecutableWorkflowContribution v3Contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_GENERATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V3_VERSION,
                TerminalPartialPolicy.fail(
                        "COVER_LETTER_GENERATION_PARTIAL_FAILURE",
                        "일부 자기소개서 문항을 생성하지 못했습니다.",
                        TerminalPartialPolicy.RetryPolicy.INHERIT_FAILURES),
                List.of(
                        step(BUILD_GENERATION_CONTEXT, new V3BuildContextExecutor()),
                        step(PLAN_QUESTIONS, new V3PlanQuestionsExecutor()),
                        step(ANALYZE_QUESTION, new V3AnalyzeQuestionExecutor()),
                        step(RETRIEVE_EVIDENCE, new V3RetrieveEvidenceExecutor()),
                        step(ALLOCATE_EXPERIENCES, new V3AllocateExperiencesExecutor()),
                        step(WRITE_ANSWER, new V3WriteAnswerExecutor()),
                        step(FACT_CHECK_ANSWER, new V3FactCheckAnswerExecutor()),
                        step(APPLY_ANSWER_VERSION, new V3ApplyAnswerExecutor())));
    }

    /** Active v4 contribution with exact-model routing and memo-aware generation inputs. */
    public ExecutableWorkflowContribution v4Contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_GENERATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION,
                TerminalPartialPolicy.fail(
                        "COVER_LETTER_GENERATION_PARTIAL_FAILURE",
                        "일부 자기소개서 문항을 생성하지 못했습니다.",
                        TerminalPartialPolicy.RetryPolicy.INHERIT_FAILURES),
                List.of(
                        step(BUILD_GENERATION_CONTEXT, new V3BuildContextExecutor()),
                        step(PLAN_QUESTIONS, new V3PlanQuestionsExecutor()),
                        step(ANALYZE_QUESTION, new V3AnalyzeQuestionExecutor()),
                        step(RETRIEVE_EVIDENCE, new V3RetrieveEvidenceExecutor()),
                        step(ALLOCATE_EXPERIENCES, new V3AllocateExperiencesExecutor()),
                        step(WRITE_ANSWER, new V3WriteAnswerExecutor()),
                        step(FACT_CHECK_ANSWER, new V3FactCheckAnswerExecutor()),
                        step(APPLY_ANSWER_VERSION, new V3ApplyAnswerExecutor())));
    }

    private ExecutableWorkflowStep step(
            String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class GenerationExecutor<T> implements WorkflowStepExecutor<T> {
        private final String stepKey;
        private final String schemaVersion;
        private final Class<T> outputType;
        private final Set<String> outputFields;

        private GenerationExecutor(
                String stepKey, String schemaVersion, Class<T> outputType) {
            this.stepKey = stepKey;
            this.schemaVersion = schemaVersion;
            this.outputType = outputType;
            this.outputFields = recordFields(outputType);
        }

        @Override
        public final Contract<T> outputContract() {
            return contract(null);
        }

        @Override
        public final Contract<T> outputContract(StepExecutionContext context) {
            return contract(context);
        }

        private Contract<T> contract(StepExecutionContext context) {
            return new Contract<>(
                    outputType,
                    schemaVersion,
                    value -> {
                        if (value == null
                                || !value.isObject()
                                || !outputFields.equals(Set.copyOf(value.propertyNames()))) {
                            throw new IllegalArgumentException(
                                    "cover generation output schema is invalid");
                        }
                    },
                    value -> validateJavaRecord(value, context),
                    value -> validateWorkflowOutput(value, context),
                    value -> validateDomainOutput(value, context));
        }

        protected void validateJavaRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflowOutput(T output, StepExecutionContext context) {}

        protected void validateDomainOutput(T output, StepExecutionContext context) {}

        protected final GenerationState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType()
                            != WorkflowType.COVER_LETTER_GENERATION
                    || (!CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION.equals(
                                    context.run().workflowVersion())
                            && !CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V2_VERSION.equals(
                                    context.run().workflowVersion())
                            && !CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V3_VERSION.equals(
                                    context.run().workflowVersion())
                            && !CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_LEGACY_VERSION.equals(
                                    context.run().workflowVersion()))
                    || !"COVER_LETTER".equals(context.run().resourceType())
                    || context.run().resourceId() == null
                    || !validSelection(context.run())) {
                throw configurationFailure();
            }
            try {
                JsonNode input = context.run().inputReferenceSnapshot();
                List<UUID> requestedQuestionIds =
                        uuidArray(input.path("questionIds"), 1, 20);
                GenerationSnapshot snapshot = context.run().retryOfRunId() == null
                        ? isExactModel(context.run())
                                ? queryPort.loadGenerationSnapshotByModel(
                                        context.run().userId(),
                                        context.run().resourceId(),
                                        input.path("coverLetterVersion").asLong(-1),
                                        requestedQuestionIds,
                                        uuidArray(input.path("preferredEvidenceIds"), 0, 50),
                                        input.path("avoidExperienceDuplication").asBoolean(false),
                                        context.run().requestedModel(),
                                        context.contextSnapshot().contextHash())
                                : queryPort.loadGenerationSnapshot(
                                        context.run().userId(),
                                        context.run().resourceId(),
                                        input.path("coverLetterVersion").asLong(-1),
                                        requestedQuestionIds,
                                        uuidArray(input.path("preferredEvidenceIds"), 0, 50),
                                        input.path("avoidExperienceDuplication").asBoolean(false),
                                        context.run().requestedQualityMode(),
                                        context.contextSnapshot().contextHash())
                        : queryPort.loadGenerationRetrySnapshot(
                                context.run().userId(),
                                context.run().id(),
                                context.contextSnapshot().contextHash());
                if (!snapshot.userId().equals(context.run().userId())
                        || !snapshot.coverLetterId().equals(context.run().resourceId())
                        || !selectionMatches(context.run(), snapshot)
                        || !snapshot.snapshotHash().equals(
                                context.contextSnapshot().contextHash())
                        || snapshot.questions().size() > 20) {
                    throw ownerFailure();
                }
                Set<UUID> targetIds = snapshot.questions().stream()
                        .map(GenerationQuestion::questionId)
                        .collect(java.util.stream.Collectors.toSet());
                List<UUID> reused = requestedQuestionIds.stream()
                        .filter(id -> !targetIds.contains(id))
                        .toList();
                EmbeddingPolicySnapshot embeddingPolicy =
                        embeddingPolicyPort.activePolicy();
                if (embeddingPolicy.dimension() < 1
                        || embeddingPolicy.version() < 1
                        || embeddingPolicy.generation() < 1) {
                    throw configurationFailure();
                }
                return new GenerationState(
                        snapshot,
                        context.run().id(),
                        requestedQuestionIds,
                        reused,
                        embeddingPolicy);
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            } catch (RuntimeException exception) {
                throw ownerFailure();
            }
        }

        protected final StepInput localInput(
                GenerationState state,
                String scopeKey,
                JsonNode refs,
                String canonicalSuffix,
                JsonNode payload) {
            return new StepInput(
                    scopeKey,
                    refs,
                    stepKey
                            + "|"
                            + state.snapshot().snapshotHash()
                            + "|"
                            + canonicalSuffix,
                    payload,
                    null,
                    state.snapshot().coverLetterVersion());
        }

        protected final ObjectNode baseRefs(GenerationState state) {
            var refs = objectMapper.createObjectNode()
                    .put(
                            "coverLetterId",
                            state.snapshot().coverLetterId().toString())
                    .put("coverLetterVersion", state.snapshot().coverLetterVersion())
                    .put("jobId", state.snapshot().job().jobId().toString())
                    .put(
                            "analysisId",
                            state.snapshot().job().analysisId().toString())
                    .put(
                            "analysisVersion",
                            state.snapshot().job().analysisVersion())
                    .put(
                            "analysisOutdated",
                            state.snapshot().job().analysisOutdated())
                    .put("snapshotHash", state.snapshot().snapshotHash())
                    .put("contextPolicyVersion", CONTEXT_POLICY_VERSION)
                    .put("retrievalPolicyVersion", RETRIEVAL_POLICY_VERSION);
            var questionIds = refs.putArray("questionIds");
            state.snapshot().questions().forEach(
                    value -> questionIds.add(value.questionId().toString()));
            return refs;
        }

        protected final ObjectNode baseRefsV2(GenerationState state) {
            var refs = baseRefs(state);
            refs.put("contextPolicyVersion", CONTEXT_POLICY_VERSION_V2);
            refs.put("retrievalPolicyVersion", RETRIEVAL_POLICY_VERSION_V2);
            refs.put("inputSchemaVersion", INPUT_SCHEMA_V2);
            return refs;
        }

        protected final ObjectNode baseRefsV3(GenerationState state) {
            var refs = baseRefs(state);
            refs.put("contextPolicyVersion", contextPolicyVersion(state));
            refs.put("retrievalPolicyVersion", retrievalPolicyVersion(state));
            refs.put("inputSchemaVersion", inputSchemaVersion(state));
            refs.put("outputLocale", CoverLetterWorkflowV3Policy.OUTPUT_LOCALE);
            return refs;
        }

        protected final JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
        }

        protected final AiGatewayResponse localResponse(Object output) {
            return new AiGatewayResponse(write(output), java.util.List.of());
        }

        @Override
        public JsonNode minimalOutput(T output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public Object ephemeralOutputFromMinimal(JsonNode minimalOutput) {
            return read(minimalOutput, outputType);
        }
    }

    private abstract class QuestionExecutor<T> extends GenerationExecutor<T> {
        private QuestionExecutor(
                String stepKey, String schemaVersion, Class<T> outputType) {
            super(stepKey, schemaVersion, outputType);
        }

        @Override
        public final StepInput prepare(StepExecutionContext context) {
            List<StepInput> inputs = prepareInputs(context);
            if (inputs.isEmpty()) {
                throw configurationFailure();
            }
            return inputs.getFirst();
        }

        @Override
        public final List<StepInput> prepareInputs(StepExecutionContext context) {
            GenerationState state = state(context);
            return state.snapshot().questions().stream()
                    .sorted(Comparator.comparingInt(GenerationQuestion::questionOrder)
                            .thenComparing(GenerationQuestion::questionId))
                    .filter(question -> eligibleQuestion(context, question))
                    .map(question -> prepareQuestion(context, state, question))
                    .toList();
        }

        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            return true;
        }

        protected abstract StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question);

        @Override
        public boolean continueAfterScopeFailure(
                AiExecutionException failure, StepExecutionContext context) {
            return context.scopeKey() != null
                    && failure.failureKind() != FailureKind.OWNER
                    && failure.failureKind() != FailureKind.CONFIGURATION
                    && failure.failureKind() != FailureKind.BUDGET
                    && failure.failureKind() != FailureKind.CANCELLATION
                    && failure.failureKind() != FailureKind.INTERRUPTION;
        }
    }

    private final class BuildContextExecutor
            extends GenerationExecutor<BuildGenerationContextOutput> {
        private BuildContextExecutor() {
            super(
                    BUILD_GENERATION_CONTEXT,
                    BUILD_SCHEMA,
                    BuildGenerationContextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            return localInput(
                    state,
                    null,
                    baseRefs(state),
                    BUILD_SCHEMA,
                    tree(new BuildGenerationContextInput(
                            INPUT_SCHEMA,
                            state.snapshot().coverLetterId(),
                            state.snapshot().coverLetterVersion(),
                            state.snapshot().snapshotHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            GenerationSnapshot snapshot = state.snapshot();
            return localResponse(new BuildGenerationContextOutput(
                    BUILD_SCHEMA,
                    snapshot.coverLetterId(),
                    snapshot.coverLetterVersion(),
                    snapshot.snapshotHash(),
                    snapshot.job().jobId(),
                    snapshot.job().analysisId(),
                    snapshot.job().analysisVersion(),
                    snapshot.job().analysisOutdated(),
                    snapshot.questions().stream()
                            .map(GenerationQuestion::questionId)
                            .toList(),
                    state.reusedQuestionIds(),
                    snapshot.verifiedEvidence().stream()
                            .map(VerifiedEvidence::id)
                            .sorted()
                            .toList(),
                    snapshot.preferredEvidenceIds()));
        }

        @Override
        public Optional<PartialResult> partialResult(
                BuildGenerationContextOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            return output.reusedQuestionIds().isEmpty()
                    ? Optional.empty()
                    : Optional.of(new PartialResult(
                            output.reusedQuestionIds().stream()
                                    .map(UUID::toString)
                                    .toList(),
                            List.of(),
                            List.of()));
        }

        @Override
        public Optional<PartialResult> partialResultFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            BuildGenerationContextOutput output =
                    read(minimalOutput, BuildGenerationContextOutput.class);
            return partialResult(output, minimalOutput, context);
        }

        @Override
        protected void validateJavaRecord(
                BuildGenerationContextOutput output,
                StepExecutionContext context) {
            if (!BUILD_SCHEMA.equals(output.schemaVersion())
                    || output.coverLetterId() == null
                    || output.coverLetterVersion() < 0
                    || !isHash(output.snapshotHash())
                    || output.jobId() == null
                    || output.analysisId() == null
                    || output.analysisVersion() < 1
                    || output.questionIds() == null
                    || output.questionIds().size() > 20
                    || hasDuplicates(output.questionIds())
                    || hasDuplicates(output.reusedQuestionIds())
                    || hasDuplicates(output.verifiedEvidenceIds())
                    || hasDuplicates(output.preferredEvidenceIds())) {
                throw new IllegalArgumentException("generation context output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                BuildGenerationContextOutput output,
                StepExecutionContext context) {
            GenerationState state = state(context);
            if (!output.coverLetterId().equals(state.snapshot().coverLetterId())
                    || output.coverLetterVersion()
                            != state.snapshot().coverLetterVersion()
                    || !output.snapshotHash().equals(
                            state.snapshot().snapshotHash())
                    || !output.questionIds().equals(state.snapshot().questions().stream()
                            .map(GenerationQuestion::questionId)
                            .toList())
                    || !output.reusedQuestionIds().equals(
                            state.reusedQuestionIds())) {
                throw domainFailure(
                        "COVER_GENERATION_CONTEXT_STALE",
                        "자기소개서 생성 기준 정보가 변경되었습니다.");
            }
        }
    }

    private final class V3BuildContextExecutor
            extends GenerationExecutor<BuildGenerationContextOutput> {
        private V3BuildContextExecutor() {
            super(BUILD_GENERATION_CONTEXT, BUILD_SCHEMA, BuildGenerationContextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            return localInput(
                    state,
                    null,
                    baseRefsV3(state),
                    BUILD_SCHEMA + "|" + inputSchemaVersion(state),
                    tree(new BuildGenerationContextInput(
                            inputSchemaVersion(state),
                            state.snapshot().coverLetterId(),
                            state.snapshot().coverLetterVersion(),
                            state.snapshot().snapshotHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            GenerationSnapshot snapshot = state.snapshot();
            return localResponse(new BuildGenerationContextOutput(
                    BUILD_SCHEMA,
                    snapshot.coverLetterId(),
                    snapshot.coverLetterVersion(),
                    snapshot.snapshotHash(),
                    snapshot.job().jobId(),
                    snapshot.job().analysisId(),
                    snapshot.job().analysisVersion(),
                    snapshot.job().analysisOutdated(),
                    snapshot.questions().stream().map(GenerationQuestion::questionId).toList(),
                    state.reusedQuestionIds(),
                    snapshot.verifiedEvidence().stream().map(VerifiedEvidence::id).sorted().toList(),
                    snapshot.preferredEvidenceIds()));
        }

        @Override
        protected void validateJavaRecord(BuildGenerationContextOutput output, StepExecutionContext context) {
            validateBuildOutput(output);
        }

        @Override
        protected void validateDomainOutput(BuildGenerationContextOutput output, StepExecutionContext context) {
            validateBuildScope(output, state(context));
        }
    }

    private final class V2BuildContextExecutor
            extends GenerationExecutor<BuildGenerationContextOutput> {
        private V2BuildContextExecutor() {
            super(BUILD_GENERATION_CONTEXT, BUILD_SCHEMA, BuildGenerationContextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            return localInput(
                    state,
                    null,
                    baseRefsV2(state),
                    BUILD_SCHEMA + "|" + INPUT_SCHEMA_V2,
                    tree(new BuildGenerationContextInput(
                            INPUT_SCHEMA_V2,
                            state.snapshot().coverLetterId(),
                            state.snapshot().coverLetterVersion(),
                            state.snapshot().snapshotHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationSnapshot snapshot = state(invocation.executionContext()).snapshot();
            GenerationState state = state(invocation.executionContext());
            return localResponse(new BuildGenerationContextOutput(
                    BUILD_SCHEMA,
                    snapshot.coverLetterId(),
                    snapshot.coverLetterVersion(),
                    snapshot.snapshotHash(),
                    snapshot.job().jobId(),
                    snapshot.job().analysisId(),
                    snapshot.job().analysisVersion(),
                    snapshot.job().analysisOutdated(),
                    snapshot.questions().stream().map(GenerationQuestion::questionId).toList(),
                    state.reusedQuestionIds(),
                    snapshot.verifiedEvidence().stream().map(VerifiedEvidence::id).sorted().toList(),
                    snapshot.preferredEvidenceIds()));
        }

        @Override
        protected void validateJavaRecord(
                BuildGenerationContextOutput output, StepExecutionContext context) {
            if (!BUILD_SCHEMA.equals(output.schemaVersion())
                    || output.coverLetterId() == null
                    || output.coverLetterVersion() < 0
                    || !isHash(output.snapshotHash())
                    || output.jobId() == null
                    || output.analysisId() == null
                    || output.analysisVersion() < 1
                    || output.questionIds() == null
                    || output.questionIds().size() > 20
                    || hasDuplicates(output.questionIds())
                    || hasDuplicates(output.reusedQuestionIds())
                    || hasDuplicates(output.verifiedEvidenceIds())
                    || hasDuplicates(output.preferredEvidenceIds())) {
                throw new IllegalArgumentException("generation context output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                BuildGenerationContextOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            if (!output.coverLetterId().equals(state.snapshot().coverLetterId())
                    || output.coverLetterVersion() != state.snapshot().coverLetterVersion()
                    || !output.snapshotHash().equals(state.snapshot().snapshotHash())
                    || !output.questionIds().equals(state.snapshot().questions().stream()
                            .map(GenerationQuestion::questionId)
                            .toList())
                    || !output.reusedQuestionIds().equals(state.reusedQuestionIds())) {
                throw domainFailure(
                        "COVER_GENERATION_CONTEXT_STALE",
                        "자기소개서 생성 기준 정보가 변경되었습니다.");
            }
        }
    }

    private final class V3PlanQuestionsExecutor extends GenerationExecutor<PlanQuestionsOutputV3> {
        private V3PlanQuestionsExecutor() {
            super(PLAN_QUESTIONS, PLAN_SCHEMA_V3, PlanQuestionsOutputV3.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            List<QuestionPlanningInput> questions = planningQuestions(state);
            List<RequirementInput> requirements = requirementInputs(state);
            List<EvidencePlanningInput> evidence = planningEvidence(state);
            var refs = baseRefsV3(state);
            refs.put("questionCount", questions.size());
            refs.put("planningEvidenceCount", evidence.size());
            refs.put("outputLocale", CoverLetterWorkflowV3Policy.OUTPUT_LOCALE);
            return localInput(
                    state,
                    null,
                    refs,
                    stableHash(questions) + "|" + stableHash(evidence),
                    tree(planQuestionsInput(state, questions, requirements, evidence)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PlanningInvocationInput input = planningInvocationInput(invocation);
            if (input.questions().size() == 1) {
                QuestionPlanningInput question = input.questions().getFirst();
                int target = Math.max(1, Math.min(
                        800,
                        question.maxLength() == null ? 800 : question.maxLength()));
                List<Integer> requirementIndexes = java.util.stream.IntStream.range(
                                0, Math.min(100, input.requirements().size()))
                        .boxed()
                        .toList();
                return localResponse(new PlanQuestionsOutputV3(
                        PLAN_SCHEMA_V3,
                        List.of(new QuestionPlanV3(
                                question.questionId(),
                                QuestionType.FREEFORM,
                                "Answer the supplied question directly with verified experience.",
                                NarrativeFramework.DIRECT_RESPONSE,
                                "Give a direct, evidence-grounded answer tailored to the role.",
                                List.of("Direct answer", "Personal action", "Result", "Role application"),
                                List.of("Unsupported claims", "Generic company praise"),
                                requirementIndexes,
                                null,
                                null,
                                List.of("Current verified experience relevant to the question"),
                                target,
                                HeadingPolicy.DISALLOWED,
                                List.of(new NarrativeSectionPlan(
                                        CoverLetterWorkflowV3Policy.NarrativeSectionType.DIRECT_ANSWER,
                                        "Answer the question with verified experience and role application.",
                                        100)))),
                        input.avoidExperienceDuplication()));
            }
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(PlanQuestionsOutputV3 output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", PLAN_SCHEMA_V3)
                    .put("planHash", stableHash(output))
                    .put("questionCount", output.plans().size());
            var ids = result.putArray("questionIds");
            output.plans().forEach(value -> ids.add(value.questionId().toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(PlanQuestionsOutputV3 output, StepExecutionContext context) {
            if (!PLAN_SCHEMA_V3.equals(output.schemaVersion())
                    || output.plans() == null
                    || output.plans().isEmpty()
                    || output.plans().size() > 20
                    || hasDuplicates(output.plans().stream().map(QuestionPlanV3::questionId).toList())) {
                throw repairable(
                        "COVER_PLAN_OUTPUT_INVALID",
                        "Set schemaVersion to cover-generation-plan-output-v3 and return exactly one nonempty plan for every supplied questionId in supplied order.");
            }
            ContextAvailabilityInput availability = contextAvailability(state(context));
            output.plans().forEach(plan -> validateQuestionPlanV3(plan, availability));
        }

        @Override
        protected void validateWorkflowOutput(PlanQuestionsOutputV3 output, StepExecutionContext context) {
            if (output.avoidExperienceDuplication() && output.plans().size() > 1) {
                List<String> messages = output.plans().stream()
                        .map(plan -> CoverLetterWorkflowV3Policy.normalize(plan.coreMessage()))
                        .toList();
                if (new HashSet<>(messages).size() != messages.size()) {
                    throw new IllegalArgumentException("question core messages must be distinct");
                }
            }
        }

        @Override
        protected void validateDomainOutput(PlanQuestionsOutputV3 output, StepExecutionContext context) {
            GenerationState state = state(context);
            List<UUID> expected = state.snapshot().questions().stream()
                    .map(GenerationQuestion::questionId)
                    .toList();
            if (!expected.equals(output.plans().stream().map(QuestionPlanV3::questionId).toList())
                    || output.avoidExperienceDuplication()
                            != state.snapshot().avoidExperienceDuplication()) {
                throw domainFailure(
                        "COVER_GENERATION_PLAN_SCOPE_INVALID",
                        "자기소개서 문항 생성 계획 범위를 확인하지 못했습니다.");
            }
            for (QuestionPlanV3 plan : output.plans()) {
                GenerationQuestion question = question(state, plan.questionId());
                if ((question.maxLength() != null
                                && plan.targetCharacterCount() > question.maxLength())
                        || plan.requirementIndexes().stream().anyMatch(index ->
                                index >= requirementInputs(state).size())) {
                    throw new IllegalArgumentException("v3 plan references invalid input");
                }
            }
        }
    }

    private final class V3AnalyzeQuestionExecutor
            extends QuestionExecutor<QuestionAnalysisOutputV3> {
        private V3AnalyzeQuestionExecutor() {
            super(ANALYZE_QUESTION, ANALYSIS_SCHEMA_V3, QuestionAnalysisOutputV3.class);
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            PlanQuestionsOutputV3 plan = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
            QuestionPlanV3 selected = plan.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            var refs = baseRefsV3(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("planHash", stableHash(selected));
            refs.put("outputLocale", CoverLetterWorkflowV3Policy.OUTPUT_LOCALE);
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    stableHash(selected),
                    tree(new AnalyzeQuestionInputV3(
                            inputSchemaVersion(state),
                            CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                            contextAvailability(state),
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            selected,
                            jobWritingContext(state),
                            requirementInputs(state))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            if (state.snapshot().questions().size() == 1) {
                AnalyzeQuestionInputV3 input = read(
                        invocation.input().gatewayPayload(), AnalyzeQuestionInputV3.class);
                QuestionPlanV3 plan = input.plan();
                return localResponse(new QuestionAnalysisOutputV3(
                        ANALYSIS_SCHEMA_V3,
                        input.questionId(),
                        plan.questionType(),
                        "Directly answer the supplied question using only verified context.",
                        plan.objective(),
                        plan.coreMessage(),
                        plan.requiredElements(),
                        plan.avoidContent(),
                        plan.narrativeFramework(),
                        plan.narrativeSections(),
                        "Emphasize the applicant's own decisions and actions.",
                        plan.evidenceSelectionCriteria(),
                        plan.requirementIndexes(),
                        plan.roleConnection(),
                        plan.companyConnection(),
                        "Conclude with a concrete role application.",
                        plan.headingPolicy()));
            }
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(QuestionAnalysisOutputV3 output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", ANALYSIS_SCHEMA_V3)
                    .put("questionId", output.questionId().toString())
                    .put("analysisHash", stableHash(output))
                    .put("sectionCount", output.narrativeSections().size());
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(QuestionAnalysisOutputV3 output, StepExecutionContext context) {
            if (!ANALYSIS_SCHEMA_V3.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.questionType() == null
                    || output.narrativeFramework() == null
                    || output.headingPolicy() == null
                    || output.requirementIndexes() == null
                    || output.requirementIndexes().size() > 100) {
                throw new IllegalArgumentException("v3 question analysis is invalid");
            }
            requireText(output.intent(), 2_000);
            requireText(output.directAnswerDirection(), 1_000);
            requireText(output.openingCoreMessage(), 1_000);
            requireText(output.personalActionFocus(), 1_000);
            requireText(output.conclusionDirection(), 1_000);
            requireTexts(output.requiredElements(), 20, 1_000);
            requireTexts(output.avoidContent(), 20, 1_000);
            requireTexts(output.requiredEvidenceTraits(), 20, 1_000);
            CoverLetterWorkflowV3Policy.validateSections(
                    output.narrativeFramework(), output.narrativeSections());
            validateOptionalConnections(
                    output.roleConnection(), output.companyConnection(), contextAvailability(state(context)));
        }

        @Override
        protected void validateDomainOutput(QuestionAnalysisOutputV3 output, StepExecutionContext context) {
            GenerationState state = state(context);
            PlanQuestionsOutputV3 plan = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
            QuestionPlanV3 selected = plan.plans().stream()
                    .filter(value -> value.questionId().equals(output.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            if (!output.questionId().toString().equals(context.scopeKey())
                    || output.questionType() != selected.questionType()
                    || output.narrativeFramework() != selected.narrativeFramework()
                    || output.headingPolicy() != selected.headingPolicy()
                    || !output.narrativeSections().equals(selected.narrativeSections())
                    || output.requirementIndexes().stream().anyMatch(index ->
                            index == null || index < 0 || index >= requirementInputs(state).size())) {
                throw domainFailure(
                        "COVER_GENERATION_QUESTION_SCOPE_INVALID",
                        "자기소개서 문항 분석 범위를 확인하지 못했습니다.");
            }
        }
    }

    private final class V2PlanQuestionsExecutor
            extends GenerationExecutor<PlanQuestionsOutputV2> {
        private V2PlanQuestionsExecutor() {
            super(PLAN_QUESTIONS, PLAN_SCHEMA_V2, PlanQuestionsOutputV2.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            List<QuestionPlanningInput> questions = planningQuestions(state);
            List<RequirementInput> requirements = requirementInputs(state);
            List<EvidencePlanningInput> evidence = planningEvidence(state);
            JobWritingContextInput job = jobWritingContext(state);
            var refs = baseRefsV2(state);
            refs.put("questionCount", questions.size());
            refs.put("planningEvidenceCount", evidence.size());
            refs.put("omittedPlanningEvidenceCount",
                    Math.max(0, state.snapshot().verifiedEvidence().size() - evidence.size()));
            refs.put("requirementsHash", stableHash(requirements));
            refs.put("planningEvidenceHash", stableHash(evidence));
            return localInput(
                    state,
                    null,
                    refs,
                    stableHash(questions) + "|" + stableHash(job) + "|" + stableHash(evidence),
                    tree(new PlanQuestionsInputV2(
                            INPUT_SCHEMA_V2,
                            job,
                            questions,
                            requirements,
                            evidence,
                            Math.max(0, state.snapshot().verifiedEvidence().size() - evidence.size()),
                            state.snapshot().avoidExperienceDuplication())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(PlanQuestionsOutputV2 output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", PLAN_SCHEMA_V2)
                    .put("planHash", stableHash(output))
                    .put("questionCount", output.plans().size())
                    .put("avoidExperienceDuplication", output.avoidExperienceDuplication());
            var ids = result.putArray("questionIds");
            output.plans().forEach(value -> ids.add(value.questionId().toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(PlanQuestionsOutputV2 output, StepExecutionContext context) {
            if (!PLAN_SCHEMA_V2.equals(output.schemaVersion())
                    || output.plans() == null
                    || output.plans().isEmpty()
                    || output.plans().size() > 20
                    || hasDuplicates(output.plans().stream().map(QuestionPlanV2::questionId).toList())) {
                throw new IllegalArgumentException("v2 question plan is invalid");
            }
            output.plans().forEach(plan -> {
                if (plan.questionId() == null
                        || plan.questionType() == null
                        || plan.narrativeFramework() == null
                        || plan.headingPolicy() == null
                        || plan.targetCharacterCount() < 1
                        || plan.targetCharacterCount() > 10_000
                        || plan.requirementIndexes() == null
                        || plan.requirementIndexes().size() > 100
                        || plan.requirementIndexes().stream().anyMatch(value -> value == null || value < 0)) {
                    throw new IllegalArgumentException("v2 question plan item is invalid");
                }
                requireText(plan.coreMessage(), 1_000);
                requireText(plan.objective(), 1_000);
                requireText(plan.roleConnection(), 1_000);
                requireText(plan.companyConnection(), 1_000);
                requireTexts(plan.requiredElements(), 20, 1_000);
                requireTexts(plan.avoidContent(), 20, 1_000);
                requireTexts(plan.evidenceSelectionCriteria(), 20, 1_000);
            });
        }

        @Override
        protected void validateWorkflowOutput(
                PlanQuestionsOutputV2 output, StepExecutionContext context) {
            if (output.avoidExperienceDuplication() && output.plans().size() > 1) {
                List<String> messages = output.plans().stream()
                        .map(plan -> plan.coreMessage().strip().toLowerCase(Locale.ROOT))
                        .toList();
                if (new HashSet<>(messages).size() != messages.size()) {
                    throw new IllegalArgumentException("question core messages must be distinct");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                PlanQuestionsOutputV2 output, StepExecutionContext context) {
            GenerationState state = state(context);
            List<UUID> expected = state.snapshot().questions().stream()
                    .map(GenerationQuestion::questionId)
                    .toList();
            if (!expected.equals(output.plans().stream().map(QuestionPlanV2::questionId).toList())
                    || output.avoidExperienceDuplication()
                            != state.snapshot().avoidExperienceDuplication()) {
                throw domainFailure(
                        "COVER_GENERATION_PLAN_SCOPE_INVALID",
                        "자기소개서 문항 생성 계획 범위를 확인하지 못했습니다.");
            }
            for (QuestionPlanV2 plan : output.plans()) {
                GenerationQuestion question = question(state, plan.questionId());
                if ((question.maxLength() != null
                                && plan.targetCharacterCount() > question.maxLength())
                        || plan.requirementIndexes().stream().anyMatch(index ->
                                index >= requirementInputs(state).size())) {
                    throw new IllegalArgumentException("v2 plan references invalid input");
                }
            }
        }
    }

    private final class V2AnalyzeQuestionExecutor
            extends QuestionExecutor<QuestionAnalysisOutputV2> {
        private V2AnalyzeQuestionExecutor() {
            super(ANALYZE_QUESTION, ANALYSIS_SCHEMA_V2, QuestionAnalysisOutputV2.class);
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            PlanQuestionsOutputV2 plan =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV2.class);
            QuestionPlanV2 selected = plan.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            var refs = baseRefsV2(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("questionOrder", question.questionOrder());
            refs.put("questionHash", sha256(question.questionText()));
            refs.put("planHash", stableHash(selected));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    stableHash(selected),
                    tree(new AnalyzeQuestionInputV2(
                            INPUT_SCHEMA_V2,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            selected,
                            jobWritingContext(state),
                            requirementInputs(state))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(QuestionAnalysisOutputV2 output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", ANALYSIS_SCHEMA_V2)
                    .put("questionId", output.questionId().toString())
                    .put("analysisHash", stableHash(output))
                    .put("requiredElementCount", output.requiredElements().size())
                    .put("requirementCount", output.requirementIndexes().size());
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                QuestionAnalysisOutputV2 output, StepExecutionContext context) {
            if (!ANALYSIS_SCHEMA_V2.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.questionType() == null
                    || output.narrativeFramework() == null
                    || output.headingPolicy() == null
                    || output.requirementIndexes() == null
                    || output.requirementIndexes().size() > 100
                    || output.requirementIndexes().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("v2 question analysis is invalid");
            }
            requireText(output.intent(), 2_000);
            requireText(output.directAnswerDirection(), 1_000);
            requireText(output.openingCoreMessage(), 1_000);
            requireText(output.personalActionFocus(), 1_000);
            requireText(output.roleConnection(), 1_000);
            requireText(output.companyConnection(), 1_000);
            requireText(output.conclusionDirection(), 1_000);
            requireTexts(output.requiredElements(), 20, 1_000);
            requireTexts(output.avoidContent(), 20, 1_000);
            requireTexts(output.requiredEvidenceTraits(), 20, 1_000);
            if (output.situationWeight() < 0
                    || output.actionWeight() < 0
                    || output.resultWeight() < 0
                    || output.learningWeight() < 0
                    || output.situationWeight() + output.actionWeight() + output.resultWeight()
                                    + output.learningWeight()
                            != 100) {
                throw new IllegalArgumentException("v2 narrative weights are invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                QuestionAnalysisOutputV2 output, StepExecutionContext context) {
            GenerationState state = state(context);
            PlanQuestionsOutputV2 plan =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV2.class);
            QuestionPlanV2 selected = plan.plans().stream()
                    .filter(value -> value.questionId().equals(output.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            if (!output.questionId().toString().equals(context.scopeKey())
                    || output.questionType() != selected.questionType()
                    || output.narrativeFramework() != selected.narrativeFramework()
                    || output.headingPolicy() != selected.headingPolicy()
                    || output.requirementIndexes().stream().anyMatch(index ->
                            index >= requirementInputs(state).size())) {
                throw domainFailure(
                        "COVER_GENERATION_QUESTION_SCOPE_INVALID",
                        "자기소개서 문항 분석 범위를 확인하지 못했습니다.");
            }
        }
    }

    private final class V3RetrieveEvidenceExecutor extends QuestionExecutor<RetrievedEvidenceOutput> {
        private V3RetrieveEvidenceExecutor() {
            super(RETRIEVE_EVIDENCE, RETRIEVAL_SCHEMA, RetrievedEvidenceOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(StepExecutionContext context, GenerationQuestion question) {
            return context.ephemeral(ANALYZE_QUESTION, question.questionId().toString()) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context, GenerationState state, GenerationQuestion question) {
            QuestionAnalysisOutputV3 analysis = requiredScopedEphemeral(
                    context, ANALYZE_QUESTION, question.questionId(), QuestionAnalysisOutputV3.class);
            PlanQuestionsOutputV3 plans = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
            QuestionPlanV3 plan = plans.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            String requirementText = analysis.requirementIndexes().stream()
                    .map(index -> state.snapshot().job().requirements().get(index).text())
                    .collect(java.util.stream.Collectors.joining("\n"));
            String queryText = bounded(String.join(
                    "\n",
                    question.questionText(),
                    plan.coreMessage(),
                    analysis.directAnswerDirection(),
                    String.join("\n", analysis.requiredElements()),
                    String.join("\n", analysis.requiredEvidenceTraits()),
                    requirementText), 4_000);
            var refs = baseRefsV3(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("queryHash", sha256(queryText));
            refs.put("embeddingPolicyVersion", state.embeddingPolicy().version());
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    sha256(queryText + "|" + retrievalPolicyVersion(state)),
                    tree(new RetrieveEvidenceInput(
                            inputSchemaVersion(state),
                            question.questionId(),
                            queryText,
                            state.embeddingPolicy().version(),
                            state.embeddingPolicy().dimension(),
                            state.embeddingPolicy().generation(),
                            retrievalPolicyVersion(state))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            RetrieveEvidenceInput input = read(invocation.input().gatewayPayload(), RetrieveEvidenceInput.class);
            if (state.snapshot().questions().size() == 1
                    && state.snapshot().verifiedEvidence().stream()
                            .allMatch(evidence -> evidence.documentId() == null)) {
                return localResponse(new RetrievedEvidenceOutput(
                        RETRIEVAL_SCHEMA,
                        input.questionId(),
                        sha256(input.queryText()),
                        selectEvidence(state.snapshot(), input.queryText()),
                        List.of()));
            }
            AiGatewayResponse embedding = invocation.embeddingGateway().embed(new EmbeddingRequest(
                    state.embeddingPolicy().providerKey(),
                    state.embeddingPolicy().productKey(),
                    List.of(input.queryText()),
                    state.embeddingPolicy().dimension(),
                    EMBEDDING_TIMEOUT,
                    invocation.executionContext().run().priceVersion()));
            List<Double> vector = parseSingleVector(embedding.rawJson(), state.embeddingPolicy().dimension());
            List<CandidateChunk> chunks = queryPort.searchEvidenceCandidates(
                    state.snapshot().userId(), vector, MAX_CHUNK_REFS);
            RetrievedEvidenceOutput output = new RetrievedEvidenceOutput(
                    RETRIEVAL_SCHEMA,
                    input.questionId(),
                    sha256(input.queryText()),
                    selectEvidence(state.snapshot(), input.queryText()),
                    chunks.stream()
                            .sorted(Comparator.comparingDouble(CandidateChunk::distance)
                                    .thenComparing(CandidateChunk::chunkId))
                            .limit(MAX_CHUNK_REFS)
                            .map(value -> new ChunkCandidateRef(
                                    value.chunkId(),
                                    value.documentId(),
                                    bounded(value.maskedContent(), 4_000),
                                    value.distance()))
                            .toList());
            return new AiGatewayResponse(write(output), embedding.usage());
        }

        @Override
        public JsonNode minimalOutput(RetrievedEvidenceOutput output, ObjectMapper ignored) {
            return retrievalMinimalOutput(output);
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(RetrievedEvidenceOutput output, StepExecutionContext context) {
            validateRetrievedEvidence(output);
        }

        @Override
        protected void validateDomainOutput(RetrievedEvidenceOutput output, StepExecutionContext context) {
            validateRetrievedEvidenceScope(output, state(context), context.scopeKey());
        }
    }

    private final class V2RetrieveEvidenceExecutor
            extends QuestionExecutor<RetrievedEvidenceOutput> {
        private V2RetrieveEvidenceExecutor() {
            super(RETRIEVE_EVIDENCE, RETRIEVAL_SCHEMA, RetrievedEvidenceOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            return context.ephemeral(ANALYZE_QUESTION, question.questionId().toString()) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            QuestionAnalysisOutputV2 analysis = requiredScopedEphemeral(
                    context,
                    ANALYZE_QUESTION,
                    question.questionId(),
                    QuestionAnalysisOutputV2.class);
            PlanQuestionsOutputV2 plans =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV2.class);
            QuestionPlanV2 plan = plans.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            String requirementText = analysis.requirementIndexes().stream()
                    .map(index -> state.snapshot().job().requirements().get(index).text())
                    .collect(java.util.stream.Collectors.joining("\n"));
            String queryText = bounded(
                    String.join(
                            "\n",
                            question.questionText(),
                            plan.coreMessage(),
                            analysis.directAnswerDirection(),
                            String.join("\n", analysis.requiredElements()),
                            String.join("\n", analysis.requiredEvidenceTraits()),
                            requirementText),
                    4_000);
            var refs = baseRefsV2(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("queryHash", sha256(queryText));
            refs.put("analysisHash", stableHash(analysis));
            refs.put("embeddingPolicyVersion", state.embeddingPolicy().version());
            refs.put("embeddingDimension", state.embeddingPolicy().dimension());
            refs.put("embeddingGeneration", state.embeddingPolicy().generation());
            refs.put("embeddingProviderKey", state.embeddingPolicy().providerKey());
            refs.put("embeddingProductKey", state.embeddingPolicy().productKey());
            String route = state.embeddingPolicy().version()
                    + "|"
                    + state.embeddingPolicy().providerKey()
                    + "|"
                    + state.embeddingPolicy().productKey()
                    + "|"
                    + state.embeddingPolicy().dimension()
                    + "|"
                    + state.embeddingPolicy().generation();
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    sha256(queryText + "|embedding-route=" + route),
                    tree(new RetrieveEvidenceInput(
                            INPUT_SCHEMA_V2,
                            question.questionId(),
                            queryText,
                            state.embeddingPolicy().version(),
                            state.embeddingPolicy().dimension(),
                            state.embeddingPolicy().generation(),
                            RETRIEVAL_POLICY_VERSION_V2)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            RetrieveEvidenceInput input =
                    read(invocation.input().gatewayPayload(), RetrieveEvidenceInput.class);
            AiGatewayResponse embedding = invocation.embeddingGateway().embed(new EmbeddingRequest(
                    state.embeddingPolicy().providerKey(),
                    state.embeddingPolicy().productKey(),
                    List.of(input.queryText()),
                    state.embeddingPolicy().dimension(),
                    EMBEDDING_TIMEOUT,
                    invocation.executionContext().run().priceVersion()));
            List<Double> vector =
                    parseSingleVector(embedding.rawJson(), state.embeddingPolicy().dimension());
            List<CandidateChunk> chunks = queryPort.searchEvidenceCandidates(
                    state.snapshot().userId(), vector, MAX_CHUNK_REFS);
            List<UUID> evidenceIds = selectEvidence(state.snapshot(), input.queryText());
            RetrievedEvidenceOutput output = new RetrievedEvidenceOutput(
                    RETRIEVAL_SCHEMA,
                    input.questionId(),
                    sha256(input.queryText()),
                    evidenceIds,
                    chunks.stream()
                            .sorted(Comparator.comparingDouble(CandidateChunk::distance)
                                    .thenComparing(CandidateChunk::chunkId))
                            .limit(MAX_CHUNK_REFS)
                            .map(value -> new ChunkCandidateRef(
                                    value.chunkId(),
                                    value.documentId(),
                                    bounded(value.maskedContent(), 4_000),
                                    value.distance()))
                            .toList());
            return new AiGatewayResponse(write(output), embedding.usage());
        }

        @Override
        public JsonNode minimalOutput(RetrievedEvidenceOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", RETRIEVAL_SCHEMA)
                    .put("questionId", output.questionId().toString())
                    .put("queryHash", output.queryHash());
            var evidenceIds = result.putArray("evidenceIds");
            output.evidenceIds().forEach(id -> evidenceIds.add(id.toString()));
            var chunks = result.putArray("candidateChunks");
            output.candidateChunks().forEach(value -> chunks.addObject()
                    .put("chunkId", value.chunkId().toString())
                    .put("documentId", value.documentId().toString())
                    .put("distance", value.distance()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                RetrievedEvidenceOutput output, StepExecutionContext context) {
            if (!RETRIEVAL_SCHEMA.equals(output.schemaVersion())
                    || output.questionId() == null
                    || !isHash(output.queryHash())
                    || output.evidenceIds() == null
                    || output.evidenceIds().size() > MAX_EVIDENCE_PER_QUESTION
                    || hasDuplicates(output.evidenceIds())
                    || output.candidateChunks() == null
                    || output.candidateChunks().size() > MAX_CHUNK_REFS
                    || hasDuplicates(output.candidateChunks().stream()
                            .map(ChunkCandidateRef::chunkId)
                            .toList())) {
                throw new IllegalArgumentException("v2 retrieval output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                RetrievedEvidenceOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            Set<UUID> allowed = state.snapshot().verifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || !allowed.containsAll(output.evidenceIds())) {
                throw domainFailure(
                        "COVER_GENERATION_EVIDENCE_SCOPE_INVALID",
                        "자기소개서에 사용할 승인 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class V3AllocateExperiencesExecutor
            extends GenerationExecutor<ExperienceAllocationOutputV2> {
        private V3AllocateExperiencesExecutor() {
            super(ALLOCATE_EXPERIENCES, ALLOCATION_SCHEMA_V2, ExperienceAllocationOutputV2.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            PlanQuestionsOutputV3 plans = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
            Map<String, Object> analyses = context.scopedEphemeral(ANALYZE_QUESTION);
            Map<String, Object> retrieved = context.scopedEphemeral(RETRIEVE_EVIDENCE);
            List<AllocationCandidateInputV3> candidates = state.snapshot().questions().stream()
                    .filter(question -> analyses.containsKey(question.questionId().toString()))
                    .filter(question -> retrieved.containsKey(question.questionId().toString()))
                    .map(question -> {
                        QuestionPlanV3 plan = plans.plans().stream()
                                .filter(value -> value.questionId().equals(question.questionId()))
                                .findFirst()
                                .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
                        QuestionAnalysisOutputV3 analysis = cast(
                                analyses.get(question.questionId().toString()), QuestionAnalysisOutputV3.class);
                        RetrievedEvidenceOutput output = cast(
                                retrieved.get(question.questionId().toString()), RetrievedEvidenceOutput.class);
                        return new AllocationCandidateInputV3(
                                question.questionId(),
                                plan.questionType(),
                                plan.coreMessage(),
                                plan.narrativeFramework(),
                                analysis.narrativeSections(),
                                analysis.requiredEvidenceTraits(),
                                output.evidenceIds().stream().map(id -> evidencePlanning(state, id)).toList());
                    })
                    .toList();
            var refs = baseRefsV3(state);
            refs.put("candidateHash", stableHash(candidates));
            return localInput(
                    state,
                    null,
                    refs,
                    stableHash(candidates),
                    tree(new AllocateExperiencesInputV3(
                            inputSchemaVersion(state),
                            CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                            candidates,
                            state.snapshot().avoidExperienceDuplication())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AllocateExperiencesInputV3 input = read(
                    invocation.input().gatewayPayload(), AllocateExperiencesInputV3.class);
            if (input.candidates().size() == 1) {
                AllocationCandidateInputV3 candidate = input.candidates().getFirst();
                return localResponse(new ExperienceAllocationOutputV2(
                        ALLOCATION_SCHEMA_V2,
                        List.of(new ExperienceAllocationV2(
                                candidate.questionId(),
                                candidate.candidateEvidence().stream()
                                        .map(EvidencePlanningInput::evidenceId)
                                        .toList(),
                                null,
                                null))));
            }
            return chat(invocation);
        }

        @Override
        protected void validateJavaRecord(ExperienceAllocationOutputV2 output, StepExecutionContext context) {
            validateAllocation(output);
        }

        @Override
        protected void validateDomainOutput(ExperienceAllocationOutputV2 output, StepExecutionContext context) {
            validateAllocationScope(output, state(context), context);
        }
    }

    private final class V2AllocateExperiencesExecutor
            extends GenerationExecutor<ExperienceAllocationOutputV2> {
        private V2AllocateExperiencesExecutor() {
            super(ALLOCATE_EXPERIENCES, ALLOCATION_SCHEMA_V2, ExperienceAllocationOutputV2.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            PlanQuestionsOutputV2 plans =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV2.class);
            Map<String, Object> analyses = context.scopedEphemeral(ANALYZE_QUESTION);
            Map<String, Object> retrieved = context.scopedEphemeral(RETRIEVE_EVIDENCE);
            List<AllocationCandidateInputV2> candidates = state.snapshot().questions().stream()
                    .filter(question -> analyses.containsKey(question.questionId().toString()))
                    .filter(question -> retrieved.containsKey(question.questionId().toString()))
                    .map(question -> {
                        QuestionPlanV2 plan = plans.plans().stream()
                                .filter(value -> value.questionId().equals(question.questionId()))
                                .findFirst()
                                .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
                        QuestionAnalysisOutputV2 analysis = cast(
                                analyses.get(question.questionId().toString()),
                                QuestionAnalysisOutputV2.class);
                        RetrievedEvidenceOutput output = cast(
                                retrieved.get(question.questionId().toString()),
                                RetrievedEvidenceOutput.class);
                        return new AllocationCandidateInputV2(
                                question.questionId(),
                                plan.questionType(),
                                plan.coreMessage(),
                                plan.narrativeFramework(),
                                analysis.requiredEvidenceTraits(),
                                output.evidenceIds().stream()
                                        .map(id -> evidencePlanning(state, id))
                                        .toList());
                    })
                    .toList();
            var refs = baseRefsV2(state);
            refs.put("candidateHash", stableHash(candidates));
            refs.put("questionCount", candidates.size());
            return localInput(
                    state,
                    null,
                    refs,
                    stableHash(candidates),
                    tree(new AllocateExperiencesInputV2(
                            INPUT_SCHEMA_V2,
                            candidates,
                            state.snapshot().avoidExperienceDuplication())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateJavaRecord(
                ExperienceAllocationOutputV2 output, StepExecutionContext context) {
            if (!ALLOCATION_SCHEMA_V2.equals(output.schemaVersion())
                    || output.allocations() == null
                    || output.allocations().size() > 20
                    || hasDuplicates(output.allocations().stream()
                            .map(ExperienceAllocationV2::questionId)
                            .toList())) {
                throw new IllegalArgumentException("v2 allocation is invalid");
            }
            output.allocations().forEach(value -> {
                if (value.questionId() == null
                        || value.evidenceIds() == null
                        || value.evidenceIds().size() > MAX_EVIDENCE_PER_QUESTION
                        || hasDuplicates(value.evidenceIds())) {
                    throw new IllegalArgumentException("v2 allocation item is invalid");
                }
                if (value.duplicationReason() != null && value.duplicationReason().length() > 1_000) {
                    throw new IllegalArgumentException("duplication reason is invalid");
                }
                if (value.distinctEmphasis() != null && value.distinctEmphasis().length() > 1_000) {
                    throw new IllegalArgumentException("distinct emphasis is invalid");
                }
            });
        }

        @Override
        protected void validateDomainOutput(
                ExperienceAllocationOutputV2 output, StepExecutionContext context) {
            GenerationState state = state(context);
            Map<String, Object> retrieved = context.scopedEphemeral(RETRIEVE_EVIDENCE);
            List<UUID> expected = state.snapshot().questions().stream()
                    .map(GenerationQuestion::questionId)
                    .filter(id -> retrieved.containsKey(id.toString()))
                    .toList();
            if (!expected.equals(output.allocations().stream()
                    .map(ExperienceAllocationV2::questionId)
                    .toList())) {
                throw domainFailure(
                        "COVER_GENERATION_ALLOCATION_SCOPE_INVALID",
                        "자기소개서 경험 배분 범위를 확인하지 못했습니다.");
            }
            Map<UUID, Integer> useCount = new LinkedHashMap<>();
            for (ExperienceAllocationV2 allocation : output.allocations()) {
                RetrievedEvidenceOutput candidate = cast(
                        retrieved.get(allocation.questionId().toString()),
                        RetrievedEvidenceOutput.class);
                if (!new HashSet<>(candidate.evidenceIds()).containsAll(allocation.evidenceIds())) {
                    throw domainFailure(
                            "COVER_GENERATION_ALLOCATION_EVIDENCE_INVALID",
                            "자기소개서 경험 배분 근거를 확인하지 못했습니다.");
                }
                allocation.evidenceIds().forEach(id -> useCount.merge(id, 1, Integer::sum));
            }
            if (state.snapshot().avoidExperienceDuplication()) {
                output.allocations().forEach(allocation -> {
                    boolean duplicates = allocation.evidenceIds().stream()
                            .anyMatch(id -> useCount.getOrDefault(id, 0) > 1);
                    if (duplicates
                            && ((allocation.duplicationReason() == null
                                            || allocation.duplicationReason().isBlank())
                                    || (allocation.distinctEmphasis() == null
                                            || allocation.distinctEmphasis().isBlank()))) {
                        throw new IllegalArgumentException(
                                "duplicated v2 experience requires reason and distinct emphasis");
                    }
                });
            }
        }
    }

    private final class V3WriteAnswerExecutor extends QuestionExecutor<WrittenAnswerOutputV3> {
        private V3WriteAnswerExecutor() {
            super(WRITE_ANSWER, ANSWER_SCHEMA_V3, WrittenAnswerOutputV3.class);
        }

        @Override
        protected boolean eligibleQuestion(StepExecutionContext context, GenerationQuestion question) {
            return context.ephemeral(ALLOCATE_EXPERIENCES) instanceof ExperienceAllocationOutputV2 allocation
                    && context.ephemeral(ANALYZE_QUESTION, question.questionId().toString()) != null
                    && allocation.allocations().stream()
                            .anyMatch(item -> item.questionId().equals(question.questionId()));
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context, GenerationState state, GenerationQuestion question) {
            PlanQuestionsOutputV3 plans = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
            QuestionPlanV3 plan = plans.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            QuestionAnalysisOutputV3 analysis = requiredScopedEphemeral(
                    context, ANALYZE_QUESTION, question.questionId(), QuestionAnalysisOutputV3.class);
            ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                    context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
            ExperienceAllocationV2 allocation = allocations.allocations().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            List<ApprovedEvidenceInput> evidence = allocation.evidenceIds().stream()
                    .map(id -> approvedEvidenceV2(state, id))
                    .toList();
            List<OtherQuestionStrategyInput> otherQuestions = plans.plans().stream()
                    .filter(value -> !value.questionId().equals(question.questionId()))
                    .map(value -> {
                        ExperienceAllocationV2 sibling = allocations.allocations().stream()
                                .filter(item -> item.questionId().equals(value.questionId()))
                                .findFirst()
                                .orElse(null);
                        return new OtherQuestionStrategyInput(
                                value.questionId(),
                                value.coreMessage(),
                                sibling == null ? List.of() : sibling.evidenceIds(),
                                sibling == null ? null : sibling.distinctEmphasis());
                    })
                    .toList();
            BoundedText current = CoverLetterWorkflowV3Policy.bound(
                    question.currentPlainText(),
                    question.maxLength() == null ? MAX_TEXT : Math.min(MAX_TEXT, question.maxLength()));
            var refs = baseRefsV3(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("currentAnswerOriginalCharacterCount", current.originalCharacterCount());
            refs.put("currentAnswerProvidedCharacterCount", current.providedCharacterCount());
            refs.put("currentAnswerTruncated", current.truncated());
            refs.put("currentAnswerFullTextHash", current.fullTextHash());
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    stableHash(plan) + "|" + current.fullTextHash(),
                    writeAnswerInput(
                            state,
                            question,
                            plan,
                            analysis,
                            evidence,
                            current,
                            otherQuestions));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(WrittenAnswerOutputV3 output, ObjectMapper ignored) {
            TipTapDocumentDto content = mapTipTap(output.content());
            String text = plainText(content);
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", ANSWER_SCHEMA_V3)
                    .put("questionId", output.questionId().toString())
                    .put("answerHash", sha256(write(content)))
                    .put("characterCount", text.codePointCount(0, text.length()));
            var evidenceIds = result.putArray("evidenceIds");
            output.claims().stream().map(EvidenceClaimDraftV3::evidenceId).distinct().sorted()
                    .forEach(id -> evidenceIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(WrittenAnswerOutputV3 output, StepExecutionContext context) {
            if (!ANSWER_SCHEMA_V3.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.content() == null) {
                throw new IllegalArgumentException("v3 written answer is invalid");
            }
            TipTapDocumentDto content = mapTipTap(output.content());
            validateTipTap(content);
            String text = plainText(content);
            CoverLetterWorkflowV3Policy.validateDistinctClaims(output.claims(), text);
            if (CoverLetterWorkflowV3Policy.hasFactualPattern(text) && output.claims().isEmpty()) {
                throw new IllegalArgumentException("factual answer requires grounded claims");
            }
            int count = text.codePointCount(0, text.length());
            if (text.isBlank() || count > MAX_TEXT) {
                throw repairable(
                        "COVER_GENERATION_ANSWER_CONTENT_INVALID",
                        "Return a nonblank answer whose plain-text code-point count is at most 20000. Keep the supplied safe TipTap node and mark allowlist.");
            }
        }

        @Override
        protected void validateWorkflowOutput(
                WrittenAnswerOutputV3 output, StepExecutionContext context) {
            if (!output.questionId().toString().equals(context.scopeKey())) {
                throw repairable(
                        ValidationPhase.WORKFLOW_CONTEXT,
                        "COVER_GENERATION_ANSWER_SCOPE_INVALID",
                        "Copy the supplied questionId exactly and answer only that question.");
            }
            GenerationState state = state(context);
            GenerationQuestion question = question(state, output.questionId());
            ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                    context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
            Set<UUID> allowed = allocations.allocations().stream()
                    .filter(value -> value.questionId().equals(output.questionId()))
                    .flatMap(value -> value.evidenceIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            if (output.claims().stream().map(EvidenceClaimDraftV3::evidenceId)
                    .anyMatch(id -> !allowed.contains(id))) {
                throw repairable(
                        ValidationPhase.WORKFLOW_CONTEXT,
                        "COVER_GENERATION_ANSWER_EVIDENCE_INVALID",
                        "Use only evidenceId values supplied for this question. Remove any claim that cannot use an allowed evidenceId without inventing support.");
            }
            String text = plainText(mapTipTap(output.content()));
            int count = text.codePointCount(0, text.length());
            if (question.maxLength() != null && count > question.maxLength()) {
                throw repairable(
                        ValidationPhase.WORKFLOW_CONTEXT,
                        "COVER_GENERATION_ANSWER_LENGTH_INVALID",
                        "Shorten the answer so its final plain-text code-point count is no greater than the supplied maxLength. Preserve only grounded claims and answer the question directly.");
            }
        }

        @Override
        protected void validateDomainOutput(WrittenAnswerOutputV3 output, StepExecutionContext context) {
            GenerationState state = state(context);
            GenerationQuestion question = question(state, output.questionId());
            ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                    context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
            Set<UUID> allowed = allocations.allocations().stream()
                    .filter(value -> value.questionId().equals(output.questionId()))
                    .flatMap(value -> value.evidenceIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            String text = plainText(mapTipTap(output.content()));
            int count = text.codePointCount(0, text.length());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || output.claims().stream().map(EvidenceClaimDraftV3::evidenceId)
                            .anyMatch(id -> !allowed.contains(id))
                    || text.isBlank()
                    || count > MAX_TEXT
                    || (question.maxLength() != null && count > question.maxLength())) {
                throw domainFailure(
                        "COVER_GENERATION_ANSWER_INVALID",
                        "자기소개서 답변의 구조와 승인 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class V2WriteAnswerExecutor
            extends QuestionExecutor<WrittenAnswerOutputV2> {
        private V2WriteAnswerExecutor() {
            super(WRITE_ANSWER, ANSWER_SCHEMA_V2, WrittenAnswerOutputV2.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            Object value = context.ephemeral(ALLOCATE_EXPERIENCES);
            return value instanceof ExperienceAllocationOutputV2 allocation
                    && context.ephemeral(ANALYZE_QUESTION, question.questionId().toString()) != null
                    && allocation.allocations().stream()
                            .anyMatch(item -> item.questionId().equals(question.questionId()));
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            PlanQuestionsOutputV2 plans =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV2.class);
            QuestionPlanV2 plan = plans.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            QuestionAnalysisOutputV2 analysis = requiredScopedEphemeral(
                    context,
                    ANALYZE_QUESTION,
                    question.questionId(),
                    QuestionAnalysisOutputV2.class);
            ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                    context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
            ExperienceAllocationV2 allocation = allocations.allocations().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            List<ApprovedEvidenceInput> evidence = allocation.evidenceIds().stream()
                    .map(id -> approvedEvidenceV2(state, id))
                    .toList();
            List<OtherQuestionStrategyInput> otherQuestions = plans.plans().stream()
                    .filter(value -> !value.questionId().equals(question.questionId()))
                    .map(value -> {
                        ExperienceAllocationV2 sibling = allocations.allocations().stream()
                                .filter(item -> item.questionId().equals(value.questionId()))
                                .findFirst()
                                .orElse(null);
                        return new OtherQuestionStrategyInput(
                                value.questionId(),
                                value.coreMessage(),
                                sibling == null ? List.of() : sibling.evidenceIds(),
                                sibling == null ? null : sibling.distinctEmphasis());
                    })
                    .toList();
            var refs = baseRefsV2(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("planHash", stableHash(plan));
            refs.put("analysisHash", stableHash(analysis));
            refs.put("allocationHash", stableHash(allocation));
            refs.put("currentAnswerVersionId",
                    question.currentAnswerVersionId() == null
                            ? null
                            : question.currentAnswerVersionId().toString());
            refs.put("currentAnswerHash",
                    question.currentPlainText() == null ? null : sha256(question.currentPlainText()));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    stableHash(plan) + "|" + stableHash(analysis) + "|" + stableHash(allocation),
                    tree(new WriteAnswerInputV2(
                            INPUT_SCHEMA_V2,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            plan,
                            analysis,
                            plan.targetCharacterCount(),
                            evidence,
                            jobWritingContext(state),
                            question.currentAnswerVersionId(),
                            bounded(question.currentPlainText(), MAX_CURRENT_ANSWER),
                            otherQuestions,
                            plan.headingPolicy())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(WrittenAnswerOutputV2 output, ObjectMapper ignored) {
            TipTapDocumentDto content = mapTipTap(output.content());
            String text = plainText(content);
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", ANSWER_SCHEMA_V2)
                    .put("questionId", output.questionId().toString())
                    .put("answerHash", sha256(write(content)))
                    .put("characterCount", text.codePointCount(0, text.length()));
            var evidenceIds = result.putArray("evidenceIds");
            output.claims().stream()
                    .map(EvidenceClaimDraft::evidenceId)
                    .distinct()
                    .sorted()
                    .forEach(id -> evidenceIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                WrittenAnswerOutputV2 output, StepExecutionContext context) {
            if (!ANSWER_SCHEMA_V2.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.content() == null
                    || output.claims() == null
                    || output.claims().size() > 50) {
                throw new IllegalArgumentException("v2 written answer is invalid");
            }
            validateTipTap(mapTipTap(output.content()));
            output.claims().forEach(value -> {
                if (value.evidenceId() == null) {
                    throw new IllegalArgumentException("v2 answer claim is invalid");
                }
                requireText(value.claimText(), 2_000);
            });
        }

        @Override
        protected void validateDomainOutput(
                WrittenAnswerOutputV2 output, StepExecutionContext context) {
            GenerationState state = state(context);
            GenerationQuestion question = question(state, output.questionId());
            ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                    context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
            Set<UUID> allowed = allocations.allocations().stream()
                    .filter(value -> value.questionId().equals(output.questionId()))
                    .flatMap(value -> value.evidenceIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            String text = plainText(mapTipTap(output.content()));
            int count = text.codePointCount(0, text.length());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || output.claims().stream()
                            .map(EvidenceClaimDraft::evidenceId)
                            .anyMatch(id -> !allowed.contains(id))
                    || text.isBlank()
                    || count > MAX_TEXT
                    || (question.maxLength() != null && count > question.maxLength())) {
                throw domainFailure(
                        "COVER_GENERATION_ANSWER_INVALID",
                        "자기소개서 답변의 구조와 승인 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class V3FactCheckAnswerExecutor
            extends QuestionExecutor<FactCheckAnswerOutputV3> {
        private V3FactCheckAnswerExecutor() {
            super(FACT_CHECK_ANSWER, FACT_CHECK_SCHEMA_V3, FactCheckAnswerOutputV3.class);
        }

        @Override
        protected boolean eligibleQuestion(StepExecutionContext context, GenerationQuestion question) {
            String scope = question.questionId().toString();
            return context.ephemeral(WRITE_ANSWER, scope) != null
                    && context.ephemeral(RETRIEVE_EVIDENCE, scope) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context, GenerationState state, GenerationQuestion question) {
            WrittenAnswerOutputV3 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, question.questionId(), WrittenAnswerOutputV3.class);
            RetrievedEvidenceOutput retrieval = requiredScopedEphemeral(
                    context, RETRIEVE_EVIDENCE, question.questionId(), RetrievedEvidenceOutput.class);
            PlanQuestionsOutputV3 plans = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
            QuestionPlanV3 plan = plans.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            QuestionAnalysisOutputV3 analysis = requiredScopedEphemeral(
                    context, ANALYZE_QUESTION, question.questionId(), QuestionAnalysisOutputV3.class);
            ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                    context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
            String answerText = plainText(mapTipTap(answer.content()));
            List<SiblingAnswerInputV3> siblings = context.scopedEphemeral(WRITE_ANSWER).entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(question.questionId().toString()))
                    .map(entry -> {
                        WrittenAnswerOutputV3 sibling = cast(entry.getValue(), WrittenAnswerOutputV3.class);
                        QuestionPlanV3 siblingPlan = plans.plans().stream()
                                .filter(value -> value.questionId().equals(sibling.questionId()))
                                .findFirst()
                                .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
                        ExperienceAllocationV2 siblingAllocation = allocations.allocations().stream()
                                .filter(value -> value.questionId().equals(sibling.questionId()))
                                .findFirst()
                                .orElse(null);
                        String fullText = plainText(mapTipTap(sibling.content()));
                        return new SiblingAnswerInputV3(
                                sibling.questionId(),
                                siblingPlan.coreMessage(),
                                siblingAllocation == null ? null : siblingAllocation.distinctEmphasis(),
                                CoverLetterWorkflowV3Policy.bound(fullText, MAX_SIBLING_ANSWER),
                                sibling.claims().stream()
                                        .map(EvidenceClaimDraftV3::evidenceId)
                                        .distinct()
                                        .toList());
                    })
                    .sorted(Comparator.comparing(SiblingAnswerInputV3::questionId))
                    .toList();
            var refs = baseRefsV3(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("answerHash", sha256(answerText));
            refs.put("duplicationPolicyVersion", CoverLetterWorkflowV3Policy.DUPLICATION_POLICY_VERSION);
            var siblingMetadata = refs.putArray("siblingAnswerMetadata");
            siblings.forEach(value -> siblingMetadata.addObject()
                    .put("questionId", value.questionId().toString())
                    .put("originalCharacterCount", value.answer().originalCharacterCount())
                    .put("providedCharacterCount", value.answer().providedCharacterCount())
                    .put("truncated", value.answer().truncated())
                    .put("fullTextHash", value.answer().fullTextHash()));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    sha256(answerText) + "|" + stableHash(siblings),
                    tree(new FactCheckAnswerInputV3(
                            inputSchemaVersion(state),
                            CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            plan,
                            analysis,
                            mapTipTap(answer.content()),
                            answerText,
                            answer.claims(),
                            retrieval.evidenceIds().stream()
                                    .map(id -> approvedEvidenceV2(state, id))
                                    .toList(),
                            requirementInputs(state),
                            retrieval.candidateChunks(),
                            siblings,
                            state.snapshot().job().analysisOutdated(),
                            CoverLetterWorkflowV3Policy.DUPLICATION_POLICY_VERSION)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            if (state.snapshot().questions().size() == 1) {
                FactCheckAnswerInputV3 input = read(
                        invocation.input().gatewayPayload(), FactCheckAnswerInputV3.class);
                List<VerificationIssueDraftV2> issues = input.claims().stream()
                        .map(claim -> new VerificationIssueDraftV2(
                                VerificationIssueKind.FACTUAL,
                                VerificationIssueCode.UNVERIFIED_CLAIM,
                                IssueSeverity.ERROR,
                                "생성된 사실 주장은 제출 전에 연결된 근거와 직접 대조해 확인해 주세요.",
                                null,
                                List.of(claim.evidenceId())))
                        .toList();
                return localResponse(new FactCheckAnswerOutputV3(
                        FACT_CHECK_SCHEMA_V3,
                        input.questionId(),
                        issues,
                        issues.isEmpty()
                                ? List.of()
                                : List.of("표시된 주장을 근거 원문과 대조한 뒤 제출해 주세요."),
                        List.of()));
            }
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(FactCheckAnswerOutputV3 output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", FACT_CHECK_SCHEMA_V3)
                    .put("questionId", output.questionId().toString())
                    .put("factCheckHash", stableHash(output))
                    .put("issueCount", output.issues().size())
                    .put("verifiedClaimCount", output.verifiedClaims().size());
            var evidenceIds = result.putArray("evidenceIds");
            referencedEvidenceV3(output).stream().sorted().forEach(id -> evidenceIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(FactCheckAnswerOutputV3 output, StepExecutionContext context) {
            if (!FACT_CHECK_SCHEMA_V3.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.issues() == null
                    || output.issues().size() > 100
                    || output.suggestions() == null
                    || output.suggestions().size() > 20
                    || output.verifiedClaims() == null
                    || output.verifiedClaims().size() > 100) {
                throw new IllegalArgumentException("v3 fact check is invalid");
            }
            output.issues().forEach(issue -> {
                CoverLetterWorkflowV3Policy.validateIssueCompatibility(
                        issue.issueKind(), issue.code(), issue.severity());
                requireText(issue.message(), 1_000);
                if (!KoreanUserFacingTextPolicy.containsKorean(issue.message())) {
                    throw new IllegalArgumentException("issue message must be Korean user-facing text");
                }
                if (issue.relatedText() != null && issue.relatedText().length() > 1_000) {
                    throw new IllegalArgumentException("related text is invalid");
                }
                if (issue.evidenceIds() == null
                        || issue.evidenceIds().size() > 20
                        || hasDuplicates(issue.evidenceIds())) {
                    throw new IllegalArgumentException("issue evidence is invalid");
                }
            });
            requireTexts(output.suggestions(), 20, 1_000);
            if (output.suggestions().stream().anyMatch(value ->
                    !KoreanUserFacingTextPolicy.containsKorean(value))) {
                throw new IllegalArgumentException("suggestions must be Korean user-facing text");
            }
            String answerText = plainText(mapTipTap(requiredScopedEphemeral(
                            context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV3.class)
                    .content()));
            Set<String> seen = new HashSet<>();
            output.verifiedClaims().forEach(value -> {
                requireText(value.exactAnswerExcerpt(), 2_000);
                if (!value.supported() || value.evidenceIds() == null || value.evidenceIds().isEmpty()
                        || value.evidenceIds().size() > 20 || hasDuplicates(value.evidenceIds())
                        || !CoverLetterWorkflowV3Policy.normalize(answerText)
                                .contains(CoverLetterWorkflowV3Policy.normalize(value.exactAnswerExcerpt()))
                        || !seen.add(CoverLetterWorkflowV3Policy.normalize(value.exactAnswerExcerpt()))) {
                    throw new IllegalArgumentException("verified claim must be positive grounded provenance");
                }
            });
        }

        @Override
        protected void validateWorkflowOutput(FactCheckAnswerOutputV3 output, StepExecutionContext context) {
            GenerationState state = state(context);
            WrittenAnswerOutputV3 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV3.class);
            String answerText = plainText(mapTipTap(answer.content()));
            if (!KoreanUserFacingTextPolicy.containsKorean(answerText)) {
                throw new IllegalArgumentException("answer must be Korean user-facing text");
            }
            if (!unsupportedNumbers(answerText, state.snapshot().verifiedEvidence()).isEmpty()
                    && output.issues().stream().noneMatch(issue ->
                            issue.issueKind() == VerificationIssueKind.FACTUAL
                                    && (issue.code() == VerificationIssueCode.UNVERIFIED_CLAIM
                                            || issue.code() == VerificationIssueCode.CONTRADICTION)
                                    && issue.severity() == IssueSeverity.ERROR)) {
                throw new IllegalArgumentException("unsupported numbers require a factual error");
            }
            if (requiresDuplicationWarningV3(output.questionId(), answerText, context)
                    && output.issues().stream().noneMatch(issue ->
                            issue.issueKind() == VerificationIssueKind.DUPLICATION
                                    && issue.code() == VerificationIssueCode.OTHER
                                    && issue.severity() == IssueSeverity.WARNING)) {
                throw new IllegalArgumentException("cross-answer duplication requires a warning");
            }
        }

        @Override
        protected void validateDomainOutput(FactCheckAnswerOutputV3 output, StepExecutionContext context) {
            Set<UUID> allowed = state(context).snapshot().verifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || !allowed.containsAll(referencedEvidenceV3(output))) {
                throw domainFailure(
                        "COVER_GENERATION_FACT_CHECK_EVIDENCE_INVALID",
                        "자기소개서 사실 검증 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class V2FactCheckAnswerExecutor
            extends QuestionExecutor<FactCheckAnswerOutputV2> {
        private V2FactCheckAnswerExecutor() {
            super(FACT_CHECK_ANSWER, FACT_CHECK_SCHEMA_V2, FactCheckAnswerOutputV2.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            String scope = question.questionId().toString();
            return context.ephemeral(WRITE_ANSWER, scope) != null
                    && context.ephemeral(RETRIEVE_EVIDENCE, scope) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            WrittenAnswerOutputV2 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, question.questionId(), WrittenAnswerOutputV2.class);
            RetrievedEvidenceOutput retrieval = requiredScopedEphemeral(
                    context, RETRIEVE_EVIDENCE, question.questionId(), RetrievedEvidenceOutput.class);
            PlanQuestionsOutputV2 plans =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV2.class);
            QuestionPlanV2 plan = plans.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            QuestionAnalysisOutputV2 analysis = requiredScopedEphemeral(
                    context,
                    ANALYZE_QUESTION,
                    question.questionId(),
                    QuestionAnalysisOutputV2.class);
            TipTapDocumentDto content = mapTipTap(answer.content());
            String answerText = plainText(content);
            List<SiblingAnswerInput> siblings = context.scopedEphemeral(WRITE_ANSWER).entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(question.questionId().toString()))
                    .map(entry -> {
                        WrittenAnswerOutputV2 sibling = cast(entry.getValue(), WrittenAnswerOutputV2.class);
                        QuestionPlanV2 siblingPlan = plans.plans().stream()
                                .filter(value -> value.questionId().equals(sibling.questionId()))
                                .findFirst()
                                .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
                        return new SiblingAnswerInput(
                                sibling.questionId(),
                                siblingPlan.coreMessage(),
                                bounded(plainText(mapTipTap(sibling.content())), MAX_SIBLING_ANSWER),
                                sibling.claims().stream()
                                        .map(EvidenceClaimDraft::evidenceId)
                                        .distinct()
                                        .toList());
                    })
                    .sorted(Comparator.comparing(SiblingAnswerInput::questionId))
                    .toList();
            var refs = baseRefsV2(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("answerHash", sha256(write(content)));
            refs.put("siblingAnswerHash", stableHash(siblings));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    sha256(answerText) + "|" + stableHash(siblings),
                    tree(new FactCheckAnswerInputV2(
                            INPUT_SCHEMA_V2,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            plan,
                            analysis,
                            content,
                            answerText,
                            answer.claims(),
                            retrieval.evidenceIds().stream()
                                    .map(id -> approvedEvidenceV2(state, id))
                                    .toList(),
                            requirementInputs(state),
                            retrieval.candidateChunks(),
                            siblings,
                            state.snapshot().job().analysisOutdated())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        public JsonNode minimalOutput(FactCheckAnswerOutputV2 output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", FACT_CHECK_SCHEMA_V2)
                    .put("questionId", output.questionId().toString())
                    .put("factCheckHash", stableHash(output))
                    .put("issueCount", output.issues().size())
                    .put("verifiedClaimCount", output.verifiedClaims().size());
            var evidenceIds = result.putArray("evidenceIds");
            referencedEvidence(output).stream().sorted().forEach(id -> evidenceIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                FactCheckAnswerOutputV2 output, StepExecutionContext context) {
            if (!FACT_CHECK_SCHEMA_V2.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.issues() == null
                    || output.issues().size() > 100
                    || output.suggestions() == null
                    || output.suggestions().size() > 20
                    || output.verifiedClaims() == null
                    || output.verifiedClaims().size() > 100) {
                throw new IllegalArgumentException("v2 fact check is invalid");
            }
            output.issues().forEach(issue -> {
                if (issue.code() == null
                        || issue.severity() == null
                        || issue.issueKind() == null
                        || ((issue.issueKind() == VerificationIssueKind.QUALITY
                                        || issue.issueKind() == VerificationIssueKind.DUPLICATION)
                                && issue.severity() != IssueSeverity.WARNING)) {
                    throw new IllegalArgumentException("v2 fact issue severity is invalid");
                }
                requireText(issue.message(), 1_000);
                if (issue.relatedText() != null && issue.relatedText().length() > 1_000) {
                    throw new IllegalArgumentException("v2 related text is invalid");
                }
                if (issue.evidenceIds() == null
                        || issue.evidenceIds().size() > 20
                        || hasDuplicates(issue.evidenceIds())) {
                    throw new IllegalArgumentException("v2 issue evidence is invalid");
                }
            });
            requireTexts(output.suggestions(), 20, 1_000);
            output.verifiedClaims().forEach(value -> {
                requireText(value.claim(), 2_000);
                if (value.evidenceIds() == null
                        || value.evidenceIds().size() > 20
                        || hasDuplicates(value.evidenceIds())) {
                    throw new IllegalArgumentException("v2 verified claim is invalid");
                }
            });
        }

        @Override
        protected void validateWorkflowOutput(
                FactCheckAnswerOutputV2 output, StepExecutionContext context) {
            GenerationState state = state(context);
            WrittenAnswerOutputV2 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV2.class);
            String answerText = plainText(mapTipTap(answer.content()));
            if (!unsupportedNumbers(answerText, state.snapshot().verifiedEvidence()).isEmpty()
                    && output.issues().stream().noneMatch(issue ->
                            (issue.code() == VerificationIssueCode.UNVERIFIED_CLAIM
                                            || issue.code() == VerificationIssueCode.CONTRADICTION)
                                    && issue.severity() == IssueSeverity.ERROR)) {
                throw new IllegalArgumentException("unsupported numbers require an error issue");
            }
            if (answer.claims().isEmpty()
                    && output.issues().stream().noneMatch(issue ->
                            issue.code() == VerificationIssueCode.UNVERIFIED_CLAIM)) {
                throw new IllegalArgumentException(
                        "an ungrounded v2 answer requires an issue");
            }
            if (requiresDuplicationWarning(output.questionId(), answerText, context)
                    && output.issues().stream().noneMatch(issue ->
                            issue.issueKind() == VerificationIssueKind.DUPLICATION
                                    && issue.severity() == IssueSeverity.WARNING)) {
                throw new IllegalArgumentException("cross-answer duplication requires a warning");
            }
        }

        @Override
        protected void validateDomainOutput(
                FactCheckAnswerOutputV2 output, StepExecutionContext context) {
            GenerationState state = state(context);
            Set<UUID> allowed = state.snapshot().verifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || !allowed.containsAll(referencedEvidence(output))) {
                throw domainFailure(
                        "COVER_GENERATION_FACT_CHECK_EVIDENCE_INVALID",
                        "자기소개서 사실 검증 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class V3ApplyAnswerExecutor extends QuestionExecutor<ApplyAnswerRequestOutput> {
        private V3ApplyAnswerExecutor() {
            super(APPLY_ANSWER_VERSION, APPLY_SCHEMA, ApplyAnswerRequestOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(StepExecutionContext context, GenerationQuestion question) {
            String scope = question.questionId().toString();
            return context.ephemeral(WRITE_ANSWER, scope) != null
                    && context.ephemeral(FACT_CHECK_ANSWER, scope) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context, GenerationState state, GenerationQuestion question) {
            WrittenAnswerOutputV3 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, question.questionId(), WrittenAnswerOutputV3.class);
            FactCheckAnswerOutputV3 factCheck = requiredScopedEphemeral(
                    context, FACT_CHECK_ANSWER, question.questionId(), FactCheckAnswerOutputV3.class);
            String answerHash = sha256(write(mapTipTap(answer.content())));
            String factCheckHash = stableHash(factCheck);
            var refs = baseRefsV3(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("answerHash", answerHash);
            refs.put("factCheckHash", factCheckHash);
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    state.agentRunId() + "|" + answerHash + "|" + factCheckHash,
                    tree(new ApplyAnswerRequestInput(
                            inputSchemaVersion(state),
                            state.agentRunId(),
                            state.snapshot().coverLetterId(),
                            question.questionId(),
                            state.snapshot().coverLetterVersion(),
                            question.currentAnswerVersionId(),
                            state.snapshot().snapshotHash(),
                            answerHash,
                            factCheckHash)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ApplyAnswerRequestInput input = read(invocation.input().gatewayPayload(), ApplyAnswerRequestInput.class);
            return localResponse(new ApplyAnswerRequestOutput(
                    APPLY_SCHEMA,
                    input.agentRunId(),
                    input.coverLetterId(),
                    input.questionId(),
                    input.expectedCoverLetterVersion(),
                    input.expectedCurrentVersionId(),
                    input.snapshotHash(),
                    input.answerHash(),
                    input.factCheckHash()));
        }

        @Override
        public DomainStepCompletion completeFresh(
                ApplyAnswerRequestOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            WrittenAnswerOutputV3 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV3.class);
            FactCheckAnswerOutputV3 factCheck = requiredScopedEphemeral(
                    context, FACT_CHECK_ANSWER, output.questionId(), FactCheckAnswerOutputV3.class);
            AppliedAnswer applied;
            try {
                applied = commandPort.applyGeneratedAnswer(
                        context.run().userId(),
                        context.run().id(),
                        new PersistGeneratedAnswer(
                                output.coverLetterId(),
                                output.questionId(),
                                output.expectedCoverLetterVersion(),
                                output.expectedCurrentVersionId(),
                                output.snapshotHash(),
                                mapTipTap(answer.content()),
                                evidenceUsesV3(answer, factCheck),
                                verificationResultV3(factCheck)));
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            JsonNode appliedOutput = tree(new AppliedAnswerOutput(
                    APPLY_SCHEMA,
                    output.questionId(),
                    applied.answerVersion().id(),
                    applied.generationVerification().id(),
                    applied.answerVersion().sourceType().name(),
                    applied.answerVersion().characterCount(),
                    applied.coverLetterVersion(),
                    output.snapshotHash()));
            return new DomainStepCompletion(
                    appliedOutput,
                    Optional.empty(),
                    new PartialResult(
                            List.of(output.questionId().toString()),
                            List.of(),
                            List.of(new ResourceReference(
                                    "COVER_LETTER_ANSWER_VERSION",
                                    applied.answerVersion().id(),
                                    "자기소개서 답변 버전"))));
        }

        @Override
        public Optional<PartialResult> partialResultFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            AppliedAnswerOutput output = read(minimalOutput, AppliedAnswerOutput.class);
            return Optional.of(new PartialResult(
                    List.of(output.questionId().toString()),
                    List.of(),
                    List.of(new ResourceReference(
                            "COVER_LETTER_ANSWER_VERSION", output.answerVersionId(), "자기소개서 답변 버전"))));
        }

        @Override
        public Object ephemeralOutputFromMinimal(JsonNode minimalOutput) {
            return read(minimalOutput, AppliedAnswerOutput.class);
        }

        @Override
        protected void validateJavaRecord(ApplyAnswerRequestOutput output, StepExecutionContext context) {
            validateApplyOutput(output);
        }

        @Override
        protected void validateDomainOutput(ApplyAnswerRequestOutput output, StepExecutionContext context) {
            WrittenAnswerOutputV3 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV3.class);
            FactCheckAnswerOutputV3 factCheck = requiredScopedEphemeral(
                    context, FACT_CHECK_ANSWER, output.questionId(), FactCheckAnswerOutputV3.class);
            if (!output.agentRunId().equals(context.run().id())
                    || !output.coverLetterId().equals(context.run().resourceId())
                    || !output.questionId().toString().equals(context.scopeKey())
                    || !output.answerHash().equals(sha256(write(mapTipTap(answer.content()))))
                    || !output.factCheckHash().equals(stableHash(factCheck))) {
                throw domainFailure(
                        "COVER_GENERATION_APPLY_HASH_INVALID",
                        "검증된 자기소개서 답변을 적용하지 못했습니다.");
            }
        }
    }

    private final class V2ApplyAnswerExecutor
            extends QuestionExecutor<ApplyAnswerRequestOutput> {
        private V2ApplyAnswerExecutor() {
            super(APPLY_ANSWER_VERSION, APPLY_SCHEMA, ApplyAnswerRequestOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            String scope = question.questionId().toString();
            return context.ephemeral(WRITE_ANSWER, scope) != null
                    && context.ephemeral(FACT_CHECK_ANSWER, scope) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            WrittenAnswerOutputV2 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, question.questionId(), WrittenAnswerOutputV2.class);
            FactCheckAnswerOutputV2 factCheck = requiredScopedEphemeral(
                    context,
                    FACT_CHECK_ANSWER,
                    question.questionId(),
                    FactCheckAnswerOutputV2.class);
            String answerHash = sha256(write(mapTipTap(answer.content())));
            String factCheckHash = stableHash(factCheck);
            var refs = baseRefsV2(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("agentRunId", state.agentRunId().toString());
            refs.put("answerHash", answerHash);
            refs.put("factCheckHash", factCheckHash);
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    state.agentRunId() + "|" + answerHash + "|" + factCheckHash,
                    tree(new ApplyAnswerRequestInput(
                            INPUT_SCHEMA_V2,
                            state.agentRunId(),
                            state.snapshot().coverLetterId(),
                            question.questionId(),
                            state.snapshot().coverLetterVersion(),
                            question.currentAnswerVersionId(),
                            state.snapshot().snapshotHash(),
                            answerHash,
                            factCheckHash)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ApplyAnswerRequestInput input =
                    read(invocation.input().gatewayPayload(), ApplyAnswerRequestInput.class);
            return localResponse(new ApplyAnswerRequestOutput(
                    APPLY_SCHEMA,
                    input.agentRunId(),
                    input.coverLetterId(),
                    input.questionId(),
                    input.expectedCoverLetterVersion(),
                    input.expectedCurrentVersionId(),
                    input.snapshotHash(),
                    input.answerHash(),
                    input.factCheckHash()));
        }

        @Override
        public DomainStepCompletion completeFresh(
                ApplyAnswerRequestOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            WrittenAnswerOutputV2 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV2.class);
            FactCheckAnswerOutputV2 factCheck = requiredScopedEphemeral(
                    context,
                    FACT_CHECK_ANSWER,
                    output.questionId(),
                    FactCheckAnswerOutputV2.class);
            AppliedAnswer applied;
            try {
                applied = commandPort.applyGeneratedAnswer(
                        context.run().userId(),
                        context.run().id(),
                        new PersistGeneratedAnswer(
                                output.coverLetterId(),
                                output.questionId(),
                                output.expectedCoverLetterVersion(),
                                output.expectedCurrentVersionId(),
                                output.snapshotHash(),
                                mapTipTap(answer.content()),
                                evidenceUses(answer, factCheck),
                                verificationResult(factCheck)));
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            JsonNode appliedOutput = tree(new AppliedAnswerOutput(
                    APPLY_SCHEMA,
                    output.questionId(),
                    applied.answerVersion().id(),
                    applied.generationVerification().id(),
                    applied.answerVersion().sourceType().name(),
                    applied.answerVersion().characterCount(),
                    applied.coverLetterVersion(),
                    output.snapshotHash()));
            return new DomainStepCompletion(
                    appliedOutput,
                    Optional.empty(),
                    new PartialResult(
                            List.of(output.questionId().toString()),
                            List.of(),
                            List.of(new ResourceReference(
                                    "COVER_LETTER_ANSWER_VERSION",
                                    applied.answerVersion().id(),
                                    "자기소개서 답변 버전"))));
        }

        @Override
        public Optional<PartialResult> partialResultFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            AppliedAnswerOutput output = read(minimalOutput, AppliedAnswerOutput.class);
            return Optional.of(new PartialResult(
                    List.of(output.questionId().toString()),
                    List.of(),
                    List.of(new ResourceReference(
                            "COVER_LETTER_ANSWER_VERSION",
                            output.answerVersionId(),
                            "자기소개서 답변 버전"))));
        }

        @Override
        public Object ephemeralOutputFromMinimal(JsonNode minimalOutput) {
            return read(minimalOutput, AppliedAnswerOutput.class);
        }

        @Override
        protected void validateJavaRecord(
                ApplyAnswerRequestOutput output, StepExecutionContext context) {
            if (!APPLY_SCHEMA.equals(output.schemaVersion())
                    || output.agentRunId() == null
                    || output.coverLetterId() == null
                    || output.questionId() == null
                    || output.expectedCoverLetterVersion() < 0
                    || !isHash(output.snapshotHash())
                    || !isHash(output.answerHash())
                    || !isHash(output.factCheckHash())) {
                throw new IllegalArgumentException("v2 answer apply output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                ApplyAnswerRequestOutput output, StepExecutionContext context) {
            WrittenAnswerOutputV2 answer = requiredScopedEphemeral(
                    context, WRITE_ANSWER, output.questionId(), WrittenAnswerOutputV2.class);
            FactCheckAnswerOutputV2 factCheck = requiredScopedEphemeral(
                    context,
                    FACT_CHECK_ANSWER,
                    output.questionId(),
                    FactCheckAnswerOutputV2.class);
            if (!output.agentRunId().equals(context.run().id())
                    || !output.coverLetterId().equals(context.run().resourceId())
                    || !output.questionId().toString().equals(context.scopeKey())
                    || !output.answerHash().equals(sha256(write(mapTipTap(answer.content()))))
                    || !output.factCheckHash().equals(stableHash(factCheck))) {
                throw domainFailure(
                        "COVER_GENERATION_APPLY_HASH_INVALID",
                        "검증된 자기소개서 답변을 적용하지 못했습니다.");
            }
        }
    }

    private final class PlanQuestionsExecutor
            extends GenerationExecutor<PlanQuestionsOutput> {
        private PlanQuestionsExecutor() {
            super(PLAN_QUESTIONS, PLAN_SCHEMA, PlanQuestionsOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            List<QuestionPlanningInput> questions = state.snapshot().questions().stream()
                    .map(value -> new QuestionPlanningInput(
                            value.questionId(),
                            value.questionOrder(),
                            bounded(value.questionText(), 2_000),
                            value.maxLength(),
                            value.currentAnswerVersionId() != null))
                    .toList();
            List<RequirementInput> requirements = state.snapshot().job().requirements().stream()
                    .map(value -> new RequirementInput(
                            value.category(),
                            bounded(value.text(), 2_000),
                            value.required()))
                    .toList();
            var refs = baseRefs(state);
            refs.put("questionCount", questions.size());
            refs.put("requirementsHash", stableHash(requirements));
            return localInput(
                    state,
                    null,
                    refs,
                    stableHash(questions) + "|" + stableHash(requirements),
                    tree(new PlanQuestionsInput(
                            INPUT_SCHEMA,
                            questions,
                            requirements,
                            state.snapshot().avoidExperienceDuplication())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        public JsonNode minimalOutput(
                PlanQuestionsOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", PLAN_SCHEMA)
                    .put("planHash", stableHash(output))
                    .put("questionCount", output.plans().size())
                    .put(
                            "avoidExperienceDuplication",
                            output.avoidExperienceDuplication());
            var ids = result.putArray("questionIds");
            output.plans().forEach(value -> ids.add(value.questionId().toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                PlanQuestionsOutput output, StepExecutionContext context) {
            if (!PLAN_SCHEMA.equals(output.schemaVersion())
                    || output.plans() == null
                    || output.plans().isEmpty()
                    || output.plans().size() > 20
                    || hasDuplicates(output.plans().stream()
                            .map(QuestionPlan::questionId)
                            .toList())) {
                throw new IllegalArgumentException("question plan is invalid");
            }
            output.plans().forEach(plan -> {
                requireText(plan.objective(), 1_000);
                requireTexts(plan.requiredElements(), 20, 1_000);
                requireTexts(plan.avoidContent(), 20, 1_000);
                if (plan.targetCharacterCount() < 1
                        || plan.targetCharacterCount() > 10_000
                        || plan.requirementIndexes() == null
                        || plan.requirementIndexes().size() > 100
                        || plan.requirementIndexes().stream()
                                .anyMatch(value -> value == null || value < 0)) {
                    throw new IllegalArgumentException("question plan item is invalid");
                }
            });
        }

        @Override
        protected void validateDomainOutput(
                PlanQuestionsOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            List<UUID> expected = state.snapshot().questions().stream()
                    .map(GenerationQuestion::questionId)
                    .toList();
            List<UUID> actual = output.plans().stream()
                    .map(QuestionPlan::questionId)
                    .toList();
            if (!expected.equals(actual)
                    || output.avoidExperienceDuplication()
                            != state.snapshot().avoidExperienceDuplication()) {
                throw domainFailure(
                        "COVER_GENERATION_PLAN_SCOPE_INVALID",
                        "자기소개서 문항 생성 계획 범위를 확인하지 못했습니다.");
            }
            for (QuestionPlan plan : output.plans()) {
                GenerationQuestion question = question(state, plan.questionId());
                if (question.maxLength() != null
                        && plan.targetCharacterCount() > question.maxLength()) {
                    throw new IllegalArgumentException(
                            "planned length exceeds question maximum");
                }
                if (plan.requirementIndexes().stream()
                        .anyMatch(index -> index
                                >= state.snapshot().job().requirements().size())) {
                    throw new IllegalArgumentException(
                            "requirement index is outside the snapshot");
                }
            }
        }
    }

    private final class AnalyzeQuestionExecutor
            extends QuestionExecutor<QuestionAnalysisOutput> {
        private AnalyzeQuestionExecutor() {
            super(ANALYZE_QUESTION, ANALYSIS_SCHEMA, QuestionAnalysisOutput.class);
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            PlanQuestionsOutput plan =
                    requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutput.class);
            QuestionPlan selected = plan.plans().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            var refs = baseRefs(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("questionOrder", question.questionOrder());
            refs.put("questionHash", sha256(question.questionText()));
            refs.put("planHash", stableHash(selected));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    stableHash(selected),
                    tree(new AnalyzeQuestionInput(
                            INPUT_SCHEMA,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            selected,
                            state.snapshot().job().requirements().stream()
                                    .map(value -> new RequirementInput(
                                            value.category(),
                                            bounded(value.text(), 2_000),
                                            value.required()))
                                    .toList())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        public JsonNode minimalOutput(
                QuestionAnalysisOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", ANALYSIS_SCHEMA)
                    .put("questionId", output.questionId().toString())
                    .put("analysisHash", stableHash(output))
                    .put("requiredElementCount", output.requiredElements().size())
                    .put("requirementCount", output.requirementIndexes().size());
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                QuestionAnalysisOutput output, StepExecutionContext context) {
            if (!ANALYSIS_SCHEMA.equals(output.schemaVersion())
                    || output.questionId() == null) {
                throw new IllegalArgumentException("question analysis is invalid");
            }
            requireText(output.intent(), 2_000);
            requireTexts(output.requiredElements(), 20, 1_000);
            requireTexts(output.avoidContent(), 20, 1_000);
            if (output.requirementIndexes() == null
                    || output.requirementIndexes().size() > 100
                    || output.requirementIndexes().stream()
                            .anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("question requirement indexes are invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                QuestionAnalysisOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            if (!output.questionId().toString().equals(context.scopeKey())
                    || output.requirementIndexes().stream()
                            .anyMatch(index -> index
                                    >= state.snapshot().job().requirements().size())) {
                throw domainFailure(
                        "COVER_GENERATION_QUESTION_SCOPE_INVALID",
                        "자기소개서 문항 분석 범위를 확인하지 못했습니다.");
            }
        }
    }

    private final class RetrieveEvidenceExecutor
            extends QuestionExecutor<RetrievedEvidenceOutput> {
        private RetrieveEvidenceExecutor() {
            super(RETRIEVE_EVIDENCE, RETRIEVAL_SCHEMA, RetrievedEvidenceOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            return context.ephemeral(
                            ANALYZE_QUESTION, question.questionId().toString())
                    != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            QuestionAnalysisOutput analysis = requiredScopedEphemeral(
                    context,
                    ANALYZE_QUESTION,
                    question.questionId(),
                    QuestionAnalysisOutput.class);
            String queryText = bounded(
                    question.questionText()
                            + "\n"
                            + analysis.intent()
                            + "\n"
                            + String.join("\n", analysis.requiredElements()),
                    4_000);
            var refs = baseRefs(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("queryHash", sha256(queryText));
            refs.put(
                    "embeddingPolicyVersion",
                    state.embeddingPolicy().version());
            refs.put("embeddingDimension", state.embeddingPolicy().dimension());
            refs.put(
                    "embeddingGeneration",
                    state.embeddingPolicy().generation());
            refs.put("embeddingProviderKey", state.embeddingPolicy().providerKey());
            refs.put("embeddingProductKey", state.embeddingPolicy().productKey());
            String embeddingRoute = state.embeddingPolicy().version()
                    + "|"
                    + state.embeddingPolicy().providerKey()
                    + "|"
                    + state.embeddingPolicy().productKey()
                    + "|"
                    + state.embeddingPolicy().dimension()
                    + "|"
                    + state.embeddingPolicy().generation();
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    sha256(queryText + "|embedding-route=" + embeddingRoute),
                    tree(new RetrieveEvidenceInput(
                            INPUT_SCHEMA,
                            question.questionId(),
                            queryText,
                            state.embeddingPolicy().version(),
                            state.embeddingPolicy().dimension(),
                            state.embeddingPolicy().generation(),
                            RETRIEVAL_POLICY_VERSION)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            RetrieveEvidenceInput input =
                    read(invocation.input().gatewayPayload(), RetrieveEvidenceInput.class);
            AiGatewayResponse embedding = invocation.embeddingGateway().embed(
                    new EmbeddingRequest(
                            state.embeddingPolicy().providerKey(),
                            state.embeddingPolicy().productKey(),
                            List.of(input.queryText()),
                            state.embeddingPolicy().dimension(),
                            EMBEDDING_TIMEOUT,
                            invocation.executionContext().run().priceVersion()));
            List<Double> vector = parseSingleVector(
                    embedding.rawJson(), state.embeddingPolicy().dimension());
            List<CandidateChunk> chunks = queryPort.searchEvidenceCandidates(
                    state.snapshot().userId(), vector, MAX_CHUNK_REFS);
            List<UUID> evidenceIds =
                    selectEvidence(state.snapshot(), input.queryText());
            RetrievedEvidenceOutput output = new RetrievedEvidenceOutput(
                    RETRIEVAL_SCHEMA,
                    input.questionId(),
                    sha256(input.queryText()),
                    evidenceIds,
                    chunks.stream()
                            .sorted(Comparator.comparingDouble(CandidateChunk::distance)
                                    .thenComparing(CandidateChunk::chunkId))
                            .limit(MAX_CHUNK_REFS)
                            .map(value -> new ChunkCandidateRef(
                                    value.chunkId(),
                                    value.documentId(),
                                    bounded(value.maskedContent(), 4_000),
                                    value.distance()))
                            .toList());
            return new AiGatewayResponse(write(output), embedding.usage());
        }

        @Override
        public JsonNode minimalOutput(
                RetrievedEvidenceOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", RETRIEVAL_SCHEMA)
                    .put("questionId", output.questionId().toString())
                    .put("queryHash", output.queryHash());
            var evidenceIds = result.putArray("evidenceIds");
            output.evidenceIds().forEach(id -> evidenceIds.add(id.toString()));
            var chunks = result.putArray("candidateChunks");
            output.candidateChunks().forEach(value -> chunks.addObject()
                    .put("chunkId", value.chunkId().toString())
                    .put("documentId", value.documentId().toString())
                    .put("distance", value.distance()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                RetrievedEvidenceOutput output, StepExecutionContext context) {
            if (!RETRIEVAL_SCHEMA.equals(output.schemaVersion())
                    || output.questionId() == null
                    || !isHash(output.queryHash())
                    || output.evidenceIds() == null
                    || output.evidenceIds().size() > MAX_EVIDENCE_PER_QUESTION
                    || hasDuplicates(output.evidenceIds())
                    || output.candidateChunks() == null
                    || output.candidateChunks().size() > MAX_CHUNK_REFS
                    || hasDuplicates(output.candidateChunks().stream()
                            .map(ChunkCandidateRef::chunkId)
                            .toList())) {
                throw new IllegalArgumentException("retrieval output is invalid");
            }
            output.candidateChunks().forEach(value -> {
                if (value.chunkId() == null
                        || value.documentId() == null
                        || value.maskedContent() == null
                        || value.maskedContent().isBlank()
                        || value.maskedContent().length() > 4_000
                        || !Double.isFinite(value.distance())
                        || value.distance() < 0) {
                    throw new IllegalArgumentException("candidate chunk reference is invalid");
                }
            });
        }

        @Override
        protected void validateDomainOutput(
                RetrievedEvidenceOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            Set<UUID> allowed = state.snapshot().verifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || !allowed.containsAll(output.evidenceIds())) {
                throw domainFailure(
                        "COVER_GENERATION_EVIDENCE_SCOPE_INVALID",
                        "자기소개서에 사용할 승인 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class AllocateExperiencesExecutor
            extends GenerationExecutor<ExperienceAllocationOutput> {
        private AllocateExperiencesExecutor() {
            super(
                    ALLOCATE_EXPERIENCES,
                    ALLOCATION_SCHEMA,
                    ExperienceAllocationOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            Map<String, Object> retrieved =
                    context.scopedEphemeral(RETRIEVE_EVIDENCE);
            List<AllocationCandidateInput> candidates = state.snapshot().questions().stream()
                    .filter(question -> retrieved.containsKey(
                            question.questionId().toString()))
                    .map(question -> {
                        RetrievedEvidenceOutput output = cast(
                                retrieved.get(question.questionId().toString()),
                                RetrievedEvidenceOutput.class);
                        return new AllocationCandidateInput(
                                question.questionId(), output.evidenceIds());
                    })
                    .toList();
            var refs = baseRefs(state);
            refs.put("candidateHash", stableHash(candidates));
            refs.put("questionCount", candidates.size());
            return localInput(
                    state,
                    null,
                    refs,
                    stableHash(candidates),
                    tree(new AllocateExperiencesInput(
                            INPUT_SCHEMA,
                            candidates,
                            state.snapshot().avoidExperienceDuplication())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        protected void validateJavaRecord(
                ExperienceAllocationOutput output,
                StepExecutionContext context) {
            if (!ALLOCATION_SCHEMA.equals(output.schemaVersion())
                    || output.allocations() == null
                    || output.allocations().size() > 20
                    || hasDuplicates(output.allocations().stream()
                            .map(ExperienceAllocation::questionId)
                            .toList())) {
                throw new IllegalArgumentException("experience allocation is invalid");
            }
            output.allocations().forEach(value -> {
                if (value.questionId() == null
                        || value.evidenceIds() == null
                        || value.evidenceIds().size() > MAX_EVIDENCE_PER_QUESTION
                        || hasDuplicates(value.evidenceIds())
                        || (value.duplicationReason() != null
                                && value.duplicationReason().length() > 1_000)) {
                    throw new IllegalArgumentException(
                            "experience allocation item is invalid");
                }
            });
        }

        @Override
        protected void validateDomainOutput(
                ExperienceAllocationOutput output,
                StepExecutionContext context) {
            GenerationState state = state(context);
            Map<String, Object> retrieved =
                    context.scopedEphemeral(RETRIEVE_EVIDENCE);
            List<UUID> expected = state.snapshot().questions().stream()
                    .map(GenerationQuestion::questionId)
                    .filter(id -> retrieved.containsKey(id.toString()))
                    .toList();
            if (!expected.equals(output.allocations().stream()
                    .map(ExperienceAllocation::questionId)
                    .toList())) {
                throw domainFailure(
                        "COVER_GENERATION_ALLOCATION_SCOPE_INVALID",
                        "자기소개서 경험 배분 범위를 확인하지 못했습니다.");
            }
            Map<UUID, Integer> usage = new LinkedHashMap<>();
            for (ExperienceAllocation allocation : output.allocations()) {
                RetrievedEvidenceOutput candidate = cast(
                        retrieved.get(allocation.questionId().toString()),
                        RetrievedEvidenceOutput.class);
                if (!new HashSet<>(candidate.evidenceIds())
                        .containsAll(allocation.evidenceIds())) {
                    throw domainFailure(
                            "COVER_GENERATION_ALLOCATION_EVIDENCE_INVALID",
                            "자기소개서 경험 배분 근거를 확인하지 못했습니다.");
                }
                allocation.evidenceIds()
                        .forEach(id -> usage.merge(id, 1, Integer::sum));
            }
            if (state.snapshot().avoidExperienceDuplication()) {
                for (ExperienceAllocation allocation : output.allocations()) {
                    boolean duplicates = allocation.evidenceIds().stream()
                            .anyMatch(id -> usage.getOrDefault(id, 0) > 1);
                    if (duplicates
                            && (allocation.duplicationReason() == null
                                    || allocation.duplicationReason().isBlank())) {
                        throw new IllegalArgumentException(
                                "duplicated experience requires a reason");
                    }
                }
            }
        }
    }

    private final class WriteAnswerExecutor
            extends QuestionExecutor<WrittenAnswerOutput> {
        private WriteAnswerExecutor() {
            super(WRITE_ANSWER, ANSWER_SCHEMA, WrittenAnswerOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            Object value = context.ephemeral(ALLOCATE_EXPERIENCES);
            if (!(value instanceof ExperienceAllocationOutput allocation)) {
                return false;
            }
            return context.ephemeral(
                                    ANALYZE_QUESTION,
                                    question.questionId().toString())
                            != null
                    && allocation.allocations().stream()
                            .anyMatch(item -> item.questionId()
                                    .equals(question.questionId()));
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            QuestionAnalysisOutput analysis = requiredScopedEphemeral(
                    context,
                    ANALYZE_QUESTION,
                    question.questionId(),
                    QuestionAnalysisOutput.class);
            ExperienceAllocationOutput allocation =
                    requiredEphemeral(
                            context,
                            ALLOCATE_EXPERIENCES,
                            ExperienceAllocationOutput.class);
            ExperienceAllocation selected = allocation.allocations().stream()
                    .filter(value -> value.questionId().equals(question.questionId()))
                    .findFirst()
                    .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
            List<ApprovedEvidenceInput> evidence = selected.evidenceIds().stream()
                    .map(id -> approvedEvidence(state, id))
                    .toList();
            var refs = baseRefs(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("analysisHash", stableHash(analysis));
            refs.put("allocationHash", stableHash(selected));
            var evidenceIds = refs.putArray("evidenceIds");
            selected.evidenceIds().forEach(id -> evidenceIds.add(id.toString()));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    stableHash(analysis) + "|" + stableHash(selected),
                    tree(new WriteAnswerInput(
                            INPUT_SCHEMA,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            analysis,
                            evidence,
                            bounded(state.snapshot().job().companyName(), 200),
                            bounded(state.snapshot().job().positionName(), 300),
                            state.snapshot().job().analysisOutdated())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        public JsonNode minimalOutput(
                WrittenAnswerOutput output, ObjectMapper ignored) {
            TipTapDocumentDto content = mapTipTap(output.content());
            String plainText = plainText(content);
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", ANSWER_SCHEMA)
                    .put("questionId", output.questionId().toString())
                    .put("answerHash", sha256(write(content)))
                    .put("characterCount", plainText.codePointCount(0, plainText.length()));
            var evidenceIds = result.putArray("evidenceIds");
            output.claims().stream()
                    .map(EvidenceClaimDraft::evidenceId)
                    .distinct()
                    .sorted()
                    .forEach(id -> evidenceIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                WrittenAnswerOutput output, StepExecutionContext context) {
            if (!ANSWER_SCHEMA.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.content() == null
                    || output.claims() == null
                    || output.claims().size() > 50) {
                throw new IllegalArgumentException("written answer is invalid");
            }
            validateTipTap(mapTipTap(output.content()));
            output.claims().forEach(value -> {
                if (value.evidenceId() == null) {
                    throw new IllegalArgumentException("answer evidence claim is invalid");
                }
                requireText(value.claimText(), 2_000);
            });
        }

        @Override
        protected void validateDomainOutput(
                WrittenAnswerOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            GenerationQuestion question = question(state, output.questionId());
            ExperienceAllocationOutput allocation =
                    requiredEphemeral(
                            context,
                            ALLOCATE_EXPERIENCES,
                            ExperienceAllocationOutput.class);
            Set<UUID> allowed = allocation.allocations().stream()
                    .filter(value -> value.questionId().equals(output.questionId()))
                    .flatMap(value -> value.evidenceIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || output.claims().stream()
                            .map(EvidenceClaimDraft::evidenceId)
                            .anyMatch(id -> !allowed.contains(id))) {
                throw domainFailure(
                        "COVER_GENERATION_ANSWER_EVIDENCE_INVALID",
                        "자기소개서 답변의 승인 근거를 확인하지 못했습니다.");
            }
            String text = plainText(mapTipTap(output.content()));
            int count = text.codePointCount(0, text.length());
            if (count > MAX_TEXT
                    || (question.maxLength() != null
                            && count > question.maxLength())) {
                throw new IllegalArgumentException(
                        "written answer exceeds the character limit");
            }
        }
    }

    private final class FactCheckAnswerExecutor
            extends QuestionExecutor<FactCheckAnswerOutput> {
        private FactCheckAnswerExecutor() {
            super(
                    FACT_CHECK_ANSWER,
                    FACT_CHECK_SCHEMA,
                    FactCheckAnswerOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            String scope = question.questionId().toString();
            return context.ephemeral(WRITE_ANSWER, scope) != null
                    && context.ephemeral(RETRIEVE_EVIDENCE, scope) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            WrittenAnswerOutput answer = requiredScopedEphemeral(
                    context,
                    WRITE_ANSWER,
                    question.questionId(),
                    WrittenAnswerOutput.class);
            RetrievedEvidenceOutput retrieval = requiredScopedEphemeral(
                    context,
                    RETRIEVE_EVIDENCE,
                    question.questionId(),
                    RetrievedEvidenceOutput.class);
            List<ApprovedEvidenceInput> evidence = retrieval.evidenceIds().stream()
                    .map(id -> approvedEvidence(state, id))
                    .toList();
            TipTapDocumentDto answerContent = mapTipTap(answer.content());
            String answerText = plainText(answerContent);
            var refs = baseRefs(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("answerHash", sha256(write(answerContent)));
            refs.put("retrievalHash", stableHash(retrieval));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    sha256(answerText) + "|" + stableHash(retrieval),
                    tree(new FactCheckAnswerInput(
                            INPUT_SCHEMA,
                            question.questionId(),
                            bounded(question.questionText(), 2_000),
                            question.maxLength(),
                            answerContent,
                            answerText,
                            answer.claims(),
                            evidence,
                            state.snapshot().job().requirements().stream()
                                    .map(value -> new RequirementInput(
                                            value.category(),
                                            bounded(value.text(), 2_000),
                                            value.required()))
                                    .toList(),
                            retrieval.candidateChunks(),
                            state.snapshot().job().analysisOutdated())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        public JsonNode minimalOutput(
                FactCheckAnswerOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", FACT_CHECK_SCHEMA)
                    .put("questionId", output.questionId().toString())
                    .put("factCheckHash", stableHash(output))
                    .put("issueCount", output.issues().size())
                    .put("verifiedClaimCount", output.verifiedClaims().size());
            var evidenceIds = result.putArray("evidenceIds");
            referencedEvidence(output).stream()
                    .sorted()
                    .forEach(id -> evidenceIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                FactCheckAnswerOutput output,
                StepExecutionContext context) {
            if (!FACT_CHECK_SCHEMA.equals(output.schemaVersion())
                    || output.questionId() == null
                    || output.issues() == null
                    || output.issues().size() > 100
                    || output.suggestions() == null
                    || output.suggestions().size() > 20
                    || output.verifiedClaims() == null
                    || output.verifiedClaims().size() > 100) {
                throw new IllegalArgumentException("answer fact check is invalid");
            }
            output.issues().forEach(this::validateIssue);
            requireTexts(output.suggestions(), 20, 1_000);
            output.verifiedClaims().forEach(value -> {
                requireText(value.claim(), 2_000);
                if (value.evidenceIds() == null
                        || value.evidenceIds().size() > 20
                        || hasDuplicates(value.evidenceIds())) {
                    throw new IllegalArgumentException("verified claim is invalid");
                }
            });
        }

        @Override
        protected void validateWorkflowOutput(
                FactCheckAnswerOutput output,
                StepExecutionContext context) {
            GenerationState state = state(context);
            WrittenAnswerOutput answer = requiredScopedEphemeral(
                    context,
                    WRITE_ANSWER,
                    output.questionId(),
                    WrittenAnswerOutput.class);
            String answerText = plainText(mapTipTap(answer.content()));
            List<String> unsupportedNumbers =
                    unsupportedNumbers(answerText, state.snapshot().verifiedEvidence());
            if (!unsupportedNumbers.isEmpty()
                    && output.issues().stream().noneMatch(issue ->
                            (issue.code() == VerificationIssueCode.UNVERIFIED_CLAIM
                                            || issue.code()
                                                    == VerificationIssueCode.CONTRADICTION)
                                    && issue.severity() == IssueSeverity.ERROR)) {
                throw new IllegalArgumentException(
                        "unsupported numbers require an error issue");
            }
            if (answerText.isBlank()
                    || (answer.claims().isEmpty()
                            && output.issues().stream().noneMatch(issue ->
                                    issue.code()
                                            == VerificationIssueCode.UNVERIFIED_CLAIM))) {
                throw new IllegalArgumentException(
                        "an ungrounded answer requires an issue");
            }
        }

        @Override
        protected void validateDomainOutput(
                FactCheckAnswerOutput output,
                StepExecutionContext context) {
            GenerationState state = state(context);
            Set<UUID> allowed = state.snapshot().verifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.questionId().toString().equals(context.scopeKey())
                    || !allowed.containsAll(referencedEvidence(output))) {
                throw domainFailure(
                        "COVER_GENERATION_FACT_CHECK_EVIDENCE_INVALID",
                        "자기소개서 사실 검증 근거를 확인하지 못했습니다.");
            }
        }

        private void validateIssue(VerificationIssueDraft issue) {
            if (issue.code() == null || issue.severity() == null) {
                throw new IllegalArgumentException("verification issue is invalid");
            }
            requireText(issue.message(), 1_000);
            if (issue.relatedText() != null && issue.relatedText().length() > 1_000) {
                throw new IllegalArgumentException("verification related text is invalid");
            }
            if (issue.evidenceIds() == null
                    || issue.evidenceIds().size() > 20
                    || hasDuplicates(issue.evidenceIds())) {
                throw new IllegalArgumentException("verification evidence is invalid");
            }
        }
    }

    private final class ApplyAnswerExecutor
            extends QuestionExecutor<ApplyAnswerRequestOutput> {
        private ApplyAnswerExecutor() {
            super(
                    APPLY_ANSWER_VERSION,
                    APPLY_SCHEMA,
                    ApplyAnswerRequestOutput.class);
        }

        @Override
        protected boolean eligibleQuestion(
                StepExecutionContext context, GenerationQuestion question) {
            String scope = question.questionId().toString();
            return context.ephemeral(WRITE_ANSWER, scope) != null
                    && context.ephemeral(FACT_CHECK_ANSWER, scope) != null;
        }

        @Override
        protected StepInput prepareQuestion(
                StepExecutionContext context,
                GenerationState state,
                GenerationQuestion question) {
            WrittenAnswerOutput answer = requiredScopedEphemeral(
                    context,
                    WRITE_ANSWER,
                    question.questionId(),
                    WrittenAnswerOutput.class);
            FactCheckAnswerOutput factCheck = requiredScopedEphemeral(
                    context,
                    FACT_CHECK_ANSWER,
                    question.questionId(),
                    FactCheckAnswerOutput.class);
            var refs = baseRefs(state);
            refs.put("questionId", question.questionId().toString());
            refs.put("agentRunId", state.agentRunId().toString());
            refs.put("answerHash", sha256(write(mapTipTap(answer.content()))));
            refs.put("factCheckHash", stableHash(factCheck));
            return localInput(
                    state,
                    question.questionId().toString(),
                    refs,
                    state.agentRunId()
                            + "|"
                            + sha256(write(mapTipTap(answer.content())))
                            + "|"
                            + stableHash(factCheck),
                    tree(new ApplyAnswerRequestInput(
                            INPUT_SCHEMA,
                            state.agentRunId(),
                            state.snapshot().coverLetterId(),
                            question.questionId(),
                            state.snapshot().coverLetterVersion(),
                            question.currentAnswerVersionId(),
                            state.snapshot().snapshotHash(),
                            sha256(write(mapTipTap(answer.content()))),
                            stableHash(factCheck))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ApplyAnswerRequestInput input =
                    read(invocation.input().gatewayPayload(), ApplyAnswerRequestInput.class);
            return localResponse(new ApplyAnswerRequestOutput(
                    APPLY_SCHEMA,
                    input.agentRunId(),
                    input.coverLetterId(),
                    input.questionId(),
                    input.expectedCoverLetterVersion(),
                    input.expectedCurrentVersionId(),
                    input.snapshotHash(),
                    input.answerHash(),
                    input.factCheckHash()));
        }

        @Override
        public DomainStepCompletion completeFresh(
                ApplyAnswerRequestOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            WrittenAnswerOutput answer = requiredScopedEphemeral(
                    context,
                    WRITE_ANSWER,
                    output.questionId(),
                    WrittenAnswerOutput.class);
            FactCheckAnswerOutput factCheck = requiredScopedEphemeral(
                    context,
                    FACT_CHECK_ANSWER,
                    output.questionId(),
                    FactCheckAnswerOutput.class);
            VerificationResult result = verificationResult(factCheck);
            List<EvidenceUse> evidenceUses = evidenceUses(answer, factCheck);
            AppliedAnswer applied;
            try {
                applied = commandPort.applyGeneratedAnswer(
                        context.run().userId(),
                        context.run().id(),
                        new PersistGeneratedAnswer(
                                output.coverLetterId(),
                                output.questionId(),
                                output.expectedCoverLetterVersion(),
                                output.expectedCurrentVersionId(),
                                output.snapshotHash(),
                                mapTipTap(answer.content()),
                                evidenceUses,
                                result));
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            JsonNode appliedOutput = tree(new AppliedAnswerOutput(
                    APPLY_SCHEMA,
                    output.questionId(),
                    applied.answerVersion().id(),
                    applied.generationVerification().id(),
                    applied.answerVersion().sourceType().name(),
                    applied.answerVersion().characterCount(),
                    applied.coverLetterVersion(),
                    output.snapshotHash()));
            PartialResult partial = new PartialResult(
                    List.of(output.questionId().toString()),
                    List.of(),
                    List.of(new ResourceReference(
                            "COVER_LETTER_ANSWER_VERSION",
                            applied.answerVersion().id(),
                            "자기소개서 답변 버전")));
            return new DomainStepCompletion(
                    appliedOutput, Optional.empty(), partial);
        }

        @Override
        public Optional<PartialResult> partialResultFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            AppliedAnswerOutput output =
                    read(minimalOutput, AppliedAnswerOutput.class);
            return Optional.of(new PartialResult(
                    List.of(output.questionId().toString()),
                    List.of(),
                    List.of(new ResourceReference(
                            "COVER_LETTER_ANSWER_VERSION",
                            output.answerVersionId(),
                            "자기소개서 답변 버전"))));
        }

        @Override
        public Object ephemeralOutputFromMinimal(JsonNode minimalOutput) {
            return read(minimalOutput, AppliedAnswerOutput.class);
        }

        @Override
        public boolean continueAfterScopeFailure(
                AiExecutionException failure, StepExecutionContext context) {
            return context.scopeKey() != null
                    && failure.failureKind() != FailureKind.OWNER
                    && failure.failureKind() != FailureKind.CONFIGURATION
                    && failure.failureKind() != FailureKind.BUDGET
                    && failure.failureKind() != FailureKind.CANCELLATION
                    && failure.failureKind() != FailureKind.INTERRUPTION;
        }

        @Override
        protected void validateJavaRecord(
                ApplyAnswerRequestOutput output,
                StepExecutionContext context) {
            if (!APPLY_SCHEMA.equals(output.schemaVersion())
                    || output.agentRunId() == null
                    || output.coverLetterId() == null
                    || output.questionId() == null
                    || output.expectedCoverLetterVersion() < 0
                    || !isHash(output.snapshotHash())
                    || !isHash(output.answerHash())
                    || !isHash(output.factCheckHash())) {
                throw new IllegalArgumentException("answer apply output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                ApplyAnswerRequestOutput output,
                StepExecutionContext context) {
            WrittenAnswerOutput answer = requiredScopedEphemeral(
                    context,
                    WRITE_ANSWER,
                    output.questionId(),
                    WrittenAnswerOutput.class);
            FactCheckAnswerOutput factCheck = requiredScopedEphemeral(
                    context,
                    FACT_CHECK_ANSWER,
                    output.questionId(),
                    FactCheckAnswerOutput.class);
            if (!output.agentRunId().equals(context.run().id())
                    || !output.coverLetterId().equals(context.run().resourceId())
                    || !output.questionId().toString().equals(context.scopeKey())
                    || !output.answerHash().equals(sha256(write(mapTipTap(answer.content()))))
                    || !output.factCheckHash().equals(stableHash(factCheck))) {
                throw domainFailure(
                        "COVER_GENERATION_APPLY_HASH_INVALID",
                        "검증된 자기소개서 답변을 적용하지 못했습니다.");
            }
        }
    }

    private AiGatewayResponse chat(GatewayInvocation invocation) {
        return invocation.chatGateway().chat(new ChatRequest(
                invocation.modelRoute().providerKey(),
                invocation.modelRoute().productKey(),
                invocation.prompt().promptVersion(),
                invocation.prompt().instructions(),
                invocation.input().gatewayPayload(),
                invocation.prompt().outputSchemaVersion(),
                invocation.prompt().toolAllowlist(),
                0,
                CHAT_TIMEOUT,
                invocation.executionContext().run().priceVersion(),
                invocation.prompt().maxOutputTokens(),
                invocation.prompt().outputType()));
    }

    private Object planQuestionsInput(
            GenerationState state,
            List<QuestionPlanningInput> questions,
            List<RequirementInput> requirements,
            List<EvidencePlanningInput> evidence) {
        int omitted = Math.max(0, state.snapshot().verifiedEvidence().size() - evidence.size());
        if (state.snapshot().model() == null) {
            return new PlanQuestionsInputV3(
                    INPUT_SCHEMA_V3,
                    CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                    contextAvailability(state),
                    jobWritingContext(state),
                    questions,
                    requirements,
                    evidence,
                    omitted,
                    state.snapshot().avoidExperienceDuplication());
        }
        List<QuestionPlanningInputV4> memoAwareQuestions = state.snapshot().questions().stream()
                .map(value -> new QuestionPlanningInputV4(
                        value.questionId(),
                        value.questionOrder(),
                        bounded(value.questionText(), 1_000),
                        bounded(value.memo(), 2_000),
                        value.maxLength(),
                        value.currentAnswerVersionId() != null))
                .toList();
        return new PlanQuestionsInputV4(
                INPUT_SCHEMA_V4,
                CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                contextAvailability(state),
                jobWritingContext(state),
                memoAwareQuestions,
                requirements,
                evidence,
                omitted,
                state.snapshot().avoidExperienceDuplication());
    }

    private PlanningInvocationInput planningInvocationInput(GatewayInvocation invocation) {
        if (isExactModel(invocation.executionContext().run())) {
            PlanQuestionsInputV4 input = read(
                    invocation.input().gatewayPayload(), PlanQuestionsInputV4.class);
            return new PlanningInvocationInput(
                    input.questions().stream()
                            .map(value -> new QuestionPlanningInput(
                                    value.questionId(),
                                    value.questionOrder(),
                                    value.questionText(),
                                    value.maxLength(),
                                    value.hasCurrentAnswer()))
                            .toList(),
                    input.requirements(),
                    input.avoidExperienceDuplication());
        }
        PlanQuestionsInputV3 input = read(
                invocation.input().gatewayPayload(), PlanQuestionsInputV3.class);
        return new PlanningInvocationInput(
                input.questions(), input.requirements(), input.avoidExperienceDuplication());
    }

    private JsonNode writeAnswerInput(
            GenerationState state,
            GenerationQuestion question,
            QuestionPlanV3 plan,
            QuestionAnalysisOutputV3 analysis,
            List<ApprovedEvidenceInput> evidence,
            BoundedText current,
            List<OtherQuestionStrategyInput> otherQuestions) {
        if (state.snapshot().model() == null) {
            return objectMapper.valueToTree(new WriteAnswerInputV3(
                    INPUT_SCHEMA_V3,
                    CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                    contextAvailability(state),
                    question.questionId(),
                    bounded(question.questionText(), 2_000),
                    question.maxLength(),
                    plan,
                    analysis,
                    plan.targetCharacterCount(),
                    evidence,
                    jobWritingContext(state),
                    question.currentAnswerVersionId(),
                    current,
                    otherQuestions,
                    plan.headingPolicy()));
        }
        return objectMapper.valueToTree(new WriteAnswerInputV4(
                INPUT_SCHEMA_V4,
                CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                contextAvailability(state),
                question.questionId(),
                bounded(question.questionText(), 2_000),
                bounded(question.memo(), 2_000),
                question.maxLength(),
                plan,
                analysis,
                plan.targetCharacterCount(),
                evidence,
                jobWritingContext(state),
                question.currentAnswerVersionId(),
                current,
                otherQuestions,
                plan.headingPolicy()));
    }

    private String inputSchemaVersion(GenerationState state) {
        return state.snapshot().model() == null ? INPUT_SCHEMA_V3 : INPUT_SCHEMA_V4;
    }

    private String contextPolicyVersion(GenerationState state) {
        return state.snapshot().model() == null
                ? CONTEXT_POLICY_VERSION_V3
                : "cover-generation-context-v4";
    }

    private String retrievalPolicyVersion(GenerationState state) {
        return state.snapshot().model() == null
                ? RETRIEVAL_POLICY_VERSION_V3
                : "cover-generation-retrieval-v4";
    }

    private boolean isExactModel(AgentRunSnapshot run) {
        return CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION.equals(
                run.workflowVersion());
    }

    private boolean validSelection(AgentRunSnapshot run) {
        return isExactModel(run)
                ? run.requestedQualityMode() == null
                        && OpenAiChatModels.supportsCoverLetter(run.requestedModel())
                : run.requestedQualityMode() != null && run.requestedModel() == null;
    }

    private boolean selectionMatches(AgentRunSnapshot run, GenerationSnapshot snapshot) {
        return isExactModel(run)
                ? snapshot.qualityMode() == null
                        && run.requestedModel().equals(snapshot.model())
                : snapshot.model() == null
                        && run.requestedQualityMode() == snapshot.qualityMode();
    }

    private List<QuestionPlanningInput> planningQuestions(GenerationState state) {
        return state.snapshot().questions().stream()
                .map(value -> new QuestionPlanningInput(
                        value.questionId(),
                        value.questionOrder(),
                        bounded(value.questionText(), 1_000),
                        value.maxLength(),
                        value.currentAnswerVersionId() != null))
                .toList();
    }

    private List<RequirementInput> requirementInputs(GenerationState state) {
        return state.snapshot().job().requirements().stream()
                .limit(30)
                .map(value -> new RequirementInput(
                        bounded(value.category(), 200),
                        bounded(value.text(), 500),
                        value.required()))
                .toList();
    }

    private JobWritingContextInput jobWritingContext(GenerationState state) {
        var job = state.snapshot().job();
        return new JobWritingContextInput(
                bounded(job.companyName(), 200),
                bounded(job.title(), 300),
                bounded(job.positionName(), 300),
                bounded(job.descriptionText(), MAX_JOB_DESCRIPTION),
                requirementInputs(state),
                job.analysisOutdated());
    }

    private List<EvidencePlanningInput> planningEvidence(GenerationState state) {
        Set<UUID> preferred = Set.copyOf(state.snapshot().preferredEvidenceIds());
        return state.snapshot().verifiedEvidence().stream()
                .sorted(Comparator
                        .<VerifiedEvidence, Boolean>comparing(value -> !preferred.contains(value.id()))
                        .thenComparing(VerifiedEvidence::id))
                .limit(MAX_PLANNING_EVIDENCE)
                .map(value -> new EvidencePlanningInput(
                        value.id(),
                        value.sourceType().name(),
                        bounded(value.evidenceCategory(), 200),
                        bounded(value.title(), MAX_EVIDENCE_TITLE),
                        bounded(value.content(), MAX_EVIDENCE_CONTENT),
                        preferred.contains(value.id())))
                .toList();
    }

    private EvidencePlanningInput evidencePlanning(GenerationState state, UUID evidenceId) {
        Set<UUID> preferred = Set.copyOf(state.snapshot().preferredEvidenceIds());
        return state.snapshot().verifiedEvidence().stream()
                .filter(value -> value.id().equals(evidenceId))
                .findFirst()
                .map(value -> new EvidencePlanningInput(
                        value.id(),
                        value.sourceType().name(),
                        bounded(value.evidenceCategory(), 200),
                        bounded(value.title(), MAX_EVIDENCE_TITLE),
                        bounded(value.content(), MAX_EVIDENCE_CONTENT),
                        preferred.contains(value.id())))
                .orElseThrow(() -> domainFailure(
                        "COVER_GENERATION_EVIDENCE_STALE",
                        "자기소개서에 사용할 승인 근거가 변경되었습니다."));
    }

    private ApprovedEvidenceInput approvedEvidenceV2(
            GenerationState state, UUID evidenceId) {
        return state.snapshot().verifiedEvidence().stream()
                .filter(value -> value.id().equals(evidenceId))
                .findFirst()
                .map(value -> new ApprovedEvidenceInput(
                        value.id(),
                        value.sourceType().name(),
                        bounded(value.evidenceCategory(), 200),
                        bounded(value.title(), MAX_EVIDENCE_TITLE),
                        bounded(value.content(), 2_000),
                        value.version()))
                .orElseThrow(() -> domainFailure(
                        "COVER_GENERATION_EVIDENCE_STALE",
                        "자기소개서에 사용할 승인 근거가 변경되었습니다."));
    }

    private void validateBuildOutput(BuildGenerationContextOutput output) {
        if (!BUILD_SCHEMA.equals(output.schemaVersion())
                || output.coverLetterId() == null
                || output.coverLetterVersion() < 0
                || !isHash(output.snapshotHash())
                || output.jobId() == null
                || output.analysisId() == null
                || output.analysisVersion() < 1
                || output.questionIds() == null
                || output.questionIds().size() > 20
                || hasDuplicates(output.questionIds())
                || hasDuplicates(output.reusedQuestionIds())
                || hasDuplicates(output.verifiedEvidenceIds())
                || hasDuplicates(output.preferredEvidenceIds())) {
            throw new IllegalArgumentException("generation context output is invalid");
        }
    }

    private void validateBuildScope(BuildGenerationContextOutput output, GenerationState state) {
        if (!output.coverLetterId().equals(state.snapshot().coverLetterId())
                || output.coverLetterVersion() != state.snapshot().coverLetterVersion()
                || !output.snapshotHash().equals(state.snapshot().snapshotHash())
                || !output.questionIds().equals(state.snapshot().questions().stream()
                        .map(GenerationQuestion::questionId)
                        .toList())
                || !output.reusedQuestionIds().equals(state.reusedQuestionIds())) {
            throw domainFailure(
                    "COVER_GENERATION_CONTEXT_STALE",
                    "자기소개서 생성 기준 정보가 변경되었습니다.");
        }
    }

    private ContextAvailabilityInput contextAvailability(GenerationState state) {
        var job = state.snapshot().job();
        return new ContextAvailabilityInput(
                job.companyName() != null && !job.companyName().isBlank(),
                (job.positionName() != null && !job.positionName().isBlank())
                        || (job.title() != null && !job.title().isBlank()),
                job.descriptionText() != null && !job.descriptionText().isBlank(),
                !job.requirements().isEmpty());
    }

    private void validateQuestionPlanV3(
            QuestionPlanV3 plan, ContextAvailabilityInput availability) {
        if (plan == null
                || plan.questionId() == null
                || plan.questionType() == null
                || plan.narrativeFramework() == null
                || plan.headingPolicy() == null
                || plan.targetCharacterCount() < 1
                || plan.targetCharacterCount() > 10_000
                || plan.requirementIndexes() == null
                || plan.requirementIndexes().size() > 100
                || plan.requirementIndexes().stream().anyMatch(value -> value == null || value < 0)) {
            throw repairable(
                    "COVER_PLAN_FIELD_INVALID",
                    "Return every required plan field. Use targetCharacterCount 1..10000 and at most 100 unique nonnegative requirementIndexes.");
        }
        try {
            requireText(plan.coreMessage(), 1_000);
            requireText(plan.objective(), 1_000);
            requireTexts(plan.requiredElements(), 20, 1_000);
            requireTexts(plan.avoidContent(), 20, 1_000);
            requireTexts(plan.evidenceSelectionCriteria(), 20, 1_000);
        } catch (IllegalArgumentException exception) {
            throw repairable(
                    "COVER_PLAN_TEXT_INVALID",
                    "Return nonblank coreMessage and objective. Text arrays may have at most 20 nonblank values, each at most 1000 characters.");
        }
        try {
            CoverLetterWorkflowV3Policy.validateQuestionFramework(
                    plan.questionType(), plan.narrativeFramework());
        } catch (IllegalArgumentException exception) {
            throw repairable(
                    "COVER_PLAN_FRAMEWORK_INVALID",
                    "Use the exact questionType to narrativeFramework mapping stated in the instructions.");
        }
        try {
            validateOptionalConnections(plan.roleConnection(), plan.companyConnection(), availability);
        } catch (IllegalArgumentException exception) {
            throw repairable(
                    "COVER_PLAN_CONNECTION_INVALID",
                    "Use null for an unavailable or unused role/company connection; otherwise use nonblank supplied context of at most 1000 characters.");
        }
        try {
            CoverLetterWorkflowV3Policy.validateSections(
                    plan.narrativeFramework(), plan.narrativeSections());
        } catch (IllegalArgumentException exception) {
            throw repairable(
                    "COVER_PLAN_SECTIONS_INVALID",
                    "Use only unique section types allowed for the selected framework, nonblank objectives, and integer weights totaling exactly 100. Technical plans require DECISION and TRADEOFF.");
        }
    }

    private StructuredOutputValidationException repairable(
            String safeReason, String guidance) {
        return repairable(ValidationPhase.JAVA_RECORD, safeReason, guidance);
    }

    private StructuredOutputValidationException repairable(
            ValidationPhase phase, String safeReason, String guidance) {
        return StructuredOutputValidationException.repairable(
                phase, safeReason, guidance);
    }

    private void validateOptionalConnections(
            String roleConnection,
            String companyConnection,
            ContextAvailabilityInput availability) {
        if (roleConnection != null && (roleConnection.isBlank() || roleConnection.length() > 1_000)) {
            throw new IllegalArgumentException("role connection is invalid");
        }
        if (companyConnection != null
                && (companyConnection.isBlank() || companyConnection.length() > 1_000)) {
            throw new IllegalArgumentException("company connection is invalid");
        }
        if (!availability.roleContextAvailable() && roleConnection != null) {
            throw new IllegalArgumentException("unavailable role context must not be invented");
        }
        if (!availability.companyContextAvailable() && companyConnection != null) {
            throw new IllegalArgumentException("unavailable company context must not be invented");
        }
    }

    private JsonNode retrievalMinimalOutput(RetrievedEvidenceOutput output) {
        var result = objectMapper.createObjectNode()
                .put("schemaVersion", RETRIEVAL_SCHEMA)
                .put("questionId", output.questionId().toString())
                .put("queryHash", output.queryHash());
        var evidenceIds = result.putArray("evidenceIds");
        output.evidenceIds().forEach(id -> evidenceIds.add(id.toString()));
        var chunks = result.putArray("candidateChunks");
        output.candidateChunks().forEach(value -> chunks.addObject()
                .put("chunkId", value.chunkId().toString())
                .put("documentId", value.documentId().toString())
                .put("distance", value.distance()));
        return result;
    }

    private void validateRetrievedEvidence(RetrievedEvidenceOutput output) {
        if (!RETRIEVAL_SCHEMA.equals(output.schemaVersion())
                || output.questionId() == null
                || !isHash(output.queryHash())
                || output.evidenceIds() == null
                || output.evidenceIds().size() > MAX_EVIDENCE_PER_QUESTION
                || hasDuplicates(output.evidenceIds())
                || output.candidateChunks() == null
                || output.candidateChunks().size() > MAX_CHUNK_REFS
                || hasDuplicates(output.candidateChunks().stream()
                        .map(ChunkCandidateRef::chunkId)
                        .toList())) {
            throw new IllegalArgumentException("retrieval output is invalid");
        }
    }

    private void validateRetrievedEvidenceScope(
            RetrievedEvidenceOutput output, GenerationState state, String scopeKey) {
        Set<UUID> allowed = state.snapshot().verifiedEvidence().stream()
                .map(VerifiedEvidence::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!output.questionId().toString().equals(scopeKey)
                || !allowed.containsAll(output.evidenceIds())) {
            throw domainFailure(
                    "COVER_GENERATION_EVIDENCE_SCOPE_INVALID",
                    "자기소개서에 사용할 승인 근거를 확인하지 못했습니다.");
        }
    }

    private void validateAllocation(ExperienceAllocationOutputV2 output) {
        if (!ALLOCATION_SCHEMA_V2.equals(output.schemaVersion())
                || output.allocations() == null
                || output.allocations().size() > 20
                || hasDuplicates(output.allocations().stream()
                        .map(ExperienceAllocationV2::questionId)
                        .toList())) {
            throw new IllegalArgumentException("allocation is invalid");
        }
        output.allocations().forEach(value -> {
            if (value.questionId() == null
                    || value.evidenceIds() == null
                    || value.evidenceIds().size() > MAX_EVIDENCE_PER_QUESTION
                    || hasDuplicates(value.evidenceIds())
                    || (value.duplicationReason() != null && value.duplicationReason().length() > 1_000)
                    || (value.distinctEmphasis() != null && value.distinctEmphasis().length() > 1_000)) {
                throw new IllegalArgumentException("allocation item is invalid");
            }
        });
    }

    private void validateAllocationScope(
            ExperienceAllocationOutputV2 output,
            GenerationState state,
            StepExecutionContext context) {
        Map<String, Object> retrieved = context.scopedEphemeral(RETRIEVE_EVIDENCE);
        List<UUID> expected = state.snapshot().questions().stream()
                .map(GenerationQuestion::questionId)
                .filter(id -> retrieved.containsKey(id.toString()))
                .toList();
        if (!expected.equals(output.allocations().stream()
                .map(ExperienceAllocationV2::questionId)
                .toList())) {
            throw domainFailure(
                    "COVER_GENERATION_ALLOCATION_SCOPE_INVALID",
                    "자기소개서 경험 배분 범위를 확인하지 못했습니다.");
        }
        for (ExperienceAllocationV2 allocation : output.allocations()) {
            RetrievedEvidenceOutput candidate = cast(
                    retrieved.get(allocation.questionId().toString()), RetrievedEvidenceOutput.class);
            if (!candidate.evidenceIds().containsAll(allocation.evidenceIds())) {
                throw domainFailure(
                        "COVER_GENERATION_ALLOCATION_EVIDENCE_INVALID",
                        "문항별 승인 근거 배분을 확인하지 못했습니다.");
            }
            boolean duplicated = allocation.evidenceIds().stream().anyMatch(id ->
                    output.allocations().stream()
                                    .filter(other -> !other.questionId().equals(allocation.questionId()))
                                    .filter(other -> other.evidenceIds().contains(id))
                                    .count()
                            > 0);
            if (duplicated
                    && (allocation.duplicationReason() == null
                            || allocation.duplicationReason().isBlank()
                            || allocation.distinctEmphasis() == null
                            || allocation.distinctEmphasis().isBlank())) {
                throw new IllegalArgumentException(
                        "duplicated experience requires reason and distinct emphasis");
            }
        }
    }

    private Set<UUID> referencedEvidenceV3(FactCheckAnswerOutputV3 output) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        output.issues().forEach(value -> ids.addAll(value.evidenceIds()));
        output.verifiedClaims().forEach(value -> ids.addAll(value.evidenceIds()));
        return Set.copyOf(ids);
    }

    private boolean requiresDuplicationWarningV3(
            UUID questionId, String answerText, StepExecutionContext context) {
        PlanQuestionsOutputV3 plans = requiredEphemeral(context, PLAN_QUESTIONS, PlanQuestionsOutputV3.class);
        ExperienceAllocationOutputV2 allocations = requiredEphemeral(
                context, ALLOCATE_EXPERIENCES, ExperienceAllocationOutputV2.class);
        QuestionPlanV3 plan = plans.plans().stream()
                .filter(value -> value.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
        ExperienceAllocationV2 allocation = allocations.allocations().stream()
                .filter(value -> value.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
        return context.scopedEphemeral(WRITE_ANSWER).values().stream()
                .filter(WrittenAnswerOutputV3.class::isInstance)
                .map(WrittenAnswerOutputV3.class::cast)
                .filter(value -> !value.questionId().equals(questionId))
                .anyMatch(value -> {
                    QuestionPlanV3 siblingPlan = plans.plans().stream()
                            .filter(candidate -> candidate.questionId().equals(value.questionId()))
                            .findFirst()
                            .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
                    ExperienceAllocationV2 siblingAllocation = allocations.allocations().stream()
                            .filter(candidate -> candidate.questionId().equals(value.questionId()))
                            .findFirst()
                            .orElseThrow(CoverLetterGenerationWorkflow.this::configurationFailure);
                    return CoverLetterWorkflowV3Policy.duplication(
                                    answerText,
                                    Set.copyOf(allocation.evidenceIds()),
                                    plan.coreMessage(),
                                    plainText(mapTipTap(value.content())),
                                    Set.copyOf(siblingAllocation.evidenceIds()),
                                    siblingPlan.coreMessage(),
                                    allocation.distinctEmphasis())
                            .warningRequired();
                });
    }

    private VerificationResult verificationResultV3(FactCheckAnswerOutputV3 output) {
        List<VerificationIssue> issues = output.issues().stream()
                .map(value -> new VerificationIssue(
                        value.code(), value.severity(), value.message(), value.relatedText(), value.evidenceIds()))
                .toList();
        VerificationStatus status = issues.stream().anyMatch(value -> value.severity() == IssueSeverity.ERROR)
                ? VerificationStatus.FAILED
                : issues.isEmpty() ? VerificationStatus.PASSED : VerificationStatus.WARNING;
        return new VerificationResult(
                status,
                issues,
                output.suggestions(),
                output.verifiedClaims().stream()
                        .map(value -> new VerifiedClaim(
                                value.exactAnswerExcerpt(), true, value.evidenceIds()))
                        .toList());
    }

    private List<EvidenceUse> evidenceUsesV3(
            WrittenAnswerOutputV3 answer, FactCheckAnswerOutputV3 factCheck) {
        LinkedHashMap<UUID, EvidenceUse> uses = new LinkedHashMap<>();
        answer.claims().forEach(value -> uses.putIfAbsent(
                value.evidenceId(),
                new EvidenceUse(
                        value.evidenceId(),
                        value.exactAnswerExcerpt(),
                        CoverLetterEvidenceUsageType.SUPPORTING_CLAIM)));
        factCheck.verifiedClaims().stream()
                .flatMap(claim -> claim.evidenceIds().stream().map(id -> new EvidenceUse(
                        id, claim.exactAnswerExcerpt(), CoverLetterEvidenceUsageType.FACT_CHECK)))
                .forEach(value -> uses.putIfAbsent(value.evidenceId(), value));
        return List.copyOf(uses.values());
    }

    private void validateApplyOutput(ApplyAnswerRequestOutput output) {
        if (!APPLY_SCHEMA.equals(output.schemaVersion())
                || output.agentRunId() == null
                || output.coverLetterId() == null
                || output.questionId() == null
                || output.expectedCoverLetterVersion() < 0
                || !isHash(output.snapshotHash())
                || !isHash(output.answerHash())
                || !isHash(output.factCheckHash())) {
            throw new IllegalArgumentException("answer apply output is invalid");
        }
    }

    private boolean requiresDuplicationWarning(
            UUID questionId, String answerText, StepExecutionContext context) {
        Set<String> answerTokens = tokens(answerText);
        if (answerTokens.size() < 8) return false;
        return context.scopedEphemeral(WRITE_ANSWER).values().stream()
                .filter(WrittenAnswerOutputV2.class::isInstance)
                .map(WrittenAnswerOutputV2.class::cast)
                .filter(value -> !value.questionId().equals(questionId))
                .map(value -> tokens(plainText(mapTipTap(value.content()))))
                .filter(value -> value.size() >= 8)
                .anyMatch(value -> {
                    Set<String> intersection = new HashSet<>(answerTokens);
                    intersection.retainAll(value);
                    Set<String> union = new HashSet<>(answerTokens);
                    union.addAll(value);
                    return !union.isEmpty()
                            && ((double) intersection.size() / union.size()) >= 0.85d;
                });
    }

    private VerificationResult verificationResult(FactCheckAnswerOutputV2 output) {
        List<VerificationIssue> issues = output.issues().stream()
                .map(value -> new VerificationIssue(
                        value.code(),
                        value.severity(),
                        value.message(),
                        value.relatedText(),
                        value.evidenceIds()))
                .toList();
        VerificationStatus status = issues.stream()
                        .anyMatch(value -> value.severity() == IssueSeverity.ERROR)
                ? VerificationStatus.FAILED
                : issues.isEmpty() ? VerificationStatus.PASSED : VerificationStatus.WARNING;
        return new VerificationResult(
                status,
                issues,
                output.suggestions(),
                output.verifiedClaims().stream()
                        .map(value -> new VerifiedClaim(
                                value.claim(), value.supported(), value.evidenceIds()))
                        .toList());
    }

    private List<EvidenceUse> evidenceUses(
            WrittenAnswerOutputV2 answer, FactCheckAnswerOutputV2 factCheck) {
        LinkedHashMap<UUID, EvidenceUse> uses = new LinkedHashMap<>();
        answer.claims().forEach(value -> uses.putIfAbsent(
                value.evidenceId(),
                new EvidenceUse(
                        value.evidenceId(),
                        value.claimText(),
                        CoverLetterEvidenceUsageType.SUPPORTING_CLAIM)));
        factCheck.verifiedClaims().stream()
                .flatMap(claim -> claim.evidenceIds().stream()
                        .map(id -> new EvidenceUse(
                                id,
                                claim.claim(),
                                CoverLetterEvidenceUsageType.FACT_CHECK)))
                .forEach(value -> uses.putIfAbsent(value.evidenceId(), value));
        return List.copyOf(uses.values());
    }

    private Set<UUID> referencedEvidence(FactCheckAnswerOutputV2 output) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        output.issues().forEach(value -> ids.addAll(value.evidenceIds()));
        output.verifiedClaims().forEach(value -> ids.addAll(value.evidenceIds()));
        return Set.copyOf(ids);
    }

    private VerificationResult verificationResult(
            FactCheckAnswerOutput output) {
        List<VerificationIssue> issues = output.issues().stream()
                .map(value -> new VerificationIssue(
                        value.code(),
                        value.severity(),
                        value.message(),
                        value.relatedText(),
                        value.evidenceIds()))
                .toList();
        VerificationStatus status = issues.stream()
                        .anyMatch(value -> value.severity() == IssueSeverity.ERROR)
                ? VerificationStatus.FAILED
                : issues.isEmpty()
                        ? VerificationStatus.PASSED
                        : VerificationStatus.WARNING;
        return new VerificationResult(
                status,
                issues,
                output.suggestions(),
                output.verifiedClaims().stream()
                        .map(value -> new VerifiedClaim(
                                value.claim(),
                                value.supported(),
                                value.evidenceIds()))
                        .toList());
    }

    private List<EvidenceUse> evidenceUses(
            WrittenAnswerOutput answer, FactCheckAnswerOutput factCheck) {
        LinkedHashMap<UUID, EvidenceUse> uses = new LinkedHashMap<>();
        answer.claims().forEach(value -> uses.putIfAbsent(
                value.evidenceId(),
                new EvidenceUse(
                        value.evidenceId(),
                        value.claimText(),
                        CoverLetterEvidenceUsageType.SUPPORTING_CLAIM)));
        factCheck.verifiedClaims().stream()
                .flatMap(claim -> claim.evidenceIds().stream()
                        .map(id -> new EvidenceUse(
                                id,
                                claim.claim(),
                                CoverLetterEvidenceUsageType.FACT_CHECK)))
                .forEach(value -> uses.putIfAbsent(value.evidenceId(), value));
        return List.copyOf(uses.values());
    }

    private Set<UUID> referencedEvidence(FactCheckAnswerOutput output) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        output.issues().forEach(value -> ids.addAll(value.evidenceIds()));
        output.verifiedClaims().forEach(value -> ids.addAll(value.evidenceIds()));
        return Set.copyOf(ids);
    }

    private List<UUID> selectEvidence(
            GenerationSnapshot snapshot, String queryText) {
        LinkedHashSet<UUID> selected =
                new LinkedHashSet<>(snapshot.preferredEvidenceIds());
        Set<String> tokens = tokens(queryText);
        snapshot.verifiedEvidence().stream()
                .sorted(Comparator.<VerifiedEvidence>comparingInt(value ->
                                -overlap(
                                        tokens,
                                        value.title()
                                                + " "
                                                + value.evidenceCategory()
                                                + " "
                                                + value.content()))
                        .thenComparing(VerifiedEvidence::id))
                .map(VerifiedEvidence::id)
                .forEach(id -> {
                    if (selected.size() < MAX_EVIDENCE_PER_QUESTION) {
                        selected.add(id);
                    }
                });
        return List.copyOf(selected);
    }

    private Set<String> tokens(String value) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) tokens.add(token);
        }
        return Set.copyOf(tokens);
    }

    private int overlap(Set<String> query, String value) {
        Set<String> candidate = tokens(value == null ? "" : value);
        return (int) query.stream().filter(candidate::contains).count();
    }

    private List<String> unsupportedNumbers(
            String answer, List<VerifiedEvidence> evidence) {
        String evidenceText = evidence.stream()
                .map(VerifiedEvidence::content)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" "));
        List<String> unsupported = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(answer);
        while (matcher.find()) {
            String number = matcher.group();
            if (!evidenceText.contains(number)) unsupported.add(number);
        }
        return unsupported.stream().distinct().toList();
    }

    private ApprovedEvidenceInput approvedEvidence(
            GenerationState state, UUID evidenceId) {
        return state.snapshot().verifiedEvidence().stream()
                .filter(value -> value.id().equals(evidenceId))
                .findFirst()
                .map(value -> new ApprovedEvidenceInput(
                        value.id(),
                        value.sourceType().name(),
                        value.evidenceCategory(),
                        bounded(value.title(), 250),
                        bounded(value.content(), 4_000),
                        value.version()))
                .orElseThrow(() -> domainFailure(
                        "COVER_GENERATION_EVIDENCE_STALE",
                        "자기소개서에 사용할 승인 근거가 변경되었습니다."));
    }

    private GenerationQuestion question(
            GenerationState state, UUID questionId) {
        return state.snapshot().questions().stream()
                .filter(value -> value.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> domainFailure(
                        "COVER_GENERATION_QUESTION_STALE",
                        "자기소개서 문항이 변경되었습니다."));
    }

    private List<Double> parseSingleVector(String rawJson, int dimension) {
        try {
            EmbeddingValuesOutput output =
                    objectMapper.readValue(rawJson, EmbeddingValuesOutput.class);
            if (output.vectors().size() != 1
                    || output.vectors().getFirst().size() != dimension
                    || output.vectors().getFirst().stream()
                            .anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("embedding output is invalid");
            }
            return output.vectors().getFirst();
        } catch (RuntimeException exception) {
            throw AiExecutionException.deterministicStructuredOutput(
                    "AI_STRUCTURED_OUTPUT_INVALID",
                    "AI 결과 형식을 확인하지 못했습니다.",
                    ValidationPhase.JAVA_BINDING);
        }
    }

    private TipTapDocumentDto mapTipTap(ProviderTipTapDocumentOutput document) {
        if (document == null || document.content() == null || document.content().size() > 1_000) {
            throw new IllegalArgumentException("Provider TipTap document is invalid");
        }
        int[] nodes = {0};
        List<TipTapNodeDto> content = document.content().stream()
                .map(node -> mapTipTapNode(node, 1, nodes))
                .toList();
        return new TipTapDocumentDto(document.type(), content);
    }

    private TipTapNodeDto mapTipTapNode(
            ProviderTipTapNodeOutput node, int depth, int[] nodes) {
        if (node == null || depth > 20 || ++nodes[0] > 5_000
                || node.marks() == null || node.marks().size() > 2
                || node.content() == null || node.content().size() > 1_000) {
            throw new IllegalArgumentException("Provider TipTap node is invalid");
        }
        List<TipTapMarkDto> marks = node.marks().stream()
                .map(mark -> mark == null ? null : new TipTapMarkDto(mark.type()))
                .toList();
        List<TipTapNodeDto> content = node.content().stream()
                .map(child -> mapTipTapNode(child, depth + 1, nodes))
                .toList();
        return new TipTapNodeDto(node.type(), node.text(), marks, content);
    }

    private void validateTipTap(TipTapDocumentDto document) {
        if (!"doc".equals(document.type())
                || document.content() == null
                || document.content().size() > 1_000) {
            throw new IllegalArgumentException("TipTap document is invalid");
        }
        int[] nodes = {0};
        for (TipTapNodeDto node : document.content()) {
            validateNode(node, false, nodes);
        }
        if (nodes[0] > 5_000) {
            throw new IllegalArgumentException("TipTap node count is invalid");
        }
    }

    private void validateNode(
            TipTapNodeDto node, boolean root, int[] nodes) {
        nodes[0]++;
        if (node == null
                || !ALLOWED_NODES.contains(node.type())
                || "doc".equals(node.type())) {
            throw new IllegalArgumentException("TipTap node type is invalid");
        }
        boolean text = "text".equals(node.type());
        if (text) {
            requireText(node.text(), MAX_TEXT);
            if (node.content() != null && !node.content().isEmpty()) {
                throw new IllegalArgumentException("text node cannot contain child nodes");
            }
            if (node.marks() == null
                    || node.marks().size() > 2
                    || node.marks().stream()
                            .anyMatch(mark -> mark == null
                                    || !ALLOWED_MARKS.contains(mark.type()))
                    || node.marks().stream()
                                    .map(mark -> mark.type())
                                    .distinct()
                                    .count()
                            != node.marks().size()) {
                throw new IllegalArgumentException("TipTap marks are invalid");
            }
            return;
        }
        if (node.text() != null
                || (node.marks() != null && !node.marks().isEmpty())
                || node.content() == null
                || node.content().size() > 1_000) {
            throw new IllegalArgumentException("TipTap container is invalid");
        }
        if ("hardBreak".equals(node.type()) && !node.content().isEmpty()) {
            throw new IllegalArgumentException("hardBreak cannot contain child nodes");
        }
        for (TipTapNodeDto child : node.content()) {
            validateNode(child, false, nodes);
        }
    }

    private String plainText(TipTapDocumentDto document) {
        StringBuilder text = new StringBuilder();
        appendPlain(document.content(), text);
        return text.toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
    }

    private void appendPlain(List<TipTapNodeDto> nodes, StringBuilder output) {
        for (int index = 0; index < nodes.size(); index++) {
            TipTapNodeDto node = nodes.get(index);
            if ("text".equals(node.type())) {
                output.append(node.text());
            } else if ("hardBreak".equals(node.type())) {
                output.append('\n');
            } else {
                appendPlain(node.content(), output);
                if (Set.of("paragraph", "listItem").contains(node.type())
                        && (output.isEmpty()
                                || output.charAt(output.length() - 1) != '\n')) {
                    output.append('\n');
                }
            }
        }
        while (!output.isEmpty()
                && output.charAt(output.length() - 1) == '\n') {
            output.setLength(output.length() - 1);
        }
    }

    private <T> T requiredEphemeral(
            StepExecutionContext context, String stepKey, Class<T> type) {
        return cast(context.ephemeral(stepKey), type);
    }

    private <T> T requiredScopedEphemeral(
            StepExecutionContext context,
            String stepKey,
            UUID scopeKey,
            Class<T> type) {
        return cast(context.ephemeral(stepKey, scopeKey.toString()), type);
    }

    private <T> T cast(Object value, Class<T> type) {
        if (!type.isInstance(value)) throw configurationFailure();
        return type.cast(value);
    }

    private <T> T read(JsonNode value, Class<T> type) {
        try {
            return objectMapper.treeToValue(value, type);
        } catch (RuntimeException exception) {
            throw configurationFailure();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw configurationFailure();
        }
    }

    private String stableHash(Object value) {
        return sha256(write(value));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private boolean hasDuplicates(List<?> values) {
        return values == null || new HashSet<>(values).size() != values.size();
    }

    private List<UUID> uuidArray(JsonNode node, int minimum, int maximum) {
        if (node == null
                || !node.isArray()
                || node.size() < minimum
                || node.size() > maximum) {
            throw new IllegalArgumentException("UUID array is invalid");
        }
        List<UUID> values = new ArrayList<>();
        node.forEach(value -> values.add(UUID.fromString(value.asText())));
        if (hasDuplicates(values)) {
            throw new IllegalArgumentException("UUID array contains duplicates");
        }
        return List.copyOf(values);
    }

    private Set<String> recordFields(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String bounded(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private void requireText(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("text is invalid");
        }
    }

    private void requireTexts(
            List<String> values, int maximumCount, int maximumLength) {
        if (values == null
                || values.size() > maximumCount
                || values.stream().anyMatch(value -> {
                    try {
                        requireText(value, maximumLength);
                        return false;
                    } catch (IllegalArgumentException exception) {
                        return true;
                    }
                })) {
            throw new IllegalArgumentException("text list is invalid");
        }
    }

    private AiExecutionException mapBusiness(BusinessException exception) {
        if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            return ownerFailure();
        }
        if (exception.errorCode() == ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED) {
            return AiExecutionException.nonRetryable(
                    FailureKind.BUDGET,
                    exception.errorCode().code(),
                    exception.errorCode().defaultMessage());
        }
        return domainFailure(
                exception.errorCode().code(),
                exception.errorCode().defaultMessage());
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_COVER_GENERATION_NOT_CONFIGURED",
                "자기소개서 AI 생성 구성이 준비되지 않았습니다.");
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                ErrorCode.RESOURCE_NOT_FOUND.code(),
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    private AiExecutionException domainFailure(String code, String message) {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION, code, message);
    }

    private record GenerationState(
            GenerationSnapshot snapshot,
            UUID agentRunId,
            List<UUID> requestedQuestionIds,
            List<UUID> reusedQuestionIds,
            EmbeddingPolicySnapshot embeddingPolicy) {}

    private record PlanningInvocationInput(
            List<QuestionPlanningInput> questions,
            List<RequirementInput> requirements,
            boolean avoidExperienceDuplication) {
        private PlanningInvocationInput {
            questions = copy(questions);
            requirements = copy(requirements);
        }
    }

    public enum QuestionType {
        MOTIVATION,
        FUTURE_CONTRIBUTION,
        ROLE_COMPETENCY,
        TECHNICAL_PROJECT,
        PROBLEM_SOLVING,
        COLLABORATION_CONFLICT,
        CHALLENGE_FAILURE,
        GROWTH_VALUES,
        FREEFORM,
        OTHER
    }

    public enum NarrativeFramework {
        MOTIVATION_CONNECTION,
        FUTURE_CONTRIBUTION_PATH,
        COMPETENCY_EVIDENCE_APPLICATION,
        TECHNICAL_DECISION_TRADEOFF,
        PROBLEM_ACTION_RESULT,
        COLLABORATION_ALIGNMENT,
        CHALLENGE_LEARNING,
        VALUES_TO_ACTION,
        DIRECT_RESPONSE
    }

    public enum HeadingPolicy {
        ALLOWED,
        DISALLOWED,
        OPTIONAL
    }

    public enum VerificationIssueKind {
        FACTUAL,
        REQUIREMENT,
        QUALITY,
        DUPLICATION
    }

    public record JobWritingContextInput(
            String companyName,
            String jobTitle,
            String positionName,
            String boundedJobDescription,
            List<RequirementInput> requirements,
            boolean analysisOutdated) {
        public JobWritingContextInput {
            requirements = copy(requirements);
        }
    }

    public record EvidencePlanningInput(
            UUID evidenceId,
            String sourceType,
            String evidenceCategory,
            String title,
            String boundedContent,
            boolean preferred) {}

    public record PlanQuestionsInputV2(
            String schemaVersion,
            JobWritingContextInput job,
            List<QuestionPlanningInput> questions,
            List<RequirementInput> requirements,
            List<EvidencePlanningInput> evidenceCandidates,
            int omittedEvidenceCount,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsInputV2 {
            questions = copy(questions);
            requirements = copy(requirements);
            evidenceCandidates = copy(evidenceCandidates);
        }
    }

    public record QuestionPlanV2(
            UUID questionId,
            QuestionType questionType,
            String coreMessage,
            NarrativeFramework narrativeFramework,
            String objective,
            List<String> requiredElements,
            List<String> avoidContent,
            List<Integer> requirementIndexes,
            String roleConnection,
            String companyConnection,
            List<String> evidenceSelectionCriteria,
            int targetCharacterCount,
            HeadingPolicy headingPolicy) {
        public QuestionPlanV2 {
            requiredElements = copy(requiredElements);
            avoidContent = copy(avoidContent);
            requirementIndexes = copy(requirementIndexes);
            evidenceSelectionCriteria = copy(evidenceSelectionCriteria);
        }
    }

    public record PlanQuestionsOutputV2(
            String schemaVersion,
            List<QuestionPlanV2> plans,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsOutputV2 {
            plans = copy(plans);
        }
    }

    public record AnalyzeQuestionInputV2(
            String schemaVersion,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlanV2 plan,
            JobWritingContextInput job,
            List<RequirementInput> requirements) {
        public AnalyzeQuestionInputV2 {
            requirements = copy(requirements);
        }
    }

    public record QuestionAnalysisOutputV2(
            String schemaVersion,
            UUID questionId,
            QuestionType questionType,
            String intent,
            String directAnswerDirection,
            String openingCoreMessage,
            List<String> requiredElements,
            List<String> avoidContent,
            NarrativeFramework narrativeFramework,
            int situationWeight,
            int actionWeight,
            int resultWeight,
            int learningWeight,
            String personalActionFocus,
            List<String> requiredEvidenceTraits,
            List<Integer> requirementIndexes,
            String roleConnection,
            String companyConnection,
            String conclusionDirection,
            HeadingPolicy headingPolicy) {
        public QuestionAnalysisOutputV2 {
            requiredElements = copy(requiredElements);
            avoidContent = copy(avoidContent);
            requiredEvidenceTraits = copy(requiredEvidenceTraits);
            requirementIndexes = copy(requirementIndexes);
        }
    }

    public record AllocationCandidateInputV2(
            UUID questionId,
            QuestionType questionType,
            String coreMessage,
            NarrativeFramework narrativeFramework,
            List<String> requiredEvidenceTraits,
            List<EvidencePlanningInput> candidateEvidence) {
        public AllocationCandidateInputV2 {
            requiredEvidenceTraits = copy(requiredEvidenceTraits);
            candidateEvidence = copy(candidateEvidence);
        }
    }

    public record AllocateExperiencesInputV2(
            String schemaVersion,
            List<AllocationCandidateInputV2> candidates,
            boolean avoidExperienceDuplication) {
        public AllocateExperiencesInputV2 {
            candidates = copy(candidates);
        }
    }

    public record ExperienceAllocationV2(
            UUID questionId,
            List<UUID> evidenceIds,
            @ProviderNullable String duplicationReason,
            @ProviderNullable String distinctEmphasis) {
        public ExperienceAllocationV2 {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record ExperienceAllocationOutputV2(
            String schemaVersion, List<ExperienceAllocationV2> allocations) {
        public ExperienceAllocationOutputV2 {
            allocations = copy(allocations);
        }
    }

    public record OtherQuestionStrategyInput(
            UUID questionId,
            String coreMessage,
            List<UUID> evidenceIds,
            String distinctEmphasis) {
        public OtherQuestionStrategyInput {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record WriteAnswerInputV2(
            String schemaVersion,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlanV2 plan,
            QuestionAnalysisOutputV2 analysis,
            int targetCharacterCount,
            List<ApprovedEvidenceInput> verifiedEvidence,
            JobWritingContextInput job,
            UUID currentAnswerVersionId,
            String currentPlainText,
            List<OtherQuestionStrategyInput> otherQuestions,
            HeadingPolicy headingPolicy) {
        public WriteAnswerInputV2 {
            verifiedEvidence = copy(verifiedEvidence);
            otherQuestions = copy(otherQuestions);
        }
    }

    public record WrittenAnswerOutputV2(
            String schemaVersion,
            UUID questionId,
            ProviderTipTapDocumentOutput content,
            List<EvidenceClaimDraft> claims) {
        public WrittenAnswerOutputV2 {
            claims = copy(claims);
        }
    }

    public record SiblingAnswerInput(
            UUID questionId,
            String coreMessage,
            String boundedPlainText,
            List<UUID> evidenceIds) {
        public SiblingAnswerInput {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record FactCheckAnswerInputV2(
            String schemaVersion,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlanV2 plan,
            QuestionAnalysisOutputV2 analysis,
            TipTapDocumentDto content,
            String plainText,
            List<EvidenceClaimDraft> claims,
            List<ApprovedEvidenceInput> verifiedEvidence,
            List<RequirementInput> requirements,
            List<ChunkCandidateRef> candidateChunks,
            List<SiblingAnswerInput> siblingAnswers,
            boolean analysisOutdated) {
        public FactCheckAnswerInputV2 {
            claims = copy(claims);
            verifiedEvidence = copy(verifiedEvidence);
            requirements = copy(requirements);
            candidateChunks = copy(candidateChunks);
            siblingAnswers = copy(siblingAnswers);
        }
    }

    public record VerificationIssueDraftV2(
            VerificationIssueKind issueKind,
            VerificationIssueCode code,
            IssueSeverity severity,
            String message,
            @ProviderNullable String relatedText,
            List<UUID> evidenceIds) {
        public VerificationIssueDraftV2 {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record FactCheckAnswerOutputV2(
            String schemaVersion,
            UUID questionId,
            List<VerificationIssueDraftV2> issues,
            List<String> suggestions,
            List<VerifiedClaimDraft> verifiedClaims) {
        public FactCheckAnswerOutputV2 {
            issues = copy(issues);
            suggestions = copy(suggestions);
            verifiedClaims = copy(verifiedClaims);
        }
    }

    public record ContextAvailabilityInput(
            boolean companyContextAvailable,
            boolean roleContextAvailable,
            boolean jobDescriptionAvailable,
            boolean requirementContextAvailable) {}

    public record QuestionPlanV3(
            UUID questionId,
            QuestionType questionType,
            String coreMessage,
            NarrativeFramework narrativeFramework,
            String objective,
            List<String> requiredElements,
            List<String> avoidContent,
            List<Integer> requirementIndexes,
            @ProviderNullable String roleConnection,
            @ProviderNullable String companyConnection,
            List<String> evidenceSelectionCriteria,
            int targetCharacterCount,
            HeadingPolicy headingPolicy,
            List<NarrativeSectionPlan> narrativeSections) {
        public QuestionPlanV3 {
            requiredElements = copy(requiredElements);
            avoidContent = copy(avoidContent);
            requirementIndexes = copy(requirementIndexes);
            roleConnection = roleConnection == null || roleConnection.isBlank()
                    ? null
                    : roleConnection;
            companyConnection = companyConnection == null || companyConnection.isBlank()
                    ? null
                    : companyConnection;
            evidenceSelectionCriteria = copy(evidenceSelectionCriteria);
            narrativeSections = copy(narrativeSections);
        }
    }

    public record PlanQuestionsInputV3(
            String schemaVersion,
            String outputLocale,
            ContextAvailabilityInput contextAvailability,
            JobWritingContextInput job,
            List<QuestionPlanningInput> questions,
            List<RequirementInput> requirements,
            List<EvidencePlanningInput> evidenceCandidates,
            int omittedEvidenceCount,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsInputV3 {
            questions = copy(questions);
            requirements = copy(requirements);
            evidenceCandidates = copy(evidenceCandidates);
        }
    }

    public record QuestionPlanningInputV4(
            UUID questionId,
            int questionOrder,
            String questionText,
            @ProviderNullable String questionMemo,
            Integer maxLength,
            boolean hasCurrentAnswer) {}

    public record PlanQuestionsInputV4(
            String schemaVersion,
            String outputLocale,
            ContextAvailabilityInput contextAvailability,
            JobWritingContextInput job,
            List<QuestionPlanningInputV4> questions,
            List<RequirementInput> requirements,
            List<EvidencePlanningInput> evidenceCandidates,
            int omittedEvidenceCount,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsInputV4 {
            questions = copy(questions);
            requirements = copy(requirements);
            evidenceCandidates = copy(evidenceCandidates);
        }
    }

    public record PlanQuestionsOutputV3(
            String schemaVersion,
            List<QuestionPlanV3> plans,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsOutputV3 {
            plans = copy(plans);
        }
    }

    public record AnalyzeQuestionInputV3(
            String schemaVersion,
            String outputLocale,
            ContextAvailabilityInput contextAvailability,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlanV3 plan,
            JobWritingContextInput job,
            List<RequirementInput> requirements) {
        public AnalyzeQuestionInputV3 {
            requirements = copy(requirements);
        }
    }

    public record QuestionAnalysisOutputV3(
            String schemaVersion,
            UUID questionId,
            QuestionType questionType,
            String intent,
            String directAnswerDirection,
            String openingCoreMessage,
            List<String> requiredElements,
            List<String> avoidContent,
            NarrativeFramework narrativeFramework,
            List<NarrativeSectionPlan> narrativeSections,
            String personalActionFocus,
            List<String> requiredEvidenceTraits,
            List<Integer> requirementIndexes,
            @ProviderNullable String roleConnection,
            @ProviderNullable String companyConnection,
            String conclusionDirection,
            HeadingPolicy headingPolicy) {
        public QuestionAnalysisOutputV3 {
            requiredElements = copy(requiredElements);
            avoidContent = copy(avoidContent);
            narrativeSections = copy(narrativeSections);
            requiredEvidenceTraits = copy(requiredEvidenceTraits);
            requirementIndexes = copy(requirementIndexes);
            roleConnection = roleConnection == null || roleConnection.isBlank()
                    ? null
                    : roleConnection;
            companyConnection = companyConnection == null || companyConnection.isBlank()
                    ? null
                    : companyConnection;
        }
    }

    public record AllocationCandidateInputV3(
            UUID questionId,
            QuestionType questionType,
            String coreMessage,
            NarrativeFramework narrativeFramework,
            List<NarrativeSectionPlan> narrativeSections,
            List<String> requiredEvidenceTraits,
            List<EvidencePlanningInput> candidateEvidence) {
        public AllocationCandidateInputV3 {
            narrativeSections = copy(narrativeSections);
            requiredEvidenceTraits = copy(requiredEvidenceTraits);
            candidateEvidence = copy(candidateEvidence);
        }
    }

    public record AllocateExperiencesInputV3(
            String schemaVersion,
            String outputLocale,
            List<AllocationCandidateInputV3> candidates,
            boolean avoidExperienceDuplication) {
        public AllocateExperiencesInputV3 {
            candidates = copy(candidates);
        }
    }

    public record EvidenceClaimDraftV3(
            UUID evidenceId, String exactAnswerExcerpt, ClaimType claimType)
            implements CoverLetterWorkflowV3Policy.ClaimView {}

    public record WriteAnswerInputV3(
            String schemaVersion,
            String outputLocale,
            ContextAvailabilityInput contextAvailability,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlanV3 plan,
            QuestionAnalysisOutputV3 analysis,
            int targetCharacterCount,
            List<ApprovedEvidenceInput> verifiedEvidence,
            JobWritingContextInput job,
            UUID currentAnswerVersionId,
            BoundedText currentAnswer,
            List<OtherQuestionStrategyInput> otherQuestions,
            HeadingPolicy headingPolicy) {
        public WriteAnswerInputV3 {
            verifiedEvidence = copy(verifiedEvidence);
            otherQuestions = copy(otherQuestions);
        }
    }

    public record WriteAnswerInputV4(
            String schemaVersion,
            String outputLocale,
            ContextAvailabilityInput contextAvailability,
            UUID questionId,
            String questionText,
            @ProviderNullable String questionMemo,
            Integer maxLength,
            QuestionPlanV3 plan,
            QuestionAnalysisOutputV3 analysis,
            int targetCharacterCount,
            List<ApprovedEvidenceInput> verifiedEvidence,
            JobWritingContextInput job,
            UUID currentAnswerVersionId,
            BoundedText currentAnswer,
            List<OtherQuestionStrategyInput> otherQuestions,
            HeadingPolicy headingPolicy) {
        public WriteAnswerInputV4 {
            verifiedEvidence = copy(verifiedEvidence);
            otherQuestions = copy(otherQuestions);
        }
    }

    public record WrittenAnswerOutputV3(
            String schemaVersion,
            UUID questionId,
            ProviderTipTapDocumentOutput content,
            List<EvidenceClaimDraftV3> claims) {
        public WrittenAnswerOutputV3 {
            claims = copy(claims);
        }
    }

    public record SiblingAnswerInputV3(
            UUID questionId,
            String coreMessage,
            @ProviderNullable String distinctEmphasis,
            BoundedText answer,
            List<UUID> evidenceIds) {
        public SiblingAnswerInputV3 {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record FactCheckAnswerInputV3(
            String schemaVersion,
            String outputLocale,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlanV3 plan,
            QuestionAnalysisOutputV3 analysis,
            TipTapDocumentDto content,
            String plainText,
            List<EvidenceClaimDraftV3> claims,
            List<ApprovedEvidenceInput> verifiedEvidence,
            List<RequirementInput> requirements,
            List<ChunkCandidateRef> candidateChunks,
            List<SiblingAnswerInputV3> siblingAnswers,
            boolean analysisOutdated,
            String duplicationPolicyVersion) {
        public FactCheckAnswerInputV3 {
            claims = copy(claims);
            verifiedEvidence = copy(verifiedEvidence);
            requirements = copy(requirements);
            candidateChunks = copy(candidateChunks);
            siblingAnswers = copy(siblingAnswers);
        }
    }

    public record VerifiedClaimDraftV3(
            String exactAnswerExcerpt,
            boolean supported,
            List<UUID> evidenceIds) {
        public VerifiedClaimDraftV3 {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record FactCheckAnswerOutputV3(
            String schemaVersion,
            UUID questionId,
            List<VerificationIssueDraftV2> issues,
            List<String> suggestions,
            List<VerifiedClaimDraftV3> verifiedClaims) {
        public FactCheckAnswerOutputV3 {
            issues = copy(issues);
            suggestions = copy(suggestions);
            verifiedClaims = copy(verifiedClaims);
        }
    }

    public record BuildGenerationContextInput(
            String schemaVersion,
            UUID coverLetterId,
            long coverLetterVersion,
            String snapshotHash) {}

    public record BuildGenerationContextOutput(
            String schemaVersion,
            UUID coverLetterId,
            long coverLetterVersion,
            String snapshotHash,
            UUID jobId,
            UUID analysisId,
            int analysisVersion,
            boolean analysisOutdated,
            List<UUID> questionIds,
            List<UUID> reusedQuestionIds,
            List<UUID> verifiedEvidenceIds,
            List<UUID> preferredEvidenceIds) {
        public BuildGenerationContextOutput {
            questionIds = copy(questionIds);
            reusedQuestionIds = copy(reusedQuestionIds);
            verifiedEvidenceIds = copy(verifiedEvidenceIds);
            preferredEvidenceIds = copy(preferredEvidenceIds);
        }
    }

    public record QuestionPlanningInput(
            UUID questionId,
            int questionOrder,
            String questionText,
            Integer maxLength,
            boolean hasCurrentAnswer) {}

    public record RequirementInput(
            String category, String text, boolean required) {}

    public record PlanQuestionsInput(
            String schemaVersion,
            List<QuestionPlanningInput> questions,
            List<RequirementInput> requirements,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsInput {
            questions = copy(questions);
            requirements = copy(requirements);
        }
    }

    public record QuestionPlan(
            UUID questionId,
            String objective,
            List<String> requiredElements,
            List<String> avoidContent,
            List<Integer> requirementIndexes,
            int targetCharacterCount) {
        public QuestionPlan {
            requiredElements = copy(requiredElements);
            avoidContent = copy(avoidContent);
            requirementIndexes = copy(requirementIndexes);
        }
    }

    public record PlanQuestionsOutput(
            String schemaVersion,
            List<QuestionPlan> plans,
            boolean avoidExperienceDuplication) {
        public PlanQuestionsOutput {
            plans = copy(plans);
        }
    }

    public record AnalyzeQuestionInput(
            String schemaVersion,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionPlan plan,
            List<RequirementInput> jobRequirements) {
        public AnalyzeQuestionInput {
            jobRequirements = copy(jobRequirements);
        }
    }

    public record QuestionAnalysisOutput(
            String schemaVersion,
            UUID questionId,
            String intent,
            List<String> requiredElements,
            List<String> avoidContent,
            List<Integer> requirementIndexes) {
        public QuestionAnalysisOutput {
            requiredElements = copy(requiredElements);
            avoidContent = copy(avoidContent);
            requirementIndexes = copy(requirementIndexes);
        }
    }

    public record RetrieveEvidenceInput(
            String schemaVersion,
            UUID questionId,
            String queryText,
            long embeddingPolicyVersion,
            int embeddingDimension,
            int embeddingGeneration,
            String retrievalPolicyVersion) {}

    public record ChunkCandidateRef(
            UUID chunkId,
            UUID documentId,
            String maskedContent,
            double distance) {}

    public record RetrievedEvidenceOutput(
            String schemaVersion,
            UUID questionId,
            String queryHash,
            List<UUID> evidenceIds,
            List<ChunkCandidateRef> candidateChunks) {
        public RetrievedEvidenceOutput {
            evidenceIds = copy(evidenceIds);
            candidateChunks = copy(candidateChunks);
        }
    }

    public record AllocationCandidateInput(
            UUID questionId, List<UUID> evidenceIds) {
        public AllocationCandidateInput {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record AllocateExperiencesInput(
            String schemaVersion,
            List<AllocationCandidateInput> candidates,
            boolean avoidExperienceDuplication) {
        public AllocateExperiencesInput {
            candidates = copy(candidates);
        }
    }

    public record ExperienceAllocation(
            UUID questionId,
            List<UUID> evidenceIds,
            String duplicationReason) {
        public ExperienceAllocation {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record ExperienceAllocationOutput(
            String schemaVersion, List<ExperienceAllocation> allocations) {
        public ExperienceAllocationOutput {
            allocations = copy(allocations);
        }
    }

    public record ApprovedEvidenceInput(
            UUID id,
            String sourceType,
            String evidenceCategory,
            String title,
            String content,
            long version) {}

    public record WriteAnswerInput(
            String schemaVersion,
            UUID questionId,
            String questionText,
            Integer maxLength,
            QuestionAnalysisOutput analysis,
            List<ApprovedEvidenceInput> verifiedEvidence,
            String companyName,
            String positionName,
            boolean analysisOutdated) {
        public WriteAnswerInput {
            verifiedEvidence = copy(verifiedEvidence);
        }
    }

    public record EvidenceClaimDraft(
            UUID evidenceId, String claimText) {}

    public record ProviderTipTapMarkOutput(String type) {}

    public record ProviderTipTapNodeOutput(
            String type,
            @ProviderNullable String text,
            List<ProviderTipTapMarkOutput> marks,
            List<ProviderTipTapNodeOutput> content) {
        public ProviderTipTapNodeOutput {
            marks = copy(marks);
            content = copy(content);
        }
    }

    public record ProviderTipTapDocumentOutput(
            String type, List<ProviderTipTapNodeOutput> content) {
        public ProviderTipTapDocumentOutput {
            content = copy(content);
        }
    }

    public record WrittenAnswerOutput(
            String schemaVersion,
            UUID questionId,
            ProviderTipTapDocumentOutput content,
            List<EvidenceClaimDraft> claims) {
        public WrittenAnswerOutput {
            claims = copy(claims);
        }
    }

    public record FactCheckAnswerInput(
            String schemaVersion,
            UUID questionId,
            String questionText,
            Integer maxLength,
            TipTapDocumentDto content,
            String plainText,
            List<EvidenceClaimDraft> claims,
            List<ApprovedEvidenceInput> verifiedEvidence,
            List<RequirementInput> jobRequirements,
            List<ChunkCandidateRef> candidateChunks,
            boolean analysisOutdated) {
        public FactCheckAnswerInput {
            claims = copy(claims);
            verifiedEvidence = copy(verifiedEvidence);
            jobRequirements = copy(jobRequirements);
            candidateChunks = copy(candidateChunks);
        }
    }

    public record VerificationIssueDraft(
            VerificationIssueCode code,
            IssueSeverity severity,
            String message,
            String relatedText,
            List<UUID> evidenceIds) {
        public VerificationIssueDraft {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record VerifiedClaimDraft(
            String claim, boolean supported, List<UUID> evidenceIds) {
        public VerifiedClaimDraft {
            evidenceIds = copy(evidenceIds);
        }
    }

    public record FactCheckAnswerOutput(
            String schemaVersion,
            UUID questionId,
            List<VerificationIssueDraft> issues,
            List<String> suggestions,
            List<VerifiedClaimDraft> verifiedClaims) {
        public FactCheckAnswerOutput {
            issues = copy(issues);
            suggestions = copy(suggestions);
            verifiedClaims = copy(verifiedClaims);
        }
    }

    public record ApplyAnswerRequestInput(
            String schemaVersion,
            UUID agentRunId,
            UUID coverLetterId,
            UUID questionId,
            long expectedCoverLetterVersion,
            UUID expectedCurrentVersionId,
            String snapshotHash,
            String answerHash,
            String factCheckHash) {}

    public record ApplyAnswerRequestOutput(
            String schemaVersion,
            UUID agentRunId,
            UUID coverLetterId,
            UUID questionId,
            long expectedCoverLetterVersion,
            UUID expectedCurrentVersionId,
            String snapshotHash,
            String answerHash,
            String factCheckHash) {}

    public record AppliedAnswerOutput(
            String schemaVersion,
            UUID questionId,
            UUID answerVersionId,
            UUID verificationId,
            String sourceType,
            int characterCount,
            long coverLetterVersion,
            String snapshotHash) {}

    public record EmbeddingValuesOutput(List<List<Double>> vectors) {
        public EmbeddingValuesOutput {
            vectors = copy(vectors);
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
