package com.hiresemble.ai.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.EnumSet;

/** Provider-independent quality intent to internal tier routing. */
public final class PolicyModelRouter implements ModelRouter {

    private static final EnumSet<WorkflowType> HIGH_QUALITY_ALLOWED = EnumSet.of(
            WorkflowType.COVER_LETTER_GENERATION,
            WorkflowType.COVER_LETTER_VERIFICATION,
            WorkflowType.INTERVIEW_ANSWER_FEEDBACK);

    private final ModelPolicy policy;

    public PolicyModelRouter(ModelPolicy policy) {
        this.policy = policy;
    }

    @Override
    public ModelRoute route(RoutingRequest request) {
        AiQualityMode quality = request.requestedQualityMode() == null
                ? (request.workflowType() == WorkflowType.MOCK_INTERVIEW_FEEDBACK
                        ? AiQualityMode.BALANCED : AiQualityMode.ECONOMY)
                : request.requestedQualityMode();
        if (quality == AiQualityMode.HIGH_QUALITY) {
            if (!request.highQualityEnabled()
                    || !request.budgetReservationConfirmed()
                    || !HIGH_QUALITY_ALLOWED.contains(request.workflowType())
                    || !policy.highQualityWorkflowAllowlist().contains(request.workflowType())) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.REQUEST_VALIDATION,
                        "QUALITY_MODE_NOT_SUPPORTED",
                        "선택한 품질 모드를 이 작업에 사용할 수 없습니다.");
            }
        }
        if (request.providerRequired() && !policy.providerEnabled()) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_PROVIDER_DISABLED",
                    "AI 실행 공급자가 활성화되지 않았습니다.");
        }

        ModelTier tier = chooseInitialTier(quality, request.preferredTier());
        boolean promoted = false;
        if (quality == AiQualityMode.ECONOMY && request.previousTier() == ModelTier.BALANCED) {
            tier = ModelTier.BALANCED;
        } else if (quality == AiQualityMode.ECONOMY
                && request.attempt() > 1
                && request.previousTier() == ModelTier.LOW_COST
                && request.previousFailure() == FailureKind.STRUCTURED_OUTPUT) {
            tier = ModelTier.BALANCED;
            promoted = true;
        }
        if (quality == AiQualityMode.HIGH_QUALITY) {
            tier = request.preferredTier() == ModelTier.LOW_COST
                    ? ModelTier.LOW_COST : ModelTier.HIGH_QUALITY;
            promoted = false;
        }

        return new ModelRoute(
                policy.version(),
                tier,
                request.providerRequired() ? policy.providerKey() : "none",
                request.providerRequired() ? product(tier) : "none",
                promoted);
    }

    private ModelTier chooseInitialTier(AiQualityMode quality, ModelTier preferred) {
        return switch (quality) {
            case ECONOMY -> ModelTier.LOW_COST;
            case BALANCED -> preferred == ModelTier.HIGH_QUALITY ? ModelTier.BALANCED : preferred;
            case HIGH_QUALITY -> preferred == ModelTier.LOW_COST
                    ? ModelTier.LOW_COST : ModelTier.HIGH_QUALITY;
        };
    }

    private String product(ModelTier tier) {
        return switch (tier) {
            case LOW_COST -> policy.lowCostProductKey();
            case BALANCED -> policy.balancedProductKey();
            case HIGH_QUALITY -> policy.highQualityProductKey();
        };
    }
}
