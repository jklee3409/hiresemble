package com.hiresemble.profile.application;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.profile.domain.DirectEvidenceData;
import com.hiresemble.profile.domain.DirectEvidenceFactory;
import com.hiresemble.profile.domain.EvidenceSourceType;
import com.hiresemble.profile.domain.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.ProfileCommands.AwardWrite;
import com.hiresemble.profile.domain.ProfileCommands.CareerWrite;
import com.hiresemble.profile.domain.ProfileCommands.CertificationWrite;
import com.hiresemble.profile.domain.ProfileCommands.EducationWrite;
import com.hiresemble.profile.domain.ProfileCommands.EvidenceWrite;
import com.hiresemble.profile.domain.ProfileCommands.LanguageScoreWrite;
import com.hiresemble.profile.domain.ProfileCommands.ProfileUpdate;
import com.hiresemble.profile.domain.ProfileCompletion;
import com.hiresemble.profile.domain.ProfilePolicy;
import com.hiresemble.profile.domain.ProfileRecords.AwardRecord;
import com.hiresemble.profile.domain.ProfileRecords.CareerRecord;
import com.hiresemble.profile.domain.ProfileRecords.CertificationRecord;
import com.hiresemble.profile.domain.ProfileRecords.EducationRecord;
import com.hiresemble.profile.domain.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.domain.ProfileRecords.LanguageScoreRecord;
import com.hiresemble.profile.domain.ProfileRecords.PageSlice;
import com.hiresemble.profile.domain.ProfileRecords.ProfileRecord;
import com.hiresemble.profile.domain.ProfileRecords.ProfileView;
import com.hiresemble.profile.infrastructure.ProfileStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
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

    private static final Set<String> EDUCATION_SORTS =
            Set.of("createdAt,desc", "graduationDate,desc");
    private static final Set<String> CERTIFICATION_SORTS =
            Set.of("acquiredDate,desc", "createdAt,desc");
    private static final Set<String> LANGUAGE_SORTS = Set.of("testedAt,desc", "createdAt,desc");
    private static final Set<String> AWARD_SORTS = Set.of("awardedAt,desc", "createdAt,desc");
    private static final Set<String> CAREER_SORTS = Set.of("startedAt,desc", "createdAt,desc");
    private static final Set<String> EVIDENCE_SORTS =
            Set.of("updatedAt,desc", "confidence,desc");

    private final ProfileStore store;
    private final ObjectMapper objectMapper;

    public ProfileApplicationService(ProfileStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
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
            if (command.primary()) {
                demoteAndSynchronize(userId, null, now);
            }
            EducationRecord created = store.createEducation(id, userId, command, now);
            store.createDirectEvidence(
                    UUID.randomUUID(), userId, id, evidence(created), now);
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
    }

    @Transactional
    public EducationRecord updateEducation(
            UUID userId, UUID educationId, EducationWrite input, long version) {
        EducationWrite command = education(input);
        EducationRecord current = store.findEducation(userId, educationId).orElseThrow(this::notFound);
        requireVersion(current.version(), version);
        Instant now = Instant.now();
        try {
            if (command.primary()) {
                demoteAndSynchronize(userId, educationId, now);
            }
            EducationRecord updated = store.updateEducation(userId, educationId, command, version, now)
                    .orElseThrow(this::versionConflict);
            store.synchronizeDirectEvidence(userId, educationId, evidence(updated), now);
            return updated;
        } catch (DataIntegrityViolationException exception) {
            throw stateConflict(exception);
        }
    }

    @Transactional
    public void deleteEducation(UUID userId, UUID educationId, long version) {
        EducationRecord record = store.findEducation(userId, educationId).orElseThrow(this::notFound);
        requireVersion(record.version(), version);
        deleteSource("educations", userId, educationId, version, EvidenceSourceType.EDUCATION);
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
        rejectDeferredDocument(command.evidenceDocumentId());
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
        rejectDeferredDocument(command.evidenceDocumentId());
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
        rejectDeferredDocument(command.evidenceDocumentId());
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
        rejectDeferredDocument(command.evidenceDocumentId());
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
        rejectDeferredDocument(command.evidenceDocumentId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AwardRecord created = store.createAward(id, userId, command, now);
        store.createDirectEvidence(UUID.randomUUID(), userId, id, evidence(created), now);
        return created;
    }

    @Transactional
    public AwardRecord updateAward(UUID userId, UUID id, AwardWrite input, long version) {
        AwardWrite command = award(input);
        rejectDeferredDocument(command.evidenceDocumentId());
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
    public PageSlice<EvidenceRecord> listEvidence(
            UUID userId,
            EvidenceVerificationStatus status,
            String category,
            UUID documentId,
            int page,
            int size,
            String sort) {
        rejectDeferredDocument(documentId);
        String normalizedCategory = category == null ? null : ProfilePolicy.requiredLabel(category, 80);
        return store.listEvidence(
                userId,
                status,
                normalizedCategory,
                page,
                size,
                sort(sort, EVIDENCE_SORTS, "updatedAt,desc"));
    }

    @Transactional
    public EvidenceRecord updateEvidence(
            UUID userId, UUID id, EvidenceWrite input, long version) {
        EvidenceRecord current = store.findEvidence(userId, id).orElseThrow(this::notFound);
        requireEditable(current);
        requireVersion(current.version(), version);
        EvidenceWrite command = new EvidenceWrite(
                ProfilePolicy.requiredLabel(input.title(), 250),
                requiredContent(input.content()),
                validMetadata(input.metadata()));
        return store.updateEvidence(userId, id, command, version, Instant.now())
                .orElseThrow(this::versionConflict);
    }

    @Transactional
    public EvidenceRecord verifyEvidence(
            UUID userId,
            UUID id,
            EvidenceVerificationStatus status,
            long version) {
        if (status != EvidenceVerificationStatus.VERIFIED
                && status != EvidenceVerificationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        EvidenceRecord current = store.findEvidence(userId, id).orElseThrow(this::notFound);
        requireEditable(current);
        requireVersion(current.version(), version);
        return store.verifyEvidence(userId, id, status, version, Instant.now())
                .orElseThrow(this::versionConflict);
    }

    private EducationWrite education(EducationWrite input) {
        ProfilePolicy.validateDateRange(input.admissionDate(), input.graduationDate());
        ProfilePolicy.validateGpa(input.gpa(), input.gpaScale());
        return new EducationWrite(
                ProfilePolicy.requiredLabel(input.schoolName(), 200),
                ProfilePolicy.optionalLabel(input.major(), 200),
                ProfilePolicy.optionalLabel(input.degree(), 100),
                input.educationStatus(),
                input.admissionDate(),
                input.graduationDate(),
                input.gpa(),
                input.gpaScale(),
                input.primary(),
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

    private void demoteAndSynchronize(UUID userId, UUID retainedEducationId, Instant now) {
        for (EducationRecord demoted : store.demoteOtherPrimary(userId, retainedEducationId, now)) {
            store.synchronizeDirectEvidence(userId, demoted.id(), evidence(demoted), now);
        }
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

    private DirectEvidenceData evidence(EducationRecord value) {
        return DirectEvidenceFactory.education(
                value.schoolName(), value.major(), value.degree(), value.educationStatus(),
                value.admissionDate(), value.graduationDate(), value.gpa(), value.gpaScale(),
                value.primary(), value.description());
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

    private void rejectDeferredDocument(UUID documentId) {
        if (documentId != null) {
            throw notFound();
        }
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
