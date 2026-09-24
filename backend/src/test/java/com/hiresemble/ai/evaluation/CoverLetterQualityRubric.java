package com.hiresemble.ai.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, model-free answer metrics. They complement the LLM judge with signals a screener
 * notices immediately: fill ratio, limit overflow, cliches, unsupported numbers, and Markdown.
 */
public final class CoverLetterQualityRubric {

    static final List<String> CLICHES = List.of(
            "어릴 때부터",
            "어린 시절부터",
            "무궁한 발전",
            "최고의 인재",
            "열정과 성실",
            "누구보다",
            "항상 최선을",
            "귀사의 일원이",
            "뼈를 묻",
            "평소 관심");

    private static final Pattern NUMBER =
            Pattern.compile("(?<![\\p{L}\\p{N}])\\d[\\d,.%]*(?![\\p{L}\\p{N}])");
    private static final Pattern MARKDOWN =
            Pattern.compile("(?m)(```|^\\s{0,3}#{1,6}\\s|\\*\\*|^\\s*[-*]\\s)");
    private static final Pattern FIRST_PERSON = Pattern.compile("(저는|제가|저의|저 스스로)");

    private CoverLetterQualityRubric() {}

    public record AnswerMetrics(
            int characterCount,
            Double fillRatio,
            boolean withinLimit,
            int paragraphCount,
            boolean markdownDetected,
            List<String> clicheHits,
            List<String> unsupportedNumbers,
            int firstPersonCount) {
        public AnswerMetrics {
            clicheHits = List.copyOf(clicheHits);
            unsupportedNumbers = List.copyOf(unsupportedNumbers);
        }
    }

    public static AnswerMetrics measure(String answer, Integer maxLength, List<String> evidenceTexts) {
        String text = answer == null ? "" : answer.strip();
        int count = text.codePointCount(0, text.length());
        String evidence = String.join(" ", evidenceTexts == null ? List.of() : evidenceTexts);
        List<String> cliches = CLICHES.stream().filter(text::contains).toList();
        LinkedHashSet<String> unsupported = new LinkedHashSet<>();
        Matcher numbers = NUMBER.matcher(text);
        while (numbers.find()) {
            if (!evidence.contains(numbers.group())) unsupported.add(numbers.group());
        }
        int firstPerson = 0;
        Matcher person = FIRST_PERSON.matcher(text);
        while (person.find()) firstPerson++;
        List<String> paragraphs = new ArrayList<>();
        for (String block : text.split("\\n\\s*\\n|\\n")) {
            if (!block.isBlank()) paragraphs.add(block);
        }
        return new AnswerMetrics(
                count,
                maxLength == null || maxLength < 1
                        ? null
                        : Math.round(count * 1000.0 / maxLength) / 1000.0,
                maxLength == null || count <= maxLength,
                paragraphs.size(),
                MARKDOWN.matcher(text).find(),
                cliches,
                List.copyOf(unsupported),
                firstPerson);
    }
}
