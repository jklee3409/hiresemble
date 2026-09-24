package com.hiresemble.ai.evaluation;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.context.ContextBuilder.TruncationSummary;
import com.hiresemble.ai.evaluation.CoverLetterEvalCases.EvalCase;
import com.hiresemble.ai.evaluation.CoverLetterEvalCases.Evidence;
import com.hiresemble.ai.evaluation.CoverLetterQualityRubric.AnswerMetrics;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.prompt.CoverLetterGenerationV5PromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.prompt.PromptRegistry.PromptKey;
import com.hiresemble.ai.validation.StructuredOutputValidator;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.CoverLetterCallPolicy;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
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
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationQuestion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.JobContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistVerification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RequirementContext;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedEvidence;
import com.hiresemble.coverletter.application.model.CoverLetterModels.WritingInsights;
import com.hiresemble.coverletter.application.port.CoverLetterCommandPort;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.job.application.port.JobAnalysisEmbeddingQueryPort;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Runs one synthetic case through the active v5 cover-letter workflow, a single-prompt baseline
 * that mimics pasting the same material into a general chat model, and a blind pairwise judge.
 * Everything goes through {@link ChatGateway}, so CI uses a fake and the opt-in task a real one.
 */
public final class CoverLetterEvalHarness {

    public static final String EVAL_VERSION = "cover-letter-eval-v1";
    public static final String BASELINE_SCHEMA = "cover-letter-eval-baseline-output-v1";
    public static final String JUDGE_SCHEMA = "cover-letter-eval-judge-output-v1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String SNAPSHOT_HASH = "e".repeat(64);

    public record Settings(String model, String judgeModel, long priceVersion, BigDecimal maxCostUsd) {
        public Settings {
            OpenAiChatModels.requireCoverLetter(model);
            OpenAiChatModels.requireCoverLetter(judgeModel);
            if (priceVersion < 1 || maxCostUsd == null || maxCostUsd.signum() <= 0) {
                throw new IllegalArgumentException("evaluation settings are invalid");
            }
        }
    }

    public enum JudgePreference { A, B, TIE }

    public record JudgeAnswerScores(
            int questionFit,
            int specificity,
            int personalContribution,
            int roleCompanyFit,
            int credibility,
            int readability,
            int unsupportedClaimCount,
            String strongestPoint,
            String weakestPoint) {
        public double mean() {
            return Math.round((questionFit + specificity + personalContribution + roleCompanyFit
                            + credibility + readability) * 100.0 / 6) / 100.0;
        }
    }

    public record JudgeOutput(
            String schemaVersion,
            JudgeAnswerScores answerA,
            JudgeAnswerScores answerB,
            JudgePreference preferred,
            String rationale) {}

    public record BaselineAnswerOutput(String schemaVersion, String answerText) {}

    public record CaseResult(
            String caseId,
            String label,
            String question,
            Integer maxLength,
            String hiresembleAnswer,
            String baselineAnswer,
            AnswerMetrics hiresembleMetrics,
            AnswerMetrics baselineMetrics,
            JudgeAnswerScores hiresembleScores,
            JudgeAnswerScores baselineScores,
            String winner,
            String judgeRationale,
            boolean hiresembleShownAsA,
            BigDecimal costUsd,
            String failure) {}

    private final ChatGateway chat;
    private final ObjectMapper objectMapper;
    private final Settings settings;
    private final StructuredOutputValidator validator;
    private final PromptRegistry prompts;
    private BigDecimal spent = BigDecimal.ZERO;

    public CoverLetterEvalHarness(ChatGateway chat, ObjectMapper objectMapper, Settings settings) {
        this.chat = chat;
        this.objectMapper = objectMapper;
        this.settings = settings;
        this.validator = new StructuredOutputValidator(objectMapper);
        this.prompts = new PromptRegistry(CoverLetterGenerationV5PromptDefinitions.all());
    }

