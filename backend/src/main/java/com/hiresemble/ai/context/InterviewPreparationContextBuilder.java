package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.context.ContextBuilder.ContextRef;
import com.hiresemble.ai.context.ContextBuilder.ContextRequest;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.context.ContextBuilder.ResourceSnapshotRef;
import com.hiresemble.ai.context.ContextBuilder.TruncationSummary;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.interview.application.model.InterviewModels.PreparationContext;
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Owner-scoped, body-free provenance projection for interview preparation. */
public final class InterviewPreparationContextBuilder implements ContextBuilder {

    private final InterviewWorkflowQueryPort queryPort;
    private final long modelPolicyVersion;

    public InterviewPreparationContextBuilder(
            InterviewWorkflowQueryPort queryPort, long modelPolicyVersion) {
        this.queryPort = Objects.requireNonNull(queryPort);
        if (modelPolicyVersion < 1) {
            throw new IllegalArgumentException("model policy is invalid");
        }
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.INTERVIEW_PREPARATION
                || !CanonicalWorkflowDefinitions.INTERVIEW_PREPARATION_VERSION.equals(
                        run.workflowVersion())
                || !"QUESTION_SET".equals(run.resourceType())
                || run.resourceId() == null) {
            throw configurationFailure();
        }
        JsonNode input = run.inputReferenceSnapshot();
        try {
            UUID jobId = UUID.fromString(input.path("jobId").asText());
            UUID coverLetterId = UUID.fromString(input.path("coverLetterId").asText());
            UUID researchRunId = UUID.fromString(input.path("researchRunId").asText());
            UUID questionSetId = UUID.fromString(input.path("questionSetId").asText());
            String contextHash = input.path("contextHash").asText();
            if (!questionSetId.equals(run.resourceId())
                    || !contextHash.matches("[0-9a-f]{64}")) {
                throw ownerFailure();
            }
            PreparationContext context = queryPort.loadPreparationContext(
                    run.userId(), jobId, coverLetterId, contextHash);
            var research = queryPort.researchRun(run.userId(), researchRunId);
            if (!research.agentRunId().equals(run.id())
                    || !context.jobId().equals(jobId)
                    || !context.coverLetterId().equals(coverLetterId)) {
                throw ownerFailure();
            }
            List<ContextRef> refs = new ArrayList<>();
            context.coverAnswers().forEach(answer -> refs.add(new ContextRef(
                    "COVER_LETTER_ANSWER_VERSION",
                    answer.answerVersionId(),
                    null,
                    "CURRENT")));
            context.evidence().forEach(evidence -> refs.add(new ContextRef(
                    "PROFILE_EVIDENCE", evidence.id(), null, "VERIFIED")));
            if (context.profile().finalEducation() != null) {
                refs.add(new ContextRef(
                        "STRUCTURED_FINAL_EDUCATION",
                        context.profile().finalEducation().id(),
                        null,
                        "SERVER_PROJECTION"));
            }
            return new ContextSnapshot(
                    run.userId(),
                    List.of(
                            new ResourceSnapshotRef(
                                    "JOB", jobId, context.jobVersion(), contextHash),
                            new ResourceSnapshotRef(
                                    "QUESTION_SET", questionSetId, 0, contextHash),
                            new ResourceSnapshotRef(
                                    "RESEARCH_RUN", researchRunId, 0, contextHash)),
                    List.of(),
                    refs,
                    new TruncationSummary(refs.size(), 0, List.of()),
                    contextHash,
                    "STRUCTURED_PROFILE_AND_VERIFIED_EVIDENCE",
                    modelPolicyVersion,
                    false,
                    budgetConfirmed(run));
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (BusinessException exception) {
            throw mapBusiness(exception);
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private boolean budgetConfirmed(AgentRunSnapshot run) {
        return run.reservedCostUsd() != null && run.reservedCostUsd().signum() >= 0;
    }

    private AiExecutionException mapBusiness(BusinessException exception) {
        if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            return ownerFailure();
        }
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                exception.errorCode().code(),
                exception.errorCode().defaultMessage());
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                ErrorCode.RESOURCE_NOT_FOUND.code(),
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "면접 준비 AI 실행 구성이 준비되지 않았습니다.");
    }
}
