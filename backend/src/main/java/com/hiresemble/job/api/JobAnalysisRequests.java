package com.hiresemble.job.api;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class JobAnalysisRequests {

    private JobAnalysisRequests() {}

    @Schema(name = "JobAnalysisRequest")
    public record AnalyzeJobRequest(
            @NotNull AiQualityMode qualityMode,
            @NotNull Boolean forceReanalyze,
            @NotNull @PositiveOrZero Long jobVersion) {}
}
