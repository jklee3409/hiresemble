package com.hiresemble.profile.application.port;

import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EmploymentDisqualificationStatus;
import com.hiresemble.profile.domain.model.MilitaryStatus;
import com.hiresemble.profile.domain.model.OverseasTravelEligibility;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProfileAnalysisQueryPort {

    AnalysisProfileSnapshot loadAnalysisSnapshot(UUID userId);

    record AnalysisProfileSnapshot(
            UUID profileId,
            long version,
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            LocalDate expectedGraduationDate,
            AnalysisEducation primaryEducation,
            AnalysisEligibility eligibility,
            List<AnalysisEvidence> verifiedEvidence) {
        public AnalysisProfileSnapshot {
            desiredRoles = List.copyOf(desiredRoles);
            desiredIndustries = List.copyOf(desiredIndustries);
            desiredLocations = List.copyOf(desiredLocations);
            verifiedEvidence = List.copyOf(verifiedEvidence);
        }

        public AnalysisProfileSnapshot(
                UUID profileId,
                long version,
                String introduction,
                List<String> desiredRoles,
                List<String> desiredIndustries,
                List<String> desiredLocations,
                LocalDate expectedGraduationDate,
                List<AnalysisEvidence> verifiedEvidence) {
            this(profileId, version, introduction, desiredRoles, desiredIndustries, desiredLocations,
                    expectedGraduationDate, null, null, verifiedEvidence);
        }
    }

    record AnalysisEducation(
            UUID id,
            long version,
            EducationLevel educationLevel,
            EducationStatus educationStatus,
            String degree,
            String major,
            LocalDate graduationDate,
            boolean primary) {}

    record AnalysisEligibility(
            UUID id,
            long version,
            LocalDate workAvailableDate,
            MilitaryStatus militaryStatus,
            OverseasTravelEligibility overseasTravelEligibility,
            EmploymentDisqualificationStatus employmentDisqualificationStatus) {}

    record AnalysisEvidence(
            UUID id,
            EvidenceSourceType sourceType,
            UUID sourceEntityId,
            UUID documentId,
            String evidenceCategory,
            String title,
            String content,
            EvidenceVerificationStatus verificationStatus,
            boolean sourceDeleted,
            long version) {}
}
