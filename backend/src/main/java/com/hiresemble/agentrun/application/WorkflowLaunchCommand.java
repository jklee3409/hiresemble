package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ResourceReference;
import com.hiresemble.agentrun.domain.WorkflowType;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

public record WorkflowLaunchCommand(
        UUID userId,
        WorkflowType workflowType,
        String workflowVersion,
        String canonicalInputHash,
        JsonNode inputReferenceSnapshot,
        AiQualityMode requestedQualityMode,
        BigDecimal estimatedCostUsd,
        Long priceVersion,
        ResourceReference resource) {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public WorkflowLaunchCommand {
        if (userId == null || workflowType == null || workflowVersion == null
                || workflowVersion.isBlank() || workflowVersion.length() > 50
                || canonicalInputHash == null || !HASH.matcher(canonicalInputHash).matches()
                || inputReferenceSnapshot == null || !inputReferenceSnapshot.isObject()
                || estimatedCostUsd == null || estimatedCostUsd.signum() < 0
                || (estimatedCostUsd.signum() > 0 && priceVersion == null)) {
            throw new IllegalArgumentException("workflow launch command is invalid");
        }
    }
}
