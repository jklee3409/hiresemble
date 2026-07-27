package com.hiresemble.job.infrastructure;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Versioned cost estimate reserved before a Job extraction run is accepted.
 * The disabled-provider default is zero and therefore has no price catalog version.
 */
@ConfigurationProperties("hiresemble.job.ai-cost")
public class JobAiCostProperties {

    private BigDecimal estimatedCostUsd = BigDecimal.ZERO.setScale(6);
    private long priceVersion;

    public BigDecimal estimatedCostUsd() {
        validate();
        return estimatedCostUsd;
    }

    public Long priceVersion() {
        validate();
        return estimatedCostUsd.signum() == 0 ? null : priceVersion;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public void setPriceVersion(long priceVersion) {
        this.priceVersion = priceVersion;
    }

    private void validate() {
        if (estimatedCostUsd == null
                || estimatedCostUsd.signum() < 0
                || estimatedCostUsd.scale() > 6
                || (estimatedCostUsd.signum() > 0 && priceVersion < 1)
                || (estimatedCostUsd.signum() == 0 && priceVersion != 0)) {
            throw new IllegalStateException(
                    "Job AI cost estimate and immutable price version are inconsistent");
        }
    }
}
