package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.prompt.InterviewAnswerFeedbackPromptDefinitions;
import com.hiresemble.ai.prompt.InterviewPreparationPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.InterviewAnswerFeedbackWorkflow.AnalyzeFeedbackInput;
import com.hiresemble.ai.workflow.InterviewAnswerFeedbackWorkflow.AnalyzeFeedbackOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.GenerateQuestionsInput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.GeneratedQuestionDraft;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.GeneratedQuestionsOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchBatchOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchHit;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchPurpose;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.interview.application.model.InterviewModels.CoverAnswerContext;
import com.hiresemble.interview.application.model.InterviewModels.EvidenceContext;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackContext;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackResult;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackRow;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackScore;
import com.hiresemble.interview.application.model.InterviewModels.FinalEducationContext;
import com.hiresemble.interview.application.model.InterviewModels.GeneratedQuestion;
import com.hiresemble.interview.application.model.InterviewModels.PreparationContext;
import com.hiresemble.interview.application.model.InterviewModels.StructuredProfileContext;
import com.hiresemble.interview.application.port.InterviewWorkflowCommandPort;
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.research.application.model.ResearchModels.ResearchResult;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.ResearchRunStatus;
import com.hiresemble.research.domain.SourceCoverage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InterviewWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final String CONTEXT_HASH = "f".repeat(64);
    private static final String PRIVATE_ANSWER =
            "Private cover-letter achievement 010-1234-5678";
    private static final String PRIVATE_EVIDENCE =
            "Private evidence candidate@example.com";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredOutputValidator validator =
            new StructuredOutputValidator(objectMapper);
    private final PromptRegistry prompts = new PromptRegistry(
            java.util.stream.Stream.concat(
                            InterviewPreparationPromptDefinitions.all().stream(),
                            InterviewAnswerFeedbackPromptDefinitions.all().stream())
                    .toList());

    @Test
    void basicPreparationPersistsSufficientDedupedSourcesWithPublicQueriesOnly() {
        PreparationFixture fixture = preparationFixture(
                ResearchQuality.BASIC,
                SearchMode.SUFFICIENT,
                QuestionMode.VALID);

        Execution execution = execute(
                fixture.workflow().contribution(),
                fixture.run(),
                fixture.chat(),
                fixture.search(),
                fixture.command());

        assertThat(fixture.search().requests).hasSize(2);
        assertThat(fixture.search().requests)
                .allSatisfy(request -> {
                    assertThat(request.maxResultsPerQuery()).isEqualTo(5);
                    assertThat(request.queries()).hasSize(1);
                    assertThat(String.join(" ", request.queries()))
                            .doesNotContain(
                                    PRIVATE_ANSWER,
                                    PRIVATE_EVIDENCE,
                                    "candidate@example.com",
                                    "010-1234-5678",
                                    fixture.run().id().toString(),
                                    CONTEXT_HASH);
                });
        assertThat(fixture.search().requests.stream()
                        .flatMap(request -> request.queries().stream()))
                .hasSize(2);
        assertThat(fixture.command().markRunningCount).isEqualTo(1);
        assertThat(fixture.command().research).isNotNull();
        assertThat(fixture.command().research.coverage())
                .isEqualTo(SourceCoverage.SUFFICIENT);
        assertThat(fixture.command().research.sources()).hasSize(3);
        assertThat(fixture.command().research.sources())
                .extracting(source -> source.sourceUrl())
                .doesNotHaveDuplicates();
        assertThat(fixture.command().questions).hasSize(2);
        assertThat(fixture.command().questions)
                .allSatisfy(question -> {
                    assertThat(question.evidenceIds()).containsExactly(fixture.evidenceId());
                    assertThat(question.sourceIds()).hasSize(1);
                });

        GenerateQuestionsInput captured = fixture.chat().lastQuestionInput;
        assertThat(captured.context().finalEducation()).isNotNull();
        assertThat(captured.context().finalEducation().educationLevel())
                .isEqualTo("BACHELOR");
        assertThat(captured.context().evidence())
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.id()).isEqualTo(fixture.evidenceId());
                    assertThat(evidence.sourceType()).isEqualTo("MANUAL");
                    assertThat(evidence.verificationStatus()).isEqualTo("VERIFIED");
                });

        String checkpoints = execution.upstream().toString();
        assertThat(checkpoints)
                .doesNotContain(
                        PRIVATE_ANSWER,
                        PRIVATE_EVIDENCE,
                        "candidate@example.com",
                        "010-1234-5678",
                        "Official snippet",
                        "Review snippet",
                        "system message",
                        "prompt");
    }

    @Test
    void coverageTreatsLimitedAndNoneAsSuccessButEveryProviderFailureAsFailed() {
        for (var scenario : List.of(
                Map.entry(SearchMode.LIMITED, SourceCoverage.LIMITED),
                Map.entry(SearchMode.NONE, SourceCoverage.NONE))) {
            PreparationFixture fixture = preparationFixture(
                    ResearchQuality.BASIC, scenario.getKey(), QuestionMode.VALID);
            execute(
                    fixture.workflow().contribution(),
                    fixture.run(),
                    fixture.chat(),
                    fixture.search(),
                    fixture.command());
            assertThat(fixture.command().research.coverage())
                    .isEqualTo(scenario.getValue());
            if (scenario.getValue() == SourceCoverage.NONE) {
                assertThat(fixture.command().questions)
                        .allSatisfy(question -> assertThat(question.sourceBased()).isFalse());
            }
        }

        PreparationFixture failed = preparationFixture(
                ResearchQuality.BASIC, SearchMode.FAIL, QuestionMode.VALID);
        assertThatThrownBy(() -> execute(
                        failed.workflow().contribution(),
                        failed.run(),
                        failed.chat(),
                        failed.search(),
                        failed.command()))
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.PROVIDER_5XX);
                    assertThat(failure.safeCode())
                            .isEqualTo("INTERVIEW_RESEARCH_PROVIDER_UNAVAILABLE");
                });
        assertThat(failed.command().research).isNull();
        assertThat(failed.command().questions).isNull();
    }

    @Test
    void advancedUsesFourQueriesAndHallucinatedOrUnrequestedProvenanceIsRejected() {
        PreparationFixture advanced = preparationFixture(
                ResearchQuality.ADVANCED,
                SearchMode.SUFFICIENT,
                QuestionMode.VALID);
        execute(
                advanced.workflow().contribution(),
                advanced.run(),
                advanced.chat(),
                advanced.search(),
                advanced.command());
        assertThat(advanced.search().requests).hasSize(2);
        assertThat(advanced.search().requests)
                .allSatisfy(request -> {
                    assertThat(request.queries()).hasSize(2);
                    assertThat(request.maxResultsPerQuery()).isEqualTo(8);
                });
        assertThat(advanced.search().requests.stream()
                        .flatMap(request -> request.queries().stream()))
                .hasSize(4);

        for (QuestionMode invalid :
                List.of(QuestionMode.HALLUCINATED_SOURCE, QuestionMode.UNREQUESTED_TYPE)) {
            PreparationFixture fixture = preparationFixture(
                    ResearchQuality.BASIC, SearchMode.SUFFICIENT, invalid);
            assertThatThrownBy(() -> execute(
                            fixture.workflow().contribution(),
                            fixture.run(),
                            fixture.chat(),
                            fixture.search(),
                            fixture.command()))
                    .isInstanceOf(AiExecutionException.class)
                    .satisfies(error -> assertThat(
                                    ((AiExecutionException) error).failureKind())
                            .isEqualTo(FailureKind.STRUCTURED_OUTPUT));
            assertThat(fixture.command().research).isNull();
        }

        PreparationFixture outputFollowUp = preparationFixture(
                ResearchQuality.BASIC,
                SearchMode.SUFFICIENT,
                QuestionMode.OUTPUT_FOLLOW_UP);
        execute(
                outputFollowUp.workflow().contribution(),
                outputFollowUp.run(),
                outputFollowUp.chat(),
                outputFollowUp.search(),
                outputFollowUp.command());
        assertThat(outputFollowUp.command().questions)
                .extracting(GeneratedQuestion::questionType)
                .contains(InterviewQuestionType.FOLLOW_UP);
    }

    @Test
    void feedbackAcceptsExactUpperBoundsPersistsOneVersionAndRejectsOverflow() {
        FeedbackFixture valid = feedbackFixture(FeedbackMode.VALID_BOUNDARY);
        Execution execution = execute(
                valid.workflow().contribution(),
                valid.run(),
                valid.chat(),
                request -> {
                    throw new AssertionError("feedback must not search");
                },
                valid.command());

        assertThat(valid.command().feedback).isNotNull();
        assertThat(valid.command().feedbackAnswerVersionId)
                .isEqualTo(valid.context().answerVersionId());
        assertThat(valid.command().feedback.scores()).hasSize(20);
        assertThat(valid.command().feedback.strengths()).hasSize(20);
        assertThat(valid.command().feedback.strengths())
                .allSatisfy(value -> assertThat(value).hasSize(1_000));
        assertThat(valid.command().feedback.revisedExample()).hasSize(10_000);
        assertThat(execution.upstream().toString())
                .doesNotContain(
                        valid.context().answerContent(),
                        "Detailed immutable answer",
                        "provider response",
                        "prompt");

        for (FeedbackMode invalid :
                List.of(FeedbackMode.TOO_MANY, FeedbackMode.TOO_LONG)) {
            FeedbackFixture fixture = feedbackFixture(invalid);
            assertThatThrownBy(() -> execute(
                            fixture.workflow().contribution(),
                            fixture.run(),
                            fixture.chat(),
                            request -> {
                                throw new AssertionError("feedback must not search");
                            },
                            fixture.command()))
                    .isInstanceOf(AiExecutionException.class)
                    .satisfies(error -> assertThat(
                                    ((AiExecutionException) error).failureKind())
                            .isEqualTo(FailureKind.STRUCTURED_OUTPUT));
            assertThat(fixture.command().feedback).isNull();
        }
    }

    private Execution execute(
            ExecutableWorkflowContribution workflow,
            AgentRunSnapshot run,
            ChatGateway chat,
            WebSearchGateway search,
            FakeCommand command) {
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        for (ExecutableWorkflowStep step : workflow.steps()) {
            StepExecutionContext preparation =
                    context(run, upstream, ephemeral);
            List<StepInput> inputs = step.executor().prepareInputs(preparation);
            assertThat(inputs).as(step.stepKey()).hasSize(1);
            StepInput input = inputs.getFirst();
            StepExecutionContext scoped = input.scopeKey() == null
                    ? preparation
                    : preparation.forScope(input.scopeKey());
            AiGatewayResponse response = step.executor().invoke(new GatewayInvocation(
                    input,
                    new ModelRoute(1L, ModelTier.BALANCED, "fake", "fake", false),
                    prompts.require(run.workflowType(), run.workflowVersion(), step.stepKey()),
                    chat,
                    embeddingDisabled(),
                    search,
                    scoped));
            Object output = validate(step.executor(), response.rawJson(), scoped);
            JsonNode minimal = minimal(step.executor(), output);
            DomainStepCompletion completion =
                    complete(step.executor(), output, minimal, scoped);
            String outputKey =
                    StepExecutionContext.outputKey(step.stepKey(), input.scopeKey());
            upstream.put(outputKey, completion.minimalOutput());
            ephemeral.put(outputKey, ephemeral(step.executor(), output));
        }
        return new Execution(upstream, ephemeral);
    }

    private PreparationFixture preparationFixture(
            ResearchQuality quality, SearchMode searchMode, QuestionMode questionMode) {
        UUID user = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID analysis = UUID.randomUUID();
        UUID cover = UUID.randomUUID();
        UUID coverQuestion = UUID.randomUUID();
        UUID coverAnswer = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        UUID research = UUID.randomUUID();
        UUID questionSet = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        PreparationContext context = new PreparationContext(
                user,
                job,
                3,
                "Hiresemble",
                "Backend Engineer",
                "Backend Engineer",
                "BACKEND",
                "Build secure services.",
                analysis,
                "Java and Spring role.",
                cover,
                "Application",
                CoverLetterStatus.DRAFT,
                List.of(new CoverAnswerContext(
                        coverQuestion, "Why this role?", coverAnswer, PRIVATE_ANSWER)),
                new StructuredProfileContext(
                        "Profile introduction candidate@example.com",
                        List.of("Backend Engineer"),
                        List.of("Software"),
                        List.of("Seoul"),
                        new FinalEducationContext(
                                UUID.randomUUID(),
                                "Example University",
                                "Computer Science",
                                "Bachelor",
                                EducationLevel.BACHELOR,
                                EducationStatus.GRADUATED,
                                LocalDate.of(2025, 2, 1))),
                List.of(new EvidenceContext(
                        evidence,
                        EvidenceSourceType.MANUAL,
                        "CAREER",
                        "Verified project",
                        PRIVATE_EVIDENCE,
                        EvidenceVerificationStatus.VERIFIED)));
        ResearchRunRow researchRow = new ResearchRunRow(
                research,
                user,
                job,
                cover,
                null,
                quality,
                ResearchRunStatus.QUEUED,
                null,
                List.of(),
                null,
                runId,
                false,
                null,
                NOW,
                null,
                null,
                NOW);
        FakeQuery query = new FakeQuery(context, researchRow, null);
        FakeCommand command = new FakeCommand();
        FakeSearch search = new FakeSearch(searchMode, objectMapper);
        FakeChat chat = new FakeChat(objectMapper, questionMode, null);
        InterviewPreparationWorkflow workflow = new InterviewPreparationWorkflow(
                query,
                command,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
        AgentRunSnapshot run = preparationRun(
                user, runId, job, cover, research, questionSet, quality);
        return new PreparationFixture(
                workflow, run, chat, search, command, evidence);
    }

    private FeedbackFixture feedbackFixture(FeedbackMode mode) {
        UUID user = UUID.randomUUID();
        UUID answer = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        FeedbackContext context = new FeedbackContext(
                user,
                answer,
                question,
                "Explain your system design.",
                "Assess trade-offs.",
                List.of("clarity", "accuracy"),
                "Use a concise structure.",
                "Detailed immutable answer content.",
                UUID.randomUUID(),
                "Hiresemble",
                "Backend Engineer",
                UUID.randomUUID());
        FakeQuery query = new FakeQuery(null, null, context);
        FakeCommand command = new FakeCommand();
        FakeChat chat = new FakeChat(objectMapper, null, mode);
        InterviewAnswerFeedbackWorkflow workflow =
                new InterviewAnswerFeedbackWorkflow(query, command, objectMapper);
        AgentRunSnapshot run = feedbackRun(user, runId, context);
        return new FeedbackFixture(workflow, run, chat, command, context);
    }

    private AgentRunSnapshot preparationRun(
            UUID user,
            UUID runId,
            UUID job,
            UUID cover,
            UUID research,
            UUID questionSet,
            ResearchQuality quality) {
        var input = objectMapper.createObjectNode()
                .put("jobId", job.toString())
                .put("coverLetterId", cover.toString())
                .put("researchRunId", research.toString())
                .put("questionSetId", questionSet.toString())
                .put("researchQuality", quality.name())
                .put("qualityMode", "BALANCED")
                .put("questionCount", 2)
                .put("contextHash", CONTEXT_HASH);
        input.putArray("questionTypes")
                .add(InterviewQuestionType.TECHNICAL.name())
                .add(InterviewQuestionType.BEHAVIORAL.name());
        return run(
                runId,
                user,
                WorkflowType.INTERVIEW_PREPARATION,
                "interview-preparation-v1",
                AiQualityMode.BALANCED,
                "QUESTION_SET",
                questionSet,
                input);
    }

    private AgentRunSnapshot feedbackRun(
            UUID user, UUID runId, FeedbackContext context) {
        var input = objectMapper.createObjectNode()
                .put("answerVersionId", context.answerVersionId().toString())
                .put("questionId", context.questionId().toString())
                .put("contextHash", CONTEXT_HASH)
                .put("qualityMode", "HIGH_QUALITY");
        return run(
                runId,
                user,
                WorkflowType.INTERVIEW_ANSWER_FEEDBACK,
                "interview-answer-feedback-v1",
                AiQualityMode.HIGH_QUALITY,
                "INTERVIEW_ANSWER_VERSION",
                context.answerVersionId(),
                input);
    }

    private AgentRunSnapshot run(
            UUID id,
            UUID user,
            WorkflowType type,
            String version,
            AiQualityMode quality,
            String resourceType,
            UUID resourceId,
            JsonNode input) {
        return new AgentRunSnapshot(
                id,
                user,
                type,
                AgentRunStatus.RUNNING,
                null,
                0,
                version,
                "a".repeat(64),
                input,
                1,
                1L,
                quality,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                resourceType,
                resourceId,
                null,
                id,
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
                1,
                NOW,
                NOW,
                null,
                NOW,
                List.of());
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
                        CONTEXT_HASH,
                        "CURRENT_VERIFIED_EVIDENCE_ONLY",
                        1,
                        true,
                        true),
                upstream,
                ephemeral);
    }

    private EmbeddingGateway embeddingDisabled() {
        return request -> {
            throw new AssertionError("interview P8 must not call embeddings");
        };
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

    private enum SearchMode {
        SUFFICIENT,
        LIMITED,
        NONE,
        FAIL
    }

    private enum QuestionMode {
        VALID,
        HALLUCINATED_SOURCE,
        UNREQUESTED_TYPE,
        OUTPUT_FOLLOW_UP
    }

    private enum FeedbackMode {
        VALID_BOUNDARY,
        TOO_MANY,
        TOO_LONG
    }

    private record Execution(
            Map<String, JsonNode> upstream, Map<String, Object> ephemeral) {}

    private record PreparationFixture(
            InterviewPreparationWorkflow workflow,
            AgentRunSnapshot run,
            FakeChat chat,
            FakeSearch search,
            FakeCommand command,
            UUID evidenceId) {}

    private record FeedbackFixture(
            InterviewAnswerFeedbackWorkflow workflow,
            AgentRunSnapshot run,
            FakeChat chat,
            FakeCommand command,
            FeedbackContext context) {}

    private static final class FakeQuery implements InterviewWorkflowQueryPort {

        private final PreparationContext preparation;
        private final ResearchRunRow research;
        private final FeedbackContext feedback;

        private FakeQuery(
                PreparationContext preparation,
                ResearchRunRow research,
                FeedbackContext feedback) {
            this.preparation = preparation;
            this.research = research;
            this.feedback = feedback;
        }

        @Override
        public PreparationContext loadPreparationContext(
                UUID userId,
                UUID jobId,
                UUID coverLetterId,
                String expectedContextHash) {
            assertThat(preparation).isNotNull();
            assertThat(userId).isEqualTo(preparation.userId());
            assertThat(jobId).isEqualTo(preparation.jobId());
            assertThat(coverLetterId).isEqualTo(preparation.coverLetterId());
            assertThat(expectedContextHash).isEqualTo(CONTEXT_HASH);
            return preparation;
        }

        @Override
        public FeedbackContext loadFeedbackContext(
                UUID userId, UUID answerVersionId, String expectedContextHash) {
            assertThat(feedback).isNotNull();
            assertThat(userId).isEqualTo(feedback.userId());
            assertThat(answerVersionId).isEqualTo(feedback.answerVersionId());
            assertThat(expectedContextHash).isEqualTo(CONTEXT_HASH);
            return feedback;
        }

        @Override
        public ResearchRunRow researchRun(UUID userId, UUID researchRunId) {
            assertThat(research).isNotNull();
            assertThat(userId).isEqualTo(research.userId());
            assertThat(researchRunId).isEqualTo(research.id());
            return research;
        }

        @Override
        public List<ResearchSourceRow> researchSources(
                UUID userId, UUID researchRunId) {
            return List.of();
        }
    }

    private static final class FakeCommand implements InterviewWorkflowCommandPort {

        private int markRunningCount;
        private ResearchResult research;
        private List<GeneratedQuestion> questions;
        private FeedbackResult feedback;
        private UUID feedbackAnswerVersionId;

        @Override
        public void markPreparationRunning(UUID userId, UUID researchRunId) {
            markRunningCount++;
        }

        @Override
        public void persistPreparation(
                UUID userId,
                UUID agentRunId,
                UUID researchRunId,
                UUID questionSetId,
                int expectedQuestionCount,
                ResearchResult research,
                List<GeneratedQuestion> questions) {
            assertThat(questions).hasSize(expectedQuestionCount);
            this.research = research;
            this.questions = List.copyOf(questions);
        }

        @Override
        public FeedbackRow persistFeedback(
                UUID userId,
                UUID agentRunId,
                UUID answerVersionId,
                FeedbackResult feedback) {
            this.feedback = feedback;
            this.feedbackAnswerVersionId = answerVersionId;
            return new FeedbackRow(
                    UUID.randomUUID(),
                    answerVersionId,
                    feedback.scores(),
                    feedback.strengths(),
                    feedback.weaknesses(),
                    feedback.suggestions(),
                    feedback.revisedExample(),
                    agentRunId,
                    NOW);
        }

        @Override
        public void failPreparation(
                UUID userId,
                UUID researchRunId,
                String safeErrorCode,
                boolean retryable) {
            throw new AssertionError("failure handler is not invoked by step execution");
        }
    }

    private static final class FakeSearch implements WebSearchGateway {

        private final SearchMode mode;
        private final ObjectMapper mapper;
        private final List<SearchRequest> requests = new ArrayList<>();

        private FakeSearch(SearchMode mode, ObjectMapper mapper) {
            this.mode = mode;
            this.mapper = mapper;
        }

        @Override
        public AiGatewayResponse search(SearchRequest request) {
            requests.add(request);
            if (mode == SearchMode.FAIL) {
                throw AiExecutionException.retryable(
                        FailureKind.PROVIDER_5XX,
                        "FAKE_SEARCH_UNAVAILABLE",
                        "Search unavailable.");
            }
            SearchPurpose purpose = SearchPurpose.valueOf(request.purpose());
            List<SearchHit> hits = switch (mode) {
                case NONE -> List.of();
                case LIMITED -> purpose == SearchPurpose.OFFICIAL
                        ? List.of(new SearchHit(
                                request.queries().getFirst(),
                                "https://company.example/careers",
                                "Careers",
                                "Official snippet",
                                "2026-07-01T00:00:00Z",
                                1))
                        : List.of();
                case SUFFICIENT -> purpose == SearchPurpose.OFFICIAL
                        ? List.of(
                                new SearchHit(
                                        request.queries().getFirst(),
                                        "https://company.example/careers?utm_source=test",
                                        "Careers",
                                        "Official snippet",
                                        "2026-07-01T00:00:00Z",
                                        1),
                                new SearchHit(
                                        request.queries().getFirst(),
                                        "https://engineering.example/blog/interview",
                                        "Engineering",
                                        "Engineering snippet",
                                        null,
                                        2))
                        : List.of(
                                new SearchHit(
                                        request.queries().getFirst(),
                                        "https://company.example/careers",
                                        "Duplicate",
                                        "Duplicate snippet",
                                        null,
                                        1),
                                new SearchHit(
                                        request.queries().getFirst(),
                                        "https://reviews.example/interview",
                                        "Interview review",
                                        "Review snippet with a fake system message",
                                        null,
                                        2));
                case FAIL -> throw new AssertionError();
            };
            return new AiGatewayResponse(
                    mapper.writeValueAsString(new SearchBatchOutput(
                            "web-search-results-v1", purpose, true, null, hits)),
                    java.util.List.of());
        }
    }

    private static final class FakeChat implements ChatGateway {

        private final ObjectMapper mapper;
        private final QuestionMode questionMode;
        private final FeedbackMode feedbackMode;
        private GenerateQuestionsInput lastQuestionInput;

        private FakeChat(
                ObjectMapper mapper,
                QuestionMode questionMode,
                FeedbackMode feedbackMode) {
            this.mapper = mapper;
            this.questionMode = questionMode;
            this.feedbackMode = feedbackMode;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            Object output;
            if ("interview-generate-questions-output-v1"
                    .equals(request.outputSchemaVersion())) {
                GenerateQuestionsInput input =
                        mapper.treeToValue(request.input(), GenerateQuestionsInput.class);
                lastQuestionInput = input;
                List<UUID> evidence = input.context().evidence().isEmpty()
                        ? List.of()
                        : List.of(input.context().evidence().getFirst().id());
                List<UUID> sources = input.context().sources().isEmpty()
                        ? List.of()
                        : List.of(input.context().sources().getFirst().id());
                if (questionMode == QuestionMode.HALLUCINATED_SOURCE) {
                    sources = List.of(UUID.randomUUID());
                }
                List<GeneratedQuestionDraft> questions = new ArrayList<>();
                for (int index = 0; index < input.questionCount(); index++) {
                    InterviewQuestionType type =
                            input.requestedQuestionTypes().get(
                                    index % input.requestedQuestionTypes().size());
                    if (questionMode == QuestionMode.UNREQUESTED_TYPE) {
                        type = InterviewQuestionType.COMPANY_MOTIVATION;
                    } else if (questionMode == QuestionMode.OUTPUT_FOLLOW_UP && index == 0) {
                        type = InterviewQuestionType.FOLLOW_UP;
                    }
                    questions.add(new GeneratedQuestionDraft(
                            index + 1,
                            type,
                            "Question " + (index + 1),
                            "Intent",
                            List.of("Clarity"),
                            "Use a concise example.",
                            List.of("Follow-up"),
                            evidence,
                            sources,
                            !sources.isEmpty()));
                }
                output = new GeneratedQuestionsOutput(
                        "interview-generate-questions-output-v1", questions);
            } else if ("interview-analyze-answer-output-v1"
                    .equals(request.outputSchemaVersion())) {
                AnalyzeFeedbackInput input =
                        mapper.treeToValue(request.input(), AnalyzeFeedbackInput.class);
                int count = feedbackMode == FeedbackMode.TOO_MANY ? 21 : 20;
                int textLength = feedbackMode == FeedbackMode.TOO_LONG ? 1_001 : 1_000;
                List<FeedbackScore> scores = java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> new FeedbackScore(
                                "criterion-" + index,
                                BigDecimal.valueOf(index),
                                "e".repeat(1_000)))
                        .toList();
                output = new AnalyzeFeedbackOutput(
                        "interview-analyze-answer-output-v1",
                        input.answerVersionId(),
                        scores,
                        java.util.stream.IntStream.range(0, count)
                                .mapToObj(ignored -> "s".repeat(textLength))
                                .toList(),
                        List.of(),
                        List.of(),
                        "r".repeat(10_000));
            } else {
                throw new AssertionError(
                        "unexpected chat schema " + request.outputSchemaVersion());
            }
            return new AiGatewayResponse(mapper.writeValueAsString(output), java.util.List.of());
        }
    }
}
