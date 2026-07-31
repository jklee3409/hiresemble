package com.hiresemble.research.application.service;

import com.hiresemble.agentrun.application.model.WorkflowRetryOptions;
import com.hiresemble.agentrun.application.service.AgentRunRetryTransaction;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.interview.application.service.InterviewPreparationRetryContributor;
import com.hiresemble.interview.infrastructure.InterviewStore;
import com.hiresemble.research.application.model.ResearchModels.AcceptedResearchRetry;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.infrastructure.ResearchStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResearchRetryApplicationService {

    private final ResearchStore researchStore;
    private final InterviewStore interviewStore;
    private final AgentRunRetryTransaction retryTransaction;
    private final IdempotencyService idempotency;

    public ResearchRetryApplicationService(
            ResearchStore researchStore,
            InterviewStore interviewStore,
            AgentRunRetryTransaction retryTransaction,
            IdempotencyService idempotency) {
        this.researchStore = researchStore;
        this.interviewStore = interviewStore;
        this.retryTransaction = retryTransaction;
        this.idempotency = idempotency;
    }

    public IdempotentResponse<AcceptedResearchRetry> retry(
            UUID userId,
            UUID researchRunId,
            ResearchQuality researchQuality,
            AiQualityMode qualityMode,
            String idempotencyKey) {
        ResearchRunRow predecessor = researchStore
                .findRun(userId, researchRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (qualityMode == AiQualityMode.HIGH_QUALITY) {
            throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
        }
        ResearchQuality selectedResearch =
                researchQuality == null ? predecessor.researchQuality() : researchQuality;
        String canonical = "researchQuality="
                + selectedResearch
                + "|qualityMode="
                + (qualityMode == null ? "UNCHANGED" : qualityMode.name());
        IdempotencyScope scope = new IdempotencyScope(
                userId,
                "POST",
                "/api/v1/research-runs/{id}/retry",
                researchRunId,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                canonical,
                AcceptedResearchRetry.class,
                () -> predecessor,
                prepared -> {
                    var launched = retryTransaction.retry(
                            userId,
                            prepared.agentRunId(),
                            new WorkflowRetryOptions(
                                    qualityMode,
                                    Map.of(
                                            InterviewPreparationRetryContributor
                                                    .RESEARCH_QUALITY_OPTION,
                                            selectedResearch.name())));
                    ResearchRunRow successor = researchStore
                            .findByAgentRun(userId, launched.agentRunId())
                            .orElseThrow();
                    var questionSet = interviewStore
                            .findQuestionSetByAgentRun(userId, launched.agentRunId())
                            .orElseThrow();
                    AcceptedResearchRetry body = new AcceptedResearchRetry(
                            questionSet.id(),
                            successor.id(),
                            launched.agentRunId(),
                            prepared.id());
                    return new OriginalResponse<>(
                            202,
                            body,
                            "QUESTION_SET",
                            questionSet.id(),
                            launched.agentRunId());
                },
                ignored -> {});
    }
}
