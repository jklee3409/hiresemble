package com.hiresemble.agentrun.infrastructure;

import com.hiresemble.agentrun.application.BudgetPolicySnapshot;
import com.hiresemble.agentrun.application.BudgetReservationPort;
import com.hiresemble.agentrun.application.BudgetReservationRequest;
import com.hiresemble.agentrun.application.BudgetReservationSnapshot;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBudgetStore implements BudgetReservationPort {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private final JdbcClient jdbcClient;

    public JdbcBudgetStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetPolicySnapshot activePolicy(UUID userId) {
        BudgetContext context = context(userId);
        return new BudgetPolicySnapshot(
                context.policyVersion(),
                context.userDefaultDailyBudget(),
                context.systemMaxDailyBudget(),
                context.asyncRunMaxCost(),
                ZoneId.of(context.resetZone()));
    }

    @Override
    @Transactional
    public BudgetReservationSnapshot reserve(BudgetReservationRequest request) {
        return reserveInternal(request, true);
    }

    @Override
    @Transactional
    public BudgetReservationSnapshot reserveForResume(BudgetReservationRequest request) {
        return reserveInternal(request, false);
    }

    private BudgetReservationSnapshot reserveInternal(
            BudgetReservationRequest request, boolean updateRunProjection) {
        validateRequest(request);
        BudgetContext context = context(request.userId());
        BigDecimal amount = money(request.worstCaseCostUsd());
        if (amount.compareTo(context.asyncRunMaxCost()) > 0) {
            throw budgetExceeded();
        }
        requirePriceIfPaid(amount, request.priceVersion());

        LocalDate date = LocalDate.ofInstant(request.requestedAt(), ZoneId.of(context.resetZone()));
        UUID ledgerId = ensureLedger(request.userId(), date, context, request.requestedAt());
        Ledger ledger = lockLedger(request.userId(), ledgerId);
        assertAvailable(ledger, context, amount);

        jdbcClient.sql("""
                        UPDATE ai_budget_ledgers
                        SET reserved_usd = reserved_usd + :amount,
                            version = version + 1,
                            updated_at = :now
                        WHERE user_id = :userId AND id = :ledgerId
                        """)
                .param("amount", amount)
                .param("now", utc(request.requestedAt()))
                .param("userId", request.userId())
                .param("ledgerId", ledgerId)
                .update();

        UUID reservationId = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                        INSERT INTO ai_budget_reservations (
                            id, user_id, ledger_id, operation_type, agent_run_id,
                            reserved_usd, settled_usd, status, expires_at,
                            budget_policy_version, price_version, created_at, updated_at
                        ) VALUES (
                            :id, :userId, :ledgerId, :operationType, :agentRunId,
                            :amount, 0, 'RESERVED', :expiresAt,
                            :policyVersion, :priceVersion, :now, :now
                        )
                        """)
                .param("id", reservationId)
                .param("userId", request.userId())
                .param("ledgerId", ledgerId)
                .param("operationType", request.operationType())
                .param("agentRunId", request.agentRunId())
                .param("amount", amount)
                .param("expiresAt", utc(request.requestedAt().plusSeconds(86_400)))
                .param("policyVersion", context.policyVersion())
                .param("priceVersion", request.priceVersion())
                .param("now", utc(request.requestedAt()))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("budget reservation was not created");
        }
        if (updateRunProjection) {
            int runUpdated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET reserved_cost_usd = :amount, updated_at = :now
                        WHERE user_id = :userId AND id = :agentRunId AND status = 'QUEUED'
                        """)
                .param("amount", amount)
                .param("now", utc(request.requestedAt()))
                .param("userId", request.userId())
                .param("agentRunId", request.agentRunId())
                .update();
            if (runUpdated != 1) {
                throw new IllegalStateException("budget reservation lost its queued run");
            }
        }
        return new BudgetReservationSnapshot(
                reservationId, context.policyVersion(), request.priceVersion(), date,
                amount, ZERO, "RESERVED");
    }

    @Override
    @Transactional
    public BudgetReservationSnapshot topUp(
            UUID userId, UUID agentRunId, BigDecimal additionalUsd, Instant requestedAt) {
        BigDecimal amount = money(additionalUsd);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("top-up amount must be positive");
        }
        Reservation reservation = lockActiveReservation(userId, agentRunId);
        BudgetContext context = context(userId);
        Ledger ledger = lockLedger(userId, reservation.ledgerId());
        BigDecimal newRunReserve = reservation.reservedUsd().add(amount);
        if (newRunReserve.compareTo(context.asyncRunMaxCost()) > 0) {
            throw budgetExceeded();
        }
        assertAvailable(ledger, context, amount);
        jdbcClient.sql("""
                        UPDATE ai_budget_ledgers
                        SET reserved_usd = reserved_usd + :amount, version = version + 1, updated_at = :now
                        WHERE user_id = :userId AND id = :ledgerId
                        """)
                .param("amount", amount).param("now", utc(requestedAt))
                .param("userId", userId).param("ledgerId", reservation.ledgerId()).update();
        int reservationUpdated = jdbcClient.sql("""
                        UPDATE ai_budget_reservations
                        SET reserved_usd = reserved_usd + :amount, updated_at = :now
                        WHERE user_id = :userId AND id = :reservationId AND status = 'RESERVED'
                        """)
                .param("amount", amount).param("now", utc(requestedAt))
                .param("userId", userId).param("reservationId", reservation.id()).update();
        if (reservationUpdated != 1) {
            throw new IllegalStateException("top-up lost the active reservation");
        }
        int runUpdated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET reserved_cost_usd = reserved_cost_usd + :amount, updated_at = :now
                        WHERE user_id = :userId AND id = :agentRunId AND status IN ('QUEUED','RUNNING')
                        """)
                .param("amount", amount).param("now", utc(requestedAt))
                .param("userId", userId).param("agentRunId", agentRunId).update();
        if (runUpdated != 1) {
            throw new IllegalStateException("top-up lost the active run");
        }
        return new BudgetReservationSnapshot(
                reservation.id(), reservation.policyVersion(), reservation.priceVersion(),
                ledger.budgetDate(), newRunReserve, reservation.settledUsd(), "RESERVED");
    }

    @Override
    @Transactional
    public BudgetReservationSnapshot settle(
            UUID userId, UUID agentRunId, BigDecimal actualCostUsd, Instant settledAt) {
        return finish(userId, agentRunId, actualCostUsd, settledAt, "SETTLED");
    }

    @Override
    @Transactional
    public BudgetReservationSnapshot releaseUnused(
            UUID userId, UUID agentRunId, Instant releasedAt) {
        BigDecimal actual = jdbcClient.sql("""
                        SELECT actual_cost_usd FROM agent_runs
                        WHERE user_id = :userId AND id = :agentRunId
                        """)
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return finish(userId, agentRunId, actual, releasedAt, "RELEASED");
    }

    private BudgetReservationSnapshot finish(
            UUID userId,
            UUID agentRunId,
            BigDecimal actualCostUsd,
            Instant occurredAt,
            String terminalStatus) {
        BigDecimal actual = money(actualCostUsd);
        Reservation reservation = lockActiveReservation(userId, agentRunId);
        if (actual.compareTo(reservation.reservedUsd()) > 0) {
            throw budgetExceeded();
        }
        Ledger ledger = lockLedger(userId, reservation.ledgerId());
        jdbcClient.sql("""
                        UPDATE ai_budget_ledgers
                        SET reserved_usd = reserved_usd - :reserved,
                            spent_usd = spent_usd + :actual,
                            version = version + 1,
                            updated_at = :now
                        WHERE user_id = :userId AND id = :ledgerId
                        """)
                .param("reserved", reservation.reservedUsd())
                .param("actual", actual)
                .param("now", utc(occurredAt))
                .param("userId", userId)
                .param("ledgerId", reservation.ledgerId())
                .update();
        String timeColumn = terminalStatus.equals("SETTLED") ? "settled_at" : "released_at";
        jdbcClient.sql("""
                        UPDATE ai_budget_reservations
                        SET status = :status,
                            settled_usd = :actual,
                            updated_at = :now,
                            """ + timeColumn + " = :now WHERE user_id = :userId AND id = :reservationId AND status = 'RESERVED'")
                .param("status", terminalStatus)
                .param("actual", actual)
                .param("now", utc(occurredAt))
                .param("userId", userId)
                .param("reservationId", reservation.id())
                .update();
        jdbcClient.sql("""
                        UPDATE agent_runs
                        SET reserved_cost_usd = 0, actual_cost_usd = :actual, updated_at = :now
                        WHERE user_id = :userId AND id = :agentRunId
                        """)
                .param("actual", actual).param("now", utc(occurredAt))
                .param("userId", userId).param("agentRunId", agentRunId).update();
        return new BudgetReservationSnapshot(
                reservation.id(), reservation.policyVersion(), reservation.priceVersion(),
                ledger.budgetDate(), reservation.reservedUsd(), actual, terminalStatus);
    }

    private UUID ensureLedger(UUID userId, LocalDate date, BudgetContext context, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO ai_budget_ledgers (
                            id, user_id, budget_date, budget_zone, spent_usd, reserved_usd,
                            policy_version, version, created_at, updated_at
                        ) VALUES (:id, :userId, :date, :zone, 0, 0, :policyVersion, 0, :now, :now)
                        ON CONFLICT (user_id, budget_date, budget_zone) DO NOTHING
                        """)
                .param("id", id).param("userId", userId).param("date", date)
                .param("zone", context.resetZone()).param("policyVersion", context.policyVersion())
                .param("now", utc(now)).update();
        return jdbcClient.sql("""
                        SELECT id FROM ai_budget_ledgers
                        WHERE user_id = :userId AND budget_date = :date AND budget_zone = :zone
                        """)
                .param("userId", userId).param("date", date).param("zone", context.resetZone())
                .query(UUID.class).single();
    }

    private BudgetContext context(UUID userId) {
        return jdbcClient.sql("""
                        SELECT p.version AS policy_version,
                               p.user_default_daily_budget_usd,
                               p.system_max_daily_budget_usd,
                               p.async_run_max_cost_usd,
                               p.reset_zone,
                               pref.daily_budget_usd
                        FROM user_ai_preferences pref
                        JOIN ai_budget_policy_versions p ON p.version = pref.budget_policy_version
                        WHERE pref.user_id = :userId AND pref.active
                        """)
                .param("userId", userId)
                .query((rs, row) -> new BudgetContext(
                        rs.getLong("policy_version"),
                        rs.getBigDecimal("user_default_daily_budget_usd"),
                        rs.getBigDecimal("system_max_daily_budget_usd"),
                        rs.getBigDecimal("async_run_max_cost_usd"),
                        rs.getString("reset_zone"),
                        rs.getBigDecimal("daily_budget_usd")))
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Ledger lockLedger(UUID userId, UUID ledgerId) {
        return jdbcClient.sql("""
                        SELECT id, budget_date, spent_usd, reserved_usd
                        FROM ai_budget_ledgers
                        WHERE user_id = :userId AND id = :ledgerId
                        FOR UPDATE
                        """)
                .param("userId", userId).param("ledgerId", ledgerId)
                .query((rs, row) -> new Ledger(
                        rs.getObject("id", UUID.class), rs.getObject("budget_date", LocalDate.class),
                        rs.getBigDecimal("spent_usd"), rs.getBigDecimal("reserved_usd")))
                .single();
    }

    private Reservation lockActiveReservation(UUID userId, UUID agentRunId) {
        return jdbcClient.sql("""
                        SELECT id, ledger_id, reserved_usd, settled_usd,
                               budget_policy_version, price_version
                        FROM ai_budget_reservations
                        WHERE user_id = :userId AND agent_run_id = :agentRunId AND status = 'RESERVED'
                        FOR UPDATE
                        """)
                .param("userId", userId).param("agentRunId", agentRunId)
                .query((rs, row) -> new Reservation(
                        rs.getObject("id", UUID.class), rs.getObject("ledger_id", UUID.class),
                        rs.getBigDecimal("reserved_usd"), rs.getBigDecimal("settled_usd"),
                        rs.getLong("budget_policy_version"), rs.getObject("price_version", Long.class)))
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
    }

    private void assertAvailable(Ledger ledger, BudgetContext context, BigDecimal additional) {
        BigDecimal effectiveLimit = context.dailyBudget().min(context.systemMaxDailyBudget());
        if (ledger.spentUsd().add(ledger.reservedUsd()).add(additional).compareTo(effectiveLimit) > 0) {
            throw budgetExceeded();
        }
    }

    private void requirePriceIfPaid(BigDecimal amount, Long priceVersion) {
        if (amount.signum() == 0) {
            return;
        }
        if (priceVersion == null || jdbcClient.sql("SELECT count(*) FROM ai_price_versions WHERE version = :version")
                .param("version", priceVersion).query(Long.class).single() != 1L) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }

    private void validateRequest(BudgetReservationRequest request) {
        if (request == null || request.userId() == null || request.agentRunId() == null
                || request.operationType() == null || request.operationType().isBlank()
                || request.operationType().length() > 80 || request.worstCaseCostUsd() == null
                || request.worstCaseCostUsd().signum() < 0 || request.requestedAt() == null) {
            throw new IllegalArgumentException("budget reservation request is invalid");
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.scale() > 6) {
            throw new IllegalArgumentException("money value is invalid");
        }
        return value.setScale(6);
    }

    private BusinessException budgetExceeded() {
        return new BusinessException(ErrorCode.RATE_OR_BUDGET_LIMIT_EXCEEDED);
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record BudgetContext(
            long policyVersion,
            BigDecimal userDefaultDailyBudget,
            BigDecimal systemMaxDailyBudget,
            BigDecimal asyncRunMaxCost,
            String resetZone,
            BigDecimal dailyBudget) {}

    private record Ledger(UUID id, LocalDate budgetDate, BigDecimal spentUsd, BigDecimal reservedUsd) {}

    private record Reservation(
            UUID id,
            UUID ledgerId,
            BigDecimal reservedUsd,
            BigDecimal settledUsd,
            long policyVersion,
            Long priceVersion) {}
}
