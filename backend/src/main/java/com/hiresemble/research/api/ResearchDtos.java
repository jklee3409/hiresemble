package com.hiresemble.research.api;

import com.hiresemble.agentrun.api.dto.SafeErrorDto;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.ResearchRunStatus;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
import com.hiresemble.research.domain.SourceCoverage;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResearchDtos {

    private ResearchDtos() {}

    @Schema(name = "ResearchRunDto")
    public record ResearchRunDto(
            UUID id,
            @Schema(nullable = true) UUID retryOfResearchRunId,
            ResearchQuality researchQuality,
            ResearchRunStatus status,
            @Schema(nullable = true) SourceCoverage sourceCoverage,
            @ArraySchema(
                    maxItems = 20,
                    schema = @Schema(minLength = 1, maxLength = 200))
            List<String> missingCoverageTopics,
            @Schema(nullable = true, maxLength = 10000) String summary,
            UUID agentRunId,
            boolean retryable,
            @Schema(nullable = true) SafeErrorDto safeError,
            Instant createdAt,
            @Schema(nullable = true) Instant startedAt,
            @Schema(nullable = true) Instant completedAt) {
        public ResearchRunDto {
            missingCoverageTopics = List.copyOf(missingCoverageTopics);
        }
    }

    @Schema(name = "ResearchSourceDto")
    public record ResearchSourceDto(
            UUID id,
            ResearchTopic topic,
            @Schema(minLength = 1, maxLength = 2000) String sourceUrl,
            @Schema(nullable = true, maxLength = 500) String title,
            ResearchSourceType sourceType,
            @Schema(nullable = true) Instant publishedAt,
            Instant retrievedAt,
            @Schema(nullable = true, maxLength = 2000) String snippet,
            @Schema(minLength = 1, maxLength = 500) String reliabilityNotice) {}

    @Schema(name = "ResearchSourceRefDto")
    public record ResearchSourceRefDto(
            UUID id,
            ResearchTopic topic,
            @Schema(nullable = true, maxLength = 500) String title,
            @Schema(minLength = 1, maxLength = 2000) String sourceUrl,
            ResearchSourceType sourceType,
            Instant retrievedAt) {}

    @Schema(name = "ResearchRetryAcceptedDto")
    public record ResearchRetryAcceptedDto(
            UUID questionSetId,
            UUID researchRunId,
            UUID agentRunId,
            UUID retryOfResearchRunId,
            @Schema(allowableValues = "QUEUED") String status) {}
}
