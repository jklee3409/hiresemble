package com.hiresemble.ai.workflow;

import com.hiresemble.ai.workflow.JobAnalysisWorkflow.JobSourceBlock;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementSection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds stable, server-owned source blocks before a model selects scorable requirements. */
public final class JobPostingSectionPolicy {

    private static final Pattern LEADING_MARKER =
            Pattern.compile("^\\s*(?:[-*•▪◦]|\\d+[.)])\\s*");
    /** {@code 우대역량: 금융시장 이해도} style lines: a short known label followed by its content. */
    private static final Pattern INLINE_LABEL =
            Pattern.compile("^[※*]?\\s*([^:：]{1,30})[:：]\\s*(\\S.*)$");
    private static final int MAX_NON_SCORABLE_HEADING_CHARACTERS = 30;
    /**
     * A heading keyword may only be followed by a title suffix such as "및 일정" or "(요약)", so
     * condition lines like "근무지 이동 가능자" or duties like "급여 정산 업무" stay content.
     */
    private static final Pattern HEADING_SUFFIX = Pattern.compile(
            "(?:및 )?(?:요약|안내|일정|절차|정보|사항|제도|혜택|복지|방법|기간|조건)?");
    private static final List<String> NON_SCORABLE_HEADINGS = List.of(
            "채용 절차", "채용절차", "전형 절차", "전형절차", "채용 일정", "채용일정",
            "전형 일정", "전형일정", "접수 기간", "접수기간", "접수 방법", "접수방법",
            "지원 방법", "지원방법", "제출 서류", "제출서류", "복리후생", "복리 후생",
            "복지", "혜택", "처우", "급여", "근무 조건", "근무조건", "근무 형태", "근무형태",
            "근무지", "근무 지역", "근무지역", "모집 지역", "모집지역", "모집 인원",
            "모집인원", "유의 사항", "유의사항", "참고 사항", "참고사항", "기타 사항",
            "기타사항", "안내 사항", "안내사항", "문의", "회사 소개", "회사소개",
            "기업 소개", "기업소개", "hiring process", "benefits", "contact");

    public List<JobSourceBlock> segment(String descriptionText) {
        if (descriptionText == null || descriptionText.isBlank()) {
            return List.of();
        }
        String normalized = descriptionText.replace("\r\n", "\n").replace('\r', '\n');
        List<JobSourceBlock> blocks = new ArrayList<>();
        RequirementSection current = RequirementSection.OTHER;
        int ordinal = 0;
        for (String rawLine : normalized.split("\\n")) {
            String line = clean(rawLine);
            if (line.isBlank()) {
                continue;
            }
            Matcher inline = INLINE_LABEL.matcher(line);
            if (inline.matches()) {
                RequirementSection labelled = heading(inline.group(1));
                if (labelled != null) {
                    String content = inline.group(2).trim();
                    blocks.add(new JobSourceBlock(
                            sourceBlockId(labelled, content), labelled, content, ordinal));
                    ordinal++;
                    continue;
                }
            }
            RequirementSection heading = heading(line);
            if (heading != null) {
                current = heading;
                continue;
            }
            blocks.add(new JobSourceBlock(sourceBlockId(current, line), current, line, ordinal));
            ordinal++;
        }
        return List.copyOf(blocks);
    }

    static String sourceBlockId(RequirementSection section, String sourceText) {
        String normalized = sourceText == null ? "" : sourceText.strip().replaceAll("\\s+", " ");
        return "B-" + UUID.nameUUIDFromBytes(
                (section.name() + "|" + normalized).getBytes(StandardCharsets.UTF_8));
    }

    private RequirementSection heading(String value) {
        String heading = value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{So}\\p{Sk}\\p{Punct}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (heading.length() > 80) {
            return null;
        }
        if (containsAny(heading, "담당 업무", "담당업무", "주요 업무", "주요업무",
                "업무 내용", "모집 직무", "모집직무", "모집 분야", "모집분야", "모집 부문",
                "모집부문", "상세 직무", "상세직무", "responsibilities", "what you will do")) {
            return RequirementSection.RESPONSIBILITY;
        }
        if (containsAny(heading, "우대 사항", "우대사항", "우대 요건", "우대요건", "우대 역량",
                "우대역량", "preferred", "nice to have")) {
            return RequirementSection.PREFERRED_QUALIFICATION;
        }
        if (containsAny(heading, "자격 요건", "자격요건", "지원 자격", "지원자격",
                "필수 요건", "필수요건", "required qualifications", "requirements")) {
            return RequirementSection.REQUIRED_QUALIFICATION;
        }
        if (containsAny(heading, "직무 소개", "포지션 소개", "역할 소개", "이란", "role overview")
                || heading.endsWith("란")) {
            return RequirementSection.ROLE_SUMMARY;
        }
        if (nonScorableHeading(heading)) {
            return RequirementSection.OTHER;
        }
        return null;
    }

    /**
     * Process, benefit, notice, and contact headings end a qualification section. They must start
     * the line and stay short so a qualification sentence that mentions them is not dropped.
     */
    private boolean nonScorableHeading(String heading) {
        if (heading.length() > MAX_NON_SCORABLE_HEADING_CHARACTERS) {
            return false;
        }
        for (String keyword : NON_SCORABLE_HEADINGS) {
            if (heading.startsWith(keyword)) {
                return HEADING_SUFFIX.matcher(heading.substring(keyword.length()).trim())
                        .matches();
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.equals(candidate) || value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String clean(String value) {
        return LEADING_MARKER.matcher(value == null ? "" : value.trim())
                .replaceFirst("")
                .replaceAll("[\\p{Zs}\\t\\f]+", " ")
                .trim();
    }
}
