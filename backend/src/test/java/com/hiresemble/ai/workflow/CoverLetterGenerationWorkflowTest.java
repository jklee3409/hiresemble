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
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.prompt.CoverLetterGenerationPromptDefinitions;
import com.hiresemble.ai.prompt.CoverLetterGenerationV2PromptDefinitions;
import com.hiresemble.ai.prompt.CoverLetterGenerationV3PromptDefinitions;
import com.hiresemble.ai.prompt.CoverLetterGenerationV5PromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.AllocateExperiencesInput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.AllocateExperiencesInputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.AnalyzeQuestionInput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.AnalyzeQuestionInputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.EvidenceClaimDraft;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocation;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocationOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocationOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ExperienceAllocationV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.FactCheckAnswerInput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.FactCheckAnswerOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsInput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsInputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ProviderTipTapDocumentOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ProviderTipTapNodeOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionAnalysisOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionAnalysisOutputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlan;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlanV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionType;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.NarrativeFramework;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.HeadingPolicy;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WriteAnswerInputV2;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.VerifiedClaimDraft;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WriteAnswerInput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WrittenAnswerOutput;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.EvidenceClaimDraftV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.DraftAnswerInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.DraftAnswerOutputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.GroundAnswerInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.GroundedAnswerOutputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutputV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlanV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewAnswerInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewAnswerOutputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewCriterion;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewScoreV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.FactCheckAnswerOutputV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WriteAnswerInputV4;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.WrittenAnswerOutputV3;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainStepCompletion;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AppliedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CompanyResearch;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CompanyResearchSource;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceSourceExcerpt;
import com.hiresemble.coverletter.application.model.CoverLetterModels.WritingInsights;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationQuestion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.JobContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RequirementContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedEvidence;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
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

class CoverLetterGenerationWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
    private static final String SNAPSHOT_HASH = "a".repeat(64);
    private static final String V5_GROUNDED_SENTENCE = "Spring 서비스 경험을 바탕으로 장애 대응 절차를 정리했습니다.";
    private static final String V5_DRAFT = "[장애 대응으로 증명한 백엔드 역량]\n\n"
            + V5_GROUNDED_SENTENCE + " " + "초안".repeat(360);
    private static final String V5_REVIEW = "[장애 대응으로 증명한 백엔드 역량]\n\n"
            + V5_GROUNDED_SENTENCE + "\n\n" + "검토 반영".repeat(150);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredOutputValidator validator =
            new StructuredOutputValidator(objectMapper);
    private final PromptRegistry prompts =
            new PromptRegistry(CoverLetterGenerationPromptDefinitions.all());
    private final PromptRegistry v2Prompts =
            new PromptRegistry(CoverLetterGenerationV2PromptDefinitions.all());
    private final PromptRegistry v3Prompts =
            new PromptRegistry(CoverLetterGenerationV3PromptDefinitions.all());
    private final PromptRegistry v5Prompts =
            new PromptRegistry(CoverLetterGenerationV5PromptDefinitions.all());

    @Test
    void v4PlanningReceivesBoundedUserMemoAndExactModelInput() {
        Fixture base = singleQuestionFixture();
        GenerationQuestion sourceQuestion = base.snapshot().questions().getFirst();
        GenerationQuestion memoQuestion = new GenerationQuestion(
                sourceQuestion.questionId(),
                sourceQuestion.questionOrder(),
                sourceQuestion.questionText(),
                sourceQuestion.maxLength(),
                "지원 동기보다 장애 대응에서 제가 한 판단을 강조해 주세요.",
                sourceQuestion.currentAnswerVersionId(),
                sourceQuestion.currentPlainText());
        GenerationSnapshot snapshot = new GenerationSnapshot(
                base.snapshot().userId(),
                base.snapshot().coverLetterId(),
                base.snapshot().coverLetterVersion(),
                base.snapshot().title(),
                base.snapshot().job(),
                List.of(memoQuestion),
                base.snapshot().verifiedEvidence(),
                base.snapshot().preferredEvidenceIds(),
                base.snapshot().avoidExperienceDuplication(),
                null,
                OpenAiChatModels.GPT_5_6_TERRA,
                base.snapshot().snapshotHash());
        FakeQuery query = new FakeQuery(snapshot);
        CoverLetterGenerationWorkflow workflow = new CoverLetterGenerationWorkflow(
                query, new FakeCommand(snapshot.userId()), new FakeEmbeddingPolicy(), objectMapper);
        AgentRunSnapshot run = runV4(snapshot, UUID.randomUUID());
        ExecutableWorkflowStep plan = workflow.v4Contribution().steps().stream()
                .filter(value -> value.stepKey().equals(CoverLetterGenerationWorkflow.PLAN_QUESTIONS))
                .findFirst()
                .orElseThrow();

        StepInput input = plan.executor()
                .prepareInputs(context(run, Map.of(), Map.of(), null))
                .getFirst();
        CoverLetterGenerationWorkflow.PlanQuestionsInputV4 payload = objectMapper.treeToValue(
                input.gatewayPayload(), CoverLetterGenerationWorkflow.PlanQuestionsInputV4.class);

        assertThat(run.requestedQualityMode()).isNull();
        assertThat(run.requestedModel()).isEqualTo(OpenAiChatModels.GPT_5_6_TERRA);
        assertThat(payload.schemaVersion()).isEqualTo("cover-letter-input-v4");
        assertThat(payload.questions()).singleElement()
                .extracting(CoverLetterGenerationWorkflow.QuestionPlanningInputV4::questionMemo)
                .isEqualTo(memoQuestion.memo());
    }

    @Test
    void v3WriterLengthViolationRequestsOneSafeCorrectionBeforeDomainApply() {
        Fixture fixture = singleQuestionFixture();
        AgentRunSnapshot run = runV3(fixture.snapshot, UUID.randomUUID());
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();

        for (ExecutableWorkflowStep executable : fixture.workflow.v3Contribution().steps()) {
            if (executable.stepKey().equals(CoverLetterGenerationWorkflow.WRITE_ANSWER)) {
                StepInput input = executable.executor()
                        .prepareInputs(context(run, upstream, ephemeral, null))
                        .getFirst();
                StepExecutionContext scoped =
                        context(run, upstream, ephemeral, input.scopeKey());
                WrittenAnswerOutputV3 tooLong = new WrittenAnswerOutputV3(
                        "cover-generation-answer-output-v3",
                        fixture.firstQuestionId,
                        providerDocument("가".repeat(
                                fixture.snapshot.questions().getFirst().maxLength() + 1)),
                        List.of());

                assertThatThrownBy(() -> validate(
                                executable.executor(),
                                objectMapper.writeValueAsString(tooLong),
                                scoped))
                        .isInstanceOfSatisfying(AiExecutionException.class, failure -> {
                            assertThat(failure.safeCode())
                                    .isEqualTo("COVER_GENERATION_ANSWER_LENGTH_INVALID");
                            assertThat(failure.failureKind())
                                    .isEqualTo(FailureKind.STRUCTURED_OUTPUT);
                            assertThat(failure.retryable()).isTrue();
                            assertThat(failure.maxAutomaticAttempts()).isEqualTo(2);
                            assertThat(failure.correctionGuidance())
                                    .contains("maxLength")
                                    .doesNotContain(fixture.snapshot.questions()
                                            .getFirst()
                                            .questionText());
                        });
                assertThat(fixture.command.commands).isEmpty();
                return;
            }
            executeWholeStepWithRun(fixture, run, executable, upstream, ephemeral);
        }
        throw new AssertionError("WRITE_ANSWER step was not found");
    }

    @Test
    void v4WriterReceivesSourceExcerptsServerFillTargetAndWiderEvidence() {
        Fixture fixture = v4SingleQuestionFixture(1_000, "승인된 경험 ".repeat(500));
        UUID evidenceId = fixture.snapshot().verifiedEvidence().getFirst().id();
        UUID sharedChunk = UUID.randomUUID();
        fixture.query().sourceExcerpts.addAll(List.of(
                new EvidenceSourceExcerpt(evidenceId, sharedChunk, UUID.randomUUID(), 0, "원문 첫 문단"),
                new EvidenceSourceExcerpt(evidenceId, sharedChunk, UUID.randomUUID(), 0, "중복 문단"),
                new EvidenceSourceExcerpt(evidenceId, UUID.randomUUID(), UUID.randomUUID(), 1, "원문 둘째 문단"),
                new EvidenceSourceExcerpt(evidenceId, UUID.randomUUID(), UUID.randomUUID(), 2, "원문 셋째 문단"),
                new EvidenceSourceExcerpt(evidenceId, UUID.randomUUID(), UUID.randomUUID(), 3, "원문 넷째 문단"),
                new EvidenceSourceExcerpt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, "다른 근거")));

        StepInput input = prepareV4WriterInput(fixture, new HashMap<>(), new HashMap<>());
        WriteAnswerInputV4 payload = objectMapper.treeToValue(
                input.gatewayPayload(), WriteAnswerInputV4.class);

        assertThat(payload.targetCharacterCount()).isEqualTo(900);
        assertThat(payload.minimumCharacterCount()).isEqualTo(700);
        assertThat(payload.verifiedEvidence()).singleElement()
                .satisfies(value -> assertThat(value.content()).hasSize(3_500));
        assertThat(payload.evidenceSourceExcerpts()).singleElement().satisfies(value -> {
            assertThat(value.evidenceId()).isEqualTo(evidenceId);
            assertThat(value.truncated()).isFalse();
            assertThat(value.maskedSourceText())
                    .isEqualTo("원문 첫 문단\n\n원문 둘째 문단\n\n원문 셋째 문단")
                    .doesNotContain("중복 문단", "원문 넷째 문단", "다른 근거");
        });
        assertThat(fixture.query().excerptRequests).containsExactly(List.of(evidenceId));
        assertThat(input.sanitizedInputRefs().path("sourceExcerptCount").asInt()).isEqualTo(1);
    }

    @Test
    void v4WriterRequestsExpansionOnlyForClearlyUnderfilledAnswer() {
        Fixture fixture = v4SingleQuestionFixture(1_000, "Spring 서비스 경험을 정리했습니다.");
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        StepInput input = prepareV4WriterInput(fixture, upstream, ephemeral);
        StepExecutionContext scoped = context(fixture.run(), upstream, ephemeral, input.scopeKey());
        ExecutableWorkflowStep writer = v4Step(fixture, CoverLetterGenerationWorkflow.WRITE_ANSWER);

        assertThatThrownBy(() -> validate(
                        writer.executor(),
                        objectMapper.writeValueAsString(answer(fixture, "가".repeat(699), List.of())),
                        scoped))
                .isInstanceOfSatisfying(AiExecutionException.class, failure -> {
                    assertThat(failure.safeCode()).isEqualTo("COVER_GENERATION_ANSWER_TOO_SHORT");
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.STRUCTURED_OUTPUT);
                    assertThat(failure.retryable()).isTrue();
                    assertThat(failure.correctionGuidance())
                            .contains("at least 700")
                            .doesNotContain(fixture.snapshot().questions().getFirst().questionText());
                });
        assertThatCode(() -> validate(
                        writer.executor(),
                        objectMapper.writeValueAsString(answer(fixture, "가".repeat(700), List.of())),
                        scoped))
                .doesNotThrowAnyException();
    }

    @Test
    void v4AcceptsFactualWordingWithoutClaimsAndWarnsOnApply() {
        Fixture fixture = v4SingleQuestionFixture(1_000, "Spring 서비스 경험을 정리했습니다.");
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        String text = "장애 대응 성과를 만들었습니다. " + "가".repeat(700);

        runV4ThroughApply(fixture, upstream, ephemeral, answer(fixture, text, List.of()));

        assertThat(fixture.command().commands).singleElement().satisfies(command -> {
            assertThat(command.factCheck().status()).isEqualTo(VerificationStatus.WARNING);
            assertThat(command.factCheck().issues()).singleElement().satisfies(issue -> {
                assertThat(issue.code()).isEqualTo(VerificationIssueCode.UNVERIFIED_CLAIM);
                assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);
            });
        });
    }

    @Test
    void v4SingleQuestionFactCheckReportsUnsupportedNumbersWithoutClaims() {
        Fixture fixture = v4SingleQuestionFixture(1_000, "Spring 서비스 경험을 정리했습니다.");
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        String text = "응답 시간을 30% 줄였습니다. " + "가".repeat(700);

        runV4ThroughApply(fixture, upstream, ephemeral, answer(fixture, text, List.of()));

        FactCheckAnswerOutputV3 factCheck = (FactCheckAnswerOutputV3) ephemeral.get(
                StepExecutionContext.outputKey(
                        CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER,
                        fixture.firstQuestionId().toString()));
        assertThat(factCheck.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(VerificationIssueCode.UNVERIFIED_CLAIM);
            assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR);
            assertThat(issue.relatedText()).isEqualTo("30%");
        });
        assertThat(fixture.command().commands).singleElement()
                .satisfies(command -> assertThat(command.factCheck().status())
                        .isEqualTo(VerificationStatus.FAILED));
    }

    @Test
    void v3WriterStillRejectsFactualWordingWithoutClaims() {
        Fixture fixture = singleQuestionFixture();
        AgentRunSnapshot run = runV3(fixture.snapshot, UUID.randomUUID());
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        for (ExecutableWorkflowStep executable : fixture.workflow.v3Contribution().steps()) {
            if (executable.stepKey().equals(CoverLetterGenerationWorkflow.WRITE_ANSWER)) {
                StepInput input = executable.executor()
                        .prepareInputs(context(run, upstream, ephemeral, null))
                        .getFirst();
                assertStructuredOutputFailure(() -> validate(
                        executable.executor(),
                        objectMapper.writeValueAsString(answer(fixture, "장애 대응 성과를 만들었습니다.", List.of())),
                        context(run, upstream, ephemeral, input.scopeKey())));
                return;
            }
            executeWholeStepWithRun(fixture, run, executable, upstream, ephemeral);
        }
        throw new AssertionError("WRITE_ANSWER step was not found");
    }

    private StepInput prepareV4WriterInput(
            Fixture fixture, Map<String, JsonNode> upstream, Map<String, Object> ephemeral) {
        for (ExecutableWorkflowStep executable : fixture.workflow().v4Contribution().steps()) {
            if (executable.stepKey().equals(CoverLetterGenerationWorkflow.WRITE_ANSWER)) {
                return executable.executor()
                        .prepareInputs(context(fixture.run(), upstream, ephemeral, null))
                        .getFirst();
            }
            executeWholeStepWithRun(fixture, fixture.run(), executable, upstream, ephemeral);
        }
        throw new AssertionError("WRITE_ANSWER step was not found");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runV4ThroughApply(
            Fixture fixture,
            Map<String, JsonNode> upstream,
            Map<String, Object> ephemeral,
            WrittenAnswerOutputV3 written) {
        StepInput input = prepareV4WriterInput(fixture, upstream, ephemeral);
        StepExecutionContext scoped = context(fixture.run(), upstream, ephemeral, input.scopeKey());
        ExecutableWorkflowStep writer = v4Step(fixture, CoverLetterGenerationWorkflow.WRITE_ANSWER);
        Object output = validate(writer.executor(), objectMapper.writeValueAsString(written), scoped);
        DomainStepCompletion completion = complete(
                writer.executor(), output, minimal(writer.executor(), output, scoped), scoped);
        String key = StepExecutionContext.outputKey(writer.stepKey(), input.scopeKey());
        upstream.put(key, completion.minimalOutput());
        ephemeral.put(key, ephemeral(writer.executor(), output, scoped));
        executeWholeStepWithRun(
                fixture, fixture.run(), v4Step(fixture, CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER),
                upstream, ephemeral);
        executeWholeStepWithRun(
                fixture, fixture.run(), v4Step(fixture, CoverLetterGenerationWorkflow.APPLY_ANSWER_VERSION),
                upstream, ephemeral);
    }

    private ExecutableWorkflowStep v4Step(Fixture fixture, String key) {
        return fixture.workflow().v4Contribution().steps().stream()
                .filter(value -> value.stepKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private WrittenAnswerOutputV3 answer(
            Fixture fixture, String text, List<EvidenceClaimDraftV3> claims) {
        return new WrittenAnswerOutputV3(
                "cover-generation-answer-output-v3",
                fixture.firstQuestionId(),
                providerDocument(text),
                claims);
    }

    private Fixture v4SingleQuestionFixture(int maxLength, String evidenceContent) {
        Fixture base = singleQuestionFixture();
        GenerationSnapshot source = base.snapshot();
        GenerationQuestion question = source.questions().getFirst();
        VerifiedEvidence evidence = source.verifiedEvidence().getFirst();
        GenerationSnapshot snapshot = new GenerationSnapshot(
                source.userId(),
                source.coverLetterId(),
                source.coverLetterVersion(),
                source.title(),
                source.job(),
                List.of(new GenerationQuestion(
                        question.questionId(),
                        question.questionOrder(),
                        question.questionText(),
                        maxLength,
                        question.currentAnswerVersionId(),
                        question.currentPlainText())),
                List.of(new VerifiedEvidence(
                        evidence.id(),
                        evidence.sourceType(),
                        evidence.sourceEntityId(),
                        evidence.documentId(),
                        evidence.evidenceCategory(),
                        evidence.title(),
                        evidenceContent,
                        evidence.version())),
                source.preferredEvidenceIds(),
                source.avoidExperienceDuplication(),
                null,
                OpenAiChatModels.GPT_5_6_SOL,
                source.snapshotHash());
        FakeQuery query = new FakeQuery(snapshot);
        FakeCommand command = new FakeCommand(snapshot.userId());
        CoverLetterGenerationWorkflow workflow = new CoverLetterGenerationWorkflow(
                query, command, new FakeEmbeddingPolicy(), objectMapper);
        return new Fixture(
                snapshot,
                query,
                command,
                new FakeChat(objectMapper),
                new FakeEmbedding(objectMapper),
                workflow,
                runV4(snapshot, UUID.randomUUID()),
                question.questionId(),
                null);
    }

    @Test
    void v5SingleQuestionPlansDraftsReviewsAndGroundsBeforeApply() {
        Fixture fixture = v5SingleQuestionFixture(1_000);
        fixture.query().writingInsights = new WritingInsights(
                List.of("대규모 트래픽 장애 대응"),
                List.of("Kotlin 실무 경험 부족"),
                "Spring 운영 역량이 강점입니다.",
                new CompanyResearch(
                        "결제 인프라를 운영하는 회사입니다.",
                        List.of(new CompanyResearchSource(
                                "OFFICIAL", "회사 소개", "결제 인프라", "공식 출처입니다."))));
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();

        for (ExecutableWorkflowStep executable : fixture.workflow().v5Contribution().steps()) {
            executeWholeStepWithRun(fixture, fixture.run(), executable, upstream, ephemeral);
        }

        assertThat(fixture.chat().requests)
                .extracting(ChatGateway.ChatRequest::outputSchemaVersion)
                .containsExactly(
                        "cover-generation-plan-output-v3",
                        "cover-generation-draft-output-v1",
                        "cover-generation-review-output-v1",
                        "cover-generation-grounding-output-v1");
        PlanQuestionsInputV5 plan = objectMapper.treeToValue(
                fixture.chat().requests.get(0).input(), PlanQuestionsInputV5.class);
        assertThat(plan.writingInsights().analysisStrengths()).containsExactly("대규모 트래픽 장애 대응");
        DraftAnswerInputV5 draft = objectMapper.treeToValue(
                fixture.chat().requests.get(1).input(), DraftAnswerInputV5.class);
        assertThat(draft.targetCharacterCount()).isEqualTo(900);
        assertThat(draft.minimumCharacterCount()).isEqualTo(700);
        assertThat(draft.writingInsights().companyResearchSummary()).isEqualTo("결제 인프라를 운영하는 회사입니다.");
        assertThat(draft.writingInsights().companyResearchSources()).singleElement()
                .satisfies(source -> assertThat(source.sourceType()).isEqualTo("OFFICIAL"));
        ReviewAnswerInputV5 review = objectMapper.treeToValue(
                fixture.chat().requests.get(2).input(), ReviewAnswerInputV5.class);
        assertThat(review.draftAnswerText()).startsWith("[장애 대응으로 증명한 백엔드 역량]\n" + V5_GROUNDED_SENTENCE);
        GroundAnswerInputV5 grounding = objectMapper.treeToValue(
                fixture.chat().requests.get(3).input(), GroundAnswerInputV5.class);
        assertThat(grounding.answerText()).contains("검토 반영").doesNotContain("초안");
        assertThat(upstream.get(StepExecutionContext.outputKey(
                        CoverLetterGenerationWorkflow.REVIEW_ANSWER,
                        fixture.firstQuestionId().toString()))
                .path("scores").path("SPECIFICITY").asInt()).isEqualTo(4);

        assertThat(fixture.command().commands).singleElement().satisfies(command -> {
            TipTapNodeDto heading = command.contentJson().content().getFirst();
            assertThat(heading.content()).singleElement().satisfies(text -> {
                assertThat(text.text()).isEqualTo("[장애 대응으로 증명한 백엔드 역량]");
                assertThat(text.marks()).extracting(mark -> mark.type()).containsExactly("bold");
            });
            assertThat(command.contentJson().content()).hasSize(3);
            assertThat(command.evidenceUses()).singleElement()
                    .satisfies(use -> assertThat(use.claimText()).isEqualTo(V5_GROUNDED_SENTENCE));
        });
    }

    @Test
    void v5DraftRejectsMarkdownAndClearlyUnderfilledAnswers() {
        Fixture fixture = v5SingleQuestionFixture(1_000);
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        StepInput input = prepareV5Step(fixture, CoverLetterGenerationWorkflow.DRAFT_ANSWER, upstream, ephemeral);
        StepExecutionContext scoped = context(fixture.run(), upstream, ephemeral, input.scopeKey());
        ExecutableWorkflowStep draft = v5Step(fixture, CoverLetterGenerationWorkflow.DRAFT_ANSWER);

        assertThatThrownBy(() -> validate(draft.executor(), objectMapper.writeValueAsString(
                        new DraftAnswerOutputV5("cover-generation-draft-output-v1",
                                fixture.firstQuestionId(), "## 제목\n\n" + "가".repeat(800))), scoped))
                .isInstanceOfSatisfying(AiExecutionException.class, failure ->
                        assertThat(failure.safeCode()).isEqualTo("COVER_GENERATION_V5_ANSWER_MARKDOWN"));
        assertThatThrownBy(() -> validate(draft.executor(), objectMapper.writeValueAsString(
                        new DraftAnswerOutputV5("cover-generation-draft-output-v1",
                                fixture.firstQuestionId(), "가".repeat(600))), scoped))
                .isInstanceOfSatisfying(AiExecutionException.class, failure -> {
                    assertThat(failure.safeCode()).isEqualTo("COVER_GENERATION_ANSWER_TOO_SHORT");
                    assertThat(failure.retryable()).isTrue();
                });
        assertThatCode(() -> validate(draft.executor(), objectMapper.writeValueAsString(
                        new DraftAnswerOutputV5("cover-generation-draft-output-v1",
                                fixture.firstQuestionId(), V5_DRAFT)), scoped))
                .doesNotThrowAnyException();
    }

    @Test
    void v5GroundingRejectsParaphrasedExcerptsAndUnknownEvidence() {
        Fixture fixture = v5SingleQuestionFixture(1_000);
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        StepInput input = prepareV5Step(fixture, CoverLetterGenerationWorkflow.WRITE_ANSWER, upstream, ephemeral);
        StepExecutionContext scoped = context(fixture.run(), upstream, ephemeral, input.scopeKey());
        ExecutableWorkflowStep grounding = v5Step(fixture, CoverLetterGenerationWorkflow.WRITE_ANSWER);
        UUID evidenceId = fixture.snapshot().verifiedEvidence().getFirst().id();

        assertThatThrownBy(() -> validate(grounding.executor(), objectMapper.writeValueAsString(
                        new GroundedAnswerOutputV5("cover-generation-grounding-output-v1",
                                fixture.firstQuestionId(),
                                List.of(new EvidenceClaimDraftV3(evidenceId, "원문에 없는 문장입니다.",
                                        CoverLetterWorkflowV3Policy.ClaimType.FACT)))), scoped))
                .isInstanceOfSatisfying(AiExecutionException.class, failure ->
                        assertThat(failure.safeCode()).isEqualTo("COVER_GENERATION_GROUNDING_EXCERPT_INVALID"));
        assertThatThrownBy(() -> validate(grounding.executor(), objectMapper.writeValueAsString(
                        new GroundedAnswerOutputV5("cover-generation-grounding-output-v1",
                                fixture.firstQuestionId(),
                                List.of(new EvidenceClaimDraftV3(UUID.randomUUID(), V5_GROUNDED_SENTENCE,
                                        CoverLetterWorkflowV3Policy.ClaimType.FACT)))), scoped))
                .isInstanceOfSatisfying(AiExecutionException.class, failure ->
                        assertThat(failure.safeCode()).isEqualTo("COVER_GENERATION_ANSWER_EVIDENCE_INVALID"));
    }

    private StepInput prepareV5Step(
            Fixture fixture, String stepKey, Map<String, JsonNode> upstream, Map<String, Object> ephemeral) {
        for (ExecutableWorkflowStep executable : fixture.workflow().v5Contribution().steps()) {
            if (executable.stepKey().equals(stepKey)) {
                return executable.executor()
                        .prepareInputs(context(fixture.run(), upstream, ephemeral, null))
                        .getFirst();
            }
            executeWholeStepWithRun(fixture, fixture.run(), executable, upstream, ephemeral);
        }
        throw new AssertionError(stepKey + " step was not found");
    }

    private ExecutableWorkflowStep v5Step(Fixture fixture, String key) {
        return fixture.workflow().v5Contribution().steps().stream()
                .filter(value -> value.stepKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private Fixture v5SingleQuestionFixture(int maxLength) {
        Fixture v4 = v4SingleQuestionFixture(maxLength, "Spring 서비스 경험을 정리했습니다.");
        return new Fixture(
                v4.snapshot(),
                v4.query(),
                v4.command(),
                v4.chat(),
                v4.embedding(),
                v4.workflow(),
                runV5(v4.snapshot(), UUID.randomUUID()),
                v4.firstQuestionId(),
                null);
    }

    @Test
    void v2WriterReceivesPlanJobEvidenceAndCurrentAnswer() {
        Fixture fixture = fixture();
        AgentRunSnapshot run = runV2(fixture.snapshot, UUID.randomUUID());
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        for (ExecutableWorkflowStep executable : fixture.workflow.v2Contribution().steps()) {
            if (executable.stepKey().equals(CoverLetterGenerationWorkflow.WRITE_ANSWER)) {
                List<StepInput> inputs = executable.executor().prepareInputs(
                        context(run, upstream, ephemeral, null));
                StepInput revised = inputs.stream()
                        .filter(value -> value.scopeKey().equals(
                                fixture.snapshot.questions().get(1).questionId().toString()))
                        .findFirst()
                        .orElseThrow();
                WriteAnswerInputV2 payload = objectMapper.treeToValue(
                        revised.gatewayPayload(), WriteAnswerInputV2.class);
                assertThat(payload.plan().coreMessage()).isNotBlank();
                assertThat(payload.analysis().narrativeFramework())
                        .isEqualTo(payload.plan().narrativeFramework());
                assertThat(payload.job().companyName()).isEqualTo("Hiresemble");
                assertThat(payload.verifiedEvidence()).singleElement();
                assertThat(payload.verifiedEvidence().getFirst().sourceType())
                        .isEqualTo(EvidenceSourceType.ACTIVITY.name());
                assertThat(payload.currentAnswerVersionId())
                        .isEqualTo(fixture.secondCurrentVersionId);
                assertThat(payload.currentPlainText())
                        .isEqualTo(fixture.snapshot.questions().get(1).currentPlainText());
                assertThat(payload.otherQuestions()).singleElement();
                return;
            }
            executeWholeStepWithRun(fixture, run, executable, upstream, ephemeral);
        }
        throw new AssertionError("WRITE_ANSWER step was not found");
    }

    @Test
    void contributionKeepsCoverLetterSpecificTerminalPartialFailurePolicy() {
        var policy = fixture().workflow.contribution().terminalPartialPolicy();

        assertThat(policy.outcome()).isEqualTo(TerminalPartialPolicy.Outcome.FAILED);
        assertThat(policy.safeErrorCode())
                .isEqualTo("COVER_LETTER_GENERATION_PARTIAL_FAILURE");
        assertThat(policy.retryPolicy())
                .isEqualTo(TerminalPartialPolicy.RetryPolicy.INHERIT_FAILURES);
    }

    @Test
    void twoQuestionApplyUsesFrozenBaseCasAndRunScopedMaterial() {
        Fixture fixture = fixture();
        Execution execution = executeUntilApply(fixture);
        ExecutableWorkflowStep apply = step(
                fixture.workflow, CoverLetterGenerationWorkflow.APPLY_ANSWER_VERSION);
        StepExecutionContext preparation =
                context(fixture.run, execution.upstream, execution.ephemeral, null);
        List<StepInput> inputs = apply.executor().prepareInputs(preparation);

        assertThat(inputs).hasSize(2);
        assertThat(inputs)
                .allSatisfy(input -> assertThat(input.canonicalInputMaterial())
                        .contains(fixture.run.id().toString()));
        int snapshotLoadsBeforeApply = fixture.query.snapshotLoads.get();
        fixture.query.rejectSnapshotLoads = true;

        List<DomainStepCompletion> completions = new ArrayList<>();
        for (StepInput input : inputs) {
            StepExecutionContext scoped =
                    context(
                            fixture.run,
                            execution.upstream,
                            execution.ephemeral,
                            input.scopeKey());
            completions.add(executeFresh(fixture, apply, input, scoped));
        }

        assertThat(fixture.query.snapshotLoads).hasValue(snapshotLoadsBeforeApply);
        assertThat(fixture.command.commands).hasSize(2);
        assertThat(fixture.command.commands)
                .extracting(PersistGeneratedAnswer::expectedCoverLetterVersion)
                .containsExactly(7L, 7L);
        assertThat(fixture.command.commands)
                .extracting(PersistGeneratedAnswer::expectedCurrentVersionId)
                .containsExactly(null, fixture.secondCurrentVersionId);
        assertThat(fixture.command.returnedCoverVersions)
                .containsExactly(8L, 9L);
        assertThat(completions)
                .allSatisfy(completion -> {
                    assertThat(completion.partialResult().succeededScopeKeys())
                            .hasSize(1);
                    assertThat(completion.partialResult().resultRefs())
                            .singleElement()
                            .satisfies(reference -> assertThat(reference.resourceType())
                                    .isEqualTo("COVER_LETTER_ANSWER_VERSION"));
                });

        fixture.query.rejectSnapshotLoads = false;
        AgentRunSnapshot anotherRun =
                run(fixture.snapshot, UUID.randomUUID());
        StepExecutionContext anotherContext = context(
                anotherRun, execution.upstream, execution.ephemeral, null);
        List<StepInput> anotherInputs =
                apply.executor().prepareInputs(anotherContext);
        assertThat(anotherInputs.getFirst().canonicalInputMaterial())
                .isNotEqualTo(inputs.getFirst().canonicalInputMaterial())
                .contains(anotherRun.id().toString());
    }

    @Test
    void failedQuestionScopesAreExcludedFromEveryDownstreamFanOut() {
        Fixture fixture = fixture();
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();

        executeWholeStep(
                fixture,
                step(
                        fixture.workflow,
                        CoverLetterGenerationWorkflow.BUILD_GENERATION_CONTEXT),
                upstream,
                ephemeral);
        executeWholeStep(
                fixture,
                step(fixture.workflow, CoverLetterGenerationWorkflow.PLAN_QUESTIONS),
                upstream,
                ephemeral);

        ExecutableWorkflowStep analyze =
                step(fixture.workflow, CoverLetterGenerationWorkflow.ANALYZE_QUESTION);
        StepExecutionContext analyzeContext =
                context(fixture.run, upstream, ephemeral, null);
        List<StepInput> analyzeInputs =
                analyze.executor().prepareInputs(analyzeContext);
        executeAndStore(
                fixture,
                analyze,
                analyzeInputs.getFirst(),
                context(
                        fixture.run,
                        upstream,
                        ephemeral,
                        analyzeInputs.getFirst().scopeKey()),
                upstream,
                ephemeral);

        ExecutableWorkflowStep retrieve =
                step(fixture.workflow, CoverLetterGenerationWorkflow.RETRIEVE_EVIDENCE);
        List<StepInput> retrievalInputs = retrieve.executor().prepareInputs(
                context(fixture.run, upstream, ephemeral, null));
        assertThat(retrievalInputs)
                .extracting(StepInput::scopeKey)
                .containsExactly(fixture.firstQuestionId.toString());

        AiExecutionException domainFailure =
                AiExecutionException.nonRetryable(
                        FailureKind.DOMAIN_VALIDATION, "TEST", "test");
        AiExecutionException ownerFailure =
                AiExecutionException.nonRetryable(
                        FailureKind.OWNER, "TEST", "test");
        for (String key : List.of(
                CoverLetterGenerationWorkflow.ANALYZE_QUESTION,
                CoverLetterGenerationWorkflow.RETRIEVE_EVIDENCE,
                CoverLetterGenerationWorkflow.WRITE_ANSWER,
                CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER,
                CoverLetterGenerationWorkflow.APPLY_ANSWER_VERSION)) {
            var executor = step(fixture.workflow, key).executor();
            StepExecutionContext scoped = context(
                    fixture.run,
                    upstream,
                    ephemeral,
                    fixture.firstQuestionId.toString());
            assertThat(executor.continueAfterScopeFailure(domainFailure, scoped))
                    .as(key)
                    .isTrue();
            assertThat(executor.continueAfterScopeFailure(ownerFailure, scoped))
                    .as(key)
                    .isFalse();
        }
    }

    @Test
    void generationSuggestionsHonorTwentyByOneThousandBoundaryBeforeApply() {
        Fixture fixture = fixture();
        Execution execution =
                executeUntil(fixture, CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER);
        ExecutableWorkflowStep factCheck =
                step(fixture.workflow, CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER);
        StepExecutionContext context = context(
                fixture.run,
                execution.upstream,
                execution.ephemeral,
                fixture.firstQuestionId.toString());
        UUID evidenceId = fixture.snapshot.verifiedEvidence().getFirst().id();

        FactCheckAnswerOutput valid = factCheckOutput(
                fixture.firstQuestionId, evidenceId, suggestions(20, 1_000));
        assertThatCode(() -> validate(
                        factCheck.executor(),
                        objectMapper.writeValueAsString(valid),
                        context))
                .doesNotThrowAnyException();

        FactCheckAnswerOutput tooMany = factCheckOutput(
                fixture.firstQuestionId, evidenceId, suggestions(21, 1_000));
        assertStructuredOutputFailure(() -> validate(
                factCheck.executor(),
                objectMapper.writeValueAsString(tooMany),
                context));

        FactCheckAnswerOutput tooLong = factCheckOutput(
                fixture.firstQuestionId, evidenceId, suggestions(1, 1_001));
        assertStructuredOutputFailure(() -> validate(
                factCheck.executor(),
                objectMapper.writeValueAsString(tooLong),
                context));

        assertThat(fixture.command.commands).isEmpty();
    }

    private Execution executeUntilApply(Fixture fixture) {
        return executeUntil(
                fixture, CoverLetterGenerationWorkflow.APPLY_ANSWER_VERSION);
    }

    private Execution executeUntil(Fixture fixture, String stopStepKey) {
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        for (ExecutableWorkflowStep executable :
                fixture.workflow.contribution().steps()) {
            if (executable.stepKey().equals(stopStepKey)) {
                break;
            }
            executeWholeStep(fixture, executable, upstream, ephemeral);
        }
        return new Execution(upstream, ephemeral);
    }

    private void executeWholeStep(
            Fixture fixture,
            ExecutableWorkflowStep step,
            Map<String, JsonNode> upstream,
            Map<String, Object> ephemeral) {
        StepExecutionContext preparation =
                context(fixture.run, upstream, ephemeral, null);
        List<StepInput> inputs = step.executor().prepareInputs(preparation);
        for (StepInput input : inputs) {
            executeAndStore(
                    fixture,
                    step,
                    input,
                    context(
                            fixture.run,
                            upstream,
                            ephemeral,
                            input.scopeKey()),
                    upstream,
                    ephemeral);
        }
    }

    private void executeWholeStepWithRun(
            Fixture fixture,
            AgentRunSnapshot run,
            ExecutableWorkflowStep step,
            Map<String, JsonNode> upstream,
            Map<String, Object> ephemeral) {
        List<StepInput> inputs = step.executor().prepareInputs(
                context(run, upstream, ephemeral, null));
        for (StepInput input : inputs) {
            executeAndStore(
                    fixture,
                    step,
                    input,
                    context(run, upstream, ephemeral, input.scopeKey()),
                    upstream,
                    ephemeral);
        }
    }

    private void executeAndStore(
            Fixture fixture,
            ExecutableWorkflowStep step,
            StepInput input,
            StepExecutionContext context,
            Map<String, JsonNode> upstream,
            Map<String, Object> ephemeral) {
        StepValue value = invokeAndValidate(fixture, step, input, context);
        DomainStepCompletion completion =
                complete(step.executor(), value.output, value.minimal, context);
        String outputKey = StepExecutionContext.outputKey(
                step.stepKey(), input.scopeKey());
        upstream.put(outputKey, completion.minimalOutput());
        ephemeral.put(
                outputKey, ephemeral(step.executor(), value.output, context));
    }

    private DomainStepCompletion executeFresh(
            Fixture fixture,
            ExecutableWorkflowStep step,
            StepInput input,
            StepExecutionContext context) {
        StepValue value = invokeAndValidate(fixture, step, input, context);
        return complete(step.executor(), value.output, value.minimal, context);
    }

    private StepValue invokeAndValidate(
            Fixture fixture,
            ExecutableWorkflowStep step,
            StepInput input,
            StepExecutionContext context) {
        PromptRegistry registry =
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V3_VERSION.equals(
                                        context.run().workflowVersion())
                                || CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V4_VERSION.equals(
                                        context.run().workflowVersion())
                        ? v3Prompts
                        : CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION.equals(
                                        context.run().workflowVersion())
                                ? v5Prompts
                        : CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V2_VERSION.equals(
                                        context.run().workflowVersion())
                                ? v2Prompts
                                : prompts;
        AiGatewayResponse response = step.executor().invoke(new GatewayInvocation(
                input,
                new ModelRoute(1L, ModelTier.BALANCED, "fake", "fake", false),
                registry.require(
                        WorkflowType.COVER_LETTER_GENERATION,
                        context.run().workflowVersion(),
                        step.stepKey()),
                fixture.chat,
                fixture.embedding,
                request -> {
                    throw new AssertionError("web search is not allowed");
                },
                context));
        Object output = validate(step.executor(), response.rawJson(), context);
        return new StepValue(
                output, minimal(step.executor(), output, context));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object validate(
            WorkflowStepExecutor executor,
            String rawJson,
            StepExecutionContext context) {
        return validator.validate(rawJson, executor.outputContract(context));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private JsonNode minimal(
            WorkflowStepExecutor executor, Object output, StepExecutionContext context) {
        return executor.minimalOutput(output, objectMapper, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object ephemeral(
            WorkflowStepExecutor executor, Object output, StepExecutionContext context) {
        return executor.ephemeralOutput(output, context);
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
            Map<String, Object> ephemeral,
            String scopeKey) {
        StepExecutionContext context = new StepExecutionContext(
                run,
                new ContextSnapshot(
                        run.userId(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new TruncationSummary(0, 0, List.of()),
                        SNAPSHOT_HASH,
                        "CURRENT_VERIFIED_EVIDENCE_ONLY",
                        1L,
                        true,
                        true),
                upstream,
                ephemeral);
        return scopeKey == null ? context : context.forScope(scopeKey);
    }

    private ExecutableWorkflowStep step(
            CoverLetterGenerationWorkflow workflow, String key) {
        return workflow.contribution().steps().stream()
                .filter(value -> value.stepKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private FactCheckAnswerOutput factCheckOutput(
            UUID questionId, UUID evidenceId, List<String> suggestions) {
        return new FactCheckAnswerOutput(
                "cover-generation-fact-check-output-v1",
                questionId,
                List.of(),
                suggestions,
                List.of(new VerifiedClaimDraft(
                        "supported claim", true, List.of(evidenceId))));
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
        UUID firstQuestionId = UUID.randomUUID();
        UUID secondQuestionId = UUID.randomUUID();
        UUID secondCurrentVersionId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        GenerationSnapshot snapshot = new GenerationSnapshot(
                userId,
                coverLetterId,
                7L,
                "지원서",
                new JobContext(
                        UUID.randomUUID(),
                        3L,
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
                        new GenerationQuestion(
                                firstQuestionId,
                                1,
                                "지원 동기를 작성하세요.",
                                500,
                                null,
                                null),
                        new GenerationQuestion(
                                secondQuestionId,
                                2,
                                "직무 경험을 작성하세요.",
                                500,
                                secondCurrentVersionId,
                                "기존 답변")),
                List.of(new VerifiedEvidence(
                        evidenceId,
                        EvidenceSourceType.ACTIVITY,
                        UUID.randomUUID(),
                        null,
                        "CAREER",
                        "Spring 서비스 경험",
                        "Spring 서비스 성과를 만들었습니다.",
                        4L)),
                List.of(evidenceId),
                true,
                AiQualityMode.BALANCED,
                SNAPSHOT_HASH);
        FakeQuery query = new FakeQuery(snapshot);
        FakeCommand command = new FakeCommand(userId);
        FakeEmbeddingPolicy embeddingPolicy = new FakeEmbeddingPolicy();
        CoverLetterGenerationWorkflow workflow = new CoverLetterGenerationWorkflow(
                query, command, embeddingPolicy, objectMapper);
        return new Fixture(
                snapshot,
                query,
                command,
                new FakeChat(objectMapper),
                new FakeEmbedding(objectMapper),
                workflow,
                run(snapshot, UUID.randomUUID()),
                firstQuestionId,
                secondCurrentVersionId);
    }

    private Fixture singleQuestionFixture() {
        Fixture base = fixture();
        GenerationSnapshot source = base.snapshot();
        GenerationSnapshot snapshot = new GenerationSnapshot(
                source.userId(),
                source.coverLetterId(),
                source.coverLetterVersion(),
                source.title(),
                source.job(),
                List.of(source.questions().getFirst()),
                source.verifiedEvidence(),
                source.preferredEvidenceIds(),
                source.avoidExperienceDuplication(),
                source.qualityMode(),
                source.snapshotHash());
        FakeQuery query = new FakeQuery(snapshot);
        FakeCommand command = new FakeCommand(snapshot.userId());
        CoverLetterGenerationWorkflow workflow = new CoverLetterGenerationWorkflow(
                query, command, new FakeEmbeddingPolicy(), objectMapper);
        return new Fixture(
                snapshot,
                query,
                command,
                new FakeChat(objectMapper),
                new FakeEmbedding(objectMapper),
                workflow,
                runV3(snapshot, UUID.randomUUID()),
                snapshot.questions().getFirst().questionId(),
                null);
    }

    private AgentRunSnapshot run(
            GenerationSnapshot snapshot, UUID runId) {
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", snapshot.coverLetterId().toString())
                .put("coverLetterVersion", snapshot.coverLetterVersion())
                .put("snapshotHash", snapshot.snapshotHash())
                .put("qualityMode", snapshot.qualityMode().name())
                .put(
                        "avoidExperienceDuplication",
                        snapshot.avoidExperienceDuplication());
        var questionIds = input.putArray("questionIds");
        snapshot.questions()
                .forEach(value -> questionIds.add(value.questionId().toString()));
        var preferred = input.putArray("preferredEvidenceIds");
        snapshot.preferredEvidenceIds()
                .forEach(value -> preferred.add(value.toString()));
        return new AgentRunSnapshot(
                runId,
                snapshot.userId(),
                WorkflowType.COVER_LETTER_GENERATION,
                AgentRunStatus.RUNNING,
                null,
                0,
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_LEGACY_VERSION,
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

    private AgentRunSnapshot runV2(GenerationSnapshot snapshot, UUID runId) {
        AgentRunSnapshot legacy = run(snapshot, runId);
        return new AgentRunSnapshot(
                legacy.id(), legacy.userId(), legacy.workflowType(), legacy.status(),
                legacy.currentStep(), legacy.progressPercent(),
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V2_VERSION,
                legacy.canonicalInputHash(), legacy.inputReferenceSnapshot(),
                legacy.budgetPolicyVersion(), legacy.priceVersion(),
                legacy.requestedQualityMode(), legacy.highestModelTierUsed(),
                legacy.estimatedCostUsd(), legacy.reservedCostUsd(), legacy.actualCostUsd(),
                legacy.resourceType(), legacy.resourceId(), legacy.retryOfRunId(),
                legacy.rootRunId(), legacy.runAttemptNo(), legacy.retryableFailure(),
                legacy.safeError(), legacy.partialResult(), legacy.claimToken(), legacy.claimedBy(),
                legacy.leaseExpiresAt(), legacy.heartbeatAt(), legacy.cancelRequestedAt(),
                legacy.requiredUserAction(), legacy.stateVersion(), legacy.queuedAt(),
                legacy.startedAt(), legacy.completedAt(), legacy.updatedAt(), legacy.steps());
    }

    private AgentRunSnapshot runV3(GenerationSnapshot snapshot, UUID runId) {
        AgentRunSnapshot v2 = runV2(snapshot, runId);
        return new AgentRunSnapshot(
                v2.id(), v2.userId(), v2.workflowType(), v2.status(),
                v2.currentStep(), v2.progressPercent(),
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V3_VERSION,
                v2.canonicalInputHash(), v2.inputReferenceSnapshot(),
                v2.budgetPolicyVersion(), v2.priceVersion(),
                v2.requestedQualityMode(), v2.highestModelTierUsed(),
                v2.estimatedCostUsd(), v2.reservedCostUsd(), v2.actualCostUsd(),
                v2.resourceType(), v2.resourceId(), v2.retryOfRunId(),
                v2.rootRunId(), v2.runAttemptNo(), v2.retryableFailure(),
                v2.safeError(), v2.partialResult(), v2.claimToken(), v2.claimedBy(),
                v2.leaseExpiresAt(), v2.heartbeatAt(), v2.cancelRequestedAt(),
                v2.requiredUserAction(), v2.stateVersion(), v2.queuedAt(),
                v2.startedAt(), v2.completedAt(), v2.updatedAt(), v2.steps());
    }

    private AgentRunSnapshot runV4(GenerationSnapshot snapshot, UUID runId) {
        return exactModelRun(
                snapshot, runId, CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V4_VERSION);
    }

    private AgentRunSnapshot runV5(GenerationSnapshot snapshot, UUID runId) {
        return exactModelRun(
                snapshot, runId, CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION);
    }

    private AgentRunSnapshot exactModelRun(
            GenerationSnapshot snapshot, UUID runId, String workflowVersion) {
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", snapshot.coverLetterId().toString())
                .put("coverLetterVersion", snapshot.coverLetterVersion())
                .put("snapshotHash", snapshot.snapshotHash())
                .put("model", snapshot.model())
                .put(
                        "avoidExperienceDuplication",
                        snapshot.avoidExperienceDuplication());
        var questionIds = input.putArray("questionIds");
        snapshot.questions()
                .forEach(value -> questionIds.add(value.questionId().toString()));
        var preferred = input.putArray("preferredEvidenceIds");
        snapshot.preferredEvidenceIds()
                .forEach(value -> preferred.add(value.toString()));
        return new AgentRunSnapshot(
                runId,
                snapshot.userId(),
                WorkflowType.COVER_LETTER_GENERATION,
                AgentRunStatus.RUNNING,
                null,
                0,
                workflowVersion,
                "f".repeat(64),
                input,
                1L,
                1L,
                null,
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

    private ProviderTipTapDocumentOutput providerDocument(String text) {
        return new ProviderTipTapDocumentOutput(
                "doc",
                List.of(new ProviderTipTapNodeOutput(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(new ProviderTipTapNodeOutput(
                                "text", text, List.of(), List.of())))));
    }

    private record Fixture(
            GenerationSnapshot snapshot,
            FakeQuery query,
            FakeCommand command,
            FakeChat chat,
            FakeEmbedding embedding,
            CoverLetterGenerationWorkflow workflow,
            AgentRunSnapshot run,
            UUID firstQuestionId,
            UUID secondCurrentVersionId) {}

    private record Execution(
            Map<String, JsonNode> upstream, Map<String, Object> ephemeral) {}

    private record StepValue(Object output, JsonNode minimal) {}

    private final class FakeChat implements ChatGateway {

        private final ObjectMapper mapper;
        private final List<ChatRequest> requests = new ArrayList<>();
        private String draftText = V5_DRAFT;
        private String reviewText = V5_REVIEW;

        private FakeChat(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            requests.add(request);
            Object output = switch (request.outputSchemaVersion()) {
                case "cover-generation-plan-output-v3" -> {
                    PlanQuestionsInputV5 input =
                            mapper.treeToValue(request.input(), PlanQuestionsInputV5.class);
                    yield new PlanQuestionsOutputV3(
                            "cover-generation-plan-output-v3",
                            input.questions().stream()
                                    .map(question -> new QuestionPlanV3(
                                            question.questionId(),
                                            QuestionType.ROLE_COMPETENCY,
                                            "장애 대응으로 증명한 백엔드 역량 " + question.questionOrder(),
                                            NarrativeFramework.PROBLEM_ACTION_RESULT,
                                            "직무 역량을 묻는 질문에 장애 대응 경험으로 답한다",
                                            List.of("개인 판단", "검증된 결과"),
                                            List.of("근거 없는 수치"),
                                            List.of(0),
                                            "Spring 서비스 운영 요건과 연결한다",
                                            "공식 조사에 나온 결제 인프라와 연결한다",
                                            List.of("장애 대응 경험"),
                                            Math.min(900, question.maxLength()),
                                            HeadingPolicy.OPTIONAL,
                                            List.of(
                                                    new CoverLetterWorkflowV3Policy.NarrativeSectionPlan(
                                                            CoverLetterWorkflowV3Policy.NarrativeSectionType.DIRECT_ANSWER,
                                                            "첫 문장에서 역량을 말한다",
                                                            60),
                                                    new CoverLetterWorkflowV3Policy.NarrativeSectionPlan(
                                                            CoverLetterWorkflowV3Policy.NarrativeSectionType.DECISION,
                                                            "개인 판단을 보여 준다",
                                                            30))))
                                    .toList(),
                            input.avoidExperienceDuplication());
                }
                case "cover-generation-draft-output-v1" -> {
                    DraftAnswerInputV5 input =
                            mapper.treeToValue(request.input(), DraftAnswerInputV5.class);
                    yield new DraftAnswerOutputV5(
                            "cover-generation-draft-output-v1", input.questionId(), draftText);
                }
                case "cover-generation-review-output-v1" -> {
                    ReviewAnswerInputV5 input =
                            mapper.treeToValue(request.input(), ReviewAnswerInputV5.class);
                    yield new ReviewAnswerOutputV5(
                            "cover-generation-review-output-v1",
                            input.questionId(),
                            java.util.Arrays.stream(ReviewCriterion.values())
                                    .map(value -> new ReviewScoreV5(value, 4, "구체적인 근거가 잘 드러납니다."))
                                    .toList(),
                            List.of("첫 문장을 더 직접적으로 바꿔야 합니다."),
                            reviewText);
                }
                case "cover-generation-grounding-output-v1" -> {
                    GroundAnswerInputV5 input =
                            mapper.treeToValue(request.input(), GroundAnswerInputV5.class);
                    yield new GroundedAnswerOutputV5(
                            "cover-generation-grounding-output-v1",
                            input.questionId(),
                            input.answerText().contains(V5_GROUNDED_SENTENCE)
                                    ? List.of(new EvidenceClaimDraftV3(
                                            input.verifiedEvidence().getFirst().id(),
                                            V5_GROUNDED_SENTENCE,
                                            CoverLetterWorkflowV3Policy.ClaimType.FACT))
                                    : List.of());
                }
                case "cover-generation-plan-output-v2" -> {
                    PlanQuestionsInputV2 input =
                            mapper.treeToValue(request.input(), PlanQuestionsInputV2.class);
                    yield new PlanQuestionsOutputV2(
                            "cover-generation-plan-output-v2",
                            input.questions().stream()
                                    .map(question -> new QuestionPlanV2(
                                            question.questionId(),
                                            QuestionType.ROLE_COMPETENCY,
                                            "Spring 문제 해결 역량 " + question.questionOrder(),
                                            NarrativeFramework.COMPETENCY_EVIDENCE_APPLICATION,
                                            "질문에 직접 답하고 직무 역량을 증명한다",
                                            List.of("개인 행동", "선택 이유"),
                                            List.of("근거 없는 수치"),
                                            List.of(0),
                                            "검증된 행동을 직무 책임과 연결한다",
                                            "제공된 공고 범위에서만 회사 연결을 설명한다",
                                            List.of("구체적인 행동과 결과가 있는 경험"),
                                            Math.min(300, question.maxLength()),
                                            HeadingPolicy.OPTIONAL))
                                    .toList(),
                            input.avoidExperienceDuplication());
                }
                case "cover-generation-question-analysis-output-v2" -> {
                    AnalyzeQuestionInputV2 input =
                            mapper.treeToValue(request.input(), AnalyzeQuestionInputV2.class);
                    yield new QuestionAnalysisOutputV2(
                            "cover-generation-question-analysis-output-v2",
                            input.questionId(),
                            input.plan().questionType(),
                            "직무 역량을 검증하는 질문",
                            "검증된 문제 해결 행동으로 답한다",
                            input.plan().coreMessage(),
                            input.plan().requiredElements(),
                            input.plan().avoidContent(),
                            input.plan().narrativeFramework(),
                            15,
                            45,
                            25,
                            15,
                            "대안을 비교하고 선택한 개인 행동",
                            List.of("문제, 판단, 행동, 결과가 있는 경험"),
                            input.plan().requirementIndexes(),
                            input.plan().roleConnection(),
                            input.plan().companyConnection(),
                            "직무 적용 방향으로 마무리한다",
                            input.plan().headingPolicy());
                }
                case "cover-generation-allocation-output-v2" -> {
                    AllocateExperiencesInputV2 input = mapper.treeToValue(
                            request.input(), AllocateExperiencesInputV2.class);
                    yield new ExperienceAllocationOutputV2(
                            "cover-generation-allocation-output-v2",
                            input.candidates().stream()
                                    .map(candidate -> new ExperienceAllocationV2(
                                            candidate.questionId(),
                                            candidate.candidateEvidence().stream()
                                                    .map(value -> value.evidenceId())
                                                    .toList(),
                                            "검증된 후보가 하나뿐이다",
                                            "문항별 서로 다른 개인 행동을 강조한다"))
                                    .toList());
                }
                case "cover-generation-plan-output-v1" -> {
                    PlanQuestionsInput input =
                            mapper.treeToValue(request.input(), PlanQuestionsInput.class);
                    yield new PlanQuestionsOutput(
                            "cover-generation-plan-output-v1",
                            input.questions().stream()
                                    .map(question -> new QuestionPlan(
                                            question.questionId(),
                                            "문항 의도",
                                            List.of("Spring 경험"),
                                            List.of("과장"),
                                            List.of(0),
                                            Math.min(
                                                    200,
                                                    question.maxLength() == null
                                                            ? 200
                                                            : question.maxLength())))
                                    .toList(),
                            input.avoidExperienceDuplication());
                }
                case "cover-generation-question-analysis-output-v1" -> {
                    AnalyzeQuestionInput input =
                            mapper.treeToValue(request.input(), AnalyzeQuestionInput.class);
                    yield new QuestionAnalysisOutput(
                            "cover-generation-question-analysis-output-v1",
                            input.questionId(),
                            "문항 의도",
                            List.of("Spring 경험"),
                            List.of("과장"),
                            List.of(0));
                }
                case "cover-generation-allocation-output-v1" -> {
                    AllocateExperiencesInput input =
                            mapper.treeToValue(
                                    request.input(), AllocateExperiencesInput.class);
                    yield new ExperienceAllocationOutput(
                            "cover-generation-allocation-output-v1",
                            input.candidates().stream()
                                    .map(candidate -> new ExperienceAllocation(
                                            candidate.questionId(),
                                            candidate.evidenceIds(),
                                            input.avoidExperienceDuplication()
                                                            && input.candidates().size() > 1
                                                    ? "직무 연관성이 가장 높은 경험"
                                                    : null))
                                    .toList());
                }
                case "cover-generation-answer-output-v1" -> {
                    WriteAnswerInput input =
                            mapper.treeToValue(request.input(), WriteAnswerInput.class);
                    UUID evidenceId =
                            input.verifiedEvidence().getFirst().id();
                    yield new WrittenAnswerOutput(
                            "cover-generation-answer-output-v1",
                            input.questionId(),
                            providerDocument("Spring 서비스 성과를 만들었습니다."),
                            List.of(new EvidenceClaimDraft(
                                    evidenceId,
                                    "Spring 서비스 성과를 만들었습니다.")));
                }
                case "cover-generation-fact-check-output-v1" -> {
                    FactCheckAnswerInput input =
                            mapper.treeToValue(
                                    request.input(), FactCheckAnswerInput.class);
                    UUID evidenceId =
                            input.verifiedEvidence().getFirst().id();
                    yield new FactCheckAnswerOutput(
                            "cover-generation-fact-check-output-v1",
                            input.questionId(),
                            List.of(),
                            List.of(),
                            List.of(new VerifiedClaimDraft(
                                    "Spring 서비스 성과를 만들었습니다.",
                                    true,
                                    List.of(evidenceId))));
                }
                default -> throw new AssertionError(
                        "unexpected chat schema " + request.outputSchemaVersion());
            };
            return new AiGatewayResponse(mapper.writeValueAsString(output), java.util.List.of());
        }
    }

    private static final class FakeEmbedding implements EmbeddingGateway {

        private final ObjectMapper mapper;

        private FakeEmbedding(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public AiGatewayResponse embed(EmbeddingRequest request) {
            assertThat(request.providerKey()).isEqualTo("openai");
            assertThat(request.productKey()).isEqualTo("text-embedding-test");
            var output = mapper.createObjectNode();
            var vector = output.putArray("vectors").addArray();
            for (int index = 0; index < request.dimension(); index++) {
                vector.add(0.1D);
            }
            return new AiGatewayResponse(output.toString(), java.util.List.of());
        }
    }

    private static final class FakeEmbeddingPolicy
            implements JobAnalysisEmbeddingQueryPort {

        @Override
        public EmbeddingPolicySnapshot activePolicy() {
            return new EmbeddingPolicySnapshot(
                    1L, "openai", "text-embedding-test", 4, 1);
        }

        @Override
        public List<SimilarEvidenceChunk> exactCosineSearch(
                UUID userId,
                List<Double> queryVector,
                long policyVersion,
                int generation,
                int limit) {
            throw new AssertionError("generation uses the cover-letter query port");
        }
    }

    private static final class FakeQuery implements CoverLetterQueryPort {

        private final GenerationSnapshot snapshot;
        private final AtomicInteger snapshotLoads = new AtomicInteger();
        private final List<EvidenceSourceExcerpt> sourceExcerpts = new ArrayList<>();
        private final List<List<UUID>> excerptRequests = new ArrayList<>();
        private WritingInsights writingInsights = new WritingInsights(List.of(), List.of(), null, null);
        private boolean rejectSnapshotLoads;

        private FakeQuery(GenerationSnapshot snapshot) {
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
            if (rejectSnapshotLoads) {
                throw new AssertionError(
                        "APPLY completion must not reload the mutable snapshot");
            }
            snapshotLoads.incrementAndGet();
            return snapshot;
        }

        @Override
        public GenerationSnapshot loadGenerationSnapshotByModel(
                UUID userId,
                UUID coverLetterId,
                long expectedCoverLetterVersion,
                List<UUID> questionIds,
                List<UUID> preferredEvidenceIds,
                boolean avoidExperienceDuplication,
                String model,
                String expectedSnapshotHash) {
            if (rejectSnapshotLoads) {
                throw new AssertionError(
                        "APPLY completion must not reload the mutable snapshot");
            }
            snapshotLoads.incrementAndGet();
            assertThat(model).isEqualTo(snapshot.model());
            return snapshot;
        }

        @Override
        public GenerationSnapshot loadGenerationRetrySnapshot(
                UUID userId, UUID agentRunId, String expectedSnapshotHash) {
            throw new AssertionError("retry is not used in this fixture");
        }

        @Override
        public com.hiresemble.coverletter.application.model.CoverLetterModels
                        .VerificationSnapshot
                loadVerificationSnapshot(
                        UUID userId,
                        UUID answerVersionId,
                        AiQualityMode qualityMode,
                        String expectedSnapshotHash) {
            throw new AssertionError("verification is not used in this fixture");
        }

        @Override
        public com.hiresemble.coverletter.application.model.CoverLetterModels
                        .VerificationSnapshot
                loadVerificationRetrySnapshot(
                        UUID userId,
                        UUID agentRunId,
                        String expectedSnapshotHash) {
            throw new AssertionError("verification is not used in this fixture");
        }

        @Override
        public List<CandidateChunk> searchEvidenceCandidates(
                UUID userId, List<Double> queryVector, int limit) {
            return List.of(new CandidateChunk(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "masked candidate",
                    0.1D));
        }

        @Override
        public WritingInsights loadWritingInsights(
                UUID userId, UUID jobId, UUID analysisId, UUID coverLetterId) {
            assertThat(userId).isEqualTo(snapshot.userId());
            assertThat(jobId).isEqualTo(snapshot.job().jobId());
            assertThat(analysisId).isEqualTo(snapshot.job().analysisId());
            assertThat(coverLetterId).isEqualTo(snapshot.coverLetterId());
            return writingInsights;
        }

        @Override
        public List<EvidenceSourceExcerpt> findEvidenceSourceExcerpts(
                UUID userId, List<UUID> evidenceIds, int limit) {
            assertThat(userId).isEqualTo(snapshot.userId());
            excerptRequests.add(List.copyOf(evidenceIds));
            return sourceExcerpts.stream()
                    .filter(value -> evidenceIds.contains(value.evidenceId()))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class FakeCommand implements CoverLetterCommandPort {

        private final UUID userId;
        private final List<PersistGeneratedAnswer> commands = new ArrayList<>();
        private final List<Long> returnedCoverVersions = new ArrayList<>();

        private FakeCommand(UUID userId) {
            this.userId = userId;
        }

        @Override
        public AppliedAnswer applyGeneratedAnswer(
                UUID ownerId,
                UUID agentRunId,
                PersistGeneratedAnswer command) {
            assertThat(ownerId).isEqualTo(userId);
            commands.add(command);
            long coverVersion = command.expectedCoverLetterVersion() + commands.size();
            returnedCoverVersions.add(coverVersion);
            CoverLetterVersionSource source = command.expectedCurrentVersionId() == null
                    ? CoverLetterVersionSource.AI_GENERATED
                    : CoverLetterVersionSource.AI_REVISED;
            AnswerVersion answer = new AnswerVersion(
                    UUID.randomUUID(),
                    userId,
                    command.questionId(),
                    command.expectedCurrentVersionId(),
                    null,
                    1,
                    command.contentJson(),
                    "Spring 서비스 성과를 만들었습니다.",
                    21,
                    source,
                    true,
                    AnswerCreatedBy.AI,
                    NOW);
            Verification verification = new Verification(
                    UUID.randomUUID(),
                    userId,
                    answer.id(),
                    VerificationStatus.PASSED,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    agentRunId,
                    NOW);
            return new AppliedAnswer(answer, verification, coverVersion);
        }

        @Override
        public Verification persistVerification(
                UUID userId,
                UUID agentRunId,
                com.hiresemble.coverletter.application.model.CoverLetterModels
                                .PersistVerification
                        command) {
            throw new AssertionError("verification is not used in this fixture");
        }

        @Override
        public void failPendingVerification(UUID userId, UUID agentRunId) {
            throw new AssertionError("verification is not used in this fixture");
        }
    }
}
