package com.hiresemble.job.api;

import com.hiresemble.agentrun.api.dto.SafeErrorDto;
import com.hiresemble.job.api.JobDtos.JobDetailDto;
import com.hiresemble.job.api.JobDtos.JobSummaryDto;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import java.util.List;
import com.hiresemble.job.application.JobAnalysisApplicationService;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSummary;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class JobApiMapper {

    private final JobAnalysisApplicationService analysisService;
    private final JobAnalysisApiMapper analysisMapper;

    public JobApiMapper(
            JobAnalysisApplicationService analysisService,
            JobAnalysisApiMapper analysisMapper) {
        this.analysisService = analysisService;
        this.analysisMapper = analysisMapper;
    }

    public JobSummaryDto summary(JobRecord job) {
        Optional<JobAnalysisSummary> latest =
                analysisService.latestSummary(job.userId(), job.id());
        return new JobSummaryDto(
                job.id(),
                job.companyName(),
                job.title(),
                job.positionName(),
                job.status(),
                job.extractionStatus(),
                job.submittedAt(),
                job.deadlineAt(),
                job.deadlineSource(),
                latest.map(JobAnalysisSummary::fitScore).orElse(null),
                latest.map(JobAnalysisSummary::analysisOutdated).orElse(false),
                latest.map(JobAnalysisSummary::outdatedReasons).orElse(List.of()),
                null,
                0,
                job.version(),
                job.createdAt(),
                job.updatedAt());
    }

    public JobDetailDto detail(JobRecord job) {
        Optional<JobAnalysisSummary> latest =
                analysisService.latestSummary(job.userId(), job.id());
        return new JobDetailDto(
                job.id(),
                job.companyName(),
                job.title(),
                job.positionName(),
                job.status(),
                job.extractionStatus(),
                job.submittedAt(),
                job.deadlineAt(),
                job.deadlineSource(),
                latest.map(JobAnalysisSummary::fitScore).orElse(null),
                latest.map(JobAnalysisSummary::analysisOutdated).orElse(false),
                latest.map(JobAnalysisSummary::outdatedReasons).orElse(List.of()),
                null,
                0,
                job.version(),
                job.createdAt(),
                job.updatedAt(),
                job.sourceUrl(),
                job.canonicalUrl(),
                job.roleCategory(),
                job.employmentType(),
                job.location(),
                job.descriptionText(),
                job.descriptionSource(),
                error(job),
                job.closedAt(),
                job.closedReason(),
                latest.map(analysisMapper::summary).orElse(null),
                null,
                null,
                null);
    }

    private SafeErrorDto error(JobRecord job) {
        if (job.extractionErrorCode() == null) {
            return null;
        }
        String message = job.extractionErrorMessage();
        if (message == null || message.isBlank()) {
            message = "채용 공고 본문을 추출하지 못했습니다.";
        }
        return new SafeErrorDto(job.extractionErrorCode(), message);
    }
}
