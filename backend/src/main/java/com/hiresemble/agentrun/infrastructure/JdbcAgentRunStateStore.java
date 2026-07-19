package com.hiresemble.agentrun.infrastructure;

import com.hiresemble.agentrun.application.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.AgentRunEventPublisher;
import com.hiresemble.agentrun.application.AgentRunEventType;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.AgentRunStatePort;
import com.hiresemble.agentrun.application.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.ClaimedAgentRun;
import com.hiresemble.agentrun.application.SafeInterruption;
import com.hiresemble.agentrun.domain.AgentRun;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAgentRunStateStore implements AgentRunStatePort {

    private final JdbcClient jdbcClient;
    private final JdbcAgentRunRepository repository;
    private final AgentRunJdbcMapper mapper;
    private final AgentRunEventPublisher eventPublisher;

    public JdbcAgentRunStateStore(
            JdbcClient jdbcClient,
            JdbcAgentRunRepository repository,
            AgentRunJdbcMapper mapper,
            AgentRunEventPublisher eventPublisher) {
        this.jdbcClient = jdbcClient;
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Optional<ClaimedAgentRun> claimNext(
            String workerId, Instant now, Duration leaseDuration) {
        UUID token = UUID.randomUUID();
        Optional<UUID> claimedId = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET status = 'RUNNING', claim_token = :claimToken, claimed_by = :workerId,
                            heartbeat_at = :now, lease_expires_at = :leaseExpiresAt,
                            started_at = COALESCE(started_at, :now),
                            state_version = state_version + 1, updated_at = :now
                        WHERE id = (
                            SELECT id FROM agent_runs
                            WHERE status = 'QUEUED' AND cancel_requested_at IS NULL
                            ORDER BY queued_at, id
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        ) AND status = 'QUEUED'
                        RETURNING id
                        """)
                .param("claimToken", token).param("workerId", workerId)
                .param("now", utc(now)).param("leaseExpiresAt", utc(now.plus(leaseDuration)))
                .query(UUID.class).optional();
        return claimedId.map(id -> claimed(id, token, workerId, now));
    }

    @Override
    @Transactional
    public Optional<ClaimedAgentRun> claim(
            UUID agentRunId, String workerId, Instant now, Duration leaseDuration) {
        UUID token = UUID.randomUUID();
        Optional<UUID> claimedId = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET status = 'RUNNING', claim_token = :claimToken, claimed_by = :workerId,
                            heartbeat_at = :now, lease_expires_at = :leaseExpiresAt,
                            started_at = COALESCE(started_at, :now),
                            state_version = state_version + 1, updated_at = :now
                        WHERE id = :agentRunId AND status = 'QUEUED' AND cancel_requested_at IS NULL
                        RETURNING id
                        """)
                .param("claimToken", token).param("workerId", workerId)
                .param("now", utc(now)).param("leaseExpiresAt", utc(now.plus(leaseDuration)))
                .param("agentRunId", agentRunId)
                .query(UUID.class).optional();
        return claimedId.map(id -> claimed(id, token, workerId, now));
    }

    @Override
    @Transactional
    public AgentRunSnapshot transition(AgentRunTransitionCommand command) {
        AgentRunSnapshot current = owned(command.userId(), command.agentRunId());
        assertClaimAndVersion(current, command.claimToken(), command.expectedStateVersion());
        AgentRun domain = domain(current);
        domain.transitionTo(command.targetStatus());
        if ((command.targetStatus() == AgentRunStatus.WAITING_USER
                        || command.targetStatus().isTerminal())
                && current.reservedCostUsd().signum() != 0) {
            throw new IllegalStateException("unused budget must be released before leaving active execution");
        }
        if ((command.targetStatus() == AgentRunStatus.WAITING_USER)
                != (command.requiredUserAction() != null)) {
            throw new IllegalArgumentException("waiting transition requires exactly one safe action");
        }
        if (command.progressPercent() < current.progressPercent()
                || command.progressPercent() < 0 || command.progressPercent() > 100) {
            throw new IllegalArgumentException("run progress cannot move backwards");
        }
        ModelTier highestTier = highest(current.highestModelTierUsed(), command.highestModelTierUsed());
        Instant completedAt = command.targetStatus().isTerminal() ? command.occurredAt() : null;
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET status = :targetStatus,
                            current_step = :currentStep,
                            progress_percent = :progressPercent,
                            highest_model_tier_used = :highestTier,
                            actual_cost_usd = :actualCost,
                            retryable_failure = :retryableFailure,
                            error_code = :errorCode,
                            error_message_safe = :errorMessage,
                            partial_result_json = CAST(:partialResult AS jsonb),
                            claim_token = NULL,
                            claimed_by = NULL,
                            lease_expires_at = NULL,
                            heartbeat_at = NULL,
                            waiting_action_type = :waitingType,
                            waiting_action_route = :waitingRoute,
                            waiting_action_message = :waitingMessage,
                            completed_at = :completedAt,
                            state_version = state_version + 1,
                            updated_at = :occurredAt
                        WHERE user_id = :userId AND id = :agentRunId
                          AND status = 'RUNNING' AND claim_token = :claimToken
                          AND state_version = :expectedVersion
                        """)
                .param("targetStatus", command.targetStatus().name())
                .param("currentStep", command.currentStep())
                .param("progressPercent", command.progressPercent())
                .param("highestTier", highestTier == null ? null : highestTier.name())
                .param("actualCost", command.actualCostUsd())
                .param("retryableFailure", command.retryableFailure())
                .param("errorCode", command.safeError() == null ? null : command.safeError().code())
                .param("errorMessage", command.safeError() == null ? null : command.safeError().message())
                .param("partialResult", command.partialResult() == null ? null : mapper.write(command.partialResult()))
                .param("waitingType", command.requiredUserAction() == null
                        ? null : command.requiredUserAction().type().name())
                .param("waitingRoute", command.requiredUserAction() == null
                        ? null : command.requiredUserAction().route())
                .param("waitingMessage", command.requiredUserAction() == null
                        ? null : command.requiredUserAction().message())
                .param("completedAt", completedAt == null ? null : utc(completedAt))
                .param("occurredAt", utc(command.occurredAt()))
                .param("userId", command.userId()).param("agentRunId", command.agentRunId())
                .param("claimToken", command.claimToken()).param("expectedVersion", command.expectedStateVersion())
                .update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        AgentRunSnapshot changed = owned(command.userId(), command.agentRunId());
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                eventType(changed.status()), changed, null, command.occurredAt()));
        return changed;
    }

    @Override
    @Transactional
    public AgentRunSnapshot updateProgress(
            UUID userId,
            UUID agentRunId,
            UUID claimToken,
            long expectedStateVersion,
            String currentStep,
            int progressPercent,
            Instant occurredAt) {
        AgentRunSnapshot current = owned(userId, agentRunId);
        assertClaimAndVersion(current, claimToken, expectedStateVersion);
        if (progressPercent < current.progressPercent() || progressPercent > 100) {
            throw new IllegalArgumentException("run progress cannot move backwards");
        }
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET current_step = :currentStep, progress_percent = :progressPercent,
                            state_version = state_version + 1, updated_at = :occurredAt
                        WHERE user_id = :userId AND id = :agentRunId AND status = 'RUNNING'
                          AND claim_token = :claimToken AND state_version = :expectedVersion
                        """)
                .param("currentStep", currentStep).param("progressPercent", progressPercent)
                .param("occurredAt", utc(occurredAt)).param("userId", userId)
                .param("agentRunId", agentRunId).param("claimToken", claimToken)
                .param("expectedVersion", expectedStateVersion).update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        AgentRunSnapshot changed = owned(userId, agentRunId);
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.PROGRESS, changed, null, occurredAt));
        return changed;
    }

    @Override
    @Transactional
    public AgentRunSnapshot resumeWaiting(
            UUID userId,
            UUID agentRunId,
            long expectedStateVersion,
            BigDecimal reservedCostUsd,
            Instant occurredAt) {
        AgentRunSnapshot current = owned(userId, agentRunId);
        if (current.stateVersion() != expectedStateVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        AgentRun domain = domain(current);
        domain.transitionTo(AgentRunStatus.QUEUED);
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET status = 'QUEUED', reserved_cost_usd = :reservedCost,
                            waiting_action_type = NULL, waiting_action_route = NULL,
                            waiting_action_message = NULL, cancel_requested_at = NULL,
                            state_version = state_version + 1, updated_at = :occurredAt
                        WHERE user_id = :userId AND id = :agentRunId
                          AND status = 'WAITING_USER' AND state_version = :expectedVersion
                        """)
                .param("reservedCost", reservedCostUsd).param("occurredAt", utc(occurredAt))
                .param("userId", userId).param("agentRunId", agentRunId)
                .param("expectedVersion", expectedStateVersion).update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        jdbcClient.sql("""
                        UPDATE agent_steps
                        SET status = 'PENDING', started_at = NULL, updated_at = :occurredAt
                        WHERE user_id = :userId AND agent_run_id = :agentRunId
                          AND status = 'WAITING_USER'
                        """)
                .param("occurredAt", utc(occurredAt)).param("userId", userId)
                .param("agentRunId", agentRunId).update();
        AgentRunSnapshot changed = owned(userId, agentRunId);
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.PROGRESS, changed, null, occurredAt));
        return changed;
    }

    @Override
    @Transactional
    public AgentRunSnapshot markCancellationRequested(
            UUID userId,
            UUID agentRunId,
            long expectedStateVersion,
            Instant requestedAt) {
        AgentRunSnapshot current = owned(userId, agentRunId);
        if (current.stateVersion() != expectedStateVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        AgentRun domain = domain(current);
        domain.requestCancellation(requestedAt);
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET cancel_requested_at = :requestedAt,
                            state_version = state_version + 1,
                            updated_at = :requestedAt
                        WHERE user_id = :userId AND id = :agentRunId
                          AND status IN ('QUEUED','RUNNING','WAITING_USER')
                          AND cancel_requested_at IS NULL
                          AND state_version = :expectedVersion
                        """)
                .param("requestedAt", utc(requestedAt)).param("userId", userId)
                .param("agentRunId", agentRunId).param("expectedVersion", expectedStateVersion)
                .update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        AgentRunSnapshot changed = owned(userId, agentRunId);
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.PROGRESS, changed, null, requestedAt));
        return changed;
    }

    @Override
    @Transactional
    public AgentRunSnapshot cancelUnclaimed(UUID userId, UUID agentRunId, Instant completedAt) {
        AgentRunSnapshot current = owned(userId, agentRunId);
        if (current.status() != AgentRunStatus.QUEUED
                && current.status() != AgentRunStatus.WAITING_USER) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return cancel(current, null, completedAt);
    }

    @Override
    @Transactional
    public AgentRunSnapshot cancelClaimed(
            UUID userId, UUID agentRunId, UUID claimToken, Instant completedAt) {
        AgentRunSnapshot current = owned(userId, agentRunId);
        if (current.status() != AgentRunStatus.RUNNING
                || !claimToken.equals(current.claimToken())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return cancel(current, claimToken, completedAt);
    }

    @Override
    @Transactional
    public AgentRunSnapshot heartbeat(
            UUID userId,
            UUID agentRunId,
            UUID claimToken,
            Instant now,
            Duration leaseDuration) {
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET heartbeat_at = :now, lease_expires_at = :leaseExpiresAt,
                            state_version = state_version + 1, updated_at = :now
                        WHERE user_id = :userId AND id = :agentRunId
                          AND status = 'RUNNING' AND claim_token = :claimToken
                        """)
                .param("now", utc(now)).param("leaseExpiresAt", utc(now.plus(leaseDuration)))
                .param("userId", userId).param("agentRunId", agentRunId)
                .param("claimToken", claimToken).update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AgentRunSnapshot changed = owned(userId, agentRunId);
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.HEARTBEAT, changed, null, now));
        return changed;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCancellationRequested(UUID userId, UUID agentRunId, UUID claimToken) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM agent_runs
                        WHERE user_id = :userId AND id = :agentRunId
                          AND claim_token = :claimToken AND cancel_requested_at IS NOT NULL
                        """)
                .param("userId", userId).param("agentRunId", agentRunId)
                .param("claimToken", claimToken).query(Long.class).single() == 1L;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findQueuedRunIds(int limit) {
        return jdbcClient.sql("""
                        SELECT id FROM agent_runs
                        WHERE status = 'QUEUED' AND cancel_requested_at IS NULL
                        ORDER BY queued_at, id LIMIT :limit
                        """)
                .param("limit", limit).query(UUID.class).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findExpiredRunningIds(Instant now, int limit) {
        return jdbcClient.sql("""
                        SELECT id FROM agent_runs
                        WHERE status = 'RUNNING' AND lease_expires_at <= :now
                        ORDER BY lease_expires_at, id LIMIT :limit
                        """)
                .param("now", utc(now)).param("limit", limit).query(UUID.class).list();
    }

    @Override
    @Transactional
    public AgentRunSnapshot interruptExpired(
            UUID agentRunId, Instant now, SafeInterruption interruption) {
        AgentRunSnapshot current = repository.findByIdInternal(agentRunId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (current.status() != AgentRunStatus.RUNNING
                || current.leaseExpiresAt() == null || current.leaseExpiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        releaseActiveReservation(current, now);
        jdbcClient.sql("""
                        UPDATE agent_steps
                        SET status = 'INTERRUPTED', completed_at = :now, updated_at = :now,
                            error_code = :errorCode, error_message_safe = :errorMessage
                        WHERE user_id = :userId AND agent_run_id = :agentRunId AND status = 'RUNNING'
                        """)
                .param("now", utc(now)).param("errorCode", interruption.error().code())
                .param("errorMessage", interruption.error().message())
                .param("userId", current.userId()).param("agentRunId", agentRunId).update();
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET status = 'INTERRUPTED', retryable_failure = :retryable,
                            error_code = :errorCode, error_message_safe = :errorMessage,
                            reserved_cost_usd = 0, claim_token = NULL, claimed_by = NULL,
                            lease_expires_at = NULL, heartbeat_at = NULL,
                            completed_at = :now, state_version = state_version + 1, updated_at = :now
                        WHERE id = :agentRunId AND status = 'RUNNING' AND lease_expires_at <= :now
                        """)
                .param("retryable", interruption.retryable())
                .param("errorCode", interruption.error().code())
                .param("errorMessage", interruption.error().message())
                .param("now", utc(now)).param("agentRunId", agentRunId).update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AgentRunSnapshot changed = owned(current.userId(), agentRunId);
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.TERMINAL, changed, null, now));
        return changed;
    }

    private ClaimedAgentRun claimed(UUID id, UUID token, String workerId, Instant now) {
        AgentRunSnapshot run = repository.findByIdInternal(id).orElseThrow();
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.PROGRESS, run, null, now));
        return new ClaimedAgentRun(run, token, workerId, run.leaseExpiresAt());
    }

    private AgentRunSnapshot cancel(
            AgentRunSnapshot current, UUID claimToken, Instant completedAt) {
        AgentRun domain = domain(current);
        domain.transitionTo(AgentRunStatus.CANCELLED);
        if (current.reservedCostUsd().signum() != 0) {
            throw new IllegalStateException("unused budget must be released before cancellation completes");
        }
        jdbcClient.sql("""
                        UPDATE agent_steps
                        SET status = 'CANCELLED', completed_at = :completedAt, updated_at = :completedAt
                        WHERE user_id = :userId AND agent_run_id = :agentRunId
                          AND status IN ('PENDING','RUNNING','WAITING_USER')
                        """)
                .param("completedAt", utc(completedAt)).param("userId", current.userId())
                .param("agentRunId", current.id()).update();
        String claimCondition = claimToken == null
                ? "status IN ('QUEUED','WAITING_USER')"
                : "status = 'RUNNING' AND claim_token = :claimToken";
        var statement = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET status = 'CANCELLED', retryable_failure = false,
                            claim_token = NULL, claimed_by = NULL, lease_expires_at = NULL,
                            heartbeat_at = NULL, waiting_action_type = NULL,
                            waiting_action_route = NULL, waiting_action_message = NULL,
                            completed_at = :completedAt, state_version = state_version + 1,
                            updated_at = :completedAt
                        WHERE user_id = :userId AND id = :agentRunId
                          AND cancel_requested_at IS NOT NULL AND """ + " " + claimCondition)
                .param("completedAt", utc(completedAt)).param("userId", current.userId())
                .param("agentRunId", current.id());
        if (claimToken != null) {
            statement = statement.param("claimToken", claimToken);
        }
        int updated = statement.update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AgentRunSnapshot changed = owned(current.userId(), current.id());
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.TERMINAL, changed, null, completedAt));
        return changed;
    }

    private AgentRunSnapshot owned(UUID userId, UUID runId) {
        return repository.findByOwner(userId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void assertClaimAndVersion(AgentRunSnapshot run, UUID claimToken, long expectedVersion) {
        if (run.stateVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        if (run.status() != AgentRunStatus.RUNNING || !claimToken.equals(run.claimToken())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }

    private AgentRun domain(AgentRunSnapshot run) {
        return AgentRun.rehydrate(
                run.id(), run.userId(), run.workflowType(), run.status(), run.retryOfRunId(),
                run.rootRunId(), run.runAttemptNo(), run.stateVersion(),
                run.cancelRequestedAt(), run.retryableFailure());
    }

    private ModelTier highest(ModelTier left, ModelTier right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private AgentRunEventType eventType(AgentRunStatus status) {
        if (status.isTerminal()) return AgentRunEventType.TERMINAL;
        if (status == AgentRunStatus.WAITING_USER) return AgentRunEventType.WAITING_USER;
        return AgentRunEventType.PROGRESS;
    }

    private void releaseActiveReservation(AgentRunSnapshot run, Instant now) {
        Optional<ActiveReservation> active = jdbcClient.sql("""
                        SELECT id, ledger_id, reserved_usd
                        FROM ai_budget_reservations
                        WHERE user_id = :userId AND agent_run_id = :runId AND status = 'RESERVED'
                        FOR UPDATE
                        """)
                .param("userId", run.userId()).param("runId", run.id())
                .query((rs, row) -> new ActiveReservation(
                        rs.getObject("id", UUID.class), rs.getObject("ledger_id", UUID.class),
                        rs.getBigDecimal("reserved_usd")))
                .optional();
        if (active.isEmpty()) {
            return;
        }
        ActiveReservation reservation = active.get();
        jdbcClient.sql("""
                        UPDATE ai_budget_ledgers
                        SET reserved_usd = reserved_usd - :reserved,
                            spent_usd = spent_usd + :actual,
                            version = version + 1, updated_at = :now
                        WHERE user_id = :userId AND id = :ledgerId
                        """)
                .param("reserved", reservation.reservedUsd()).param("actual", run.actualCostUsd())
                .param("now", utc(now)).param("userId", run.userId())
                .param("ledgerId", reservation.ledgerId()).update();
        jdbcClient.sql("""
                        UPDATE ai_budget_reservations
                        SET status = 'RELEASED', settled_usd = :actual,
                            released_at = :now, updated_at = :now
                        WHERE user_id = :userId AND id = :id AND status = 'RESERVED'
                        """)
                .param("actual", run.actualCostUsd()).param("now", utc(now))
                .param("userId", run.userId()).param("id", reservation.id()).update();
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ActiveReservation(UUID id, UUID ledgerId, BigDecimal reservedUsd) {}
}
