package com.hiresemble.job.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisEducation;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisEligibility;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisProfileSnapshot;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EmploymentDisqualificationStatus;
import com.hiresemble.profile.domain.model.MilitaryStatus;
import com.hiresemble.profile.domain.model.OverseasTravelEligibility;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobAnalysisHashingTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID EDUCATION_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID ELIGIBILITY_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Test
    void structuredEducationAndEligibilityChangesInvalidateProfileHash() {
        AnalysisProfileSnapshot baseline = snapshot(
                new AnalysisEducation(
                        EDUCATION_ID, 1, EducationLevel.BACHELOR,
                        EducationStatus.EXPECTED_GRADUATION, "학사", "컴퓨터공학",
                        LocalDate.parse("2026-08-25"), true),
                new AnalysisEligibility(
                        ELIGIBILITY_ID, 1, LocalDate.parse("2026-08-01"),
                        MilitaryStatus.COMPLETED, OverseasTravelEligibility.ELIGIBLE,
                        EmploymentDisqualificationStatus.NONE_DECLARED));
        String baselineHash = JobAnalysisHashing.profileHash(USER_ID, baseline);

        assertThat(JobAnalysisHashing.profileHash(USER_ID, snapshot(
                        new AnalysisEducation(
                                EDUCATION_ID, 2, EducationLevel.MASTER,
                                EducationStatus.GRADUATED, "석사", "컴퓨터공학",
                                LocalDate.parse("2026-08-26"), true),
                        baseline.eligibility())))
                .isNotEqualTo(baselineHash);
        assertThat(JobAnalysisHashing.profileHash(USER_ID, snapshot(
                        baseline.primaryEducation(),
                        new AnalysisEligibility(
                                ELIGIBILITY_ID, 2, LocalDate.parse("2026-09-01"),
                                MilitaryStatus.NOT_COMPLETED,
                                OverseasTravelEligibility.RESTRICTED,
                                EmploymentDisqualificationStatus.HAS_RESTRICTION))))
                .isNotEqualTo(baselineHash);
    }

    @Test
    void structuredFactHashSeparatesOwnerAndSourceVersion() {
        String baseline = JobAnalysisHashing.structuredFactHash(
                USER_ID,
                StructuredProfileFactType.PRIMARY_EDUCATION,
                EDUCATION_ID,
                1,
                "educationLevel=BACHELOR");

        assertThat(JobAnalysisHashing.structuredFactHash(
                        UUID.randomUUID(),
                        StructuredProfileFactType.PRIMARY_EDUCATION,
                        EDUCATION_ID,
                        1,
                        "educationLevel=BACHELOR"))
                .isNotEqualTo(baseline);
        assertThat(JobAnalysisHashing.structuredFactHash(
                        USER_ID,
                        StructuredProfileFactType.PRIMARY_EDUCATION,
                        EDUCATION_ID,
                        2,
                        "educationLevel=BACHELOR"))
                .isNotEqualTo(baseline);
    }

    private AnalysisProfileSnapshot snapshot(
            AnalysisEducation education, AnalysisEligibility eligibility) {
        return new AnalysisProfileSnapshot(
                PROFILE_ID,
                3,
                "소개",
                List.of("개발자"),
                List.of("IT"),
                List.of("서울"),
                LocalDate.parse("2026-08-25"),
                education,
                eligibility,
                List.of());
    }
}
