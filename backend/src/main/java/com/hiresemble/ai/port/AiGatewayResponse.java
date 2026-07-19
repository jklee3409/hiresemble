package com.hiresemble.ai.port;

public record AiGatewayResponse(String rawJson, AiUsage usage) {
    public AiGatewayResponse {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("gateway response is empty");
        }
    }
}
