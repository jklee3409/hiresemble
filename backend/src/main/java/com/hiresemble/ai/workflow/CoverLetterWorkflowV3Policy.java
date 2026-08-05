package com.hiresemble.ai.workflow;

import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shared deterministic contracts for active cover-letter generation and verification v3. */
public final class CoverLetterWorkflowV3Policy {

    public static final String OUTPUT_LOCALE = "ko-KR";
    public static final String DUPLICATION_POLICY_VERSION =
            "cover-letter-duplication-v3";
    public static final String EVIDENCE_SELECTION_POLICY_VERSION =
            "cover-letter-evidence-selection-v3";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<CoverLetterGenerationWorkflow.NarrativeFramework, Set<NarrativeSectionType>>
            ALLOWED_SECTIONS = allowedSections();

    private CoverLetterWorkflowV3Policy() {}

    public enum NarrativeSectionType {
        COMPANY_REASON,
        ROLE_REASON,
        EXPERIENCE_CONNECTION,
        CONTRIBUTION,
        CURRENT_CAPABILITY,
        EARLY_CONTRIBUTION,
        GROWTH_PATH,
        ORGANIZATION_CONNECTION,
        PROBLEM,
        ALTERNATIVES,
        DECISION,
        IMPLEMENTATION,
        TRADEOFF,
        RESULT,
        SHARED_GOAL,
        CONFLICT,
        PERSONAL_ACTION,
        ALIGNMENT,
        PRINCIPLE,
        SITUATION,
        ACTION,
        LEARNING,
        VALUE,
        DIRECT_ANSWER
    }

    public enum ClaimType {
        FACT,
        NUMBER,
        ROLE,
        ACHIEVEMENT
    }

    public record NarrativeSectionPlan(
            NarrativeSectionType sectionType, String objective, int emphasisWeight) {}

    /** Ephemeral Provider context. Only count/hash metadata belongs in durable refs. */
    public record BoundedText(
            int originalCharacterCount,
            int providedCharacterCount,
            boolean truncated,
            String fullTextHash,
            String boundedPlainText) {}

    public record EvidenceSelection<T>(
            List<T> selected,
            int omittedCount,
            String policyVersion) {
        public EvidenceSelection {
            selected = List.copyOf(selected);
        }
    }

    public record DuplicationSignal(
            boolean warningRequired,
            double textJaccard,
            int sharedEvidenceCount,
            boolean coreMessageOverlap,
            boolean distinctEmphasisObserved,
            String policyVersion) {}

    public static BoundedText bound(String value, int maximum) {
        String full = value == null ? "" : value;
        int original = full.codePointCount(0, full.length());
        if (original <= maximum) {
            return new BoundedText(original, original, false, sha256(full), full);
        }
        String omissionMarker = "\n[...중간 생략...]\n";
        int markerLength = omissionMarker.codePointCount(0, omissionMarker.length());
        int available = Math.max(2, maximum - markerLength);
        int tailLength = Math.max(1, available / 3);
        int headLength = available - tailLength;
        String head = codePointSlice(full, 0, headLength);
        String tail = codePointSlice(full, original - tailLength, original);
        String bounded = head + omissionMarker + tail;
        return new BoundedText(
                original,
                bounded.codePointCount(0, bounded.length()),
                true,
                sha256(full),
                bounded);
    }

    public static void validateSections(
            CoverLetterGenerationWorkflow.NarrativeFramework framework,
            List<NarrativeSectionPlan> sections) {
        if (framework == null || sections == null || sections.isEmpty() || sections.size() > 12) {
            throw new IllegalArgumentException("narrative sections are invalid");
        }
        Set<NarrativeSectionType> allowed = ALLOWED_SECTIONS.get(framework);
        if (allowed == null) {
            throw new IllegalArgumentException("narrative framework is unsupported");
        }
        int total = 0;
        Set<NarrativeSectionType> unique = EnumSet.noneOf(NarrativeSectionType.class);
        for (NarrativeSectionPlan section : sections) {
            if (section == null
                    || section.sectionType() == null
                    || !allowed.contains(section.sectionType())
                    || !unique.add(section.sectionType())
                    || section.objective() == null
                    || section.objective().isBlank()
                    || section.objective().length() > 1_000
                    || section.emphasisWeight() < 1
                    || section.emphasisWeight() > 100) {
                throw new IllegalArgumentException("narrative section is invalid");
            }
            total += section.emphasisWeight();
        }
        if (total != 100) {
            throw new IllegalArgumentException("narrative section weights must total 100");
        }
        if (framework
                        == CoverLetterGenerationWorkflow.NarrativeFramework.TECHNICAL_DECISION_TRADEOFF
                && (!unique.contains(NarrativeSectionType.DECISION)
                        || !unique.contains(NarrativeSectionType.TRADEOFF))) {
            throw new IllegalArgumentException("technical narrative requires decision and tradeoff");
        }
    }

