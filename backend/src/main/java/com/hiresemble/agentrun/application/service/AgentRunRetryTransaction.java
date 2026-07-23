package com.hiresemble.agentrun.application.service;

import com.hiresemble.agentrun.application.command.BudgetReservationRequest;
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
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AgentRunRetryTransaction {

    private final AgentRunQueryPort queryPort;
    private final AgentRunCreationPort creationPort;
    private final BudgetReservationPort budgetPort;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunDispatchPort dispatchPort;
    private final Clock clock;

    public AgentRunRetryTransaction(
            AgentRunQueryPort queryPort,
            AgentRunCreationPort creationPort,
            BudgetReservationPort budgetPort,
            AgentRunEventPublisher eventPublisher,
            AgentRunDispatchPort dispatchPort,
            Clock clock) {
        this.queryPort = queryPort;
        this.creationPort = creationPort;
        this.budgetPort = budgetPort;
        this.eventPublisher = eventPublisher;
        this.dispatchPort = dispatchPort;
        this.clock = clock;
    }

    @Transactional
    public WorkflowLaunchResult retry(UUID userId, UUID predecessorRunId) {
        AgentRunSnapshot predecessor = queryPort.findByOwner(userId, predecessorRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if ((predecessor.status() != AgentRunStatus.FAILED
                        && predecessor.status() != AgentRunStatus.INTERRUPTED)
                || !predecessor.retryable()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Instant now = clock.instant();
        BudgetPolicySnapshot policy = budgetPort.activePolicy(userId);
        UUID proposedId = UUID.randomUUID();
        AgentRunSnapshot successor = creationPort.createRetry(
                proposedId, predecessor, policy.version(), now);
        boolean created = successor.id().equals(proposedId);
        if (created) {
            budgetPort.reserve(new BudgetReservationRequest(
                    userId, successor.id(), successor.workflowType().name(),
                    successor.estimatedCostUsd(), successor.priceVersion(), now));
            successor = queryPort.findByOwner(userId, successor.id()).orElseThrow();
            eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                    AgentRunEventType.SNAPSHOT, successor, null, now));
            UUID dispatchId = successor.id();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchPort.enqueue(dispatchId);
                }
            });
        }
        return new WorkflowLaunchResult(
                successor.id(), successor.status(), successor.resourceType(), successor.resourceId(), false);
    }
}
