package com.hiresemble.profile.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceMatchDecision;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceVerification;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceWrite;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.model.ExperienceMatchResolution;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemDetail;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.PageSlice;
import com.hiresemble.profile.domain.policy.ExperienceSimilarityPolicy;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import com.hiresemble.profile.infrastructure.persistence.ExperienceStore;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperienceApplicationService {

    private static final Set<String> SORTS = Set.of("updatedAt,desc", "createdAt,desc");

    private final ExperienceStore experienceStore;
    private final ProfileStore profileStore;
    private final Clock clock;

    public ExperienceApplicationService(
            ExperienceStore experienceStore, ProfileStore profileStore, Clock clock) {
        this.experienceStore = experienceStore;
        this.profileStore = profileStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageSlice<ExperienceItemRecord> list(
            UUID userId,
            EvidenceVerificationStatus status,
            ExperienceMatchKind matchKind,
            int page,
            int size,
            String sort) {
        if (matchKind == ExperienceMatchKind.SAME_EXPERIENCE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalizedSort = SORTS.contains(sort) ? sort : null;
        if (normalizedSort == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return experienceStore.list(userId, status, matchKind, page, size, normalizedSort);
    }

    @Transactional(readOnly = true)
    public ExperienceItemDetail get(UUID userId, UUID itemId) {
        return experienceStore.findDetail(userId, itemId).orElseThrow(this::notFound);
    }

    @Transactional
    public ExperienceItemDetail update(UUID userId, UUID itemId, ExperienceWrite command) {
        ExperienceItemRecord current = experienceStore.findActive(userId, itemId)
                .orElseThrow(this::notFound);
        String title = ProfilePolicy.requiredLabel(command.title(), 250);
        String content = requiredContent(command.content());
        String fingerprint = ExperienceSimilarityPolicy.fingerprint(
                current.evidenceCategory(), title, content);
        Instant now = clock.instant();
        try {
            if (!experienceStore.updateItem(
                    userId, itemId, title, content, fingerprint, command.version(), now)) {
                throw versionConflict();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT, exception);
        }
        profileStore.synchronizeExperienceEvidenceContent(
                userId, current.canonicalEvidenceId(), title, content, now);
        return get(userId, itemId);
    }

    @Transactional
    public ExperienceItemDetail verify(
            UUID userId, UUID itemId, ExperienceVerification command) {
        requireReviewStatus(command.status());
        ExperienceItemRecord current = experienceStore.findActive(userId, itemId)
                .orElseThrow(this::notFound);
        Instant now = clock.instant();
        if (!experienceStore.updateVerification(
                userId, itemId, command.status(), command.version(), now)) {
            throw versionConflict();
        }
        profileStore.synchronizeExperienceEvidenceVerification(
                userId, current.canonicalEvidenceId(), command.status(), now);
        return get(userId, itemId);
    }

    @Transactional
    public ExperienceItemDetail resolveMatch(
            UUID userId, UUID itemId, ExperienceMatchDecision command) {
        ExperienceItemRecord current = experienceStore.findActive(userId, itemId)
                .orElseThrow(this::notFound);
        if (current.matchKind() == ExperienceMatchKind.NEW) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        if (command.resolution() == ExperienceMatchResolution.KEEP_SEPARATE) {
            if (command.targetExperienceItemId() != null
                    || !experienceStore.keepSeparate(userId, itemId, command.version(), now)) {
                throw versionConflict();
            }
            return get(userId, itemId);
        }
        if (command.resolution() != ExperienceMatchResolution.MERGE_WITH_TARGET
                || command.targetExperienceItemId() == null
                || !command.targetExperienceItemId().equals(current.matchedExperienceItemId())
                || current.verificationStatus() == EvidenceVerificationStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        ExperienceItemRecord target = experienceStore
                .findActive(userId, command.targetExperienceItemId())
                .orElseThrow(this::notFound);
        if (current.version() != command.version()) {
            throw versionConflict();
        }
        experienceStore.moveEvidenceLinks(userId, itemId, target.id());
        experienceStore.redirectInboundMatches(userId, itemId, target.id(), now);
        experienceStore.deleteItem(userId, itemId);
        profileStore.deleteExperienceEvidence(userId, current.canonicalEvidenceId());
        return get(userId, target.id());
    }

    private String requiredContent(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000 || value.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }

    private void requireReviewStatus(EvidenceVerificationStatus status) {
        if (status != EvidenceVerificationStatus.PENDING
                && status != EvidenceVerificationStatus.VERIFIED
                && status != EvidenceVerificationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", "version", "reason", "STALE"),
                null);
    }
}
