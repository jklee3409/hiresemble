package com.hiresemble.agentrun.application;

import java.util.List;

public record AgentRunPage(
        List<AgentRunSnapshot> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public AgentRunPage {
        items = List.copyOf(items);
    }
}
