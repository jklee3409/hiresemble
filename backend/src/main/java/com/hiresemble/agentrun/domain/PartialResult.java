package com.hiresemble.agentrun.domain;

import java.util.List;

public record PartialResult(
        List<String> succeededScopeKeys,
        List<String> failedScopeKeys,
        List<ResourceReference> resultRefs) {

    public PartialResult {
        succeededScopeKeys = validatedKeys(succeededScopeKeys, "succeeded scope keys");
        failedScopeKeys = validatedKeys(failedScopeKeys, "failed scope keys");
        resultRefs = resultRefs == null ? List.of() : List.copyOf(resultRefs);
        if (resultRefs.size() > 200) {
            throw new IllegalArgumentException("result refs exceed the limit");
        }
    }

    private static List<String> validatedKeys(List<String> values, String name) {
        List<String> copy = values == null ? List.of() : List.copyOf(values);
        if (copy.size() > 100 || copy.stream().anyMatch(value -> value == null
                || value.isBlank() || value.length() > 100)) {
            throw new IllegalArgumentException(name + " are invalid");
        }
        return copy;
    }
}
