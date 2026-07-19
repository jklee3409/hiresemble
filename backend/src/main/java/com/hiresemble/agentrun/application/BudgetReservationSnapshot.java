package com.hiresemble.agentrun.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetReservationSnapshot(
        UUID reservationId,
        long budgetPolicyVersion,
        Long priceVersion,
        LocalDate budgetDate,
        BigDecimal reservedUsd,
        BigDecimal settledUsd,
        String status) {}
