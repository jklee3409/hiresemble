package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ReusableStepSnapshot(
        UUID stepId,
        String outputHash,
        JsonNode minimalOutput,
        AiQualityMode requestedQualityMode,
        ModelTier modelTierUsed) {}
