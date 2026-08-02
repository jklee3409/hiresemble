package com.hiresemble.dashboard.application;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.dashboard.application.DashboardModels.AgentRunSnapshot;
import com.hiresemble.dashboard.application.DashboardModels.CareerGuidePost;
import com.hiresemble.dashboard.application.DashboardModels.DashboardView;
import com.hiresemble.dashboard.application.DashboardModels.DeadlineDay;
import com.hiresemble.dashboard.application.DashboardModels.DeadlineJob;
import com.hiresemble.dashboard.application.DashboardModels.DocumentSnapshot;
import com.hiresemble.dashboard.application.DashboardModels.JobSnapshot;
import com.hiresemble.dashboard.application.DashboardModels.ProfileSnapshot;
import com.hiresemble.dashboard.infrastructure.DashboardReadStore;
import com.hiresemble.dashboard.infrastructure.DashboardReadStore.ProfileProjection;
import com.hiresemble.dashboard.infrastructure.DashboardReadStore.SummaryCounts;
import com.hiresemble.profile.domain.model.ProfileCompletion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DashboardReadStore store;
    private final Clock clock;

    public DashboardQueryService(DashboardReadStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(UUID userId, YearMonth month) {
        Instant now = clock.instant();
        Instant fromInclusive = month.atDay(1).atStartOfDay(SEOUL).toInstant();
        Instant toExclusive = month.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
        ProfileProjection profile = store.profile(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ProfileCompletion completion = ProfileCompletion.calculate(
                profile.legalName(),
                profile.desiredRoles(),
                profile.desiredIndustries(),
                profile.desiredLocations(),
                profile.primaryEducation() != null);
        SummaryCounts counts = store.summaryCounts(userId);

        Map<LocalDate, List<DeadlineJob>> byDate = new LinkedHashMap<>();
        for (DeadlineJob job : store.deadlines(userId, fromInclusive, toExclusive)) {
            LocalDate date = job.deadlineAt().atZone(SEOUL).toLocalDate();
            byDate.computeIfAbsent(date, ignored -> new java.util.ArrayList<>()).add(job);
        }
        List<DeadlineDay> deadlineDays = byDate.entrySet().stream()
                .map(entry -> new DeadlineDay(entry.getKey(), entry.getValue()))
                .toList();

        return new DashboardView(
                now,
                month,
                new ProfileSnapshot(
                        profile.displayName(),
                        profile.legalName(),
                        profile.desiredRoles(),
                        profile.desiredLocations(),
                        completion.completed(),
                        completion.completionPercent(),
                        completion.missingItems(),
                        profile.primaryEducation()),
                new DocumentSnapshot(
                        counts.documentRegisteredCount(),
                        counts.documentProcessingCount(),
                        counts.documentNeedsActionCount()),
                new JobSnapshot(
                        counts.jobRegisteredCount(),
                        counts.jobPreparingCount(),
                        counts.jobSubmittedCount()),
                new AgentRunSnapshot(counts.activeRunCount()),
                deadlineDays);
    }

    @Transactional(readOnly = true)
    public List<CareerGuidePost> careerGuides() {
        return store.publishedGuides(clock.instant());
    }
}
