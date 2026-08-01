package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.EmbeddingGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.ai.provider", havingValue = "none", matchIfMissing = true)
public final class DisabledEmbeddingGateway implements EmbeddingGateway {
    @Override
    public AiGatewayResponse embed(EmbeddingRequest request) {
        throw DisabledChatGateway.disabled();
    }
}
