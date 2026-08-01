package com.hiresemble.job.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.job.auto-analysis")
public class JobAutoAnalysisProperties {

    private Duration scanInterval = Duration.ofSeconds(5);
    private Duration leaseDuration = Duration.ofMinutes(1);
    private Duration retryDelay = Duration.ofSeconds(15);
    private int batchSize = 20;
    private int maxAttempts = 3;

    public Duration getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = scanInterval;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void validate() {
        if (scanInterval == null || scanInterval.isNegative() || scanInterval.isZero()
                || leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()
                || batchSize < 1 || batchSize > 100 || maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalStateException("Job automatic analysis properties are invalid");
        }
    }
}
