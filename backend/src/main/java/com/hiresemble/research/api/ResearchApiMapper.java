package com.hiresemble.research.api;

import com.hiresemble.agentrun.api.dto.SafeErrorDto;
import com.hiresemble.research.api.ResearchDtos.ResearchRunDto;
import com.hiresemble.research.api.ResearchDtos.ResearchSourceDto;
import com.hiresemble.research.api.ResearchDtos.ResearchSourceRefDto;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import org.springframework.stereotype.Component;

@Component
public class ResearchApiMapper {

    public ResearchRunDto run(ResearchRunRow value) {
        SafeErrorDto safeError = value.safeErrorCode() == null
                ? null
                : new SafeErrorDto(
                        value.safeErrorCode(),
                        "면접 조사 중 외부 정보를 불러오지 못했어요.");
        return new ResearchRunDto(
                value.id(),
                value.retryOfResearchRunId(),
                value.researchQuality(),
                value.status(),
                value.sourceCoverage(),
                value.missingCoverageTopics(),
                value.summary(),
                value.agentRunId(),
                value.retryable(),
                safeError,
                value.createdAt(),
                value.startedAt(),
                value.completedAt());
    }

    public ResearchSourceDto source(ResearchSourceRow value) {
        return new ResearchSourceDto(
                value.id(),
                value.topic(),
                value.sourceUrl(),
                value.title(),
                value.sourceType(),
                value.publishedAt(),
                value.retrievedAt(),
                value.snippet(),
                value.reliabilityNotice());
    }

    public ResearchSourceRefDto sourceRef(ResearchSourceRow value) {
        return new ResearchSourceRefDto(
                value.id(),
                value.topic(),
                value.title(),
                value.sourceUrl(),
                value.sourceType(),
                value.retrievedAt());
    }
}
