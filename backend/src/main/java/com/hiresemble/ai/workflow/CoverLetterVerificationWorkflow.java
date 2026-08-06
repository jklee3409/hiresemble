package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.validation.KoreanUserFacingTextPolicy;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.BoundedText;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.EvidenceSelection;
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
import com.hiresemble.coverletter.application.model.CoverLetterModels.HistoricalEvidence;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistVerification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RequirementContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationIssue;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedClaim;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedEvidence;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Controlled six-step verification of one immutable cover-letter answer version. */
public final class CoverLetterVerificationWorkflow {

    public static final String LOAD_ANSWER_VERSION = "LOAD_ANSWER_VERSION";
    public static final String BUILD_PROVENANCE_CONTEXT =
            "BUILD_PROVENANCE_CONTEXT";
    public static final String CHECK_FACTS = "CHECK_FACTS";
    public static final String CHECK_REQUIREMENTS_AND_LENGTH =
            "CHECK_REQUIREMENTS_AND_LENGTH";
    public static final String AGGREGATE_VERIFICATION =
            "AGGREGATE_VERIFICATION";
    public static final String PERSIST_VERIFICATION = "PERSIST_VERIFICATION";

    private static final String LOAD_SCHEMA = "cover-verification-load-output-v1";
    private static final String PROVENANCE_SCHEMA =
            "cover-verification-provenance-output-v1";
    private static final String FACTS_SCHEMA = "cover-verification-facts-output-v1";
    private static final String FACTS_SCHEMA_V2 = "cover-verification-facts-output-v2";
    private static final String FACTS_SCHEMA_V3 = "cover-verification-facts-output-v3";
    private static final String REQUIREMENTS_SCHEMA =
            "cover-verification-requirements-output-v1";
    private static final String REQUIREMENTS_SCHEMA_V2 =
            "cover-verification-requirements-output-v2";
    private static final String REQUIREMENTS_SCHEMA_V3 =
            "cover-verification-requirements-output-v3";
    private static final String AGGREGATE_SCHEMA =
            "cover-verification-aggregate-output-v1";
    private static final String PERSIST_SCHEMA =
            "cover-verification-persist-output-v1";
    private static final String INPUT_SCHEMA = "cover-letter-input-v1";
    private static final String INPUT_SCHEMA_V2 = "cover-letter-input-v2";
    private static final String INPUT_SCHEMA_V3 = "cover-letter-input-v3";
    private static final String INPUT_SCHEMA_V4 = "cover-letter-input-v4";
    private static final String WRITING_QUALITY_RUBRIC_VERSION =
            "cover-letter-writing-quality-rubric-v2";
    private static final int MAX_VERIFICATION_EVIDENCE = 30;
    private static final int MAX_VERIFICATION_REQUIREMENTS = 30;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);
    private static final Pattern NUMBER =
            Pattern.compile("(?<![\\p{L}\\p{N}])\\d[\\d,.%]*(?![\\p{L}\\p{N}])");

    private final CoverLetterQueryPort queryPort;
    private final CoverLetterCommandPort commandPort;
    private final ObjectMapper objectMapper;

    public CoverLetterVerificationWorkflow(
            CoverLetterQueryPort queryPort,
            CoverLetterCommandPort commandPort,
            ObjectMapper objectMapper) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_VERIFICATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_LEGACY_VERSION,
                TerminalPartialPolicy.rejectUnexpected(),
                List.of(
                        step(LOAD_ANSWER_VERSION, new LoadAnswerExecutor()),
                        step(
                                BUILD_PROVENANCE_CONTEXT,
                                new BuildProvenanceExecutor()),
                        step(CHECK_FACTS, new CheckFactsExecutor(1)),
                        step(
                                CHECK_REQUIREMENTS_AND_LENGTH,
                                new CheckRequirementsExecutor(1)),
                        step(
                                AGGREGATE_VERIFICATION,
                                new AggregateVerificationExecutor()),
                        step(PERSIST_VERIFICATION, new PersistVerificationExecutor())));
    }

    /** Durable v2 contribution. */
    public ExecutableWorkflowContribution v2Contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_VERIFICATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V2_VERSION,
                TerminalPartialPolicy.rejectUnexpected(),
                List.of(
                        step(LOAD_ANSWER_VERSION, new LoadAnswerExecutor()),
                        step(BUILD_PROVENANCE_CONTEXT, new BuildProvenanceExecutor()),
                        step(CHECK_FACTS, new CheckFactsExecutor(2)),
                        step(CHECK_REQUIREMENTS_AND_LENGTH, new CheckRequirementsExecutor(2)),
                        step(AGGREGATE_VERIFICATION, new AggregateVerificationExecutor()),
                        step(PERSIST_VERIFICATION, new PersistVerificationExecutor())));
    }

    /** Durable v3 contribution. Legacy v1/v2 runs remain executable by exact version. */
    public ExecutableWorkflowContribution v3Contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_VERIFICATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V3_VERSION,
                TerminalPartialPolicy.rejectUnexpected(),
                List.of(
                        step(LOAD_ANSWER_VERSION, new LoadAnswerExecutor()),
                        step(BUILD_PROVENANCE_CONTEXT, new BuildProvenanceExecutor()),
                        step(CHECK_FACTS, new CheckFactsExecutor(3)),
                        step(CHECK_REQUIREMENTS_AND_LENGTH, new CheckRequirementsExecutor(3)),
                        step(AGGREGATE_VERIFICATION, new AggregateVerificationExecutor()),
                        step(PERSIST_VERIFICATION, new PersistVerificationExecutor())));
    }

    /** Active v4 contribution with an exact user-selected model. */
    public ExecutableWorkflowContribution v4Contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.COVER_LETTER_VERIFICATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION,
                TerminalPartialPolicy.rejectUnexpected(),
                List.of(
                        step(LOAD_ANSWER_VERSION, new LoadAnswerExecutor()),
                        step(BUILD_PROVENANCE_CONTEXT, new BuildProvenanceExecutor()),
                        step(CHECK_FACTS, new CheckFactsExecutor(3)),
                        step(CHECK_REQUIREMENTS_AND_LENGTH, new CheckRequirementsExecutor(3)),
                        step(AGGREGATE_VERIFICATION, new AggregateVerificationExecutor()),
                        step(PERSIST_VERIFICATION, new PersistVerificationExecutor())));
    }

    private ExecutableWorkflowStep step(
            String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class VerificationExecutor<T>
            implements WorkflowStepExecutor<T> {
        private final String stepKey;
        private final String schemaVersion;
        private final Class<T> outputType;
        private final Set<String> outputFields;

        private VerificationExecutor(
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
                                || !outputFields.equals(
                                        Set.copyOf(value.propertyNames()))) {
                            throw new IllegalArgumentException(
                                    "cover verification output schema is invalid");
                        }
                    },
                    value -> validateJavaRecord(value, context),
                    value -> validateWorkflowOutput(value, context),
                    value -> validateDomainOutput(value, context));
        }

        protected void validateJavaRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflowOutput(T output, StepExecutionContext context) {}

        protected void validateDomainOutput(T output, StepExecutionContext context) {}

        protected final VerificationState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType()
                            != WorkflowType.COVER_LETTER_VERIFICATION
                    || (!CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(
                                    context.run().workflowVersion())
                            && !CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V2_VERSION.equals(
                                    context.run().workflowVersion())
                            && !CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V3_VERSION.equals(
                                    context.run().workflowVersion())
                            && !CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_LEGACY_VERSION.equals(
                                    context.run().workflowVersion()))
                    || !"COVER_LETTER".equals(context.run().resourceType())
                    || context.run().resourceId() == null
                    || !validSelection(context.run())) {
                throw configurationFailure();
            }
            try {
                JsonNode input = context.run().inputReferenceSnapshot();
                UUID answerVersionId =
                        UUID.fromString(input.path("answerVersionId").asText());
                UUID verificationId =
                        UUID.fromString(input.path("verificationId").asText());
                VerificationSnapshot snapshot =
                        context.run().retryOfRunId() == null
                                ? isExactModel(context.run())
                                        ? queryPort.loadVerificationSnapshotByModel(
                                                context.run().userId(),
                                                answerVersionId,
                                                context.run().requestedModel(),
                                                context.contextSnapshot().contextHash())
                                        : isModern(context.run().workflowVersion())
                                        ? queryPort.loadVerificationSnapshotV2(
                                                context.run().userId(),
                                                answerVersionId,
                                                context.run().requestedQualityMode(),
                                                context.contextSnapshot().contextHash())
                                        : queryPort.loadVerificationSnapshot(
                                                context.run().userId(),
                                                answerVersionId,
                                                context.run().requestedQualityMode(),
                                                context.contextSnapshot().contextHash())
                                : isModern(context.run().workflowVersion())
                                        ? queryPort.loadVerificationRetrySnapshotV2(
                                                context.run().userId(),
                                                context.run().id(),
                                                context.contextSnapshot().contextHash())
                                        : queryPort.loadVerificationRetrySnapshot(
                                                context.run().userId(),
                                                context.run().id(),
                                                context.contextSnapshot().contextHash());
                if (!snapshot.userId().equals(context.run().userId())
                        || !snapshot.coverLetterId().equals(
                                context.run().resourceId())
                        || !snapshot.answerVersion().id().equals(answerVersionId)
                        || !selectionMatches(context.run(), snapshot)
                        || !snapshot.snapshotHash().equals(
                                context.contextSnapshot().contextHash())) {
                    throw ownerFailure();
                }
                return new VerificationState(
                        snapshot,
                        context.run().id(),
                        verificationId,
                        context.run().workflowVersion());
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            } catch (RuntimeException exception) {
                throw ownerFailure();
            }
        }

        protected final StepInput localInput(
                VerificationState state,
                JsonNode refs,
                String canonicalSuffix,
                JsonNode payload) {
            return new StepInput(
                    null,
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

        protected final tools.jackson.databind.node.ObjectNode baseRefs(
                VerificationState state) {
            return objectMapper.createObjectNode()
                    .put(
                            "coverLetterId",
                            state.snapshot().coverLetterId().toString())
                    .put(
                            "coverLetterVersion",
                            state.snapshot().coverLetterVersion())
                    .put(
                            "questionId",
                            state.snapshot().question().id().toString())
                    .put(
                            "answerVersionId",
                            state.snapshot().answerVersion().id().toString())
                    .put(
                            "answerVersionNo",
                            state.snapshot().answerVersion().versionNo())
                    .put("verificationId", state.verificationId().toString())
                    .put("snapshotHash", state.snapshot().snapshotHash());
        }

        protected final JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
        }

        protected final AiGatewayResponse localResponse(Object value) {
            return new AiGatewayResponse(write(value), java.util.List.of());
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

    private final class LoadAnswerExecutor
            extends VerificationExecutor<LoadAnswerOutput> {
        private LoadAnswerExecutor() {
            super(LOAD_ANSWER_VERSION, LOAD_SCHEMA, LoadAnswerOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            VerificationState state = state(context);
            return localInput(
                    state,
                    baseRefs(state),
                    LOAD_SCHEMA,
                    tree(new LoadAnswerInput(
                            inputSchema(state),
                            state.snapshot().answerVersion().id(),
                            state.snapshot().answerVersion().versionNo(),
                            state.snapshot().snapshotHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            VerificationState state = state(invocation.executionContext());
            return localResponse(new LoadAnswerOutput(
                    LOAD_SCHEMA,
                    state.snapshot().coverLetterId(),
                    state.snapshot().question().id(),
                    state.snapshot().answerVersion().id(),
                    state.snapshot().answerVersion().versionNo(),
                    sha256(state.snapshot().answerVersion().contentJson().toString()),
                    state.snapshot().answerVersion().characterCount(),
                    state.snapshot().question().maxLength(),
                    state.snapshot().snapshotHash()));
        }

        @Override
        protected void validateJavaRecord(
                LoadAnswerOutput output, StepExecutionContext context) {
            if (!LOAD_SCHEMA.equals(output.schemaVersion())
                    || output.coverLetterId() == null
                    || output.questionId() == null
                    || output.answerVersionId() == null
                    || output.answerVersionNo() < 1
                    || !isHash(output.answerHash())
                    || output.characterCount() < 0
                    || (output.maxLength() != null && output.maxLength() < 1)
                    || !isHash(output.snapshotHash())) {
                throw new IllegalArgumentException("loaded answer output is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                LoadAnswerOutput output, StepExecutionContext context) {
            VerificationState state = state(context);
            if (!output.coverLetterId().equals(state.snapshot().coverLetterId())
                    || !output.questionId().equals(
                            state.snapshot().question().id())
                    || !output.answerVersionId().equals(
                            state.snapshot().answerVersion().id())
                    || !output.snapshotHash().equals(
                            state.snapshot().snapshotHash())) {
                throw domainFailure(
                        "COVER_VERIFICATION_ANSWER_STALE",
                        "검증할 자기소개서 답변이 변경되었습니다.");
            }
        }
    }

    private final class BuildProvenanceExecutor
            extends VerificationExecutor<ProvenanceContextOutput> {
        private BuildProvenanceExecutor() {
            super(
                    BUILD_PROVENANCE_CONTEXT,
                    PROVENANCE_SCHEMA,
                    ProvenanceContextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            VerificationState state = state(context);
            var refs = baseRefs(state);
            refs.put(
                    "historicalEvidenceCount",
                    state.snapshot().historicalEvidence().size());
            refs.put(
                    "currentVerifiedEvidenceCount",
                    state.snapshot().currentVerifiedEvidence().size());
            refs.put(
                    "historicalHash",
                    stableHash(state.snapshot().historicalEvidence()));
            return localInput(
                    state,
                    refs,
                    stableHash(state.snapshot().historicalEvidence()),
                    tree(new BuildProvenanceInput(
                            inputSchema(state),
                            state.snapshot().answerVersion().id(),
                            state.snapshot().snapshotHash())));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            VerificationState state = state(invocation.executionContext());
            List<HistoricalEvidenceRef> historical =
                    state.snapshot().historicalEvidence().stream()
                            .sorted(Comparator.comparing(HistoricalEvidence::id))
                            .map(value -> new HistoricalEvidenceRef(
                                    value.id(),
                                    value.sourceType().name(),
                                    value.evidenceCategory(),
                                    value.currentStatus(),
                                    value.sourceDeleted(),
                                    bounded(value.claimText(), 2_000),
                                    value.usageType().name()))
                            .toList();
            return localResponse(new ProvenanceContextOutput(
                    PROVENANCE_SCHEMA,
                    state.snapshot().answerVersion().id(),
                    historical,
                    state.snapshot().currentVerifiedEvidence().stream()
                            .map(VerifiedEvidence::id)
                            .sorted()
                            .toList(),
                    historical.stream()
                            .filter(value -> value.sourceDeleted()
                                    || value.currentStatus()
                                            == EvidenceVerificationStatus.SOURCE_DELETED)
                            .map(HistoricalEvidenceRef::evidenceId)
                            .toList(),
                    state.snapshot().snapshotHash()));
        }

        @Override
        public JsonNode minimalOutput(
                ProvenanceContextOutput output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("schemaVersion", PROVENANCE_SCHEMA)
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("historicalHash", stableHash(output.historicalEvidence()))
                    .put("historicalEvidenceCount", output.historicalEvidence().size())
                    .put(
                            "currentVerifiedEvidenceCount",
                            output.currentVerifiedEvidenceIds().size())
                    .put(
                            "sourceDeletedEvidenceCount",
                            output.sourceDeletedEvidenceIds().size())
                    .put("snapshotHash", output.snapshotHash());
            var historicalIds = result.putArray("historicalEvidenceIds");
            output.historicalEvidence().stream()
                    .map(HistoricalEvidenceRef::evidenceId)
                    .sorted()
                    .forEach(id -> historicalIds.add(id.toString()));
            return result;
        }

        @Override
        public boolean reusable() {
            // Downstream checks need bounded claim text only in memory. Re-run this local step
            // after a restart instead of persisting or reconstructing that content from a checkpoint.
            return false;
        }

        @Override
        protected void validateJavaRecord(
                ProvenanceContextOutput output,
                StepExecutionContext context) {
            if (!PROVENANCE_SCHEMA.equals(output.schemaVersion())
                    || output.answerVersionId() == null
                    || output.historicalEvidence() == null
                    || output.historicalEvidence().size() > 100
                    || hasDuplicates(output.historicalEvidence().stream()
                            .map(HistoricalEvidenceRef::evidenceId)
                            .toList())
                    || hasDuplicates(output.currentVerifiedEvidenceIds())
                    || hasDuplicates(output.sourceDeletedEvidenceIds())
                    || !isHash(output.snapshotHash())) {
                throw new IllegalArgumentException("provenance context is invalid");
            }
            output.historicalEvidence().forEach(value -> {
                if (value.evidenceId() == null
                        || value.currentStatus() == null
                        || value.sourceType() == null
                        || value.evidenceCategory() == null
                        || value.evidenceCategory().isBlank()
                        || value.evidenceCategory().length() > 80
                        || value.usageType() == null
                        || (value.claimText() != null
                                && value.claimText().length() > 2_000)) {
                    throw new IllegalArgumentException(
                            "historical provenance is invalid");
                }
            });
        }

        @Override
        protected void validateDomainOutput(
                ProvenanceContextOutput output,
                StepExecutionContext context) {
            VerificationState state = state(context);
            Set<UUID> historical = state.snapshot().historicalEvidence().stream()
                    .map(HistoricalEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> current = state.snapshot().currentVerifiedEvidence().stream()
                    .map(VerifiedEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.answerVersionId().equals(
                            state.snapshot().answerVersion().id())
                    || !output.snapshotHash().equals(
                            state.snapshot().snapshotHash())
                    || !historical.equals(output.historicalEvidence().stream()
                            .map(HistoricalEvidenceRef::evidenceId)
                            .collect(java.util.stream.Collectors.toSet()))
                    || !current.equals(new HashSet<>(
                            output.currentVerifiedEvidenceIds()))) {
                throw domainFailure(
                        "COVER_VERIFICATION_PROVENANCE_STALE",
                        "자기소개서 답변의 근거 상태가 변경되었습니다.");
            }
        }
    }

    private final class CheckFactsExecutor
            extends VerificationExecutor<FactCheckOutput> {
        private final int generation;

        private CheckFactsExecutor(int generation) {
            super(CHECK_FACTS, factsSchema(generation), FactCheckOutput.class);
            this.generation = generation;
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            VerificationState state = state(context);
            ProvenanceContextOutput provenance = requiredEphemeral(
                    context,
                    BUILD_PROVENANCE_CONTEXT,
                    ProvenanceContextOutput.class);
            Set<UUID> historicallyUsed = state.snapshot().historicalEvidence().stream()
                    .map(HistoricalEvidence::id)
                    .collect(java.util.stream.Collectors.toSet());
            EvidenceSelection<VerifiedEvidence> selection = generation == 3
                    ? CoverLetterWorkflowV3Policy.selectRelevant(
                            state.snapshot().currentVerifiedEvidence(),
                            historicallyUsed,
                            state.snapshot().answerVersion().plainText(),
                            MAX_VERIFICATION_EVIDENCE,
                            VerifiedEvidence::id,
                            value -> Objects.toString(value.title(), "")
                                    + " "
                                    + Objects.toString(value.content(), ""))
                    : new EvidenceSelection<>(
                            state.snapshot().currentVerifiedEvidence().stream()
                                    .sorted(Comparator
                                            .<VerifiedEvidence, Boolean>comparing(
                                                    value -> !historicallyUsed.contains(value.id()))
                                            .thenComparing(VerifiedEvidence::id))
                                    .limit(generation == 2
                                            ? MAX_VERIFICATION_EVIDENCE
                                            : Long.MAX_VALUE)
                                    .toList(),
                            0,
                            "legacy-uuid-order");
            List<ApprovedEvidenceInput> current = selection.selected().stream()
                            .map(value -> new ApprovedEvidenceInput(
                                    value.id(),
                                    value.sourceType().name(),
                                    value.evidenceCategory(),
                                    bounded(value.title(), generation >= 2 ? 200 : 250),
                                    bounded(value.content(), generation >= 2 ? 1_500 : 4_000),
                                    value.version()))
                            .toList();
            var refs = baseRefs(state);
            refs.put(
                    "answerHash",
                    sha256(state.snapshot().answerVersion().plainText()));
            refs.put("provenanceHash", stableHash(provenance));
            return localInput(
                    state,
                    refs,
                    stableHash(provenance),
                    generation == 3
                            ? tree(new CheckFactsInputV3(
                                    INPUT_SCHEMA_V3,
                                    CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                                    state.snapshot().answerVersion().id(),
                                    CoverLetterWorkflowV3Policy.bound(
                                            state.snapshot().answerVersion().plainText(), 20_000),
                                    provenance.historicalEvidence(),
                                    current,
                                    selection.omittedCount(),
                                    selection.policyVersion(),
                                    bounded(state.snapshot().job().companyName(), 200),
                                    bounded(state.snapshot().job().positionName(), 300)))
                            : generation == 2
                                    ? tree(new CheckFactsInputV2(
                                    INPUT_SCHEMA_V2,
                                    state.snapshot().answerVersion().id(),
                                    bounded(state.snapshot().answerVersion().plainText(), 20_000),
                                    provenance.historicalEvidence(),
                                    current,
                                    Math.max(0, state.snapshot().currentVerifiedEvidence().size()
                                            - current.size()),
                                    bounded(state.snapshot().job().companyName(), 200),
                                    bounded(state.snapshot().job().positionName(), 300)))
                                    : tree(new CheckFactsInput(
                                    INPUT_SCHEMA,
                                    state.snapshot().answerVersion().id(),
                                    bounded(state.snapshot().answerVersion().plainText(), 20_000),
                                    provenance.historicalEvidence(),
                                    current,
                                    bounded(state.snapshot().job().companyName(), 200),
                                    bounded(state.snapshot().job().positionName(), 300))));
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
        public JsonNode minimalOutput(FactCheckOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", factsSchema(generation))
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("factsHash", stableHash(output))
                    .put("issueCount", output.issues().size())
                    .put("verifiedClaimCount", output.verifiedClaims().size());
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                FactCheckOutput output, StepExecutionContext context) {
            validateCheckOutput(
                    output.schemaVersion(),
                    factsSchema(generation),
                    output.answerVersionId(),
                    output.issues(),
                    output.suggestions());
            if (output.verifiedClaims() == null
                    || output.verifiedClaims().size() > 100) {
                throw new IllegalArgumentException("verified claims are invalid");
            }
            output.verifiedClaims().forEach(value -> {
                requireText(value.claim(), 2_000);
                if (value.evidenceIds() == null
                        || value.evidenceIds().size() > 20
                        || hasDuplicates(value.evidenceIds())) {
                    throw new IllegalArgumentException(
                            "verified claim evidence is invalid");
                }
                if (generation == 3
                        && (!value.supported()
                                || value.evidenceIds().isEmpty()
                                || !CoverLetterWorkflowV3Policy.normalize(
                                                state(context).snapshot().answerVersion().plainText())
                                        .contains(CoverLetterWorkflowV3Policy.normalize(
                                                value.claim())))) {
                    throw new IllegalArgumentException(
                            "verified claims must be positive grounded excerpts");
                }
            });
            if (generation == 3) {
                output.issues().forEach(CoverLetterVerificationWorkflow.this::validateV3FactIssue);
                requireKorean(output.issues(), output.suggestions());
            }
        }

        @Override
        protected void validateWorkflowOutput(
                FactCheckOutput output, StepExecutionContext context) {
            VerificationState state = state(context);
            List<String> unsupported = unsupportedNumbers(
                    state.snapshot().answerVersion().plainText(),
                    state.snapshot().currentVerifiedEvidence());
            if (!unsupported.isEmpty()
                    && output.issues().stream().noneMatch(issue ->
                            (issue.code()
                                                    == VerificationIssueCode.UNVERIFIED_CLAIM
                                            || issue.code()
                                                    == VerificationIssueCode.CONTRADICTION)
                                    && issue.severity() == IssueSeverity.ERROR)) {
                throw new IllegalArgumentException(
                        "unsupported numbers require an error issue");
            }
            if (!state.snapshot().answerVersion().plainText().isBlank()
                    && state.snapshot().currentVerifiedEvidence().isEmpty()
                    && output.issues().stream().noneMatch(issue ->
                            issue.code()
                                    == VerificationIssueCode.UNVERIFIED_CLAIM)) {
                throw new IllegalArgumentException(
                        "unverified content requires an issue");
            }
        }

        @Override
        protected void validateDomainOutput(
                FactCheckOutput output, StepExecutionContext context) {
            VerificationState state = state(context);
            Set<UUID> allAllowed = new HashSet<>();
            state.snapshot()
                    .historicalEvidence()
                    .forEach(value -> allAllowed.add(value.id()));
            Set<UUID> currentVerified =
                    state.snapshot().currentVerifiedEvidence().stream()
                            .map(VerifiedEvidence::id)
                            .collect(java.util.stream.Collectors.toSet());
            Set<UUID> issueEvidence = output.issues().stream()
                    .flatMap(value -> value.evidenceIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            allAllowed.addAll(currentVerified);
            Set<UUID> claimEvidence = output.verifiedClaims().stream()
                    .flatMap(value -> value.evidenceIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            if (!output.answerVersionId().equals(
                            state.snapshot().answerVersion().id())
                    || !allAllowed.containsAll(issueEvidence)
                    || !currentVerified.containsAll(claimEvidence)) {
                throw domainFailure(
                        "COVER_VERIFICATION_EVIDENCE_INVALID",
                        "자기소개서 검증 근거를 확인하지 못했습니다.");
            }
        }
    }

    private final class CheckRequirementsExecutor
            extends VerificationExecutor<RequirementCheckOutput> {
        private final int generation;

        private CheckRequirementsExecutor(int generation) {
            super(
                    CHECK_REQUIREMENTS_AND_LENGTH,
                    requirementsSchema(generation),
                    RequirementCheckOutput.class);
            this.generation = generation;
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            VerificationState state = state(context);
            List<RequirementInput> requirements =
                    state.snapshot().job().requirements().stream()
                            .limit(generation >= 2
                                    ? MAX_VERIFICATION_REQUIREMENTS
                                    : Long.MAX_VALUE)
                            .map(value -> new RequirementInput(
                                    value.category(),
                                    bounded(value.text(), generation >= 2 ? 500 : 2_000),
                                    value.required()))
                            .toList();
            var refs = baseRefs(state);
            refs.put(
                    "answerHash",
                    sha256(state.snapshot().answerVersion().plainText()));
            refs.put("requirementsHash", stableHash(requirements));
            List<SiblingAnswerInput> siblings = state.snapshot().siblingAnswers().stream()
                    .map(value -> new SiblingAnswerInput(
                            value.questionId(),
                            bounded(value.questionText(), 800),
                            value.maxLength(),
                            bounded(value.plainText(), 800),
                            value.characterCount()))
                    .toList();
            refs.put("siblingAnswerHash", stableHash(siblings));
            refs.put(
                    "characterCount",
                    state.snapshot().answerVersion().characterCount());
            if (state.snapshot().question().maxLength() != null) {
                refs.put(
                        "maxLength",
                        state.snapshot().question().maxLength());
            }
            return localInput(
                    state,
                    refs,
                    stableHash(requirements),
                    generation == 3
                            ? tree(new CheckRequirementsInputV3(
                                    INPUT_SCHEMA_V3,
                                    CoverLetterWorkflowV3Policy.OUTPUT_LOCALE,
                                    state.snapshot().answerVersion().id(),
                                    bounded(state.snapshot().question().questionText(), 2_000),
                                    CoverLetterWorkflowV3Policy.bound(
                                            state.snapshot().answerVersion().plainText(), 20_000),
                                    state.snapshot().answerVersion().characterCount(),
                                    state.snapshot().question().maxLength(),
                                    new JobWritingContextInput(
                                            bounded(state.snapshot().job().companyName(), 200),
                                            bounded(state.snapshot().job().title(), 300),
                                            bounded(state.snapshot().job().positionName(), 300),
                                            bounded(state.snapshot().job().descriptionText(), 4_000),
                                            requirements,
                                            state.snapshot().job().analysisOutdated()),
                                    requirements,
                                    state.snapshot().siblingAnswers().stream()
                                            .map(value -> new SiblingAnswerInputV3(
                                                    value.questionId(),
                                                    bounded(value.questionText(), 800),
                                                    value.maxLength(),
                                                    CoverLetterWorkflowV3Policy.bound(
                                                            value.plainText(), 800)))
                                            .toList(),
                                    WRITING_QUALITY_RUBRIC_VERSION,
                                    CoverLetterWorkflowV3Policy.DUPLICATION_POLICY_VERSION))
                            : generation == 2
                                    ? tree(new CheckRequirementsInputV2(
                                    INPUT_SCHEMA_V2,
                                    state.snapshot().answerVersion().id(),
                                    bounded(state.snapshot().question().questionText(), 2_000),
                                    bounded(state.snapshot().answerVersion().plainText(), 20_000),
                                    state.snapshot().answerVersion().characterCount(),
                                    state.snapshot().question().maxLength(),
                                    new JobWritingContextInput(
                                            bounded(state.snapshot().job().companyName(), 200),
                                            bounded(state.snapshot().job().title(), 300),
                                            bounded(state.snapshot().job().positionName(), 300),
                                            bounded(state.snapshot().job().descriptionText(), 4_000),
                                            requirements,
                                            state.snapshot().job().analysisOutdated()),
                                    requirements,
                                    siblings,
                                    WRITING_QUALITY_RUBRIC_VERSION))
                                    : tree(new CheckRequirementsInput(
                                    INPUT_SCHEMA,
                                    state.snapshot().answerVersion().id(),
                                    bounded(state.snapshot().question().questionText(), 2_000),
                                    bounded(state.snapshot().answerVersion().plainText(), 20_000),
                                    state.snapshot().answerVersion().characterCount(),
                                    state.snapshot().question().maxLength(),
                                    requirements,
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
                RequirementCheckOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", requirementsSchema(generation))
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("requirementsHash", stableHash(output))
                    .put("issueCount", output.issues().size());
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                RequirementCheckOutput output,
                StepExecutionContext context) {
            validateCheckOutput(
                    output.schemaVersion(),
                    requirementsSchema(generation),
                    output.answerVersionId(),
                    output.issues(),
                    output.suggestions());
            if (generation >= 2 && output.issues().stream().anyMatch(issue ->
                    issue.code() == VerificationIssueCode.OTHER
                            && issue.severity() != IssueSeverity.WARNING)) {
                throw new IllegalArgumentException("writing-quality issues must be warnings");
            }
            if (generation == 3) {
                output.issues().forEach(CoverLetterVerificationWorkflow.this::validateV3RequirementIssue);
                requireKorean(output.issues(), output.suggestions());
            }
        }

        @Override
        protected void validateWorkflowOutput(
                RequirementCheckOutput output,
                StepExecutionContext context) {
            VerificationState state = state(context);
            Integer maximum = state.snapshot().question().maxLength();
            if (maximum != null
                    && state.snapshot().answerVersion().characterCount() > maximum
                    && output.issues().stream().noneMatch(issue ->
                            issue.code()
                                    == VerificationIssueCode.LENGTH_VIOLATION)) {
                throw new IllegalArgumentException(
                        "length violation issue is required");
            }
            if (generation == 3) {
                boolean duplicate = state.snapshot().siblingAnswers().stream().anyMatch(sibling ->
                        CoverLetterWorkflowV3Policy.duplication(
                                        state.snapshot().answerVersion().plainText(),
                                        Set.of(),
                                        "",
                                        sibling.plainText(),
                                        Set.of(),
                                        "",
                                        null)
                                .warningRequired());
                if (duplicate
                        && output.issues().stream().noneMatch(issue ->
                                issue.code() == VerificationIssueCode.OTHER
                                        && issue.severity() == IssueSeverity.WARNING)) {
                    throw new IllegalArgumentException(
                            "high cross-answer duplication requires a warning");
                }
            }
        }

        @Override
        protected void validateDomainOutput(
                RequirementCheckOutput output,
                StepExecutionContext context) {
            VerificationState state = state(context);
            if (!output.answerVersionId().equals(
                    state.snapshot().answerVersion().id())) {
                throw domainFailure(
                        "COVER_VERIFICATION_REQUIREMENT_SCOPE_INVALID",
                        "자기소개서 검증 문항 범위를 확인하지 못했습니다.");
            }
        }
    }

    private final class AggregateVerificationExecutor
            extends VerificationExecutor<AggregatedVerificationOutput> {
        private AggregateVerificationExecutor() {
            super(
                    AGGREGATE_VERIFICATION,
                    AGGREGATE_SCHEMA,
                    AggregatedVerificationOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            VerificationState state = state(context);
            FactCheckOutput facts = requiredEphemeral(
                    context, CHECK_FACTS, FactCheckOutput.class);
            RequirementCheckOutput requirements = requiredEphemeral(
                    context,
                    CHECK_REQUIREMENTS_AND_LENGTH,
                    RequirementCheckOutput.class);
            var refs = baseRefs(state);
            refs.put("factsHash", stableHash(facts));
            refs.put("requirementsHash", stableHash(requirements));
            return localInput(
                    state,
                    refs,
                    stableHash(facts) + "|" + stableHash(requirements),
                    tree(new AggregateVerificationInput(
                            inputSchema(state),
                            state.snapshot().answerVersion().id(),
                            stableHash(facts),
                            stableHash(requirements))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            VerificationState state = state(invocation.executionContext());
            FactCheckOutput facts = requiredEphemeral(
                    invocation.executionContext(),
                    CHECK_FACTS,
                    FactCheckOutput.class);
            RequirementCheckOutput requirements = requiredEphemeral(
                    invocation.executionContext(),
                    CHECK_REQUIREMENTS_AND_LENGTH,
                    RequirementCheckOutput.class);
            LinkedHashMap<String, VerificationIssueDraft> issues =
                    new LinkedHashMap<>();
            facts.issues().forEach(value -> issues.put(issueKey(value), value));
            requirements
                    .issues()
                    .forEach(value -> issues.putIfAbsent(issueKey(value), value));
            for (HistoricalEvidence evidence :
                    state.snapshot().historicalEvidence()) {
                boolean sourceDeleted = evidence.sourceDeleted()
                        || evidence.currentStatus()
                                == EvidenceVerificationStatus.SOURCE_DELETED;
                if (evidence.currentStatus() == EvidenceVerificationStatus.VERIFIED
                        && !sourceDeleted) {
                    continue;
                }
                VerificationIssueCode code = sourceDeleted
                        ? VerificationIssueCode.SOURCE_DELETED
                        : VerificationIssueCode.UNVERIFIED_CLAIM;
                if (issues.values().stream()
                        .anyMatch(value -> value.code() == code
                                && value.evidenceIds().contains(evidence.id()))) {
                    continue;
                }
                VerificationIssueDraft issue = new VerificationIssueDraft(
                        code,
                        IssueSeverity.WARNING,
                        sourceDeleted
                                ? "작성 당시 사용한 근거의 원본이 현재 삭제되었습니다."
                                : "작성 당시 사용한 근거가 현재 승인 상태가 아닙니다.",
                        bounded(evidence.claimText(), 1_000),
                        List.of(evidence.id()));
                issues.putIfAbsent(issueKey(issue), issue);
            }
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(facts.suggestions());
            requirements.suggestions().forEach(value -> {
                if (suggestions.size() < 20 && !suggestions.contains(value)) {
                    suggestions.add(value);
                }
            });
            VerificationStatus status = issues.values().stream()
                            .anyMatch(value ->
                                    value.severity() == IssueSeverity.ERROR)
                    ? VerificationStatus.FAILED
                    : issues.isEmpty()
                            ? VerificationStatus.PASSED
                            : VerificationStatus.WARNING;
            return localResponse(new AggregatedVerificationOutput(
                    AGGREGATE_SCHEMA,
                    state.snapshot().answerVersion().id(),
                    status,
                    List.copyOf(issues.values()),
                    List.copyOf(suggestions),
                    facts.verifiedClaims(),
                    state.snapshot().snapshotHash()));
        }

        @Override
        public JsonNode minimalOutput(
                AggregatedVerificationOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("schemaVersion", AGGREGATE_SCHEMA)
                    .put("answerVersionId", output.answerVersionId().toString())
                    .put("status", output.status().name())
                    .put("resultHash", stableHash(output))
                    .put("issueCount", output.issues().size())
                    .put("verifiedClaimCount", output.verifiedClaims().size());
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                AggregatedVerificationOutput output,
                StepExecutionContext context) {
            if (!AGGREGATE_SCHEMA.equals(output.schemaVersion())
                    || output.answerVersionId() == null
                    || output.status() == null
                    || output.status() == VerificationStatus.PENDING
                    || output.issues() == null
                    || output.issues().size() > 100
                    || output.suggestions() == null
                    || output.suggestions().size() > 20
                    || output.verifiedClaims() == null
                    || output.verifiedClaims().size() > 100
                    || !isHash(output.snapshotHash())) {
                throw new IllegalArgumentException(
                        "aggregated verification is invalid");
            }
            output.issues().forEach(
                    CoverLetterVerificationWorkflow.this::validateIssue);
            requireTexts(output.suggestions(), 20, 1_000);
        }

        @Override
        protected void validateWorkflowOutput(
                AggregatedVerificationOutput output,
                StepExecutionContext context) {
            VerificationStatus expected = output.issues().stream()
                            .anyMatch(value ->
                                    value.severity() == IssueSeverity.ERROR)
                    ? VerificationStatus.FAILED
                    : output.issues().isEmpty()
                            ? VerificationStatus.PASSED
                            : VerificationStatus.WARNING;
            if (output.status() != expected) {
                throw new IllegalArgumentException(
                        "verification status does not match issues");
            }
        }

        @Override
        protected void validateDomainOutput(
                AggregatedVerificationOutput output,
                StepExecutionContext context) {
            VerificationState state = state(context);
            if (!output.answerVersionId().equals(
                            state.snapshot().answerVersion().id())
                    || !output.snapshotHash().equals(
                            state.snapshot().snapshotHash())) {
                throw domainFailure(
                        "COVER_VERIFICATION_AGGREGATE_SCOPE_INVALID",
                        "자기소개서 검증 결과 범위를 확인하지 못했습니다.");
            }
        }
    }

    private final class PersistVerificationExecutor
            extends VerificationExecutor<PersistVerificationRequestOutput> {
        private PersistVerificationExecutor() {
            super(
                    PERSIST_VERIFICATION,
                    PERSIST_SCHEMA,
                    PersistVerificationRequestOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            VerificationState state = state(context);
            AggregatedVerificationOutput aggregate = requiredEphemeral(
                    context,
                    AGGREGATE_VERIFICATION,
                    AggregatedVerificationOutput.class);
            var refs = baseRefs(state);
            refs.put("resultHash", stableHash(aggregate));
            refs.put("status", aggregate.status().name());
            return localInput(
                    state,
                    refs,
                    stableHash(aggregate),
                    tree(new PersistVerificationInput(
                            inputSchema(state),
                            state.verificationId(),
                            state.snapshot().answerVersion().id(),
                            stableHash(aggregate))));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            PersistVerificationInput input = read(
                    invocation.input().gatewayPayload(),
                    PersistVerificationInput.class);
            return localResponse(new PersistVerificationRequestOutput(
                    PERSIST_SCHEMA,
                    input.verificationId(),
                    input.answerVersionId(),
                    input.resultHash()));
        }

        @Override
        public DomainStepCompletion completeFresh(
                PersistVerificationRequestOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            VerificationState state = state(context);
            AggregatedVerificationOutput aggregate = requiredEphemeral(
                    context,
                    AGGREGATE_VERIFICATION,
                    AggregatedVerificationOutput.class);
            Verification saved;
            try {
                saved = commandPort.persistVerification(
                        state.snapshot().userId(),
                        state.agentRunId(),
                        new PersistVerification(
                                state.verificationId(),
                                state.snapshot().answerVersion().id(),
                                state.snapshot().snapshotHash(),
                                result(aggregate)));
            } catch (BusinessException exception) {
                throw mapBusiness(exception);
            }
            return new DomainStepCompletion(
                    tree(new PersistedVerificationOutput(
                            PERSIST_SCHEMA,
                            saved.id(),
                            saved.answerVersionId(),
                            saved.status(),
                            state.snapshot().snapshotHash())),
                    Optional.empty(),
                    null);
        }

        @Override
        public Object ephemeralOutputFromMinimal(JsonNode minimalOutput) {
            return read(minimalOutput, PersistedVerificationOutput.class);
        }

        @Override
        protected void validateJavaRecord(
                PersistVerificationRequestOutput output,
                StepExecutionContext context) {
            if (!PERSIST_SCHEMA.equals(output.schemaVersion())
                    || output.verificationId() == null
                    || output.answerVersionId() == null
                    || !isHash(output.resultHash())) {
                throw new IllegalArgumentException(
                        "verification persist request is invalid");
            }
        }

        @Override
        protected void validateDomainOutput(
                PersistVerificationRequestOutput output,
                StepExecutionContext context) {
            VerificationState state = state(context);
            AggregatedVerificationOutput aggregate = requiredEphemeral(
                    context,
                    AGGREGATE_VERIFICATION,
                    AggregatedVerificationOutput.class);
            if (!output.verificationId().equals(state.verificationId())
                    || !output.answerVersionId().equals(
                            state.snapshot().answerVersion().id())
                    || !output.resultHash().equals(stableHash(aggregate))) {
                throw domainFailure(
                        "COVER_VERIFICATION_PERSIST_HASH_INVALID",
                        "검증된 자기소개서 결과를 저장하지 못했습니다.");
            }
        }
    }

    private VerificationResult result(AggregatedVerificationOutput aggregate) {
        return new VerificationResult(
                aggregate.status(),
                aggregate.issues().stream()
                        .map(value -> new VerificationIssue(
                                value.code(),
                                value.severity(),
                                value.message(),
                                value.relatedText(),
                                value.evidenceIds()))
                        .toList(),
                aggregate.suggestions(),
                aggregate.verifiedClaims().stream()
                        .map(value -> new VerifiedClaim(
                                value.claim(),
                                value.supported(),
                                value.evidenceIds()))
                        .toList());
    }

    private void validateCheckOutput(
            String schemaVersion,
            String expectedSchema,
            UUID answerVersionId,
            List<VerificationIssueDraft> issues,
            List<String> suggestions) {
        if (!expectedSchema.equals(schemaVersion)
                || answerVersionId == null
                || issues == null
                || issues.size() > 100
                || suggestions == null
                || suggestions.size() > 20) {
            throw new IllegalArgumentException("verification check is invalid");
        }
        issues.forEach(this::validateIssue);
        requireTexts(suggestions, 20, 1_000);
    }

    private void validateIssue(VerificationIssueDraft issue) {
        if (issue.code() == null || issue.severity() == null) {
            throw new IllegalArgumentException("verification issue is invalid");
        }
        requireText(issue.message(), 1_000);
        if (issue.relatedText() != null && issue.relatedText().length() > 1_000) {
            throw new IllegalArgumentException(
                    "verification related text is invalid");
        }
        if (issue.evidenceIds() == null
                || issue.evidenceIds().size() > 20
                || hasDuplicates(issue.evidenceIds())) {
            throw new IllegalArgumentException(
                    "verification issue evidence is invalid");
        }
    }

    private void validateV3FactIssue(VerificationIssueDraft issue) {
        if (!Set.of(
                        VerificationIssueCode.UNVERIFIED_CLAIM,
                        VerificationIssueCode.CONTRADICTION,
                        VerificationIssueCode.SOURCE_DELETED)
                .contains(issue.code())) {
            throw new IllegalArgumentException("fact issue code is incompatible");
        }
    }

    private void validateV3RequirementIssue(VerificationIssueDraft issue) {
        if (issue.code() == VerificationIssueCode.OTHER) {
            if (issue.severity() != IssueSeverity.WARNING) {
                throw new IllegalArgumentException("style issue must be a warning");
            }
            return;
        }
        if (!Set.of(
                        VerificationIssueCode.REQUIREMENT_MISSING,
                        VerificationIssueCode.LENGTH_VIOLATION)
                .contains(issue.code())) {
            throw new IllegalArgumentException("requirement issue code is incompatible");
        }
    }

    private void requireKorean(
            List<VerificationIssueDraft> issues, List<String> suggestions) {
        if (issues.stream().anyMatch(issue ->
                        !KoreanUserFacingTextPolicy.containsKorean(issue.message()))
                || suggestions.stream()
                        .anyMatch(value -> !KoreanUserFacingTextPolicy.containsKorean(value))) {
            throw new IllegalArgumentException("user-facing verification prose must be Korean");
        }
    }

    private String issueKey(VerificationIssueDraft issue) {
        return issue.code()
                + "|"
                + issue.severity()
                + "|"
                + Objects.toString(issue.relatedText(), "")
                + "|"
                + issue.evidenceIds();
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
            String value = matcher.group();
            if (!evidenceText.contains(value)) unsupported.add(value);
        }
        return unsupported.stream().distinct().toList();
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
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
                || values.stream().anyMatch(value -> value == null
                        || value.isBlank()
                        || value.length() > maximumLength)) {
            throw new IllegalArgumentException("text list is invalid");
        }
    }

    private boolean hasDuplicates(List<?> values) {
        return values == null || new HashSet<>(values).size() != values.size();
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private Set<String> recordFields(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
                "AI_COVER_VERIFICATION_NOT_CONFIGURED",
                "자기소개서 AI 검증 구성이 준비되지 않았습니다.");
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

    private record VerificationState(
            VerificationSnapshot snapshot,
            UUID agentRunId,
            UUID verificationId,
            String workflowVersion) {}

    private boolean isModern(String workflowVersion) {
        return CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(workflowVersion)
                || CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V3_VERSION.equals(
                        workflowVersion)
                || CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V2_VERSION.equals(
                        workflowVersion);
    }

    private static String factsSchema(int generation) {
        return generation == 3
                ? FACTS_SCHEMA_V3
                : generation == 2 ? FACTS_SCHEMA_V2 : FACTS_SCHEMA;
    }

    private static String requirementsSchema(int generation) {
        return generation == 3
                ? REQUIREMENTS_SCHEMA_V3
                : generation == 2 ? REQUIREMENTS_SCHEMA_V2 : REQUIREMENTS_SCHEMA;
    }

    private String inputSchema(VerificationState state) {
        if (CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(
                state.workflowVersion())) {
            return INPUT_SCHEMA_V4;
        }
        if (CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V3_VERSION.equals(
                state.workflowVersion())) {
            return INPUT_SCHEMA_V3;
        }
        return CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V2_VERSION.equals(
                        state.workflowVersion())
                ? INPUT_SCHEMA_V2
                : INPUT_SCHEMA;
    }

    private boolean isExactModel(AgentRunSnapshot run) {
        return CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(
                run.workflowVersion());
    }

    private boolean validSelection(AgentRunSnapshot run) {
        return isExactModel(run)
                ? run.requestedQualityMode() == null
                        && OpenAiChatModels.supportsCoverLetter(run.requestedModel())
                : run.requestedQualityMode() != null && run.requestedModel() == null;
    }

    private boolean selectionMatches(AgentRunSnapshot run, VerificationSnapshot snapshot) {
        return isExactModel(run)
                ? snapshot.qualityMode() == null
                        && run.requestedModel().equals(snapshot.model())
                : snapshot.model() == null
                        && run.requestedQualityMode() == snapshot.qualityMode();
    }

    public record LoadAnswerInput(
            String schemaVersion,
            UUID answerVersionId,
            int answerVersionNo,
            String snapshotHash) {}

    public record LoadAnswerOutput(
            String schemaVersion,
            UUID coverLetterId,
            UUID questionId,
            UUID answerVersionId,
            int answerVersionNo,
            String answerHash,
            int characterCount,
            Integer maxLength,
            String snapshotHash) {}

    public record BuildProvenanceInput(
            String schemaVersion,
            UUID answerVersionId,
            String snapshotHash) {}

    public record HistoricalEvidenceRef(
            UUID evidenceId,
            String sourceType,
            String evidenceCategory,
            EvidenceVerificationStatus currentStatus,
            boolean sourceDeleted,
            String claimText,
            String usageType) {}

    public record ProvenanceContextOutput(
            String schemaVersion,
            UUID answerVersionId,
            List<HistoricalEvidenceRef> historicalEvidence,
            List<UUID> currentVerifiedEvidenceIds,
            List<UUID> sourceDeletedEvidenceIds,
            String snapshotHash) {
        public ProvenanceContextOutput {
            historicalEvidence = copy(historicalEvidence);
            currentVerifiedEvidenceIds = copy(currentVerifiedEvidenceIds);
            sourceDeletedEvidenceIds = copy(sourceDeletedEvidenceIds);
        }
    }

    public record ApprovedEvidenceInput(
            UUID id,
            String sourceType,
            String evidenceCategory,
            String title,
            String content,
            long version) {}

    public record CheckFactsInput(
            String schemaVersion,
            UUID answerVersionId,
            String answerText,
            List<HistoricalEvidenceRef> historicalEvidence,
            List<ApprovedEvidenceInput> currentVerifiedEvidence,
            String companyName,
            String positionName) {
        public CheckFactsInput {
            historicalEvidence = copy(historicalEvidence);
            currentVerifiedEvidence = copy(currentVerifiedEvidence);
        }
    }

    public record CheckFactsInputV2(
            String schemaVersion,
            UUID answerVersionId,
            String answerText,
            List<HistoricalEvidenceRef> historicalEvidence,
            List<ApprovedEvidenceInput> currentVerifiedEvidence,
            int omittedCurrentVerifiedEvidenceCount,
            String companyName,
            String positionName) {
        public CheckFactsInputV2 {
            historicalEvidence = copy(historicalEvidence);
            currentVerifiedEvidence = copy(currentVerifiedEvidence);
        }
    }

    public record CheckFactsInputV3(
            String schemaVersion,
            String outputLocale,
            UUID answerVersionId,
            BoundedText answer,
            List<HistoricalEvidenceRef> historicalEvidence,
            List<ApprovedEvidenceInput> currentVerifiedEvidence,
            int omittedCurrentVerifiedEvidenceCount,
            String evidenceSelectionPolicyVersion,
            String companyName,
            String positionName) {
        public CheckFactsInputV3 {
            historicalEvidence = copy(historicalEvidence);
            currentVerifiedEvidence = copy(currentVerifiedEvidence);
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

    public record FactCheckOutput(
            String schemaVersion,
            UUID answerVersionId,
            List<VerificationIssueDraft> issues,
            List<String> suggestions,
            List<VerifiedClaimDraft> verifiedClaims) {
        public FactCheckOutput {
            issues = copy(issues);
            suggestions = copy(suggestions);
            verifiedClaims = copy(verifiedClaims);
        }
    }

    public record RequirementInput(
            String category, String text, boolean required) {}

    public record CheckRequirementsInput(
            String schemaVersion,
            UUID answerVersionId,
            String questionText,
            String answerText,
            int characterCount,
            Integer maxLength,
            List<RequirementInput> jobRequirements,
            boolean analysisOutdated) {
        public CheckRequirementsInput {
            jobRequirements = copy(jobRequirements);
        }
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

    public record SiblingAnswerInput(
            UUID questionId,
            String questionText,
            Integer maxLength,
            String boundedPlainText,
            int characterCount) {}

    public record CheckRequirementsInputV2(
            String schemaVersion,
            UUID answerVersionId,
            String questionText,
            String answerText,
            int characterCount,
            Integer maxLength,
            JobWritingContextInput job,
            List<RequirementInput> jobRequirements,
            List<SiblingAnswerInput> siblingAnswers,
            String writingQualityRubricVersion) {
        public CheckRequirementsInputV2 {
            jobRequirements = copy(jobRequirements);
            siblingAnswers = copy(siblingAnswers);
        }
    }

    public record SiblingAnswerInputV3(
            UUID questionId,
            String questionText,
            Integer maxLength,
            BoundedText answer) {}

    public record CheckRequirementsInputV3(
            String schemaVersion,
            String outputLocale,
            UUID answerVersionId,
            String questionText,
            BoundedText answer,
            int characterCount,
            Integer maxLength,
            JobWritingContextInput job,
            List<RequirementInput> jobRequirements,
            List<SiblingAnswerInputV3> siblingAnswers,
            String writingQualityRubricVersion,
            String duplicationPolicyVersion) {
        public CheckRequirementsInputV3 {
            jobRequirements = copy(jobRequirements);
            siblingAnswers = copy(siblingAnswers);
        }
    }

    public record RequirementCheckOutput(
            String schemaVersion,
            UUID answerVersionId,
            List<VerificationIssueDraft> issues,
            List<String> suggestions) {
        public RequirementCheckOutput {
            issues = copy(issues);
            suggestions = copy(suggestions);
        }
    }

    public record AggregateVerificationInput(
            String schemaVersion,
            UUID answerVersionId,
            String factsHash,
            String requirementsHash) {}

    public record AggregatedVerificationOutput(
            String schemaVersion,
            UUID answerVersionId,
            VerificationStatus status,
            List<VerificationIssueDraft> issues,
            List<String> suggestions,
            List<VerifiedClaimDraft> verifiedClaims,
            String snapshotHash) {
        public AggregatedVerificationOutput {
            issues = copy(issues);
            suggestions = copy(suggestions);
            verifiedClaims = copy(verifiedClaims);
        }
    }

    public record PersistVerificationInput(
            String schemaVersion,
            UUID verificationId,
            UUID answerVersionId,
            String resultHash) {}

    public record PersistVerificationRequestOutput(
            String schemaVersion,
            UUID verificationId,
            UUID answerVersionId,
            String resultHash) {}

    public record PersistedVerificationOutput(
            String schemaVersion,
            UUID verificationId,
            UUID answerVersionId,
            VerificationStatus status,
            String snapshotHash) {}

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
