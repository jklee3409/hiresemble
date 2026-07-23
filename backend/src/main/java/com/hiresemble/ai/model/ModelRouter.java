package com.hiresemble.ai.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.Objects;
import java.util.Set;

public interface ModelRouter {

    ModelRoute route(RoutingRequest request);

    record RoutingRequest(
            WorkflowType workflowType,
            String stepKey,
            AiQualityMode requestedQualityMode,
            ModelTier preferredTier,
            boolean providerRequired,
            boolean highQualityEnabled,
            boolean budgetReservationConfirmed,
            int attempt,
            ModelTier previousTier,
            FailureKind previousFailure) {
        public RoutingRequest {
            Objects.requireNonNull(workflowType, "workflowType");
            if (stepKey == null || stepKey.isBlank()) throw new IllegalArgumentException("stepKey is required");
            Objects.requireNonNull(preferredTier, "preferredTier");
            if (attempt < 1 || attempt > 3) throw new IllegalArgumentException("attempt is invalid");
        }
    }

    record ModelPolicy(
            long version,
            boolean providerEnabled,
            String providerKey,
            String lowCostProductKey,
            String balancedProductKey,
            String highQualityProductKey,
            Set<WorkflowType> highQualityWorkflowAllowlist) {
        public ModelPolicy {
            if (version < 1) throw new IllegalArgumentException("policy version is invalid");
            providerKey = providerKey == null ? "none" : providerKey;
            lowCostProductKey = lowCostProductKey == null ? "none" : lowCostProductKey;
            balancedProductKey = balancedProductKey == null ? "none" : balancedProductKey;
            highQualityProductKey = highQualityProductKey == null ? "none" : highQualityProductKey;
            highQualityWorkflowAllowlist = highQualityWorkflowAllowlist == null
                    ? Set.of() : Set.copyOf(highQualityWorkflowAllowlist);
        }

        public static ModelPolicy disabled(long version) {
            return new ModelPolicy(version, false, "none", "none", "none", "none", Set.of());
        }
    }

    record ModelRoute(
            long policyVersion,
            ModelTier tier,
            String providerKey,
            String productKey,
            boolean promoted) {
        public ModelRoute {
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(providerKey, "providerKey");
            Objects.requireNonNull(productKey, "productKey");
        }
    }
}
