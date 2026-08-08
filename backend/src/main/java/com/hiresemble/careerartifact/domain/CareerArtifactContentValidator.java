package com.hiresemble.careerartifact.domain;

import com.hiresemble.careerartifact.domain.CareerArtifactContent.EvidenceRef;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic post-provider validation. It never repairs or invents content. */
public final class CareerArtifactContentValidator {

    private static final Pattern NUMBER_OR_DATE = Pattern.compile(
            "(?<!\\p{N})(?:\\d{1,4}(?:[.,:/%-]\\d{1,4})*)(?!\\p{N})");
    private static final Pattern EXTERNAL_DIRECTIVE = Pattern.compile(
            "(?i)(https?://|data:|image\\s*url|font\\s*size|#[0-9a-f]{6}|x\\s*=|y\\s*=|ooxml|pptx|docx)");
    private static final List<Pattern> NAMED_FACT_PATTERNS = List.of(
            Pattern.compile(
                    "(?iu)(?:organization|company|employer|회사|조직|소속)\\s*[:：]\\s*([^,;|\\n]{2,80})"),
            Pattern.compile(
                    "(?iu)(?:role|position|직무|역할|직업)\\s*[:：]\\s*([^,;|\\n]{2,80})"));

    public void validateResume(ResumeContent content, List<VerifiedEvidence> selected) {
        if (content == null
                || content.sections() == null
                || content.sections().isEmpty()
                || content.sections().size() > 12) {
            throw new IllegalArgumentException("RESUME_SECTION_COUNT_INVALID");
        }
        EvidenceIndex index = new EvidenceIndex(selected);
        String allMaterial = index.allMaterial();
        requireText(content.headline(), 200, true, "RESUME_HEADLINE_INVALID");
        requireText(content.summary(), 2000, true, "RESUME_SUMMARY_INVALID");
        bounded(content.skills(), 30, 100, "RESUME_SKILLS_INVALID");
        bounded(content.warnings(), 20, 500, "RESUME_WARNINGS_INVALID");
        if (content.headline() != null) validateClaim(content.headline(), allMaterial);
        if (content.summary() != null) validateClaim(content.summary(), allMaterial);
        content.skills().forEach(skill -> validateClaim(skill, allMaterial));
        content.sections().forEach(section -> {
            requireText(section.type(), 50, false, "RESUME_SECTION_INVALID");
            requireText(section.title(), 100, false, "RESUME_SECTION_INVALID");
            if (section.items() == null || section.items().size() > 30) {
                throw new IllegalArgumentException("RESUME_ITEM_COUNT_INVALID");
            }
            section.items().forEach(item -> {
                requireText(item.heading(), 250, true, "RESUME_ITEM_INVALID");
                requireText(item.subheading(), 250, true, "RESUME_ITEM_INVALID");
                requireText(item.period(), 100, true, "RESUME_ITEM_INVALID");
                bounded(item.bullets(), 10, 500, "RESUME_BULLET_INVALID");
                validateRefs(item.evidenceRefs(), index);
                if (item.bullets() != null
                        && !item.bullets().isEmpty()
                        && (item.evidenceRefs() == null || item.evidenceRefs().isEmpty())) {
                    throw new IllegalArgumentException("UNGROUNDED_POSITIVE_CLAIM");
                }
                String source = index.material(item.evidenceRefs());
                validateSupportedField(item.heading(), source);
                validateSupportedField(item.subheading(), source);
                if (item.period() != null) validateClaim(item.period(), source);
                if (item.bullets() != null) {
                    item.bullets().forEach(bullet -> validateClaim(bullet, source));
                }
            });
        });
    }

    public void validatePortfolio(PortfolioContent content, List<VerifiedEvidence> selected) {
        if (content == null
                || content.slides() == null
                || content.slides().size() < 6
                || content.slides().size() > 12) {
            throw new IllegalArgumentException("PORTFOLIO_SLIDE_COUNT_INVALID");
        }
        EvidenceIndex index = new EvidenceIndex(selected);
        String allMaterial = index.allMaterial();
        bounded(content.warnings(), 20, 500, "PORTFOLIO_WARNINGS_INVALID");
        Set<Integer> numbers = new HashSet<>();
        content.slides().forEach(slide -> {
            if (slide.slideNo() < 1
                    || slide.slideNo() > 12
                    || !numbers.add(slide.slideNo())
                    || slide.slideType() == null
                    || slide.visualType() == null) {
                throw new IllegalArgumentException("PORTFOLIO_SLIDE_INVALID");
            }
            requireText(slide.title(), 120, false, "PORTFOLIO_TITLE_INVALID");
            requireText(slide.subtitle(), 200, true, "PORTFOLIO_SUBTITLE_INVALID");
            validateClaim(slide.title(), allMaterial);
            if (slide.subtitle() != null) validateClaim(slide.subtitle(), allMaterial);
            bounded(slide.items(), 10, 500, "PORTFOLIO_ITEM_INVALID");
            validateRefs(slide.evidenceRefs(), index);
            boolean claimsRequireEvidence = switch (slide.slideType()) {
                case COVER, CLOSING -> false;
                default -> slide.items() != null && !slide.items().isEmpty();
            };
            if (claimsRequireEvidence
                    && (slide.evidenceRefs() == null || slide.evidenceRefs().isEmpty())) {
                throw new IllegalArgumentException("UNGROUNDED_CASE_STUDY_CLAIM");
            }
            String source = index.material(slide.evidenceRefs());
            if (slide.items() != null) {
                slide.items().forEach(item -> validateClaim(item, source));
            }
        });
        for (int expected = 1; expected <= content.slides().size(); expected++) {
            if (!numbers.contains(expected)) {
                throw new IllegalArgumentException("PORTFOLIO_SLIDE_SEQUENCE_INVALID");
            }
        }
    }

