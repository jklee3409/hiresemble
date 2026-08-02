package com.hiresemble.profile.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiresemble.profile.application.service.ProfileAnalysisQueryService;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EmploymentDisqualificationStatus;
import com.hiresemble.profile.domain.model.MilitaryStatus;
import com.hiresemble.profile.domain.model.OverseasTravelEligibility;
import com.hiresemble.profile.domain.model.ProfileRecords.EducationRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.ProfileEligibilityRecord;
import com.hiresemble.profile.domain.model.ProfileRecords.ProfileRecord;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileAnalysisQueryServiceTest {

    @Test
    void loadsOnlyOwnerPrimaryEducationAndEligibilityDeclaration() {
        ProfileStore store = mock(ProfileStore.class);
        UUID ownerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID educationId = UUID.randomUUID();
        UUID declarationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-02T00:00:00Z");
        when(store.findProfile(ownerId)).thenReturn(Optional.of(new ProfileRecord(
                profileId, ownerId, "지원자", "소개", List.of("개발자"), List.of("IT"),
                List.of("서울"), LocalDate.parse("2026-08-25"), 3L, now, now)));
        when(store.listActiveEducations(ownerId)).thenReturn(List.of(new EducationRecord(
                educationId, ownerId, "대학교", "컴퓨터공학", "학사",
                EducationLevel.BACHELOR, EducationStatus.EXPECTED_GRADUATION,
                LocalDate.parse("2022-03-01"), LocalDate.parse("2026-08-25"),
                null, null, true, null, 2L, now, now)));
        when(store.findEligibility(ownerId)).thenReturn(Optional.of(new ProfileEligibilityRecord(
                declarationId, ownerId, LocalDate.parse("2026-08-01"),
                MilitaryStatus.COMPLETED, OverseasTravelEligibility.ELIGIBLE,
                EmploymentDisqualificationStatus.NONE_DECLARED, 1L, now, now)));
        when(store.findVerifiedEvidenceForAnalysis(ownerId)).thenReturn(List.of());

        var snapshot = new ProfileAnalysisQueryService(store).loadAnalysisSnapshot(ownerId);

        assertThat(snapshot.profileId()).isEqualTo(profileId);
        assertThat(snapshot.primaryEducation().id()).isEqualTo(educationId);
        assertThat(snapshot.primaryEducation().educationLevel()).isEqualTo(EducationLevel.BACHELOR);
        assertThat(snapshot.eligibility().id()).isEqualTo(declarationId);
        assertThat(snapshot.eligibility().workAvailableDate())
                .isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(snapshot.verifiedEvidence()).isEmpty();
        verify(store).findProfile(ownerId);
        verify(store).listActiveEducations(ownerId);
        verify(store).findEligibility(ownerId);
        verify(store).findVerifiedEvidenceForAnalysis(ownerId);
    }
}
