package com.hiresemble.job.api;

import com.hiresemble.job.domain.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class JobRequests {

    private JobRequests() {}

    @Schema(name = "JobCreateRequest")
    public record CreateJobRequest(
            @NotBlank @Size(max = 2000) String sourceUrl,
            @Size(max = 200) String companyName,
            @Size(max = 300) String positionName,
            @Size(min = 1, max = 200000) String descriptionText,
            Instant deadlineAt) {}

    @Schema(name = "JobUpdateRequest")
    public record UpdateJobRequest(
            @Size(max = 200) String companyName,
            @Size(max = 300) String title,
            @Size(max = 300) String positionName,
            @Size(max = 200000) String descriptionText,
            Instant deadlineAt,
            @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "JobStatusUpdateRequest")
    public record ChangeJobStatusRequest(
            @NotNull JobStatus status,
            @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "JobExtractionRetryRequest")
    public record RetryJobExtractionRequest(
            @NotNull @PositiveOrZero Long version) {}
}
