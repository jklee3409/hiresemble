package com.hiresemble.profile.infrastructure.persistence;

import com.hiresemble.profile.domain.model.DirectEvidenceData;
import com.hiresemble.profile.domain.model.ActivityType;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.EmploymentDisqualificationStatus;
import com.hiresemble.profile.domain.model.MilitaryStatus;
import com.hiresemble.profile.domain.model.OverseasTravelEligibility;
import com.hiresemble.profile.domain.model.ProfileCommands.AwardWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.ActivityWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.CareerWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.CertificationWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.EducationWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.EvidenceWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.LanguageScoreWrite;
import com.hiresemble.profile.domain.model.ProfileCommands.ProfileUpdate;
import com.hiresemble.profile.domain.model.ProfileCommands.ProfileEligibilityUpdate;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ProfileStore {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ProfileStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void createDefaultProfile(UUID userId, Instant now) {
        jdbcClient
                .sql("""
                        INSERT INTO user_profiles (
                            id, user_id, legal_name, introduction,
                            desired_roles, desired_industries, desired_locations,
                            expected_graduation_date, version, created_at, updated_at
                        ) VALUES (
                            :id, :userId, NULL, NULL,
                            '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,
                            NULL, 0, :now, :now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("now", utc(now))
                .update();
        jdbcClient
                .sql("""
                        INSERT INTO profile_eligibility_declarations (
                            id,user_id,work_available_date,military_status,
                            overseas_travel_eligibility,employment_disqualification_status,
                            version,created_at,updated_at
                        ) VALUES (
                            :id,:userId,NULL,'UNSPECIFIED','UNSPECIFIED','UNSPECIFIED',0,:now,:now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("now", utc(now))
                .update();
    }

    public Optional<ProfileEligibilityRecord> findEligibility(UUID userId) {
        return jdbcClient
                .sql("""
                        SELECT * FROM profile_eligibility_declarations
                        WHERE user_id=:userId
                        """)
                .param("userId", userId)
                .query(this::eligibility)
                .optional();
    }

    public Optional<ProfileEligibilityRecord> updateEligibility(
            UUID userId, ProfileEligibilityUpdate command, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE profile_eligibility_declarations
                        SET work_available_date=:workAvailableDate,
                            military_status=:militaryStatus,
                            overseas_travel_eligibility=:overseasTravelEligibility,
                            employment_disqualification_status=:employmentDisqualificationStatus,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND version=:version
                        RETURNING *
                        """)
                .param("workAvailableDate", command.workAvailableDate())
                .param("militaryStatus", command.militaryStatus().name())
                .param("overseasTravelEligibility", command.overseasTravelEligibility().name())
                .param("employmentDisqualificationStatus", command.employmentDisqualificationStatus().name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("version", command.version())
                .query(this::eligibility)
                .optional();
    }

    public Optional<ProfileRecord> findProfile(UUID userId) {
        return jdbcClient
                .sql("""
                        SELECT id, user_id, legal_name, introduction,
                               desired_roles::text AS desired_roles,
                               desired_industries::text AS desired_industries,
                               desired_locations::text AS desired_locations,
                               expected_graduation_date, version, created_at, updated_at
                        FROM user_profiles
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(this::profile)
                .optional();
    }

    public Optional<ProfileRecord> updateProfile(UUID userId, ProfileUpdate command, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE user_profiles
                        SET legal_name = :legalName,
                            introduction = :introduction,
                            desired_roles = CAST(:desiredRoles AS jsonb),
                            desired_industries = CAST(:desiredIndustries AS jsonb),
                            desired_locations = CAST(:desiredLocations AS jsonb),
                            expected_graduation_date = :expectedGraduationDate,
                            version = version + 1,
                            updated_at = :now
                        WHERE user_id = :userId AND version = :version
                        RETURNING id, user_id, legal_name, introduction,
                                  desired_roles::text AS desired_roles,
                                  desired_industries::text AS desired_industries,
                                  desired_locations::text AS desired_locations,
                                  expected_graduation_date, version, created_at, updated_at
                        """)
                .param("legalName", command.legalName())
                .param("introduction", command.introduction())
                .param("desiredRoles", json(command.desiredRoles()))
                .param("desiredIndustries", json(command.desiredIndustries()))
                .param("desiredLocations", json(command.desiredLocations()))
                .param("expectedGraduationDate", command.expectedGraduationDate())
                .param("now", utc(now))
                .param("userId", userId)
                .param("version", command.version())
                .query(this::profile)
                .optional();
    }

    public boolean hasPrimaryEducation(UUID userId) {
        return Boolean.TRUE.equals(jdbcClient
                .sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM educations
                            WHERE user_id = :userId AND is_primary AND deleted_at IS NULL
                        )
                        """)
                .param("userId", userId)
                .query(Boolean.class)
                .single());
    }

    public boolean lockProfile(UUID userId) {
        return jdbcClient
                .sql("SELECT 1 FROM user_profiles WHERE user_id = :userId FOR UPDATE")
                .param("userId", userId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public List<EducationRecord> listActiveEducations(UUID userId) {
        return jdbcClient
                .sql("""
                        SELECT * FROM educations
                        WHERE user_id = :userId AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .query(this::education)
                .list();
    }

    public void replacePrimaryEducation(UUID userId, UUID educationId, Instant now) {
        UUID retainedId = educationId == null ? new UUID(0L, 0L) : educationId;
        jdbcClient
                .sql("""
                        UPDATE educations
                        SET is_primary = false, version = version + 1, updated_at = :now
                        WHERE user_id = :userId
                          AND is_primary
                          AND deleted_at IS NULL
                          AND id <> :retainedId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("retainedId", retainedId)
                .update();
        if (educationId == null) {
            return;
        }
        jdbcClient
                .sql("""
                        UPDATE educations
                        SET is_primary = true, version = version + 1, updated_at = :now
                        WHERE user_id = :userId
                          AND id = :educationId
                          AND NOT is_primary
                          AND deleted_at IS NULL
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("educationId", educationId)
                .update();
    }

    public EducationRecord createEducation(
            UUID id, UUID userId, EducationWrite command, Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO educations (
                            id, user_id, school_name, major, degree, education_level, education_status,
                            admission_date, graduation_date, gpa, gpa_scale, is_primary,
                            description, version, created_at, updated_at, deleted_at
                        ) VALUES (
                            :id, :userId, :schoolName, :major, :degree, :educationLevel, :educationStatus,
                            :admissionDate, :graduationDate, :gpa, :gpaScale, false,
                            :description, 0, :now, :now, NULL
                        )
                        RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("schoolName", command.schoolName())
                .param("major", command.major())
                .param("degree", command.degree())
                .param("educationLevel", command.educationLevel().name())
                .param("educationStatus", command.educationStatus().name())
                .param("admissionDate", command.admissionDate())
                .param("graduationDate", command.graduationDate())
                .param("gpa", command.gpa())
                .param("gpaScale", command.gpaScale())
                .param("description", command.description())
                .param("now", utc(now))
                .query(this::education)
                .single();
    }

    public Optional<EducationRecord> updateEducation(
            UUID userId, UUID id, EducationWrite command, long version, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE educations
                        SET school_name = :schoolName,
                            major = :major,
                            degree = :degree,
                            education_level = :educationLevel,
                            education_status = :educationStatus,
                            admission_date = :admissionDate,
                            graduation_date = :graduationDate,
                            gpa = :gpa,
                            gpa_scale = :gpaScale,
                            description = :description,
                            version = version + 1,
                            updated_at = :now
                        WHERE user_id = :userId AND id = :id AND version = :version AND deleted_at IS NULL
                        RETURNING *
                        """)
                .param("schoolName", command.schoolName())
                .param("major", command.major())
                .param("degree", command.degree())
                .param("educationLevel", command.educationLevel().name())
                .param("educationStatus", command.educationStatus().name())
                .param("admissionDate", command.admissionDate())
                .param("graduationDate", command.graduationDate())
                .param("gpa", command.gpa())
                .param("gpaScale", command.gpaScale())
                .param("description", command.description())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::education)
                .optional();
    }

    public Optional<EducationRecord> findEducation(UUID userId, UUID id) {
        return jdbcClient
                .sql("SELECT * FROM educations WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", id)
                .query(this::education)
                .optional();
    }

    public PageSlice<EducationRecord> listEducations(
            UUID userId, int page, int size, String sort) {
        String order = switch (sort) {
            case "createdAt,desc" -> "created_at DESC, id DESC";
            case "graduationDate,desc" -> "graduation_date DESC NULLS LAST, id DESC";
            default -> throw new IllegalArgumentException("unsupported education sort");
        };
        List<EducationRecord> items = jdbcClient
                .sql("""
                        SELECT * FROM educations
                        WHERE user_id=:userId AND deleted_at IS NULL
                        ORDER BY %s LIMIT :size OFFSET :offset
                        """.formatted(order))
                .param("userId", userId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::education)
                .list();
        return page(items, page, size, countActive("educations", userId));
    }

    public CertificationRecord createCertification(
            UUID id, UUID userId, CertificationWrite command, Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO certifications (
                            id,user_id,name,issuer,credential_number,acquired_date,expires_at,
                            description,evidence_document_id,version,created_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:name,:issuer,:credentialNumber,:acquiredDate,:expiresAt,
                            :description,:evidenceDocumentId,0,:now,:now,NULL
                        ) RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("name", command.name())
                .param("issuer", command.issuer())
                .param("credentialNumber", command.credentialNumber())
                .param("acquiredDate", command.acquiredDate())
                .param("expiresAt", command.expiresAt())
                .param("description", command.description())
                .param("evidenceDocumentId", command.evidenceDocumentId())
                .param("now", utc(now))
                .query(this::certification)
                .single();
    }

    public Optional<CertificationRecord> updateCertification(
            UUID userId, UUID id, CertificationWrite command, long version, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE certifications SET
                            name=:name,issuer=:issuer,credential_number=:credentialNumber,
                            acquired_date=:acquiredDate,expires_at=:expiresAt,description=:description,
                            evidence_document_id=:evidenceDocumentId,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version AND deleted_at IS NULL
                        RETURNING *
                        """)
                .param("name", command.name())
                .param("issuer", command.issuer())
                .param("credentialNumber", command.credentialNumber())
                .param("acquiredDate", command.acquiredDate())
                .param("expiresAt", command.expiresAt())
                .param("description", command.description())
                .param("evidenceDocumentId", command.evidenceDocumentId())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::certification)
                .optional();
    }

    public Optional<CertificationRecord> findCertification(UUID userId, UUID id) {
        return jdbcClient
                .sql("SELECT * FROM certifications WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", id)
                .query(this::certification)
                .optional();
    }

    public PageSlice<CertificationRecord> listCertifications(
            UUID userId, int page, int size, String sort) {
        String order = switch (sort) {
            case "acquiredDate,desc" -> "acquired_date DESC NULLS LAST, id DESC";
            case "createdAt,desc" -> "created_at DESC, id DESC";
            default -> throw new IllegalArgumentException("unsupported certification sort");
        };
        List<CertificationRecord> items = jdbcClient
                .sql("SELECT * FROM certifications WHERE user_id=:userId AND deleted_at IS NULL ORDER BY %s LIMIT :size OFFSET :offset".formatted(order))
                .param("userId", userId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::certification)
                .list();
        return page(items, page, size, countActive("certifications", userId));
    }

    public LanguageScoreRecord createLanguageScore(
            UUID id, UUID userId, LanguageScoreWrite command, Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO language_scores (
                            id,user_id,test_name,score,grade,tested_at,expires_at,evidence_document_id,
                            version,created_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:testName,:score,:grade,:testedAt,:expiresAt,:evidenceDocumentId,
                            0,:now,:now,NULL
                        ) RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("testName", command.testName())
                .param("score", command.score())
                .param("grade", command.grade())
                .param("testedAt", command.testedAt())
                .param("expiresAt", command.expiresAt())
                .param("evidenceDocumentId", command.evidenceDocumentId())
                .param("now", utc(now))
                .query(this::languageScore)
                .single();
    }

    public Optional<LanguageScoreRecord> updateLanguageScore(
            UUID userId, UUID id, LanguageScoreWrite command, long version, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE language_scores SET
                            test_name=:testName,score=:score,grade=:grade,tested_at=:testedAt,
                            expires_at=:expiresAt,evidence_document_id=:evidenceDocumentId,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version AND deleted_at IS NULL
                        RETURNING *
                        """)
                .param("testName", command.testName())
                .param("score", command.score())
                .param("grade", command.grade())
                .param("testedAt", command.testedAt())
                .param("expiresAt", command.expiresAt())
                .param("evidenceDocumentId", command.evidenceDocumentId())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::languageScore)
                .optional();
    }

    public Optional<LanguageScoreRecord> findLanguageScore(UUID userId, UUID id) {
        return jdbcClient
                .sql("SELECT * FROM language_scores WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", id)
                .query(this::languageScore)
                .optional();
    }

    public PageSlice<LanguageScoreRecord> listLanguageScores(
            UUID userId, int page, int size, String sort) {
        String order = switch (sort) {
            case "testedAt,desc" -> "tested_at DESC NULLS LAST, id DESC";
            case "createdAt,desc" -> "created_at DESC, id DESC";
            default -> throw new IllegalArgumentException("unsupported language score sort");
        };
        List<LanguageScoreRecord> items = jdbcClient
                .sql("SELECT * FROM language_scores WHERE user_id=:userId AND deleted_at IS NULL ORDER BY %s LIMIT :size OFFSET :offset".formatted(order))
                .param("userId", userId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::languageScore)
                .list();
        return page(items, page, size, countActive("language_scores", userId));
    }

    public AwardRecord createAward(UUID id, UUID userId, AwardWrite command, Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO awards (
                            id,user_id,name,organizer,awarded_at,description,evidence_document_id,
                            version,created_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:name,:organizer,:awardedAt,:description,:evidenceDocumentId,
                            0,:now,:now,NULL
                        ) RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("name", command.name())
                .param("organizer", command.organizer())
                .param("awardedAt", command.awardedAt())
                .param("description", command.description())
                .param("evidenceDocumentId", command.evidenceDocumentId())
                .param("now", utc(now))
                .query(this::award)
                .single();
    }

    public Optional<AwardRecord> updateAward(
            UUID userId, UUID id, AwardWrite command, long version, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE awards SET
                            name=:name,organizer=:organizer,awarded_at=:awardedAt,
                            description=:description,evidence_document_id=:evidenceDocumentId,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version AND deleted_at IS NULL
                        RETURNING *
                        """)
                .param("name", command.name())
                .param("organizer", command.organizer())
                .param("awardedAt", command.awardedAt())
                .param("description", command.description())
                .param("evidenceDocumentId", command.evidenceDocumentId())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::award)
                .optional();
    }

    public Optional<AwardRecord> findAward(UUID userId, UUID id) {
        return jdbcClient
                .sql("SELECT * FROM awards WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", id)
                .query(this::award)
                .optional();
    }

    public PageSlice<AwardRecord> listAwards(UUID userId, int page, int size, String sort) {
        String order = switch (sort) {
            case "awardedAt,desc" -> "awarded_at DESC NULLS LAST, id DESC";
            case "createdAt,desc" -> "created_at DESC, id DESC";
            default -> throw new IllegalArgumentException("unsupported award sort");
        };
        List<AwardRecord> items = jdbcClient
                .sql("SELECT * FROM awards WHERE user_id=:userId AND deleted_at IS NULL ORDER BY %s LIMIT :size OFFSET :offset".formatted(order))
                .param("userId", userId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::award)
                .list();
        return page(items, page, size, countActive("awards", userId));
    }

    public CareerRecord createCareer(UUID id, UUID userId, CareerWrite command, Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO careers (
                            id,user_id,organization,position,employment_type,started_at,ended_at,is_current,
                            responsibilities,achievements,version,created_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:organization,:position,:employmentType,:startedAt,:endedAt,:current,
                            :responsibilities,:achievements,0,:now,:now,NULL
                        ) RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("organization", command.organization())
                .param("position", command.position())
                .param("employmentType", command.employmentType())
                .param("startedAt", command.startedAt())
                .param("endedAt", command.endedAt())
                .param("current", command.current())
                .param("responsibilities", command.responsibilities())
                .param("achievements", command.achievements())
                .param("now", utc(now))
                .query(this::career)
                .single();
    }

    public Optional<CareerRecord> updateCareer(
            UUID userId, UUID id, CareerWrite command, long version, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE careers SET
                            organization=:organization,position=:position,employment_type=:employmentType,
                            started_at=:startedAt,ended_at=:endedAt,is_current=:current,
                            responsibilities=:responsibilities,achievements=:achievements,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version AND deleted_at IS NULL
                        RETURNING *
                        """)
                .param("organization", command.organization())
                .param("position", command.position())
                .param("employmentType", command.employmentType())
                .param("startedAt", command.startedAt())
                .param("endedAt", command.endedAt())
                .param("current", command.current())
                .param("responsibilities", command.responsibilities())
                .param("achievements", command.achievements())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::career)
                .optional();
    }

    public Optional<CareerRecord> findCareer(UUID userId, UUID id) {
        return jdbcClient
                .sql("SELECT * FROM careers WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", id)
                .query(this::career)
                .optional();
    }

    public PageSlice<CareerRecord> listCareers(UUID userId, int page, int size, String sort) {
        String order = switch (sort) {
            case "startedAt,desc" -> "started_at DESC NULLS LAST, id DESC";
            case "createdAt,desc" -> "created_at DESC, id DESC";
            default -> throw new IllegalArgumentException("unsupported career sort");
        };
        List<CareerRecord> items = jdbcClient
                .sql("SELECT * FROM careers WHERE user_id=:userId AND deleted_at IS NULL ORDER BY %s LIMIT :size OFFSET :offset".formatted(order))
                .param("userId", userId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::career)
                .list();
        return page(items, page, size, countActive("careers", userId));
    }

    public ActivityRecord createActivity(
            UUID id, UUID userId, ActivityWrite command, Instant now) {
        return jdbcClient.sql("""
                        INSERT INTO activities (
                            id,user_id,title,activity_type,organizer,started_at,ended_at,ongoing,
                            role,description,achievements,related_url,use_as_material,
                            version,created_at,updated_at,deleted_at
                        ) VALUES (
                            :id,:userId,:title,:activityType,:organizer,:startedAt,:endedAt,:ongoing,
                            :role,:description,:achievements,:relatedUrl,:useAsMaterial,
                            0,:now,:now,NULL
                        ) RETURNING *
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("title", command.title())
                .param("activityType", command.activityType().name())
                .param("organizer", command.organizer())
                .param("startedAt", command.startedAt())
                .param("endedAt", command.endedAt())
                .param("ongoing", command.ongoing())
                .param("role", command.role())
                .param("description", command.description())
                .param("achievements", command.achievements())
                .param("relatedUrl", command.relatedUrl())
                .param("useAsMaterial", command.useAsMaterial())
                .param("now", utc(now))
                .query(this::activity)
                .single();
    }

    public Optional<ActivityRecord> updateActivity(
            UUID userId, UUID id, ActivityWrite command, long version, Instant now) {
        return jdbcClient.sql("""
                        UPDATE activities SET
                            title=:title,activity_type=:activityType,organizer=:organizer,
                            started_at=:startedAt,ended_at=:endedAt,ongoing=:ongoing,role=:role,
                            description=:description,achievements=:achievements,related_url=:relatedUrl,
                            use_as_material=:useAsMaterial,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version AND deleted_at IS NULL
                        RETURNING *
                        """)
                .param("title", command.title())
                .param("activityType", command.activityType().name())
                .param("organizer", command.organizer())
                .param("startedAt", command.startedAt())
                .param("endedAt", command.endedAt())
                .param("ongoing", command.ongoing())
                .param("role", command.role())
                .param("description", command.description())
                .param("achievements", command.achievements())
                .param("relatedUrl", command.relatedUrl())
                .param("useAsMaterial", command.useAsMaterial())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::activity)
                .optional();
    }

    public Optional<ActivityRecord> findActivity(UUID userId, UUID id) {
        return jdbcClient.sql("SELECT * FROM activities WHERE user_id=:userId AND id=:id AND deleted_at IS NULL")
                .param("userId", userId)
                .param("id", id)
                .query(this::activity)
                .optional();
    }

    public PageSlice<ActivityRecord> listActivities(UUID userId, int page, int size, String sort) {
        String order = switch (sort) {
            case "startedAt,desc" -> "started_at DESC NULLS LAST, created_at DESC, id DESC";
            case "createdAt,desc" -> "created_at DESC, id DESC";
            default -> throw new IllegalArgumentException("unsupported activity sort");
        };
        List<ActivityRecord> items = jdbcClient
                .sql("SELECT * FROM activities WHERE user_id=:userId AND deleted_at IS NULL ORDER BY %s LIMIT :size OFFSET :offset".formatted(order))
                .param("userId", userId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::activity)
                .list();
        return page(items, page, size, countActive("activities", userId));
    }

    public boolean softDeleteSource(
            String table, UUID userId, UUID id, long version, Instant now) {
        if (!List.of("educations", "certifications", "language_scores", "awards", "careers", "activities")
                .contains(table)) {
            throw new IllegalArgumentException("unsupported profile source table");
        }
        return jdbcClient
                        .sql("UPDATE %s SET deleted_at=:now, version=version+1, updated_at=:now WHERE user_id=:userId AND id=:id AND version=:version AND deleted_at IS NULL".formatted(table))
                        .param("now", utc(now))
                        .param("userId", userId)
                        .param("id", id)
                        .param("version", version)
                        .update()
                == 1;
    }

    public EvidenceRecord createDirectEvidence(
            UUID id, UUID userId, UUID sourceEntityId, DirectEvidenceData data, Instant now) {
        return createDirectEvidence(
                id, userId, sourceEntityId, data, EvidenceVerificationStatus.VERIFIED, now);
    }

    public EvidenceRecord createDirectEvidence(
            UUID id,
            UUID userId,
            UUID sourceEntityId,
            DirectEvidenceData data,
            EvidenceVerificationStatus status,
            Instant now) {
        return jdbcClient
                .sql("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,confidence,verification_status,verified_at,
                            source_deleted_at,version,created_at,updated_at
                        ) VALUES (
                            :id,:userId,:sourceType,:sourceEntityId,NULL,:category,
                            :title,:content,CAST(:metadata AS jsonb),NULL,:status,:verifiedAt,
                            NULL,0,:now,:now
                        ) RETURNING *, metadata::text AS metadata_text
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("sourceType", data.sourceType().name())
                .param("sourceEntityId", sourceEntityId)
                .param("category", data.evidenceCategory())
                .param("title", data.title())
                .param("content", data.content())
                .param("metadata", json(data.metadata()))
                .param("status", status.name())
                .param("verifiedAt", status == EvidenceVerificationStatus.VERIFIED ? utc(now) : null)
                .param("now", utc(now))
                .query(this::evidence)
                .single();
    }

    public EvidenceRecord synchronizeDirectEvidence(
            UUID userId, UUID sourceEntityId, DirectEvidenceData data, Instant now) {
        return synchronizeDirectEvidence(
                userId, sourceEntityId, data, EvidenceVerificationStatus.VERIFIED, now);
    }

    public EvidenceRecord synchronizeDirectEvidence(
            UUID userId,
            UUID sourceEntityId,
            DirectEvidenceData data,
            EvidenceVerificationStatus status,
            Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE profile_evidence
                        SET evidence_category=:category,title=:title,content=:content,
                            metadata=CAST(:metadata AS jsonb),confidence=NULL,
                            verification_status=:status,verified_at=:verifiedAt,source_deleted_at=NULL,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND source_type=:sourceType AND source_entity_id=:sourceEntityId
                        RETURNING *, metadata::text AS metadata_text
                        """)
                .param("category", data.evidenceCategory())
                .param("title", data.title())
                .param("content", data.content())
                .param("metadata", json(data.metadata()))
                .param("status", status.name())
                .param("verifiedAt", status == EvidenceVerificationStatus.VERIFIED ? utc(now) : null)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceType", data.sourceType().name())
                .param("sourceEntityId", sourceEntityId)
                .query(this::evidence)
                .single();
    }

    public void deleteDirectEvidence(UUID userId, EvidenceSourceType sourceType, UUID sourceEntityId) {
        int deleted = jdbcClient
                .sql("DELETE FROM profile_evidence WHERE user_id=:userId AND source_type=:sourceType AND source_entity_id=:sourceEntityId")
                .param("userId", userId)
                .param("sourceType", sourceType.name())
                .param("sourceEntityId", sourceEntityId)
                .update();
        if (deleted != 1) {
            throw new IllegalStateException("structured source direct evidence is missing");
        }
    }

    public Optional<EvidenceRecord> findEvidence(UUID userId, UUID id) {
        return jdbcClient
                .sql("""
                        SELECT *, metadata::text AS metadata_text
                        FROM profile_evidence
                        WHERE user_id=:userId
                          AND id=:id
                          AND source_type <> 'EDUCATION'
                          AND NOT (
                              upper(regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g'))
                                  IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
                                      'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
                              OR regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g')
                                  IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
                          )
                        """)
                .param("userId", userId)
                .param("id", id)
                .query(this::evidence)
                .optional();
    }

    public List<EvidenceRecord> findVerifiedEvidenceForAnalysis(UUID userId) {
        return jdbcClient
                .sql("""
                        SELECT *, metadata::text AS metadata_text
                        FROM profile_evidence
                        WHERE user_id=:userId
                          AND source_type <> 'EDUCATION'
                          AND NOT (
                              upper(regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g'))
                                  IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
                                      'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
                              OR regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g')
                                  IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
                          )
                          AND verification_status='VERIFIED'
                          AND source_deleted_at IS NULL
                          AND (
                              source_type <> 'DOCUMENT_CHUNK'
                              OR NOT EXISTS (
                                  SELECT 1 FROM experience_evidence_links experience_link
                                  WHERE experience_link.user_id=profile_evidence.user_id
                                    AND experience_link.profile_evidence_id=profile_evidence.id
                              )
                          )
                        ORDER BY id
                        """)
                .param("userId", userId)
                .query(this::evidence)
                .list();
    }

    public Optional<EvidenceRecord> updateEvidence(
            UUID userId, UUID id, EvidenceWrite command, long version, Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE profile_evidence
                        SET title=:title,content=:content,metadata=CAST(:metadata AS jsonb),
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version
                        RETURNING *, metadata::text AS metadata_text
                        """)
                .param("title", command.title())
                .param("content", command.content())
                .param("metadata", json(command.metadata()))
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::evidence)
                .optional();
    }

    public Optional<EvidenceRecord> verifyEvidence(
            UUID userId,
            UUID id,
            EvidenceVerificationStatus status,
            long version,
            Instant now) {
        return jdbcClient
                .sql("""
                        UPDATE profile_evidence
                        SET verification_status=:status,
                            verified_at=CASE WHEN :status='VERIFIED' THEN :now ELSE NULL END,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND version=:version
                        RETURNING *, metadata::text AS metadata_text
                        """)
                .param("status", status.name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", id)
                .param("version", version)
                .query(this::evidence)
                .optional();
    }

    public PageSlice<EvidenceRecord> listEvidence(
            UUID userId,
            EvidenceVerificationStatus status,
            String category,
            UUID documentId,
            int page,
            int size,
            String sort) {
        String order = switch (sort) {
            case "updatedAt,desc" -> "updated_at DESC, id DESC";
            case "confidence,desc" -> "confidence DESC NULLS LAST, id DESC";
            default -> throw new IllegalArgumentException("unsupported evidence sort");
        };
        String statusValue = status == null ? "" : status.name();
        String categoryValue = category == null ? "" : category;
        String documentValue = documentId == null ? "" : documentId.toString();
        String where = """
                user_id=:userId
                AND source_type <> 'EDUCATION'
                AND NOT (
                    upper(regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g'))
                        IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
                            'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
                    OR regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g')
                        IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
                )
                """
                + "AND (:status='' OR verification_status=:status) "
                + "AND (:category='' OR evidence_category=:category) "
                + "AND (:documentId='' OR (source_type='DOCUMENT_CHUNK' AND document_id=CAST(:documentId AS uuid))) "
                + "AND (:documentId<>'' OR source_type<>'DOCUMENT_CHUNK' OR NOT EXISTS ("
                + "SELECT 1 FROM experience_evidence_links experience_link "
                + "WHERE experience_link.user_id=profile_evidence.user_id "
                + "AND experience_link.profile_evidence_id=profile_evidence.id))";
        List<EvidenceRecord> items = jdbcClient
                .sql("SELECT *, metadata::text AS metadata_text FROM profile_evidence WHERE %s ORDER BY %s LIMIT :size OFFSET :offset".formatted(where, order))
                .param("userId", userId)
                .param("status", statusValue)
                .param("category", categoryValue)
                .param("documentId", documentValue)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::evidence)
                .list();
        long count = jdbcClient
                .sql("SELECT count(*) FROM profile_evidence WHERE " + where)
                .param("userId", userId)
                .param("status", statusValue)
                .param("category", categoryValue)
                .param("documentId", documentValue)
                .query(Long.class)
                .single();
        return page(items, page, size, count);
    }

    public EvidenceRecord createDocumentEvidence(
            UUID id,
            UUID userId,
            UUID documentId,
            UUID sourceChunkId,
            String category,
            String title,
            String content,
            Map<String, Object> metadata,
            java.math.BigDecimal confidence,
            Instant now) {
        return jdbcClient.sql("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,confidence,verification_status,verified_at,
                            source_deleted_at,version,created_at,updated_at
                        ) VALUES (
                            :id,:userId,'DOCUMENT_CHUNK',:sourceChunkId,:documentId,:category,
                            :title,:content,CAST(:metadata AS jsonb),:confidence,'PENDING',NULL,
                            NULL,0,:now,:now
                        ) RETURNING *,metadata::text AS metadata_text
                        """)
                .param("id", id).param("userId", userId).param("sourceChunkId", sourceChunkId)
                .param("documentId", documentId).param("category", category).param("title", title)
                .param("content", content).param("metadata", json(metadata)).param("confidence", confidence)
                .param("now", utc(now)).query(this::evidence).single();
    }

    public EvidenceRecord createExperienceEvidence(
            UUID id,
            UUID userId,
            UUID experienceItemId,
            String category,
            String title,
            String content,
            Map<String, Object> metadata,
            java.math.BigDecimal confidence,
            Instant now) {
        return jdbcClient.sql("""
                        INSERT INTO profile_evidence (
                            id,user_id,source_type,source_entity_id,document_id,evidence_category,
                            title,content,metadata,confidence,verification_status,verified_at,
                            source_deleted_at,version,created_at,updated_at
                        ) VALUES (
                            :id,:userId,'EXPERIENCE',:experienceItemId,NULL,:category,
                            :title,:content,CAST(:metadata AS jsonb),:confidence,'PENDING',NULL,
                            NULL,0,:now,:now
                        ) RETURNING *,metadata::text AS metadata_text
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("experienceItemId", experienceItemId)
                .param("category", category)
                .param("title", title)
                .param("content", content)
                .param("metadata", json(metadata))
                .param("confidence", confidence)
                .param("now", utc(now))
                .query(this::evidence)
                .single();
    }

    public void synchronizeExperienceEvidenceContent(
            UUID userId, UUID evidenceId, String title, String content, Instant now) {
        int updated = jdbcClient.sql("""
                        UPDATE profile_evidence
                        SET title=:title,content=:content,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:evidenceId AND source_type='EXPERIENCE'
                          AND source_deleted_at IS NULL
                        """)
                .param("title", title)
                .param("content", content)
                .param("now", utc(now))
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("canonical experience evidence could not be updated");
        }
    }

    public void synchronizeExperienceEvidenceVerification(
            UUID userId,
            UUID evidenceId,
            EvidenceVerificationStatus status,
            Instant now) {
        int updated = jdbcClient.sql("""
                        UPDATE profile_evidence
                        SET verification_status=:status,
                            verified_at=CASE WHEN :status='VERIFIED' THEN :now ELSE NULL END,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:evidenceId AND source_type='EXPERIENCE'
                          AND source_deleted_at IS NULL
                        """)
                .param("status", status.name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("canonical experience verification could not be updated");
        }
    }

    public void deleteExperienceEvidence(UUID userId, UUID evidenceId) {
        int deleted = jdbcClient.sql("""
                        DELETE FROM profile_evidence
                        WHERE user_id=:userId AND id=:evidenceId AND source_type='EXPERIENCE'
                        """)
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .update();
        if (deleted != 1) {
            throw new IllegalStateException("canonical experience evidence could not be deleted");
        }
    }

    public List<EvidenceRecord> findDocumentEvidence(UUID userId, UUID documentId) {
        return jdbcClient.sql("""
                        SELECT *,metadata::text AS metadata_text FROM profile_evidence
                        WHERE user_id=:userId AND source_type='DOCUMENT_CHUNK' AND document_id=:documentId
                        ORDER BY created_at,id
                        """)
                .param("userId", userId).param("documentId", documentId)
                .query(this::evidence).list();
    }

    public boolean documentChunkExists(
            UUID userId, UUID documentId, long sourceRevision, UUID chunkId) {
        return jdbcClient.sql("""
                        SELECT EXISTS(
                            SELECT 1 FROM document_chunks
                            WHERE user_id=:userId AND document_id=:documentId
                              AND source_revision=:revision AND id=:chunkId
                        )
                        """)
                .param("userId", userId).param("documentId", documentId)
                .param("revision", sourceRevision).param("chunkId", chunkId)
                .query(Boolean.class).single();
    }

    public String documentChunkContent(
            UUID userId, UUID documentId, long sourceRevision, UUID chunkId) {
        return jdbcClient.sql("""
                        SELECT content FROM document_chunks
                        WHERE user_id=:userId AND document_id=:documentId
                          AND source_revision=:revision AND id=:chunkId
                        """)
                .param("userId", userId).param("documentId", documentId)
                .param("revision", sourceRevision).param("chunkId", chunkId)
                .query(String.class).single();
    }

    public void deleteEvidence(UUID userId, UUID evidenceId) {
        jdbcClient.sql("DELETE FROM profile_evidence WHERE user_id=:userId AND id=:evidenceId")
                .param("userId", userId).param("evidenceId", evidenceId).update();
    }

    public void tombstoneEvidence(UUID userId, UUID evidenceId, Instant now) {
        jdbcClient.sql("""
                        UPDATE profile_evidence SET source_entity_id=NULL,document_id=NULL,
                            title='[삭제된 문서 근거]',content='[원본 문서가 삭제되었습니다.]',
                            metadata='{}'::jsonb,confidence=NULL,verification_status='SOURCE_DELETED',
                            verified_at=NULL,source_deleted_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:evidenceId
                        """)
                .param("now", utc(now)).param("userId", userId).param("evidenceId", evidenceId)
                .update();
    }

    private long countActive(String table, UUID userId) {
        if (!List.of("educations", "certifications", "language_scores", "awards", "careers", "activities")
                .contains(table)) {
            throw new IllegalArgumentException("unsupported profile source table");
        }
        return jdbcClient
                .sql("SELECT count(*) FROM %s WHERE user_id=:userId AND deleted_at IS NULL".formatted(table))
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private <T> PageSlice<T> page(List<T> items, int page, int size, long count) {
        int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new PageSlice<>(items, page, size, count, totalPages);
    }

    private ProfileRecord profile(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProfileRecord(
                uuid(resultSet, "id"),
                uuid(resultSet, "user_id"),
                resultSet.getString("legal_name"),
                resultSet.getString("introduction"),
                read(resultSet.getString("desired_roles"), STRING_LIST),
                read(resultSet.getString("desired_industries"), STRING_LIST),
                read(resultSet.getString("desired_locations"), STRING_LIST),
                resultSet.getObject("expected_graduation_date", java.time.LocalDate.class),
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private ProfileEligibilityRecord eligibility(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ProfileEligibilityRecord(
                uuid(resultSet, "id"),
                uuid(resultSet, "user_id"),
                resultSet.getObject("work_available_date", java.time.LocalDate.class),
                MilitaryStatus.valueOf(resultSet.getString("military_status")),
                OverseasTravelEligibility.valueOf(resultSet.getString("overseas_travel_eligibility")),
                EmploymentDisqualificationStatus.valueOf(
                        resultSet.getString("employment_disqualification_status")),
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private EducationRecord education(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EducationRecord(
                uuid(resultSet, "id"),
                uuid(resultSet, "user_id"),
                resultSet.getString("school_name"),
                resultSet.getString("major"),
                resultSet.getString("degree"),
                EducationLevel.valueOf(resultSet.getString("education_level")),
                EducationStatus.valueOf(resultSet.getString("education_status")),
                resultSet.getObject("admission_date", java.time.LocalDate.class),
                resultSet.getObject("graduation_date", java.time.LocalDate.class),
                resultSet.getBigDecimal("gpa"),
                resultSet.getBigDecimal("gpa_scale"),
                resultSet.getBoolean("is_primary"),
                resultSet.getString("description"),
                resultSet.getLong("version"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private CertificationRecord certification(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new CertificationRecord(
                uuid(resultSet, "id"), uuid(resultSet, "user_id"), resultSet.getString("name"),
                resultSet.getString("issuer"), resultSet.getString("credential_number"),
                resultSet.getObject("acquired_date", java.time.LocalDate.class),
                resultSet.getObject("expires_at", java.time.LocalDate.class),
                resultSet.getString("description"), uuidNullable(resultSet, "evidence_document_id"),
                resultSet.getLong("version"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private LanguageScoreRecord languageScore(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new LanguageScoreRecord(
                uuid(resultSet, "id"), uuid(resultSet, "user_id"), resultSet.getString("test_name"),
                resultSet.getString("score"), resultSet.getString("grade"),
                resultSet.getObject("tested_at", java.time.LocalDate.class),
                resultSet.getObject("expires_at", java.time.LocalDate.class),
                uuidNullable(resultSet, "evidence_document_id"), resultSet.getLong("version"),
                instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private AwardRecord award(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AwardRecord(
                uuid(resultSet, "id"), uuid(resultSet, "user_id"), resultSet.getString("name"),
                resultSet.getString("organizer"),
                resultSet.getObject("awarded_at", java.time.LocalDate.class),
                resultSet.getString("description"), uuidNullable(resultSet, "evidence_document_id"),
                resultSet.getLong("version"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private CareerRecord career(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CareerRecord(
                uuid(resultSet, "id"), uuid(resultSet, "user_id"), resultSet.getString("organization"),
                resultSet.getString("position"), resultSet.getString("employment_type"),
                resultSet.getObject("started_at", java.time.LocalDate.class),
                resultSet.getObject("ended_at", java.time.LocalDate.class), resultSet.getBoolean("is_current"),
                resultSet.getString("responsibilities"), resultSet.getString("achievements"),
                resultSet.getLong("version"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private ActivityRecord activity(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ActivityRecord(
                uuid(resultSet, "id"), uuid(resultSet, "user_id"), resultSet.getString("title"),
                ActivityType.valueOf(resultSet.getString("activity_type")),
                resultSet.getString("organizer"),
                resultSet.getObject("started_at", java.time.LocalDate.class),
                resultSet.getObject("ended_at", java.time.LocalDate.class),
                resultSet.getBoolean("ongoing"), resultSet.getString("role"),
                resultSet.getString("description"), resultSet.getString("achievements"),
                resultSet.getString("related_url"), resultSet.getBoolean("use_as_material"),
                resultSet.getLong("version"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private EvidenceRecord evidence(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EvidenceRecord(
                uuid(resultSet, "id"), uuid(resultSet, "user_id"),
                EvidenceSourceType.valueOf(resultSet.getString("source_type")),
                uuidNullable(resultSet, "source_entity_id"), uuidNullable(resultSet, "document_id"),
                null, null, null,
                instantNullable(resultSet, "source_deleted_at"), resultSet.getString("evidence_category"),
                resultSet.getString("title"), resultSet.getString("content"),
                read(resultSet.getString("metadata_text"), METADATA), resultSet.getBigDecimal("confidence"),
                EvidenceVerificationStatus.valueOf(resultSet.getString("verification_status")),
                instantNullable(resultSet, "verified_at"), resultSet.getLong("version"),
                instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private UUID uuidNullable(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant instantNullable(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("profile JSON value could not be serialized", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored profile JSON value is invalid", exception);
        }
    }
}
