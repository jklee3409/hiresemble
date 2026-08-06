package com.hiresemble.interview.application.service;

import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowRetryOptions;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.AgentRunRetryContributor;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.interview.application.model.InterviewModels.PreparedPreparation;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.interview.infrastructure.InterviewStore;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.infrastructure.ResearchStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class InterviewPreparationRetryContributor implements AgentRunRetryContributor {

    public static final String RESEARCH_QUALITY_OPTION = "researchQuality";

    private final InterviewApplicationService service;
    private final InterviewStore store;
    private final ResearchStore researchStore;
    private final AgentRunQueryPort runQuery;

    public InterviewPreparationRetryContributor(
            InterviewApplicationService service,
            InterviewStore store,
            ResearchStore researchStore,
            AgentRunQueryPort runQuery) {
        this.service = service;
        this.store = store;
        this.researchStore = researchStore;
        this.runQuery = runQuery;
    }

    @Override
    public boolean supports(WorkflowType workflowType) {
        return workflowType == WorkflowType.INTERVIEW_PREPARATION;
    }

    @Override
    public AgentRunSnapshot createRetry(
            UUID proposedId,
            AgentRunSnapshot predecessor,
            WorkflowRetryOptions options,
            long budgetPolicyVersion,
            long priceVersion,
            Instant queuedAt) {
        ResearchRunRow predecessorResearch = researchStore
                .findByAgentRun(predecessor.userId(), predecessor.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ResearchQuality researchQuality = parseResearchQuality(
                options.values().get(RESEARCH_QUALITY_OPTION),
                predecessorResearch.researchQuality());
        AiQualityMode qualityMode =
                options.qualityMode() == null
                        ? predecessor.requestedQualityMode()
                        : options.qualityMode();
        if (qualityMode == null || qualityMode == AiQualityMode.HIGH_QUALITY) {
            throw new BusinessException(ErrorCode.QUALITY_MODE_NOT_SUPPORTED);
        }
        List<InterviewQuestionType> questionTypes =
                questionTypes(predecessor.inputReferenceSnapshot());
        int questionCount = predecessor.inputReferenceSnapshot().path("questionCount").asInt(-1);
        if (questionCount < 1 || questionCount > 20) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        PreparedPreparation candidate = service.prepareSnapshot(
                predecessor.userId(),
                predecessorResearch.jobId(),
                predecessorResearch.coverLetterId(),
                researchQuality,
                qualityMode,
                questionTypes,
                questionCount,
                predecessorResearch.id());
        PreparedPreparation prepared = new PreparedPreparation(
                candidate.userId(),
                candidate.jobId(),
                candidate.coverLetterId(),
                candidate.researchRunId(),
                candidate.questionSetId(),
                proposedId,
                candidate.researchQuality(),
                candidate.qualityMode(),
                candidate.questionTypes(),
                candidate.questionCount(),
                candidate.context(),
                candidate.retryOfResearchRunId());
        WorkflowLaunchCommand command = service.preparationLaunchCommand(prepared);
        boolean inserted = store.insertPreparationRetryAgentRun(
                proposedId, predecessor, command, budgetPolicyVersion, priceVersion, queuedAt);
        if (inserted) {
            researchStore.createQueued(
                    prepared.researchRunId(),
                    prepared.userId(),
                    prepared.jobId(),
                    prepared.coverLetterId(),
                    prepared.retryOfResearchRunId(),
                    prepared.researchQuality(),
                    proposedId,
                    queuedAt);
            store.createQuestionSet(
                    prepared.questionSetId(),
                    prepared.userId(),
                    prepared.jobId(),
                    prepared.coverLetterId(),
                    prepared.researchRunId(),
                    service.questionSetTitle(prepared.context()),
                    service.generationConfig(prepared),
                    proposedId,
                    queuedAt);
            store.attachPrimaryQuestionSetRunLink(
                    prepared.userId(), proposedId, prepared.questionSetId(), queuedAt);
            researchStore.attachSecondaryRunLink(
                    prepared.userId(), proposedId, prepared.researchRunId(), queuedAt);
            return runQuery.findByOwner(prepared.userId(), proposedId).orElseThrow();
        }
        UUID existingId = store.findVisibleRetrySuccessorId(
                        predecessor.userId(), predecessor.id())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED));
        AgentRunSnapshot existing =
                runQuery.findByOwner(predecessor.userId(), existingId).orElseThrow();
        String existingResearchQuality =
                existing.inputReferenceSnapshot().path("researchQuality").asText();
        if (existing.workflowType() != WorkflowType.INTERVIEW_PREPARATION
                || existing.requestedQualityMode() != qualityMode
                || !researchQuality.name().equals(existingResearchQuality)
                || !InterviewApplicationService.QUESTION_SET_RESOURCE.equals(
                        existing.resourceType())) {
            throw new BusinessException(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED);
        }
        return existing;
    }

    private ResearchQuality parseResearchQuality(
            String requested, ResearchQuality fallback) {
        if (requested == null) {
            return fallback;
        }
        try {
            return ResearchQuality.valueOf(requested);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception);
        }
    }

    private List<InterviewQuestionType> questionTypes(JsonNode input) {
        JsonNode values = input.path("questionTypes");
        if (!values.isArray() || values.isEmpty() || values.size() > 7) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        List<InterviewQuestionType> types = new ArrayList<>();
        for (JsonNode value : values) {
            try {
                InterviewQuestionType type = InterviewQuestionType.valueOf(value.asText());
                if (type == InterviewQuestionType.FOLLOW_UP) {
                    throw new IllegalArgumentException("FOLLOW_UP request type is forbidden");
                }
                types.add(type);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT, exception);
            }
        }
        if (new HashSet<>(types).size() != types.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return List.copyOf(types);
    }
}
