package com.hiresemble.ai.workflow.careerartifact;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.validation.StructuredOutputValidationException;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.TerminalPartialPolicy;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort.GenerationState;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort.PersistPreparation;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.EvidenceRef;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioFactCheckResult;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioPlan;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeFactCheckResult;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumePlan;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ValidationIssue;
import com.hiresemble.careerartifact.domain.CareerArtifactContentValidator;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Two fixed, tool-free Career Artifact workflows with reference-only checkpoints. */
public final class CareerArtifactGenerationWorkflow {

    public static final String LOAD_RESUME_REQUEST = "LOAD_RESUME_REQUEST";
    public static final String BUILD_VERIFIED_CAREER_CONTEXT = "BUILD_VERIFIED_CAREER_CONTEXT";
    public static final String PLAN_RESUME = "PLAN_RESUME";
    public static final String DRAFT_RESUME_CONTENT = "DRAFT_RESUME_CONTENT";
    public static final String FACT_CHECK_RESUME_CONTENT = "FACT_CHECK_RESUME_CONTENT";
    public static final String RENDER_DOCX = "RENDER_DOCX";
    public static final String VALIDATE_DOCX = "VALIDATE_DOCX";
    public static final String PERSIST_RESUME_VERSION = "PERSIST_RESUME_VERSION";

    public static final String LOAD_PORTFOLIO_REQUEST = "LOAD_PORTFOLIO_REQUEST";
    public static final String PLAN_PORTFOLIO_STORY = "PLAN_PORTFOLIO_STORY";
    public static final String DRAFT_PORTFOLIO_SLIDES = "DRAFT_PORTFOLIO_SLIDES";
    public static final String FACT_CHECK_PORTFOLIO_CONTENT = "FACT_CHECK_PORTFOLIO_CONTENT";
    public static final String RENDER_PPTX = "RENDER_PPTX";
    public static final String VALIDATE_PPTX = "VALIDATE_PPTX";
    public static final String PERSIST_PORTFOLIO_VERSION = "PERSIST_PORTFOLIO_VERSION";

    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(90);
    private final CareerArtifactWorkflowPort port;
    private final ObjectMapper objectMapper;
    private final CareerArtifactContentValidator contentValidator =
            new CareerArtifactContentValidator();

    public CareerArtifactGenerationWorkflow(
            CareerArtifactWorkflowPort port, ObjectMapper objectMapper) {
        this.port = port;
        this.objectMapper = objectMapper;
    }

