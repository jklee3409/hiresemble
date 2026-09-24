package com.hiresemble.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.BaselineAnswerOutput;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.CaseResult;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.JudgeAnswerScores;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.JudgeOutput;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.JudgePass;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.JudgePreference;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.PreferenceMargin;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.Settings;
import com.hiresemble.ai.evaluation.CoverLetterQualityRubric.AnswerMetrics;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.DraftAnswerInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.DraftAnswerOutputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.GroundedAnswerOutputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.HeadingPolicy;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.NarrativeFramework;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.PlanQuestionsOutputV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionPlanV3;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.QuestionType;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewAnswerInputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewAnswerOutputV5;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewCriterion;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.ReviewScoreV5;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionPlan;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionType;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises the evaluation harness end to end with a deterministic fake gateway (no network). */
class CoverLetterEvalHarnessTest {

    private static final BigDecimal CALL_COST = new BigDecimal("0.001000");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir Path reportDirectory;

    @Test
    void everySyntheticCaseRunsWorkflowBaselineAndTwoSwappedJudgePasses() throws Exception {
        FakeGateway gateway = new FakeGateway(objectMapper, JudgeMode.PREFER_CONTENT);
        CoverLetterEvalHarness harness = new CoverLetterEvalHarness(gateway, objectMapper, settings("5.000000"));
        List<CoverLetterEvalCases.EvalCase> cases = CoverLetterEvalCases.load(objectMapper);

        List<CaseResult> results = cases.stream().map(harness::run).toList();

        assertThat(cases).hasSize(5);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.failure()).isNull();
            assertThat(result.judgePasses()).extracting(JudgePass::hiresembleShownAsA).containsExactly(true, false);
            assertThat(result.positionConsistent()).isTrue();
            assertThat(result.winner()).isEqualTo("HIRESEMBLE");
            assertThat(result.hiresembleScores().criteria()).hasSize(CoverLetterEvalHarness.CRITERIA.size());
            assertThat(result.hiresembleScores().mean()).isEqualTo(4.0);
            assertThat(result.baselineScores().mean()).isEqualTo(3.0);
            assertThat(result.hiresembleMetrics().withinLimit()).isTrue();
            assertThat(result.hiresembleMetrics().fillRatio()).isGreaterThanOrEqualTo(0.7);
            assertThat(result.baselineMetrics().clicheHits()).contains("어릴 때부터");
            assertThat(result.baselineMetrics().unsupportedNumbers()).contains("99%");
        });
        assertThat(gateway.judgeInputs).hasSize(10).allSatisfy(input -> {
            assertThat(input.toString()).doesNotContain("Hiresemble", "baseline", "HIRESEMBLE");
            assertThat(input.has("applicantDirection")).isTrue();
        });
        assertThat(gateway.judgeInputs)
                .anySatisfy(input -> assertThat(input.path("applicantDirection").asText()).contains("성능 개선"));
        assertThat(gateway.judgeRequests).allSatisfy(request -> {
            assertThat(request.productKey()).isEqualTo(OpenAiChatModels.GPT_5_6_SOL);
            assertThat(request.reasoningEffort()).isEqualTo("high");
        });
        assertThat(gateway.schemas).contains(
                "cover-generation-plan-output-v3",
                "cover-generation-draft-output-v1",
                "cover-generation-review-output-v1",
                "cover-generation-grounding-output-v1",
                CoverLetterEvalHarness.BASELINE_SCHEMA,
                CoverLetterEvalHarness.JUDGE_SCHEMA);
        assertThat(harness.spentUsd()).isEqualByComparingTo(CALL_COST.multiply(BigDecimal.valueOf(gateway.calls)));

        CoverLetterEvalReport.Report report = CoverLetterEvalReport.write(
                reportDirectory, settings("5.000000"), results, harness.spentUsd(), objectMapper);
        assertThat(report.summary().completedCount()).isEqualTo(5);
        assertThat(report.summary().hiresembleWins()).isEqualTo(5);
        assertThat(report.summary().positionConsistencyRate()).isEqualTo(1.0);
        assertThat(report.summary().scoreSaturationRate()).isZero();
        assertThat(report.summary().criterionDeltas()).containsEntry("concision", 1.0);
        assertThat(report.summary().hiresembleMeanFillRatio())
                .isGreaterThan(report.summary().baselineMeanFillRatio());
        assertThat(Files.readString(reportDirectory.resolve("report.md")))
                .contains("Consistent verdicts", "Criterion deltas", "report.json")
                .doesNotContain("Position consistency 0");
        assertThat(objectMapper.readTree(Files.readString(reportDirectory.resolve("report.json")))
                        .path("cases"))
                .hasSize(5);
    }

    @Test
    void positionBiasedAndSaturatedJudgeBecomesTieWithWarnings() {
        FakeGateway gateway = new FakeGateway(objectMapper, JudgeMode.ALWAYS_A_ALL_FIVES);
        Settings sameJudge = new Settings(
                OpenAiChatModels.GPT_5_6_TERRA, OpenAiChatModels.GPT_5_6_TERRA, 2026080601L, new BigDecimal("5.000000"));
        CoverLetterEvalHarness harness = new CoverLetterEvalHarness(gateway, objectMapper, sameJudge);

        List<CaseResult> results = CoverLetterEvalCases.load(objectMapper).stream().map(harness::run).toList();
        CoverLetterEvalReport.Summary summary =
                CoverLetterEvalReport.summarize(sameJudge, results, harness.spentUsd());

        assertThat(results).allSatisfy(result -> {
            assertThat(result.positionConsistent()).isFalse();
            assertThat(result.winner()).isEqualTo("TIE");
        });
        assertThat(summary.ties()).isEqualTo(5);
        assertThat(summary.scoreSaturationRate()).isEqualTo(1.0);
        assertThat(summary.warnings()).anySatisfy(value -> assertThat(value).contains("saturation"))
                .anySatisfy(value -> assertThat(value).contains("Position consistency"))
                .anySatisfy(value -> assertThat(value).contains("self-preference"))
                .anySatisfy(value -> assertThat(value).contains("Fewer than 10"));
    }

    @Test
    void defaultJudgeDiffersFromWorkflowModel() {
        assertThat(Settings.defaultJudgeFor(OpenAiChatModels.GPT_5_6_TERRA)).isEqualTo(OpenAiChatModels.GPT_5_6_SOL);
        assertThat(Settings.defaultJudgeFor(OpenAiChatModels.GPT_5_6_SOL)).isEqualTo(OpenAiChatModels.GPT_5_6_TERRA);
    }

    @Test
    void costCapStopsFurtherCasesWithoutHidingTheFailure() {
        FakeGateway gateway = new FakeGateway(objectMapper, JudgeMode.PREFER_CONTENT);
        CoverLetterEvalHarness harness = new CoverLetterEvalHarness(gateway, objectMapper, settings("0.004000"));
        List<CoverLetterEvalCases.EvalCase> cases = CoverLetterEvalCases.load(objectMapper);

        CaseResult first = harness.run(cases.get(0));
        CaseResult second = harness.run(cases.get(1));

        assertThat(first.failure()).contains("cost cap");
        assertThat(second.failure()).contains("cost cap");
        assertThat(gateway.calls).isEqualTo(4);
    }

    @Test
    void rubricFlagsOverflowUnderfillMarkdownAndUnsupportedNumbers() {
        AnswerMetrics metrics = CoverLetterQualityRubric.measure(
                "## 제목\n저는 어릴 때부터 성실했고 응답 시간을 30% 줄였습니다.",
                20,
                List.of("응답 시간을 개선했습니다."));

        assertThat(metrics.withinLimit()).isFalse();
        assertThat(metrics.fillRatio()).isGreaterThan(1.0);
        assertThat(metrics.markdownDetected()).isTrue();
        assertThat(metrics.clicheHits()).containsExactly("어릴 때부터");
        assertThat(metrics.unsupportedNumbers()).containsExactly("30%");
        assertThat(metrics.firstPersonCount()).isEqualTo(1);
        assertThat(CoverLetterQualityRubric.measure("근거 있는 답변", null, List.of()).fillRatio()).isNull();
    }

    private Settings settings(String maxCost) {
        return new Settings(
                OpenAiChatModels.GPT_5_6_TERRA, OpenAiChatModels.GPT_5_6_SOL, 2026080601L, new BigDecimal(maxCost));
    }

    private enum JudgeMode { PREFER_CONTENT, ALWAYS_A_ALL_FIVES }

    private static final class FakeGateway implements ChatGateway {
        private final ObjectMapper mapper;
        private final JudgeMode mode;
        private final List<String> schemas = new ArrayList<>();
        private final List<JsonNode> judgeInputs = new ArrayList<>();
        private final List<ChatRequest> judgeRequests = new ArrayList<>();
        private int calls;

        private FakeGateway(ObjectMapper mapper, JudgeMode mode) {
            this.mapper = mapper;
            this.mode = mode;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls++;
            schemas.add(request.outputSchemaVersion());
            Object output = switch (request.outputSchemaVersion()) {
                case "cover-generation-plan-output-v3" -> plan(mapper.treeToValue(request.input(), PlanQuestionsInputV5.class));
                case "cover-generation-draft-output-v1" -> {
                    DraftAnswerInputV5 input = mapper.treeToValue(request.input(), DraftAnswerInputV5.class);
                    yield new DraftAnswerOutputV5(
                            "cover-generation-draft-output-v1", input.questionId(), answer(input.targetCharacterCount()));
                }
                case "cover-generation-review-output-v1" -> {
                    ReviewAnswerInputV5 input = mapper.treeToValue(request.input(), ReviewAnswerInputV5.class);
                    yield new ReviewAnswerOutputV5(
                            "cover-generation-review-output-v1",
                            input.questionId(),
                            Arrays.stream(ReviewCriterion.values())
                                    .map(value -> new ReviewScoreV5(value, 4, "근거가 구체적입니다."))
                                    .toList(),
                            List.of(),
                            input.draftAnswerText());
                }
                case "cover-generation-grounding-output-v1" -> new GroundedAnswerOutputV5(
                        "cover-generation-grounding-output-v1",
                        UUID.fromString(request.input().path("questionId").asText()),
                        List.of());
                case CoverLetterEvalHarness.BASELINE_SCHEMA -> new BaselineAnswerOutput(
                        CoverLetterEvalHarness.BASELINE_SCHEMA,
                        "저는 어릴 때부터 성실했고 성능을 99% 개선한 경험이 있습니다.");
                case CoverLetterEvalHarness.JUDGE_SCHEMA -> {
                    judgeInputs.add(request.input());
                    judgeRequests.add(request);
                    if (mode == JudgeMode.ALWAYS_A_ALL_FIVES) {
                        yield new JudgeOutput(
                                CoverLetterEvalHarness.JUDGE_SCHEMA, scores(5), scores(5),
                                JudgePreference.A, PreferenceMargin.SLIGHT, "A를 선택합니다.");
                    }
                    boolean hiresembleIsA = request.input().path("answerA").path("text").asText()
                            .startsWith("제가 맡은 문제");
                    yield new JudgeOutput(
                            CoverLetterEvalHarness.JUDGE_SCHEMA,
                            scores(hiresembleIsA ? 4 : 3),
                            scores(hiresembleIsA ? 3 : 4),
                            hiresembleIsA ? JudgePreference.A : JudgePreference.B,
                            PreferenceMargin.CLEAR,
                            "구체적인 판단 과정이 드러난 답변을 선택합니다.");
                }
                default -> throw new AssertionError("unexpected schema " + request.outputSchemaVersion());
            };
            return new AiGatewayResponse(
                    mapper.writeValueAsString(output),
                    List.of(new AiUsage(
                            com.hiresemble.agentrun.domain.model.UsageType.CHAT,
                            "openai",
                            request.productKey(),
                            100,
                            0,
                            100,
                            0,
                            0,
                            request.priceVersion(),
                            UUID.randomUUID(),
                            CALL_COST,
                            1,
                            null)));
        }

        private PlanQuestionsOutputV3 plan(PlanQuestionsInputV5 input) {
            return new PlanQuestionsOutputV3(
                    "cover-generation-plan-output-v3",
                    input.questions().stream()
                            .map(question -> new QuestionPlanV3(
                                    question.questionId(),
                                    QuestionType.ROLE_COMPETENCY,
                                    "검증된 경험으로 직무 역량을 보여 준다",
                                    NarrativeFramework.COMPETENCY_EVIDENCE_APPLICATION,
                                    "질문에 직접 답한다",
                                    List.of("개인 행동"),
                                    List.of("근거 없는 수치"),
                                    List.of(),
                                    null,
                                    null,
                                    List.of("구체적인 경험"),
                                    question.maxLength() == null ? 900 : question.maxLength() * 9 / 10,
                                    HeadingPolicy.OPTIONAL,
                                    List.of(new NarrativeSectionPlan(
                                            NarrativeSectionType.DIRECT_ANSWER, "직접 답한다", 70))))
                            .toList(),
                    input.avoidExperienceDuplication());
        }

        private String answer(int target) {
            StringBuilder text = new StringBuilder("제가 맡은 문제를 원인부터 추적해 해결했습니다.");
            String filler = " 판단 근거와 결과를 구체적으로 설명했습니다.";
            while (text.codePointCount(0, text.length()) + filler.codePointCount(0, filler.length()) <= target) {
                text.append(filler);
            }
            return text.toString();
        }

        private JudgeAnswerScores scores(int value) {
            return new JudgeAnswerScores(
                    value, value, value, value, value, value, value, 0, "직접적인 답변입니다.", "회사 연결이 약합니다.");
        }
    }
}
