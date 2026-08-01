package com.hiresemble.job.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.job-page-fetch")
public class JobPageFetchProperties {

    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(10);
    private int maxRedirects = 5;
    private int maxResponseBytes = 2 * 1024 * 1024;
    private Duration imageResponseTimeout = Duration.ofSeconds(15);
    private int maxImageCandidates = 6;
    private int maxImageBytes = 5 * 1024 * 1024;
    private int maxTotalImageBytes = 20 * 1024 * 1024;
    private long maxImagePixels = 40_000_000L;
    private int minDomMeaningfulCharacters = 600;
    private int minDescriptionMeaningfulCharacters = 120;
    private int minImageItemMeaningfulCharacters = 20;
    private double maxReplacementCharacterRatio = 0.001d;

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

    public Duration getImageResponseTimeout() { return imageResponseTimeout; }
    public void setImageResponseTimeout(Duration value) { imageResponseTimeout = value; }
    public int getMaxImageCandidates() { return maxImageCandidates; }
    public void setMaxImageCandidates(int value) { maxImageCandidates = value; }
    public int getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(int value) { maxImageBytes = value; }
    public int getMaxTotalImageBytes() { return maxTotalImageBytes; }
    public void setMaxTotalImageBytes(int value) { maxTotalImageBytes = value; }
    public long getMaxImagePixels() { return maxImagePixels; }
    public void setMaxImagePixels(long value) { maxImagePixels = value; }
    public int getMinDomMeaningfulCharacters() { return minDomMeaningfulCharacters; }
    public void setMinDomMeaningfulCharacters(int value) { minDomMeaningfulCharacters = value; }
    public int getMinDescriptionMeaningfulCharacters() { return minDescriptionMeaningfulCharacters; }
    public void setMinDescriptionMeaningfulCharacters(int value) { minDescriptionMeaningfulCharacters = value; }
    public int getMinImageItemMeaningfulCharacters() { return minImageItemMeaningfulCharacters; }
    public void setMinImageItemMeaningfulCharacters(int value) { minImageItemMeaningfulCharacters = value; }
    public double getMaxReplacementCharacterRatio() { return maxReplacementCharacterRatio; }
    public void setMaxReplacementCharacterRatio(double value) { maxReplacementCharacterRatio = value; }

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
                || maxResponseBytes > 10 * 1024 * 1024
                || imageResponseTimeout == null
                || imageResponseTimeout.isZero()
                || imageResponseTimeout.isNegative()
                || maxImageCandidates < 1
                || maxImageCandidates > 12
                || maxImageBytes < 1024
                || maxImageBytes > 10 * 1024 * 1024
                || maxTotalImageBytes < maxImageBytes
                || maxTotalImageBytes > 40 * 1024 * 1024
                || maxImagePixels < 1_000_000L
                || maxImagePixels > 100_000_000L
                || minDomMeaningfulCharacters < 100
                || minDomMeaningfulCharacters > 5_000
                || minDescriptionMeaningfulCharacters < 40
                || minDescriptionMeaningfulCharacters > 2_000
                || minImageItemMeaningfulCharacters < 10
                || minImageItemMeaningfulCharacters > minDescriptionMeaningfulCharacters
                || maxReplacementCharacterRatio < 0d
                || maxReplacementCharacterRatio > 0.05d) {
            throw new IllegalStateException("job page fetch limits are invalid");
        }
    }
}
