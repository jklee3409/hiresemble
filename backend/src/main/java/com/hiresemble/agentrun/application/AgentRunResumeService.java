package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AgentRunResumeService implements AgentRunResumePort {

    private final AgentRunQueryPort queryPort;
    private final AgentRunStatePort statePort;
    private final BudgetReservationPort budgetPort;
    private final AgentRunDispatchPort dispatchPort;

    public AgentRunResumeService(
            AgentRunQueryPort queryPort,
            AgentRunStatePort statePort,
            BudgetReservationPort budgetPort,
            AgentRunDispatchPort dispatchPort) {
        this.queryPort = queryPort;
        this.statePort = statePort;
        this.budgetPort = budgetPort;
        this.dispatchPort = dispatchPort;
    }

    @Override
    @Transactional
    public AgentRunSnapshot resume(
            UUID userId, UUID agentRunId, long expectedStateVersion, Instant requestedAt) {
        AgentRunSnapshot waiting = queryPort.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (waiting.status() != AgentRunStatus.WAITING_USER) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (waiting.stateVersion() != expectedStateVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        BigDecimal remaining = waiting.estimatedCostUsd().subtract(waiting.actualCostUsd()).max(BigDecimal.ZERO);
        BudgetReservationSnapshot reservation = budgetPort.reserveForResume(
                new BudgetReservationRequest(
                        userId, agentRunId, waiting.workflowType().name(), remaining,
                        waiting.priceVersion(), requestedAt));
        AgentRunSnapshot resumed = statePort.resumeWaiting(
                userId, agentRunId, expectedStateVersion, reservation.reservedUsd(), requestedAt);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchPort.enqueue(agentRunId);
            }
        });
        return resumed;
    }
}
