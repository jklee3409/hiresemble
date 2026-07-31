package com.hiresemble.interview.infrastructure;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.interview.ai-cost")
public class InterviewAiCostProperties {

    private BigDecimal preparationEstimatedCostUsd = BigDecimal.ZERO.setScale(6);
    private long preparationPriceVersion;
    private BigDecimal feedbackEstimatedCostUsd = BigDecimal.ZERO.setScale(6);
    private long feedbackPriceVersion;

    public BigDecimal preparationEstimatedCostUsd() {
        validate(preparationEstimatedCostUsd, preparationPriceVersion);
        return preparationEstimatedCostUsd;
    }

    public Long preparationPriceVersion() {
        validate(preparationEstimatedCostUsd, preparationPriceVersion);
        return preparationEstimatedCostUsd.signum() == 0 ? null : preparationPriceVersion;
    }

    public BigDecimal feedbackEstimatedCostUsd() {
        validate(feedbackEstimatedCostUsd, feedbackPriceVersion);
        return feedbackEstimatedCostUsd;
    }

    public Long feedbackPriceVersion() {
        validate(feedbackEstimatedCostUsd, feedbackPriceVersion);
        return feedbackEstimatedCostUsd.signum() == 0 ? null : feedbackPriceVersion;
    }

    public void setPreparationEstimatedCostUsd(BigDecimal value) {
        this.preparationEstimatedCostUsd = value;
    }

    public void setPreparationPriceVersion(long value) {
        this.preparationPriceVersion = value;
    }

    public void setFeedbackEstimatedCostUsd(BigDecimal value) {
        this.feedbackEstimatedCostUsd = value;
    }

    public void setFeedbackPriceVersion(long value) {
        this.feedbackPriceVersion = value;
    }

    private void validate(BigDecimal estimate, long priceVersion) {
        if (estimate == null
                || estimate.signum() < 0
                || estimate.scale() > 6
                || (estimate.signum() > 0 && priceVersion < 1)
                || (estimate.signum() == 0 && priceVersion != 0)) {
            throw new IllegalStateException(
                    "Interview AI cost estimate and immutable price version are inconsistent");
        }
    }
}
