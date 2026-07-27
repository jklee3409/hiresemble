package com.hiresemble.job.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.job-page-fetch")
public class JobPageFetchProperties {

    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(10);
    private int maxRedirects = 5;
    private int maxResponseBytes = 2 * 1024 * 1024;

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public void validate() {
        if (connectTimeout == null
                || connectTimeout.isNegative()
                || connectTimeout.isZero()
                || responseTimeout == null
                || responseTimeout.isNegative()
                || responseTimeout.isZero()
                || maxRedirects < 0
                || maxRedirects > 10
                || maxResponseBytes < 1024
                || maxResponseBytes > 10 * 1024 * 1024) {
            throw new IllegalStateException("job page fetch limits are invalid");
        }
    }
}
