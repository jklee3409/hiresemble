package com.hiresemble.ai.budget;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.ai.model.ModelRouter.ModelRoute;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort.AiPriceUnit;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import java.math.BigDecimal;
import java.util.Set;

/** Price-catalog-backed preflight estimate; workflow-specific dollar constants are deliberately absent. */
public final class PriceCatalogAiCallCostEstimator implements AiCallCostEstimator {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final String OPENAI = "openai";
    private static final String TAVILY = "tavily";

    private final AiPriceCatalogQueryPort priceCatalog;
    private final String embeddingModel;

    public PriceCatalogAiCallCostEstimator(
            AiPriceCatalogQueryPort priceCatalog, String embeddingModel) {
        this.priceCatalog = java.util.Objects.requireNonNull(priceCatalog);
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embedding model is required");
        }
        this.embeddingModel = embeddingModel;
    }

    @Override
    public BigDecimal maximumCallCost(
            AgentRunSnapshot run,
            ModelRoute route,
            PromptDefinition prompt,
            StepInput input) {
        if (prompt.maxModelCalls() == 0
                || "none".equals(route.providerKey())
                || "fake".equals(route.providerKey())) {
            return ZERO;
        }
        Long priceVersion = run.priceVersion();
        if (priceVersion == null) {
            throw new IllegalStateException("Agent Run price version is missing");
        }
        Set<String> tools = prompt.toolAllowlist();
        if (tools.contains("WEB_SEARCH")) {
            int queryCount = Math.max(1, input.gatewayPayload().path("queries").size());
            return priceCatalog.requireQuote(
                            priceVersion,
                            TAVILY,
                            "advanced",
                            AiPriceUnit.SEARCH_ADVANCED_REQUEST)
                    .costFor(queryCount);
        }
        if (tools.contains("EMBEDDING")) {
            return priceCatalog.requireQuote(
                            priceVersion,
                            OPENAI,
                            embeddingModel,
                            AiPriceUnit.EMBEDDING_INPUT_TOKEN)
                    .costFor(prompt.maxInputTokens());
        }
        BigDecimal inputCost = priceCatalog.requireQuote(
                priceVersion,
                route.providerKey(),
                route.productKey(),
                AiPriceUnit.CHAT_INPUT_TOKEN)
                .costFor(prompt.maxInputTokens());
        BigDecimal outputCost = priceCatalog.requireQuote(
                priceVersion,
                route.providerKey(),
                route.productKey(),
                AiPriceUnit.CHAT_OUTPUT_TOKEN)
                .costFor(prompt.maxOutputTokens());
        return inputCost.add(outputCost);
    }
}
