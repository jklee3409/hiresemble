package com.hiresemble.profile.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort;
import com.hiresemble.profile.domain.model.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.ProfileRecord;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileAnalysisQueryService implements ProfileAnalysisQueryPort {

    private final ProfileStore store;

    public ProfileAnalysisQueryService(ProfileStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisProfileSnapshot loadAnalysisSnapshot(UUID userId) {
        ProfileRecord profile = store.findProfile(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new AnalysisProfileSnapshot(
                profile.id(),
                profile.version(),
                profile.introduction(),
                profile.desiredRoles(),
                profile.desiredIndustries(),
                profile.desiredLocations(),
                profile.expectedGraduationDate(),
                store.findVerifiedEvidenceForAnalysis(userId).stream()
                        .map(this::evidence)
                        .toList());
    }

    private AnalysisEvidence evidence(EvidenceRecord evidence) {
        return new AnalysisEvidence(
                evidence.id(),
                evidence.sourceType(),
                evidence.sourceEntityId(),
                evidence.documentId(),
                evidence.evidenceCategory(),
                evidence.title(),
                evidence.content(),
                evidence.verificationStatus(),
                evidence.sourceDeletedAt() != null,
                evidence.version());
    }
}
