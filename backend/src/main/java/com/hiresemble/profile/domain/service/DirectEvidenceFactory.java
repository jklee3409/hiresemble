package com.hiresemble.profile.domain.service;

import com.hiresemble.profile.domain.model.DirectEvidenceData;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DirectEvidenceFactory {

    private DirectEvidenceFactory() {}

    public static DirectEvidenceData education(
            String schoolName,
            String major,
            String degree,
            EducationStatus status,
            LocalDate admissionDate,
            LocalDate graduationDate,
            BigDecimal gpa,
            BigDecimal gpaScale,
            boolean primary,
            String description) {
        Map<String, Object> metadata = metadata(
                "schoolName", schoolName,
                "major", major,
                "degree", degree,
                "educationStatus", status.name(),
                "admissionDate", text(admissionDate),
                "graduationDate", text(graduationDate),
                "gpa", gpa,
                "gpaScale", gpaScale,
                "isPrimary", primary);
        return data(
                EvidenceSourceType.EDUCATION,
                "EDUCATION",
                schoolName,
                firstContent(description, schoolName, major, degree, status.name()),
                metadata);
    }

    public static DirectEvidenceData certification(
            String name,
            String issuer,
            String credentialNumber,
            LocalDate acquiredDate,
            LocalDate expiresAt,
            String description) {
        return data(
                EvidenceSourceType.CERTIFICATION,
                "CERTIFICATION",
                name,
                firstContent(description, name, issuer, text(acquiredDate), text(expiresAt)),
                metadata(
                        "name", name,
                        "issuer", issuer,
                        "credentialNumber", credentialNumber,
                        "acquiredDate", text(acquiredDate),
                        "expiresAt", text(expiresAt)));
    }

    public static DirectEvidenceData languageScore(
            String testName,
            String score,
            String grade,
            LocalDate testedAt,
            LocalDate expiresAt) {
        return data(
                EvidenceSourceType.LANGUAGE_SCORE,
                "LANGUAGE_SCORE",
                testName,
                firstContent(null, testName, score, grade, text(testedAt), text(expiresAt)),
                metadata(
                        "testName", testName,
                        "score", score,
                        "grade", grade,
                        "testedAt", text(testedAt),
                        "expiresAt", text(expiresAt)));
    }

    public static DirectEvidenceData award(
            String name, String organizer, LocalDate awardedAt, String description) {
        return data(
                EvidenceSourceType.AWARD,
                "AWARD",
                name,
                firstContent(description, name, organizer, text(awardedAt)),
                metadata("name", name, "organizer", organizer, "awardedAt", text(awardedAt)));
    }

    public static DirectEvidenceData career(
            String organization,
            String position,
            String employmentType,
            LocalDate startedAt,
            LocalDate endedAt,
            boolean current,
            String responsibilities,
            String achievements) {
        return data(
                EvidenceSourceType.CAREER,
                "CAREER",
                organization,
                firstContent(
                        joinBodies(responsibilities, achievements),
                        organization,
                        position,
                        employmentType,
                        text(startedAt),
                        current ? "CURRENT" : text(endedAt)),
                metadata(
                        "organization", organization,
                        "position", position,
                        "employmentType", employmentType,
                        "startedAt", text(startedAt),
                        "endedAt", text(endedAt),
                        "isCurrent", current));
    }

    private static DirectEvidenceData data(
            EvidenceSourceType sourceType,
            String category,
            String title,
            String content,
            Map<String, Object> metadata) {
        return new DirectEvidenceData(sourceType, category, title, truncate(content), metadata);
    }

    private static String firstContent(String preferred, String... values) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        StringBuilder content = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append(" | ");
            }
            content.append(value);
        }
        return content.toString();
    }

    private static String joinBodies(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n" + second;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String truncate(String value) {
        int codePoints = value.codePointCount(0, value.length());
        return codePoints <= 20000
                ? value
                : value.substring(0, value.offsetByCodePoints(0, 20000));
    }

    private static Map<String, Object> metadata(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return values;
    }
}
