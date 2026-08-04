package com.hiresemble.ai.workflow;

import com.hiresemble.ai.workflow.JobAnalysisWorkflow.JobSourceBlock;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderSourceRequirement;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementCandidate;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementSection;
import com.hiresemble.job.domain.CriterionSupportType;
import com.hiresemble.job.domain.FitCriterionCategory;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical server-owned policy for turning faithful posting source requirements into atomic
 * criteria. Provider output never owns scoring categories, support types, required flags, or
 * dates.
 */
public final class JobRequirementNormalizationPolicy {

    private static final Pattern LEADING_MARKER =
            Pattern.compile("^\\s*(?:[-*•▪◦]|\\d+[.)])\\s*");
    private static final Pattern WORK_DATE = Pattern.compile(
            "(?<!\\d)(?<year>20\\d{2})\\s*[년./-]\\s*(?<month>0?[1-9]|1[0-2])\\s*(?:월|[./-])?\\s*(?:(?<day>0?[1-9]|[12]\\d|3[01])\\s*일?)?");

    public List<RequirementCandidate> normalize(
            List<ProviderSourceRequirement> sources, List<JobSourceBlock> sourceBlocks) {
        if (sources == null || sources.isEmpty() || sourceBlocks == null || sourceBlocks.isEmpty()) {
            return List.of();
        }
        Map<String, JobSourceBlock> blocksById = new LinkedHashMap<>();
        for (JobSourceBlock block : sourceBlocks) {
            if (block != null && block.sourceBlockId() != null) {
                blocksById.put(block.sourceBlockId(), block);
            }
        }
        Map<String, RequirementCandidate> unique = new LinkedHashMap<>();
        for (ProviderSourceRequirement source : sources) {
            if (source == null || source.sourceText() == null) {
                continue;
            }
            JobSourceBlock block = blocksById.get(source.sourceBlockId());
            if (block == null) {
                throw new IllegalArgumentException("Unknown job source block: " + source.sourceBlockId());
            }
            String sourceText = cleanSourceText(block.sourceText());
            if (!sourceText.equals(cleanSourceText(source.sourceText()))) {
                throw new IllegalArgumentException(
                        "Job requirement source text must exactly match source block "
                                + source.sourceBlockId());
            }
            if (sourceText.isBlank()) {
                continue;
            }
            RequirementSection section = block.section();
            if (section == RequirementSection.ROLE_SUMMARY || section == RequirementSection.OTHER) {
                continue;
            }
            String sourceLocation = sourceLocation(section);
            for (String clause : atomicClauses(sourceText)) {
                CriterionSupportType supportType = supportType(clause);
                LocalDate requiredByDate = supportType == CriterionSupportType.WORK_AVAILABLE_DATE
                        ? requiredByDate(clause)
                        : null;
                FitCriterionCategory category = category(section, supportType);
                RequirementCandidate normalized = new RequirementCandidate(
                        section,
                        category,
                        clause,
                        section == RequirementSection.REQUIRED_QUALIFICATION,
                        sourceLocation,
                        supportType,
                        requiredByDate,
                        block.sourceOrdinal(),
                        sourceText,
                        block.sourceBlockId());
                String key = deduplicationKey(normalized);
                RequirementCandidate existing = unique.get(key);
                if (existing == null || (!existing.required() && normalized.required())) {
                    unique.put(key, normalized);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private List<String> atomicClauses(String sourceText) {
        List<String> clauses = splitTopLevel(sourceText);
        List<String> result = new ArrayList<>();
        for (String clause : clauses) {
            String normalized = clean(clause);
            if (normalized.isBlank()) {
                continue;
            }
            if (hasOverseasTravel(normalized) && hasEmploymentDisqualification(normalized)) {
                result.add("해외여행에 결격사유가 없는 자");
                result.add("채용에 결격사유가 없는 자");
            } else {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> splitTopLevel(String value) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parentheses = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(' || character == '[' || character == '{') {
                parentheses++;
            } else if (character == ')' || character == ']' || character == '}') {
                parentheses = Math.max(0, parentheses - 1);
            }
            if (parentheses == 0
                    && (character == ',' || character == ';' || character == '\n' || character == '\r')) {
                addClause(values, current);
                continue;
            }
            current.append(character);
        }
        addClause(values, current);
        return values;
    }

    private void addClause(List<String> values, StringBuilder current) {
        String value = clean(current.toString());
        if (!value.isBlank()) {
            values.add(value);
        }
        current.setLength(0);
    }

    private FitCriterionCategory category(
            RequirementSection section, CriterionSupportType supportType) {
        if (supportType == CriterionSupportType.EDUCATION
                || supportType == CriterionSupportType.CERTIFICATION
                || supportType == CriterionSupportType.LANGUAGE) {
            return FitCriterionCategory.EDUCATION_CERTIFICATION_LANGUAGE;
        }
        return switch (section) {
            case RESPONSIBILITY -> FitCriterionCategory.CORE_RESPONSIBILITY_OR_SKILL;
            case PREFERRED_QUALIFICATION -> FitCriterionCategory.PREFERRED_QUALIFICATION;
            case REQUIRED_QUALIFICATION -> FitCriterionCategory.REQUIRED_QUALIFICATION;
            case ROLE_SUMMARY, OTHER -> throw new IllegalArgumentException(
                    "Non-scorable job section cannot become a fit criterion: " + section);
        };
    }

    private CriterionSupportType supportType(String value) {
        String normalized = normalize(value);
        if (containsAny(normalized, "근무 가능", "입사 가능", "근무 시작", "입사 예정")) {
            return CriterionSupportType.WORK_AVAILABLE_DATE;
        }
        if (normalized.contains("병역")) {
            return CriterionSupportType.MILITARY_STATUS;
        }
        if (hasOverseasTravel(normalized)) {
            return CriterionSupportType.OVERSEAS_TRAVEL_ELIGIBILITY;
        }
        if (hasEmploymentDisqualification(normalized)) {
            return CriterionSupportType.EMPLOYMENT_DISQUALIFICATION_STATUS;
        }
        if (containsAny(
                normalized, "자격증", "자격 보유", "자격 취득", "자격 소지", "certificate", "certification")) {
            return CriterionSupportType.CERTIFICATION;
        }
        if (containsAny(
                normalized,
                "toeic", "toefl", "opic", "ielts", "토익", "토플", "오픽", "아이엘츠",
                "어학", "외국어", "영어 능력", "language proficiency")) {
            return CriterionSupportType.LANGUAGE;
        }
        if (containsAny(normalized, "4년제", "전문대", "학사", "석사", "박사", "학력", "학위")
                || (normalized.contains("대학")
                        && containsAny(normalized, "졸업", "재학", "졸업 예정"))) {
            return CriterionSupportType.EDUCATION;
        }
        if (containsAny(
                normalized,
                "경력", "경험", "인턴", "대외활동", "프로젝트", "역량", "기술", "스킬",
                "개발", "운영", "도메인", "experience", "skill", "project")) {
            return CriterionSupportType.EXPERIENCE_OR_SKILL;
        }
        return CriterionSupportType.GENERAL;
    }

    private LocalDate requiredByDate(String value) {
        Matcher matcher = WORK_DATE.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            int year = Integer.parseInt(matcher.group("year"));
            int month = Integer.parseInt(matcher.group("month"));
            String day = matcher.group("day");
            return day == null
                    ? YearMonth.of(year, month).atEndOfMonth()
                    : LocalDate.of(year, month, Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private String sourceLocation(RequirementSection section) {
        return switch (section) {
            case RESPONSIBILITY -> "주요 업무";
            case REQUIRED_QUALIFICATION -> "지원 자격";
            case PREFERRED_QUALIFICATION -> "우대 사항";
            case ROLE_SUMMARY, OTHER -> null;
        };
    }

    private String deduplicationKey(RequirementCandidate value) {
        return value.supportType()
                + "|"
                + normalize(value.text()).replaceAll("[\\s.!?]+$", "");
    }

    private boolean hasOverseasTravel(String value) {
        String normalized = normalize(value);
        return normalized.contains("해외여행") || normalized.contains("해외 여행");
    }

    private boolean hasEmploymentDisqualification(String value) {
        String normalized = normalize(value);
        return containsAny(normalized, "채용 결격", "채용에 결격", "결격 사유", "결격사유");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String clean(String value) {
        return LEADING_MARKER.matcher(value == null ? "" : value.trim())
                .replaceFirst("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanSourceText(String value) {
        if (value == null) {
            return "";
        }
        String normalizedLines = value.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Zs}\\t\\f]+", " ")
                .replaceAll(" *\\n *", "\n");
        return LEADING_MARKER.matcher(normalizedLines).replaceFirst("").trim();
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }
}
