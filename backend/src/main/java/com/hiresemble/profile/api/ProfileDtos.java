package com.hiresemble.profile.api;

import com.hiresemble.profile.domain.EducationStatus;
import com.hiresemble.profile.domain.EvidenceSourceType;
import com.hiresemble.profile.domain.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.ProfileCompletionItem;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileDtos {

    private ProfileDtos() {}

    @Schema(name = "ProfileDto")
    public record ProfileDto(
            @Schema(nullable = true, minLength = 1, maxLength = 100) String legalName,
            @Schema(nullable = true, maxLength = 2000) String introduction,
            @ArraySchema(maxItems = 10, schema = @Schema(minLength = 1, maxLength = 100))
                    List<String> desiredRoles,
            @ArraySchema(maxItems = 10, schema = @Schema(minLength = 1, maxLength = 100))
                    List<String> desiredIndustries,
            @ArraySchema(maxItems = 10, schema = @Schema(minLength = 1, maxLength = 100))
                    List<String> desiredLocations,
            @Schema(nullable = true) LocalDate expectedGraduationDate,
            @Schema(accessMode = Schema.AccessMode.READ_ONLY) boolean profileCompleted,
            @ArraySchema(
                            arraySchema = @Schema(accessMode = Schema.AccessMode.READ_ONLY),
                            maxItems = 5,
                            schema = @Schema(implementation = ProfileCompletionItem.class))
                    List<ProfileCompletionItem> missingCompletionItems,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "EducationDto")
    public record EducationDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 200) String schoolName,
            @Schema(nullable = true, maxLength = 200) String major,
            @Schema(nullable = true, maxLength = 100) String degree,
            EducationStatus educationStatus,
            @Schema(nullable = true) LocalDate admissionDate,
            @Schema(nullable = true) LocalDate graduationDate,
            @Schema(nullable = true, minimum = "0", maximum = "10") BigDecimal gpa,
            @Schema(nullable = true, minimum = "0.01", maximum = "10") BigDecimal gpaScale,
            boolean isPrimary,
            @Schema(nullable = true, maxLength = 5000) String description,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CertificationDto")
    public record CertificationDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 200) String name,
            @Schema(nullable = true, maxLength = 200) String issuer,
            @Schema(nullable = true, maxLength = 200) String credentialNumber,
            @Schema(nullable = true) LocalDate acquiredDate,
            @Schema(nullable = true) LocalDate expiresAt,
            @Schema(nullable = true, maxLength = 5000) String description,
            @Schema(nullable = true, description = "Document linkage is deferred until P4.")
                    UUID evidenceDocumentId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "LanguageScoreDto")
    public record LanguageScoreDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 100) String testName,
            @Schema(minLength = 1, maxLength = 100) String score,
            @Schema(nullable = true, maxLength = 100) String grade,
            @Schema(nullable = true) LocalDate testedAt,
            @Schema(nullable = true) LocalDate expiresAt,
            @Schema(nullable = true, description = "Document linkage is deferred until P4.")
                    UUID evidenceDocumentId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "AwardDto")
    public record AwardDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 200) String name,
            @Schema(nullable = true, maxLength = 200) String organizer,
            @Schema(nullable = true) LocalDate awardedAt,
            @Schema(nullable = true, maxLength = 5000) String description,
            @Schema(nullable = true, description = "Document linkage is deferred until P4.")
                    UUID evidenceDocumentId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "CareerDto")
    public record CareerDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 200) String organization,
            @Schema(nullable = true, maxLength = 200) String position,
            @Schema(nullable = true, maxLength = 50) String employmentType,
            @Schema(nullable = true) LocalDate startedAt,
            @Schema(nullable = true) LocalDate endedAt,
            boolean isCurrent,
            @Schema(nullable = true, maxLength = 20000) String responsibilities,
            @Schema(nullable = true, maxLength = 20000) String achievements,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    @Schema(name = "EvidenceDto")
    public record EvidenceDto(
            UUID id,
            EvidenceSourceType sourceType,
            @Schema(nullable = true) UUID sourceEntityId,
            @Schema(nullable = true, description = "Document linkage is deferred until P4.")
                    UUID documentId,
            @Schema(nullable = true) Instant sourceDeletedAt,
            @Schema(minLength = 1, maxLength = 80) String evidenceCategory,
            @Schema(minLength = 1, maxLength = 250) String title,
            @Schema(minLength = 1, maxLength = 20000) String content,
            Map<String, Object> metadata,
            @Schema(nullable = true, minimum = "0", maximum = "1") BigDecimal confidence,
            EvidenceVerificationStatus verificationStatus,
            @Schema(nullable = true) Instant verifiedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {}
}
