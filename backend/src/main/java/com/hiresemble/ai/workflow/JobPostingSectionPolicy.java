package com.hiresemble.ai.workflow;

import com.hiresemble.ai.workflow.JobAnalysisWorkflow.JobSourceBlock;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementSection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Builds stable, server-owned source blocks before a model selects scorable requirements. */
public final class JobPostingSectionPolicy {

    private static final Pattern LEADING_MARKER =
            Pattern.compile("^\\s*(?:[-*•▪◦]|\\d+[.)])\\s*");

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

    static RequirementSection sourceSectionHint(String sourceSection) {
        String normalized = sourceSection == null ? "" : sourceSection.toLowerCase(Locale.ROOT);
        if (containsAnyStatic(normalized, "우대", "preferred")) {
            return RequirementSection.PREFERRED_QUALIFICATION;
        }
        if (containsAnyStatic(normalized, "업무", "responsibil", "duties")) {
            return RequirementSection.RESPONSIBILITY;
        }
        if (containsAnyStatic(normalized, "자격", "필수", "required", "qualification")) {
            return RequirementSection.REQUIRED_QUALIFICATION;
        }
        return RequirementSection.OTHER;
    }

    private static boolean containsAnyStatic(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
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
                "업무 내용", "responsibilities", "what you will do")) {
            return RequirementSection.RESPONSIBILITY;
        }
        if (containsAny(heading, "우대 사항", "우대사항", "우대 요건", "preferred",
                "nice to have")) {
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
        return null;
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
