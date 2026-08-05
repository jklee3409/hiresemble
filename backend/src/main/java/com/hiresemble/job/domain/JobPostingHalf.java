package com.hiresemble.job.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public enum JobPostingHalf {
    FIRST_HALF,
    SECOND_HALF;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static JobPostingHalf from(Instant postingStartedAt) {
        return LocalDate.ofInstant(postingStartedAt, SEOUL).getMonthValue() <= 6
                ? FIRST_HALF
                : SECOND_HALF;
    }
}