    private void validateRefs(List<EvidenceRef> refs, EvidenceIndex index) {
        if (refs == null || refs.size() > 20) {
            throw new IllegalArgumentException("EVIDENCE_REFERENCE_COUNT_INVALID");
        }
        for (EvidenceRef ref : refs) {
            if (ref == null
                    || ref.experienceItemId() == null
                    || ref.evidenceId() == null
                    || ref.usageType() == null
                    || index.get(ref) == null) {
                throw new IllegalArgumentException("UNKNOWN_EVIDENCE_REFERENCE");
            }
            requireText(ref.title(), 250, false, "EVIDENCE_REFERENCE_TITLE_INVALID");
            if (!ref.title().equals(index.get(ref).title())) {
                throw new IllegalArgumentException("UNKNOWN_EVIDENCE_REFERENCE");
            }
        }
    }

    private void validateClaim(String claim, String citedMaterial) {
        requireText(claim, 500, false, "CLAIM_INVALID");
        if (EXTERNAL_DIRECTIVE.matcher(claim).find()) {
            throw new IllegalArgumentException("EXTERNAL_LAYOUT_OR_ASSET_DIRECTIVE");
        }
        Matcher matcher = NUMBER_OR_DATE.matcher(claim);
        while (matcher.find()) {
            if (!citedMaterial.contains(matcher.group())) {
                throw new IllegalArgumentException("INVENTED_METRIC_OR_DATE");
            }
        }
        for (Pattern pattern : NAMED_FACT_PATTERNS) {
            Matcher namedFact = pattern.matcher(claim);
            while (namedFact.find()) {
                validateSupportedField(namedFact.group(1), citedMaterial);
            }
        }
    }

    private void validateSupportedField(String value, String citedMaterial) {
        if (value == null) return;
        String fact = normalizeFact(value);
        String source = normalizeFact(citedMaterial);
        if (fact.length() >= 2 && !source.contains(fact)) {
            throw new IllegalArgumentException("INVENTED_ORGANIZATION_OR_ROLE");
        }
    }

    private String normalizeFact(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}+#.]", "");
    }

    private void bounded(List<String> values, int maxItems, int maxLength, String code) {
        if (values == null || values.size() > maxItems) {
            throw new IllegalArgumentException(code);
        }
        values.forEach(value -> requireText(value, maxLength, false, code));
    }

    private void requireText(String value, int max, boolean nullable, String code) {
        if (value == null) {
            if (nullable) return;
            throw new IllegalArgumentException(code);
        }
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(code);
        }
    }

    private static final class EvidenceIndex {
        private final Map<String, VerifiedEvidence> values = new HashMap<>();

        private EvidenceIndex(List<VerifiedEvidence> selected) {
            if (selected == null || selected.isEmpty() || selected.size() > 20) {
                throw new IllegalArgumentException("INSUFFICIENT_VERIFIED_EXPERIENCE");
            }
            selected.forEach(value -> values.put(
                    key(value.experienceItemId(), value.evidenceId()), value));
        }

        private VerifiedEvidence get(EvidenceRef ref) {
            return values.get(key(ref.experienceItemId(), ref.evidenceId()));
        }

        private String material(List<EvidenceRef> refs) {
            if (refs == null) return "";
            StringBuilder result = new StringBuilder();
            refs.forEach(ref -> {
                VerifiedEvidence value = values.get(key(ref.experienceItemId(), ref.evidenceId()));
                if (value != null) {
                    result.append(value.title()).append(' ').append(value.content()).append(' ');
                }
            });
            return result.toString();
        }

        private String allMaterial() {
            StringBuilder result = new StringBuilder();
            values.values().forEach(value -> result
                    .append(value.title())
                    .append(' ')
                    .append(value.content())
                    .append(' '));
            return result.toString();
        }

        private static String key(UUID experienceId, UUID evidenceId) {
            return experienceId + "|" + evidenceId;
        }
    }
}
