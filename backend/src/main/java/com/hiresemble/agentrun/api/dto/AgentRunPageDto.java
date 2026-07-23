package com.hiresemble.agentrun.api.dto;

import java.util.List;

public record AgentRunPageDto(
        List<AgentRunSummaryDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public AgentRunPageDto {
        items = List.copyOf(items);
    }
}
