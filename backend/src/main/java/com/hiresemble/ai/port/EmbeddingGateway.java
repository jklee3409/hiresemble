package com.hiresemble.ai.port;

import java.time.Duration;
import java.util.List;

public interface EmbeddingGateway {
    AiGatewayResponse embed(EmbeddingRequest request);

    record EmbeddingRequest(
            String providerKey,
            String productKey,
            List<String> maskedInputs,
            int dimension,
            Duration timeout) {
        public EmbeddingRequest {
            maskedInputs = maskedInputs == null ? List.of() : List.copyOf(maskedInputs);
            if (providerKey == null || providerKey.isBlank() || productKey == null || productKey.isBlank()
                    || maskedInputs.isEmpty() || dimension < 1 || timeout == null
                    || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("embedding request is invalid");
            }
        }
    }
}
