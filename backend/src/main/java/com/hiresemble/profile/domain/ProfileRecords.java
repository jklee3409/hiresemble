package com.hiresemble.profile.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileRecords {

    private ProfileRecords() {}

    public record ProfileRecord(
            UUID id,
            UUID userId,
            String legalName,
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            LocalDate expectedGraduationDate,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record ProfileView(ProfileRecord profile, ProfileCompletion completion) {}

    public record EducationRecord(
            UUID id,
            UUID userId,
            String schoolName,
            String major,
            String degree,
            EducationStatus educationStatus,
            LocalDate admissionDate,
            LocalDate graduationDate,
            BigDecimal gpa,
            BigDecimal gpaScale,
            boolean primary,
            String description,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record CertificationRecord(
            UUID id,
            UUID userId,
            String name,
            String issuer,
            String credentialNumber,
            LocalDate acquiredDate,
            LocalDate expiresAt,
            String description,
            UUID evidenceDocumentId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record LanguageScoreRecord(
            UUID id,
            UUID userId,
            String testName,
            String score,
            String grade,
            LocalDate testedAt,
            LocalDate expiresAt,
            UUID evidenceDocumentId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record AwardRecord(
            UUID id,
            UUID userId,
            String name,
            String organizer,
            LocalDate awardedAt,
            String description,
            UUID evidenceDocumentId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record CareerRecord(
            UUID id,
            UUID userId,
            String organization,
            String position,
            String employmentType,
            LocalDate startedAt,
            LocalDate endedAt,
            boolean current,
            String responsibilities,
            String achievements,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record EvidenceRecord(
            UUID id,
            UUID userId,
            EvidenceSourceType sourceType,
            UUID sourceEntityId,
            UUID documentId,
            Instant sourceDeletedAt,
            String evidenceCategory,
            String title,
            String content,
            Map<String, Object> metadata,
            BigDecimal confidence,
            EvidenceVerificationStatus verificationStatus,
            Instant verifiedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record PageSlice<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }
}
