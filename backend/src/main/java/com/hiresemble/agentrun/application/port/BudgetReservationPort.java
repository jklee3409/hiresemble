package com.hiresemble.agentrun.application.port;

import com.hiresemble.agentrun.application.command.BudgetReservationRequest;
import com.hiresemble.agentrun.application.model.BudgetPolicySnapshot;
import com.hiresemble.agentrun.application.model.BudgetReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface BudgetReservationPort {
    BudgetPolicySnapshot activePolicy(UUID userId);
    BudgetReservationSnapshot reserve(BudgetReservationRequest request);
    BudgetReservationSnapshot reserveForResume(BudgetReservationRequest request);
    BudgetReservationSnapshot topUp(
            UUID userId, UUID agentRunId, BigDecimal additionalUsd, Instant requestedAt);
    BudgetReservationSnapshot settle(
            UUID userId, UUID agentRunId, BigDecimal actualCostUsd, Instant settledAt);
    BudgetReservationSnapshot releaseUnused(UUID userId, UUID agentRunId, Instant releasedAt);
}
