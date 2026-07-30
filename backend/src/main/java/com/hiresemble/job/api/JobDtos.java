package com.hiresemble.job.api;

import com.hiresemble.agentrun.api.dto.SafeErrorDto;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.job.domain.ClosedReason;
import com.hiresemble.job.domain.DeadlineSource;
import com.hiresemble.job.domain.JobDescriptionSource;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.job.domain.OutdatedReason;
import com.hiresemble.job.api.JobAnalysisDtos.JobAnalysisSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {}

    @Schema(name = "JobCreationAcceptedDto")
    public record JobCreationAcceptedDto(
            UUID jobId,
            JobStatus status,
            JobExtractionStatus extractionStatus,
            @Schema(nullable = true) UUID agentRunId) {}

    @Schema(name = "JobSummaryDto")
    public record JobSummaryDto(
            UUID id,
            @Schema(nullable = true, maxLength = 200) String companyName,
            @Schema(nullable = true, maxLength = 300) String title,
            @Schema(nullable = true, maxLength = 300) String positionName,
            JobStatus status,
            JobExtractionStatus extractionStatus,
            @Schema(nullable = true) Instant submittedAt,
            @Schema(nullable = true) Instant deadlineAt,
            DeadlineSource deadlineSource,
            @Schema(nullable = true, minimum = "0", maximum = "100") java.math.BigDecimal latestFitScore,
            boolean analysisOutdated,
            List<OutdatedReason> outdatedReasons,
            @Schema(nullable = true) CoverLetterStatus coverLetterStatus,
            @Schema(minimum = "0") int interviewPreparationCount,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        public JobSummaryDto {
            outdatedReasons = List.copyOf(outdatedReasons);
        }
    }

    @Schema(name = "JobDetailDto")
    public record JobDetailDto(
            UUID id,
            @Schema(nullable = true, maxLength = 200) String companyName,
            @Schema(nullable = true, maxLength = 300) String title,
            @Schema(nullable = true, maxLength = 300) String positionName,
            JobStatus status,
            JobExtractionStatus extractionStatus,
            @Schema(nullable = true) Instant submittedAt,
            @Schema(nullable = true) Instant deadlineAt,
            DeadlineSource deadlineSource,
            @Schema(nullable = true, minimum = "0", maximum = "100") java.math.BigDecimal latestFitScore,
            boolean analysisOutdated,
            List<OutdatedReason> outdatedReasons,
            @Schema(nullable = true) CoverLetterStatus coverLetterStatus,
            @Schema(minimum = "0") int interviewPreparationCount,
            long version,
            Instant createdAt,
            Instant updatedAt,
            @Schema(minLength = 1, maxLength = 2000) String sourceUrl,
            @Schema(minLength = 1, maxLength = 2000) String canonicalUrl,
            @Schema(nullable = true, maxLength = 100) String roleCategory,
            @Schema(nullable = true, maxLength = 100) String employmentType,
            @Schema(nullable = true, maxLength = 200) String location,
            @Schema(nullable = true, maxLength = 200000) String descriptionText,
            @Schema(nullable = true) JobDescriptionSource descriptionSource,
            @Schema(nullable = true) SafeErrorDto extractionError,
            @Schema(nullable = true) Instant closedAt,
            @Schema(nullable = true) ClosedReason closedReason,
            @Schema(nullable = true) JobAnalysisSummaryDto latestAnalysis,
            @Schema(nullable = true) UUID coverLetterId,
            @Schema(nullable = true) UUID latestQuestionSetId,
            @Schema(nullable = true) UUID latestMockSessionId) {
        public JobDetailDto {
            outdatedReasons = List.copyOf(outdatedReasons);
        }
    }

    @Schema(name = "JobPageDto")
    public record JobPageDto(
            List<JobSummaryDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public JobPageDto {
            items = List.copyOf(items);
        }
    }
}
