package com.hiresemble.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.CaseResult;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.Settings;
import com.hiresemble.ai.infrastructure.SpringAiOpenAiChatGateway;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort;
import com.hiresemble.ai.prompt.CoverLetterGenerationV5PromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.validation.OpenAiStrictSchemaCompatibilityValidator;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaGenerator;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaRegistry;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in, paid A/B evaluation on synthetic cases. It only runs through the
 * {@code coverLetterQualityEvaluation} Gradle task with explicit environment approval and a USD cap;
 * it is never part of test, check, CI, or E2E.
 */
class CoverLetterQualityEvaluationTest extends PostgresIntegrationTest {

    private static final Path REPORT = Path.of("build", "reports", "cover-letter-eval");

    @Autowired private OpenAiChatModel chatModel;
    @Autowired private AiPriceCatalogQueryPort priceCatalog;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void compareV5WorkflowWithDirectBaseline() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("COVER_LETTER_EVAL_ENABLED")),
                "IMPLEMENTED_NOT_LIVE_VERIFIED: cover-letter evaluation gate is disabled");
        assumeTrue(
                System.getenv("AI_PROVIDER_API_KEY") != null && !System.getenv("AI_PROVIDER_API_KEY").isBlank(),
                "IMPLEMENTED_NOT_LIVE_VERIFIED: provider key is absent");
        String model = env("COVER_LETTER_EVAL_MODEL", OpenAiChatModels.RECOMMENDED);
        Settings settings = new Settings(
                model,
                env("COVER_LETTER_EVAL_JUDGE_MODEL", Settings.defaultJudgeFor(model)),
                Long.parseLong(env("COVER_LETTER_EVAL_PRICE_VERSION", "2026080601")),
                new BigDecimal(env("COVER_LETTER_EVAL_MAX_COST_USD", "2.000000")));
        Set<String> selected = Set.of(env("COVER_LETTER_EVAL_CASES", "").split(","));
        int repetitions = Math.max(1, Math.min(5, Integer.parseInt(env("COVER_LETTER_EVAL_REPEATS", "1"))));

        PromptRegistry prompts = new PromptRegistry(Stream.concat(
                        CoverLetterGenerationV5PromptDefinitions.all().stream(),
                        CoverLetterEvalHarness.promptDefinitions().stream())
                .toList());
        StrictStructuredOutputSchemaRegistry schemas = new StrictStructuredOutputSchemaRegistry(
                prompts,
                new StrictStructuredOutputSchemaGenerator(objectMapper),
                new OpenAiStrictSchemaCompatibilityValidator(objectMapper));
        SpringAiOpenAiChatGateway gateway = new SpringAiOpenAiChatGateway(
                chatModel, objectMapper, priceCatalog, schemas, Duration.ofSeconds(180));
        CoverLetterEvalHarness harness = new CoverLetterEvalHarness(gateway, objectMapper, settings);

        List<CaseResult> results = new ArrayList<>();
        for (CoverLetterEvalCases.EvalCase evalCase : CoverLetterEvalCases.load(objectMapper)) {
            if (selected.contains("") || selected.contains(evalCase.id())) {
                for (int repetition = 1; repetition <= repetitions; repetition++) {
                    results.add(harness.run(evalCase, repetition));
                }
            }
        }
        CoverLetterEvalReport.Report report = CoverLetterEvalReport.write(
                REPORT, settings, results, harness.spentUsd(), objectMapper);

        assertThat(report.summary().resultCount()).isPositive();
        assertThat(harness.spentUsd()).isLessThanOrEqualTo(
                settings.maxCostUsd().add(new BigDecimal("0.500000")));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
