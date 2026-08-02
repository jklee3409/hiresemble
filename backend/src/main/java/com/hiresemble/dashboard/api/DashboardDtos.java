package com.hiresemble.dashboard.api;

import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.ProfileCompletionItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {}

    @Schema(name = "DashboardDto")
    public record DashboardDto(
            Instant generatedAt,
            @Schema(pattern = "^\\d{4}-(0[1-9]|1[0-2])$") String month,
            DashboardProfileDto profile,
            DashboardDocumentsDto documents,
            DashboardJobsDto jobs,
            DashboardAgentRunsDto agentRuns,
            List<DashboardDeadlineDayDto> deadlineDays) {
        public DashboardDto {
            deadlineDays = List.copyOf(deadlineDays);
        }
    }

    @Schema(name = "DashboardProfileDto")
    public record DashboardProfileDto(
            @Schema(minLength = 1, maxLength = 100) String displayName,
            @Schema(nullable = true, minLength = 1, maxLength = 100) String legalName,
            List<String> desiredRoles,
            List<String> desiredLocations,
            boolean completed,
            @Schema(minimum = "0", maximum = "100") int completionPercent,
            List<ProfileCompletionItem> missingItems,
            @Schema(nullable = true) DashboardEducationDto primaryEducation) {
        public DashboardProfileDto {
            desiredRoles = List.copyOf(desiredRoles);
            desiredLocations = List.copyOf(desiredLocations);
            missingItems = List.copyOf(missingItems);
        }
    }

    @Schema(name = "DashboardEducationDto")
    public record DashboardEducationDto(
            @Schema(minLength = 1, maxLength = 200) String schoolName,
            @Schema(nullable = true, maxLength = 200) String major,
            @Schema(nullable = true, maxLength = 100) String degree,
            EducationLevel educationLevel,
            EducationStatus educationStatus) {}

    @Schema(name = "DashboardDocumentsDto")
    public record DashboardDocumentsDto(
            @Schema(minimum = "0") long registeredCount,
            @Schema(minimum = "0") long processingCount,
            @Schema(minimum = "0") long needsActionCount) {}

    @Schema(name = "DashboardJobsDto")
    public record DashboardJobsDto(
            @Schema(minimum = "0") long registeredCount,
            @Schema(minimum = "0") long preparingCount,
            @Schema(minimum = "0") long submittedCount) {}

    @Schema(name = "DashboardAgentRunsDto")
    public record DashboardAgentRunsDto(@Schema(minimum = "0") long activeCount) {}

    @Schema(name = "DashboardDeadlineDayDto")
    public record DashboardDeadlineDayDto(
            LocalDate date,
            @Schema(minimum = "1") int count,
            List<DashboardDeadlineJobDto> items) {
        public DashboardDeadlineDayDto {
            items = List.copyOf(items);
        }
    }

    @Schema(name = "DashboardDeadlineJobDto")
    public record DashboardDeadlineJobDto(
            UUID id,
            @Schema(nullable = true, maxLength = 200) String companyName,
            @Schema(nullable = true, maxLength = 300) String title,
            @Schema(nullable = true, maxLength = 300) String positionName,
            JobStatus status,
            Instant deadlineAt) {}

    @Schema(name = "CareerGuidePostDto")
    public record CareerGuidePostDto(
            UUID id,
            @Schema(allowableValues = "PUBLISHED") String status,
            @Schema(minimum = "0") int displayOrder,
            @Schema(minLength = 1, maxLength = 60) String category,
            @Schema(minLength = 1, maxLength = 200) String title,
            @Schema(minLength = 1, maxLength = 500) String summary,
            @Schema(minLength = 1, maxLength = 10000) String body,
            Instant publishedAt,
            @Schema(minimum = "0") long version) {}
}
