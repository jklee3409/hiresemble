package com.hiresemble.job.api;

import com.hiresemble.job.api.JobAnalysisDtos.EvidenceRefDto;
import com.hiresemble.job.api.JobAnalysisDtos.JobAnalysisDetailDto;
import com.hiresemble.job.api.JobAnalysisDtos.JobAnalysisSummaryDto;
import com.hiresemble.job.api.JobAnalysisDtos.RequirementItemDto;
import com.hiresemble.job.api.JobAnalysisDtos.ScoreCriterionDto;
import com.hiresemble.job.application.model.JobAnalysisModels.EvidenceReference;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisDetail;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSummary;
import com.hiresemble.job.application.model.JobAnalysisModels.RequirementItem;
import org.springframework.stereotype.Component;

@Component
public final class JobAnalysisApiMapper {

    public JobAnalysisSummaryDto summary(JobAnalysisSummary value) {
        return new JobAnalysisSummaryDto(
                value.id(),
                value.analysisVersion(),
                value.eligibility(),
                value.fitScore(),
                value.analysisCoverage(),
                value.analysisOutdated(),
                value.outdatedReasons(),
                value.createdAt(),
                value.agentRunId());
    }

    public JobAnalysisDetailDto detail(JobAnalysisDetail value) {
        JobAnalysisSummary summary = value.summary();
        return new JobAnalysisDetailDto(
                summary.id(),
                summary.analysisVersion(),
                summary.eligibility(),
                summary.fitScore(),
                summary.analysisCoverage(),
                summary.analysisOutdated(),
                summary.outdatedReasons(),
                summary.createdAt(),
                summary.agentRunId(),
                value.scoreBreakdown().stream()
                        .map(criterion -> new ScoreCriterionDto(
                                criterion.category(),
                                criterion.criterion(),
                                criterion.weight(),
                                criterion.matchLevel(),
                                criterion.score(),
                                criterion.evidenceReferences().stream()
                                        .map(this::evidence)
                                        .toList(),
                                criterion.explanation()))
                        .toList(),
                value.requiredQualifications().stream().map(this::requirement).toList(),
                value.preferredQualifications().stream().map(this::requirement).toList(),
                value.responsibilities().stream().map(this::requirement).toList(),
                value.strengths(),
                value.gaps(),
                value.matchedEvidenceReferences().stream().map(this::evidence).toList(),
                value.analysisSummary());
    }

    private RequirementItemDto requirement(RequirementItem value) {
        return new RequirementItemDto(
                value.category(), value.text(), value.required(), value.sourceLocation());
    }

    private EvidenceRefDto evidence(EvidenceReference value) {
        return new EvidenceRefDto(
                value.id(),
                value.title(),
                value.evidenceCategory(),
                value.verificationStatus(),
                value.sourceType(),
                value.sourceDeleted());
    }
}
