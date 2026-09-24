package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.ModelTier;
import java.time.Duration;

/**
 * Per-step provider call profile for active cover-letter generation v5. Writing steps get more
 * reasoning and time on stronger models; mechanical steps stay fast. Output-token caps live in the
 * prompt definitions because they also drive the preflight budget reservation.
 */
public final class CoverLetterCallPolicy {

    private CoverLetterCallPolicy() {}

    public record CallProfile(Duration timeout, String reasoningEffort) {}

    public static CallProfile v5(String stepKey, ModelTier tier) {
        ModelTier value = tier == null ? ModelTier.BALANCED : tier;
        return switch (stepKey) {
            case CoverLetterGenerationWorkflow.DRAFT_ANSWER,
                    CoverLetterGenerationWorkflow.REVIEW_ANSWER -> switch (value) {
                        case LOW_COST -> new CallProfile(Duration.ofSeconds(90), "low");
                        case BALANCED -> new CallProfile(Duration.ofSeconds(150), "medium");
                        case HIGH_QUALITY -> new CallProfile(Duration.ofSeconds(180), "high");
                    };
            case CoverLetterGenerationWorkflow.PLAN_QUESTIONS -> value == ModelTier.LOW_COST
                    ? new CallProfile(Duration.ofSeconds(90), "low")
                    : new CallProfile(Duration.ofSeconds(120), "medium");
            default -> new CallProfile(Duration.ofSeconds(90), "low");
        };
    }
}
