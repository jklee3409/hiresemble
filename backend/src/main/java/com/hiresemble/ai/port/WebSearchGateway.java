package com.hiresemble.ai.port;

import java.time.Duration;
import java.util.List;

public interface WebSearchGateway {
    AiGatewayResponse search(SearchRequest request);

    record SearchRequest(
            String providerKey,
            String productKey,
            List<String> queries,
            String researchQuality,
            int maxResultsPerQuery,
            Duration timeout) {
        public SearchRequest {
            queries = queries == null ? List.of() : List.copyOf(queries);
            if (providerKey == null || providerKey.isBlank() || productKey == null || productKey.isBlank()
                    || queries.isEmpty() || researchQuality == null || researchQuality.isBlank()
                    || maxResultsPerQuery < 1 || maxResultsPerQuery > 10 || timeout == null
                    || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("search request is invalid");
            }
        }
    }
}
