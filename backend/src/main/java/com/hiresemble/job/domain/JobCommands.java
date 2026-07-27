package com.hiresemble.job.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class JobCommands {

    private JobCommands() {}

    public record CreateJob(
            UUID jobId,
            UUID userId,
            String sourceUrl,
            String canonicalUrl,
            String companyName,
            String positionName,
            String descriptionText,
            Instant deadlineAt) {}

    public record UpdateJob(
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            long version) {}

    public record ChangeStatus(JobStatus status, long version) {}

    public record JobListQuery(
            JobStatus status,
            JobExtractionStatus extractionStatus,
            String query,
            Instant deadlineFrom,
            Instant deadlineTo,
            Integer deadlineWithinDays,
            int page,
            int size,
            String sort) {}

    public record ExtractedFields(
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            BigDecimal deadlineConfidence,
            String roleCategory,
            String employmentType,
            String location) {}
}
