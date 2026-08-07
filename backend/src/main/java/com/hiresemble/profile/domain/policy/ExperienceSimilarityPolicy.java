package com.hiresemble.profile.domain.policy;

import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemRecord;
import com.hiresemble.profile.domain.model.ExperienceRecords.SimilarExperienceRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative deterministic guardrails around semantic experience matching. */
public final class ExperienceSimilarityPolicy {

    public static final String VERSION = "experience-semantic-v1";
    public static final double SAME_EXPERIENCE_SIMILARITY = 0.94d;
    public static final double REVIEW_SIMILARITY = 0.82d;
    public static final int TOP_K = 5;

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?%?");
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "경험", "업무", "프로젝트", "활동", "담당", "수행", "진행", "통해", "위해", "관련");

    private ExperienceSimilarityPolicy() {}

    public static String fingerprint(String category, String title, String content) {
        String normalized = normalize(category) + '|' + normalize(title) + '|' + normalize(content);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static MatchDecision decide(
            String title, String content, List<SimilarExperienceRecord> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return MatchDecision.newExperience();
        }
        SimilarExperienceRecord nearest = candidates.getFirst();
        double similarity = Math.max(0d, Math.min(1d, 1d - nearest.distance()));
        if (similarity < REVIEW_SIMILARITY) {
            return MatchDecision.newExperience();
        }
        ExperienceItemRecord matched = nearest.item();
        boolean numericConflict = numericConflict(
                title + ' ' + content, matched.title() + ' ' + matched.content());
        if (numericConflict) {
            return new MatchDecision(
                    ExperienceMatchKind.CONFLICT,
                    matched.id(),
                    similarity(similarity));
        }
        if (similarity >= SAME_EXPERIENCE_SIMILARITY
                && sharedAnchorCount(
                                title + ' ' + content,
                                matched.title() + ' ' + matched.content())
                        >= 2) {
            return new MatchDecision(
                    ExperienceMatchKind.SAME_EXPERIENCE,
                    matched.id(),
                    similarity(similarity));
        }
        return new MatchDecision(
                ExperienceMatchKind.RELATED_DIFFERENT,
                matched.id(),
                similarity(similarity));
    }

    static int sharedAnchorCount(String left, String right) {
        Set<String> leftTokens = tokens(left);
        leftTokens.retainAll(tokens(right));
        return leftTokens.size();
    }

    static boolean numericConflict(String left, String right) {
        Set<String> leftNumbers = numbers(left);
        Set<String> rightNumbers = numbers(right);
        return !leftNumbers.isEmpty()
                && !rightNumbers.isEmpty()
                && !leftNumbers.equals(rightNumbers);
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(TOKEN_SEPARATOR.split(normalizeText(value)))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .forEach(result::add);
        return result;
    }

    private static Set<String> numbers(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            result.add(matcher.group().replace(",", ""));
        }
        return result;
    }

    private static String normalize(String value) {
        return normalizeText(value).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String normalizeText(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static BigDecimal similarity(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }

    public record MatchDecision(
            ExperienceMatchKind kind,
            UUID matchedExperienceItemId,
            BigDecimal similarity) {

        public static MatchDecision newExperience() {
            return new MatchDecision(ExperienceMatchKind.NEW, null, null);
        }
    }
}
