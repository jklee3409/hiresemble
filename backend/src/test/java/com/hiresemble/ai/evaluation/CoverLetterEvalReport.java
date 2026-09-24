package com.hiresemble.ai.evaluation;

import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.CaseResult;
import com.hiresemble.ai.evaluation.CoverLetterEvalHarness.Settings;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** Writes the A/B evaluation as JSON for human re-scoring and Markdown for a quick summary. */
public final class CoverLetterEvalReport {

    private CoverLetterEvalReport() {}

    public record Summary(
            int caseCount,
            int completedCount,
            int hiresembleWins,
            int baselineWins,
            int ties,
            Double hiresembleMeanScore,
            Double baselineMeanScore,
            Double hiresembleMeanFillRatio,
            Double baselineMeanFillRatio,
            int hiresembleUnsupportedNumbers,
            int baselineUnsupportedNumbers,
            BigDecimal totalCostUsd) {}

    public record Report(
            String evalVersion,
            String model,
            String judgeModel,
            long priceVersion,
            Summary summary,
            List<CaseResult> cases) {}

    public static Summary summarize(List<CaseResult> results, BigDecimal totalCostUsd) {
        List<CaseResult> completed = results.stream().filter(value -> value.failure() == null).toList();
        return new Summary(
                results.size(),
                completed.size(),
                (int) completed.stream().filter(value -> "HIRESEMBLE".equals(value.winner())).count(),
                (int) completed.stream().filter(value -> "BASELINE".equals(value.winner())).count(),
                (int) completed.stream().filter(value -> "TIE".equals(value.winner())).count(),
                mean(completed.stream().map(value -> value.hiresembleScores().mean()).toList()),
                mean(completed.stream().map(value -> value.baselineScores().mean()).toList()),
                mean(completed.stream().map(value -> value.hiresembleMetrics().fillRatio()).toList()),
                mean(completed.stream().map(value -> value.baselineMetrics().fillRatio()).toList()),
                completed.stream().mapToInt(value -> value.hiresembleMetrics().unsupportedNumbers().size()).sum(),
                completed.stream().mapToInt(value -> value.baselineMetrics().unsupportedNumbers().size()).sum(),
                totalCostUsd);
    }

    public static Report write(
            Path directory, Settings settings, List<CaseResult> results, BigDecimal totalCostUsd,
            ObjectMapper objectMapper) {
        Report report = new Report(
                CoverLetterEvalHarness.EVAL_VERSION,
                settings.model(),
                settings.judgeModel(),
                settings.priceVersion(),
                summarize(results, totalCostUsd),
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
                .append("# Cover letter quality evaluation\n\n")
                .append("- Workflow model: `").append(report.model()).append("`, judge: `")
                .append(report.judgeModel()).append("`, price version ").append(report.priceVersion()).append('\n')
                .append("- Cases: ").append(summary.completedCount()).append('/').append(summary.caseCount())
                .append(" completed, cost USD ").append(summary.totalCostUsd().toPlainString()).append('\n')
                .append("- Judge preference: Hiresemble ").append(summary.hiresembleWins())
                .append(" / baseline ").append(summary.baselineWins())
                .append(" / tie ").append(summary.ties()).append('\n')
                .append("- Mean judge score (1-5): Hiresemble ").append(format(summary.hiresembleMeanScore()))
                .append(", baseline ").append(format(summary.baselineMeanScore())).append('\n')
                .append("- Mean fill ratio: Hiresemble ").append(format(summary.hiresembleMeanFillRatio()))
                .append(", baseline ").append(format(summary.baselineMeanFillRatio())).append('\n')
                .append("- Unsupported numbers: Hiresemble ").append(summary.hiresembleUnsupportedNumbers())
                .append(", baseline ").append(summary.baselineUnsupportedNumbers()).append("\n\n")
                .append("| Case | Winner | Score H / B | Fill H / B | Cliches H / B | Failure |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (CaseResult value : report.cases()) {
            text.append("| ").append(value.label())
                    .append(" | ").append(value.winner())
                    .append(" | ").append(value.hiresembleScores() == null ? "-" : format(value.hiresembleScores().mean()))
                    .append(" / ").append(value.baselineScores() == null ? "-" : format(value.baselineScores().mean()))
                    .append(" | ").append(value.hiresembleMetrics() == null ? "-" : format(value.hiresembleMetrics().fillRatio()))
                    .append(" / ").append(value.baselineMetrics() == null ? "-" : format(value.baselineMetrics().fillRatio()))
                    .append(" | ").append(value.hiresembleMetrics() == null ? "-" : value.hiresembleMetrics().clicheHits().size())
                    .append(" / ").append(value.baselineMetrics() == null ? "-" : value.baselineMetrics().clicheHits().size())
                    .append(" | ").append(value.failure() == null ? "" : value.failure())
                    .append(" |\n");
        }
        text.append("\nAnswers, judge rationale, and per-criterion scores are in `report.json` for human re-scoring.\n");
        return text.toString();
    }

    private static Double mean(List<Double> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        return Math.round(present.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100.0) / 100.0;
    }

    private static String format(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