    /** Prompt metadata that registers the baseline and judge strict schemas with a real gateway. */
    public static List<PromptDefinition> promptDefinitions() {
        return List.of(
                evalPrompt("EVAL_BASELINE", BASELINE_SCHEMA, BaselineAnswerOutput.class, BASELINE_INSTRUCTIONS),
                evalPrompt("EVAL_JUDGE", JUDGE_SCHEMA, JudgeOutput.class, JUDGE_INSTRUCTIONS));
    }

    public BigDecimal spentUsd() {
        return spent;
    }

    public CaseResult run(EvalCase evalCase) {
        BigDecimal before = spent;
        List<String> evidenceTexts = evidenceTexts(evalCase);
        String hiresemble = null;
        String baseline = null;
        try {
            hiresemble = runWorkflow(evalCase);
            baseline = runBaseline(evalCase);
            boolean hiresembleFirst = Math.floorMod(evalCase.id().hashCode(), 2) == 0;
            JudgeOutput judged = judge(
                    evalCase, hiresembleFirst ? hiresemble : baseline, hiresembleFirst ? baseline : hiresemble);
            JudgeAnswerScores hiresembleScores = hiresembleFirst ? judged.answerA() : judged.answerB();
            JudgeAnswerScores baselineScores = hiresembleFirst ? judged.answerB() : judged.answerA();
            String winner = switch (judged.preferred()) {
                case TIE -> "TIE";
                case A -> hiresembleFirst ? "HIRESEMBLE" : "BASELINE";
                case B -> hiresembleFirst ? "BASELINE" : "HIRESEMBLE";
            };
            return new CaseResult(
                    evalCase.id(), evalCase.label(), evalCase.question(), evalCase.maxLength(),
                    hiresemble, baseline,
                    CoverLetterQualityRubric.measure(hiresemble, evalCase.maxLength(), evidenceTexts),
                    CoverLetterQualityRubric.measure(baseline, evalCase.maxLength(), evidenceTexts),
                    hiresembleScores, baselineScores, winner, judged.rationale(), hiresembleFirst,
                    spent.subtract(before), null);
        } catch (RuntimeException exception) {
            String failure = exception instanceof AiExecutionException ai
                    ? ai.safeCode()
                    : exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return new CaseResult(
                    evalCase.id(), evalCase.label(), evalCase.question(), evalCase.maxLength(),
                    hiresemble, baseline,
                    hiresemble == null ? null : CoverLetterQualityRubric.measure(hiresemble, evalCase.maxLength(), evidenceTexts),
                    baseline == null ? null : CoverLetterQualityRubric.measure(baseline, evalCase.maxLength(), evidenceTexts),
                    null, null, "FAILED", null, false, spent.subtract(before), failure);
        }
    }

    // ---- v5 workflow -----------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String runWorkflow(EvalCase evalCase) {
        Fixture fixture = fixture(evalCase);
        CoverLetterGenerationWorkflow workflow = new CoverLetterGenerationWorkflow(
                fixture.query(), fixture.command(), new EvalEmbeddingPolicy(), objectMapper);
        AgentRunSnapshot run = run(fixture.snapshot());
        ModelRoute route = new ModelRoute(
                1L,
                OpenAiChatModels.requireCoverLetter(settings.model()).tier(),
                "openai",
                settings.model(),
                false);
        Map<String, JsonNode> upstream = new HashMap<>();
        Map<String, Object> ephemeral = new HashMap<>();
        for (ExecutableWorkflowStep step : workflow.v5Contribution().steps()) {
            WorkflowStepExecutor executor = step.executor();
            List<StepInput> inputs = executor.prepareInputs(context(run, upstream, ephemeral, null));
            for (StepInput input : inputs) {
                StepExecutionContext scoped = context(run, upstream, ephemeral, input.scopeKey());
                Object output = invokeWithCorrection(step, input, scoped, route);
                JsonNode minimal = executor.minimalOutput(output, objectMapper, scoped);
                DomainStepCompletion completion = executor.completeFresh(output, minimal, scoped);
                String key = StepExecutionContext.outputKey(step.stepKey(), input.scopeKey());
                upstream.put(key, completion.minimalOutput());
                ephemeral.put(key, executor.ephemeralOutput(output, scoped));
            }
        }
        if (fixture.command().applied.isEmpty()) {
            throw new IllegalStateException("workflow did not apply an answer");
        }
        return plainText(fixture.command().applied.getLast().contentJson());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object invokeWithCorrection(
            ExecutableWorkflowStep step, StepInput input, StepExecutionContext context, ModelRoute route) {
        PromptDefinition prompt = prompts.require(
                WorkflowType.COVER_LETTER_GENERATION,
                CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION,
                step.stepKey());
        String guidance = null;
        for (int attempt = 1; ; attempt++) {
            requireBudget();
            AiGatewayResponse response = step.executor().invoke(new GatewayInvocation(
                    input,
                    route,
                    withCorrection(prompt, guidance),
                    this::countedChat,
                    request -> {
                        throw new IllegalStateException("embedding is not used for single-question evaluation");
                    },
                    request -> {
                        throw new IllegalStateException("web search is not allowed");
                    },
                    context));
            try {
                return validator.validate(response.rawJson(), step.executor().outputContract(context));
            } catch (AiExecutionException exception) {
                if (attempt >= 2 || !exception.isSemanticCorrectionFailure()) throw exception;
                guidance = exception.correctionGuidance();
            }
        }
    }

