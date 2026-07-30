package com.hiresemble.coverletter.infrastructure;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.cover-letter.ai-cost")
public class CoverLetterAiCostProperties {

    private BigDecimal generationEstimatedCostUsd = BigDecimal.ZERO.setScale(6);
    private long generationPriceVersion;
    private BigDecimal verificationEstimatedCostUsd = BigDecimal.ZERO.setScale(6);
    private long verificationPriceVersion;

    public BigDecimal generationEstimatedCostUsd() {
        validate(generationEstimatedCostUsd, generationPriceVersion);
        return generationEstimatedCostUsd;
    }

    public Long generationPriceVersion() {
        validate(generationEstimatedCostUsd, generationPriceVersion);
        return generationEstimatedCostUsd.signum() == 0 ? null : generationPriceVersion;
    }

    public BigDecimal verificationEstimatedCostUsd() {
        validate(verificationEstimatedCostUsd, verificationPriceVersion);
        return verificationEstimatedCostUsd;
    }

    public Long verificationPriceVersion() {
        validate(verificationEstimatedCostUsd, verificationPriceVersion);
        return verificationEstimatedCostUsd.signum() == 0 ? null : verificationPriceVersion;
    }

    public void setGenerationEstimatedCostUsd(BigDecimal value) {
        this.generationEstimatedCostUsd = value;
    }

    public void setGenerationPriceVersion(long value) {
        this.generationPriceVersion = value;
    }

    public void setVerificationEstimatedCostUsd(BigDecimal value) {
        this.verificationEstimatedCostUsd = value;
    }

    public void setVerificationPriceVersion(long value) {
        this.verificationPriceVersion = value;
    }

    private void validate(BigDecimal estimate, long priceVersion) {
        if (estimate == null
                || estimate.signum() < 0
                || estimate.scale() > 6
                || (estimate.signum() > 0 && priceVersion < 1)
                || (estimate.signum() == 0 && priceVersion != 0)) {
            throw new IllegalStateException(
                    "Cover letter AI cost estimate and immutable price version are inconsistent");
        }
    }
}
