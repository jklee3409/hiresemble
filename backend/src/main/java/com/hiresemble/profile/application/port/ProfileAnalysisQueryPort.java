package com.hiresemble.profile.application.port;

import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
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
            List<AnalysisEvidence> verifiedEvidence) {
        public AnalysisProfileSnapshot {
            desiredRoles = List.copyOf(desiredRoles);
            desiredIndustries = List.copyOf(desiredIndustries);
            desiredLocations = List.copyOf(desiredLocations);
            verifiedEvidence = List.copyOf(verifiedEvidence);
        }
    }

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
