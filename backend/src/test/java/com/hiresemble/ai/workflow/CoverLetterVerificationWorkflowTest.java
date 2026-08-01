package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.context.ContextBuilder.TruncationSummary;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.prompt.CoverLetterVerificationPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.AggregatedVerificationOutput;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.CheckFactsInput;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.CheckRequirementsInput;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.FactCheckOutput;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.RequirementCheckOutput;
import com.hiresemble.ai.workflow.CoverLetterVerificationWorkflow.VerifiedClaimDraft;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AppliedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.HistoricalEvidence;
import com.hiresemble.coverletter.application.model.CoverLetterModels.JobContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistVerification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Question;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RequirementContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedEvidence;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterEvidenceUsageType;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CoverLetterVerificationWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
    private static final String SNAPSHOT_HASH = "b".repeat(64);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredOutputValidator validator =
            new StructuredOutputValidator(objectMapper);
    private final PromptRegistry prompts =
            new PromptRegistry(CoverLetterVerificationPromptDefinitions.all());

    @Test
    void verifiesImmutableVersionAndAddsCurrentEvidenceStateWarnings() {
        Fixture fixture = fixture();
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        List<String> steps = new ArrayList<>();

        for (ExecutableWorkflowStep step :
                fixture.workflow.contribution().steps()) {
            StepExecutionContext context =
                    context(fixture.run, upstream, ephemeral);
            var inputs = step.executor().prepareInputs(context);
            assertThat(inputs).singleElement();
            assertThat(inputs.getFirst().scopeKey()).isNull();
            AiGatewayResponse response = step.executor().invoke(new GatewayInvocation(
                    inputs.getFirst(),
                    new ModelRoute(
                            1L, ModelTier.BALANCED, "fake", "fake", false),
                    prompts.require(
                            WorkflowType.COVER_LETTER_VERIFICATION,
                            CanonicalWorkflowDefinitions
                                    .COVER_LETTER_VERIFICATION_VERSION,
                            step.stepKey()),
                    fixture.chat,
                    request -> {
                        throw new AssertionError("embedding is not allowed");
                    },
                    request -> {
                        throw new AssertionError("web search is not allowed");
                    },
                    context));
            Object output =
                    validate(step.executor(), response.rawJson(), context);
            JsonNode minimal = minimal(step.executor(), output);
            if (step.stepKey().equals(
                    CoverLetterVerificationWorkflow.BUILD_PROVENANCE_CONTEXT)) {
                assertThat(step.executor().reusable()).isFalse();
                assertThat(minimal.toString())
                        .doesNotContain(
                                "거절된 근거",
                                "삭제된 근거",
                                "과거 경력 근거",
                                "삭제된 원본 근거");
                assertThat(minimal.path("historicalEvidenceCount").asInt())
                        .isEqualTo(2);
            }
            DomainStepCompletion completion =
                    complete(step.executor(), output, minimal, context);
            upstream.put(step.stepKey(), completion.minimalOutput());
            ephemeral.put(
                    step.stepKey(), ephemeral(step.executor(), output));
            steps.add(step.stepKey());
        }

        assertThat(steps).containsExactly(
                CoverLetterVerificationWorkflow.LOAD_ANSWER_VERSION,
                CoverLetterVerificationWorkflow.BUILD_PROVENANCE_CONTEXT,
                CoverLetterVerificationWorkflow.CHECK_FACTS,
                CoverLetterVerificationWorkflow.CHECK_REQUIREMENTS_AND_LENGTH,
                CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION,
                CoverLetterVerificationWorkflow.PERSIST_VERIFICATION);
        assertThat(fixture.chat.calls).hasValue(2);
        assertThat(fixture.command.persisted).isNotNull();
        assertThat(fixture.command.persisted.verificationId())
                .isEqualTo(fixture.verificationId);
        assertThat(fixture.command.persisted.answerVersionId())
                .isEqualTo(fixture.snapshot.answerVersion().id());
        assertThat(fixture.command.persisted.expectedSnapshotHash())
                .isEqualTo(SNAPSHOT_HASH);
        assertThat(fixture.command.persisted.result().status())
                .isEqualTo(VerificationStatus.WARNING);
        assertThat(fixture.command.persisted.result().issues())
                .extracting(value -> value.code())
                .containsExactlyInAnyOrder(
                        VerificationIssueCode.UNVERIFIED_CLAIM,
                        VerificationIssueCode.SOURCE_DELETED);
        assertThat(fixture.command.persisted.result().verifiedClaims())
                .singleElement()
                .satisfies(claim -> assertThat(claim.evidenceIds())
                        .containsExactly(fixture.currentVerifiedId));
    }

    @Test
    void failureHandlerCompensatesPendingVerification() {
        Fixture fixture = fixture();
        CoverLetterVerificationFailureHandler handler =
                new CoverLetterVerificationFailureHandler(fixture.command);

        assertThat(handler.supports(fixture.run)).isTrue();
        handler.onFailure(
                fixture.run,
                AiExecutionException.nonRetryable(
                        FailureKind.DOMAIN_VALIDATION, "TEST", "test"));

        assertThat(fixture.command.failedRuns)
                .containsExactly(fixture.run.id());
    }

    @Test
    void verificationSuggestionsHonorTwentyByOneThousandBoundaryBeforePersist() {
        Fixture fixture = fixture();
        StepExecutionContext context =
                context(fixture.run, new HashMap<>(), new HashMap<>());
        ExecutableWorkflowStep factCheck =
                step(fixture.workflow, CoverLetterVerificationWorkflow.CHECK_FACTS);
        ExecutableWorkflowStep aggregate = step(
                fixture.workflow,
                CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION);

        FactCheckOutput validCheck = factCheckOutput(
                fixture, suggestions(20, 1_000));
        assertThatCode(() -> validate(
                        factCheck.executor(),
                        objectMapper.writeValueAsString(validCheck),
                        context))
                .doesNotThrowAnyException();
        assertStructuredOutputFailure(() -> validate(
                factCheck.executor(),
                objectMapper.writeValueAsString(
                        factCheckOutput(fixture, suggestions(21, 1_000))),
                context));
        assertStructuredOutputFailure(() -> validate(
                factCheck.executor(),
                objectMapper.writeValueAsString(
                        factCheckOutput(fixture, suggestions(1, 1_001))),
                context));

        AggregatedVerificationOutput validAggregate = aggregateOutput(
                fixture, suggestions(20, 1_000));
        assertThatCode(() -> validate(
                        aggregate.executor(),
                        objectMapper.writeValueAsString(validAggregate),
                        context))
                .doesNotThrowAnyException();
        assertStructuredOutputFailure(() -> validate(
                aggregate.executor(),
                objectMapper.writeValueAsString(
                        aggregateOutput(fixture, suggestions(21, 1_000))),
                context));
        assertStructuredOutputFailure(() -> validate(
                aggregate.executor(),
                objectMapper.writeValueAsString(
                        aggregateOutput(fixture, suggestions(1, 1_001))),
                context));

        assertThat(fixture.command.persisted).isNull();
    }

    @Test
    void aggregateCapsDistinctValidStepSuggestionsAtPublicContractMaximum() {
        Fixture fixture = fixture();
        List<String> factSuggestions = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> "fact suggestion " + index)
                .toList();
        List<String> requirementSuggestions =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> "requirement suggestion " + index)
                        .toList();
        Map<String, Object> ephemeral = new HashMap<>();
        ephemeral.put(
                CoverLetterVerificationWorkflow.CHECK_FACTS,
                factCheckOutput(fixture, factSuggestions));
        ephemeral.put(
                CoverLetterVerificationWorkflow.CHECK_REQUIREMENTS_AND_LENGTH,
                new RequirementCheckOutput(
                        "cover-verification-requirements-output-v1",
                        fixture.snapshot.answerVersion().id(),
                        List.of(),
                        requirementSuggestions));
        StepExecutionContext context =
                context(fixture.run, new HashMap<>(), ephemeral);
        ExecutableWorkflowStep aggregate = step(
                fixture.workflow,
                CoverLetterVerificationWorkflow.AGGREGATE_VERIFICATION);
        var inputs = aggregate.executor().prepareInputs(context);

        AiGatewayResponse response = aggregate.executor().invoke(
                new GatewayInvocation(
                        inputs.getFirst(),
                        new ModelRoute(
                                1L,
                                ModelTier.BALANCED,
                                "fake",
                                "fake",
                                false),
                        prompts.require(
                                WorkflowType.COVER_LETTER_VERIFICATION,
                                CanonicalWorkflowDefinitions
                                        .COVER_LETTER_VERIFICATION_VERSION,
                                aggregate.stepKey()),
                        fixture.chat,
                        request -> {
                            throw new AssertionError("embedding is not allowed");
                        },
                        request -> {
                            throw new AssertionError("web search is not allowed");
                        },
                        context));
        AggregatedVerificationOutput output =
                (AggregatedVerificationOutput) validate(
                        aggregate.executor(), response.rawJson(), context);

        assertThat(output.suggestions())
                .hasSize(20)
                .containsExactlyElementsOf(factSuggestions);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object validate(
            WorkflowStepExecutor executor,
            String rawJson,
            StepExecutionContext context) {
        return validator.validate(rawJson, executor.outputContract(context));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private JsonNode minimal(WorkflowStepExecutor executor, Object output) {
        return executor.minimalOutput(output, objectMapper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object ephemeral(WorkflowStepExecutor executor, Object output) {
        return executor.ephemeralOutput(output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private DomainStepCompletion complete(
            WorkflowStepExecutor executor,
            Object output,
            JsonNode minimal,
            StepExecutionContext context) {
        return executor.completeFresh(output, minimal, context);
    }

    private StepExecutionContext context(
            AgentRunSnapshot run,
            Map<String, JsonNode> upstream,
            Map<String, Object> ephemeral) {
        return new StepExecutionContext(
                run,
                new ContextSnapshot(
                        run.userId(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new TruncationSummary(0, 0, List.of()),
                        SNAPSHOT_HASH,
                        "HISTORICAL_PROVENANCE_WITH_CURRENT_STATUS",
                        1L,
                        true,
                        true),
                upstream,
                ephemeral);
    }

    private ExecutableWorkflowStep step(
            CoverLetterVerificationWorkflow workflow, String key) {
        return workflow.contribution().steps().stream()
                .filter(value -> value.stepKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private FactCheckOutput factCheckOutput(
            Fixture fixture, List<String> suggestions) {
        return new FactCheckOutput(
                "cover-verification-facts-output-v1",
                fixture.snapshot.answerVersion().id(),
                List.of(),
                suggestions,
                List.of(new VerifiedClaimDraft(
                        "supported claim",
                        true,
                        List.of(fixture.currentVerifiedId))));
    }

    private AggregatedVerificationOutput aggregateOutput(
            Fixture fixture, List<String> suggestions) {
        return new AggregatedVerificationOutput(
                "cover-verification-aggregate-output-v1",
                fixture.snapshot.answerVersion().id(),
                VerificationStatus.PASSED,
                List.of(),
                suggestions,
                List.of(new VerifiedClaimDraft(
                        "supported claim",
                        true,
                        List.of(fixture.currentVerifiedId))),
                SNAPSHOT_HASH);
    }

    private List<String> suggestions(int count, int length) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> "s".repeat(length))
                .toList();
    }

    private void assertStructuredOutputFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.failureKind())
                            .isEqualTo(FailureKind.STRUCTURED_OUTPUT);
                });
    }

    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        UUID coverLetterId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerVersionId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        UUID currentVerifiedId = UUID.randomUUID();
        TipTapDocumentDto content = document("Spring 서비스 성과를 만들었습니다.");
        AnswerVersion answer = new AnswerVersion(
                answerVersionId,
                userId,
                questionId,
                null,
                null,
                2,
                content,
                "Spring 서비스 성과를 만들었습니다.",
                21,
                CoverLetterVersionSource.USER_EDITED,
                true,
                AnswerCreatedBy.USER,
                NOW);
        Question question = new Question(
                questionId,
                userId,
                coverLetterId,
                1,
                "직무 경험을 작성하세요.",
                500,
                null,
                answer,
                null,
                3L,
                NOW,
                NOW,
                null);
        VerificationSnapshot snapshot = new VerificationSnapshot(
                userId,
                coverLetterId,
                9L,
                question,
                answer,
                new JobContext(
                        UUID.randomUUID(),
                        4L,
                        "Hiresemble",
                        "Backend Engineer",
                        "Backend Engineer",
                        "Spring 기반 서비스 개발",
                        UUID.randomUUID(),
                        2,
                        false,
                        List.of(new RequirementContext(
                                "RESPONSIBILITY",
                                "Spring 서비스 개발",
                                true,
                                "requirements"))),
                List.of(
                        new HistoricalEvidence(
                                rejectedId,
                                "거절된 근거",
                                "CAREER",
                                EvidenceSourceType.MANUAL,
                                EvidenceVerificationStatus.REJECTED,
                                false,
                                "과거 경력 근거",
                                CoverLetterEvidenceUsageType.SUPPORTING_CLAIM),
                        new HistoricalEvidence(
                                deletedId,
                                "삭제된 근거",
                                "CAREER",
                                EvidenceSourceType.DOCUMENT_CHUNK,
                                EvidenceVerificationStatus.SOURCE_DELETED,
                                true,
                                "삭제된 원본 근거",
                                CoverLetterEvidenceUsageType.SUPPORTING_CLAIM)),
                List.of(new VerifiedEvidence(
                        currentVerifiedId,
                        EvidenceSourceType.MANUAL,
                        UUID.randomUUID(),
                        null,
                        "CAREER",
                        "현재 승인 근거",
                        "Spring 서비스 성과를 만들었습니다.",
                        5L)),
                AiQualityMode.BALANCED,
                SNAPSHOT_HASH);
        FakeQuery query = new FakeQuery(snapshot);
        FakeCommand command = new FakeCommand(userId, snapshot);
        AgentRunSnapshot run =
                run(snapshot, verificationId, UUID.randomUUID());
        return new Fixture(
                snapshot,
                verificationId,
                currentVerifiedId,
                command,
                new FakeChat(objectMapper, currentVerifiedId),
                new CoverLetterVerificationWorkflow(
                        query, command, objectMapper),
                run);
    }

    private AgentRunSnapshot run(
            VerificationSnapshot snapshot,
            UUID verificationId,
            UUID runId) {
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", snapshot.coverLetterId().toString())
                .put(
                        "answerVersionId",
                        snapshot.answerVersion().id().toString())
                .put("verificationId", verificationId.toString())
                .put("snapshotHash", snapshot.snapshotHash())
                .put("qualityMode", snapshot.qualityMode().name());
        return new AgentRunSnapshot(
                runId,
                snapshot.userId(),
                WorkflowType.COVER_LETTER_VERIFICATION,
                AgentRunStatus.RUNNING,
                null,
                0,
                CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION,
                "f".repeat(64),
                input,
                1L,
                1L,
                snapshot.qualityMode(),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "COVER_LETTER",
                snapshot.coverLetterId(),
                null,
                runId,
                1,
                false,
                null,
                null,
                UUID.randomUUID(),
                "test-worker",
                NOW.plusSeconds(60),
                NOW,
                null,
                null,
                1L,
                NOW,
                NOW,
                null,
                NOW,
                List.of());
    }

    private TipTapDocumentDto document(String text) {
        return new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(new TipTapNodeDto(
                                "text", text, List.of(), List.of())))));
    }

    private record Fixture(
            VerificationSnapshot snapshot,
            UUID verificationId,
            UUID currentVerifiedId,
            FakeCommand command,
            FakeChat chat,
            CoverLetterVerificationWorkflow workflow,
            AgentRunSnapshot run) {}

    private static final class FakeChat implements ChatGateway {

        private final ObjectMapper mapper;
        private final UUID currentVerifiedId;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeChat(
                ObjectMapper mapper, UUID currentVerifiedId) {
            this.mapper = mapper;
            this.currentVerifiedId = currentVerifiedId;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls.incrementAndGet();
            Object output = switch (request.outputSchemaVersion()) {
                case "cover-verification-facts-output-v1" -> {
                    CheckFactsInput input =
                            mapper.treeToValue(request.input(), CheckFactsInput.class);
                    yield new FactCheckOutput(
                            "cover-verification-facts-output-v1",
                            input.answerVersionId(),
                            List.of(),
                            List.of(),
                            List.of(new VerifiedClaimDraft(
                                    "Spring 서비스 성과를 만들었습니다.",
                                    true,
                                    List.of(currentVerifiedId))));
                }
                case "cover-verification-requirements-output-v1" -> {
                    CheckRequirementsInput input = mapper.treeToValue(
                            request.input(), CheckRequirementsInput.class);
                    yield new RequirementCheckOutput(
                            "cover-verification-requirements-output-v1",
                            input.answerVersionId(),
                            List.of(),
                            List.of());
                }
                default -> throw new AssertionError(
                        "unexpected chat schema " + request.outputSchemaVersion());
            };
            return new AiGatewayResponse(mapper.writeValueAsString(output), java.util.List.of());
        }
    }

    private static final class FakeQuery implements CoverLetterQueryPort {

        private final VerificationSnapshot snapshot;

        private FakeQuery(VerificationSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public GenerationSnapshot loadGenerationSnapshot(
                UUID userId,
                UUID coverLetterId,
                long expectedCoverLetterVersion,
                List<UUID> questionIds,
                List<UUID> preferredEvidenceIds,
                boolean avoidExperienceDuplication,
                AiQualityMode qualityMode,
                String expectedSnapshotHash) {
            throw new AssertionError("generation is not used");
        }

        @Override
        public GenerationSnapshot loadGenerationRetrySnapshot(
                UUID userId, UUID agentRunId, String expectedSnapshotHash) {
            throw new AssertionError("generation is not used");
        }

        @Override
        public VerificationSnapshot loadVerificationSnapshot(
                UUID userId,
                UUID answerVersionId,
                AiQualityMode qualityMode,
                String expectedSnapshotHash) {
            return snapshot;
        }

        @Override
        public VerificationSnapshot loadVerificationRetrySnapshot(
                UUID userId, UUID agentRunId, String expectedSnapshotHash) {
            throw new AssertionError("retry is not used");
        }

        @Override
        public List<CandidateChunk> searchEvidenceCandidates(
                UUID userId, List<Double> queryVector, int limit) {
            throw new AssertionError("retrieval is not used");
        }
    }

    private static final class FakeCommand implements CoverLetterCommandPort {

        private final UUID userId;
        private final VerificationSnapshot snapshot;
        private final List<UUID> failedRuns = new ArrayList<>();
        private PersistVerification persisted;

        private FakeCommand(
                UUID userId, VerificationSnapshot snapshot) {
            this.userId = userId;
            this.snapshot = snapshot;
        }

        @Override
        public AppliedAnswer applyGeneratedAnswer(
                UUID userId,
                UUID agentRunId,
                PersistGeneratedAnswer command) {
            throw new AssertionError("generation is not used");
        }

        @Override
        public Verification persistVerification(
                UUID ownerId,
                UUID agentRunId,
                PersistVerification command) {
            assertThat(ownerId).isEqualTo(userId);
            persisted = command;
            return new Verification(
                    command.verificationId(),
                    userId,
                    command.answerVersionId(),
                    command.result().status(),
                    command.result().issues(),
                    command.result().suggestions(),
                    command.result().verifiedClaims(),
                    List.of(),
                    agentRunId,
                    NOW);
        }

        @Override
        public void failPendingVerification(
                UUID ownerId, UUID agentRunId) {
            assertThat(ownerId).isEqualTo(userId);
            assertThat(snapshot.userId()).isEqualTo(ownerId);
            failedRuns.add(agentRunId);
        }
    }
}
