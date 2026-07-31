package com.hiresemble.agentrun.application.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import java.util.Map;

public record WorkflowRetryOptions(
        AiQualityMode qualityMode,
        Map<String, String> values) {

    public static WorkflowRetryOptions unchanged() {
        return new WorkflowRetryOptions(null, Map.of());
    }

    public WorkflowRetryOptions {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
