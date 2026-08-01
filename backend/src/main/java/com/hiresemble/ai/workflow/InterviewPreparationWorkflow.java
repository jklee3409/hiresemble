package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.WebSearchGateway.SearchRequest;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.interview.application.model.InterviewModels.EvidenceContext;
import com.hiresemble.interview.application.model.InterviewModels.GeneratedQuestion;
import com.hiresemble.interview.application.model.InterviewModels.PreparationContext;
import com.hiresemble.interview.application.model.InterviewModels.StructuredProfileContext;
import com.hiresemble.interview.application.port.InterviewWorkflowCommandPort;
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.research.application.model.ResearchModels.ResearchResult;
import com.hiresemble.research.application.model.ResearchModels.SourceCandidate;
import com.hiresemble.research.application.model.ResearchModels.TopicPlan;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
import com.hiresemble.research.domain.SourceCoverage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Fixed ten-step public-source research and interview-question workflow. */
public final class InterviewPreparationWorkflow {

    public static final String VALIDATE_PREREQUISITES = "VALIDATE_PREREQUISITES";
    public static final String BUILD_PUBLIC_SEARCH_PLAN = "BUILD_PUBLIC_SEARCH_PLAN";
    public static final String SEARCH_OFFICIAL_SOURCES = "SEARCH_OFFICIAL_SOURCES";
    public static final String SEARCH_INTERVIEW_SOURCES = "SEARCH_INTERVIEW_SOURCES";
    public static final String DEDUPE_CLASSIFY_SOURCES = "DEDUPE_CLASSIFY_SOURCES";
    public static final String ASSESS_SOURCE_COVERAGE = "ASSESS_SOURCE_COVERAGE";
    public static final String BUILD_QUESTION_CONTEXT = "BUILD_QUESTION_CONTEXT";
    public static final String GENERATE_QUESTIONS = "GENERATE_QUESTIONS";
    public static final String VALIDATE_QUESTION_PROVENANCE =
            "VALIDATE_QUESTION_PROVENANCE";
    public static final String PERSIST_RESEARCH_AND_QUESTION_SET =
            "PERSIST_RESEARCH_AND_QUESTION_SET";

    private static final String INPUT_SCHEMA = "interview-preparation-input-v1";
    private static final String SEARCH_SCHEMA = "web-search-results-v1";
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(60);

