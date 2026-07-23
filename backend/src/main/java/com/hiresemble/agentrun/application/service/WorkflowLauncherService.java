package com.hiresemble.agentrun.application.service;

import com.hiresemble.agentrun.application.command.BudgetReservationRequest;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.model.AgentRunEventType;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.BudgetPolicySnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunCreationPort;
import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.agentrun.application.port.AgentRunEventPublisher;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WorkflowLauncherService implements WorkflowLauncher {

    private final AgentRunCreationPort creationPort;
    private final AgentRunQueryPort queryPort;
    private final BudgetReservationPort budgetPort;
    private final AgentRunDispatchPort dispatchPort;
    private final AgentRunEventPublisher eventPublisher;
    private final Clock clock;

    public WorkflowLauncherService(
            AgentRunCreationPort creationPort,
            AgentRunQueryPort queryPort,
            BudgetReservationPort budgetPort,
            AgentRunDispatchPort dispatchPort,
            AgentRunEventPublisher eventPublisher,
            Clock clock) {
        this.creationPort = creationPort;
        this.queryPort = queryPort;
        this.budgetPort = budgetPort;
        this.dispatchPort = dispatchPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WorkflowLaunchResult launch(WorkflowLaunchCommand command) {
        Instant now = clock.instant();
        BudgetPolicySnapshot policy = budgetPort.activePolicy(command.userId());
        UUID runId = UUID.randomUUID();
        creationPort.createQueued(runId, command, policy.version(), now);
        budgetPort.reserve(new BudgetReservationRequest(
                command.userId(), runId, command.workflowType().name(),
                command.estimatedCostUsd(), command.priceVersion(), now));
        AgentRunSnapshot run = queryPort.findByOwner(command.userId(), runId).orElseThrow();
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.SNAPSHOT, run, null, now));
        enqueueAfterCommit(runId);
        return new WorkflowLaunchResult(
                run.id(), run.status(), run.resourceType(), run.resourceId(), false);
    }

    private void enqueueAfterCommit(UUID runId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchPort.enqueue(runId);
            }
        });
    }
}
