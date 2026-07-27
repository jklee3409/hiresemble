package com.hiresemble.job.domain;

public enum JobExtractionStatus {
    QUEUED,
    EXTRACTING,
    EXTRACTED,
    MANUAL_INPUT_PROVIDED,
    NEEDS_MANUAL_INPUT,
    FAILED
}