    private final InterviewWorkflowQueryPort queryPort;
    private final InterviewWorkflowCommandPort commandPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InterviewPreparationWorkflow(
            InterviewWorkflowQueryPort queryPort,
            InterviewWorkflowCommandPort commandPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.INTERVIEW_PREPARATION,
                CanonicalWorkflowDefinitions.INTERVIEW_PREPARATION_VERSION,
                List.of(
                        step(VALIDATE_PREREQUISITES, new ValidateExecutor()),
                        step(BUILD_PUBLIC_SEARCH_PLAN, new SearchPlanExecutor()),
                        step(SEARCH_OFFICIAL_SOURCES, new OfficialSearchExecutor()),
                        step(SEARCH_INTERVIEW_SOURCES, new InterviewSearchExecutor()),
                        step(DEDUPE_CLASSIFY_SOURCES, new DedupeExecutor()),
                        step(ASSESS_SOURCE_COVERAGE, new CoverageExecutor()),
                        step(BUILD_QUESTION_CONTEXT, new QuestionContextExecutor()),
                        step(GENERATE_QUESTIONS, new GenerateQuestionsExecutor()),
                        step(
                                VALIDATE_QUESTION_PROVENANCE,
                                new ValidateProvenanceExecutor()),
                        step(
                                PERSIST_RESEARCH_AND_QUESTION_SET,
                                new PersistExecutor())));
    }

    private ExecutableWorkflowStep step(String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class PreparationExecutor<T> implements WorkflowStepExecutor<T> {
        private final String stepKey;
        private final Class<T> outputType;
        private final String outputSchema;
        private final Set<String> fields;

        private PreparationExecutor(String stepKey, Class<T> outputType) {
            this.stepKey = stepKey;
            this.outputType = outputType;
            this.outputSchema = outputSchema(stepKey);
            this.fields = recordFields(outputType);
        }

        @Override
        public Contract<T> outputContract() {
            return contract(null);
        }

        @Override
        public Contract<T> outputContract(StepExecutionContext context) {
            return contract(context);
        }

        private Contract<T> contract(StepExecutionContext context) {
            return new Contract<>(
                    outputType,
                    outputSchema,
                    node -> {
                        if (node == null
                                || !node.isObject()
                                || !fields.equals(Set.copyOf(node.propertyNames()))) {
                            throw new IllegalArgumentException(
                                    "interview preparation output schema is invalid");
                        }
                    },
                    output -> validate(output, context),
                    output -> {},
                    output -> {});
        }

        protected void validate(T output, StepExecutionContext context) {}

        protected PreparationState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType() != WorkflowType.INTERVIEW_PREPARATION
                    || !CanonicalWorkflowDefinitions.INTERVIEW_PREPARATION_VERSION.equals(
                            context.run().workflowVersion())
                    || !"QUESTION_SET".equals(context.run().resourceType())
                    || context.run().resourceId() == null) {
                throw configurationFailure();
            }
            try {
                JsonNode input = context.run().inputReferenceSnapshot();
                UUID jobId = UUID.fromString(input.path("jobId").asText());
                UUID coverLetterId =
                        UUID.fromString(input.path("coverLetterId").asText());
                UUID researchRunId =
                        UUID.fromString(input.path("researchRunId").asText());
                UUID questionSetId =
                        UUID.fromString(input.path("questionSetId").asText());
                String contextHash = input.path("contextHash").asText();
                ResearchQuality researchQuality = ResearchQuality.valueOf(
                        input.path("researchQuality").asText());
                int questionCount = input.path("questionCount").asInt(-1);
                List<InterviewQuestionType> questionTypes =
                        questionTypes(input.path("questionTypes"));
                if (!questionSetId.equals(context.run().resourceId())
                        || questionCount < 1
                        || questionCount > 20
                        || !contextHash.matches("[0-9a-f]{64}")) {
                    throw ownerFailure();
                }
                PreparationContext preparation = queryPort.loadPreparationContext(
                        context.run().userId(), jobId, coverLetterId, contextHash);
                var research =
                        queryPort.researchRun(context.run().userId(), researchRunId);
                if (!research.agentRunId().equals(context.run().id())
                        || research.researchQuality() != researchQuality) {
                    throw ownerFailure();
                }
                return new PreparationState(
                        preparation,
                        researchRunId,
                        questionSetId,
                        researchQuality,
                        questionTypes,
                        questionCount,
                        contextHash);
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            } catch (RuntimeException exception) {
                throw ownerFailure();
            }
        }

        protected StepInput localInput(
                PreparationState state,
                JsonNode refs,
                String suffix,
                JsonNode payload) {
            return new StepInput(
                    null,
                    refs,
                    stepKey + "|" + state.contextHash() + "|" + suffix,
                    payload,
                    null,
                    0);
        }

        protected ObjectNode baseRefs(PreparationState state) {
            return objectMapper.createObjectNode()
                    .put("researchRunId", state.researchRunId().toString())
                    .put("questionSetId", state.questionSetId().toString())
                    .put("contextHash", state.contextHash())
                    .put("questionCount", state.questionCount());
        }

        protected AiGatewayResponse local(Object value) {
            return new AiGatewayResponse(write(value), java.util.List.of());
        }

        protected JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
        }
    }

    private final class ValidateExecutor
            extends PreparationExecutor<ValidatePrerequisitesOutput> {

        private ValidateExecutor() {
            super(VALIDATE_PREREQUISITES, ValidatePrerequisitesOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            return localInput(
                    state,
                    baseRefs(state),
                    "validate",
                    tree(new ValidatePrerequisitesInput(
                            INPUT_SCHEMA,
                            state.preparation().jobId(),
                            state.preparation().jobAnalysisId(),
                            state.preparation().coverLetterId(),
                            state.preparation().coverAnswers().size(),
                            state.preparation().evidence().size(),
                            state.contextHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PreparationState state = state(invocation.executionContext());
            return local(new ValidatePrerequisitesOutput(
                    outputSchema(VALIDATE_PREREQUISITES),
                    state.preparation().jobId(),
                    state.preparation().jobAnalysisId(),
                    state.preparation().coverLetterId(),
                    state.researchRunId(),
                    state.questionSetId(),
                    state.preparation().coverAnswers().size(),
                    state.preparation().evidence().size(),
                    state.contextHash()));
        }

        @Override
        protected void validate(
                ValidatePrerequisitesOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), VALIDATE_PREREQUISITES);
            if (output.jobId() == null
                    || output.analysisId() == null
                    || output.coverLetterId() == null
                    || output.researchRunId() == null
                    || output.questionSetId() == null
                    || output.currentCoverAnswerCount() < 1
                    || output.verifiedEvidenceCount() < 0
                    || !isHash(output.contextHash())) {
                throw new IllegalArgumentException("preparation prerequisites are invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(
                ValidatePrerequisitesOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public DomainStepCompletion completeFresh(
                ValidatePrerequisitesOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            PreparationState state = state(context);
            commandPort.markPreparationRunning(
                    context.run().userId(), state.researchRunId());
            return new DomainStepCompletion(minimalOutput, java.util.Optional.empty(), null);
        }
    }

    private final class SearchPlanExecutor
            extends PreparationExecutor<SearchPlanOutput> {

        private SearchPlanExecutor() {
            super(BUILD_PUBLIC_SEARCH_PLAN, SearchPlanOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            return localInput(
                    state,
                    baseRefs(state),
                    "public-plan",
                    tree(new SearchPlanInput(
                            INPUT_SCHEMA,
                            bounded(state.preparation().companyName(), 200),
                            bounded(publicRole(state.preparation()), 300),
                            state.researchQuality().name())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PreparationState state = state(invocation.executionContext());
            List<PlannedSearchQuery> queries = buildSearchPlan(state);
            return local(new SearchPlanOutput(
                    outputSchema(BUILD_PUBLIC_SEARCH_PLAN),
                    state.researchQuality().name(),
                    queries,
                    stableHash(queries)));
        }

        @Override
        protected void validate(SearchPlanOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), BUILD_PUBLIC_SEARCH_PLAN);
            PreparationState state = state(context);
            int max = state.researchQuality().maxQueries();
            if (!state.researchQuality().name().equals(output.researchQuality())
                    || output.queries().size() < 2
                    || output.queries().size() > max
                    || output.queries().stream().anyMatch(query -> query == null
                            || query.topic() == null
                            || query.purpose() == null
                            || !validText(query.queryText(), 500))
                    || output.queries().stream().map(PlannedSearchQuery::queryText).distinct().count()
                            != output.queries().size()
                    || !isHash(output.planHash())) {
                throw new IllegalArgumentException("public search plan is invalid");
            }
            assertPublicQueries(state, output.queries());
        }

        @Override
        public JsonNode minimalOutput(SearchPlanOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("researchQuality", output.researchQuality())
                    .put("queryCount", output.queries().size())
                    .put("planHash", output.planHash());
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private abstract class SearchExecutor extends PreparationExecutor<SearchBatchOutput> {
        private final SearchPurpose purpose;

        private SearchExecutor(String stepKey, SearchPurpose purpose) {
            super(stepKey, SearchBatchOutput.class);
            this.purpose = purpose;
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            SearchPlanOutput plan =
                    requiredEphemeral(context, BUILD_PUBLIC_SEARCH_PLAN, SearchPlanOutput.class);
            List<PlannedSearchQuery> selected = plan.queries().stream()
                    .filter(query -> query.purpose() == purpose)
                    .toList();
            var refs = baseRefs(state);
            refs.put("planHash", plan.planHash());
            refs.put("queryCount", selected.size());
            return localInput(
                    state,
                    refs,
                    purpose.name() + "|" + plan.planHash(),
                    tree(new SearchBatchInput(
                            INPUT_SCHEMA,
                            purpose,
                            selected,
                            state.researchQuality().maxResultsPerQuery())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            SearchBatchInput input =
                    read(invocation.input().gatewayPayload(), SearchBatchInput.class);
            List<String> queries =
                    input.queries().stream().map(PlannedSearchQuery::queryText).toList();
            try {
                return invocation.webSearchGateway().search(new SearchRequest(
                        invocation.modelRoute().providerKey(),
                        invocation.modelRoute().productKey(),
                        queries,
                        state(invocation.executionContext()).researchQuality().name(),
                        input.maxResultsPerQuery(),
                        SEARCH_TIMEOUT,
                        purpose.name(),
                        invocation.executionContext().run().priceVersion()));
            } catch (AiExecutionException failure) {
                return new AiGatewayResponse(
                        write(new SearchBatchOutput(
                                SEARCH_SCHEMA,
                                purpose,
                                false,
                                failure.safeCode(),
                                List.of())),
                        failure.incurredUsages());
            }
        }

        @Override
        protected void validate(SearchBatchOutput output, StepExecutionContext context) {
            SearchPlanOutput plan =
                    requiredEphemeral(context, BUILD_PUBLIC_SEARCH_PLAN, SearchPlanOutput.class);
            Set<String> allowed = plan.queries().stream()
                    .filter(query -> query.purpose() == purpose)
                    .map(PlannedSearchQuery::queryText)
                    .collect(Collectors.toSet());
            if (!SEARCH_SCHEMA.equals(output.schemaVersion())
                    || output.purpose() != purpose
                    || (output.callSucceeded() && output.failureCode() != null)
                    || (!output.callSucceeded()
                            && (output.failureCode() == null
                                    || !output.results().isEmpty()))
                    || output.results().size()
                            > allowed.size()
                                    * state(context).researchQuality().maxResultsPerQuery()
                    || output.results().stream().anyMatch(hit -> hit == null
                            || !validText(hit.query(), 500)
                            || !validText(hit.sourceUrl(), 2000)
                            || !validNullableText(hit.title(), 500)
                            || !validNullableText(hit.snippet(), 2000)
                            || hit.providerRank() < 1)) {
                throw new IllegalArgumentException("web search result is invalid");
            }
            if (output.results().stream().anyMatch(hit -> !allowed.contains(hit.query()))) {
                throw new IllegalArgumentException("search response contains an unknown query");
            }
        }

        @Override
        public JsonNode minimalOutput(SearchBatchOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", outputSchema(stepKeyFor(purpose)))
                    .put("purpose", purpose.name())
                    .put("callSucceeded", output.callSucceeded())
                    .put("failureCode", output.failureCode())
                    .put("resultCount", output.results().size())
                    .put("resultHash", stableHash(output.results()));
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class OfficialSearchExecutor extends SearchExecutor {
        private OfficialSearchExecutor() {
            super(SEARCH_OFFICIAL_SOURCES, SearchPurpose.OFFICIAL);
        }
    }

    private final class InterviewSearchExecutor extends SearchExecutor {
        private InterviewSearchExecutor() {
            super(SEARCH_INTERVIEW_SOURCES, SearchPurpose.INTERVIEW);
        }
    }

    private final class DedupeExecutor
            extends PreparationExecutor<ClassifiedSourcesOutput> {

        private DedupeExecutor() {
            super(DEDUPE_CLASSIFY_SOURCES, ClassifiedSourcesOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            SearchBatchOutput official =
                    requiredEphemeral(context, SEARCH_OFFICIAL_SOURCES, SearchBatchOutput.class);
            SearchBatchOutput interviews =
                    requiredEphemeral(context, SEARCH_INTERVIEW_SOURCES, SearchBatchOutput.class);
            var refs = baseRefs(state);
            refs.put("officialCount", official.results().size());
            refs.put("interviewCount", interviews.results().size());
            return localInput(
                    state,
                    refs,
                    stableHash(List.of(official, interviews)),
                    tree(new ClassifySourcesInput(
                            INPUT_SCHEMA,
                            official.callSucceeded(),
                            interviews.callSucceeded(),
                            official.results().size() + interviews.results().size())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            StepExecutionContext context = invocation.executionContext();
            PreparationState state = state(context);
            SearchPlanOutput plan =
                    requiredEphemeral(context, BUILD_PUBLIC_SEARCH_PLAN, SearchPlanOutput.class);
            SearchBatchOutput official =
                    requiredEphemeral(context, SEARCH_OFFICIAL_SOURCES, SearchBatchOutput.class);
            SearchBatchOutput interviews =
                    requiredEphemeral(context, SEARCH_INTERVIEW_SOURCES, SearchBatchOutput.class);
            List<ClassifiedSource> sources =
                    classifyAndDedupe(context.run().id(), plan, official, interviews);
            return local(new ClassifiedSourcesOutput(
                    outputSchema(DEDUPE_CLASSIFY_SOURCES),
                    official.callSucceeded(),
                    interviews.callSucceeded(),
                    sources,
                    stableHash(sources)));
        }

        @Override
        protected void validate(
                ClassifiedSourcesOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), DEDUPE_CLASSIFY_SOURCES);
            if (output.sources().size() > 32
                    || output.sources().stream().map(ClassifiedSource::canonicalUrl).distinct().count()
                            != output.sources().size()
                    || output.sources().stream().anyMatch(source -> source == null
                            || source.id() == null
                            || source.topic() == null
                            || source.topics() == null
                            || source.topics().isEmpty()
                            || source.topics().size() > ResearchTopic.values().length
                            || !source.topics().contains(source.topic())
                            || new HashSet<>(source.topics()).size() != source.topics().size()
                            || source.sourceType() == null
                            || !validText(source.canonicalUrl(), 2000)
                            || !validNullableText(source.title(), 500)
                            || !validNullableText(source.snippet(), 2000)
                            || !validText(source.reliabilityNotice(), 500)
                            || source.providerRank() < 1
                            || !isHash(source.contentHash()))
                    || !isHash(output.sourcesHash())) {
                throw new IllegalArgumentException("classified source output is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(
                ClassifiedSourcesOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("officialCallSucceeded", output.officialCallSucceeded())
                    .put("interviewCallSucceeded", output.interviewCallSucceeded())
                    .put("sourceCount", output.sources().size())
                    .put("sourcesHash", output.sourcesHash());
            var ids = result.putArray("sourceIds");
            output.sources().forEach(source -> ids.add(source.id().toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class CoverageExecutor
            extends PreparationExecutor<CoverageOutput> {

        private CoverageExecutor() {
            super(ASSESS_SOURCE_COVERAGE, CoverageOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            ClassifiedSourcesOutput sources = requiredEphemeral(
                    context, DEDUPE_CLASSIFY_SOURCES, ClassifiedSourcesOutput.class);
            var refs = baseRefs(state);
            refs.put("sourcesHash", sources.sourcesHash());
            refs.put("sourceCount", sources.sources().size());
            return localInput(
                    state,
                    refs,
                    sources.sourcesHash(),
                    tree(new CoverageInput(
                            INPUT_SCHEMA,
                            sources.officialCallSucceeded(),
                            sources.interviewCallSucceeded(),
                            sources.sources().size())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            StepExecutionContext context = invocation.executionContext();
            ClassifiedSourcesOutput classified = requiredEphemeral(
                    context, DEDUPE_CLASSIFY_SOURCES, ClassifiedSourcesOutput.class);
            if (!classified.officialCallSucceeded()
                    && !classified.interviewCallSucceeded()) {
                throw AiExecutionException.retryable(
                        FailureKind.PROVIDER_5XX,
                        "INTERVIEW_RESEARCH_PROVIDER_UNAVAILABLE",
                        "면접 조사 출처를 일시적으로 불러오지 못했습니다.");
            }
            CoverageAssessment assessment = assessCoverage(classified.sources());
            return local(new CoverageOutput(
                    outputSchema(ASSESS_SOURCE_COVERAGE),
                    assessment.coverage(),
                    assessment.missingTopics(),
                    assessment.summary(),
                    classified.sources().size(),
                    classified.sourcesHash()));
        }

        @Override
        protected void validate(CoverageOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), ASSESS_SOURCE_COVERAGE);
            if (output.coverage() == null
                    || output.missingCoverageTopics().size() > 20
                    || output.missingCoverageTopics().stream()
                            .anyMatch(value -> !validText(value, 200))
                    || !validText(output.summary(), 10000)
                    || output.usableSourceCount() < 0
                    || !isHash(output.sourcesHash())
                    || (output.coverage() == SourceCoverage.NONE
                            && output.usableSourceCount() != 0)
                    || (output.coverage() != SourceCoverage.NONE
                            && output.usableSourceCount() == 0)) {
                throw new IllegalArgumentException("source coverage is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(CoverageOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("coverage", output.coverage().name())
                    .put("usableSourceCount", output.usableSourceCount())
                    .put("sourcesHash", output.sourcesHash());
            var missing = result.putArray("missingCoverageTopics");
            output.missingCoverageTopics().forEach(missing::add);
            result.put("summaryHash", sha256(output.summary()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class QuestionContextExecutor
            extends PreparationExecutor<QuestionContextOutput> {

        private QuestionContextExecutor() {
            super(BUILD_QUESTION_CONTEXT, QuestionContextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            CoverageOutput coverage =
                    requiredEphemeral(context, ASSESS_SOURCE_COVERAGE, CoverageOutput.class);
            ClassifiedSourcesOutput sources = requiredEphemeral(
                    context, DEDUPE_CLASSIFY_SOURCES, ClassifiedSourcesOutput.class);
            var refs = baseRefs(state);
            refs.put("coverage", coverage.coverage().name());
            refs.put("sourcesHash", sources.sourcesHash());
            return localInput(
                    state,
                    refs,
                    coverage.coverage() + "|" + sources.sourcesHash(),
                    tree(new QuestionContextInput(
                            INPUT_SCHEMA,
                            state.preparation().jobId(),
                            state.preparation().coverLetterId(),
                            coverage.coverage(),
                            state.questionCount())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            StepExecutionContext context = invocation.executionContext();
            PreparationState state = state(context);
            CoverageOutput coverage =
                    requiredEphemeral(context, ASSESS_SOURCE_COVERAGE, CoverageOutput.class);
            ClassifiedSourcesOutput sources = requiredEphemeral(
                    context, DEDUPE_CLASSIFY_SOURCES, ClassifiedSourcesOutput.class);
            QuestionContextPayload payload = questionContext(state, coverage, sources.sources());
            return local(new QuestionContextOutput(
                    outputSchema(BUILD_QUESTION_CONTEXT),
                    payload,
                    stableHash(payload),
                    state.preparation().evidence().size(),
                    sources.sources().size()));
        }

        @Override
        protected void validate(
                QuestionContextOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), BUILD_QUESTION_CONTEXT);
            if (output.context() == null
                    || !isHash(output.questionContextHash())
                    || output.verifiedEvidenceCount() < 0
                    || output.sourceCount() < 0) {
                throw new IllegalArgumentException("question context is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(
                QuestionContextOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("questionContextHash", output.questionContextHash())
                    .put("verifiedEvidenceCount", output.verifiedEvidenceCount())
                    .put("sourceCount", output.sourceCount());
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class GenerateQuestionsExecutor
            extends PreparationExecutor<GeneratedQuestionsOutput> {

        private GenerateQuestionsExecutor() {
            super(GENERATE_QUESTIONS, GeneratedQuestionsOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            QuestionContextOutput questionContext = requiredEphemeral(
                    context, BUILD_QUESTION_CONTEXT, QuestionContextOutput.class);
            var refs = baseRefs(state);
            refs.put("questionContextHash", questionContext.questionContextHash());
            return localInput(
                    state,
                    refs,
                    questionContext.questionContextHash(),
                    tree(new GenerateQuestionsInput(
                            INPUT_SCHEMA,
                            state.questionCount(),
                            state.questionTypes(),
                            questionContext.context())));
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
        protected void validate(
                GeneratedQuestionsOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), GENERATE_QUESTIONS);
            PreparationState state = state(context);
            QuestionContextOutput questionContext = requiredEphemeral(
                    context, BUILD_QUESTION_CONTEXT, QuestionContextOutput.class);
            Set<UUID> allowedEvidence =
                    questionContext.context().evidence().stream()
                            .map(EvidenceInput::id)
                            .collect(Collectors.toSet());
            Set<UUID> allowedSources =
                    questionContext.context().sources().stream()
                            .map(SourceInput::id)
                            .collect(Collectors.toSet());
            if (output.questions().size() != state.questionCount()
                    || output.questions().size() > 20
                    || output.questions().stream()
                                    .map(GeneratedQuestionDraft::questionOrder)
                                    .collect(Collectors.toSet())
                            .size()
                            != output.questions().size()) {
                throw new IllegalArgumentException("generated question count is invalid");
            }
            for (GeneratedQuestionDraft question : output.questions()) {
                if (question.questionOrder() < 1
                        || question.questionOrder() > state.questionCount()
                        || question.questionType() == null
                        || (question.questionType() != InterviewQuestionType.FOLLOW_UP
                                && !state.questionTypes().contains(question.questionType()))
                        || !validText(question.questionText(), 2000)
                        || !validNullableText(question.intent(), 2000)
                        || !validNullableText(question.answerGuide(), 10000)
                        || !validTexts(question.evaluationPoints(), 20, 500)
                        || !validTexts(question.followUpQuestions(), 10, 2000)
                        || question.evidenceIds().size() > 20
                        || question.sourceIds().size() > 50
                        || !allowedEvidence.containsAll(question.evidenceIds())
                        || !allowedSources.containsAll(question.sourceIds())
                        || new HashSet<>(question.evidenceIds()).size()
                                != question.evidenceIds().size()
                        || new HashSet<>(question.sourceIds()).size()
                                != question.sourceIds().size()
                        || question.sourceBased() != !question.sourceIds().isEmpty()) {
                    throw new IllegalArgumentException(
                            "generated question provenance is invalid");
                }
            }
        }

        @Override
        public JsonNode minimalOutput(
                GeneratedQuestionsOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("questionCount", output.questions().size())
                    .put("questionsHash", stableHash(output.questions()));
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class ValidateProvenanceExecutor
            extends PreparationExecutor<ValidatedQuestionsOutput> {

        private ValidateProvenanceExecutor() {
            super(VALIDATE_QUESTION_PROVENANCE, ValidatedQuestionsOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            GeneratedQuestionsOutput generated = requiredEphemeral(
                    context, GENERATE_QUESTIONS, GeneratedQuestionsOutput.class);
            var refs = baseRefs(state);
            refs.put("questionsHash", stableHash(generated.questions()));
            return localInput(
                    state,
                    refs,
                    stableHash(generated.questions()),
                    tree(new ValidateProvenanceInput(
                            INPUT_SCHEMA,
                            generated.questions().size(),
                            stableHash(generated.questions()))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            StepExecutionContext context = invocation.executionContext();
            PreparationState state = state(context);
            GeneratedQuestionsOutput generated = requiredEphemeral(
                    context, GENERATE_QUESTIONS, GeneratedQuestionsOutput.class);
            List<GeneratedQuestion> questions = generated.questions().stream()
                    .sorted(Comparator.comparingInt(GeneratedQuestionDraft::questionOrder))
                    .map(question -> new GeneratedQuestion(
                            deterministicId(
                                    state.questionSetId(),
                                    "question-" + question.questionOrder()),
                            question.questionOrder(),
                            question.questionType(),
                            question.questionText().strip(),
                            stripOrNull(question.intent()),
                            question.evaluationPoints(),
                            stripOrNull(question.answerGuide()),
                            question.followUpQuestions(),
                            question.evidenceIds(),
                            question.sourceIds()))
                    .toList();
            return local(new ValidatedQuestionsOutput(
                    outputSchema(VALIDATE_QUESTION_PROVENANCE),
                    questions,
                    stableHash(questions),
                    questions.stream().mapToInt(value -> value.evidenceIds().size()).sum(),
                    questions.stream().mapToInt(value -> value.sourceIds().size()).sum()));
        }

        @Override
        protected void validate(
                ValidatedQuestionsOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), VALIDATE_QUESTION_PROVENANCE);
            PreparationState state = state(context);
            if (output.questions().size() != state.questionCount()
                    || !isHash(output.questionsHash())
                    || output.evidenceLinkCount() < 0
                    || output.sourceLinkCount() < 0
                    || output.questions().stream().anyMatch(question ->
                            question.sourceBased() != !question.sourceIds().isEmpty())) {
                throw new IllegalArgumentException("validated question output is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(
                ValidatedQuestionsOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("questionCount", output.questions().size())
                    .put("questionsHash", output.questionsHash())
                    .put("evidenceLinkCount", output.evidenceLinkCount())
                    .put("sourceLinkCount", output.sourceLinkCount());
            var ids = result.putArray("questionIds");
            output.questions().forEach(question -> ids.add(question.id().toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class PersistExecutor extends PreparationExecutor<PersistOutput> {

        private PersistExecutor() {
            super(PERSIST_RESEARCH_AND_QUESTION_SET, PersistOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            PreparationState state = state(context);
            CoverageOutput coverage =
                    requiredEphemeral(context, ASSESS_SOURCE_COVERAGE, CoverageOutput.class);
            ValidatedQuestionsOutput questions = requiredEphemeral(
                    context, VALIDATE_QUESTION_PROVENANCE, ValidatedQuestionsOutput.class);
            var refs = baseRefs(state);
            refs.put("coverage", coverage.coverage().name());
            refs.put("questionsHash", questions.questionsHash());
            return localInput(
                    state,
                    refs,
                    coverage.sourcesHash() + "|" + questions.questionsHash(),
                    tree(new PersistInput(
                            INPUT_SCHEMA,
                            state.researchRunId(),
                            state.questionSetId(),
                            coverage.coverage(),
                            questions.questions().size())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PreparationState state = state(invocation.executionContext());
            CoverageOutput coverage = requiredEphemeral(
                    invocation.executionContext(),
                    ASSESS_SOURCE_COVERAGE,
                    CoverageOutput.class);
            ValidatedQuestionsOutput questions = requiredEphemeral(
                    invocation.executionContext(),
                    VALIDATE_QUESTION_PROVENANCE,
                    ValidatedQuestionsOutput.class);
            return local(new PersistOutput(
                    outputSchema(PERSIST_RESEARCH_AND_QUESTION_SET),
                    state.researchRunId(),
                    state.questionSetId(),
                    coverage.coverage(),
                    questions.questions().stream().map(GeneratedQuestion::id).toList(),
                    coverage.sourcesHash(),
                    questions.questionsHash()));
        }

        @Override
        protected void validate(PersistOutput output, StepExecutionContext context) {
            requireSchema(output.schemaVersion(), PERSIST_RESEARCH_AND_QUESTION_SET);
            PreparationState state = state(context);
            if (!state.researchRunId().equals(output.researchRunId())
                    || !state.questionSetId().equals(output.questionSetId())
                    || output.coverage() == null
                    || output.questionIds().size() != state.questionCount()
                    || !isHash(output.sourcesHash())
                    || !isHash(output.questionsHash())) {
                throw new IllegalArgumentException("persist request is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(PersistOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public DomainStepCompletion completeFresh(
                PersistOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            PreparationState state = state(context);
            SearchPlanOutput plan =
                    requiredEphemeral(context, BUILD_PUBLIC_SEARCH_PLAN, SearchPlanOutput.class);
            ClassifiedSourcesOutput classified = requiredEphemeral(
                    context, DEDUPE_CLASSIFY_SOURCES, ClassifiedSourcesOutput.class);
            CoverageOutput coverage =
                    requiredEphemeral(context, ASSESS_SOURCE_COVERAGE, CoverageOutput.class);
            ValidatedQuestionsOutput questions = requiredEphemeral(
                    context, VALIDATE_QUESTION_PROVENANCE, ValidatedQuestionsOutput.class);
            List<TopicPlan> topics = plan.queries().stream()
                    .map(query -> new TopicPlan(
                            deterministicId(
                                    state.researchRunId(),
                                    "topic-" + query.queryOrder()),
                            query.topic(),
                            query.queryText(),
                            query.queryOrder()))
                    .toList();
            List<SourceCandidate> sources = classified.sources().stream()
                    .map(source -> new SourceCandidate(
                            source.id(),
                            source.topic(),
                            source.topics(),
                            source.canonicalUrl(),
                            source.title(),
                            source.sourceType(),
                            source.publishedAt(),
                            source.retrievedAt(),
                            source.snippet(),
                            source.reliabilityNotice(),
                            source.providerRank(),
                            source.contentHash()))
                    .toList();
            commandPort.persistPreparation(
                    context.run().userId(),
                    context.run().id(),
                    state.researchRunId(),
                    state.questionSetId(),
                    state.questionCount(),
                    new ResearchResult(
                            topics,
                            sources,
                            coverage.coverage(),
                            coverage.missingCoverageTopics(),
                            coverage.summary()),
                    questions.questions());
            return new DomainStepCompletion(minimalOutput, java.util.Optional.empty(), null);
        }
    }

    private List<PlannedSearchQuery> buildSearchPlan(PreparationState state) {
        String company = publicCompany(state.preparation());
        String role = publicRole(state.preparation());
        List<PlannedSearchQuery> values = new ArrayList<>();
        values.add(new PlannedSearchQuery(
                1,
                ResearchTopic.COMPANY,
                SearchPurpose.OFFICIAL,
                bounded(company + " 채용 공식 회사 정보", 500)));
        values.add(new PlannedSearchQuery(
                2,
                ResearchTopic.ROLE_TECHNICAL,
                SearchPurpose.INTERVIEW,
                bounded(company + " " + role + " 면접 후기", 500)));
        if (state.researchQuality() == ResearchQuality.ADVANCED) {
            values.add(new PlannedSearchQuery(
                    3,
                    ResearchTopic.INTERVIEW_PROCESS,
                    SearchPurpose.OFFICIAL,
                    bounded(company + " 채용 면접 전형 공식", 500)));
            values.add(new PlannedSearchQuery(
                    4,
                    ResearchTopic.INTERVIEW_PROCESS,
                    SearchPurpose.INTERVIEW,
                    bounded(company + " " + role + " 면접 과정 경험", 500)));
        }
        return List.copyOf(values);
    }

    private void assertPublicQueries(
            PreparationState state, List<PlannedSearchQuery> queries) {
        List<String> forbidden = new ArrayList<>();
        state.preparation().coverAnswers().forEach(answer -> forbidden.add(answer.answerText()));
        state.preparation().evidence().forEach(evidence -> forbidden.add(evidence.content()));
        String introduction = state.preparation().profile().introduction();
        if (introduction != null) forbidden.add(introduction);
        for (PlannedSearchQuery query : queries) {
            String text = query.queryText().toLowerCase(Locale.ROOT);
            if (text.contains("@")
                    || text.matches(".*\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b.*")
                    || forbidden.stream()
                            .filter(value -> value != null && value.length() >= 8)
                            .anyMatch(value -> text.contains(value.toLowerCase(Locale.ROOT)))) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.SAFETY,
                        "INTERVIEW_SEARCH_QUERY_PRIVATE_DATA",
                        "면접 조사 검색어에 개인 정보를 포함할 수 없습니다.");
            }
        }
    }

    private List<ClassifiedSource> classifyAndDedupe(
            UUID runId,
            SearchPlanOutput plan,
            SearchBatchOutput official,
            SearchBatchOutput interviews) {
        Map<String, PlannedSearchQuery> queryMap = plan.queries().stream()
                .collect(Collectors.toMap(
                        PlannedSearchQuery::queryText, value -> value));
        LinkedHashMap<String, ClassifiedSource> deduped = new LinkedHashMap<>();
        addHits(runId, official, queryMap, deduped);
        addHits(runId, interviews, queryMap, deduped);
        return deduped.values().stream()
                .sorted(Comparator.comparingInt(ClassifiedSource::providerRank)
                        .thenComparing(ClassifiedSource::canonicalUrl))
                .limit(32)
                .toList();
    }

    private void addHits(
            UUID runId,
            SearchBatchOutput batch,
            Map<String, PlannedSearchQuery> queryMap,
            Map<String, ClassifiedSource> deduped) {
        for (SearchHit hit : batch.results()) {
            PlannedSearchQuery query = queryMap.get(hit.query());
            if (query == null) continue;
            String canonical = canonicalUrl(hit.sourceUrl());
            if (canonical == null) continue;
            ClassifiedSource existing = deduped.get(canonical);
            if (existing != null) {
                LinkedHashSet<ResearchTopic> topics =
                        new LinkedHashSet<>(existing.topics());
                topics.add(query.topic());
                deduped.put(canonical, new ClassifiedSource(
                        existing.id(),
                        existing.topic(),
                        List.copyOf(topics),
                        existing.canonicalUrl(),
                        existing.title(),
                        existing.sourceType(),
                        existing.publishedAt(),
                        existing.retrievedAt(),
                        existing.snippet(),
                        existing.reliabilityNotice(),
                        Math.min(existing.providerRank(), hit.providerRank()),
                        existing.contentHash()));
                continue;
            }
            ResearchSourceType type = classify(canonical, batch.purpose());
            String title = bounded(stripOrNull(hit.title()), 500);
            String snippet = bounded(stripOrNull(hit.snippet()), 2000);
            Instant publishedAt = parseInstant(hit.publishedAt());
            Instant retrievedAt = clock.instant();
            deduped.put(canonical, new ClassifiedSource(
                    deterministicId(runId, canonical),
                    query.topic(),
                    List.of(query.topic()),
                    canonical,
                    title,
                    type,
                    publishedAt,
                    retrievedAt,
                    snippet,
                    reliabilityNotice(type),
                    hit.providerRank(),
                    sha256(canonical + "|" + nullable(title) + "|" + nullable(snippet))));
        }
    }

    private ResearchSourceType classify(String url, SearchPurpose purpose) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("news") || lower.contains("press")) {
            return ResearchSourceType.NEWS;
        }
        if (lower.contains("tech") || lower.contains("engineering") || lower.contains("blog")) {
            return ResearchSourceType.TECH_BLOG;
        }
        if (purpose == SearchPurpose.OFFICIAL) {
            return ResearchSourceType.OFFICIAL;
        }
        if (lower.contains("reddit")
                || lower.contains("blind")
                || lower.contains("community")
                || lower.contains("cafe")) {
            return ResearchSourceType.COMMUNITY;
        }
        return ResearchSourceType.INTERVIEW_REVIEW;
    }

    private CoverageAssessment assessCoverage(List<ClassifiedSource> sources) {
        Set<String> domains = sources.stream()
                .map(source -> URI.create(source.canonicalUrl()).getHost())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<ResearchSourceType> categories =
                sources.stream().map(ClassifiedSource::sourceType).collect(Collectors.toSet());
        boolean authoritative = sources.stream().anyMatch(source ->
                source.sourceType() == ResearchSourceType.OFFICIAL
                        || source.sourceType() == ResearchSourceType.TECH_BLOG);
        SourceCoverage coverage;
        if (sources.isEmpty()) {
            coverage = SourceCoverage.NONE;
        } else if (sources.size() >= 3
                && authoritative
                && domains.size() >= 2
                && categories.size() >= 2) {
            coverage = SourceCoverage.SUFFICIENT;
        } else {
            coverage = SourceCoverage.LIMITED;
        }
        Set<ResearchTopic> present =
                sources.stream()
                        .flatMap(source -> source.topics().stream())
                        .collect(Collectors.toSet());
        List<String> missing = Arrays.stream(ResearchTopic.values())
                .filter(topic -> !present.contains(topic))
                .map(this::topicLabel)
                .toList();
        long authoritativeCount = sources.stream()
                .filter(source -> source.sourceType() == ResearchSourceType.OFFICIAL
                        || source.sourceType() == ResearchSourceType.TECH_BLOG)
                .count();
        String summary = "공개 출처 %d개를 수집했으며 공식·기술 출처는 %d개입니다. 출처 coverage는 %s입니다."
                .formatted(sources.size(), authoritativeCount, coverage.name());
        return new CoverageAssessment(coverage, missing, summary);
    }

    private QuestionContextPayload questionContext(
            PreparationState state,
            CoverageOutput coverage,
            List<ClassifiedSource> sources) {
        PreparationContext context = state.preparation();
        StructuredProfileContext profile = context.profile();
        FinalEducationInput education = profile.finalEducation() == null
                ? null
                : new FinalEducationInput(
                        profile.finalEducation().id(),
                        bounded(profile.finalEducation().schoolName(), 200),
                        bounded(profile.finalEducation().major(), 200),
                        bounded(profile.finalEducation().degree(), 100),
                        profile.finalEducation().educationLevel().name(),
                        profile.finalEducation().educationStatus().name());
        return new QuestionContextPayload(
                bounded(context.companyName(), 200),
                bounded(context.positionName(), 300),
                bounded(context.jobTitle(), 300),
                bounded(context.roleCategory(), 100),
                bounded(context.jobDescription(), 20000),
                bounded(context.analysisSummary(), 10000),
                bounded(profile.introduction(), 2000),
                profile.desiredRoles(),
                profile.desiredIndustries(),
                profile.desiredLocations(),
                education,
                context.evidence().stream().map(this::evidenceInput).toList(),
                context.coverAnswers().stream()
                        .map(answer -> new CoverAnswerInput(
                                answer.answerVersionId(),
                                bounded(answer.questionText(), 2000),
                                bounded(answer.answerText(), 20000)))
                        .toList(),
                coverage.coverage(),
                coverage.missingCoverageTopics(),
                sources.stream()
                        .map(source -> new SourceInput(
                                source.id(),
                                source.topic(),
                                source.sourceType(),
                                source.canonicalUrl(),
                                source.title(),
                                source.snippet(),
                                source.reliabilityNotice()))
                        .toList());
    }

    private EvidenceInput evidenceInput(EvidenceContext evidence) {
        return new EvidenceInput(
                evidence.id(),
                evidence.sourceType().name(),
                bounded(evidence.category(), 80),
                bounded(evidence.title(), 250),
                bounded(evidence.content(), 4000),
                evidence.verificationStatus().name());
    }

    private String publicCompany(PreparationContext context) {
        String company = stripOrNull(context.companyName());
        return company == null ? "채용 기업" : bounded(company, 200);
    }

    private String publicRole(PreparationContext context) {
        String role = stripOrNull(context.positionName());
        if (role == null) role = stripOrNull(context.roleCategory());
        if (role == null) role = stripOrNull(context.jobTitle());
        return role == null ? "지원 직무" : bounded(role, 300);
    }

    private String topicLabel(ResearchTopic topic) {
        return switch (topic) {
            case COMPANY -> "회사 정보";
            case INTERVIEW_PROCESS -> "채용·면접 과정";
            case ROLE_TECHNICAL -> "유사 직무 면접 정보";
        };
    }

    private String reliabilityNotice(ResearchSourceType type) {
        return switch (type) {
            case OFFICIAL -> "기업이 공개한 공식 정보이며 최신 여부를 원문에서 확인하세요.";
            case TECH_BLOG -> "기술 조직이 공개한 자료이며 실제 채용 절차와 다를 수 있습니다.";
            case NEWS -> "보도 자료이므로 기업 공식 안내와 함께 확인하세요.";
            case INTERVIEW_REVIEW -> "개인 면접 경험으로 시기·직무에 따라 다를 수 있습니다.";
            case COMMUNITY -> "익명 커뮤니티 정보이며 사실로 단정하지 마세요.";
            case OTHER -> "출처 성격을 확인하고 참고 정보로만 사용하세요.";
        };
    }

    private String canonicalUrl(String value) {
        try {
            URI input = URI.create(value.strip());
            String scheme = input.getScheme() == null
                    ? null
                    : input.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || input.getHost() == null) {
                return null;
            }
            String query = input.getQuery();
            if (query != null) {
                query = Arrays.stream(query.split("&"))
                        .filter(part -> {
                            String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                            return !key.startsWith("utm_")
                                    && !Set.of("fbclid", "gclid", "ref").contains(key);
                        })
                        .sorted()
                        .collect(Collectors.joining("&"));
                if (query.isBlank()) query = null;
            }
            String path = input.getPath();
            if (path == null || path.isBlank()) path = "/";
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(
                            scheme,
                            input.getUserInfo(),
                            input.getHost().toLowerCase(Locale.ROOT),
                            input.getPort(),
                            path,
                            query,
                            null)
                    .toASCIIString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<InterviewQuestionType> questionTypes(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > 7) {
            throw new IllegalArgumentException("question types are invalid");
        }
        List<InterviewQuestionType> values = new ArrayList<>();
        node.forEach(value -> values.add(InterviewQuestionType.valueOf(value.asText())));
        if (values.contains(InterviewQuestionType.FOLLOW_UP)
                || new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("question types are invalid");
        }
        return List.copyOf(values);
    }

    private <T> T requiredEphemeral(
            StepExecutionContext context, String stepKey, Class<T> type) {
        Object value = context.ephemeral(stepKey);
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
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UUID deterministicId(UUID namespace, String value) {
        return UUID.nameUUIDFromBytes(
                (namespace + "|" + value).getBytes(StandardCharsets.UTF_8));
    }

    private Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String outputSchema(String stepKey) {
        if (PERSIST_RESEARCH_AND_QUESTION_SET.equals(stepKey)) {
            return "interview-persist-preparation-output-v1";
        }
        return "interview-"
                + stepKey.toLowerCase(Locale.ROOT).replace('_', '-')
                + "-output-v1";
    }

    private String stepKeyFor(SearchPurpose purpose) {
        return purpose == SearchPurpose.OFFICIAL
                ? SEARCH_OFFICIAL_SOURCES
                : SEARCH_INTERVIEW_SOURCES;
    }

    private void requireSchema(String value, String stepKey) {
        if (!outputSchema(stepKey).equals(value)) {
            throw new IllegalArgumentException("schema version is invalid");
        }
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private boolean validText(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    private boolean validNullableText(String value, int max) {
        return value == null || (!value.isBlank() && value.length() <= max);
    }

    private boolean validTexts(List<String> values, int maxItems, int maxLength) {
        return values != null
                && values.size() <= maxItems
                && values.stream().allMatch(value -> validText(value, maxLength));
    }

    private String bounded(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String stripOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private AiExecutionException mapBusiness(BusinessException exception) {
        if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            return ownerFailure();
        }
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                exception.errorCode().code(),
                exception.errorCode().defaultMessage());
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                ErrorCode.RESOURCE_NOT_FOUND.code(),
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_INTERVIEW_PREPARATION_NOT_CONFIGURED",
                "면접 준비 AI 실행 구성이 준비되지 않았습니다.");
    }

    private record PreparationState(
            PreparationContext preparation,
            UUID researchRunId,
            UUID questionSetId,
            ResearchQuality researchQuality,
            List<InterviewQuestionType> questionTypes,
            int questionCount,
            String contextHash) {
        private PreparationState {
            questionTypes = List.copyOf(questionTypes);
        }
    }

    public enum SearchPurpose {
        OFFICIAL,
        INTERVIEW
    }

    public record ValidatePrerequisitesInput(
            String schemaVersion,
            UUID jobId,
            UUID analysisId,
            UUID coverLetterId,
            int currentCoverAnswerCount,
            int verifiedEvidenceCount,
            String contextHash) {}

    public record ValidatePrerequisitesOutput(
            String schemaVersion,
            UUID jobId,
            UUID analysisId,
            UUID coverLetterId,
            UUID researchRunId,
            UUID questionSetId,
            int currentCoverAnswerCount,
            int verifiedEvidenceCount,
            String contextHash) {}

    public record SearchPlanInput(
            String schemaVersion,
            String companyName,
            String publicRole,
            String researchQuality) {}

    public record PlannedSearchQuery(
            int queryOrder,
            ResearchTopic topic,
            SearchPurpose purpose,
            String queryText) {}

    public record SearchPlanOutput(
            String schemaVersion,
            String researchQuality,
            List<PlannedSearchQuery> queries,
            String planHash) {
        public SearchPlanOutput {
            queries = copy(queries);
        }
    }

    public record SearchBatchInput(
            String schemaVersion,
            SearchPurpose purpose,
            List<PlannedSearchQuery> queries,
            int maxResultsPerQuery) {
        public SearchBatchInput {
            queries = copy(queries);
        }
    }

    public record SearchHit(
            String query,
            String sourceUrl,
            String title,
            String snippet,
            String publishedAt,
            int providerRank) {}

    public record SearchBatchOutput(
            String schemaVersion,
            SearchPurpose purpose,
            boolean callSucceeded,
            String failureCode,
            List<SearchHit> results) {
        public SearchBatchOutput {
            results = copy(results);
        }
    }

    public record ClassifySourcesInput(
            String schemaVersion,
            boolean officialCallSucceeded,
            boolean interviewCallSucceeded,
            int rawResultCount) {}

    public record ClassifiedSource(
            UUID id,
            ResearchTopic topic,
            List<ResearchTopic> topics,
            String canonicalUrl,
            String title,
            ResearchSourceType sourceType,
            Instant publishedAt,
            Instant retrievedAt,
            String snippet,
            String reliabilityNotice,
            int providerRank,
            String contentHash) {
        public ClassifiedSource {
            topics = copy(topics);
        }
    }

    public record ClassifiedSourcesOutput(
            String schemaVersion,
            boolean officialCallSucceeded,
            boolean interviewCallSucceeded,
            List<ClassifiedSource> sources,
            String sourcesHash) {
        public ClassifiedSourcesOutput {
            sources = copy(sources);
        }
    }

    public record CoverageInput(
            String schemaVersion,
            boolean officialCallSucceeded,
            boolean interviewCallSucceeded,
            int usableSourceCount) {}

    public record CoverageOutput(
            String schemaVersion,
            SourceCoverage coverage,
            List<String> missingCoverageTopics,
            String summary,
            int usableSourceCount,
            String sourcesHash) {
        public CoverageOutput {
            missingCoverageTopics = copy(missingCoverageTopics);
        }
    }

    private record CoverageAssessment(
            SourceCoverage coverage, List<String> missingTopics, String summary) {}

    public record QuestionContextInput(
            String schemaVersion,
            UUID jobId,
            UUID coverLetterId,
            SourceCoverage coverage,
            int questionCount) {}

    public record FinalEducationInput(
            UUID id,
            String schoolName,
            String major,
            String degree,
            String educationLevel,
            String educationStatus) {}

    public record EvidenceInput(
            UUID id,
            String sourceType,
            String category,
            String title,
            String content,
            String verificationStatus) {}

    public record CoverAnswerInput(
            UUID answerVersionId, String questionText, String answerText) {}

    public record SourceInput(
            UUID id,
            ResearchTopic topic,
            ResearchSourceType sourceType,
            String sourceUrl,
            String title,
            String snippet,
            String reliabilityNotice) {}

    public record QuestionContextPayload(
            String companyName,
            String positionName,
            String jobTitle,
            String roleCategory,
            String jobDescription,
            String analysisSummary,
            String profileIntroduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            FinalEducationInput finalEducation,
            List<EvidenceInput> evidence,
            List<CoverAnswerInput> coverLetterAnswers,
            SourceCoverage sourceCoverage,
            List<String> missingCoverageTopics,
            List<SourceInput> sources) {
        public QuestionContextPayload {
            desiredRoles = copy(desiredRoles);
            desiredIndustries = copy(desiredIndustries);
            desiredLocations = copy(desiredLocations);
            evidence = copy(evidence);
            coverLetterAnswers = copy(coverLetterAnswers);
            missingCoverageTopics = copy(missingCoverageTopics);
            sources = copy(sources);
        }
    }

    public record QuestionContextOutput(
            String schemaVersion,
            QuestionContextPayload context,
            String questionContextHash,
            int verifiedEvidenceCount,
            int sourceCount) {}

    public record GenerateQuestionsInput(
            String schemaVersion,
            int questionCount,
            List<InterviewQuestionType> requestedQuestionTypes,
            QuestionContextPayload context) {
        public GenerateQuestionsInput {
            requestedQuestionTypes = copy(requestedQuestionTypes);
        }
    }

    public record GeneratedQuestionDraft(
            int questionOrder,
            InterviewQuestionType questionType,
            String questionText,
            String intent,
            List<String> evaluationPoints,
            String answerGuide,
            List<String> followUpQuestions,
            List<UUID> evidenceIds,
            List<UUID> sourceIds,
            boolean sourceBased) {
        public GeneratedQuestionDraft {
            evaluationPoints = copy(evaluationPoints);
            followUpQuestions = copy(followUpQuestions);
            evidenceIds = copy(evidenceIds);
            sourceIds = copy(sourceIds);
        }
    }

    public record GeneratedQuestionsOutput(
            String schemaVersion, List<GeneratedQuestionDraft> questions) {
        public GeneratedQuestionsOutput {
            questions = copy(questions);
        }
    }

    public record ValidateProvenanceInput(
            String schemaVersion, int questionCount, String questionsHash) {}

    public record ValidatedQuestionsOutput(
            String schemaVersion,
            List<GeneratedQuestion> questions,
            String questionsHash,
            int evidenceLinkCount,
            int sourceLinkCount) {
        public ValidatedQuestionsOutput {
            questions = copy(questions);
        }
    }

    public record PersistInput(
            String schemaVersion,
            UUID researchRunId,
            UUID questionSetId,
            SourceCoverage coverage,
            int questionCount) {}

    public record PersistOutput(
            String schemaVersion,
            UUID researchRunId,
            UUID questionSetId,
            SourceCoverage coverage,
            List<UUID> questionIds,
            String sourcesHash,
            String questionsHash) {
        public PersistOutput {
            questionIds = copy(questionIds);
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
