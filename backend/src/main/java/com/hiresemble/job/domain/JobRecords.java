package com.hiresemble.job.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobRecords {

    private JobRecords() {}

    public record JobRecord(
            UUID id,
            UUID userId,
            UUID companyId,
            String companyName,
            String sourceUrl,
            String canonicalUrl,
            String title,
            String positionName,
            String roleCategory,
            String employmentType,
            String location,
            String descriptionText,
            JobDescriptionSource descriptionSource,
            Instant deadlineAt,
            DeadlineSource deadlineSource,
            java.math.BigDecimal deadlineConfidence,
            JobStatus status,
            JobExtractionStatus extractionStatus,
            Instant submittedAt,
            Instant closedAt,
            ClosedReason closedReason,
            String contentHash,
            UUID latestAgentRunId,
            String extractionErrorCode,
            String extractionErrorMessage,
            boolean companyUserOverride,
            boolean titleUserOverride,
            boolean positionUserOverride,
            boolean deadlineUserOverride,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {}

    public record JobPage(
            List<JobRecord> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public JobPage {
            items = List.copyOf(items);
        }
    }

    public record StatusChange(
            JobStatus fromStatus,
            JobStatus toStatus,
            Instant submittedAt,
            Instant closedAt,
            ClosedReason closedReason) {}

    public record UserOverrides(
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt) {}

    public record WorkflowSnapshot(
            UUID jobId,
            UUID userId,
            UUID latestAgentRunId,
            long version,
            String sourceUrl,
            String canonicalUrl,
            JobExtractionStatus extractionStatus,
            String contentHash,
            UserOverrides userOverrides) {}
}
