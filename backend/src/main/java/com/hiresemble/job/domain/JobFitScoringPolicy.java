package com.hiresemble.job.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JobFitScoringPolicy {

    private static final BigDecimal TOTAL = new BigDecimal("100.00");
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private JobFitScoringPolicy() {}

    public static ScoreResult score(List<CriterionInput> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > 100) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        List<CriterionInput> ordered = requested.stream()
                .map(JobFitScoringPolicy::validated)
                .sorted(Comparator.comparing(CriterionInput::category)
                        .thenComparing(value -> value.criterion().toLowerCase(java.util.Locale.ROOT))
                        .thenComparing(value -> value.sourceLocation() == null ? "" : value.sourceLocation()))
                .toList();
        EnumMap<FitCriterionCategory, List<CriterionInput>> groups =
                new EnumMap<>(FitCriterionCategory.class);
        for (CriterionInput input : ordered) {
            groups.computeIfAbsent(input.category(), ignored -> new ArrayList<>()).add(input);
        }
        BigDecimal presentBaseTotal = groups.keySet().stream()
                .map(FitCriterionCategory::baseWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<FitCriterionCategory, BigDecimal> categoryWeights =
                allocateCategoryWeights(groups, presentBaseTotal);
        List<ScoredCriterion> criteria = new ArrayList<>(ordered.size());
        int order = 0;
        for (FitCriterionCategory category : FitCriterionCategory.values()) {
            List<CriterionInput> categoryCriteria = groups.get(category);
            if (categoryCriteria == null) {
                continue;
            }
            List<BigDecimal> weights = equalAllocation(
                    categoryWeights.get(category), categoryCriteria.size());
            for (int index = 0; index < categoryCriteria.size(); index++) {
                CriterionInput input = categoryCriteria.get(index);
                BigDecimal weight = weights.get(index);
                BigDecimal criterionScore = weight.multiply(input.matchLevel().coefficient())
                        .setScale(2, RoundingMode.HALF_UP);
                criteria.add(new ScoredCriterion(
                        input.category(),
                        input.criterion(),
                        weight,
                        input.matchLevel(),
                        criterionScore,
                        input.explanation(),
                        input.sourceLocation(),
                        input.evidenceIds(),
                        order++));
            }
        }
        BigDecimal total = criteria.stream()
                .map(ScoredCriterion::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ZERO) < 0 || total.compareTo(TOTAL) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return new ScoreResult(total, criteria);
    }

    private static Map<FitCriterionCategory, BigDecimal> allocateCategoryWeights(
            EnumMap<FitCriterionCategory, List<CriterionInput>> groups,
            BigDecimal presentBaseTotal) {
        Map<FitCriterionCategory, BigDecimal> weights = new LinkedHashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        List<FitCriterionCategory> present = groups.keySet().stream().sorted().toList();
        for (int index = 0; index < present.size(); index++) {
            FitCriterionCategory category = present.get(index);
            BigDecimal weight;
            if (index == present.size() - 1) {
                weight = TOTAL.subtract(allocated);
            } else {
                weight = category.baseWeight()
                        .multiply(TOTAL)
                        .divide(presentBaseTotal, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(weight);
            }
            weights.put(category, weight.setScale(2, RoundingMode.UNNECESSARY));
        }
        return weights;
    }

    private static List<BigDecimal> equalAllocation(BigDecimal total, int count) {
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        int residualCents = total.subtract(base.multiply(BigDecimal.valueOf(count)))
                .divide(CENT, 0, RoundingMode.UNNECESSARY)
                .intValueExact();
        List<BigDecimal> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(index < residualCents ? base.add(CENT) : base);
        }
        return List.copyOf(result);
    }

    private static CriterionInput validated(CriterionInput input) {
        if (input == null
                || input.category() == null
                || input.matchLevel() == null
                || input.criterion() == null
                || input.criterion().isBlank()
                || input.criterion().length() > 2000
                || input.explanation() == null
                || input.explanation().isBlank()
                || input.explanation().length() > 2000
                || (input.sourceLocation() != null
                        && (input.sourceLocation().isBlank()
                                || input.sourceLocation().length() > 500))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new CriterionInput(
                input.category(),
                input.criterion().trim(),
                input.matchLevel(),
                input.explanation().trim(),
                input.sourceLocation() == null ? null : input.sourceLocation().trim(),
                input.evidenceIds() == null ? List.of() : input.evidenceIds());
    }

    public record CriterionInput(
            FitCriterionCategory category,
            String criterion,
            MatchLevel matchLevel,
            String explanation,
            String sourceLocation,
            List<java.util.UUID> evidenceIds) {
        public CriterionInput {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record ScoredCriterion(
            FitCriterionCategory category,
            String criterion,
            BigDecimal weight,
            MatchLevel matchLevel,
            BigDecimal score,
            String explanation,
            String sourceLocation,
            List<java.util.UUID> evidenceIds,
            int order) {
        public ScoredCriterion {
            evidenceIds = List.copyOf(evidenceIds);
        }
    }

    public record ScoreResult(BigDecimal totalScore, List<ScoredCriterion> criteria) {
        public ScoreResult {
            criteria = List.copyOf(criteria);
        }
    }
}
