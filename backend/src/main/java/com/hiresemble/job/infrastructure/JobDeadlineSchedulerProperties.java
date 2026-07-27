package com.hiresemble.job.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiresemble.job-deadline-scheduler")
public final class JobDeadlineSchedulerProperties {

    private int batchSize = 100;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("job scheduler batch size is invalid");
        }
        this.batchSize = batchSize;
    }
}
