package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
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
import com.hiresemble.interview.application.model.InterviewModels.FeedbackContext;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackResult;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackRow;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackScore;
import com.hiresemble.interview.application.port.InterviewWorkflowCommandPort;
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Fixed five-step feedback workflow bound to one immutable answer version. */
public final class InterviewAnswerFeedbackWorkflow {

    public static final String LOAD_ANSWER_VERSION = "LOAD_ANSWER_VERSION";
    public static final String BUILD_FEEDBACK_CONTEXT = "BUILD_FEEDBACK_CONTEXT";
    public static final String ANALYZE_ANSWER = "ANALYZE_ANSWER";
    public static final String VALIDATE_FEEDBACK = "VALIDATE_FEEDBACK";
    public static final String PERSIST_FEEDBACK = "PERSIST_FEEDBACK";

    private static final String INPUT_SCHEMA = "interview-feedback-input-v1";
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(60);

    private final InterviewWorkflowQueryPort queryPort;
    private final InterviewWorkflowCommandPort commandPort;
    private final ObjectMapper objectMapper;

    public InterviewAnswerFeedbackWorkflow(
            InterviewWorkflowQueryPort queryPort,
            InterviewWorkflowCommandPort commandPort,
            ObjectMapper objectMapper) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.INTERVIEW_ANSWER_FEEDBACK,
                CanonicalWorkflowDefinitions.INTERVIEW_ANSWER_FEEDBACK_VERSION,
                List.of(
                        step(LOAD_ANSWER_VERSION, new LoadAnswerExecutor()),
                        step(BUILD_FEEDBACK_CONTEXT, new BuildContextExecutor()),
                        step(ANALYZE_ANSWER, new AnalyzeExecutor()),
                        step(VALIDATE_FEEDBACK, new ValidateExecutor()),
                        step(PERSIST_FEEDBACK, new PersistExecutor())));
    }

    private ExecutableWorkflowStep step(String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class FeedbackExecutor<T> implements WorkflowStepExecutor<T> {
        private final String stepKey;
        private final Class<T> outputType;
        private final Set<String> outputFields;

        private FeedbackExecutor(String stepKey, Class<T> outputType) {
            this.stepKey = stepKey;
            this.outputType = outputType;
            this.outputFields = recordFields(outputType);
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
                    outputSchema(stepKey),
                    node -> {
                        if (node == null
                                || !node.isObject()
                                || !outputFields.equals(Set.copyOf(node.propertyNames()))) {
                            throw new IllegalArgumentException(
                                    "interview feedback output schema is invalid");
                        }
                    },
                    output -> validate(output, context),
                    output -> {},
                    output -> {});
        }

        protected void validate(T output, StepExecutionContext context) {}

        protected FeedbackState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType() != WorkflowType.INTERVIEW_ANSWER_FEEDBACK
                    || !CanonicalWorkflowDefinitions.INTERVIEW_ANSWER_FEEDBACK_VERSION.equals(
                            context.run().workflowVersion())
                    || !"INTERVIEW_ANSWER_VERSION".equals(context.run().resourceType())
                    || context.run().resourceId() == null) {
                throw configurationFailure();
            }
            try {
                JsonNode input = context.run().inputReferenceSnapshot();
                UUID answerVersionId =
                        UUID.fromString(input.path("answerVersionId").asText());
                UUID questionId = UUID.fromString(input.path("questionId").asText());
                String contextHash = input.path("contextHash").asText();
                if (!answerVersionId.equals(context.run().resourceId())
                        || !isHash(contextHash)) {
                    throw ownerFailure();
                }
                FeedbackContext feedback =
                        queryPort.loadFeedbackContext(
                                context.run().userId(), answerVersionId, contextHash);
                if (!questionId.equals(feedback.questionId())) {
                    throw ownerFailure();
                }
                return new FeedbackState(feedback, contextHash);
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            } catch (RuntimeException exception) {
                throw ownerFailure();
            }
        }

        protected ObjectNode refs(FeedbackState state) {
            return objectMapper.createObjectNode()
                    .put("answerVersionId", state.context().answerVersionId().toString())
                    .put("questionId", state.context().questionId().toString())
                    .put("contextHash", state.contextHash());
        }

        protected StepInput localInput(
                FeedbackState state, String suffix, JsonNode payload) {
            return new StepInput(
                    null,
                    refs(state),
                    stepKey + "|" + state.contextHash() + "|" + suffix,
                    payload,
                    null,
                    0);
        }

        protected AiGatewayResponse local(Object value) {
            return new AiGatewayResponse(write(value), java.util.List.of());
        }

        protected JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
        }

        @Override
        public boolean reusable() {
            // Downstream steps need bounded answer/context values only in memory.
            return false;
        }
    }

    private final class LoadAnswerExecutor extends FeedbackExecutor<LoadAnswerOutput> {

        private LoadAnswerExecutor() {
            super(LOAD_ANSWER_VERSION, LoadAnswerOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            FeedbackState state = state(context);
            return localInput(
                    state,
                    "load",
                    tree(new LoadAnswerInput(
                            INPUT_SCHEMA,
                            state.context().answerVersionId(),
                            state.contextHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            FeedbackState state = state(invocation.executionContext());
            return local(new LoadAnswerOutput(
                    outputSchema(LOAD_ANSWER_VERSION),
                    state.context().answerVersionId(),
                    state.context().questionId(),
                    state.contextHash(),
                    sha256(state.context().answerContent())));
        }

        @Override
        protected void validate(LoadAnswerOutput output, StepExecutionContext context) {
            FeedbackState state = state(context);
            if (!outputSchema(LOAD_ANSWER_VERSION).equals(output.schemaVersion())
                    || !state.context().answerVersionId().equals(output.answerVersionId())
                    || !state.context().questionId().equals(output.questionId())
                    || !state.contextHash().equals(output.contextHash())
                    || !sha256(state.context().answerContent()).equals(output.answerHash())) {
                throw new IllegalArgumentException("interview answer snapshot is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(LoadAnswerOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private final class BuildContextExecutor extends FeedbackExecutor<FeedbackContextOutput> {

        private BuildContextExecutor() {
            super(BUILD_FEEDBACK_CONTEXT, FeedbackContextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            FeedbackState state = state(context);
            LoadAnswerOutput loaded =
                    requiredEphemeral(context, LOAD_ANSWER_VERSION, LoadAnswerOutput.class);
            return localInput(
                    state,
                    loaded.answerHash(),
                    tree(new BuildFeedbackContextInput(
                            INPUT_SCHEMA,
                            state.context().answerVersionId(),
                            state.context().questionId(),
                            loaded.answerHash(),
                            state.contextHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            FeedbackState state = state(invocation.executionContext());
            FeedbackContext value = state.context();
            return local(new FeedbackContextOutput(
                    outputSchema(BUILD_FEEDBACK_CONTEXT),
                    value.answerVersionId(),
                    value.questionId(),
                    bounded(value.questionText(), 2_000),
                    boundedNullable(value.intent(), 2_000),
                    value.evaluationPoints().stream()
                            .limit(20)
                            .map(point -> bounded(point, 500))
                            .toList(),
                    boundedNullable(value.answerGuide(), 10_000),
                    bounded(value.answerContent(), 20_000),
                    boundedNullable(value.companyName(), 200),
                    boundedNullable(value.positionName(), 300),
                    state.contextHash()));
        }

        @Override
        protected void validate(FeedbackContextOutput output, StepExecutionContext context) {
            FeedbackState state = state(context);
            if (!outputSchema(BUILD_FEEDBACK_CONTEXT).equals(output.schemaVersion())
                    || !state.context().answerVersionId().equals(output.answerVersionId())
                    || !state.context().questionId().equals(output.questionId())
                    || !validText(output.questionText(), 2_000)
                    || !validNullableText(output.intent(), 2_000)
                    || output.evaluationPoints() == null
                    || output.evaluationPoints().size() > 20
                    || output.evaluationPoints().stream()
                            .anyMatch(point -> !validText(point, 500))
                    || !validNullableText(output.answerGuide(), 10_000)
                    || !validText(output.answerContent(), 20_000)
                    || !validNullableText(output.companyName(), 200)
                    || !validNullableText(output.positionName(), 300)
                    || !state.contextHash().equals(output.contextHash())) {
                throw new IllegalArgumentException("feedback context is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(
                FeedbackContextOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("questionId", output.questionId().toString())
                    .put("contextHash", output.contextHash())
                    .put("contextMaterialHash", stableHash(output));
        }
    }

    private final class AnalyzeExecutor extends FeedbackExecutor<AnalyzeFeedbackOutput> {

        private AnalyzeExecutor() {
            super(ANALYZE_ANSWER, AnalyzeFeedbackOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            FeedbackState state = state(context);
            FeedbackContextOutput feedbackContext = requiredEphemeral(
                    context, BUILD_FEEDBACK_CONTEXT, FeedbackContextOutput.class);
            var input = tree(new AnalyzeFeedbackInput(
                    INPUT_SCHEMA,
                    feedbackContext.answerVersionId(),
                    feedbackContext.questionText(),
                    feedbackContext.intent(),
                    feedbackContext.evaluationPoints(),
                    feedbackContext.answerGuide(),
                    feedbackContext.answerContent(),
                    feedbackContext.companyName(),
                    feedbackContext.positionName()));
            return localInput(state, stableHash(feedbackContext), input);
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
        protected void validate(AnalyzeFeedbackOutput output, StepExecutionContext context) {
            FeedbackState state = state(context);
            if (!outputSchema(ANALYZE_ANSWER).equals(output.schemaVersion())
                    || !state.context().answerVersionId().equals(output.answerVersionId())) {
                throw new IllegalArgumentException("feedback analysis identity is invalid");
            }
            validateFeedback(
                    output.scores(),
                    output.strengths(),
                    output.weaknesses(),
                    output.suggestions(),
                    output.revisedExample());
        }

        @Override
        public JsonNode minimalOutput(
                AnalyzeFeedbackOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("scoreCount", output.scores().size())
                    .put("strengthCount", output.strengths().size())
                    .put("weaknessCount", output.weaknesses().size())
                    .put("suggestionCount", output.suggestions().size())
                    .put("resultHash", stableHash(output));
        }
    }

    private final class ValidateExecutor
            extends FeedbackExecutor<ValidatedFeedbackOutput> {

        private ValidateExecutor() {
            super(VALIDATE_FEEDBACK, ValidatedFeedbackOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            FeedbackState state = state(context);
            AnalyzeFeedbackOutput analysis =
                    requiredEphemeral(context, ANALYZE_ANSWER, AnalyzeFeedbackOutput.class);
            return localInput(
                    state,
                    stableHash(analysis),
                    tree(new ValidateFeedbackInput(
                            INPUT_SCHEMA,
                            analysis.answerVersionId(),
                            stableHash(analysis))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            AnalyzeFeedbackOutput analysis = requiredEphemeral(
                    invocation.executionContext(),
                    ANALYZE_ANSWER,
                    AnalyzeFeedbackOutput.class);
            return local(new ValidatedFeedbackOutput(
                    outputSchema(VALIDATE_FEEDBACK),
                    analysis.answerVersionId(),
                    analysis.scores(),
                    analysis.strengths(),
                    analysis.weaknesses(),
                    analysis.suggestions(),
                    analysis.revisedExample(),
                    stableHash(analysis)));
        }

        @Override
        protected void validate(
                ValidatedFeedbackOutput output, StepExecutionContext context) {
            FeedbackState state = state(context);
            if (!outputSchema(VALIDATE_FEEDBACK).equals(output.schemaVersion())
                    || !state.context().answerVersionId().equals(output.answerVersionId())
                    || !isHash(output.resultHash())) {
                throw new IllegalArgumentException("validated feedback identity is invalid");
            }
            validateFeedback(
                    output.scores(),
                    output.strengths(),
                    output.weaknesses(),
                    output.suggestions(),
                    output.revisedExample());
        }

        @Override
        public JsonNode minimalOutput(
                ValidatedFeedbackOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", output.schemaVersion())
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("scoreCount", output.scores().size())
                    .put("resultHash", output.resultHash());
        }
    }

    private final class PersistExecutor extends FeedbackExecutor<PersistFeedbackOutput> {

        private PersistExecutor() {
            super(PERSIST_FEEDBACK, PersistFeedbackOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            FeedbackState state = state(context);
            ValidatedFeedbackOutput validated = requiredEphemeral(
                    context, VALIDATE_FEEDBACK, ValidatedFeedbackOutput.class);
            return localInput(
                    state,
                    validated.resultHash(),
                    tree(new PersistFeedbackInput(
                            INPUT_SCHEMA,
                            validated.answerVersionId(),
                            validated.resultHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PersistFeedbackInput input =
                    read(invocation.input().gatewayPayload(), PersistFeedbackInput.class);
            return local(new PersistFeedbackOutput(
                    outputSchema(PERSIST_FEEDBACK),
                    input.answerVersionId(),
                    null,
                    input.resultHash()));
        }

        @Override
        protected void validate(PersistFeedbackOutput output, StepExecutionContext context) {
            FeedbackState state = state(context);
            if (!outputSchema(PERSIST_FEEDBACK).equals(output.schemaVersion())
                    || !state.context().answerVersionId().equals(output.answerVersionId())
                    || output.feedbackId() != null
                    || !isHash(output.resultHash())) {
                throw new IllegalArgumentException("feedback persist request is invalid");
            }
        }

        @Override
        public JsonNode minimalOutput(
                PersistFeedbackOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public DomainStepCompletion completeFresh(
                PersistFeedbackOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            FeedbackState state = state(context);
            ValidatedFeedbackOutput validated = requiredEphemeral(
                    context, VALIDATE_FEEDBACK, ValidatedFeedbackOutput.class);
            FeedbackRow saved;
            try {
                saved = commandPort.persistFeedback(
                        context.run().userId(),
                        context.run().id(),
                        state.context().answerVersionId(),
                        new FeedbackResult(
                                validated.scores(),
                                validated.strengths(),
                                validated.weaknesses(),
                                validated.suggestions(),
                                validated.revisedExample()));
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            return new DomainStepCompletion(
                    tree(new PersistFeedbackOutput(
                            outputSchema(PERSIST_FEEDBACK),
                            saved.answerVersionId(),
                            saved.id(),
                            validated.resultHash())),
                    Optional.empty(),
                    null);
        }
    }

    private void validateFeedback(
            List<FeedbackScore> scores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> suggestions,
            String revisedExample) {
        if (scores == null
                || scores.isEmpty()
                || scores.size() > 20
                || strengths == null
                || strengths.size() > 20
                || weaknesses == null
                || weaknesses.size() > 20
                || suggestions == null
                || suggestions.size() > 20
                || !validNullableText(revisedExample, 10_000)) {
            throw new IllegalArgumentException("feedback limits are invalid");
        }
        scores.forEach(score -> {
            if (score == null
                    || !validText(score.criterion(), 100)
                    || score.score() == null
                    || score.score().signum() < 0
                    || score.score().compareTo(java.math.BigDecimal.valueOf(100)) > 0
                    || !validNullableText(score.explanation(), 1_000)) {
                throw new IllegalArgumentException("feedback score is invalid");
            }
        });
        validateTextList(strengths, 1_000);
        validateTextList(weaknesses, 1_000);
        validateTextList(suggestions, 1_000);
    }

    private void validateTextList(List<String> values, int maxLength) {
        if (values.stream().anyMatch(value -> !validText(value, maxLength))) {
            throw new IllegalArgumentException("feedback text list is invalid");
        }
    }

    private <T> T requiredEphemeral(
            StepExecutionContext context, String stepKey, Class<T> type) {
        Object value = context.ephemeral(stepKey);
        if (!type.isInstance(value)) {
            throw configurationFailure();
        }
        return type.cast(value);
    }

    private Set<String> recordFields(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String outputSchema(String stepKey) {
        return "interview-"
                + stepKey.toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                + "-output-v1";
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw configurationFailure();
        }
    }

    private <T> T read(JsonNode value, Class<T> type) {
        try {
            return objectMapper.treeToValue(value, type);
        } catch (RuntimeException exception) {
            throw configurationFailure();
        }
    }

    private String stableHash(Object value) {
        return sha256(write(value));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String boundedNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return bounded(value, maxLength);
    }

    private boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private boolean validNullableText(String value, int maxLength) {
        return value == null || validText(value, maxLength);
    }

    private AiExecutionException mapBusiness(BusinessException exception) {
        return AiExecutionException.nonRetryable(
                exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND
                        ? FailureKind.OWNER
                        : FailureKind.DOMAIN_VALIDATION,
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
                "AI_CONTEXT_NOT_CONFIGURED",
                "면접 답변 피드백 AI 실행 구성이 준비되지 않았습니다.");
    }

    private record FeedbackState(FeedbackContext context, String contextHash) {}

    public record LoadAnswerInput(
            String schemaVersion, UUID answerVersionId, String contextHash) {}

    public record LoadAnswerOutput(
            String schemaVersion,
            UUID answerVersionId,
            UUID questionId,
            String contextHash,
            String answerHash) {}

    public record BuildFeedbackContextInput(
            String schemaVersion,
            UUID answerVersionId,
            UUID questionId,
            String answerHash,
            String contextHash) {}

    public record FeedbackContextOutput(
            String schemaVersion,
            UUID answerVersionId,
            UUID questionId,
            String questionText,
            String intent,
            List<String> evaluationPoints,
            String answerGuide,
            String answerContent,
            String companyName,
            String positionName,
            String contextHash) {
        public FeedbackContextOutput {
            evaluationPoints =
                    evaluationPoints == null ? null : List.copyOf(evaluationPoints);
        }
    }

    public record AnalyzeFeedbackInput(
            String schemaVersion,
            UUID answerVersionId,
            String questionText,
            String intent,
            List<String> evaluationPoints,
            String answerGuide,
            String answerContent,
            String companyName,
            String positionName) {
        public AnalyzeFeedbackInput {
            evaluationPoints =
                    evaluationPoints == null ? null : List.copyOf(evaluationPoints);
        }
    }

    public record AnalyzeFeedbackOutput(
            String schemaVersion,
            UUID answerVersionId,
            List<FeedbackScore> scores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> suggestions,
            String revisedExample) {
        public AnalyzeFeedbackOutput {
            scores = copy(scores);
            strengths = copy(strengths);
            weaknesses = copy(weaknesses);
            suggestions = copy(suggestions);
        }
    }

    public record ValidateFeedbackInput(
            String schemaVersion, UUID answerVersionId, String resultHash) {}

    public record ValidatedFeedbackOutput(
            String schemaVersion,
            UUID answerVersionId,
            List<FeedbackScore> scores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> suggestions,
            String revisedExample,
            String resultHash) {
        public ValidatedFeedbackOutput {
            scores = copy(scores);
            strengths = copy(strengths);
            weaknesses = copy(weaknesses);
            suggestions = copy(suggestions);
        }
    }

    public record PersistFeedbackInput(
            String schemaVersion, UUID answerVersionId, String resultHash) {}

    public record PersistFeedbackOutput(
            String schemaVersion,
            UUID answerVersionId,
            UUID feedbackId,
            String resultHash) {}

    private static <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
