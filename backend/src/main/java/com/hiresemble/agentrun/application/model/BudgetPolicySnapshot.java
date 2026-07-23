package com.hiresemble.agentrun.application.model;

import java.math.BigDecimal;
import java.time.ZoneId;

public record BudgetPolicySnapshot(
        long version,
        BigDecimal userDefaultDailyBudgetUsd,
        BigDecimal systemMaxDailyBudgetUsd,
        BigDecimal asyncRunMaxCostUsd,
        ZoneId resetZone) {}
