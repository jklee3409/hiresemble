package com.hiresemble.agentrun.api;

import java.util.List;

public record PartialResultDto(
        List<String> succeededScopeKeys,
        List<String> failedScopeKeys,
        List<ResourceRefDto> resultRefs) {
    public PartialResultDto {
        succeededScopeKeys = List.copyOf(succeededScopeKeys);
        failedScopeKeys = List.copyOf(failedScopeKeys);
        resultRefs = List.copyOf(resultRefs);
    }
}
