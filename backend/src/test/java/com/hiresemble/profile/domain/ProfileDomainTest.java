package com.hiresemble.profile.domain;

import com.hiresemble.profile.domain.model.DirectEvidenceData;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.model.ExperienceRecords.ExperienceItemRecord;
import com.hiresemble.profile.domain.model.ExperienceRecords.SimilarExperienceRecord;
import com.hiresemble.profile.domain.model.ProfileCompletion;
import com.hiresemble.profile.domain.model.ProfileCompletionItem;
import com.hiresemble.profile.domain.policy.ExperienceSimilarityPolicy;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import com.hiresemble.profile.domain.service.DirectEvidenceFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    void educationEvidenceCategoriesAreRecognizedAcrossSupportedLabels() {
        assertThat(ProfilePolicy.isEducationEvidenceCategory("EDUCATION_HISTORY")).isTrue();
        assertThat(ProfilePolicy.isEducationEvidenceCategory(" academic-background ")).isTrue();
        assertThat(ProfilePolicy.isEducationEvidenceCategory("학력 정보")).isTrue();
        assertThat(ProfilePolicy.isEducationEvidenceCategory("PROJECT")).isFalse();
    }

    @Test
    void directEvidenceFactoryMapsNonEducationStructuredSourcesAndBoundsGeneratedContent() {
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

    @Test
    void experienceSimilarityUsesSemanticDistanceAnchorsAndNumericConflictGuardrails() {
        ExperienceItemRecord existing = experience(
                "결제 API 성능 개선",
                "Redis 캐시를 도입해 평균 응답 시간을 40% 단축했습니다.");

        var same = ExperienceSimilarityPolicy.decide(
                "결제 API 응답 개선",
                "Redis 캐시 적용으로 평균 응답 시간을 40% 줄였습니다.",
                List.of(new SimilarExperienceRecord(existing, 0.04d)));
        assertThat(same.kind()).isEqualTo(ExperienceMatchKind.SAME_EXPERIENCE);

        var conflict = ExperienceSimilarityPolicy.decide(
                "결제 API 응답 개선",
                "Redis 캐시 적용으로 평균 응답 시간을 45% 줄였습니다.",
                List.of(new SimilarExperienceRecord(existing, 0.04d)));
        assertThat(conflict.kind()).isEqualTo(ExperienceMatchKind.CONFLICT);

        var review = ExperienceSimilarityPolicy.decide(
                "결제 장애 대응",
                "결제 모니터링과 장애 알림을 구축했습니다.",
                List.of(new SimilarExperienceRecord(existing, 0.10d)));
        assertThat(review.kind()).isEqualTo(ExperienceMatchKind.RELATED_DIFFERENT);

        assertThat(ExperienceSimilarityPolicy.decide(
                                "추천 시스템",
                                "개인화 추천 모델을 운영했습니다.",
                                List.of(new SimilarExperienceRecord(existing, 0.30d)))
                        .kind())
                .isEqualTo(ExperienceMatchKind.NEW);
    }

    @Test
    void experienceFingerprintIgnoresPresentationOnlyDifferences() {
        assertThat(ExperienceSimilarityPolicy.fingerprint(
                        "PROJECT", "결제 API", "응답 시간을 개선했습니다."))
                .isEqualTo(ExperienceSimilarityPolicy.fingerprint(
                        " project ", "결제-API", "응답 시간을  개선했습니다!"));
    }

    private ExperienceItemRecord experience(String title, String content) {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new ExperienceItemRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PROJECT",
                title,
                content,
                EvidenceVerificationStatus.VERIFIED,
                ExperienceMatchKind.NEW,
                null,
                null,
                ExperienceSimilarityPolicy.VERSION,
                "a".repeat(64),
                1,
                1,
                0,
                "지원용 이력서.pdf",
                0,
                now,
                now);
    }
}