    public static void validateQuestionFramework(
            CoverLetterGenerationWorkflow.QuestionType questionType,
            CoverLetterGenerationWorkflow.NarrativeFramework framework) {
        Set<CoverLetterGenerationWorkflow.NarrativeFramework> allowed = switch (questionType) {
            case MOTIVATION -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.MOTIVATION_CONNECTION);
            case FUTURE_CONTRIBUTION -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.FUTURE_CONTRIBUTION_PATH);
            case ROLE_COMPETENCY -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.COMPETENCY_EVIDENCE_APPLICATION);
            case TECHNICAL_PROJECT -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.TECHNICAL_DECISION_TRADEOFF);
            case PROBLEM_SOLVING -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.PROBLEM_ACTION_RESULT);
            case COLLABORATION_CONFLICT -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.COLLABORATION_ALIGNMENT);
            case CHALLENGE_FAILURE -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.CHALLENGE_LEARNING);
            case GROWTH_VALUES -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.VALUES_TO_ACTION);
            case FREEFORM, OTHER -> EnumSet.of(
                    CoverLetterGenerationWorkflow.NarrativeFramework.DIRECT_RESPONSE);
        };
        if (!allowed.contains(framework)) {
            throw new IllegalArgumentException("question type and framework are incompatible");
        }
    }

    public static void validateIssueCompatibility(
            CoverLetterGenerationWorkflow.VerificationIssueKind kind,
            VerificationIssueCode code,
            IssueSeverity severity) {
        if (kind == null || code == null || severity == null) {
            throw new IllegalArgumentException("issue compatibility is invalid");
        }
        Set<VerificationIssueCode> allowed = switch (kind) {
            case FACTUAL -> EnumSet.of(
                    VerificationIssueCode.UNVERIFIED_CLAIM,
                    VerificationIssueCode.CONTRADICTION,
                    VerificationIssueCode.SOURCE_DELETED);
            case REQUIREMENT -> EnumSet.of(
                    VerificationIssueCode.REQUIREMENT_MISSING,
                    VerificationIssueCode.LENGTH_VIOLATION);
            case QUALITY, DUPLICATION -> EnumSet.of(VerificationIssueCode.OTHER);
        };
        if (!allowed.contains(code)
                || ((kind == CoverLetterGenerationWorkflow.VerificationIssueKind.QUALITY
                                || kind
                                        == CoverLetterGenerationWorkflow.VerificationIssueKind.DUPLICATION)
                        && severity != IssueSeverity.WARNING)) {
            throw new IllegalArgumentException("issue kind, code, and severity are incompatible");
        }
    }

    public static void validateExactExcerpt(
            String answerPlainText, UUID evidenceId, String excerpt) {
        if (evidenceId == null
                || excerpt == null
                || excerpt.isBlank()
                || excerpt.length() > 2_000
                || !normalize(answerPlainText).contains(normalize(excerpt))) {
            throw new IllegalArgumentException("claim excerpt is not grounded in the answer");
        }
    }

    public static void validateDistinctClaims(
            List<? extends ClaimView> claims, String answerPlainText) {
        if (claims == null || claims.size() > 50) {
            throw new IllegalArgumentException("answer claims are invalid");
        }
        Set<String> seen = new HashSet<>();
        for (ClaimView claim : claims) {
            if (claim == null || claim.claimType() == null) {
                throw new IllegalArgumentException("answer claim is invalid");
            }
            validateExactExcerpt(answerPlainText, claim.evidenceId(), claim.exactAnswerExcerpt());
            String key = claim.evidenceId() + "|" + normalize(claim.exactAnswerExcerpt());
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate answer claim");
            }
        }
    }

    public interface ClaimView {
        UUID evidenceId();

        String exactAnswerExcerpt();

        ClaimType claimType();
    }

    public static <T> EvidenceSelection<T> selectRelevant(
            List<T> candidates,
            Set<UUID> historicalIds,
            String answerText,
            int maximum,
            java.util.function.Function<T, UUID> id,
            java.util.function.Function<T, String> searchableText) {
        Set<String> answerTokens = tokens(answerText);
        List<T> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .<T>comparingInt(value -> historicalIds.contains(id.apply(value)) ? 0 : 1)
                .thenComparingInt(value -> -overlap(answerTokens, searchableText.apply(value)))
                .thenComparing(id));
        List<T> selected = ordered.stream().limit(maximum).toList();
        return new EvidenceSelection<>(
                selected,
                Math.max(0, ordered.size() - selected.size()),
                EVIDENCE_SELECTION_POLICY_VERSION);
    }

    public static DuplicationSignal duplication(
            String answer,
            Set<UUID> evidenceIds,
            String coreMessage,
            String siblingAnswer,
            Set<UUID> siblingEvidenceIds,
            String siblingCoreMessage,
            String distinctEmphasis) {
        Set<String> left = tokens(answer);
        Set<String> right = tokens(siblingAnswer);
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        double jaccard = union.isEmpty() ? 0d : (double) intersection.size() / union.size();
        Set<UUID> shared = new HashSet<>(evidenceIds);
        shared.retainAll(siblingEvidenceIds);
        boolean coreOverlap = overlap(tokens(coreMessage), siblingCoreMessage) >= 2;
        boolean emphasisObserved = distinctEmphasis != null
                && !distinctEmphasis.isBlank()
                && normalize(answer).contains(normalize(distinctEmphasis));
        boolean repeatedAction = intersection.size() >= 5 && jaccard >= 0.45d;
        boolean warning = jaccard >= 0.70d
                || (!shared.isEmpty() && repeatedAction && !emphasisObserved)
                || (coreOverlap && jaccard >= 0.55d);
        return new DuplicationSignal(
                warning,
                jaccard,
                shared.size(),
                coreOverlap,
                emphasisObserved,
                DUPLICATION_POLICY_VERSION);
    }

    public static boolean hasFactualPattern(String answer) {
        if (answer == null || answer.isBlank()) return false;
        return Pattern.compile(
                        "(?:\\d[\\d,.%]*|\\d{4}년|\\d+개월|팀장|리더|담당|재직|근무|성과|향상|감소|증가|달성)")
                .matcher(answer)
                .find();
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return WHITESPACE.matcher(value.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private static int overlap(Set<String> query, String value) {
        Set<String> candidate = tokens(value);
        return (int) query.stream().filter(candidate::contains).count();
    }

    private static Set<String> tokens(String value) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String token : normalize(value).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2) values.add(token);
        }
        return Set.copyOf(values);
    }

    private static String codePointSlice(String value, int start, int end) {
        int startOffset = value.offsetByCodePoints(0, start);
        int endOffset = value.offsetByCodePoints(0, end);
        return value.substring(startOffset, endOffset);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<CoverLetterGenerationWorkflow.NarrativeFramework, Set<NarrativeSectionType>>
            allowedSections() {
        Map<CoverLetterGenerationWorkflow.NarrativeFramework, Set<NarrativeSectionType>> map =
                new EnumMap<>(CoverLetterGenerationWorkflow.NarrativeFramework.class);
        map.put(
                CoverLetterGenerationWorkflow.NarrativeFramework.MOTIVATION_CONNECTION,
                EnumSet.of(
                        NarrativeSectionType.COMPANY_REASON,
                        NarrativeSectionType.ROLE_REASON,
                        NarrativeSectionType.EXPERIENCE_CONNECTION,
                        NarrativeSectionType.CONTRIBUTION));
        map.put(
                CoverLetterGenerationWorkflow.NarrativeFramework.FUTURE_CONTRIBUTION_PATH,
                EnumSet.of(
                        NarrativeSectionType.CURRENT_CAPABILITY,
                        NarrativeSectionType.EARLY_CONTRIBUTION,
                        NarrativeSectionType.GROWTH_PATH,
                        NarrativeSectionType.ORGANIZATION_CONNECTION));
        map.put(
                CoverLetterGenerationWorkflow.NarrativeFramework.TECHNICAL_DECISION_TRADEOFF,
                EnumSet.of(
                        NarrativeSectionType.PROBLEM,
                        NarrativeSectionType.ALTERNATIVES,
                        NarrativeSectionType.DECISION,
                        NarrativeSectionType.IMPLEMENTATION,
                        NarrativeSectionType.TRADEOFF,
                        NarrativeSectionType.RESULT));
        map.put(
                CoverLetterGenerationWorkflow.NarrativeFramework.COLLABORATION_ALIGNMENT,
                EnumSet.of(
                        NarrativeSectionType.SHARED_GOAL,
                        NarrativeSectionType.CONFLICT,
                        NarrativeSectionType.PERSONAL_ACTION,
                        NarrativeSectionType.ALIGNMENT,
                        NarrativeSectionType.RESULT,
                        NarrativeSectionType.PRINCIPLE));
        Set<NarrativeSectionType> experience = EnumSet.of(
                NarrativeSectionType.SITUATION,
                NarrativeSectionType.PROBLEM,
                NarrativeSectionType.ACTION,
                NarrativeSectionType.PERSONAL_ACTION,
                NarrativeSectionType.RESULT,
                NarrativeSectionType.LEARNING,
                NarrativeSectionType.CONTRIBUTION,
                NarrativeSectionType.DIRECT_ANSWER,
                NarrativeSectionType.VALUE);
        map.put(CoverLetterGenerationWorkflow.NarrativeFramework.COMPETENCY_EVIDENCE_APPLICATION, experience);
        map.put(CoverLetterGenerationWorkflow.NarrativeFramework.PROBLEM_ACTION_RESULT, experience);
        map.put(CoverLetterGenerationWorkflow.NarrativeFramework.CHALLENGE_LEARNING, experience);
        map.put(CoverLetterGenerationWorkflow.NarrativeFramework.VALUES_TO_ACTION, experience);
        map.put(CoverLetterGenerationWorkflow.NarrativeFramework.DIRECT_RESPONSE, experience);
        return Map.copyOf(map);
    }
}
