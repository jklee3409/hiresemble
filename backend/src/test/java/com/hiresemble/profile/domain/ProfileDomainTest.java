package com.hiresemble.profile.domain;

import com.hiresemble.profile.domain.model.DirectEvidenceData;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.ProfileCompletion;
import com.hiresemble.profile.domain.model.ProfileCompletionItem;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import com.hiresemble.profile.domain.service.DirectEvidenceFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileDomainTest {

    @Test
    void completionUsesExactlyFiveTwentyPercentItems() {
        ProfileCompletion empty = ProfileCompletion.calculate(null, List.of(), List.of(), List.of(), false);
        assertThat(empty.completed()).isFalse();
        assertThat(empty.completionPercent()).isZero();
        assertThat(empty.missingItems()).containsExactly(
                ProfileCompletionItem.LEGAL_NAME,
                ProfileCompletionItem.DESIRED_ROLE,
                ProfileCompletionItem.DESIRED_INDUSTRY,
                ProfileCompletionItem.DESIRED_LOCATION,
                ProfileCompletionItem.PRIMARY_EDUCATION);

        ProfileCompletion partial = ProfileCompletion.calculate(
                "Candidate", List.of("Backend"), List.of(), List.of("Seoul"), false);
        assertThat(partial.completed()).isFalse();
        assertThat(partial.completionPercent()).isEqualTo(60);
        assertThat(partial.missingItems()).containsExactly(
                ProfileCompletionItem.DESIRED_INDUSTRY,
                ProfileCompletionItem.PRIMARY_EDUCATION);

        ProfileCompletion complete = ProfileCompletion.calculate(
                "Candidate", List.of("Backend"), List.of("Software"), List.of("Seoul"), true);
        assertThat(complete.completed()).isTrue();
        assertThat(complete.completionPercent()).isEqualTo(100);
        assertThat(complete.missingItems()).isEmpty();
    }

    @Test
    void canonicalArraysTrimValuesAndRejectCanonicalDuplicates() {
        assertThat(ProfilePolicy.canonicalArray(List.of(" Backend ", "Data")))
                .containsExactly("Backend", "Data");
        assertThatThrownBy(() -> ProfilePolicy.canonicalArray(List.of("Backend", " backend ")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ProfilePolicy.canonicalArray(List.of("\t")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void educationDateAndGpaRulesRejectInvalidCombinations() {
        LocalDate earlier = LocalDate.of(2024, 1, 1);
        LocalDate later = LocalDate.of(2025, 1, 1);

        ProfilePolicy.validateDateRange(earlier, later);
        ProfilePolicy.validateGpa(new BigDecimal("4.0"), new BigDecimal("4.5"));

        assertThatThrownBy(() -> ProfilePolicy.validateDateRange(later, earlier))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ProfilePolicy.validateGpa(BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ProfilePolicy.validateGpa(new BigDecimal("4.6"), new BigDecimal("4.5")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ProfilePolicy.validateGpa(new BigDecimal("10.01"), BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void certificationLanguageAndCurrentCareerShareOrderedDateRules() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        ProfilePolicy.validateDateRange(start, end);
        ProfilePolicy.validateCareer(start, null, true);
        assertThatThrownBy(() -> ProfilePolicy.validateDateRange(end, start))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ProfilePolicy.validateCareer(start, end, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void directEvidenceFactoryMapsEveryStructuredSourceAndBoundsGeneratedContent() {
        assertThat(DirectEvidenceFactory.education(
                                "School", null, null, EducationStatus.GRADUATED,
                                null, null, null, null, true, null)
                        .sourceType())
                .isEqualTo(EvidenceSourceType.EDUCATION);
        assertThat(DirectEvidenceFactory.certification(
                                "Certificate", null, null, null, null, null)
                        .sourceType())
                .isEqualTo(EvidenceSourceType.CERTIFICATION);
        assertThat(DirectEvidenceFactory.languageScore("TOEIC", "900", null, null, null)
                        .sourceType())
                .isEqualTo(EvidenceSourceType.LANGUAGE_SCORE);
        assertThat(DirectEvidenceFactory.award("Award", null, null, null).sourceType())
                .isEqualTo(EvidenceSourceType.AWARD);

        String longBody = "x".repeat(20000);
        DirectEvidenceData career = DirectEvidenceFactory.career(
                "Company", null, null, null, null, true, longBody, longBody);
        assertThat(career.sourceType()).isEqualTo(EvidenceSourceType.CAREER);
        assertThat(career.content()).hasSize(20000);
        assertThat(career.metadata()).containsEntry("endedAt", null);
    }
}
