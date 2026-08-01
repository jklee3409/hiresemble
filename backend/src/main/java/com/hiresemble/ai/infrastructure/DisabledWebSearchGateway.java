package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.WebSearchGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.search.provider", havingValue = "none", matchIfMissing = true)
public final class DisabledWebSearchGateway implements WebSearchGateway {
    @Override
    public AiGatewayResponse search(SearchRequest request) {
        throw DisabledChatGateway.disabled();
    }
}
