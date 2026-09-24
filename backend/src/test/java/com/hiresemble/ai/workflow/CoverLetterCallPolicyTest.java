package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.domain.model.ModelTier;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CoverLetterCallPolicyTest {

    @Test
    void writingStepsScaleReasoningAndTimeoutWithModelTier() {
        assertThat(CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.DRAFT_ANSWER, ModelTier.LOW_COST))
                .isEqualTo(new CoverLetterCallPolicy.CallProfile(Duration.ofSeconds(90), "low"));
        assertThat(CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.REVIEW_ANSWER, ModelTier.BALANCED))
                .isEqualTo(new CoverLetterCallPolicy.CallProfile(Duration.ofSeconds(150), "medium"));
        assertThat(CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.DRAFT_ANSWER, ModelTier.HIGH_QUALITY))
                .isEqualTo(new CoverLetterCallPolicy.CallProfile(Duration.ofSeconds(180), "high"));
    }

    @Test
    void planningAndMechanicalStepsStayBounded() {
        assertThat(CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.PLAN_QUESTIONS, ModelTier.HIGH_QUALITY))
                .isEqualTo(new CoverLetterCallPolicy.CallProfile(Duration.ofSeconds(120), "medium"));
        assertThat(CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.WRITE_ANSWER, ModelTier.HIGH_QUALITY))
                .isEqualTo(new CoverLetterCallPolicy.CallProfile(Duration.ofSeconds(90), "low"));
        assertThat(CoverLetterCallPolicy.v5(CoverLetterGenerationWorkflow.FACT_CHECK_ANSWER, null))
                .isEqualTo(new CoverLetterCallPolicy.CallProfile(Duration.ofSeconds(90), "low"));
    }
}
