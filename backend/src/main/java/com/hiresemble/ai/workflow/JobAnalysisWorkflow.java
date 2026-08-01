package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.application.model.JobAnalysisModels.CriterionDraft;
import com.hiresemble.job.application.model.JobAnalysisModels.EvidenceUsage;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSnapshot;
import com.hiresemble.job.application.model.JobAnalysisModels.PersistJobAnalysis;
import com.hiresemble.job.application.model.JobAnalysisModels.ProfileContext;
import com.hiresemble.job.application.model.JobAnalysisModels.RequirementItem;
import com.hiresemble.job.application.model.JobAnalysisModels.RetrievedVerifiedEvidence;
import com.hiresemble.job.application.model.JobAnalysisModels.VerifiedEvidence;
import com.hiresemble.job.application.port.JobAnalysisCommandPort;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort.EmbeddingPolicySnapshot;
import com.hiresemble.job.application.port.JobAnalysisQueryPort;
import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.FitCriterionCategory;
import com.hiresemble.job.domain.JobAnalysisEvidenceUsageType;
import com.hiresemble.job.domain.JobFitScoringPolicy;
import com.hiresemble.job.domain.JobFitScoringPolicy.CriterionInput;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bounded P6 JOB_ANALYSIS workflow. Job content is untrusted data, retrieval is owner-scoped,
 * and only the Backend command port can persist an immutable analysis.
 */
public final class JobAnalysisWorkflow {

    public static final String BUILD_JOB_SNAPSHOT = "BUILD_JOB_SNAPSHOT";
    public static final String EXTRACT_REQUIREMENTS = "EXTRACT_REQUIREMENTS";
    public static final String ASSESS_ELIGIBILITY = "ASSESS_ELIGIBILITY";
    public static final String RETRIEVE_VERIFIED_EVIDENCE = "RETRIEVE_VERIFIED_EVIDENCE";
    public static final String MATCH_EVIDENCE = "MATCH_EVIDENCE";
    public static final String SCORE_FIT = "SCORE_FIT";
    public static final String VALIDATE_ANALYSIS = "VALIDATE_ANALYSIS";
    public static final String PERSIST_ANALYSIS = "PERSIST_ANALYSIS";

    public static final String RUBRIC_VERSION = "job-fit-rubric-v1";
    public static final String RETRIEVAL_POLICY_VERSION = "verified-evidence-rag-v1";

    private static final String BUILD_SCHEMA = "job-analysis-build-output-v1";
    private static final String REQUIREMENTS_SCHEMA = "job-analysis-requirements-output-v1";
    private static final String ELIGIBILITY_SCHEMA = "job-analysis-eligibility-output-v1";
    private static final String RETRIEVAL_SCHEMA = "job-analysis-retrieval-output-v1";
    private static final String MATCH_SCHEMA = "job-analysis-match-output-v1";
    private static final String SCORE_SCHEMA = "job-analysis-score-output-v1";
    private static final String VALIDATION_SCHEMA = "job-analysis-validation-output-v1";
    private static final String PERSIST_SCHEMA = "job-analysis-persist-output-v1";
    private static final String INPUT_SCHEMA = "job-analysis-input-v1";

