package com.hiresemble.ai.evaluation;

import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.AveragedScores;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.CaseResult;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.JudgePass;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.Settings;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the A/B evaluation as JSON for human re-scoring and Markdown for a quick summary. Wins
 * count only position-consistent verdicts, and the summary warns when the judge saturates or
 * flips with answer order, because those make a win rate meaningless.
 */
public final class CoverLetterEvalReport {

    static final double SATURATION_WARNING = 0.5;
    static final double CONSISTENCY_WARNING = 0.7;

    private CoverLetterEvalReport() {}

    public record Summary(
            int resultCount,
            int completedCount,
            int hiresembleWins,
            int baselineWins,
            int ties,
            Double positionConsistencyRate,
            Double hiresembleMeanScore,
            Double baselineMeanScore,
            Map<String, Double> criterionDeltas,
            Double scoreSaturationRate,
            Double hiresembleMeanFillRatio,
            Double baselineMeanFillRatio,
            int hiresembleUnsupportedNumbers,
            int baselineUnsupportedNumbers,
            BigDecimal totalCostUsd,
            List<String> warnings) {
        public Summary {
            criterionDeltas = Map.copyOf(criterionDeltas);
            warnings = List.copyOf(warnings);
        }
    }

    public record Report(
            String evalVersion,
            String model,
            String judgeModel,
            long priceVersion,
            Summary summary,
            List<CaseResult> cases) {}

    public static Summary summarize(Settings settings, List<CaseResult> results, BigDecimal totalCostUsd) {
        List<CaseResult> completed = results.stream().filter(value -> value.failure() == null).toList();
        Map<String, Double> deltas = new LinkedHashMap<>();
        for (int index = 0; index < CoverLetterEvalHarness.CRITERIA.size(); index++) {
            final int criterion = index;
            Double hiresemble = mean(completed.stream()
                    .map(value -> value.hiresembleScores().criteria().get(criterion)).toList());
            Double baseline = mean(completed.stream()
                    .map(value -> value.baselineScores().criteria().get(criterion)).toList());
            deltas.put(
                    CoverLetterEvalHarness.CRITERIA.get(criterion),
                    hiresemble == null || baseline == null ? null : round(hiresemble - baseline));
        }
        List<Integer> rawScores = new ArrayList<>();
        completed.forEach(value -> value.judgePasses().forEach(pass -> {
            rawScores.addAll(pass.hiresemble().values());
            rawScores.addAll(pass.baseline().values());
        }));
        Double saturation = rawScores.isEmpty()
                ? null
                : round(rawScores.stream().filter(value -> value == 5).count() / (double) rawScores.size());
        Double consistency = completed.isEmpty()
                ? null
                : round(completed.stream().filter(CaseResult::positionConsistent).count() / (double) completed.size());
        List<String> warnings = new ArrayList<>();
        if (completed.size() < results.size()) {
            warnings.add((results.size() - completed.size()) + " result(s) failed; see the failure column.");
        }
        if (saturation != null && saturation > SATURATION_WARNING) {
            warnings.add("Judge saturation " + format(saturation)
                    + ": more than half of the scores are 5, so score differences are unreliable.");
        }
        if (consistency != null && consistency < CONSISTENCY_WARNING) {
            warnings.add("Position consistency " + format(consistency)
                    + ": the judge often changed its verdict when A/B order swapped.");
        }
        if (settings.model().equals(settings.judgeModel())) {
            warnings.add("The judge model equals the workflow model; self-preference bias is possible.");
        }
        if (completed.size() < 10) {
            warnings.add("Fewer than 10 completed results; treat win counts as a regression signal only.");
        }
        return new Summary(
                results.size(),
                completed.size(),
                (int) completed.stream().filter(value -> "HIRESEMBLE".equals(value.winner())).count(),
                (int) completed.stream().filter(value -> "BASELINE".equals(value.winner())).count(),
                (int) completed.stream().filter(value -> "TIE".equals(value.winner())).count(),
                consistency,
                mean(completed.stream().map(value -> value.hiresembleScores().mean()).toList()),
                mean(completed.stream().map(value -> value.baselineScores().mean()).toList()),
                deltas,
                saturation,
                mean(completed.stream().map(value -> value.hiresembleMetrics().fillRatio()).toList()),
                mean(completed.stream().map(value -> value.baselineMetrics().fillRatio()).toList()),
                completed.stream().mapToInt(value -> value.hiresembleMetrics().unsupportedNumbers().size()).sum(),
                completed.stream().mapToInt(value -> value.baselineMetrics().unsupportedNumbers().size()).sum(),
                totalCostUsd,
                warnings);
    }

