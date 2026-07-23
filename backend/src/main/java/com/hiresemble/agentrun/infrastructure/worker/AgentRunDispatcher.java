package com.hiresemble.agentrun.infrastructure.worker;

import com.hiresemble.agentrun.infrastructure.config.AgentRuntimeProperties;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.WorkflowExecutionPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class AgentRunDispatcher implements AgentRunDispatchPort {

    private final AgentRunStatePort statePort;
    private final ObjectProvider<WorkflowExecutionPort> executionPorts;
    private final ThreadPoolTaskExecutor executor;
    private final AgentRuntimeProperties properties;
    private final Clock clock;

    public AgentRunDispatcher(
            AgentRunStatePort statePort,
            ObjectProvider<WorkflowExecutionPort> executionPorts,
            @Qualifier("agentWorkflowTaskExecutor") ThreadPoolTaskExecutor executor,
            AgentRuntimeProperties properties,
            Clock clock) {
        this.statePort = statePort;
        this.executionPorts = executionPorts;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void enqueue(UUID agentRunId) {
        WorkflowExecutionPort executionPort = executionPorts.getIfAvailable();
        if (executionPort == null) {
            return;
        }
        try {
            executor.execute(() -> statePort.claim(
                            agentRunId,
                            properties.getWorkerId(),
                            clock.instant(),
                            properties.getLeaseDuration())
                    .ifPresent(executionPort::execute));
        } catch (TaskRejectedException ignored) {
            // The task has not claimed the row, so reconciliation can discover the QUEUED run.
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${hiresemble.agent-runtime.dispatch-interval:1s}")
    public void scanQueued() {
        if (executionPorts.getIfAvailable() == null) {
            return;
        }
        for (UUID runId : statePort.findQueuedRunIds(properties.getReconciliationBatchSize())) {
            enqueue(runId);
        }
    }
}
