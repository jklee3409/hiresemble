package com.hiresemble.agentrun.application.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ReusableStepSnapshot(
        UUID stepId,
        String outputHash,
        JsonNode minimalOutput,
        AiQualityMode requestedQualityMode,
        ModelTier modelTierUsed) {}
