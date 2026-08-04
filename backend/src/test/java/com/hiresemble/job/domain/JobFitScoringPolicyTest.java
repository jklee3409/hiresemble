package com.hiresemble.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobFitScoringPolicyTest {

    @Test
    void scoresAllFiveCanonicalCategoriesWithFixedWeights() {
        var result = JobFitScoringPolicy.score(List.of(
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "Required", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.CORE_RESPONSIBILITY_OR_SKILL, "Core", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.PREFERRED_QUALIFICATION, "Preferred", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.RELATED_EXPERIENCE_OR_DOMAIN, "Domain", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.EDUCATION_CERTIFICATION_LANGUAGE, "Education", MatchLevel.MATCHED)));

        assertThat(result.totalScore()).isEqualByComparingTo("100.00");
        assertThat(result.analysisCoverage()).isEqualByComparingTo("100.00");
        assertThat(result.criteria())
                .extracting(value -> value.weight().toPlainString())
                .containsExactly("40.00", "30.00", "15.00", "10.00", "5.00");
    }

    @Test
    void redistributesMissingCategoryWeightProportionally() {
        var result = JobFitScoringPolicy.score(List.of(
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "Required", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.CORE_RESPONSIBILITY_OR_SKILL, "Core", MatchLevel.PARTIAL)));

        assertThat(result.criteria().get(0).weight()).isEqualByComparingTo("57.14");
        assertThat(result.criteria().get(1).weight()).isEqualByComparingTo("42.86");
        assertThat(result.totalScore()).isEqualByComparingTo("78.57");
    }

    @Test
    void coefficientsProduceMatchedPartialMissingAndUnknownScores() {
        var result = JobFitScoringPolicy.score(List.of(
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "A", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "B", MatchLevel.PARTIAL),
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "C", MatchLevel.MISSING),
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "D", MatchLevel.UNKNOWN)));

        assertThat(result.criteria())
                .extracting(value -> value.score().toPlainString())
                .containsExactly("33.34", "16.67", "0.00", "0.00");
        assertThat(result.totalScore()).isEqualByComparingTo("50.01");
        assertThat(result.analysisCoverage()).isEqualByComparingTo("75.00");
        assertThat(result.criteria().get(3).weight()).isEqualByComparingTo("0.00");
    }

    @Test
    void allUnknownCriteriaProduceNoMisleadingFitScore() {
        var result = JobFitScoringPolicy.score(List.of(
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "A", MatchLevel.UNKNOWN),
                criterion(FitCriterionCategory.CORE_RESPONSIBILITY_OR_SKILL, "B", MatchLevel.UNKNOWN)));

        assertThat(result.totalScore()).isNull();
        assertThat(result.analysisCoverage()).isEqualByComparingTo("0.00");
        assertThat(result.criteria())
                .allSatisfy(value -> {
                    assertThat(value.weight()).isEqualByComparingTo("0.00");
                    assertThat(value.score()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    void distributesDecimalResidualDeterministicallyAndNeverExceedsWeight() {
        var result = JobFitScoringPolicy.score(List.of(
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "A", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "B", MatchLevel.MATCHED),
                criterion(FitCriterionCategory.REQUIRED_QUALIFICATION, "C", MatchLevel.MATCHED)));

        assertThat(result.criteria())
                .extracting(value -> value.weight().toPlainString())
                .containsExactly("33.34", "33.33", "33.33");
        assertThat(result.criteria())
                .allSatisfy(value -> assertThat(value.score())
                        .isLessThanOrEqualTo(value.weight()));
        assertThat(result.totalScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void keepsEligibilityIndependentFromZeroAndHundredScores() {
        var ineligibleHundred = new Assessment(
                Eligibility.INELIGIBLE,
                JobFitScoringPolicy.score(List.of(criterion(
                        FitCriterionCategory.REQUIRED_QUALIFICATION,
                        "Required",
                        MatchLevel.MATCHED))));
        var eligibleZero = new Assessment(
                Eligibility.ELIGIBLE,
                JobFitScoringPolicy.score(List.of(criterion(
                        FitCriterionCategory.REQUIRED_QUALIFICATION,
                        "Required",
                        MatchLevel.MISSING))));

        assertThat(ineligibleHundred.eligibility()).isEqualTo(Eligibility.INELIGIBLE);
        assertThat(ineligibleHundred.score().totalScore()).isEqualByComparingTo("100.00");
        assertThat(eligibleZero.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(eligibleZero.score().totalScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsAnAnalysisWithoutCriteria() {
        assertThatThrownBy(() -> JobFitScoringPolicy.score(List.of()))
                .isInstanceOf(BusinessException.class);
    }

    private JobFitScoringPolicy.CriterionInput criterion(
            FitCriterionCategory category, String text, MatchLevel level) {
        return new JobFitScoringPolicy.CriterionInput(
                category, text, level, "Deterministic explanation", "line 1", List.of());
    }

    private record Assessment(
            Eligibility eligibility, JobFitScoringPolicy.ScoreResult score) {}
}
