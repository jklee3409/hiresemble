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
            Duration timeout,
            Long priceVersion,
            int maxOutputTokens,
            Class<?> outputType,
            String reasoningEffort,
            String verbosity) {
        public ChatRequest {
            allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
            if (providerKey == null || providerKey.isBlank() || productKey == null || productKey.isBlank()
                    || promptVersion == null || promptVersion.isBlank() || instructions == null
                    || input == null || outputSchemaVersion == null || outputSchemaVersion.isBlank()
                    || maxToolCalls < 0 || timeout == null || timeout.isNegative() || timeout.isZero()
                    || maxOutputTokens < 1
                    || (reasoningEffort != null && !Set.of("minimal", "low", "medium", "high").contains(reasoningEffort))
                    || (verbosity != null && !Set.of("low", "medium", "high").contains(verbosity))) {
                throw new IllegalArgumentException("chat request is invalid");
            }
        }

        public ChatRequest(
                String providerKey,
                String productKey,
                String promptVersion,
                String instructions,
                JsonNode input,
                String outputSchemaVersion,
                Set<String> allowedTools,
                int maxToolCalls,
                Duration timeout,
                Long priceVersion,
                int maxOutputTokens,
                Class<?> outputType) {
            this(
                    providerKey, productKey, promptVersion, instructions, input,
                    outputSchemaVersion, allowedTools, maxToolCalls, timeout, priceVersion,
                    maxOutputTokens, outputType, null, null);
        }

        public ChatRequest(
                String providerKey,
                String productKey,
                String promptVersion,
                String instructions,
                JsonNode input,
                String outputSchemaVersion,
                Set<String> allowedTools,
                int maxToolCalls,
                Duration timeout) {
            this(
                    providerKey,
                    productKey,
                    promptVersion,
                    instructions,
                    input,
                    outputSchemaVersion,
                    allowedTools,
                    maxToolCalls,
                    timeout,
                    null,
                    4096,
                    null,
                    null,
                    null);
        }
    }
}
