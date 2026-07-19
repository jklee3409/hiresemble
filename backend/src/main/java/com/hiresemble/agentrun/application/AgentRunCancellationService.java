package com.hiresemble.agentrun.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunCancellationService implements AgentRunCancellationPort {

    private final AgentRunQueryPort queryPort;
    private final AgentRunStatePort statePort;
    private final BudgetReservationPort budgetPort;
    private final ObjectProvider<ResourceCompensationPort> compensationPorts;

    public AgentRunCancellationService(
            AgentRunQueryPort queryPort,
            AgentRunStatePort statePort,
            BudgetReservationPort budgetPort,
            ObjectProvider<ResourceCompensationPort> compensationPorts) {
        this.queryPort = queryPort;
        this.statePort = statePort;
        this.budgetPort = budgetPort;
        this.compensationPorts = compensationPorts;
    }

    @Override
    @Transactional
    public AgentRunSnapshot requestCancellation(
            UUID userId,
            UUID agentRunId,
            long expectedStateVersion,
            Instant requestedAt) {
        AgentRunSnapshot before = queryPort.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!before.cancellable()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AgentRunSnapshot requested = statePort.markCancellationRequested(
                userId, agentRunId, expectedStateVersion, requestedAt);
        if (requested.status() == AgentRunStatus.RUNNING) {
            return requested;
        }
        if (requested.status() == AgentRunStatus.QUEUED) {
            budgetPort.releaseUnused(userId, agentRunId, requestedAt);
        }
        compensate(requested);
        return statePort.cancelUnclaimed(userId, agentRunId, requestedAt);
    }

    @Override
    @Transactional
    public AgentRunSnapshot completeCancellation(
            UUID userId, UUID agentRunId, UUID claimToken, Instant completedAt) {
        AgentRunSnapshot running = queryPort.findByOwner(userId, agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (running.status() != AgentRunStatus.RUNNING
                || running.cancelRequestedAt() == null
                || !claimToken.equals(running.claimToken())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        budgetPort.releaseUnused(userId, agentRunId, completedAt);
        compensate(running);
        return statePort.cancelClaimed(userId, agentRunId, claimToken, completedAt);
    }

    private void compensate(AgentRunSnapshot run) {
        if (run.resourceType() == null) {
            return;
        }
        ResourceCompensationPort compensationPort = compensationPorts.getIfAvailable();
        if (compensationPort == null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        compensationPort.compensate(
                run.userId(), run.id(), run.resourceType(), run.resourceId());
    }
}
