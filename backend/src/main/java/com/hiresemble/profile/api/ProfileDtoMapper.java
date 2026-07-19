package com.hiresemble.profile.api;

import com.hiresemble.profile.api.ProfileDtos.AwardDto;
import com.hiresemble.profile.api.ProfileDtos.CareerDto;
import com.hiresemble.profile.api.ProfileDtos.CertificationDto;
import com.hiresemble.profile.api.ProfileDtos.EducationDto;
import com.hiresemble.profile.api.ProfileDtos.EvidenceDto;
import com.hiresemble.profile.api.ProfileDtos.LanguageScoreDto;
import com.hiresemble.profile.api.ProfileDtos.ProfileDto;
import com.hiresemble.profile.domain.ProfileRecords.AwardRecord;
import com.hiresemble.profile.domain.ProfileRecords.CareerRecord;
import com.hiresemble.profile.domain.ProfileRecords.CertificationRecord;
import com.hiresemble.profile.domain.ProfileRecords.EducationRecord;
import com.hiresemble.profile.domain.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.domain.ProfileRecords.LanguageScoreRecord;
import com.hiresemble.profile.domain.ProfileRecords.PageSlice;
import com.hiresemble.profile.domain.ProfileRecords.ProfileView;
import java.util.function.Function;

final class ProfileDtoMapper {

    private ProfileDtoMapper() {}

    static ProfileDto profile(ProfileView view) {
        var value = view.profile();
        return new ProfileDto(
                value.legalName(),
                value.introduction(),
                value.desiredRoles(),
                value.desiredIndustries(),
                value.desiredLocations(),
                value.expectedGraduationDate(),
                view.completion().completed(),
                view.completion().missingItems(),
                value.version(),
                value.createdAt(),
                value.updatedAt());
    }

    static EducationDto education(EducationRecord value) {
        return new EducationDto(
                value.id(), value.schoolName(), value.major(), value.degree(), value.educationStatus(),
                value.admissionDate(), value.graduationDate(), value.gpa(), value.gpaScale(),
                value.primary(), value.description(), value.version(), value.createdAt(), value.updatedAt());
    }

    static CertificationDto certification(CertificationRecord value) {
        return new CertificationDto(
                value.id(), value.name(), value.issuer(), value.credentialNumber(), value.acquiredDate(),
                value.expiresAt(), value.description(), value.evidenceDocumentId(), value.version(),
                value.createdAt(), value.updatedAt());
    }

    static LanguageScoreDto languageScore(LanguageScoreRecord value) {
        return new LanguageScoreDto(
                value.id(), value.testName(), value.score(), value.grade(), value.testedAt(),
                value.expiresAt(), value.evidenceDocumentId(), value.version(), value.createdAt(), value.updatedAt());
    }

    static AwardDto award(AwardRecord value) {
        return new AwardDto(
                value.id(), value.name(), value.organizer(), value.awardedAt(), value.description(),
                value.evidenceDocumentId(), value.version(), value.createdAt(), value.updatedAt());
    }

    static CareerDto career(CareerRecord value) {
        return new CareerDto(
                value.id(), value.organization(), value.position(), value.employmentType(), value.startedAt(),
                value.endedAt(), value.current(), value.responsibilities(), value.achievements(), value.version(),
                value.createdAt(), value.updatedAt());
    }

    static EvidenceDto evidence(EvidenceRecord value) {
        return new EvidenceDto(
                value.id(), value.sourceType(), value.sourceEntityId(), value.documentId(), value.sourceDeletedAt(),
                value.evidenceCategory(), value.title(), value.content(), value.metadata(), value.confidence(),
                value.verificationStatus(), value.verifiedAt(), value.version(), value.createdAt(), value.updatedAt());
    }

    static <S, T> PageResponse<T> page(PageSlice<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
