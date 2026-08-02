package com.hiresemble.dashboard.application;

import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.ProfileCompletionItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class DashboardModels {

    private DashboardModels() {}

    public record DashboardView(
            Instant generatedAt,
            YearMonth month,
            ProfileSnapshot profile,
            DocumentSnapshot documents,
            JobSnapshot jobs,
            AgentRunSnapshot agentRuns,
            List<DeadlineDay> deadlineDays) {
        public DashboardView {
            deadlineDays = List.copyOf(deadlineDays);
        }
    }

    public record ProfileSnapshot(
            String displayName,
            String legalName,
            List<String> desiredRoles,
            List<String> desiredLocations,
            boolean completed,
            int completionPercent,
            List<ProfileCompletionItem> missingItems,
            EducationSnapshot primaryEducation) {
        public ProfileSnapshot {
            desiredRoles = List.copyOf(desiredRoles);
            desiredLocations = List.copyOf(desiredLocations);
            missingItems = List.copyOf(missingItems);
        }
    }

    public record EducationSnapshot(
            String schoolName,
            String major,
            String degree,
            EducationLevel educationLevel,
            EducationStatus educationStatus) {}

    public record DocumentSnapshot(long registeredCount, long processingCount, long needsActionCount) {}

    public record JobSnapshot(long registeredCount, long preparingCount, long submittedCount) {}

    public record AgentRunSnapshot(long activeCount) {}

    public record DeadlineDay(LocalDate date, List<DeadlineJob> items) {
        public DeadlineDay {
            items = List.copyOf(items);
        }
    }

    public record DeadlineJob(
            UUID id,
            String companyName,
            String title,
            String positionName,
            JobStatus status,
            Instant deadlineAt) {}

    public record CareerGuidePost(
            UUID id,
            String status,
            int displayOrder,
            String category,
            String title,
            String summary,
            String body,
            Instant publishedAt,
            long version) {}
}
