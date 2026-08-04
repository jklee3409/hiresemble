package com.hiresemble.job.api;

import com.hiresemble.job.domain.Eligibility;
import com.hiresemble.job.domain.FitCriterionCategory;
import com.hiresemble.job.domain.MatchLevel;
import com.hiresemble.job.domain.OutdatedReason;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobAnalysisDtos {

    private JobAnalysisDtos() {}

    @Schema(name = "EvidenceRefDto")
    public record EvidenceRefDto(
            UUID id,
            @Schema(minLength = 1, maxLength = 250) String title,
            @Schema(minLength = 1, maxLength = 80) String evidenceCategory,
            EvidenceVerificationStatus verificationStatus,
            EvidenceSourceType sourceType,
            boolean sourceDeleted) {}

    @Schema(name = "RequirementItemDto")
    public record RequirementItemDto(
            FitCriterionCategory category,
            @Schema(minLength = 1, maxLength = 2000) String text,
            boolean required,
            @Schema(nullable = true, maxLength = 500) String sourceLocation) {}

    @Schema(name = "ScoreCriterionDto")
    public record ScoreCriterionDto(
            FitCriterionCategory category,
            @Schema(minLength = 1, maxLength = 2000) String criterion,
            @Schema(minimum = "0", maximum = "100") BigDecimal weight,
            MatchLevel matchLevel,
            @Schema(minimum = "0", maximum = "100") BigDecimal score,
            List<EvidenceRefDto> evidenceRefs,
            @Schema(minLength = 1, maxLength = 2000) String explanation) {
        public ScoreCriterionDto {
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }

    @Schema(name = "JobAnalysisSummaryDto")
    public record JobAnalysisSummaryDto(
            UUID id,
            @Schema(minimum = "1") int analysisVersion,
            Eligibility eligibility,
            @Schema(nullable = true, minimum = "0", maximum = "100") BigDecimal fitScore,
            @Schema(nullable = true, minimum = "0", maximum = "100") BigDecimal analysisCoverage,
            boolean analysisOutdated,
            List<OutdatedReason> outdatedReasons,
            Instant createdAt,
            UUID agentRunId) {
        public JobAnalysisSummaryDto {
            outdatedReasons = List.copyOf(outdatedReasons);
        }
    }

    @Schema(name = "JobAnalysisDetailDto")
    public record JobAnalysisDetailDto(
            UUID id,
            @Schema(minimum = "1") int analysisVersion,
            Eligibility eligibility,
            @Schema(nullable = true, minimum = "0", maximum = "100") BigDecimal fitScore,
            @Schema(nullable = true, minimum = "0", maximum = "100") BigDecimal analysisCoverage,
            boolean analysisOutdated,
            List<OutdatedReason> outdatedReasons,
            Instant createdAt,
            UUID agentRunId,
            List<ScoreCriterionDto> scoreBreakdown,
            List<RequirementItemDto> requiredQualifications,
            List<RequirementItemDto> preferredQualifications,
            List<RequirementItemDto> responsibilities,
            List<String> strengths,
            List<String> gaps,
            List<EvidenceRefDto> matchedEvidenceRefs,
            @Schema(nullable = true, maxLength = 10000) String analysisSummary) {
        public JobAnalysisDetailDto {
            outdatedReasons = List.copyOf(outdatedReasons);
            scoreBreakdown = List.copyOf(scoreBreakdown);
            requiredQualifications = List.copyOf(requiredQualifications);
            preferredQualifications = List.copyOf(preferredQualifications);
            responsibilities = List.copyOf(responsibilities);
            strengths = List.copyOf(strengths);
            gaps = List.copyOf(gaps);
            matchedEvidenceRefs = List.copyOf(matchedEvidenceRefs);
        }
    }

    @Schema(name = "JobAnalysisPageResponse")
    public record JobAnalysisPageDto(
            List<JobAnalysisSummaryDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public JobAnalysisPageDto {
            items = List.copyOf(items);
        }
    }
}
