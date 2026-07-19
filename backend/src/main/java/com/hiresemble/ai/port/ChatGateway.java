package com.hiresemble.ai.port;

import java.time.Duration;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public interface ChatGateway {
    AiGatewayResponse chat(ChatRequest request);

    record ChatRequest(
            String providerKey,
            String productKey,
            String promptVersion,
            String instructions,
            JsonNode input,
            String outputSchemaVersion,
            Set<String> allowedTools,
            int maxToolCalls,
            Duration timeout) {
        public ChatRequest {
            allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
            if (providerKey == null || providerKey.isBlank() || productKey == null || productKey.isBlank()
                    || promptVersion == null || promptVersion.isBlank() || instructions == null
                    || input == null || outputSchemaVersion == null || outputSchemaVersion.isBlank()
                    || maxToolCalls < 0 || timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("chat request is invalid");
            }
        }
    }
}