    private static final int MAX_REQUIREMENTS = 100;
    private static final int MAX_RETRIEVED_EVIDENCE = 20;
    private static final int MAX_JOB_CONTENT_CHARACTERS = 80_000;
    private static final int MAX_EVIDENCE_CONTEXT_CHARACTERS = 2_500;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?82[- .]?)?0?1[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(api[_ -]?key|secret|password|token)\\s*[:=]\\s*[^\\s,;]{4,}");
    private static final List<String> PROBABILITY_PHRASES = List.of(
            "합격률",
            "합격 확률",
            "합격 가능성",
            "취업 성공 가능성",
            "chance of acceptance",
            "probability of acceptance");

    private final JobAnalysisQueryPort queryPort;
    private final JobAnalysisCommandPort commandPort;
    private final JobAnalysisEmbeddingQueryPort embeddingQueryPort;
    private final ObjectMapper objectMapper;

    public JobAnalysisWorkflow(
            JobAnalysisQueryPort queryPort,
            JobAnalysisCommandPort commandPort,
            JobAnalysisEmbeddingQueryPort embeddingQueryPort,
            ObjectMapper objectMapper) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.embeddingQueryPort = Objects.requireNonNull(embeddingQueryPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.JOB_ANALYSIS,
                CanonicalWorkflowDefinitions.JOB_ANALYSIS_VERSION,
                List.of(
                        step(BUILD_JOB_SNAPSHOT, new BuildSnapshotExecutor()),
                        step(EXTRACT_REQUIREMENTS, new ExtractRequirementsExecutor()),
                        step(ASSESS_ELIGIBILITY, new AssessEligibilityExecutor()),
                        step(RETRIEVE_VERIFIED_EVIDENCE, new RetrieveEvidenceExecutor()),
                        step(MATCH_EVIDENCE, new MatchEvidenceExecutor()),
                        step(SCORE_FIT, new ScoreFitExecutor()),
                        step(VALIDATE_ANALYSIS, new ValidateAnalysisExecutor()),
                        step(PERSIST_ANALYSIS, new PersistAnalysisExecutor())));
    }

    private ExecutableWorkflowStep step(
            String stepKey, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(stepKey, executor);
    }

    private abstract class AnalysisExecutor<T> implements WorkflowStepExecutor<T> {

        private final String stepKey;
        private final String schemaVersion;
        private final Class<T> outputType;
        private final Set<String> outputFields;

        private AnalysisExecutor(
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
                                    "job analysis structured output schema is invalid");
                        }
                    },
                    value -> validateJavaRecord(value, context),
                    value -> validateWorkflowOutput(value, context),
                    value -> validateDomainOutput(value, context));
        }

        protected void validateJavaRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflowOutput(T output, StepExecutionContext context) {}

        protected void validateDomainOutput(T output, StepExecutionContext context) {}

        protected final AnalysisState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType() != WorkflowType.JOB_ANALYSIS
                    || !CanonicalWorkflowDefinitions.JOB_ANALYSIS_VERSION.equals(
                            context.run().workflowVersion())
                    || !"JOB".equals(context.run().resourceType())
                    || context.run().resourceId() == null
                    || context.run().requestedQualityMode() == null) {
                throw configurationFailure();
            }
            JsonNode input = context.run().inputReferenceSnapshot();
            try {
                UUID jobId = UUID.fromString(input.path("jobId").asText());
                long jobVersion = input.path("jobVersion").asLong(-1);
                String expectedContextHash = input.path("contextHash").asText();
                AiQualityMode qualityMode =
                        AiQualityMode.valueOf(input.path("qualityMode").asText());
                boolean force = input.path("forceReanalyze").asBoolean(false);
                UUID reusableId = input.hasNonNull("reusableAnalysisId")
                        ? UUID.fromString(input.path("reusableAnalysisId").asText())
                        : null;
                if (!jobId.equals(context.run().resourceId())
                        || jobVersion < 0
                        || !isHash(expectedContextHash)
                        || qualityMode != context.run().requestedQualityMode()
                        || (qualityMode != AiQualityMode.ECONOMY
                                && qualityMode != AiQualityMode.BALANCED)
                        || (force && reusableId != null)) {
                    throw new IllegalArgumentException("run input is invalid");
                }
                JobAnalysisSnapshot snapshot = loadSnapshot(
                        context.run().userId(),
                        jobId,
                        jobVersion,
                        qualityMode,
                        expectedContextHash);
                validateSnapshot(input, context, snapshot);
                if (reusableId != null) {
                    UUID expected = reusableId;
                    queryPort.findReusable(
                                    context.run().userId(),
                                    jobId,
                                    expectedContextHash,
                                    qualityMode)
                            .filter(value -> value.summary().id().equals(expected))
                            .orElseThrow(() -> domainFailure(
                                    "JOB_ANALYSIS_REUSABLE_RESULT_STALE",
                                    "재사용할 공고 분석을 확인하지 못했습니다."));
                }
                EmbeddingPolicySnapshot embeddingPolicy = embeddingQueryPort.activePolicy();
                if (embeddingPolicy.version() != snapshot.embeddingPolicyVersion()
                        || embeddingPolicy.generation() != snapshot.embeddingGeneration()
                        || embeddingPolicy.dimension() < 1) {
                    throw domainFailure(
                            "JOB_ANALYSIS_EMBEDDING_POLICY_STALE",
                            "경험 정보 검색 정책이 변경되었습니다.");
                }
                return new AnalysisState(
                        snapshot,
                        context.run().id(),
                        force,
                        reusableId,
                        embeddingPolicy);
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw ownerFailure();
            }
        }

        protected final StepInput localInput(
                AnalysisState state,
                JsonNode refs,
                String canonicalSuffix,
                JsonNode payload) {
            return new StepInput(
                    state.snapshot().jobId().toString(),
                    refs,
                    stepKey
                            + "|"
                            + state.snapshot().contextHash()
                            + "|reuse="
                            + nullSafe(state.reusableAnalysisId())
                            + "|"
                            + canonicalSuffix,
                    payload,
                    null,
                    state.snapshot().jobVersion());
        }

        protected final JsonNode baseRefs(AnalysisState state) {
            JobAnalysisSnapshot snapshot = state.snapshot();
            return objectMapper.createObjectNode()
                    .put("jobId", snapshot.jobId().toString())
                    .put("jobVersion", snapshot.jobVersion())
                    .put("contextHash", snapshot.contextHash())
                    .put("jobContentHash", snapshot.jobContentHash())
                    .put("profileSnapshotHash", snapshot.profileSnapshotHash())
                    .put("evidenceSnapshotHash", snapshot.evidenceSnapshotHash())
                    .put("rubricVersion", snapshot.rubricVersion())
                    .put("workflowVersion", snapshot.workflowVersion())
                    .put("qualityMode", snapshot.qualityMode().name())
                    .put("embeddingPolicyVersion", snapshot.embeddingPolicyVersion())
                    .put("embeddingDimension", state.embeddingPolicy().dimension())
                    .put("embeddingGeneration", snapshot.embeddingGeneration())
                    .put("retrievalPolicyVersion", snapshot.retrievalPolicyVersion())
                    .put("forceReanalyze", state.forceReanalyze())
                    .put(
                            "reusableAnalysisId",
                            state.reusableAnalysisId() == null
                                    ? null
                                    : state.reusableAnalysisId().toString());
        }

        protected final AiGatewayResponse localResponse(Object output) {
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), java.util.List.of());
            } catch (Exception exception) {
                throw configurationFailure();
            }
        }

        protected final JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
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

    private final class BuildSnapshotExecutor
            extends AnalysisExecutor<BuildSnapshotOutput> {

        private BuildSnapshotExecutor() {
            super(BUILD_JOB_SNAPSHOT, BUILD_SCHEMA, BuildSnapshotOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            return localInput(
                    state,
                    baseRefs(state),
                    BUILD_SCHEMA,
                    tree(new BuildSnapshotInput(
                            INPUT_SCHEMA,
                            state.snapshot().jobId(),
                            state.snapshot().jobVersion(),
                            state.snapshot().contextHash(),
                            state.reusableAnalysisId())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            JobAnalysisSnapshot snapshot = state.snapshot();
            List<EvidenceSnapshotReference> evidence = snapshot.verifiedEvidence().stream()
                    .sorted(Comparator.comparing(VerifiedEvidence::id))
                    .map(value -> new EvidenceSnapshotReference(
                            value.id(),
                            value.version(),
                            value.evidenceHash(),
                            value.evidenceCategory(),
                            value.sourceType()))
                    .toList();
            return localResponse(new BuildSnapshotOutput(
                    BUILD_SCHEMA,
                    snapshot.jobId(),
                    snapshot.jobVersion(),
                    snapshot.contextHash(),
                    snapshot.jobContentHash(),
                    snapshot.profileSnapshotHash(),
                    snapshot.evidenceSnapshotHash(),
                    snapshot.rubricVersion(),
                    snapshot.workflowVersion(),
                    snapshot.qualityMode(),
                    snapshot.embeddingPolicyVersion(),
                    state.embeddingPolicy().dimension(),
                    snapshot.embeddingGeneration(),
                    snapshot.retrievalPolicyVersion(),
                    state.reusing(),
                    state.reusableAnalysisId(),
                    evidence));
        }

        @Override
        protected void validateJavaRecord(
                BuildSnapshotOutput output, StepExecutionContext context) {
            if (!BUILD_SCHEMA.equals(output.schemaVersion())
                    || output.jobId() == null
                    || output.jobVersion() < 0
                    || !isHash(output.contextHash())
                    || !isHash(output.jobContentHash())
                    || !isHash(output.profileSnapshotHash())
                    || !isHash(output.evidenceSnapshotHash())
                    || !RUBRIC_VERSION.equals(output.rubricVersion())
                    || !CanonicalWorkflowDefinitions.JOB_ANALYSIS_VERSION.equals(
                            output.workflowVersion())
                    || output.qualityMode() == null
                    || output.embeddingPolicyVersion() < 1
                    || output.embeddingDimension() < 1
                    || output.embeddingGeneration() < 1
                    || !RETRIEVAL_POLICY_VERSION.equals(output.retrievalPolicyVersion())
                    || output.verifiedEvidence() == null
                    || output.reusable() != (output.reusableAnalysisId() != null)) {
                throw new IllegalArgumentException("job snapshot output is invalid");
            }
            for (EvidenceSnapshotReference evidence : output.verifiedEvidence()) {
                if (evidence == null
                        || evidence.id() == null
                        || evidence.version() < 0
                        || !isHash(evidence.evidenceHash())
                        || !hasText(evidence.evidenceCategory(), 80)
                        || evidence.sourceType() == null) {
                    throw new IllegalArgumentException("evidence reference is invalid");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                BuildSnapshotOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            if (!output.jobId().equals(state.snapshot().jobId())
                    || output.jobVersion() != state.snapshot().jobVersion()
                    || !output.contextHash().equals(state.snapshot().contextHash())
                    || output.embeddingPolicyVersion()
                            != state.embeddingPolicy().version()
                    || output.embeddingDimension()
                            != state.embeddingPolicy().dimension()
                    || output.embeddingGeneration()
                            != state.embeddingPolicy().generation()
                    || output.reusable() != state.reusing()
                    || !Objects.equals(
                            output.reusableAnalysisId(), state.reusableAnalysisId())) {
                throw domainFailure(
                        "JOB_ANALYSIS_SNAPSHOT_MISMATCH",
                        "공고 분석 입력이 변경되었습니다.");
            }
        }
    }

    private final class ExtractRequirementsExecutor
            extends AnalysisExecutor<ExtractRequirementsOutput> {

        private ExtractRequirementsExecutor() {
            super(
                    EXTRACT_REQUIREMENTS,
                    REQUIREMENTS_SCHEMA,
                    ExtractRequirementsOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            requiredEphemeral(context, BUILD_JOB_SNAPSHOT, BuildSnapshotOutput.class);
            JsonNode refs = baseRefs(state);
            if (state.reusing()) {
                return localInput(
                        state,
                        refs,
                        "reuse|" + state.reusableAnalysisId(),
                        tree(new ReuseInput(INPUT_SCHEMA, state.reusableAnalysisId())));
            }
            JobAnalysisSnapshot snapshot = state.snapshot();
            UntrustedJobPosting posting = new UntrustedJobPosting(
                    maskAndLimit(snapshot.companyName(), 200),
                    maskAndLimit(snapshot.title(), 300),
                    maskAndLimit(snapshot.positionName(), 300),
                    maskAndLimit(snapshot.roleCategory(), 100),
                    maskAndLimit(snapshot.employmentType(), 100),
                    maskAndLimit(snapshot.location(), 200),
                    maskAndLimit(snapshot.descriptionText(), MAX_JOB_CONTENT_CHARACTERS),
                    snapshot.descriptionText().length() > MAX_JOB_CONTENT_CHARACTERS);
            return localInput(
                    state,
                    refs,
                    snapshot.jobContentHash(),
                    tree(new ExtractRequirementsInput(INPUT_SCHEMA, posting)));
        }

        @Override
        public boolean requiresProvider(StepExecutionContext context) {
            return !state(context).reusing();
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            if (state.reusing()) {
                return localResponse(new ExtractRequirementsOutput(
                        REQUIREMENTS_SCHEMA,
                        true,
                        state.reusableAnalysisId(),
                        List.of()));
            }
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
                ExtractRequirementsOutput output, StepExecutionContext context) {
            if (!REQUIREMENTS_SCHEMA.equals(output.schemaVersion())
                    || output.requirements() == null
                    || output.requirements().size() > MAX_REQUIREMENTS
                    || output.reusable() != (output.reusableAnalysisId() != null)) {
                throw new IllegalArgumentException("requirements output is invalid");
            }
            if (output.reusable() && !output.requirements().isEmpty()) {
                throw new IllegalArgumentException("reuse output must be reference-only");
            }
            for (RequirementCandidate requirement : output.requirements()) {
                validateRequirement(requirement);
            }
        }

        @Override
        protected void validateWorkflowOutput(
                ExtractRequirementsOutput output, StepExecutionContext context) {
            Set<String> unique = new HashSet<>();
            for (RequirementCandidate requirement : output.requirements()) {
                String key = requirement.section()
                        + "|"
                        + requirement.category()
                        + "|"
                        + requirement.text().trim().toLowerCase(Locale.ROOT);
                if (!unique.add(key)) {
                    throw new IllegalArgumentException("duplicate requirement");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                ExtractRequirementsOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            if (!state.reusing() && output.requirements().isEmpty()) {
                throw insufficientData();
            }
        }
    }

    private final class AssessEligibilityExecutor
            extends AnalysisExecutor<EligibilityAssessmentOutput> {

        private AssessEligibilityExecutor() {
            super(
                    ASSESS_ELIGIBILITY,
                    ELIGIBILITY_SCHEMA,
                    EligibilityAssessmentOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            ExtractRequirementsOutput requirements = requiredEphemeral(
                    context, EXTRACT_REQUIREMENTS, ExtractRequirementsOutput.class);
            JsonNode refs = baseRefs(state);
            if (state.reusing()) {
                return localInput(
                        state,
                        refs,
                        "reuse|" + state.reusableAnalysisId(),
                        tree(new ReuseInput(INPUT_SCHEMA, state.reusableAnalysisId())));
            }
            ProfileContext profile = state.snapshot().profile();
            ApprovedProfileInput approvedProfile = new ApprovedProfileInput(
                    maskAndLimit(profile.introduction(), 2_000),
                    maskList(profile.desiredRoles(), 100),
                    maskList(profile.desiredIndustries(), 100),
                    maskList(profile.desiredLocations(), 100),
                    profile.expectedGraduationDate(),
                    state.snapshot().verifiedEvidence().stream()
                            .sorted(Comparator.comparing(VerifiedEvidence::id))
                            .limit(100)
                            .map(value -> new ApprovedEvidenceDescriptor(
                                    value.id(),
                                    value.sourceType(),
                                    value.evidenceCategory(),
                                    maskAndLimit(value.title(), 250)))
                            .toList());
            return localInput(
                    state,
                    refs,
                    stableHash(requirements) + "|" + state.snapshot().profileSnapshotHash(),
                    tree(new AssessEligibilityInput(
                            INPUT_SCHEMA,
                            requirements.requirements(),
                            approvedProfile)));
        }

        @Override
        public boolean requiresProvider(StepExecutionContext context) {
            return !state(context).reusing();
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            if (state.reusing()) {
                return localResponse(new EligibilityAssessmentOutput(
                        ELIGIBILITY_SCHEMA,
                        true,
                        state.reusableAnalysisId(),
                        Eligibility.UNKNOWN,
                        List.of(),
                        null));
            }
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
                EligibilityAssessmentOutput output, StepExecutionContext context) {
            if (!ELIGIBILITY_SCHEMA.equals(output.schemaVersion())
                    || output.eligibility() == null
                    || output.evidenceIds() == null
                    || output.evidenceIds().size() > 20
                    || output.reusable() != (output.reusableAnalysisId() != null)
                    || (!output.reusable()
                            && !hasText(output.explanation(), 2_000))) {
                throw new IllegalArgumentException("eligibility output is invalid");
            }
            if (output.reusable()
                    && (!output.evidenceIds().isEmpty()
                            || output.explanation() != null
                            || output.eligibility() != Eligibility.UNKNOWN)) {
                throw new IllegalArgumentException("reuse eligibility is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                EligibilityAssessmentOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            requireAllowedEvidenceIds(state.snapshot(), output.evidenceIds());
            rejectProbabilityLanguage(output.explanation());
        }
    }

    private final class RetrieveEvidenceExecutor
            extends AnalysisExecutor<RetrievedEvidenceOutput> {

        private RetrieveEvidenceExecutor() {
            super(
                    RETRIEVE_VERIFIED_EVIDENCE,
                    RETRIEVAL_SCHEMA,
                    RetrievedEvidenceOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            ExtractRequirementsOutput requirements = requiredEphemeral(
                    context, EXTRACT_REQUIREMENTS, ExtractRequirementsOutput.class);
            requiredEphemeral(
                    context, ASSESS_ELIGIBILITY, EligibilityAssessmentOutput.class);
            JsonNode refs = baseRefs(state);
            if (state.reusing()) {
                return localInput(
                        state,
                        refs,
                        "reuse|" + state.reusableAnalysisId(),
                        tree(new ReuseInput(INPUT_SCHEMA, state.reusableAnalysisId())));
            }
            String query = retrievalQuery(requirements.requirements());
            return localInput(
                    state,
                    refs,
                    sha256(query),
                    tree(new RetrieveEvidenceInput(
                            INPUT_SCHEMA,
                            query,
                            state.snapshot().embeddingPolicyVersion(),
                            state.snapshot().embeddingGeneration(),
                            state.snapshot().retrievalPolicyVersion())));
        }

        @Override
        public boolean requiresProvider(StepExecutionContext context) {
            return !state(context).reusing();
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            if (state.reusing()) {
                return localResponse(new RetrievedEvidenceOutput(
                        RETRIEVAL_SCHEMA,
                        true,
                        state.reusableAnalysisId(),
                        null,
                        List.of()));
            }
            RetrieveEvidenceInput input =
                    read(invocation.input().gatewayPayload(), RetrieveEvidenceInput.class);
            AiGatewayResponse embedding = invocation.embeddingGateway().embed(new EmbeddingRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    List.of(maskAndLimit(input.queryText(), 2_000)),
                    state.embeddingPolicy().dimension(),
                    EMBEDDING_TIMEOUT,
                    invocation.executionContext().run().priceVersion()));
            List<Double> vector = parseSingleVector(
                    embedding.rawJson(), state.embeddingPolicy().dimension());
            List<RetrievedVerifiedEvidence> retrieved;
            try {
                retrieved = queryPort.searchVerifiedEvidence(
                        state.snapshot().userId(),
                        state.snapshot().jobId(),
                        state.snapshot().jobVersion(),
                        state.snapshot().qualityMode(),
                        state.snapshot().contextHash(),
                        input.queryText(),
                        vector,
                        input.embeddingPolicyVersion(),
                        input.embeddingGeneration(),
                        MAX_RETRIEVED_EVIDENCE);
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            List<RetrievedEvidenceCandidate> candidates = new ArrayList<>();
            Set<UUID> selected = new HashSet<>();
            Set<UUID> allowlist = state.snapshot().verifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            for (RetrievedVerifiedEvidence item : retrieved) {
                VerifiedEvidence evidence = item.evidence();
                if (evidence == null
                        || !allowlist.contains(evidence.id())
                        || evidence.verificationStatus()
                                != EvidenceVerificationStatus.VERIFIED
                        || evidence.sourceDeleted()
                        || !selected.add(evidence.id())
                        || !hasText(evidence.title(), 250)
                        || !hasText(item.content(), 20_000)) {
                    throw domainFailure(
                            "JOB_ANALYSIS_RETRIEVAL_SCOPE_INVALID",
                            "승인된 경험 정보를 안전하게 검색하지 못했습니다.");
                }
                candidates.add(new RetrievedEvidenceCandidate(
                        evidence.id(),
                        evidence.version(),
                        evidence.evidenceHash(),
                        evidence.sourceType(),
                        evidence.evidenceCategory(),
                        maskAndLimit(evidence.title(), 250),
                        maskAndLimit(item.content(), MAX_EVIDENCE_CONTEXT_CHARACTERS),
                        maskAndLimit(item.maskedContext(), 1_500),
                        item.matchedChunkId(),
                        item.matchedDocumentId(),
                        item.distance()));
            }
            return new AiGatewayResponse(
                    write(new RetrievedEvidenceOutput(
                            RETRIEVAL_SCHEMA,
                            false,
                            null,
                            sha256(input.queryText()),
                            candidates)),
                    embedding.usages());
        }

        @Override
        public JsonNode minimalOutput(
                RetrievedEvidenceOutput output, ObjectMapper ignored) {
            // Only bounded, masked, VERIFIED evidence context is checkpointed. Whole documents,
            // unmasked source rows, provider payloads, and embedding vectors never enter it.
            return tree(output);
        }

        @Override
        protected void validateJavaRecord(
                RetrievedEvidenceOutput output, StepExecutionContext context) {
            if (!RETRIEVAL_SCHEMA.equals(output.schemaVersion())
                    || output.candidates() == null
                    || output.candidates().size() > MAX_RETRIEVED_EVIDENCE
                    || output.reusable() != (output.reusableAnalysisId() != null)
                    || (!output.reusable() && !isHash(output.queryHash()))) {
                throw new IllegalArgumentException("retrieval output is invalid");
            }
            if (output.reusable() && (!output.candidates().isEmpty() || output.queryHash() != null)) {
                throw new IllegalArgumentException("reuse retrieval is invalid");
            }
            Set<UUID> ids = new HashSet<>();
            for (RetrievedEvidenceCandidate candidate : output.candidates()) {
                if (candidate == null
                        || candidate.evidenceId() == null
                        || candidate.evidenceVersion() < 0
                        || !isHash(candidate.evidenceHash())
                        || candidate.sourceType() == null
                        || !hasText(candidate.evidenceCategory(), 80)
                        || !hasText(candidate.title(), 250)
                        || !hasText(candidate.content(), MAX_EVIDENCE_CONTEXT_CHARACTERS)
                        || !ids.add(candidate.evidenceId())
                        || (candidate.distance() != null
                                && !Double.isFinite(candidate.distance()))) {
                    throw new IllegalArgumentException("retrieved evidence is invalid");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                RetrievedEvidenceOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            requireAllowedEvidenceIds(
                    state.snapshot(),
                    output.candidates().stream()
                            .map(RetrievedEvidenceCandidate::evidenceId)
                            .toList());
        }
    }

    private final class MatchEvidenceExecutor
            extends AnalysisExecutor<MatchEvidenceOutput> {

        private MatchEvidenceExecutor() {
            super(MATCH_EVIDENCE, MATCH_SCHEMA, MatchEvidenceOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            ExtractRequirementsOutput requirements = requiredEphemeral(
                    context, EXTRACT_REQUIREMENTS, ExtractRequirementsOutput.class);
            EligibilityAssessmentOutput eligibility = requiredEphemeral(
                    context, ASSESS_ELIGIBILITY, EligibilityAssessmentOutput.class);
            RetrievedEvidenceOutput retrieved = requiredEphemeral(
                    context,
                    RETRIEVE_VERIFIED_EVIDENCE,
                    RetrievedEvidenceOutput.class);
            JsonNode refs = baseRefs(state);
            if (state.reusing()) {
                return localInput(
                        state,
                        refs,
                        "reuse|" + state.reusableAnalysisId(),
                        tree(new ReuseInput(INPUT_SCHEMA, state.reusableAnalysisId())));
            }
            return localInput(
                    state,
                    refs,
                    stableHash(requirements)
                            + "|"
                            + stableHash(eligibility)
                            + "|"
                            + retrieved.queryHash(),
                    tree(new MatchEvidenceInput(
                            INPUT_SCHEMA,
                            requirements.requirements(),
                            eligibility.eligibility(),
                            retrieved.candidates())));
        }

        @Override
        public boolean requiresProvider(StepExecutionContext context) {
            return !state(context).reusing();
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            if (state.reusing()) {
                return localResponse(new MatchEvidenceOutput(
                        MATCH_SCHEMA,
                        true,
                        state.reusableAnalysisId(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null));
            }
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
                MatchEvidenceOutput output, StepExecutionContext context) {
            if (!MATCH_SCHEMA.equals(output.schemaVersion())
                    || output.criteria() == null
                    || output.strengths() == null
                    || output.gaps() == null
                    || output.criteria().size() > MAX_REQUIREMENTS
                    || output.strengths().size() > 20
                    || output.gaps().size() > 20
                    || output.reusable() != (output.reusableAnalysisId() != null)
                    || (!output.reusable()
                            && output.analysisSummary() != null
                            && !hasText(output.analysisSummary(), 10_000))) {
                throw new IllegalArgumentException("match output is invalid");
            }
            if (output.reusable()
                    && (!output.criteria().isEmpty()
                            || !output.strengths().isEmpty()
                            || !output.gaps().isEmpty()
                            || output.analysisSummary() != null)) {
                throw new IllegalArgumentException("reuse match output is invalid");
            }
            for (MatchedCriterion criterion : output.criteria()) {
                if (criterion == null
                        || criterion.criterionIndex() < 0
                        || criterion.matchLevel() == null
                        || criterion.evidenceIds() == null
                        || criterion.evidenceIds().size() > 20
                        || !hasText(criterion.explanation(), 2_000)
                        || ((criterion.matchLevel() == MatchLevel.MISSING
                                        || criterion.matchLevel() == MatchLevel.UNKNOWN)
                                && !hasText(criterion.missingReason(), 1_000))) {
                    throw new IllegalArgumentException("matched criterion is invalid");
                }
            }
            for (StrengthDraft strength : output.strengths()) {
                if (strength == null
                        || !hasText(strength.text(), 1_000)
                        || strength.criterionIndex() < 0
                        || strength.evidenceIds() == null
                        || strength.evidenceIds().isEmpty()
                        || strength.evidenceIds().size() > 20) {
                    throw new IllegalArgumentException("strength is invalid");
                }
            }
            for (GapDraft gap : output.gaps()) {
                if (gap == null
                        || !hasText(gap.text(), 1_000)
                        || gap.criterionIndex() < 0) {
                    throw new IllegalArgumentException("gap is invalid");
                }
            }
        }

        @Override
        protected void validateWorkflowOutput(
                MatchEvidenceOutput output, StepExecutionContext context) {
            if (output.reusable()) {
                return;
            }
            ExtractRequirementsOutput requirements = requiredEphemeral(
                    context, EXTRACT_REQUIREMENTS, ExtractRequirementsOutput.class);
            if (output.criteria().size() != requirements.requirements().size()) {
                throw new IllegalArgumentException("criterion count is invalid");
            }
            Set<Integer> indexes = new HashSet<>();
            for (MatchedCriterion criterion : output.criteria()) {
                if (criterion.criterionIndex() >= requirements.requirements().size()
                        || !indexes.add(criterion.criterionIndex())) {
                    throw new IllegalArgumentException("criterion index is invalid");
                }
            }
            for (int index = 0; index < requirements.requirements().size(); index++) {
                if (!indexes.contains(index)) {
                    throw new IllegalArgumentException("criterion is missing");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                MatchEvidenceOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            if (output.reusable()) {
                return;
            }
            RetrievedEvidenceOutput retrieved = requiredEphemeral(
                    context,
                    RETRIEVE_VERIFIED_EVIDENCE,
                    RetrievedEvidenceOutput.class);
            Set<UUID> retrievedIds = retrieved.candidates().stream()
                    .map(RetrievedEvidenceCandidate::evidenceId)
                    .collect(java.util.stream.Collectors.toSet());
            List<MatchedCriterion> ordered = orderedCriteria(output.criteria());
            for (MatchedCriterion criterion : ordered) {
                requireEvidenceSubset(criterion.evidenceIds(), retrievedIds);
                if ((criterion.matchLevel() == MatchLevel.MATCHED
                                || criterion.matchLevel() == MatchLevel.PARTIAL)
                        && criterion.evidenceIds().isEmpty()) {
                    throw invalidEvidence();
                }
                if ((criterion.matchLevel() == MatchLevel.MISSING
                                || criterion.matchLevel() == MatchLevel.UNKNOWN)
                        && !criterion.evidenceIds().isEmpty()) {
                    throw invalidEvidence();
                }
                rejectProbabilityLanguage(criterion.explanation());
                rejectProbabilityLanguage(criterion.missingReason());
            }
            for (StrengthDraft strength : output.strengths()) {
                if (strength.criterionIndex() >= ordered.size()) {
                    throw invalidEvidence();
                }
                MatchedCriterion criterion = ordered.get(strength.criterionIndex());
                requireEvidenceSubset(strength.evidenceIds(), retrievedIds);
                if (!criterion.evidenceIds().containsAll(strength.evidenceIds())
                        || (criterion.matchLevel() != MatchLevel.MATCHED
                                && criterion.matchLevel() != MatchLevel.PARTIAL)) {
                    throw invalidEvidence();
                }
                rejectProbabilityLanguage(strength.text());
            }
            for (GapDraft gap : output.gaps()) {
                if (gap.criterionIndex() >= ordered.size()
                        || ordered.get(gap.criterionIndex()).matchLevel()
                                == MatchLevel.MATCHED) {
                    throw domainFailure(
                            "JOB_ANALYSIS_GAP_PROVENANCE_INVALID",
                            "부족한 점의 공고 근거를 확인하지 못했습니다.");
                }
                rejectProbabilityLanguage(gap.text());
            }
            rejectProbabilityLanguage(output.analysisSummary());
        }
    }

    private final class ScoreFitExecutor
            extends AnalysisExecutor<ScoredAnalysisOutput> {

        private ScoreFitExecutor() {
            super(SCORE_FIT, SCORE_SCHEMA, ScoredAnalysisOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            ExtractRequirementsOutput requirements = requiredEphemeral(
                    context, EXTRACT_REQUIREMENTS, ExtractRequirementsOutput.class);
            EligibilityAssessmentOutput eligibility = requiredEphemeral(
                    context, ASSESS_ELIGIBILITY, EligibilityAssessmentOutput.class);
            MatchEvidenceOutput match =
                    requiredEphemeral(context, MATCH_EVIDENCE, MatchEvidenceOutput.class);
            return localInput(
                    state,
                    baseRefs(state),
                    stableHash(requirements)
                            + "|"
                            + stableHash(eligibility)
                            + "|"
                            + stableHash(match)
                            + "|"
                            + RUBRIC_VERSION,
                    tree(new ScoreFitInput(
                            INPUT_SCHEMA,
                            requirements,
                            eligibility,
                            match,
                            RUBRIC_VERSION)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            if (state.reusing()) {
                return localResponse(ScoredAnalysisOutput.reuse(
                        state.reusableAnalysisId()));
            }
            ExtractRequirementsOutput requirements = requiredEphemeral(
                    invocation.executionContext(),
                    EXTRACT_REQUIREMENTS,
                    ExtractRequirementsOutput.class);
            EligibilityAssessmentOutput eligibility = requiredEphemeral(
                    invocation.executionContext(),
                    ASSESS_ELIGIBILITY,
                    EligibilityAssessmentOutput.class);
            MatchEvidenceOutput match = requiredEphemeral(
                    invocation.executionContext(), MATCH_EVIDENCE, MatchEvidenceOutput.class);
            List<RequirementCandidate> requirementList = requirements.requirements();
            List<MatchedCriterion> matched = orderedCriteria(match.criteria());
            List<CriterionInput> inputs = new ArrayList<>();
            for (int index = 0; index < requirementList.size(); index++) {
                RequirementCandidate requirement = requirementList.get(index);
                MatchedCriterion criterion = matched.get(index);
                inputs.add(new CriterionInput(
                        requirement.category(),
                        requirement.text(),
                        criterion.matchLevel(),
                        criterion.explanation(),
                        requirement.sourceLocation(),
                        criterion.evidenceIds()));
            }
            JobFitScoringPolicy.ScoreResult score;
            try {
                score = JobFitScoringPolicy.score(inputs);
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            List<ScoredCriterionOutput> scoredCriteria = new ArrayList<>();
            for (JobFitScoringPolicy.ScoredCriterion criterion : score.criteria()) {
                int requirementIndex = requirementIndex(
                        requirementList,
                        criterion.category(),
                        criterion.criterion(),
                        criterion.sourceLocation());
                RequirementCandidate requirement = requirementList.get(requirementIndex);
                scoredCriteria.add(new ScoredCriterionOutput(
                        requirementIndex,
                        criterion.category(),
                        criterion.criterion(),
                        criterion.weight(),
                        criterion.matchLevel(),
                        criterion.score(),
                        criterion.explanation(),
                        criterion.sourceLocation(),
                        criterion.evidenceIds(),
                        requirement.required(),
                        requirement.section()));
            }
            scoredCriteria.sort(Comparator.comparingInt(ScoredCriterionOutput::criterionIndex));
            return localResponse(new ScoredAnalysisOutput(
                    SCORE_SCHEMA,
                    false,
                    null,
                    eligibility.eligibility(),
                    eligibility.evidenceIds(),
                    scoredCriteria,
                    requirementList,
                    match.strengths(),
                    match.gaps(),
                    match.analysisSummary(),
                    score.totalScore()));
        }

        @Override
        protected void validateJavaRecord(
                ScoredAnalysisOutput output, StepExecutionContext context) {
            if (!SCORE_SCHEMA.equals(output.schemaVersion())
                    || output.eligibility() == null
                    || output.eligibilityEvidenceIds() == null
                    || output.criteria() == null
                    || output.requirements() == null
                    || output.strengths() == null
                    || output.gaps() == null
                    || output.reusable() != (output.reusableAnalysisId() != null)) {
                throw new IllegalArgumentException("score output is invalid");
            }
            if (output.reusable()) {
                if (!output.criteria().isEmpty()
                        || !output.requirements().isEmpty()
                        || output.fitScore() != null
                        || output.eligibility() != Eligibility.UNKNOWN) {
                    throw new IllegalArgumentException("reuse score is invalid");
                }
                return;
            }
            if (output.criteria().isEmpty()
                    || output.criteria().size() != output.requirements().size()
                    || output.fitScore() == null
                    || output.fitScore().scale() > 2
                    || output.fitScore().compareTo(BigDecimal.ZERO) < 0
                    || output.fitScore().compareTo(new BigDecimal("100.00")) > 0) {
                throw new IllegalArgumentException("score range is invalid");
            }
            for (ScoredCriterionOutput criterion : output.criteria()) {
                if (criterion == null
                        || criterion.criterionIndex() < 0
                        || criterion.category() == null
                        || !hasText(criterion.criterion(), 2_000)
                        || criterion.weight() == null
                        || criterion.score() == null
                        || criterion.matchLevel() == null
                        || criterion.weight().compareTo(BigDecimal.ZERO) < 0
                        || criterion.weight().compareTo(new BigDecimal("100.00")) > 0
                        || criterion.score().compareTo(BigDecimal.ZERO) < 0
                        || criterion.score().compareTo(criterion.weight()) > 0
                        || criterion.evidenceIds() == null
                        || criterion.section() == null) {
                    throw new IllegalArgumentException("scored criterion is invalid");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                ScoredAnalysisOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            if (output.reusable()) {
                return;
            }
            JobFitScoringPolicy.ScoreResult recalculated = JobFitScoringPolicy.score(
                    output.criteria().stream()
                            .sorted(Comparator.comparingInt(
                                    ScoredCriterionOutput::criterionIndex))
                            .map(value -> new CriterionInput(
                                    value.category(),
                                    value.criterion(),
                                    value.matchLevel(),
                                    value.explanation(),
                                    value.sourceLocation(),
                                    value.evidenceIds()))
                            .toList());
            if (recalculated.totalScore().compareTo(output.fitScore()) != 0) {
                throw domainFailure(
                        "JOB_ANALYSIS_SCORE_MISMATCH",
                        "적합도 점수 검증에 실패했습니다.");
            }
            for (ScoredCriterionOutput criterion : output.criteria()) {
                JobFitScoringPolicy.ScoredCriterion expected =
                        recalculated.criteria().stream()
                                .filter(value -> value.category() == criterion.category()
                                        && value.criterion().equals(criterion.criterion())
                                        && Objects.equals(
                                                value.sourceLocation(),
                                                criterion.sourceLocation()))
                                .findFirst()
                                .orElseThrow(() -> domainFailure(
                                        "JOB_ANALYSIS_SCORE_MISMATCH",
                                        "적합도 기준 점수 검증에 실패했습니다."));
                if (expected.weight().compareTo(criterion.weight()) != 0
                        || expected.score().compareTo(criterion.score()) != 0
                        || expected.matchLevel() != criterion.matchLevel()) {
                    throw domainFailure(
                            "JOB_ANALYSIS_SCORE_MISMATCH",
                            "적합도 기준 점수 검증에 실패했습니다.");
                }
            }
            requireAllowedEvidenceIds(state.snapshot(), output.eligibilityEvidenceIds());
            for (ScoredCriterionOutput criterion : output.criteria()) {
                requireAllowedEvidenceIds(state.snapshot(), criterion.evidenceIds());
            }
        }
    }

    private final class ValidateAnalysisExecutor
            extends AnalysisExecutor<ValidatedAnalysisOutput> {

        private ValidateAnalysisExecutor() {
            super(
                    VALIDATE_ANALYSIS,
                    VALIDATION_SCHEMA,
                    ValidatedAnalysisOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            ScoredAnalysisOutput scored =
                    requiredEphemeral(context, SCORE_FIT, ScoredAnalysisOutput.class);
            return localInput(
                    state,
                    baseRefs(state),
                    stableHash(scored),
                    tree(new ValidateAnalysisInput(INPUT_SCHEMA, scored)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            ScoredAnalysisOutput scored = requiredEphemeral(
                    invocation.executionContext(), SCORE_FIT, ScoredAnalysisOutput.class);
            if (!state.reusing()) {
                validateFinalAnalysis(state, scored);
            }
            return localResponse(new ValidatedAnalysisOutput(
                    VALIDATION_SCHEMA,
                    state.reusing(),
                    state.reusableAnalysisId(),
                    stableHash(scored),
                    scored.criteria().size(),
                    scored.fitScore(),
                    scored.eligibility()));
        }

        @Override
        protected void validateJavaRecord(
                ValidatedAnalysisOutput output, StepExecutionContext context) {
            if (!VALIDATION_SCHEMA.equals(output.schemaVersion())
                    || !isHash(output.analysisHash())
                    || output.criterionCount() < 0
                    || output.eligibility() == null
                    || output.reusable() != (output.reusableAnalysisId() != null)
                    || (!output.reusable() && output.fitScore() == null)) {
                throw new IllegalArgumentException("validation output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                ValidatedAnalysisOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            ScoredAnalysisOutput scored =
                    requiredEphemeral(context, SCORE_FIT, ScoredAnalysisOutput.class);
            if (!output.analysisHash().equals(stableHash(scored))
                    || output.criterionCount() != scored.criteria().size()
                    || !Objects.equals(output.fitScore(), scored.fitScore())
                    || output.eligibility() != scored.eligibility()) {
                throw domainFailure(
                        "JOB_ANALYSIS_VALIDATION_MISMATCH",
                        "공고 분석 검증 결과가 일치하지 않습니다.");
            }
        }
    }

    private final class PersistAnalysisExecutor
            extends AnalysisExecutor<PersistAnalysisOutput> {

        private PersistAnalysisExecutor() {
            super(PERSIST_ANALYSIS, PERSIST_SCHEMA, PersistAnalysisOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            AnalysisState state = state(context);
            ScoredAnalysisOutput scored =
                    requiredEphemeral(context, SCORE_FIT, ScoredAnalysisOutput.class);
            ValidatedAnalysisOutput validated = requiredEphemeral(
                    context, VALIDATE_ANALYSIS, ValidatedAnalysisOutput.class);
            if (!validated.analysisHash().equals(stableHash(scored))) {
                throw domainFailure(
                        "JOB_ANALYSIS_PERSIST_INPUT_INVALID",
                        "검증된 공고 분석 결과를 찾지 못했습니다.");
            }
            return localInput(
                    state,
                    baseRefs(state),
                    validated.analysisHash(),
                    tree(new PersistAnalysisInput(
                            INPUT_SCHEMA,
                            state.snapshot().jobId(),
                            state.snapshot().contextHash(),
                            validated.analysisHash(),
                            state.reusableAnalysisId())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalysisState state = state(invocation.executionContext());
            ScoredAnalysisOutput scored = requiredEphemeral(
                    invocation.executionContext(), SCORE_FIT, ScoredAnalysisOutput.class);
            return localResponse(new PersistAnalysisOutput(
                    PERSIST_SCHEMA,
                    state.reusing(),
                    state.reusableAnalysisId(),
                    state.snapshot().jobId(),
                    state.snapshot().contextHash(),
                    stableHash(scored)));
        }

        @Override
        public Optional<DomainApplyPlan> domainApply(
                PersistAnalysisOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            AnalysisState state = state(context);
            ScoredAnalysisOutput scored =
                    requiredEphemeral(context, SCORE_FIT, ScoredAnalysisOutput.class);
            if (!output.analysisHash().equals(stableHash(scored))) {
                throw domainFailure(
                        "JOB_ANALYSIS_PERSIST_HASH_MISMATCH",
                        "검증된 공고 분석 결과를 적용하지 못했습니다.");
            }
            persistOrAttach(state, scored);
            return Optional.empty();
        }

        @Override
        public Optional<DomainApplyPlan> domainApplyFromMinimal(
                JsonNode minimalOutput, StepExecutionContext context) {
            PersistAnalysisOutput output =
                    read(minimalOutput, PersistAnalysisOutput.class);
            AnalysisState state = state(context);
            ScoredAnalysisOutput scored =
                    requiredEphemeral(context, SCORE_FIT, ScoredAnalysisOutput.class);
            if (!output.analysisHash().equals(stableHash(scored))) {
                throw domainFailure(
                        "JOB_ANALYSIS_PERSIST_HASH_MISMATCH",
                        "검증된 공고 분석 결과를 적용하지 못했습니다.");
            }
            persistOrAttach(state, scored);
            return Optional.empty();
        }

        @Override
        protected void validateJavaRecord(
                PersistAnalysisOutput output, StepExecutionContext context) {
            if (!PERSIST_SCHEMA.equals(output.schemaVersion())
                    || output.jobId() == null
                    || !isHash(output.contextHash())
                    || !isHash(output.analysisHash())
                    || output.reusable() != (output.reusableAnalysisId() != null)) {
                throw new IllegalArgumentException("persist output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                PersistAnalysisOutput output, StepExecutionContext context) {
            AnalysisState state = state(context);
            requireReuseParity(
                    state,
                    output.reusable(),
                    output.reusableAnalysisId());
            if (!output.jobId().equals(state.snapshot().jobId())
                    || !output.contextHash().equals(state.snapshot().contextHash())) {
                throw domainFailure(
                        "JOB_ANALYSIS_PERSIST_SCOPE_INVALID",
                        "공고 분석 저장 범위를 확인하지 못했습니다.");
            }
        }
    }

    private void persistOrAttach(
            AnalysisState state, ScoredAnalysisOutput scored) {
        try {
            if (state.reusing()) {
                commandPort.attachReusable(
                        state.snapshot().userId(),
                        state.agentRunId(),
                        state.snapshot().jobId(),
                        state.reusableAnalysisId(),
                        state.snapshot().contextHash());
                return;
            }
            Optional<com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail>
                    alreadyApplied = queryPort.findReusable(
                            state.snapshot().userId(),
                            state.snapshot().jobId(),
                            state.snapshot().contextHash(),
                            state.snapshot().qualityMode());
            if (state.forceReanalyze()) {
                alreadyApplied = alreadyApplied.filter(value -> value.summary()
                        .agentRunId()
                        .equals(state.agentRunId()));
            }
            if (alreadyApplied.isPresent()) {
                commandPort.attachReusable(
                        state.snapshot().userId(),
                        state.agentRunId(),
                        state.snapshot().jobId(),
                        alreadyApplied.get().summary().id(),
                        state.snapshot().contextHash());
                return;
            }
            List<CriterionDraft> criteria = scored.criteria().stream()
                    .sorted(Comparator.comparingInt(
                            ScoredCriterionOutput::criterionIndex))
                    .map(value -> new CriterionDraft(
                            value.category(),
                            value.criterion(),
                            value.matchLevel(),
                            value.explanation(),
                            value.sourceLocation(),
                            value.evidenceIds()))
                    .toList();
            List<RequirementItem> responsibilities =
                    requirements(scored, RequirementSection.RESPONSIBILITY);
            List<RequirementItem> required =
                    requirements(scored, RequirementSection.REQUIRED_QUALIFICATION);
            List<RequirementItem> preferred =
                    requirements(scored, RequirementSection.PREFERRED_QUALIFICATION);
            Set<EvidenceUsage> usages = new LinkedHashSet<>();
            scored.eligibilityEvidenceIds().forEach(id -> usages.add(new EvidenceUsage(
                    id, JobAnalysisEvidenceUsageType.ELIGIBILITY)));
            scored.strengths().stream()
                    .flatMap(value -> value.evidenceIds().stream())
                    .forEach(id -> usages.add(new EvidenceUsage(
                            id, JobAnalysisEvidenceUsageType.STRENGTH)));
            commandPort.persist(
                    state.snapshot().userId(),
                    state.agentRunId(),
                    new PersistJobAnalysis(
                            state.snapshot().jobId(),
                            state.snapshot().jobVersion(),
                            state.snapshot().jobContentHash(),
                            state.snapshot().profileSnapshotHash(),
                            state.snapshot().evidenceSnapshotHash(),
                            state.snapshot().contextHash(),
                            state.snapshot().qualityMode(),
                            scored.eligibility(),
                            criteria,
                            responsibilities,
                            required,
                            preferred,
                            scored.strengths().stream()
                                    .map(StrengthDraft::text)
                                    .toList(),
                            scored.gaps().stream().map(GapDraft::text).toList(),
                            List.copyOf(usages),
                            scored.analysisSummary()));
        } catch (BusinessException exception) {
            throw mapBusiness(exception);
        }
    }

    private List<RequirementItem> requirements(
            ScoredAnalysisOutput scored, RequirementSection section) {
        return scored.requirements().stream()
                .filter(value -> value.section() == section)
                .map(value -> new RequirementItem(
                        value.category(),
                        value.text(),
                        value.required(),
                        value.sourceLocation()))
                .toList();
    }

    private void validateFinalAnalysis(
            AnalysisState state, ScoredAnalysisOutput scored) {
        if (scored.criteria().isEmpty()) {
            throw insufficientData();
        }
        if (scored.criteria().size() != scored.requirements().size()) {
            throw domainFailure(
                    "JOB_ANALYSIS_CRITERIA_INVALID",
                    "공고 분석 기준을 확인하지 못했습니다.");
        }
        BigDecimal weights = scored.criteria().stream()
                .map(ScoredCriterionOutput::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scores = scored.criteria().stream()
                .map(ScoredCriterionOutput::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weights.compareTo(new BigDecimal("100.00")) != 0
                || scores.compareTo(scored.fitScore()) != 0) {
            throw domainFailure(
                    "JOB_ANALYSIS_SCORE_MISMATCH",
                    "적합도 점수 검증에 실패했습니다.");
        }
        requireAllowedEvidenceIds(state.snapshot(), scored.eligibilityEvidenceIds());
        for (ScoredCriterionOutput criterion : scored.criteria()) {
            requireAllowedEvidenceIds(state.snapshot(), criterion.evidenceIds());
            if (criterion.score().compareTo(criterion.weight()) > 0) {
                throw domainFailure(
                        "JOB_ANALYSIS_SCORE_RANGE_INVALID",
                        "적합도 기준 점수 범위를 확인하지 못했습니다.");
            }
            rejectProbabilityLanguage(criterion.explanation());
        }
        for (StrengthDraft strength : scored.strengths()) {
            if (strength.evidenceIds().isEmpty()) {
                throw invalidEvidence();
            }
            requireAllowedEvidenceIds(state.snapshot(), strength.evidenceIds());
            rejectProbabilityLanguage(strength.text());
        }
        scored.gaps().forEach(value -> rejectProbabilityLanguage(value.text()));
        rejectProbabilityLanguage(scored.analysisSummary());
    }

    private void validateSnapshot(
            JsonNode input,
            StepExecutionContext context,
            JobAnalysisSnapshot snapshot) {
        if (!context.run().userId().equals(snapshot.userId())
                || !context.run().resourceId().equals(snapshot.jobId())
                || snapshot.jobVersion() != input.path("jobVersion").asLong(-1)
                || !input.path("jobContentHash").asText().equals(snapshot.jobContentHash())
                || !input.path("profileSnapshotHash")
                        .asText()
                        .equals(snapshot.profileSnapshotHash())
                || !input.path("evidenceSnapshotHash")
                        .asText()
                        .equals(snapshot.evidenceSnapshotHash())
                || !input.path("contextHash").asText().equals(snapshot.contextHash())
                || !input.path("rubricVersion").asText().equals(snapshot.rubricVersion())
                || !input.path("workflowVersion").asText().equals(snapshot.workflowVersion())
                || input.path("embeddingPolicyVersion").asLong(-1)
                        != snapshot.embeddingPolicyVersion()
                || input.path("embeddingGeneration").asInt(-1)
                        != snapshot.embeddingGeneration()
                || !input.path("retrievalPolicyVersion")
                        .asText()
                        .equals(snapshot.retrievalPolicyVersion())
                || !RUBRIC_VERSION.equals(snapshot.rubricVersion())
                || !RETRIEVAL_POLICY_VERSION.equals(snapshot.retrievalPolicyVersion())
                || snapshot.descriptionText() == null
                || snapshot.descriptionText().isBlank()) {
            throw domainFailure(
                    "JOB_ANALYSIS_SNAPSHOT_STALE",
                    "공고 또는 프로필 정보가 변경되었습니다.");
        }
    }

    private JobAnalysisSnapshot loadSnapshot(
            UUID userId,
            UUID jobId,
            long jobVersion,
            AiQualityMode qualityMode,
            String contextHash) {
        try {
            return queryPort.loadSnapshot(
                    userId, jobId, jobVersion, qualityMode, contextHash);
        } catch (BusinessException exception) {
            throw mapBusiness(exception);
        }
    }

    private AiExecutionException mapBusiness(BusinessException exception) {
        if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            return ownerFailure();
        }
        if (exception.errorCode() == ErrorCode.INSUFFICIENT_JOB_DATA) {
            return insufficientData();
        }
        if (exception.errorCode() == ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED) {
            return AiExecutionException.nonRetryable(
                    FailureKind.BUDGET,
                    exception.errorCode().code(),
                    exception.errorCode().defaultMessage());
        }
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                exception.errorCode().code(),
                exception.errorCode().defaultMessage());
    }

    private List<Double> parseSingleVector(String rawJson, int dimension) {
        try {
            EmbeddingValuesOutput output =
                    objectMapper.readValue(rawJson, EmbeddingValuesOutput.class);
            if (output.vectors() == null || output.vectors().size() != 1) {
                throw new IllegalArgumentException("embedding batch is invalid");
            }
            List<Double> vector = output.vectors().getFirst();
            if (vector == null
                    || vector.size() != dimension
                    || vector.stream().anyMatch(
                            value -> value == null || !Double.isFinite(value))) {
                throw new IllegalArgumentException("embedding vector is invalid");
            }
            return List.copyOf(vector);
        } catch (RuntimeException exception) {
            throw AiExecutionException.retryable(
                    FailureKind.STRUCTURED_OUTPUT,
                    "JOB_ANALYSIS_EMBEDDING_OUTPUT_INVALID",
                    "경험 정보 검색 결과를 확인하지 못했습니다.");
        } catch (Exception exception) {
            throw AiExecutionException.retryable(
                    FailureKind.STRUCTURED_OUTPUT,
                    "JOB_ANALYSIS_EMBEDDING_OUTPUT_INVALID",
                    "경험 정보 검색 결과를 확인하지 못했습니다.");
        }
    }

    private String retrievalQuery(List<RequirementCandidate> requirements) {
        String query = requirements.stream()
                .sorted(Comparator.comparing(RequirementCandidate::category)
                        .thenComparing(RequirementCandidate::text))
                .map(value -> value.category().name() + ": " + value.text())
                .collect(java.util.stream.Collectors.joining("\n"));
        if (query.isBlank()) {
            throw insufficientData();
        }
        return maskAndLimit(query, 2_000);
    }

    private int requirementIndex(
            List<RequirementCandidate> requirements,
            FitCriterionCategory category,
            String criterion,
            String sourceLocation) {
        for (int index = 0; index < requirements.size(); index++) {
            RequirementCandidate requirement = requirements.get(index);
            if (requirement.category() == category
                    && requirement.text().equals(criterion)
                    && Objects.equals(requirement.sourceLocation(), sourceLocation)) {
                return index;
            }
        }
        throw domainFailure(
                "JOB_ANALYSIS_CRITERION_MAPPING_INVALID",
                "공고 분석 기준을 연결하지 못했습니다.");
    }

    private List<MatchedCriterion> orderedCriteria(List<MatchedCriterion> criteria) {
        return criteria.stream()
                .sorted(Comparator.comparingInt(MatchedCriterion::criterionIndex))
                .toList();
    }

    private void validateRequirement(RequirementCandidate requirement) {
        if (requirement == null
                || requirement.section() == null
                || requirement.category() == null
                || !hasText(requirement.text(), 2_000)
                || (requirement.sourceLocation() != null
                        && !hasText(requirement.sourceLocation(), 500))) {
            throw new IllegalArgumentException("requirement is invalid");
        }
        if (requirement.section() == RequirementSection.REQUIRED_QUALIFICATION
                && !requirement.required()) {
            throw new IllegalArgumentException("required qualification must be required");
        }
        if (requirement.section() == RequirementSection.PREFERRED_QUALIFICATION
                && requirement.required()) {
            throw new IllegalArgumentException("preferred qualification cannot be required");
        }
        if (requirement.section() == RequirementSection.RESPONSIBILITY
                && requirement.category()
                        != FitCriterionCategory.CORE_RESPONSIBILITY_OR_SKILL) {
            throw new IllegalArgumentException("responsibility category is invalid");
        }
        if (requirement.section() == RequirementSection.PREFERRED_QUALIFICATION
                && requirement.category()
                        != FitCriterionCategory.PREFERRED_QUALIFICATION) {
            throw new IllegalArgumentException("preferred qualification category is invalid");
        }
    }

    private void requireAllowedEvidenceIds(
            JobAnalysisSnapshot snapshot, List<UUID> evidenceIds) {
        Set<UUID> allowlist = snapshot.verifiedEvidence().stream()
                .filter(value -> value.verificationStatus()
                                == EvidenceVerificationStatus.VERIFIED
                        && !value.sourceDeleted())
                .map(VerifiedEvidence::id)
                .collect(java.util.stream.Collectors.toSet());
        requireEvidenceSubset(evidenceIds, allowlist);
    }

    private void requireEvidenceSubset(
            List<UUID> evidenceIds, Set<UUID> allowlist) {
        if (evidenceIds == null
                || evidenceIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(evidenceIds).size() != evidenceIds.size()
                || !allowlist.containsAll(evidenceIds)) {
            throw invalidEvidence();
        }
    }

    private void requireReuseParity(
            AnalysisState state, boolean reusable, UUID reusableAnalysisId) {
        if (reusable != state.reusing()
                || !Objects.equals(reusableAnalysisId, state.reusableAnalysisId())) {
            throw domainFailure(
                    "JOB_ANALYSIS_REUSE_MISMATCH",
                    "공고 분석 재사용 조건이 일치하지 않습니다.");
        }
    }

    private void rejectProbabilityLanguage(String value) {
        if (value == null) {
            return;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (PROBABILITY_PHRASES.stream().anyMatch(normalized::contains)) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.SAFETY,
                    "JOB_ANALYSIS_UNSAFE_COPY",
                    "적합도 설명의 안전한 표현을 확인하지 못했습니다.");
        }
    }

    private <T> T requiredEphemeral(
            StepExecutionContext context, String stepKey, Class<T> type) {
        Object value = context.ephemeralOutputs().get(stepKey);
        if (!type.isInstance(value)) {
            throw configurationFailure();
        }
        return type.cast(value);
    }

    private <T> T read(JsonNode value, Class<T> type) {
        try {
            return objectMapper.treeToValue(value, type);
        } catch (Exception exception) {
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

    private String maskAndLimit(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String masked = EMAIL.matcher(value).replaceAll("[EMAIL]");
        masked = PHONE.matcher(masked).replaceAll("[PHONE]");
        masked = SECRET.matcher(masked).replaceAll("$1=[SECRET]");
        return masked.length() <= maximum ? masked : masked.substring(0, maximum);
    }

    private List<String> maskList(List<String> values, int maximum) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> maskAndLimit(value, maximum))
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean hasText(String value, int maximum) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximum;
    }

    private boolean isHash(String value) {
        return value != null && HASH.matcher(value).matches();
    }

    private String nullSafe(Object value) {
        return value == null ? "-" : value.toString();
    }

    private Set<String> recordFields(Class<?> recordType) {
        Set<String> fields = new HashSet<>();
        for (var component : recordType.getRecordComponents()) {
            fields.add(component.getName());
        }
        return Set.copyOf(fields);
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "JOB_ANALYSIS_WORKFLOW_CONFIGURATION_INVALID",
                "공고 분석 실행 구성이 준비되지 않았습니다.");
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                ErrorCode.RESOURCE_NOT_FOUND.code(),
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    private AiExecutionException insufficientData() {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                ErrorCode.INSUFFICIENT_JOB_DATA.code(),
                ErrorCode.INSUFFICIENT_JOB_DATA.defaultMessage());
    }

    private AiExecutionException invalidEvidence() {
        return domainFailure(
                "JOB_ANALYSIS_EVIDENCE_INVALID",
                "공고 분석에 사용할 승인된 경험 정보를 확인하지 못했습니다.");
    }

    private AiExecutionException domainFailure(String code, String message) {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION, code, message);
    }

    private record AnalysisState(
            JobAnalysisSnapshot snapshot,
            UUID agentRunId,
            boolean forceReanalyze,
            UUID reusableAnalysisId,
            EmbeddingPolicySnapshot embeddingPolicy) {

        boolean reusing() {
            return !forceReanalyze && reusableAnalysisId != null;
        }
    }

    public enum RequirementSection {
        RESPONSIBILITY,
        REQUIRED_QUALIFICATION,
        PREFERRED_QUALIFICATION
    }

    public record ReuseInput(String schemaVersion, UUID reusableAnalysisId) {}

    public record BuildSnapshotInput(
            String schemaVersion,
            UUID jobId,
            long jobVersion,
            String contextHash,
            UUID reusableAnalysisId) {}

    public record UntrustedJobPosting(
            String companyName,
            String title,
            String positionName,
            String roleCategory,
            String employmentType,
            String location,
            String descriptionText,
            boolean truncated) {}

    public record ExtractRequirementsInput(
            String schemaVersion, UntrustedJobPosting untrustedJobPosting) {}

    public record ApprovedEvidenceDescriptor(
            UUID id,
            EvidenceSourceType sourceType,
            String evidenceCategory,
            String title) {}

    public record ApprovedProfileInput(
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            java.time.LocalDate expectedGraduationDate,
            List<ApprovedEvidenceDescriptor> verifiedEvidence) {
        public ApprovedProfileInput {
            desiredRoles = desiredRoles == null ? List.of() : List.copyOf(desiredRoles);
            desiredIndustries =
                    desiredIndustries == null ? List.of() : List.copyOf(desiredIndustries);
            desiredLocations =
                    desiredLocations == null ? List.of() : List.copyOf(desiredLocations);
            verifiedEvidence =
                    verifiedEvidence == null ? List.of() : List.copyOf(verifiedEvidence);
        }
    }

    public record AssessEligibilityInput(
            String schemaVersion,
            List<RequirementCandidate> requirements,
            ApprovedProfileInput approvedProfile) {}

    public record RetrieveEvidenceInput(
            String schemaVersion,
            String queryText,
            long embeddingPolicyVersion,
            int embeddingGeneration,
            String retrievalPolicyVersion) {}

    public record MatchEvidenceInput(
            String schemaVersion,
            List<RequirementCandidate> requirements,
            Eligibility eligibility,
            List<RetrievedEvidenceCandidate> verifiedEvidenceCandidates) {}

    public record ScoreFitInput(
            String schemaVersion,
            ExtractRequirementsOutput requirements,
            EligibilityAssessmentOutput eligibility,
            MatchEvidenceOutput matches,
            String rubricVersion) {}

    public record ValidateAnalysisInput(
            String schemaVersion, ScoredAnalysisOutput analysis) {}

    public record PersistAnalysisInput(
            String schemaVersion,
            UUID jobId,
            String contextHash,
            String analysisHash,
            UUID reusableAnalysisId) {}

    public record EvidenceSnapshotReference(
            UUID id,
            long version,
            String evidenceHash,
            String evidenceCategory,
            EvidenceSourceType sourceType) {}

    public record BuildSnapshotOutput(
            String schemaVersion,
            UUID jobId,
            long jobVersion,
            String contextHash,
            String jobContentHash,
            String profileSnapshotHash,
            String evidenceSnapshotHash,
            String rubricVersion,
            String workflowVersion,
            AiQualityMode qualityMode,
            long embeddingPolicyVersion,
            int embeddingDimension,
            int embeddingGeneration,
            String retrievalPolicyVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            List<EvidenceSnapshotReference> verifiedEvidence) {
        public BuildSnapshotOutput {
            verifiedEvidence =
                    verifiedEvidence == null ? List.of() : List.copyOf(verifiedEvidence);
        }
    }

    public record RequirementCandidate(
            RequirementSection section,
            FitCriterionCategory category,
            String text,
            boolean required,
            String sourceLocation) {}

    public record ExtractRequirementsOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            List<RequirementCandidate> requirements) {
        public ExtractRequirementsOutput {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }
    }

    public record EligibilityAssessmentOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            Eligibility eligibility,
            List<UUID> evidenceIds,
            String explanation) {
        public EligibilityAssessmentOutput {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record RetrievedEvidenceCandidate(
            UUID evidenceId,
            long evidenceVersion,
            String evidenceHash,
            EvidenceSourceType sourceType,
            String evidenceCategory,
            String title,
            String content,
            String maskedCandidateContext,
            UUID matchedChunkId,
            UUID matchedDocumentId,
            Double distance) {}

    public record RetrievedEvidenceOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            String queryHash,
            List<RetrievedEvidenceCandidate> candidates) {
        public RetrievedEvidenceOutput {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record MatchedCriterion(
            int criterionIndex,
            MatchLevel matchLevel,
            List<UUID> evidenceIds,
            String explanation,
            String missingReason) {
        public MatchedCriterion {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record StrengthDraft(
            String text, int criterionIndex, List<UUID> evidenceIds) {
        public StrengthDraft {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record GapDraft(String text, int criterionIndex) {}

    public record MatchEvidenceOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            List<MatchedCriterion> criteria,
            List<StrengthDraft> strengths,
            List<GapDraft> gaps,
            String analysisSummary) {
        public MatchEvidenceOutput {
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
        }
    }

    public record ScoredCriterionOutput(
            int criterionIndex,
            FitCriterionCategory category,
            String criterion,
            BigDecimal weight,
            MatchLevel matchLevel,
            BigDecimal score,
            String explanation,
            String sourceLocation,
            List<UUID> evidenceIds,
            boolean required,
            RequirementSection section) {
        public ScoredCriterionOutput {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record ScoredAnalysisOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            Eligibility eligibility,
            List<UUID> eligibilityEvidenceIds,
            List<ScoredCriterionOutput> criteria,
            List<RequirementCandidate> requirements,
            List<StrengthDraft> strengths,
            List<GapDraft> gaps,
            String analysisSummary,
            BigDecimal fitScore) {
        public ScoredAnalysisOutput {
            eligibilityEvidenceIds = eligibilityEvidenceIds == null
                    ? List.of()
                    : List.copyOf(eligibilityEvidenceIds);
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
        }

        static ScoredAnalysisOutput reuse(UUID reusableAnalysisId) {
            return new ScoredAnalysisOutput(
                    SCORE_SCHEMA,
                    true,
                    reusableAnalysisId,
                    Eligibility.UNKNOWN,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null);
        }
    }

    public record ValidatedAnalysisOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            String analysisHash,
            int criterionCount,
            BigDecimal fitScore,
            Eligibility eligibility) {}

    public record PersistAnalysisOutput(
            String schemaVersion,
            boolean reusable,
            UUID reusableAnalysisId,
            UUID jobId,
            String contextHash,
            String analysisHash) {}

    public record EmbeddingValuesOutput(List<List<Double>> vectors) {
        public EmbeddingValuesOutput {
            vectors = vectors == null ? List.of() : List.copyOf(vectors);
        }
    }
}
