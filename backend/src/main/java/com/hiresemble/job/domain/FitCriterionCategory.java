package com.hiresemble.job.domain;

import java.math.BigDecimal;

public enum FitCriterionCategory {
    REQUIRED_QUALIFICATION("40.00"),
    CORE_RESPONSIBILITY_OR_SKILL("30.00"),
    PREFERRED_QUALIFICATION("15.00"),
    RELATED_EXPERIENCE_OR_DOMAIN("10.00"),
    EDUCATION_CERTIFICATION_LANGUAGE("5.00");

    private final BigDecimal baseWeight;

    FitCriterionCategory(String baseWeight) {
        this.baseWeight = new BigDecimal(baseWeight);
    }

    public BigDecimal baseWeight() {
        return baseWeight;
    }
}
