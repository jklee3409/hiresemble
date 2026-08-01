package com.hiresemble.ai.port;

import java.util.List;

public record AiGatewayResponse(String rawJson, List<AiUsage> usages) {
    public AiGatewayResponse {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("gateway response is empty");
        }
        usages = usages == null ? List.of() : List.copyOf(usages);
    }

    public AiGatewayResponse(String rawJson, AiUsage usage) {
        this(rawJson, usage == null ? List.of() : List.of(usage));
    }

    /** Compatibility accessor for single-usage Fake fixtures. */
    public AiUsage usage() {
        return usages.isEmpty() ? null : usages.getFirst();
    }
}
