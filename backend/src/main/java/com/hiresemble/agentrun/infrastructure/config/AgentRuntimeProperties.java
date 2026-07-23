package com.hiresemble.agentrun.infrastructure.config;

import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiresemble.agent-runtime")
@Validated
public class AgentRuntimeProperties {

    private Duration heartbeatInterval = Duration.ofSeconds(15);
    private Duration leaseDuration = Duration.ofSeconds(60);
    private Duration reconciliationInterval = Duration.ofSeconds(30);
    private Duration dispatchInterval = Duration.ofSeconds(1);
    @Min(1) private int workerThreads = 2;
    @Min(1) private int queueCapacity = 32;
    @Min(1) private int reconciliationBatchSize = 50;
    @NotBlank private String workerId = "local-worker";

    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration value) { heartbeatInterval = value; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration value) { leaseDuration = value; }
    public Duration getReconciliationInterval() { return reconciliationInterval; }
    public void setReconciliationInterval(Duration value) { reconciliationInterval = value; }
    public Duration getDispatchInterval() { return dispatchInterval; }
    public void setDispatchInterval(Duration value) { dispatchInterval = value; }
    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int value) { workerThreads = value; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { queueCapacity = value; }
    public int getReconciliationBatchSize() { return reconciliationBatchSize; }
    public void setReconciliationBatchSize(int value) { reconciliationBatchSize = value; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String value) { workerId = value; }

    @AssertTrue(message = "Agent runtime durations must be positive and heartbeat must be shorter than lease")
    public boolean isDurationConfigurationValid() {
        return positive(heartbeatInterval)
                && positive(leaseDuration)
                && positive(reconciliationInterval)
                && positive(dispatchInterval)
                && heartbeatInterval.compareTo(leaseDuration) < 0;
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
