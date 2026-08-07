package com.hiresemble.githubsource.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.github")
public class GitHubProperties implements InitializingBean {

    private boolean enabled;
    private URI apiBaseUrl = URI.create("https://api.github.com");
    private String apiVersion = "2026-03-10";
    private String retrievalPolicyVersion = "github-snapshot-v1";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(10);
    private int maxResponseBytes = 8 * 1024 * 1024;
    private int maxConcurrentRequests = 2;
    private int maxDiscoveredRepositories = 200;
    private int maxSelectedRepositories = 10;
    private int maxCandidateFiles = 80;
    private int maxTextFileBytes = 64 * 1024;
    private int maxSanitizedCodePoints = 400_000;
    private int maxCandidatesPerRepository = 12;
    private int maxCandidatesPerRun = 40;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(URI apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getRetrievalPolicyVersion() {
        return retrievalPolicyVersion;
    }

    public void setRetrievalPolicyVersion(String retrievalPolicyVersion) {
        this.retrievalPolicyVersion = retrievalPolicyVersion;
    }

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

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getMaxDiscoveredRepositories() {
        return maxDiscoveredRepositories;
    }

    public void setMaxDiscoveredRepositories(int maxDiscoveredRepositories) {
        this.maxDiscoveredRepositories = maxDiscoveredRepositories;
    }

    public int getMaxSelectedRepositories() {
        return maxSelectedRepositories;
    }

    public void setMaxSelectedRepositories(int maxSelectedRepositories) {
        this.maxSelectedRepositories = maxSelectedRepositories;
    }

    public int getMaxCandidateFiles() {
        return maxCandidateFiles;
    }

    public void setMaxCandidateFiles(int maxCandidateFiles) {
        this.maxCandidateFiles = maxCandidateFiles;
    }

    public int getMaxTextFileBytes() {
        return maxTextFileBytes;
    }

    public void setMaxTextFileBytes(int maxTextFileBytes) {
        this.maxTextFileBytes = maxTextFileBytes;
    }

    public int getMaxSanitizedCodePoints() {
        return maxSanitizedCodePoints;
    }

    public void setMaxSanitizedCodePoints(int maxSanitizedCodePoints) {
        this.maxSanitizedCodePoints = maxSanitizedCodePoints;
    }

    public int getMaxCandidatesPerRepository() {
        return maxCandidatesPerRepository;
    }

    public void setMaxCandidatesPerRepository(int maxCandidatesPerRepository) {
        this.maxCandidatesPerRepository = maxCandidatesPerRepository;
    }

    public int getMaxCandidatesPerRun() {
        return maxCandidatesPerRun;
    }

    public void setMaxCandidatesPerRun(int maxCandidatesPerRun) {
        this.maxCandidatesPerRun = maxCandidatesPerRun;
    }

    @Override
    public void afterPropertiesSet() {
        if (apiBaseUrl == null
                || !"https".equalsIgnoreCase(apiBaseUrl.getScheme())
                || !"api.github.com".equalsIgnoreCase(apiBaseUrl.getHost())
                || apiBaseUrl.getPort() != -1
                || (apiBaseUrl.getPath() != null && !apiBaseUrl.getPath().isEmpty())
                || apiBaseUrl.getUserInfo() != null
                || apiBaseUrl.getQuery() != null
                || apiBaseUrl.getFragment() != null
                || apiVersion == null
                || !apiVersion.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || retrievalPolicyVersion == null
                || !retrievalPolicyVersion.matches("[A-Za-z0-9._-]{1,80}")
                || !positive(connectTimeout)
                || !positive(responseTimeout)
                || maxResponseBytes < 1024
                || maxResponseBytes > 10 * 1024 * 1024
                || maxConcurrentRequests < 1
                || maxConcurrentRequests > 8
                || maxDiscoveredRepositories != 200
                || maxSelectedRepositories != 10
                || maxCandidateFiles != 80
                || maxTextFileBytes != 64 * 1024
                || maxSanitizedCodePoints != 400_000
                || maxCandidatesPerRepository != 12
                || maxCandidatesPerRun != 40) {
            throw new IllegalStateException("GitHub ingestion configuration is invalid");
        }
    }

    private boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }
}
