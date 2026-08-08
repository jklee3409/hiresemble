package com.hiresemble.careerartifact.infrastructure;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.career-artifact")
public class CareerArtifactProperties implements InitializingBean {

    private boolean enabled;
    private int maxGeneratedFileBytes = 10 * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxGeneratedFileBytes() {
        return maxGeneratedFileBytes;
    }

    public void setMaxGeneratedFileBytes(int maxGeneratedFileBytes) {
        this.maxGeneratedFileBytes = maxGeneratedFileBytes;
    }

    @Override
    public void afterPropertiesSet() {
        if (maxGeneratedFileBytes < 1024 || maxGeneratedFileBytes > 10 * 1024 * 1024) {
            throw new IllegalStateException("Career Artifact renderer configuration is invalid");
        }
    }
}
