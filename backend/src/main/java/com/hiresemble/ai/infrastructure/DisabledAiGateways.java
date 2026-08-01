package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;

/** Non-Bean compatibility fixture retained for focused unit tests. */
public final class DisabledAiGateways implements ChatGateway, EmbeddingGateway, WebSearchGateway {
    @Override
    public AiGatewayResponse chat(ChatRequest request) {
        throw DisabledChatGateway.disabled();
    }

    @Override
    public AiGatewayResponse embed(EmbeddingRequest request) {
        throw DisabledChatGateway.disabled();
    }

    @Override
    public AiGatewayResponse search(SearchRequest request) {
        throw DisabledChatGateway.disabled();
    }
}