    private PromptDefinition withCorrection(PromptDefinition prompt, String guidance) {
        if (guidance == null) return prompt;
        return new PromptDefinition(
                prompt.key(), prompt.promptVersion(), prompt.inputType(), prompt.outputType(),
                prompt.outputSchemaVersion(), prompt.toolAllowlist(), prompt.maxInputTokens(),
                prompt.maxOutputTokens(), prompt.maxModelCalls(),
                prompt.instructions() + "\nCorrection for this bounded retry:\n" + guidance);
    }

    // ---- baseline and judge ----------------------------------------------------------------

    private String runBaseline(EvalCase evalCase) {
        ObjectNode input = objectMapper.createObjectNode()
                .put("company", evalCase.company())
                .put("jobTitle", evalCase.jobTitle())
                .put("jobPosting", evalCase.jobDescription())
                .put("question", evalCase.question())
                .put("memo", evalCase.memo());
        if (evalCase.maxLength() == null) input.putNull("maxLength");
        else input.put("maxLength", evalCase.maxLength());
        var requirements = input.putArray("requirements");
        evalCase.requirements().forEach(value -> requirements.add(value.text()));
        input.put("myMaterials", rawMaterials(evalCase));
        input.put("companyInfo", companyInfo(evalCase));
        var tier = OpenAiChatModels.requireCoverLetter(settings.model()).tier();
        BaselineAnswerOutput output = callStrict(
                settings.model(), "EVAL_BASELINE", BASELINE_SCHEMA, BaselineAnswerOutput.class, input,
                CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.DRAFT_ANSWER, tier), 16_000);
        if (!BASELINE_SCHEMA.equals(output.schemaVersion())
                || output.answerText() == null || output.answerText().isBlank()) {
            throw new IllegalStateException("baseline answer is invalid");
        }
        return output.answerText().strip();
    }

    private JudgeOutput judge(EvalCase evalCase, String answerA, String answerB) {
        ObjectNode input = objectMapper.createObjectNode()
                .put("company", evalCase.company())
                .put("jobTitle", evalCase.jobTitle())
                .put("jobPosting", evalCase.jobDescription())
                .put("question", evalCase.question());
        if (evalCase.maxLength() == null) input.putNull("maxLength");
        else input.put("maxLength", evalCase.maxLength());
        var requirements = input.putArray("requirements");
        evalCase.requirements().forEach(value -> requirements.add(value.text()));
        var facts = input.putArray("candidateFacts");
        evidenceTexts(evalCase).forEach(facts::add);
        input.put("companyFacts", companyInfo(evalCase));
        input.putObject("answerA")
                .put("text", answerA)
                .put("characterCount", answerA.codePointCount(0, answerA.length()));
        input.putObject("answerB")
                .put("text", answerB)
                .put("characterCount", answerB.codePointCount(0, answerB.length()));
        var tier = OpenAiChatModels.requireCoverLetter(settings.judgeModel()).tier();
        JudgeOutput output = callStrict(
                settings.judgeModel(), "EVAL_JUDGE", JUDGE_SCHEMA, JudgeOutput.class, input,
                CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.REVIEW_ANSWER, tier), 12_000);
        if (!JUDGE_SCHEMA.equals(output.schemaVersion())
                || output.preferred() == null
                || !validScores(output.answerA())
                || !validScores(output.answerB())) {
            throw new IllegalStateException("judge output is invalid");
        }
        return output;
    }

    private boolean validScores(JudgeAnswerScores scores) {
        return scores != null
                && List.of(scores.questionFit(), scores.specificity(), scores.personalContribution(),
                                scores.roleCompanyFit(), scores.credibility(), scores.readability())
                        .stream().allMatch(value -> value >= 1 && value <= 5)
                && scores.unsupportedClaimCount() >= 0;
    }

    private <T> T callStrict(
            String model,
            String stepKey,
            String schemaVersion,
            Class<T> type,
            JsonNode input,
            CoverLetterCallPolicy.CallProfile profile,
            int maxOutputTokens) {
        requireBudget();
        PromptDefinition prompt = promptDefinitions().stream()
                .filter(value -> value.key().stepKey().equals(stepKey))
                .findFirst()
                .orElseThrow();
        AiGatewayResponse response = countedChat(new ChatGateway.ChatRequest(
                "openai",
                model,
                prompt.promptVersion(),
                prompt.instructions(),
                input,
                schemaVersion,
                Set.of(),
                0,
                profile.timeout(),
                settings.priceVersion(),
                maxOutputTokens,
                type,
                profile.reasoningEffort(),
                null));
        return objectMapper.readValue(response.rawJson(), type);
    }

    private AiGatewayResponse countedChat(ChatGateway.ChatRequest request) {
        AiGatewayResponse response = chat.chat(request);
        for (AiUsage usage : response.usages()) {
            spent = spent.add(usage.costUsd());
        }
        return response;
    }

    private void requireBudget() {
        if (spent.compareTo(settings.maxCostUsd()) >= 0) {
            throw new IllegalStateException("evaluation cost cap reached");
        }
    }

    private static PromptDefinition evalPrompt(
            String stepKey, String schemaVersion, Class<?> outputType, String instructions) {
        return new PromptDefinition(
                new PromptKey(WorkflowType.COVER_LETTER_GENERATION, EVAL_VERSION, stepKey),
                EVAL_VERSION + "-" + stepKey.toLowerCase(java.util.Locale.ROOT),
                JsonNode.class,
                outputType,
                schemaVersion,
                Set.of(),
                40_000,
                16_000,
                1,
                instructions);
    }

    /** Mimics a user pasting the same material into a general chat model with a plain request. */
    static final String BASELINE_INSTRUCTIONS = """
            Set schemaVersion to exactly cover-letter-eval-baseline-output-v1.
            아래 자료를 바탕으로 자기소개서 문항에 대한 답변을 한국어로 작성해 줘. 채용 담당자가 읽었을 때
            좋은 인상을 줄 수 있게 써 주고, maxLength가 있으면 공백 포함 글자 수가 그 값을 넘지 않게 해 줘.
            memo가 있으면 참고해 줘. 자료에 없는 사실은 지어내지 마. answerText에는 답변 본문만 넣어 줘.
            """;

    static final String JUDGE_INSTRUCTIONS = """
            Set schemaVersion to exactly cover-letter-eval-judge-output-v1. You are a senior hiring
            manager and HR screener for the supplied company and job. Two anonymous Korean cover-letter
            answers, answerA and answerB, respond to the same question; their order carries no meaning.
            candidateFacts are the only true information about the applicant; company facts may come
            only from jobPosting and companyFacts. Score each answer from 1 to 5 on questionFit (directly
            answers the question), specificity (concrete situation, decisions, and results),
            personalContribution (the applicant's own actions and judgment), roleCompanyFit (connection to
            the requirements and supported company facts), credibility (no exaggeration and no claim
            beyond candidateFacts), and readability (clear opening, flow, natural Korean). Count claims not
            supported by candidateFacts in unsupportedClaimCount. An answer above maxLength is a severe
            flaw: cap its credibility and readability at 2. An answer far below maxLength reads as low
            effort, but never reward length alone. strongestPoint and weakestPoint are one Korean
            sentence each. Choose preferred as the answer you would advance to an interview, TIE only when
            they are genuinely equivalent, and explain in at most three Korean sentences in rationale.
            """;

    // ---- fixture ---------------------------------------------------------------------------

    private record Fixture(GenerationSnapshot snapshot, EvalQuery query, EvalCommand command) {}

    private Fixture fixture(EvalCase evalCase) {
        UUID userId = UUID.nameUUIDFromBytes(("user-" + evalCase.id()).getBytes(StandardCharsets.UTF_8));
        List<VerifiedEvidence> evidence = new ArrayList<>();
        List<EvidenceSourceExcerpt> excerpts = new ArrayList<>();
        for (int index = 0; index < evalCase.evidence().size(); index++) {
            Evidence value = evalCase.evidence().get(index);
            UUID evidenceId = UUID.nameUUIDFromBytes(
                    (evalCase.id() + "-evidence-" + index).getBytes(StandardCharsets.UTF_8));
            evidence.add(new VerifiedEvidence(
                    evidenceId, EvidenceSourceType.EXPERIENCE, UUID.randomUUID(), null,
                    value.category(), value.title(), value.content(), 1L));
            if (value.sourceExcerpt() != null && !value.sourceExcerpt().isBlank()) {
                excerpts.add(new EvidenceSourceExcerpt(
                        evidenceId, UUID.randomUUID(), UUID.randomUUID(), 0, value.sourceExcerpt()));
            }
        }
        GenerationSnapshot snapshot = new GenerationSnapshot(
                userId,
                UUID.nameUUIDFromBytes(("cover-" + evalCase.id()).getBytes(StandardCharsets.UTF_8)),
                1L,
                evalCase.company() + " 자기소개서",
                new JobContext(
                        UUID.nameUUIDFromBytes(("job-" + evalCase.id()).getBytes(StandardCharsets.UTF_8)),
                        1L,
                        evalCase.company(),
                        evalCase.jobTitle(),
                        evalCase.jobTitle(),
                        evalCase.jobDescription(),
                        UUID.nameUUIDFromBytes(("analysis-" + evalCase.id()).getBytes(StandardCharsets.UTF_8)),
                        1,
                        false,
                        evalCase.requirements().stream()
                                .map(value -> new RequirementContext(
                                        value.category(), value.text(), value.required(), "requirements"))
                                .toList()),
                List.of(new GenerationQuestion(
                        UUID.nameUUIDFromBytes(("question-" + evalCase.id()).getBytes(StandardCharsets.UTF_8)),
                        1,
                        evalCase.question(),
                        evalCase.maxLength(),
                        evalCase.memo(),
                        null,
                        null)),
                evidence,
                List.of(),
                true,
                (AiQualityMode) null,
                settings.model(),
                SNAPSHOT_HASH);
        CoverLetterEvalCases.Analysis analysis = evalCase.analysis();
        CoverLetterEvalCases.CompanyResearch research = evalCase.companyResearch();
        WritingInsights insights = new WritingInsights(
                analysis == null ? List.of() : analysis.strengths(),
                analysis == null ? List.of() : analysis.gaps(),
                analysis == null ? null : analysis.summary(),
                research == null
                        ? null
                        : new CompanyResearch(
                                research.summary(),
                                research.sources().stream()
                                        .map(source -> new CompanyResearchSource(
                                                source.sourceType(), source.title(),
                                                source.snippet(), source.reliabilityNotice()))
                                        .toList()));
        return new Fixture(snapshot, new EvalQuery(snapshot, insights, excerpts), new EvalCommand(userId));
    }

    private AgentRunSnapshot run(GenerationSnapshot snapshot) {
        ObjectNode input = objectMapper.createObjectNode()
                .put("coverLetterId", snapshot.coverLetterId().toString())
                .put("coverLetterVersion", snapshot.coverLetterVersion())
                .put("snapshotHash", snapshot.snapshotHash())
                .put("model", snapshot.model())
                .put("avoidExperienceDuplication", snapshot.avoidExperienceDuplication());
        var questionIds = input.putArray("questionIds");
        snapshot.questions().forEach(value -> questionIds.add(value.questionId().toString()));
        input.putArray("preferredEvidenceIds");
        UUID runId = UUID.randomUUID();
        return new AgentRunSnapshot(
                runId, snapshot.userId(), WorkflowType.COVER_LETTER_GENERATION, AgentRunStatus.RUNNING,
                null, 0, CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION, "f".repeat(64),
                input, 1L, settings.priceVersion(), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "COVER_LETTER", snapshot.coverLetterId(), null, runId, 1, false, null,
                null, UUID.randomUUID(), "eval-worker", NOW.plusSeconds(60), NOW, null, null, 1L, NOW,
                NOW, null, NOW, List.of());
    }

    private StepExecutionContext context(
            AgentRunSnapshot run, Map<String, JsonNode> upstream, Map<String, Object> ephemeral, String scope) {
        StepExecutionContext context = new StepExecutionContext(
                run,
                new ContextSnapshot(
                        run.userId(), List.of(), List.of(), List.of(),
                        new TruncationSummary(0, 0, List.of()), SNAPSHOT_HASH,
                        "CURRENT_VERIFIED_EVIDENCE_ONLY", 1L, true, true),
                upstream,
                ephemeral);
        return scope == null ? context : context.forScope(scope);
    }

    private static List<String> evidenceTexts(EvalCase evalCase) {
        List<String> values = new ArrayList<>();
        evalCase.evidence().forEach(value -> {
            values.add(value.title() + ": " + value.content());
            if (value.sourceExcerpt() != null) values.add(value.sourceExcerpt());
        });
        return values;
    }

    private static String rawMaterials(EvalCase evalCase) {
        StringBuilder text = new StringBuilder();
        for (Evidence value : evalCase.evidence()) {
            text.append("[").append(value.title()).append("]\n")
                    .append(value.content()).append('\n');
            if (value.sourceExcerpt() != null) text.append(value.sourceExcerpt()).append('\n');
            text.append('\n');
        }
        return text.toString().strip();
    }

    private static String companyInfo(EvalCase evalCase) {
        if (evalCase.companyResearch() == null) return "";
        StringBuilder text = new StringBuilder(evalCase.companyResearch().summary() == null
                ? "" : evalCase.companyResearch().summary());
        evalCase.companyResearch().sources().forEach(source -> text.append("\n- ")
                .append(source.title()).append(": ").append(source.snippet()));
        return text.toString().strip();
    }

    static String plainText(TipTapDocumentDto document) {
        StringBuilder text = new StringBuilder();
        for (TipTapNodeDto paragraph : document.content()) {
            if (!text.isEmpty()) text.append('\n');
            for (TipTapNodeDto node : paragraph.content()) {
                if ("hardBreak".equals(node.type())) text.append('\n');
                else if (node.text() != null) text.append(node.text());
            }
        }
        return text.toString();
    }

    private static final class EvalQuery implements CoverLetterQueryPort {
        private final GenerationSnapshot snapshot;
        private final WritingInsights insights;
        private final List<EvidenceSourceExcerpt> excerpts;

        private EvalQuery(GenerationSnapshot snapshot, WritingInsights insights, List<EvidenceSourceExcerpt> excerpts) {
            this.snapshot = snapshot;
            this.insights = insights;
            this.excerpts = List.copyOf(excerpts);
        }

        @Override
        public GenerationSnapshot loadGenerationSnapshotByModel(
                UUID userId, UUID coverLetterId, long expectedCoverLetterVersion, List<UUID> questionIds,
                List<UUID> preferredEvidenceIds, boolean avoidExperienceDuplication, String model,
                String expectedSnapshotHash) {
            return snapshot;
        }

        @Override
        public WritingInsights loadWritingInsights(UUID userId, UUID jobId, UUID analysisId, UUID coverLetterId) {
            return insights;
        }

        @Override
        public List<EvidenceSourceExcerpt> findEvidenceSourceExcerpts(UUID userId, List<UUID> evidenceIds, int limit) {
            return excerpts.stream().filter(value -> evidenceIds.contains(value.evidenceId())).limit(limit).toList();
        }

        @Override
        public GenerationSnapshot loadGenerationSnapshot(
                UUID userId, UUID coverLetterId, long expectedCoverLetterVersion, List<UUID> questionIds,
                List<UUID> preferredEvidenceIds, boolean avoidExperienceDuplication, AiQualityMode qualityMode,
                String expectedSnapshotHash) {
            throw new UnsupportedOperationException("quality-mode generation is not evaluated");
        }

        @Override
        public GenerationSnapshot loadGenerationRetrySnapshot(UUID userId, UUID agentRunId, String expectedSnapshotHash) {
            throw new UnsupportedOperationException("retry is not evaluated");
        }

        @Override
        public VerificationSnapshot loadVerificationSnapshot(
                UUID userId, UUID answerVersionId, AiQualityMode qualityMode, String expectedSnapshotHash) {
            throw new UnsupportedOperationException("verification is not evaluated");
        }

        @Override
        public VerificationSnapshot loadVerificationRetrySnapshot(
                UUID userId, UUID agentRunId, String expectedSnapshotHash) {
            throw new UnsupportedOperationException("verification is not evaluated");
        }

        @Override
        public List<CandidateChunk> searchEvidenceCandidates(UUID userId, List<Double> queryVector, int limit) {
            return List.of();
        }
    }

    private static final class EvalCommand implements CoverLetterCommandPort {
        private final UUID userId;
        private final List<PersistGeneratedAnswer> applied = new ArrayList<>();

        private EvalCommand(UUID userId) {
            this.userId = userId;
        }

        @Override
        public AppliedAnswer applyGeneratedAnswer(UUID ownerId, UUID agentRunId, PersistGeneratedAnswer command) {
            applied.add(command);
            AnswerVersion answer = new AnswerVersion(
                    UUID.randomUUID(), userId, command.questionId(), null, null, 1,
                    command.contentJson(), plainText(command.contentJson()), 0,
                    CoverLetterVersionSource.AI_GENERATED, true, AnswerCreatedBy.AI, NOW);
            Verification verification = new Verification(
                    UUID.randomUUID(), userId, answer.id(), VerificationStatus.PASSED,
                    List.of(), List.of(), List.of(), List.of(), agentRunId, NOW);
            return new AppliedAnswer(answer, verification, command.expectedCoverLetterVersion() + 1);
        }

        @Override
        public Verification persistVerification(UUID userId, UUID agentRunId, PersistVerification command) {
            throw new UnsupportedOperationException("verification is not evaluated");
        }

        @Override
        public void failPendingVerification(UUID userId, UUID agentRunId) {
            throw new UnsupportedOperationException("verification is not evaluated");
        }
    }

    private static final class EvalEmbeddingPolicy implements JobAnalysisEmbeddingQueryPort {
        @Override
        public EmbeddingPolicySnapshot activePolicy() {
            return new EmbeddingPolicySnapshot(1L, "openai", "text-embedding-3-small", 1536, 1);
        }

        @Override
        public List<SimilarEvidenceChunk> exactCosineSearch(
                UUID userId, List<Double> queryVector, long policyVersion, int generation, int limit) {
            throw new UnsupportedOperationException("embedding search is not evaluated");
        }
    }
}
