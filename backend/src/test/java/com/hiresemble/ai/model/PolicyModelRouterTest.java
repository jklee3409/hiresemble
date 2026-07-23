package com.hiresemble.ai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.ModelRouter.ModelPolicy;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.model.ModelRouter.RoutingRequest;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyModelRouterTest {

    private final ModelPolicy policy = new ModelPolicy(
            41, true, "fixture-provider", "low", "balanced", "high",
            Set.of(WorkflowType.COVER_LETTER_GENERATION,
                    WorkflowType.COVER_LETTER_VERIFICATION,
                    WorkflowType.INTERVIEW_ANSWER_FEEDBACK));

    @Test
    void economyPromotesLowCostToBalancedOnlyOnceForStructuredFailure() {
        PolicyModelRouter router = new PolicyModelRouter(policy);
        ModelRoute first = router.route(request(AiQualityMode.ECONOMY, 1, null, null));
        ModelRoute promoted = router.route(request(
                AiQualityMode.ECONOMY, 2, ModelTier.LOW_COST, FailureKind.STRUCTURED_OUTPUT));
        ModelRoute networkRetry = router.route(request(
                AiQualityMode.ECONOMY, 2, ModelTier.LOW_COST, FailureKind.NETWORK));
        ModelRoute afterPromotion = router.route(request(
                AiQualityMode.ECONOMY, 3, ModelTier.BALANCED, FailureKind.STRUCTURED_OUTPUT));

        assertThat(first.tier()).isEqualTo(ModelTier.LOW_COST);
        assertThat(promoted.tier()).isEqualTo(ModelTier.BALANCED);
        assertThat(promoted.promoted()).isTrue();
        assertThat(networkRetry.tier()).isEqualTo(ModelTier.LOW_COST);
        assertThat(afterPromotion.tier()).isEqualTo(ModelTier.BALANCED);
        assertThat(afterPromotion.promoted()).isFalse();
    }

    @Test
    void highQualityRequiresPreferenceExplicitWorkflowAndReservationAndNeverAutoPromotes() {
        PolicyModelRouter router = new PolicyModelRouter(policy);
        ModelRoute route = router.route(new RoutingRequest(
                WorkflowType.COVER_LETTER_GENERATION, "WRITE_ANSWER", AiQualityMode.HIGH_QUALITY,
                ModelTier.HIGH_QUALITY, true, true, true, 2,
                ModelTier.BALANCED, FailureKind.STRUCTURED_OUTPUT));

        assertThat(route.tier()).isEqualTo(ModelTier.HIGH_QUALITY);
        assertThat(route.promoted()).isFalse();
        assertThatThrownBy(() -> router.route(new RoutingRequest(
                WorkflowType.JOB_ANALYSIS, "SCORE_FIT", AiQualityMode.HIGH_QUALITY,
                ModelTier.HIGH_QUALITY, true, true, true, 1, null, null)))
                .isInstanceOf(AiExecutionException.class)
                .extracting(error -> ((AiExecutionException) error).failureKind())
                .isEqualTo(FailureKind.REQUEST_VALIDATION);
    }

    @Test
    void disabledProviderFailsSafelyWithoutFallbackButDeterministicStepCanRoute() {
        PolicyModelRouter router = new PolicyModelRouter(ModelPolicy.disabled(7));
        assertThatThrownBy(() -> router.route(request(AiQualityMode.ECONOMY, 1, null, null)))
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.safeCode()).isEqualTo("AI_PROVIDER_DISABLED");
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.CONFIGURATION);
                    assertThat(failure.retryable()).isFalse();
                });

        ModelRoute deterministic = router.route(new RoutingRequest(
                WorkflowType.JOB_ANALYSIS, "LOAD_FIXTURE", AiQualityMode.ECONOMY,
                ModelTier.LOW_COST, false, false, true, 1, null, null));
        assertThat(deterministic.providerKey()).isEqualTo("none");
    }

    private RoutingRequest request(
            AiQualityMode quality, int attempt, ModelTier previous, FailureKind failure) {
        return new RoutingRequest(
                WorkflowType.COVER_LETTER_GENERATION, "WRITE_ANSWER", quality,
                ModelTier.BALANCED, true, true, true, attempt, previous, failure);
    }
}
