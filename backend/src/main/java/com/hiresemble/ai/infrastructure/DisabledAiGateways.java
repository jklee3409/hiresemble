package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import org.springframework.stereotype.Component;

/** Default local/production adapter. It performs no network I/O and never falls back to a provider. */
@Component
public final class DisabledAiGateways implements ChatGateway, EmbeddingGateway, WebSearchGateway {

    @Override
    public AiGatewayResponse chat(ChatRequest request) {
        throw disabled();
    }

    @Override
    public AiGatewayResponse embed(EmbeddingRequest request) {
        throw disabled();
    }

    @Override
    public AiGatewayResponse search(SearchRequest request) {
        throw disabled();
    }

    private AiExecutionException disabled() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_PROVIDER_DISABLED",
                "AI 실행 공급자가 활성화되지 않았습니다.");
    }
}
