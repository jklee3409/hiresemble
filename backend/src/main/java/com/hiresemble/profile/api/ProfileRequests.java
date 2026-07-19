package com.hiresemble.profile.api;

import com.hiresemble.profile.domain.EducationStatus;
import com.hiresemble.profile.domain.EvidenceVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileRequests {

    private ProfileRequests() {}

    @Schema(name = "ProfileWrite")
    public record ProfileUpdateRequest(
            @Schema(nullable = true) @Size(min = 1, max = 100) String legalName,
            @Schema(nullable = true) @Size(max = 2000) String introduction,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 100) String> desiredRoles,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 100) String> desiredIndustries,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 100) String> desiredLocations,
            @Schema(nullable = true) LocalDate expectedGraduationDate,
            @NotNull @PositiveOrZero Long version) {}

    public interface EducationFields {
        String schoolName();

        String major();

        String degree();

        EducationStatus educationStatus();

        LocalDate admissionDate();

        LocalDate graduationDate();

        BigDecimal gpa();

        BigDecimal gpaScale();

        Boolean isPrimary();

        String description();
    }

    @Schema(name = "EducationCreateRequest")
    public record EducationCreateRequest(
            @NotBlank @Size(max = 200) String schoolName,
            @Schema(nullable = true) @Size(max = 200) String major,
            @Schema(nullable = true) @Size(max = 100) String degree,
            @NotNull EducationStatus educationStatus,
            @Schema(nullable = true) LocalDate admissionDate,
            @Schema(nullable = true) LocalDate graduationDate,
            @Schema(nullable = true) @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal gpa,
            @Schema(nullable = true) @DecimalMin("0.01") @DecimalMax("10.00") BigDecimal gpaScale,
            @NotNull Boolean isPrimary,
            @Schema(nullable = true) @Size(max = 5000) String description)
            implements EducationFields {}

    @Schema(name = "EducationUpdateRequest")
    public record EducationUpdateRequest(
            @NotBlank @Size(max = 200) String schoolName,
            @Schema(nullable = true) @Size(max = 200) String major,
            @Schema(nullable = true) @Size(max = 100) String degree,
            @NotNull EducationStatus educationStatus,
            @Schema(nullable = true) LocalDate admissionDate,
            @Schema(nullable = true) LocalDate graduationDate,
            @Schema(nullable = true) @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal gpa,
            @Schema(nullable = true) @DecimalMin("0.01") @DecimalMax("10.00") BigDecimal gpaScale,
            @NotNull Boolean isPrimary,
            @Schema(nullable = true) @Size(max = 5000) String description,
            @NotNull @PositiveOrZero Long version)
            implements EducationFields {}

    public interface CertificationFields {
        String name();

        String issuer();

        String credentialNumber();

        LocalDate acquiredDate();

        LocalDate expiresAt();

        String description();

        UUID evidenceDocumentId();
    }

    @Schema(name = "CertificationCreateRequest")
    public record CertificationCreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Schema(nullable = true) @Size(max = 200) String issuer,
            @Schema(nullable = true) @Size(max = 200) String credentialNumber,
            @Schema(nullable = true) LocalDate acquiredDate,
            @Schema(nullable = true) LocalDate expiresAt,
            @Schema(nullable = true) @Size(max = 5000) String description,
            @Schema(nullable = true, description = "Must reference an active document owned by the current user.")
                    UUID evidenceDocumentId)
            implements CertificationFields {}

    @Schema(name = "CertificationUpdateRequest")
    public record CertificationUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Schema(nullable = true) @Size(max = 200) String issuer,
            @Schema(nullable = true) @Size(max = 200) String credentialNumber,
            @Schema(nullable = true) LocalDate acquiredDate,
            @Schema(nullable = true) LocalDate expiresAt,
            @Schema(nullable = true) @Size(max = 5000) String description,
            @Schema(nullable = true, description = "Must reference an active document owned by the current user.")
                    UUID evidenceDocumentId,
            @NotNull @PositiveOrZero Long version)
            implements CertificationFields {}

    public interface LanguageScoreFields {
        String testName();

        String score();

        String grade();

        LocalDate testedAt();

        LocalDate expiresAt();

        UUID evidenceDocumentId();
    }

    @Schema(name = "LanguageScoreCreateRequest")
    public record LanguageScoreCreateRequest(
            @NotBlank @Size(max = 100) String testName,
            @NotBlank @Size(max = 100) String score,
            @Schema(nullable = true) @Size(max = 100) String grade,
            @Schema(nullable = true) LocalDate testedAt,
            @Schema(nullable = true) LocalDate expiresAt,
            @Schema(nullable = true, description = "Must reference an active document owned by the current user.")
                    UUID evidenceDocumentId)
            implements LanguageScoreFields {}

    @Schema(name = "LanguageScoreUpdateRequest")
    public record LanguageScoreUpdateRequest(
            @NotBlank @Size(max = 100) String testName,
            @NotBlank @Size(max = 100) String score,
            @Schema(nullable = true) @Size(max = 100) String grade,
            @Schema(nullable = true) LocalDate testedAt,
            @Schema(nullable = true) LocalDate expiresAt,
            @Schema(nullable = true, description = "Must reference an active document owned by the current user.")
                    UUID evidenceDocumentId,
            @NotNull @PositiveOrZero Long version)
            implements LanguageScoreFields {}

    public interface AwardFields {
        String name();

        String organizer();

        LocalDate awardedAt();

        String description();

        UUID evidenceDocumentId();
    }

    @Schema(name = "AwardCreateRequest")
    public record AwardCreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Schema(nullable = true) @Size(max = 200) String organizer,
            @Schema(nullable = true) LocalDate awardedAt,
            @Schema(nullable = true) @Size(max = 5000) String description,
            @Schema(nullable = true, description = "Must reference an active document owned by the current user.")
                    UUID evidenceDocumentId)
            implements AwardFields {}

    @Schema(name = "AwardUpdateRequest")
    public record AwardUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Schema(nullable = true) @Size(max = 200) String organizer,
            @Schema(nullable = true) LocalDate awardedAt,
            @Schema(nullable = true) @Size(max = 5000) String description,
            @Schema(nullable = true, description = "Must reference an active document owned by the current user.")
                    UUID evidenceDocumentId,
            @NotNull @PositiveOrZero Long version)
            implements AwardFields {}

    public interface CareerFields {
        String organization();

        String position();

        String employmentType();

        LocalDate startedAt();

        LocalDate endedAt();

        Boolean isCurrent();

        String responsibilities();

        String achievements();
    }

    @Schema(name = "CareerCreateRequest")
    public record CareerCreateRequest(
            @NotBlank @Size(max = 200) String organization,
            @Schema(nullable = true) @Size(max = 200) String position,
            @Schema(nullable = true) @Size(max = 50) String employmentType,
            @Schema(nullable = true) LocalDate startedAt,
            @Schema(nullable = true) LocalDate endedAt,
            @NotNull Boolean isCurrent,
            @Schema(nullable = true) @Size(max = 20000) String responsibilities,
            @Schema(nullable = true) @Size(max = 20000) String achievements)
            implements CareerFields {}

    @Schema(name = "CareerUpdateRequest")
    public record CareerUpdateRequest(
            @NotBlank @Size(max = 200) String organization,
            @Schema(nullable = true) @Size(max = 200) String position,
            @Schema(nullable = true) @Size(max = 50) String employmentType,
            @Schema(nullable = true) LocalDate startedAt,
            @Schema(nullable = true) LocalDate endedAt,
            @NotNull Boolean isCurrent,
            @Schema(nullable = true) @Size(max = 20000) String responsibilities,
            @Schema(nullable = true) @Size(max = 20000) String achievements,
            @NotNull @PositiveOrZero Long version)
            implements CareerFields {}

    @Schema(name = "EvidenceUpdateRequest")
    public record EvidenceUpdateRequest(
            @NotBlank @Size(max = 250) String title,
            @NotBlank @Size(max = 20000) String content,
            @NotNull Map<String, Object> metadata,
            @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "EvidenceVerificationRequest")
    public record EvidenceVerificationRequest(
            @NotNull EvidenceVerificationStatus status,
            @NotNull @PositiveOrZero Long version) {}
}
