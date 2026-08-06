package com.hiresemble.ai.budget;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import java.math.BigDecimal;

/** Computes the maximum provider charge that must be reserved before one bounded step call. */
@FunctionalInterface
public interface AiCallCostEstimator {

    BigDecimal maximumCallCost(
            AgentRunSnapshot run,
            ModelRoute route,
            PromptDefinition prompt,
            StepInput input);
}
