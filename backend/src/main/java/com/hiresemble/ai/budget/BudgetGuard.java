package com.hiresemble.ai.budget;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import java.math.BigDecimal;
import java.time.Instant;

/** Thin AI-side interpretation of the backend-owned atomic reservation port. */
public final class BudgetGuard {

    private final BudgetReservationPort reservationPort;

    public BudgetGuard(BudgetReservationPort reservationPort) {
        this.reservationPort = reservationPort;
    }

    public void ensureNextCallCovered(
            AgentRunSnapshot run, BigDecimal worstCaseAdditionalCost, Instant now) {
        if (worstCaseAdditionalCost == null || worstCaseAdditionalCost.signum() < 0) {
            throw new IllegalArgumentException("call cost estimate is invalid");
        }
        BigDecimal required = run.actualCostUsd().add(worstCaseAdditionalCost);
        if (required.compareTo(run.reservedCostUsd()) > 0) {
            reservationPort.topUp(run.userId(), run.id(), required.subtract(run.reservedCostUsd()), now);
        }
    }

    public void settleSuccess(AgentRunSnapshot run, Instant now) {
        reservationPort.settle(run.userId(), run.id(), run.actualCostUsd(), now);
    }

    public void releaseUnused(AgentRunSnapshot run, Instant now) {
        reservationPort.releaseUnused(run.userId(), run.id(), now);
    }
}
