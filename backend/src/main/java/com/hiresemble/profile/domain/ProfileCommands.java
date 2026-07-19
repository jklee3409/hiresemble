package com.hiresemble.profile.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileCommands {

    private ProfileCommands() {}

    public record ProfileUpdate(
            String legalName,
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            LocalDate expectedGraduationDate,
            long version) {}

    public record EducationWrite(
            String schoolName,
            String major,
            String degree,
            EducationStatus educationStatus,
            LocalDate admissionDate,
            LocalDate graduationDate,
            BigDecimal gpa,
            BigDecimal gpaScale,
            boolean primary,
            String description) {}

    public record CertificationWrite(
            String name,
            String issuer,
            String credentialNumber,
            LocalDate acquiredDate,
            LocalDate expiresAt,
            String description,
            UUID evidenceDocumentId) {}

    public record LanguageScoreWrite(
            String testName,
            String score,
            String grade,
            LocalDate testedAt,
            LocalDate expiresAt,
            UUID evidenceDocumentId) {}

    public record AwardWrite(
            String name,
            String organizer,
            LocalDate awardedAt,
            String description,
            UUID evidenceDocumentId) {}

    public record CareerWrite(
            String organization,
            String position,
            String employmentType,
            LocalDate startedAt,
            LocalDate endedAt,
            boolean current,
            String responsibilities,
            String achievements) {}

    public record EvidenceWrite(String title, String content, Map<String, Object> metadata) {}
}
