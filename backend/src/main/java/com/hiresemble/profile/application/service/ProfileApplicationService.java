package com.hiresemble.profile.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.application.port.DocumentWorkflowQueryPort;
import com.hiresemble.profile.domain.model.DirectEvidenceData;
import com.hiresemble.profile.domain.model.ActivityType;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceLinkKind;
import com.hiresemble.profile.domain.model.ExperienceRecords.EvidenceExperienceLink;
import com.hiresemble.profile.domain.model.ProfileCommands.AwardWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.ActivityWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.CareerWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.CertificationWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.EducationWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.EvidenceWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.EvidenceVersion;
import com.hiresemble.profile.domain.model.ProfileCommands.LanguageScoreWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.ProfileUpdate;
import com.hiresemble.profile.domain.model.ProfileCommands.ProfileEligibilityUpdate;
import com.hiresemble.profile.domain.model.ProfileCompletion;
import com.hiresemble.profile.domain.model.ProfileRecords.AwardRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.ActivityRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.CareerRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.CertificationRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.EducationRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.LanguageScoreRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.PageSlice;
import com.hiresemble.profile.domain.model.ProfileRecords.ProfileRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.ProfileEligibilityRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.ProfileView;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import com.hiresemble.profile.domain.service.DirectEvidenceFactory;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import com.hiresemble.profile.infrastructure.persistence.ExperienceStore;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProfileApplicationService {

    private static final Comparator<EducationRecord> FINAL_EDUCATION_ORDER = Comparator
            .comparingInt((EducationRecord education) -> education.educationLevel().rank())
            .thenComparingInt(education -> educationStatusRank(education.educationStatus()))
            .thenComparing(
                    EducationRecord::graduationDate,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                    EducationRecord::admissionDate,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EducationRecord::createdAt)
            .thenComparing(EducationRecord::id);

    private static final Set<String> EDUCATION_SORTS =
            Set.of("createdAt,desc", "graduationDate,desc");
    private static final Set<String> CERTIFICATION_SORTS =
            Set.of("acquiredDate,desc", "createdAt,desc");
    private static final Set<String> LANGUAGE_SORTS = Set.of("testedAt,desc", "createdAt,desc");
    private static final Set<String> AWARD_SORTS = Set.of("awardedAt,desc", "createdAt,desc");
    private static final Set<String> CAREER_SORTS = Set.of("startedAt,desc", "createdAt,desc");
    private static final Set<String> ACTIVITY_SORTS = Set.of("startedAt,desc", "createdAt,desc");
    private static final Set<String> EVIDENCE_SORTS =
            Set.of("updatedAt,desc", "confidence,desc");

    private final ProfileStore store;
    private final ObjectMapper objectMapper;
    private final DocumentWorkflowQueryPort documentQueryPort;
    private final ExperienceStore experienceStore;

    public ProfileApplicationService(
            ProfileStore store,
            ObjectMapper objectMapper,
            DocumentWorkflowQueryPort documentQueryPort,
            ExperienceStore experienceStore) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.documentQueryPort = documentQueryPort;
        this.experienceStore = experienceStore;
    }

    @Transactional(readOnly = true)
    public ProfileView getProfile(UUID userId) {
        ProfileRecord profile = store.findProfile(userId).orElseThrow(this::notFound);
        ProfileCompletion completion = ProfileCompletion.calculate(
                profile.legalName(),
                profile.desiredRoles(),
                profile.desiredIndustries(),
                profile.desiredLocations(),
                store.hasPrimaryEducation(userId));
        return new ProfileView(profile, completion);
    }

    @Transactional
    public ProfileView updateProfile(UUID userId, ProfileUpdate input) {
        ProfileUpdate command = new ProfileUpdate(
                ProfilePolicy.optionalLabel(input.legalName(), 100),
                ProfilePolicy.optionalBody(input.introduction(), 2000),
                ProfilePolicy.canonicalArray(input.desiredRoles()),
                ProfilePolicy.canonicalArray(input.desiredIndustries()),
                ProfilePolicy.canonicalArray(input.desiredLocations()),
                input.expectedGraduationDate(),
                input.version());
        ProfileRecord updated = store.updateProfile(userId, command, Instant.now())
                .orElseGet(() -> {
                    if (store.findProfile(userId).isPresent()) {
                        throw versionConflict();
                    }
                    throw notFound();
                });
        ProfileCompletion completion = ProfileCompletion.calculate(
                updated.legalName(),
                updated.desiredRoles(),
                updated.desiredIndustries(),
                updated.desiredLocations(),
                store.hasPrimaryEducation(userId));
        return new ProfileView(updated, completion);
    }

    @Transactional(readOnly = true)
    public ProfileEligibilityRecord getEligibility(UUID userId) {
        if (store.findProfile(userId).isEmpty()) {
            throw notFound();
        }
        return store.findEligibility(userId).orElseThrow(this::notFound);
    }

    @Transactional
    public ProfileEligibilityRecord updateEligibility(
            UUID userId, ProfileEligibilityUpdate input) {
        if (input.militaryStatus() == null
                || input.overseasTravelEligibility() == null
                || input.employmentDisqualificationStatus() == null
                || input.version() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        ProfileEligibilityUpdate command = new ProfileEligibilityUpdate(
                input.workAvailableDate(),
                input.militaryStatus(),
                input.overseasTravelEligibility(),
                input.employmentDisqualificationStatus(),
                input.version());
        return store.updateEligibility(userId, command, Instant.now()).orElseGet(() -> {
            if (store.findEligibility(userId).isPresent()) {
                throw versionConflict();
            }
            throw notFound();
        });
    }

    @Transactional(readOnly = true)
    public PageSlice<EducationRecord> listEducations(
            UUID userId, int page, int size, String sort) {
        return store.listEducations(userId, page, size, sort(sort, EDUCATION_SORTS, "createdAt,desc"));
    }

    @Transactional
    public EducationRecord createEducation(UUID userId, EducationWrite input) {
        EducationWrite command = education(input);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        try {
            requireProfileLock(userId);
            store.createEducation(id, userId, command, now);
            recalculateFinalEducation(userId, now);
            return store.findEducation(userId, id).orElseThrow(this::notFound);
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
    }

    @Transactional
    public EducationRecord updateEducation(
            UUID userId, UUID educationId, EducationWrite input, long version) {
        EducationWrite command = education(input);
        requireProfileLock(userId);
        EducationRecord current = store.findEducation(userId, educationId).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        try {
            store.updateEducation(userId, educationId, command, version, now)
                    .orElseThrow(this::versionConflict);
            recalculateFinalEducation(userId, now);
            return store.findEducation(userId, educationId).orElseThrow(this::notFound);
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
    }

    @Transactional
    public void deleteEducation(UUID userId, UUID educationId, long version) {
        requireProfileLock(userId);
        EducationRecord record = store.findEducation(userId, educationId).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        Instant now = Instant.now();
        if (!store.softDeleteSource("educations", userId, educationId, version, now)) {
            throw versionConflict();
        }
        recalculateFinalEducation(userId, now);
    }

    @Transactional(readOnly = true)
    public PageSlice<CertificationRecord> listCertifications(
            UUID userId, int page, int size, String sort) {
        return store.listCertifications(
                userId, page, size, sort(sort, CERTIFICATION_SORTS, "acquiredDate,desc"));
    }

    @Transactional
    public CertificationRecord createCertification(UUID userId, CertificationWrite input) {
        CertificationWrite command = certification(input);
        requireActiveDocument(userId, command.evidenceDocumentId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        CertificationRecord created = store.createCertification(id, userId, command, now);
        store.createDirectEvidence(UUID.randomUUID(), userId, id, evidence(created), now);
        return created;
    }

    @Transactional
    public CertificationRecord updateCertification(
            UUID userId, UUID id, CertificationWrite input, long version) {
        CertificationWrite command = certification(input);
        requireActiveDocument(userId, command.evidenceDocumentId());
        CertificationRecord current = store.findCertification(userId, id).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        CertificationRecord updated = store.updateCertification(userId, id, command, version, now)
                .orElseThrow(this::versionConflict);
        store.synchronizeDirectEvidence(userId, id, evidence(updated), now);
        return updated;
    }

    @Transactional
    public void deleteCertification(UUID userId, UUID id, long version) {
        CertificationRecord record = store.findCertification(userId, id).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        deleteSource("certifications", userId, id, version, EvidenceSourceType.CERTIFICATION);
    }

    @Transactional(readOnly = true)
    public PageSlice<LanguageScoreRecord> listLanguageScores(
            UUID userId, int page, int size, String sort) {
        return store.listLanguageScores(
                userId, page, size, sort(sort, LANGUAGE_SORTS, "testedAt,desc"));
    }

    @Transactional
    public LanguageScoreRecord createLanguageScore(UUID userId, LanguageScoreWrite input) {
        LanguageScoreWrite command = languageScore(input);
        requireActiveDocument(userId, command.evidenceDocumentId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        LanguageScoreRecord created = store.createLanguageScore(id, userId, command, now);
        store.createDirectEvidence(UUID.randomUUID(), userId, id, evidence(created), now);
        return created;
    }

    @Transactional
    public LanguageScoreRecord updateLanguageScore(
            UUID userId, UUID id, LanguageScoreWrite input, long version) {
        LanguageScoreWrite command = languageScore(input);
        requireActiveDocument(userId, command.evidenceDocumentId());
        LanguageScoreRecord current = store.findLanguageScore(userId, id).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        LanguageScoreRecord updated = store.updateLanguageScore(userId, id, command, version, now)
                .orElseThrow(this::versionConflict);
        store.synchronizeDirectEvidence(userId, id, evidence(updated), now);
        return updated;
    }

    @Transactional
    public void deleteLanguageScore(UUID userId, UUID id, long version) {
        LanguageScoreRecord record = store.findLanguageScore(userId, id).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        deleteSource("language_scores", userId, id, version, EvidenceSourceType.LANGUAGE_SCORE);
    }

    @Transactional(readOnly = true)
    public PageSlice<AwardRecord> listAwards(UUID userId, int page, int size, String sort) {
        return store.listAwards(userId, page, size, sort(sort, AWARD_SORTS, "awardedAt,desc"));
    }

    @Transactional
    public AwardRecord createAward(UUID userId, AwardWrite input) {
        AwardWrite command = award(input);
        requireActiveDocument(userId, command.evidenceDocumentId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AwardRecord created = store.createAward(id, userId, command, now);
        store.createDirectEvidence(UUID.randomUUID(), userId, id, evidence(created), now);
        return created;
    }

    @Transactional
    public AwardRecord updateAward(UUID userId, UUID id, AwardWrite input, long version) {
        AwardWrite command = award(input);
        requireActiveDocument(userId, command.evidenceDocumentId());
        AwardRecord current = store.findAward(userId, id).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        AwardRecord updated = store.updateAward(userId, id, command, version, now)
                .orElseThrow(this::versionConflict);
        store.synchronizeDirectEvidence(userId, id, evidence(updated), now);
        return updated;
    }

    @Transactional
    public void deleteAward(UUID userId, UUID id, long version) {
        AwardRecord record = store.findAward(userId, id).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        deleteSource("awards", userId, id, version, EvidenceSourceType.AWARD);
    }

    @Transactional(readOnly = true)
    public PageSlice<CareerRecord> listCareers(UUID userId, int page, int size, String sort) {
        return store.listCareers(userId, page, size, sort(sort, CAREER_SORTS, "startedAt,desc"));
    }

    @Transactional
    public CareerRecord createCareer(UUID userId, CareerWrite input) {
        CareerWrite command = career(input);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        CareerRecord created = store.createCareer(id, userId, command, now);
        store.createDirectEvidence(UUID.randomUUID(), userId, id, evidence(created), now);
        return created;
    }

    @Transactional
    public CareerRecord updateCareer(
            UUID userId, UUID id, CareerWrite input, long version) {
        CareerWrite command = career(input);
        CareerRecord current = store.findCareer(userId, id).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        CareerRecord updated = store.updateCareer(userId, id, command, version, now)
                .orElseThrow(this::versionConflict);
        store.synchronizeDirectEvidence(userId, id, evidence(updated), now);
        return updated;
    }

    @Transactional
    public void deleteCareer(UUID userId, UUID id, long version) {
        CareerRecord record = store.findCareer(userId, id).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        deleteSource("careers", userId, id, version, EvidenceSourceType.CAREER);
    }

    @Transactional(readOnly = true)
    public PageSlice<ActivityRecord> listActivities(
            UUID userId, int page, int size, String sort) {
        return store.listActivities(
                userId, page, size, sort(sort, ACTIVITY_SORTS, "startedAt,desc"));
    }

    @Transactional(readOnly = true)
    public ActivityRecord getActivity(UUID userId, UUID id) {
        return store.findActivity(userId, id).orElseThrow(this::notFound);
    }

    @Transactional
    public ActivityRecord createActivity(UUID userId, ActivityWrite input) {
        ActivityWrite command = activity(input);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ActivityRecord created = store.createActivity(id, userId, command, now);
        store.createDirectEvidence(
                UUID.randomUUID(), userId, id, evidence(created), materialStatus(created.useAsMaterial()), now);
        return created;
    }

    @Transactional
    public ActivityRecord updateActivity(
            UUID userId, UUID id, ActivityWrite input, long version) {
        ActivityWrite command = activity(input);
        ActivityRecord current = store.findActivity(userId, id).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        ActivityRecord updated = store.updateActivity(userId, id, command, version, now)
                .orElseThrow(this::versionConflict);
        store.synchronizeDirectEvidence(
                userId, id, evidence(updated), materialStatus(updated.useAsMaterial()), now);
        return updated;
    }

    @Transactional
    public void deleteActivity(UUID userId, UUID id, long version) {
        ActivityRecord record = store.findActivity(userId, id).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        deleteSource("activities", userId, id, version, EvidenceSourceType.ACTIVITY);
    }

    @Transactional(readOnly = true)
    public PageSlice<EvidenceRecord> listEvidence(
            UUID userId,
            EvidenceVerificationStatus status,
            String category,
            UUID documentId,
            int page,
            int size,
            String sort) {
        requireActiveDocument(userId, documentId);
        String normalizedCategory = category == null ? null : ProfilePolicy.requiredLabel(category, 80);
        PageSlice<EvidenceRecord> result = store.listEvidence(
                userId,
                status,
                normalizedCategory,
                documentId,
                page,
                size,
                sort(sort, EVIDENCE_SORTS, "updatedAt,desc"));
        Map<UUID, EvidenceExperienceLink> links = experienceStore.findBySourceEvidence(
                userId, result.items().stream().map(EvidenceRecord::id).toList());
        return new PageSlice<>(
                result.items().stream()
                        .map(evidence -> withExperienceLink(evidence, links.get(evidence.id())))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @Transactional
    public EvidenceRecord updateEvidence(
            UUID userId, UUID id, EvidenceWrite input, long version) {
        EvidenceRecord current = store.findEvidence(userId, id).orElseThrow(this::notFound);
        requireEditable(current);
        if (current.sourceType() == EvidenceSourceType.EXPERIENCE) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        requireVersion(current.version(), version);
        EvidenceWrite command = new EvidenceWrite(
                ProfilePolicy.requiredLabel(input.title(), 250),
                requiredContent(input.content()),
                validMetadata(input.metadata()));
        EvidenceRecord updated = store.updateEvidence(userId, id, command, version, Instant.now())
                .orElseThrow(this::versionConflict);
        return withExperienceLink(
                updated, experienceStore.findBySourceEvidence(userId, id).orElse(null));
    }

    @Transactional
    public EvidenceRecord verifyEvidence(
            UUID userId,
            UUID id,
            EvidenceVerificationStatus status,
            long version) {
        if (status != EvidenceVerificationStatus.PENDING
                && status != EvidenceVerificationStatus.VERIFIED
                && status != EvidenceVerificationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        EvidenceRecord current = store.findEvidence(userId, id).orElseThrow(this::notFound);
        requireEditable(current);
        if (current.sourceType() != EvidenceSourceType.DOCUMENT_CHUNK) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        EvidenceRecord updated = store.verifyEvidence(userId, id, status, version, now)
                .orElseThrow(this::versionConflict);
        experienceStore.findBySourceEvidence(userId, id)
                .filter(link -> link.relationKind() == ExperienceLinkKind.PRIMARY_SOURCE)
                .ifPresent(link -> {
                    experienceStore.synchronizeVerification(
                            userId, link.experienceItemId(), status, now);
                    store.synchronizeExperienceEvidenceVerification(
                            userId, link.canonicalEvidenceId(), status, now);
                });
        return withExperienceLink(
                updated, experienceStore.findBySourceEvidence(userId, id).orElse(null));
    }

    @Transactional
    public List<EvidenceRecord> verifyEvidenceBatch(
            UUID userId,
            List<EvidenceVersion> items,
            EvidenceVerificationStatus status) {
        if (items == null || items.isEmpty() || items.size() > 100
                || items.stream().map(EvidenceVersion::id).distinct().count() != items.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return items.stream()
                .map(item -> verifyEvidence(userId, item.id(), status, item.version()))
                .toList();
    }

    private EvidenceRecord withExperienceLink(
            EvidenceRecord evidence, EvidenceExperienceLink link) {
        return new EvidenceRecord(
                evidence.id(),
                evidence.userId(),
                evidence.sourceType(),
                evidence.sourceEntityId(),
                evidence.documentId(),
                link == null ? null : link.experienceItemId(),
                link == null ? null : link.relationKind(),
                link == null ? null : link.matchKind(),
                evidence.sourceDeletedAt(),
                evidence.evidenceCategory(),
                evidence.title(),
                evidence.content(),
                evidence.metadata(),
                evidence.confidence(),
                evidence.verificationStatus(),
                evidence.verifiedAt(),
                evidence.version(),
                evidence.createdAt(),
                evidence.updatedAt());
    }

    private EducationWrite education(EducationWrite input) {
        ProfilePolicy.validateDateRange(input.admissionDate(), input.graduationDate());
        ProfilePolicy.validateGpa(input.gpa(), input.gpaScale());
        return new EducationWrite(
                ProfilePolicy.requiredLabel(input.schoolName(), 200),
                ProfilePolicy.optionalLabel(input.major(), 200),
                ProfilePolicy.optionalLabel(input.degree(), 100),
                input.educationLevel(),
                input.educationStatus(),
                input.admissionDate(),
                input.graduationDate(),
                input.gpa(),
                input.gpaScale(),
                ProfilePolicy.optionalBody(input.description(), 5000));
    }

    private CertificationWrite certification(CertificationWrite input) {
        ProfilePolicy.validateDateRange(input.acquiredDate(), input.expiresAt());
        return new CertificationWrite(
                ProfilePolicy.requiredLabel(input.name(), 200),
                ProfilePolicy.optionalLabel(input.issuer(), 200),
                ProfilePolicy.optionalLabel(input.credentialNumber(), 200),
                input.acquiredDate(),
                input.expiresAt(),
                ProfilePolicy.optionalBody(input.description(), 5000),
                input.evidenceDocumentId());
    }

    private LanguageScoreWrite languageScore(LanguageScoreWrite input) {
        ProfilePolicy.validateDateRange(input.testedAt(), input.expiresAt());
        return new LanguageScoreWrite(
                ProfilePolicy.requiredLabel(input.testName(), 100),
                ProfilePolicy.requiredLabel(input.score(), 100),
                ProfilePolicy.optionalLabel(input.grade(), 100),
                input.testedAt(),
                input.expiresAt(),
                input.evidenceDocumentId());
    }

    private AwardWrite award(AwardWrite input) {
        return new AwardWrite(
                ProfilePolicy.requiredLabel(input.name(), 200),
                ProfilePolicy.optionalLabel(input.organizer(), 200),
                input.awardedAt(),
                ProfilePolicy.optionalBody(input.description(), 5000),
                input.evidenceDocumentId());
    }

    private CareerWrite career(CareerWrite input) {
        ProfilePolicy.validateCareer(input.startedAt(), input.endedAt(), input.current());
        return new CareerWrite(
                ProfilePolicy.requiredLabel(input.organization(), 200),
                ProfilePolicy.optionalLabel(input.position(), 200),
                ProfilePolicy.optionalLabel(input.employmentType(), 50),
                input.startedAt(),
                input.endedAt(),
                input.current(),
                ProfilePolicy.optionalBody(input.responsibilities(), 20000),
                ProfilePolicy.optionalBody(input.achievements(), 20000));
    }

    private ActivityWrite activity(ActivityWrite input) {
        ProfilePolicy.validateCareer(input.startedAt(), input.endedAt(), input.ongoing());
        if (input.activityType() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new ActivityWrite(
                ProfilePolicy.requiredLabel(input.title(), 200),
                input.activityType(),
                ProfilePolicy.requiredLabel(input.organizer(), 200),
                input.startedAt(),
                input.endedAt(),
                input.ongoing(),
                ProfilePolicy.optionalLabel(input.role(), 200),
                requiredBody(input.description(), 10000),
                ProfilePolicy.optionalBody(input.achievements(), 10000),
                optionalHttpUrl(input.relatedUrl()),
                input.useAsMaterial());
    }

    private void requireProfileLock(UUID userId) {
        if (!store.lockProfile(userId)) {
            throw notFound();
        }
    }

    private void recalculateFinalEducation(UUID userId, Instant now) {
        UUID finalEducationId = store.listActiveEducations(userId).stream()
                .max(FINAL_EDUCATION_ORDER)
                .map(EducationRecord::id)
                .orElse(null);
        store.replacePrimaryEducation(userId, finalEducationId, now);
    }

    private static int educationStatusRank(EducationStatus status) {
        return switch (status) {
            case WITHDRAWN -> 0;
            case LEAVE_OF_ABSENCE -> 10;
            case ENROLLED -> 20;
            case EXPECTED_GRADUATION -> 30;
            case GRADUATED -> 40;
        };
    }

    private void deleteSource(
            String table,
            UUID userId,
            UUID id,
            long version,
            EvidenceSourceType sourceType) {
        Instant now = Instant.now();
        if (!store.softDeleteSource(table, userId, id, version, now)) {
            throw versionConflict();
        }
        store.deleteDirectEvidence(userId, sourceType, id);
    }

    private DirectEvidenceData evidence(CertificationRecord value) {
        return DirectEvidenceFactory.certification(
                value.name(), value.issuer(), value.credentialNumber(), value.acquiredDate(),
                value.expiresAt(), value.description());
    }

    private DirectEvidenceData evidence(LanguageScoreRecord value) {
        return DirectEvidenceFactory.languageScore(
                value.testName(), value.score(), value.grade(), value.testedAt(), value.expiresAt());
    }

    private DirectEvidenceData evidence(AwardRecord value) {
        return DirectEvidenceFactory.award(
                value.name(), value.organizer(), value.awardedAt(), value.description());
    }

    private DirectEvidenceData evidence(CareerRecord value) {
        return DirectEvidenceFactory.career(
                value.organization(), value.position(), value.employmentType(), value.startedAt(),
                value.endedAt(), value.current(), value.responsibilities(), value.achievements());
    }

    private DirectEvidenceData evidence(ActivityRecord value) {
        return DirectEvidenceFactory.activity(
                value.title(), value.activityType(), value.organizer(), value.startedAt(), value.endedAt(),
                value.ongoing(), value.role(), value.description(), value.achievements(), value.relatedUrl());
    }

    private EvidenceVerificationStatus materialStatus(boolean useAsMaterial) {
        return useAsMaterial
                ? EvidenceVerificationStatus.VERIFIED
                : EvidenceVerificationStatus.REJECTED;
    }

    private void requireActiveDocument(UUID userId, UUID documentId) {
        if (documentId != null) documentQueryPort.snapshot(userId, documentId);
    }

    private String sort(String requested, Set<String> allowed, String defaultValue) {
        String value = requested == null || requested.isBlank() ? defaultValue : requested;
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }

    private String requiredContent(String value) {
        if (value == null || value.isBlank() || value.length() > 20000 || value.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value;
    }

    private String requiredBody(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength || value.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value.trim();
    }

    private String optionalHttpUrl(String value) {
        String normalized = ProfilePolicy.optionalBody(value, 1000);
        if (normalized == null) return null;
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception);
        }
    }

    private Map<String, Object> validMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.values().stream().anyMatch(value -> value != null
                && !(value instanceof String)
                && !(value instanceof Number)
                && !(value instanceof Boolean))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            if (objectMapper.writeValueAsString(metadata).getBytes(StandardCharsets.UTF_8).length > 16384) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private void requireEditable(EvidenceRecord evidence) {
        if (evidence.verificationStatus() == EvidenceVerificationStatus.SOURCE_DELETED) {
            throw new BusinessException(ErrorCode.EVIDENCE_SOURCE_DELETED);
        }
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw versionConflict();
        }
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", "version", "reason", "STALE"),
                null);
    }

    private BusinessException stateConflict(Throwable cause) {
        return new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT, cause);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