    public ExecutableWorkflowContribution resumeContribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.RESUME_GENERATION,
                CanonicalWorkflowDefinitions.RESUME_GENERATION_VERSION,
                TerminalPartialPolicy.succeed(),
                List.of(
                        step(LOAD_RESUME_REQUEST, new LoadExecutor(ArtifactType.RESUME)),
                        step(BUILD_VERIFIED_CAREER_CONTEXT, new ContextExecutor(ArtifactType.RESUME)),
                        step(PLAN_RESUME, new ResumePlanExecutor()),
                        step(DRAFT_RESUME_CONTENT, new ResumeDraftExecutor()),
                        step(FACT_CHECK_RESUME_CONTENT, new ResumeFactCheckExecutor()),
                        step(RENDER_DOCX, new RenderExecutor(ArtifactType.RESUME)),
                        step(VALIDATE_DOCX, new ValidateExecutor(ArtifactType.RESUME)),
                        step(PERSIST_RESUME_VERSION, new PersistExecutor(ArtifactType.RESUME))));
    }

    public ExecutableWorkflowContribution portfolioContribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.PORTFOLIO_GENERATION,
                CanonicalWorkflowDefinitions.PORTFOLIO_GENERATION_VERSION,
                TerminalPartialPolicy.succeed(),
                List.of(
                        step(LOAD_PORTFOLIO_REQUEST, new LoadExecutor(ArtifactType.PORTFOLIO)),
                        step(BUILD_VERIFIED_CAREER_CONTEXT, new ContextExecutor(ArtifactType.PORTFOLIO)),
                        step(PLAN_PORTFOLIO_STORY, new PortfolioPlanExecutor()),
                        step(DRAFT_PORTFOLIO_SLIDES, new PortfolioDraftExecutor()),
                        step(FACT_CHECK_PORTFOLIO_CONTENT, new PortfolioFactCheckExecutor()),
                        step(RENDER_PPTX, new RenderExecutor(ArtifactType.PORTFOLIO)),
                        step(VALIDATE_PPTX, new ValidateExecutor(ArtifactType.PORTFOLIO)),
                        step(PERSIST_PORTFOLIO_VERSION, new PersistExecutor(ArtifactType.PORTFOLIO))));
    }

    private ExecutableWorkflowStep step(String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class ArtifactExecutor<T> implements WorkflowStepExecutor<T> {
        private final String stepKey;
        private final ArtifactType type;
        private final Class<T> outputType;
        private final Set<String> fields;

        private ArtifactExecutor(String stepKey, ArtifactType type, Class<T> outputType) {
            this.stepKey = stepKey;
            this.type = type;
            this.outputType = outputType;
            this.fields = Arrays.stream(outputType.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .collect(Collectors.toUnmodifiableSet());
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
                    stepKey.toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "-v1",
                    node -> {
                        if (node == null || !node.isObject()
                                || !fields.equals(Set.copyOf(node.propertyNames()))) {
                            throw StructuredOutputValidationException.deterministic(
                                    ValidationPhase.SCHEMA_SHAPE,
                                    "CAREER_ARTIFACT_OUTPUT_SCHEMA_INVALID");
                        }
                    },
                    output -> validateRecord(output, context),
                    output -> validateWorkflow(output, context),
                    output -> {});
        }

        protected void validateRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflow(T output, StepExecutionContext context) {}

        protected final GenerationState state(StepExecutionContext context) {
            WorkflowType expected = type.workflowType();
            if (context.run().workflowType() != expected) {
                throw configurationFailure();
            }
            String loadKey = type == ArtifactType.RESUME
                    ? LOAD_RESUME_REQUEST : LOAD_PORTFOLIO_REQUEST;
            Object loaded = context.ephemeral(loadKey);
            GenerationState state = loaded instanceof GenerationState generation
                    ? generation : port.load(context.run());
            if (state.artifact().artifactType() != type
                    || !state.model().equals(context.run().requestedModel())) {
                throw configurationFailure();
            }
            return state;
        }

        protected final JsonNode stableRefs(StepExecutionContext context) {
            GenerationState state = state(context);
            return objectMapper.createObjectNode()
                    .put("careerArtifactId", state.artifact().id().toString())
                    .put("targetVersionId", state.targetVersionId().toString())
                    .put("artifactType", type.name())
                    .put("model", state.model())
                    .put("templateKey", state.templateKey())
                    .put("templateVersion", state.templateVersion())
                    .put("renderProfileHash", state.renderProfileHash())
                    .put("contextHash", state.contextHash());
        }

        protected final StepInput localInput(
                StepExecutionContext context, JsonNode payload, String material) {
            GenerationState state = state(context);
            return new StepInput(
                    context.scopeKey(),
                    stableRefs(context),
                    stepKey + "|" + context.run().canonicalInputHash() + "|"
                            + state.model() + "|" + state.templateKey() + "|"
                            + state.templateVersion() + "|" + state.contextHash() + "|" + material,
                    payload,
                    null,
                    state.artifact().version());
        }

        protected final AiGatewayResponse localResponse(Object value) {
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(value), List.of());
            } catch (Exception exception) {
                throw configurationFailure();
            }
        }

        protected final AiGatewayResponse chat(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            if (!"none".equals(invocation.modelRoute().productKey())
                    && !state.model().equals(invocation.modelRoute().productKey())) {
                throw configurationFailure();
            }
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    Set.of(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType(),
                    "medium",
                    "low"));
        }

        protected final JsonNode hashOutput(Object value, String countField, int count) {
            return objectMapper.createObjectNode()
                    .put("outputHash", hash(objectMapper.valueToTree(value)))
                    .put(countField, count);
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class LoadExecutor extends ArtifactExecutor<RequestLoadedOutput> {
        private LoadExecutor(ArtifactType type) {
            super(type == ArtifactType.RESUME ? LOAD_RESUME_REQUEST : LOAD_PORTFOLIO_REQUEST,
                    type, RequestLoadedOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return localInput(context, stableRefs(context), "load");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            return localResponse(new RequestLoadedOutput(
                    state.artifact().id(), state.targetVersionId(), state.model(),
                    state.templateKey(), state.contextHash()));
        }

        @Override
        public JsonNode minimalOutput(RequestLoadedOutput output, ObjectMapper ignored) {
            return objectMapper.valueToTree(output);
        }

        @Override
        public Object ephemeralOutput(
                RequestLoadedOutput output, StepExecutionContext context) {
            return port.load(context.run());
        }
    }

    private final class ContextExecutor extends ArtifactExecutor<ContextBuiltOutput> {
        private ContextExecutor(ArtifactType type) {
            super(BUILD_VERIFIED_CAREER_CONTEXT, type, ContextBuiltOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return localInput(context, stableRefs(context), "context");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GenerationState state = state(invocation.executionContext());
            Set<String> present = state.profileSnapshots().stream()
                    .map(value -> value.section()).collect(Collectors.toSet());
            List<String> warnings = state.requestedProfileSections().stream()
                    .filter(section -> !present.contains(section))
                    .map(section -> "선택한 " + section + " 섹션에 사용할 항목이 없습니다.")
                    .toList();
            return localResponse(new ContextBuiltOutput(
                    state.contextHash(), state.evidence().size(),
                    state.profileSnapshots().size(), state.omittedRefCount(), warnings));
        }

        @Override
        public JsonNode minimalOutput(ContextBuiltOutput output, ObjectMapper ignored) {
            return objectMapper.valueToTree(output);
        }
    }

    private final class ResumePlanExecutor extends ArtifactExecutor<ResumePlan> {
        private ResumePlanExecutor() {
            super(PLAN_RESUME, ArtifactType.RESUME, ResumePlan.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            JsonNode payload = objectMapper.createObjectNode()
                    .put("artifactType", "RESUME")
                    .set("verifiedContext", state.boundedContext().deepCopy());
            return localInput(context, payload, "plan");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateRecord(ResumePlan output, StepExecutionContext context) {
            requireText(output.headlineDirection(), 500, false, "RESUME_PLAN_INVALID");
            bounded(output.sectionOrder(), 12, 100, "RESUME_PLAN_INVALID");
            bounded(output.warnings(), 20, 500, "RESUME_PLAN_INVALID");
            validateRefs(output.evidenceRefs(), state(context), false);
        }

        @Override
        public JsonNode minimalOutput(ResumePlan output, ObjectMapper ignored) {
            return hashOutput(output, "warningCount", output.warnings().size());
        }
    }

    private final class ResumeDraftExecutor extends ArtifactExecutor<ResumeContent> {
        private ResumeDraftExecutor() {
            super(DRAFT_RESUME_CONTENT, ArtifactType.RESUME, ResumeContent.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            Object plan = context.ephemeral(PLAN_RESUME);
            if (!(plan instanceof ResumePlan)) throw configurationFailure();
            JsonNode payload = objectMapper.createObjectNode()
                    .set("verifiedContext", state.boundedContext().deepCopy());
            ((tools.jackson.databind.node.ObjectNode) payload)
                    .set("resumePlan", objectMapper.valueToTree(plan));
            return localInput(context, payload, "draft|" + hash(objectMapper.valueToTree(plan)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateWorkflow(ResumeContent output, StepExecutionContext context) {
            validateResumeContent(output, state(context));
        }

        @Override
        public JsonNode minimalOutput(ResumeContent output, ObjectMapper ignored) {
            return hashOutput(output, "sectionCount", output.sections().size());
        }
    }

    private final class ResumeFactCheckExecutor
            extends ArtifactExecutor<ResumeFactCheckResult> {
        private ResumeFactCheckExecutor() {
            super(FACT_CHECK_RESUME_CONTENT, ArtifactType.RESUME,
                    ResumeFactCheckResult.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            Object draft = context.ephemeral(DRAFT_RESUME_CONTENT);
            if (!(draft instanceof ResumeContent)) throw configurationFailure();
            JsonNode payload = objectMapper.createObjectNode()
                    .set("verifiedContext", state.boundedContext().deepCopy());
            ((tools.jackson.databind.node.ObjectNode) payload)
                    .set("draftToFactCheck", objectMapper.valueToTree(draft));
            return localInput(context, payload, "fact|" + hash(objectMapper.valueToTree(draft)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateRecord(
                ResumeFactCheckResult output, StepExecutionContext context) {
            validateIssues(output.issues());
            bounded(output.warnings(), 20, 500, "RESUME_FACT_CHECK_INVALID");
        }

        @Override
        protected void validateWorkflow(
                ResumeFactCheckResult output, StepExecutionContext context) {
            validateResumeContent(output.groundedDraft(), state(context));
        }

        @Override
        public JsonNode minimalOutput(ResumeFactCheckResult output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("contentHash", hash(objectMapper.valueToTree(output.groundedDraft())))
                    .put("issueCount", output.issues().size())
                    .put("warningCount", output.warnings().size());
        }

        @Override
        public Object ephemeralOutput(
                ResumeFactCheckResult output, StepExecutionContext context) {
            ResumeContent draft = output.groundedDraft();
            List<String> warnings = mergeWarnings(
                    mergeWarnings(draft.warnings(), output.warnings()),
                    contextWarnings(context));
            return new ResumeContent(
                    draft.headline(), draft.summary(), draft.skills(), draft.sections(), warnings);
        }
    }

    private final class PortfolioPlanExecutor extends ArtifactExecutor<PortfolioPlan> {
        private PortfolioPlanExecutor() {
            super(PLAN_PORTFOLIO_STORY, ArtifactType.PORTFOLIO, PortfolioPlan.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            JsonNode payload = objectMapper.createObjectNode()
                    .put("artifactType", "PORTFOLIO")
                    .set("verifiedContext", state.boundedContext().deepCopy());
            return localInput(context, payload, "plan");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateRecord(PortfolioPlan output, StepExecutionContext context) {
            requireText(output.audience(), 200, false, "PORTFOLIO_PLAN_INVALID");
            bounded(output.coreMessages(), 12, 500, "PORTFOLIO_PLAN_INVALID");
            bounded(output.warnings(), 20, 500, "PORTFOLIO_PLAN_INVALID");
            validateRefs(output.evidenceRefs(), state(context), false);
        }

        @Override
        public JsonNode minimalOutput(PortfolioPlan output, ObjectMapper ignored) {
            return hashOutput(output, "messageCount", output.coreMessages().size());
        }
    }

    private final class PortfolioDraftExecutor extends ArtifactExecutor<PortfolioContent> {
        private PortfolioDraftExecutor() {
            super(DRAFT_PORTFOLIO_SLIDES, ArtifactType.PORTFOLIO, PortfolioContent.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            Object plan = context.ephemeral(PLAN_PORTFOLIO_STORY);
            if (!(plan instanceof PortfolioPlan)) throw configurationFailure();
            JsonNode payload = objectMapper.createObjectNode()
                    .set("verifiedContext", state.boundedContext().deepCopy());
            ((tools.jackson.databind.node.ObjectNode) payload)
                    .set("portfolioPlan", objectMapper.valueToTree(plan));
            return localInput(context, payload, "draft|" + hash(objectMapper.valueToTree(plan)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateWorkflow(PortfolioContent output, StepExecutionContext context) {
            validatePortfolioContent(output, state(context));
        }

        @Override
        public JsonNode minimalOutput(PortfolioContent output, ObjectMapper ignored) {
            return hashOutput(output, "slideCount", output.slides().size());
        }
    }

    private final class PortfolioFactCheckExecutor
            extends ArtifactExecutor<PortfolioFactCheckResult> {
        private PortfolioFactCheckExecutor() {
            super(FACT_CHECK_PORTFOLIO_CONTENT, ArtifactType.PORTFOLIO,
                    PortfolioFactCheckResult.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GenerationState state = state(context);
            Object draft = context.ephemeral(DRAFT_PORTFOLIO_SLIDES);
            if (!(draft instanceof PortfolioContent)) throw configurationFailure();
            JsonNode payload = objectMapper.createObjectNode()
                    .set("verifiedContext", state.boundedContext().deepCopy());
            ((tools.jackson.databind.node.ObjectNode) payload)
                    .set("draftToFactCheck", objectMapper.valueToTree(draft));
            return localInput(context, payload, "fact|" + hash(objectMapper.valueToTree(draft)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return chat(invocation);
        }

        @Override
        protected void validateRecord(
                PortfolioFactCheckResult output, StepExecutionContext context) {
            validateIssues(output.issues());
            bounded(output.warnings(), 20, 500, "PORTFOLIO_FACT_CHECK_INVALID");
        }

        @Override
        protected void validateWorkflow(
                PortfolioFactCheckResult output, StepExecutionContext context) {
            validatePortfolioContent(output.groundedDraft(), state(context));
        }

        @Override
        public JsonNode minimalOutput(PortfolioFactCheckResult output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("contentHash", hash(objectMapper.valueToTree(output.groundedDraft())))
                    .put("issueCount", output.issues().size())
                    .put("warningCount", output.warnings().size());
        }

        @Override
        public Object ephemeralOutput(
                PortfolioFactCheckResult output, StepExecutionContext context) {
            PortfolioContent draft = output.groundedDraft();
            return new PortfolioContent(
                    draft.slides(),
                    mergeWarnings(
                            mergeWarnings(draft.warnings(), output.warnings()),
                            contextWarnings(context)));
        }
    }

    private final class RenderExecutor extends ArtifactExecutor<RenderOutput> {
        private RenderExecutor(ArtifactType type) {
            super(type == ArtifactType.RESUME ? RENDER_DOCX : RENDER_PPTX,
                    type, RenderOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            String factKey = type(context) == ArtifactType.RESUME
                    ? FACT_CHECK_RESUME_CONTENT : FACT_CHECK_PORTFOLIO_CONTENT;
            Object content = context.ephemeral(factKey);
            if (!(content instanceof ResumeContent) && !(content instanceof PortfolioContent)) {
                throw configurationFailure();
            }
            return localInput(context, stableRefs(context),
                    "render|" + hash(objectMapper.valueToTree(content)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            StepExecutionContext context = invocation.executionContext();
            String factKey = type(context) == ArtifactType.RESUME
                    ? FACT_CHECK_RESUME_CONTENT : FACT_CHECK_PORTFOLIO_CONTENT;
            Object content = context.ephemeral(factKey);
            var rendered = port.render(state(context), content);
            return localResponse(new RenderOutput(
                    rendered.state().targetVersionId(), rendered.file().mimeType(),
                    rendered.file().sizeBytes(), rendered.file().checksumSha256()));
        }

        @Override
        protected void validateRecord(RenderOutput output, StepExecutionContext context) {
            GenerationState state = state(context);
            if (!state.targetVersionId().equals(output.targetVersionId())
                    || !state.artifact().artifactType().mimeType().equals(output.mimeType())
                    || output.sizeBytes() < 1
                    || output.checksumSha256() == null
                    || !output.checksumSha256().matches("[0-9a-f]{64}")) {
                throw deterministic("CAREER_ARTIFACT_RENDER_OUTPUT_INVALID");
            }
        }

        @Override
        public JsonNode minimalOutput(RenderOutput output, ObjectMapper ignored) {
            return objectMapper.valueToTree(output);
        }

        private ArtifactType type(StepExecutionContext context) {
            return state(context).artifact().artifactType();
        }
    }

    private final class ValidateExecutor extends ArtifactExecutor<ValidateOutput> {
        private ValidateExecutor(ArtifactType type) {
            super(type == ArtifactType.RESUME ? VALIDATE_DOCX : VALIDATE_PPTX,
                    type, ValidateOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return localInput(context, stableRefs(context), "validate");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            var rendered = port.validate(invocation.executionContext().run().id());
            return localResponse(new ValidateOutput(
                    rendered.state().targetVersionId(), rendered.validation().mimeType(),
                    rendered.validation().sizeBytes(), rendered.validation().checksumSha256(),
                    rendered.validation().contentUnitCount(), rendered.validation().warnings()));
        }

        @Override
        protected void validateRecord(ValidateOutput output, StepExecutionContext context) {
            if (!state(context).targetVersionId().equals(output.targetVersionId())
                    || output.contentUnitCount() < 1
                    || output.sizeBytes() < 1
                    || output.checksumSha256() == null
                    || !output.checksumSha256().matches("[0-9a-f]{64}")) {
                throw deterministic("CAREER_ARTIFACT_VALIDATE_OUTPUT_INVALID");
            }
            bounded(output.warnings(), 20, 500, "CAREER_ARTIFACT_VALIDATE_OUTPUT_INVALID");
        }

        @Override
        public JsonNode minimalOutput(ValidateOutput output, ObjectMapper ignored) {
            return objectMapper.valueToTree(output);
        }
    }

    private final class PersistExecutor extends ArtifactExecutor<PersistOutput> {
        private PersistExecutor(ArtifactType type) {
            super(type == ArtifactType.RESUME
                            ? PERSIST_RESUME_VERSION : PERSIST_PORTFOLIO_VERSION,
                    type, PersistOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return localInput(context, stableRefs(context), "persist");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PersistPreparation prepared =
                    port.upload(invocation.executionContext().run().id());
            return localResponse(new PersistOutput(
                    prepared.versionId(), prepared.versionNo(), prepared.checksumSha256()));
        }

        @Override
        protected void validateRecord(PersistOutput output, StepExecutionContext context) {
            if (!state(context).targetVersionId().equals(output.versionId())
                    || output.versionNo() < 1 || output.checksumSha256() == null
                    || !output.checksumSha256().matches("[0-9a-f]{64}")) {
                throw deterministic("CAREER_ARTIFACT_PERSIST_OUTPUT_INVALID");
            }
        }

        @Override
        public JsonNode minimalOutput(PersistOutput output, ObjectMapper ignored) {
            return objectMapper.valueToTree(output);
        }

        @Override
        public DomainStepCompletion completeFresh(
                PersistOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            Version version = port.apply(
                    context.run().id(),
                    new PersistPreparation(
                            output.versionId(), output.versionNo(), output.checksumSha256()));
            JsonNode committed = objectMapper.valueToTree(new PersistOutput(
                    version.id(), version.versionNo(), version.checksumSha256()));
            return new DomainStepCompletion(committed, java.util.Optional.empty(), null);
        }
    }

    private void validateResumeContent(ResumeContent output, GenerationState state) {
        try {
            contentValidator.validateResume(output, state.evidence());
        } catch (IllegalArgumentException exception) {
            throw validationFailure(exception);
        }
    }

    private void validatePortfolioContent(PortfolioContent output, GenerationState state) {
        try {
            contentValidator.validatePortfolio(output, state.evidence());
        } catch (IllegalArgumentException exception) {
            throw validationFailure(exception);
        }
    }

    private RuntimeException validationFailure(IllegalArgumentException failure) {
        String code = failure.getMessage() == null
                ? "CAREER_ARTIFACT_CONTENT_INVALID" : failure.getMessage();
        if ("UNKNOWN_EVIDENCE_REFERENCE".equals(code)) {
            return deterministic(code);
        }
        return StructuredOutputValidationException.repairable(
                ValidationPhase.WORKFLOW_CONTEXT,
                safeCode(code),
                "Return a fully grounded draft within every count and length limit. Use only supplied evidence references and copy every number or date exactly from its cited evidence.");
    }

    private void validateRefs(
            List<EvidenceRef> refs, GenerationState state, boolean required) {
        if (refs == null || refs.size() > 20 || required && refs.isEmpty()) {
            throw repairable("CAREER_ARTIFACT_EVIDENCE_REFS_INVALID");
        }
        for (EvidenceRef ref : refs) {
            boolean allowed = ref != null && state.evidence().stream().anyMatch(value ->
                    value.experienceItemId().equals(ref.experienceItemId())
                            && value.evidenceId().equals(ref.evidenceId())
                            && value.title().equals(ref.title()));
            if (!allowed) throw deterministic("UNKNOWN_EVIDENCE_REFERENCE");
            requireText(ref.title(), 250, false, "CAREER_ARTIFACT_EVIDENCE_REFS_INVALID");
        }
    }

    private void validateIssues(List<ValidationIssue> issues) {
        if (issues == null || issues.size() > 20) {
            throw repairable("CAREER_ARTIFACT_FACT_CHECK_ISSUES_INVALID");
        }
        issues.forEach(issue -> {
            if (issue == null || issue.code() == null
                    || !issue.code().matches("[A-Z0-9_]{1,100}")) {
                throw repairable("CAREER_ARTIFACT_FACT_CHECK_ISSUES_INVALID");
            }
            requireText(issue.location(), 200, false,
                    "CAREER_ARTIFACT_FACT_CHECK_ISSUES_INVALID");
            requireText(issue.safeMessage(), 500, false,
                    "CAREER_ARTIFACT_FACT_CHECK_ISSUES_INVALID");
        });
    }

    private void bounded(List<String> values, int maxItems, int maxLength, String code) {
        if (values == null || values.size() > maxItems) throw repairable(code);
        values.forEach(value -> requireText(value, maxLength, false, code));
    }

    private void requireText(
            String value, int maxLength, boolean nullable, String code) {
        if (value == null) {
            if (nullable) return;
            throw repairable(code);
        }
        if (value.isBlank() || value.length() > maxLength) throw repairable(code);
    }

    private StructuredOutputValidationException repairable(String code) {
        return StructuredOutputValidationException.repairable(
                ValidationPhase.JAVA_RECORD,
                safeCode(code),
                "Return a new strict object that follows every field, list, length, and evidence-reference constraint.");
    }

    private StructuredOutputValidationException deterministic(String code) {
        return StructuredOutputValidationException.deterministic(
                ValidationPhase.WORKFLOW_CONTEXT, safeCode(code));
    }

    private String safeCode(String value) {
        String code = value.replaceAll("[^A-Z0-9_]", "_");
        if (code.isBlank() || code.length() > 100) return "CAREER_ARTIFACT_CONTENT_INVALID";
        return code;
    }

    private List<String> mergeWarnings(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (first != null) values.addAll(first);
        if (second != null) values.addAll(second);
        return values.stream().limit(20).toList();
    }

    private List<String> contextWarnings(StepExecutionContext context) {
        Object value = context.ephemeral(BUILD_VERIFIED_CAREER_CONTEXT);
        return value instanceof ContextBuiltOutput output ? output.warnings() : List.of();
    }

    private String hash(JsonNode value) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "CAREER_ARTIFACT_WORKFLOW_NOT_CONFIGURED",
                "The Career Artifact workflow is not configured.");
    }

    public record RequestLoadedOutput(
            UUID artifactId,
            UUID targetVersionId,
            String model,
            String templateKey,
            String contextHash) {}

    public record ContextBuiltOutput(
            String contextHash,
            int evidenceCount,
            int profileSectionCount,
            int omittedRefCount,
            List<String> warnings) {}

    public record RenderOutput(
            UUID targetVersionId,
            String mimeType,
            long sizeBytes,
            String checksumSha256) {}

    public record ValidateOutput(
            UUID targetVersionId,
            String mimeType,
            long sizeBytes,
            String checksumSha256,
            int contentUnitCount,
            List<String> warnings) {}

    public record PersistOutput(
            UUID versionId,
            int versionNo,
            String checksumSha256) {}
}