    public static Report write(
            Path directory, Settings settings, List<CaseResult> results, BigDecimal totalCostUsd,
            ObjectMapper objectMapper) {
        Report report = new Report(
                CoverLetterEvalHarness.EVAL_VERSION,
                settings.model(),
                settings.judgeModel(),
                settings.priceVersion(),
                summarize(settings, results, totalCostUsd),
                results);
        try {
            Files.createDirectories(directory);
            Files.writeString(
                    directory.resolve("report.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            Files.writeString(directory.resolve("report.md"), markdown(report));
        } catch (IOException exception) {
            throw new IllegalStateException("evaluation report could not be written", exception);
        }
        return report;
    }

    static String markdown(Report report) {
        Summary summary = report.summary();
        StringBuilder text = new StringBuilder()
                .append("# Cover letter quality evaluation (").append(report.evalVersion()).append(")\n\n")
                .append("- Workflow model: `").append(report.model()).append("`, judge: `")
                .append(report.judgeModel()).append("`, price version ").append(report.priceVersion()).append('\n')
                .append("- Results: ").append(summary.completedCount()).append('/').append(summary.resultCount())
                .append(" completed, cost USD ").append(summary.totalCostUsd().toPlainString()).append('\n')
                .append("- Consistent verdicts (both A/B orders agree): Hiresemble ").append(summary.hiresembleWins())
                .append(" / baseline ").append(summary.baselineWins())
                .append(" / tie or inconsistent ").append(summary.ties()).append('\n')
                .append("- Position consistency: ").append(format(summary.positionConsistencyRate()))
                .append(", score saturation (share of 5s): ").append(format(summary.scoreSaturationRate())).append('\n')
                .append("- Mean judge score (1-5, both passes): Hiresemble ").append(format(summary.hiresembleMeanScore()))
                .append(", baseline ").append(format(summary.baselineMeanScore())).append('\n')
                .append("- Mean fill ratio: Hiresemble ").append(format(summary.hiresembleMeanFillRatio()))
                .append(", baseline ").append(format(summary.baselineMeanFillRatio())).append('\n')
                .append("- Unsupported numbers: Hiresemble ").append(summary.hiresembleUnsupportedNumbers())
                .append(", baseline ").append(summary.baselineUnsupportedNumbers()).append("\n\n");
        if (!summary.warnings().isEmpty()) {
            text.append("## Warnings\n\n");
            summary.warnings().forEach(value -> text.append("- ").append(value).append('\n'));
            text.append('\n');
        }
        text.append("## Criterion deltas (Hiresemble minus baseline)\n\n")
                .append("| Criterion | Delta |\n| --- | --- |\n");
        summary.criterionDeltas().forEach((criterion, delta) ->
                text.append("| ").append(criterion).append(" | ").append(signed(delta)).append(" |\n"));
        text.append("\n## Results\n\n")
                .append("| Case | Run | Winner | Pass 1 / Pass 2 | Score H / B | Fill H / B | Failure |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (CaseResult value : report.cases()) {
            text.append("| ").append(value.label())
                    .append(" | ").append(value.repetition())
                    .append(" | ").append(value.winner())
                    .append(" | ").append(passes(value.judgePasses()))
                    .append(" | ").append(score(value.hiresembleScores())).append(" / ").append(score(value.baselineScores()))
                    .append(" | ").append(metric(value, CaseResult::hiresembleMetrics))
                    .append(" / ").append(metric(value, CaseResult::baselineMetrics))
                    .append(" | ").append(value.failure() == null ? "" : value.failure())
                    .append(" |\n");
        }
        text.append("\nAnswers, both judge passes, and per-criterion scores are in `report.json` for human re-scoring.\n");
        return text.toString();
    }

    private static String passes(List<JudgePass> passes) {
        if (passes.isEmpty()) return "-";
        return String.join(" / ", passes.stream()
                .map(pass -> pass.preferred() + " " + pass.margin().name().toLowerCase(Locale.ROOT))
                .toList());
    }

    private static String score(AveragedScores scores) {
        return scores == null ? "-" : format(scores.mean());
    }

    private static String metric(
            CaseResult value, Function<CaseResult, CoverLetterQualityRubric.AnswerMetrics> metrics) {
        CoverLetterQualityRubric.AnswerMetrics selected = metrics.apply(value);
        return selected == null ? "-" : format(selected.fillRatio());
    }

    private static Double mean(List<Double> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        return round(present.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String signed(Double value) {
        if (value == null) return "-";
        return (value > 0 ? "+" : "") + format(value);
    }

    private static String format(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }
}
