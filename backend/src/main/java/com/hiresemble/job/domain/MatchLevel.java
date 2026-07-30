package com.hiresemble.job.domain;

import java.math.BigDecimal;

public enum MatchLevel {
    MATCHED("1.0"),
    PARTIAL("0.5"),
    MISSING("0"),
    UNKNOWN("0");

    private final BigDecimal coefficient;

    MatchLevel(String coefficient) {
        this.coefficient = new BigDecimal(coefficient);
    }

    public BigDecimal coefficient() {
        return coefficient;
    }
}
