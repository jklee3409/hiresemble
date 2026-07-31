package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AiPreferenceQueryPort;
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
import com.hiresemble.interview.application.port.InterviewWorkflowQueryPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Body-free projection for feedback bound to one immutable answer version. */
public final class InterviewFeedbackContextBuilder implements ContextBuilder {

    private final InterviewWorkflowQueryPort queryPort;
    private final AiPreferenceQueryPort preferenceQueryPort;
    private final long modelPolicyVersion;

    public InterviewFeedbackContextBuilder(
            InterviewWorkflowQueryPort queryPort,
            AiPreferenceQueryPort preferenceQueryPort,
            long modelPolicyVersion) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.preferenceQueryPort = Objects.requireNonNull(preferenceQueryPort);
        if (modelPolicyVersion < 1) {
            throw new IllegalArgumentException("model policy is invalid");
        }
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.INTERVIEW_ANSWER_FEEDBACK
                || !CanonicalWorkflowDefinitions.INTERVIEW_ANSWER_FEEDBACK_VERSION.equals(
                        run.workflowVersion())
                || !"INTERVIEW_ANSWER_VERSION".equals(run.resourceType())
                || run.resourceId() == null
                || run.requestedQualityMode() == null) {
            throw configurationFailure();
        }
        JsonNode input = run.inputReferenceSnapshot();
        try {
            UUID answerVersionId =
                    UUID.fromString(input.path("answerVersionId").asText());
            String contextHash = input.path("contextHash").asText();
            if (!answerVersionId.equals(run.resourceId())
                    || !contextHash.matches("[0-9a-f]{64}")) {
                throw ownerFailure();
            }
            var context = queryPort.loadFeedbackContext(
                    run.userId(), answerVersionId, contextHash);
            var preference = preferenceQueryPort.activePreference(run.userId());
            return new ContextSnapshot(
                    run.userId(),
                    List.of(new ResourceSnapshotRef(
                            "INTERVIEW_ANSWER_VERSION",
                            answerVersionId,
                            0,
                            contextHash)),
                    List.of(),
                    List.of(new ContextRef(
                            "INTERVIEW_QUESTION",
                            context.questionId(),
                            null,
                            "IMMUTABLE_ANSWER_CONTEXT")),
                    new TruncationSummary(1, 0, List.of()),
                    contextHash,
                    "IMMUTABLE_ANSWER_VERSION",
                    modelPolicyVersion,
                    preference.highQualityEnabled(),
                    budgetConfirmed(run));
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (BusinessException exception) {
            throw AiExecutionException.nonRetryable(
                    exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND
                            ? FailureKind.OWNER
                            : FailureKind.DOMAIN_VALIDATION,
                    exception.errorCode().code(),
                    exception.errorCode().defaultMessage());
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private boolean budgetConfirmed(AgentRunSnapshot run) {
        return run.reservedCostUsd() != null && run.reservedCostUsd().signum() >= 0;
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
                "면접 답변 피드백 AI 실행 구성이 준비되지 않았습니다.");
    }
}
