package com.hiresemble.agentrun.infrastructure.persistence;

import com.hiresemble.agentrun.application.model.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.port.AgentRunEventPublisher;
import com.hiresemble.agentrun.application.model.AgentRunEventType;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AgentStepCheckpointPort;
import com.hiresemble.agentrun.application.model.AgentStepSnapshot;
import com.hiresemble.agentrun.application.model.ReusableStepSnapshot;
import com.hiresemble.agentrun.application.command.StepCheckpointCommand;
import com.hiresemble.agentrun.application.command.StepStartCommand;
import com.hiresemble.agentrun.domain.model.AgentStep;
import com.hiresemble.agentrun.domain.model.AgentStepStatus;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAgentStepStore implements AgentStepCheckpointPort {

    private final JdbcClient jdbcClient;
    private final AgentRunJdbcMapper mapper;
    private final JdbcAgentRunRepository runRepository;
    private final AgentRunEventPublisher eventPublisher;

    public JdbcAgentStepStore(
            JdbcClient jdbcClient,
            AgentRunJdbcMapper mapper,
            JdbcAgentRunRepository runRepository,
            AgentRunEventPublisher eventPublisher) {
        this.jdbcClient = jdbcClient;
        this.mapper = mapper;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AgentStepSnapshot start(StepStartCommand command) {
        validateStart(command);
        UUID stepId = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                        INSERT INTO agent_steps (
                            id, user_id, agent_run_id, step_key, scope_key, step_order,
                            agent_name, status, attempt, max_attempts, input_hash,
                            input_refs, output_schema_version, model_policy_version,
                            prompt_version, requested_quality_mode,
                            created_at, started_at, updated_at
                        )
                        SELECT :id, :userId, :runId, :stepKey, :scopeKey, :stepOrder,
                               :agentName, 'RUNNING', :attempt, :maxAttempts, :inputHash,
                               CAST(:inputRefs AS jsonb), :schemaVersion, :modelPolicyVersion,
                               :promptVersion, :qualityMode, :now, :now, :now
                        WHERE EXISTS (
                            SELECT 1 FROM agent_runs
                            WHERE user_id = :userId AND id = :runId
                              AND status = 'RUNNING' AND claim_token = :claimToken
                              AND cancel_requested_at IS NULL
                        )
                        """)
                .param("id", stepId).param("userId", command.userId())
                .param("runId", command.agentRunId()).param("stepKey", command.stepKey())
                .param("scopeKey", command.scopeKey()).param("stepOrder", command.stepOrder())
                .param("agentName", command.agentName()).param("attempt", command.attempt())
                .param("maxAttempts", command.maxAttempts()).param("inputHash", command.inputHash())
                .param("inputRefs", mapper.write(command.sanitizedInputRefs()))
                .param("schemaVersion", command.outputSchemaVersion())
                .param("modelPolicyVersion", command.modelPolicyVersion())
                .param("promptVersion", command.promptVersion())
                .param("qualityMode", command.requestedQualityMode() == null
                        ? null : command.requestedQualityMode().name())
                .param("now", utc(command.occurredAt())).param("claimToken", command.claimToken())
                .update();
        if (inserted != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return publishStep(command.userId(), command.agentRunId(), command.claimToken(),
                stepId, command.stepKey(), command.occurredAt());
    }

    @Override
    @Transactional
    public AgentStepSnapshot reuse(StepStartCommand command, ReusableStepSnapshot reusableStep) {
        validateStart(command);
        if (reusableStep == null || reusableStep.outputHash() == null
                || reusableStep.minimalOutput() == null
                || (command.requestedQualityMode() != reusableStep.requestedQualityMode())) {
            throw new IllegalArgumentException("reusable step does not match the requested quality");
        }
        UUID stepId = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                        INSERT INTO agent_steps (
                            id, user_id, agent_run_id, step_key, scope_key, step_order,
                            agent_name, status, attempt, max_attempts, input_hash,
                            output_hash, input_refs, output_json, output_schema_version,
                            model_policy_version, prompt_version, requested_quality_mode,
                            model_tier_used, reused_step_id, created_at, completed_at, updated_at
                        )
                        SELECT :id, :userId, :runId, :stepKey, :scopeKey, :stepOrder,
                               :agentName, 'REUSED', :attempt, :maxAttempts, :inputHash,
                               :outputHash, CAST(:inputRefs AS jsonb), CAST(:outputJson AS jsonb),
                               :schemaVersion, :modelPolicyVersion, :promptVersion, :qualityMode,
                               :modelTier, :reusedStepId, :now, :now, :now
                        WHERE EXISTS (
                            SELECT 1 FROM agent_runs
                            WHERE user_id = :userId AND id = :runId
                              AND status = 'RUNNING' AND claim_token = :claimToken
                              AND cancel_requested_at IS NULL
                        )
                        """)
                .param("id", stepId).param("userId", command.userId())
                .param("runId", command.agentRunId()).param("stepKey", command.stepKey())
                .param("scopeKey", command.scopeKey()).param("stepOrder", command.stepOrder())
                .param("agentName", command.agentName()).param("attempt", command.attempt())
                .param("maxAttempts", command.maxAttempts()).param("inputHash", command.inputHash())
                .param("outputHash", reusableStep.outputHash())
                .param("inputRefs", mapper.write(command.sanitizedInputRefs()))
                .param("outputJson", mapper.write(reusableStep.minimalOutput()))
                .param("schemaVersion", command.outputSchemaVersion())
                .param("modelPolicyVersion", command.modelPolicyVersion())
                .param("promptVersion", command.promptVersion())
                .param("qualityMode", command.requestedQualityMode() == null
                        ? null : command.requestedQualityMode().name())
                .param("modelTier", reusableStep.modelTierUsed() == null
                        ? null : reusableStep.modelTierUsed().name())
                .param("reusedStepId", reusableStep.stepId())
                .param("now", utc(command.occurredAt())).param("claimToken", command.claimToken())
                .update();
        if (inserted != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return publishStep(command.userId(), command.agentRunId(), command.claimToken(),
                stepId, command.stepKey(), command.occurredAt());
    }

    @Override
    @Transactional
    public AgentStepSnapshot checkpoint(StepCheckpointCommand command) {
        StepState current = load(command.userId(), command.agentRunId(), command.agentStepId());
        AgentStep step = new AgentStep(
                current.id(), command.agentRunId(), current.stepKey(), current.scopeKey(),
                current.attempt(), current.maxAttempts(), current.status());
        step.transitionTo(command.targetStatus());
        boolean terminal = command.targetStatus().isTerminal();
        if ((command.targetStatus() == AgentStepStatus.SUCCEEDED
                        || command.targetStatus() == AgentStepStatus.REUSED)
                && (command.outputHash() == null || command.minimalOutput() == null)) {
            throw new IllegalArgumentException("successful step requires minimal validated output");
        }
        int updated = jdbcClient.sql("""
                        UPDATE agent_steps
                        SET status = :targetStatus, output_hash = :outputHash,
                            output_json = CAST(:outputJson AS jsonb), model_tier_used = :modelTier,
                            reused_step_id = :reusedStepId, error_code = :errorCode,
                            error_message_safe = :errorMessage, completed_at = :completedAt,
                            updated_at = :now
                        WHERE user_id = :userId AND agent_run_id = :runId AND id = :stepId
                          AND status = :currentStatus
                          AND EXISTS (
                              SELECT 1 FROM agent_runs
                              WHERE user_id = :userId AND id = :runId
                                AND status = 'RUNNING' AND claim_token = :claimToken
                          )
                        """)
                .param("targetStatus", command.targetStatus().name())
                .param("outputHash", command.outputHash())
                .param("outputJson", command.minimalOutput() == null
                        ? null : mapper.write(command.minimalOutput()))
                .param("modelTier", command.modelTierUsed() == null
                        ? null : command.modelTierUsed().name())
                .param("reusedStepId", command.reusedStepId())
                .param("errorCode", command.safeError() == null ? null : command.safeError().code())
                .param("errorMessage", command.safeError() == null ? null : command.safeError().message())
                .param("completedAt", terminal ? utc(command.occurredAt()) : null)
                .param("now", utc(command.occurredAt())).param("userId", command.userId())
                .param("runId", command.agentRunId()).param("stepId", command.agentStepId())
                .param("currentStatus", current.status().name()).param("claimToken", command.claimToken())
                .update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return publishStep(command.userId(), command.agentRunId(), command.claimToken(),
                command.agentStepId(), current.stepKey(), command.occurredAt());
    }

    private AgentStepSnapshot publishStep(
            UUID userId,
            UUID runId,
            UUID claimToken,
            UUID stepId,
            String stepKey,
            Instant occurredAt) {
        int runUpdated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET current_step = :stepKey, state_version = state_version + 1, updated_at = :now
                        WHERE user_id = :userId AND id = :runId
                          AND status = 'RUNNING' AND claim_token = :claimToken
                        """)
                .param("stepKey", stepKey).param("now", utc(occurredAt))
                .param("userId", userId).param("runId", runId).param("claimToken", claimToken)
                .update();
        if (runUpdated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        AgentStepSnapshot changed = step(userId, runId, stepId);
        AgentRunSnapshot run = runRepository.findByOwner(userId, runId).orElseThrow();
        eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                AgentRunEventType.STEP, run, changed, occurredAt));
        return changed;
    }

    private void validateStart(StepStartCommand command) {
        if (command == null || command.userId() == null || command.agentRunId() == null
                || command.claimToken() == null || command.stepKey() == null
                || command.stepKey().isBlank() || command.stepKey().length() > 100
                || command.agentName() == null || command.agentName().isBlank()
                || command.agentName().length() > 150 || command.stepOrder() < 1
                || command.attempt() < 1 || command.maxAttempts() < command.attempt()
                || command.maxAttempts() > 3 || command.inputHash() == null
                || !command.inputHash().matches("[0-9a-f]{64}")
                || command.sanitizedInputRefs() == null || !command.sanitizedInputRefs().isObject()
                || command.outputSchemaVersion() == null || command.outputSchemaVersion().isBlank()
                || command.modelPolicyVersion() < 1 || command.promptVersion() == null
                || command.promptVersion().isBlank() || command.occurredAt() == null) {
            throw new IllegalArgumentException("step start command is invalid");
        }
    }

    private StepState load(UUID userId, UUID runId, UUID stepId) {
        return jdbcClient.sql("""
                        SELECT id, step_key, scope_key, attempt, max_attempts, status
                        FROM agent_steps
                        WHERE user_id = :userId AND agent_run_id = :runId AND id = :stepId
                        """)
                .param("userId", userId).param("runId", runId).param("stepId", stepId)
                .query((rs, row) -> new StepState(
                        rs.getObject("id", UUID.class), rs.getString("step_key"),
                        rs.getString("scope_key"), rs.getInt("attempt"),
                        rs.getInt("max_attempts"), AgentStepStatus.valueOf(rs.getString("status"))))
                .optional().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AgentStepSnapshot step(UUID userId, UUID runId, UUID stepId) {
        return jdbcClient.sql("""
                        SELECT id, step_key, scope_key, step_order, agent_name, status,
                               attempt, max_attempts, started_at, completed_at,
                               error_code, error_message_safe
                        FROM agent_steps
                        WHERE user_id = :userId AND agent_run_id = :runId AND id = :stepId
                        """)
                .param("userId", userId).param("runId", runId).param("stepId", stepId)
                .query((rs, row) -> mapper.step(rs)).single();
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record StepState(
            UUID id,
            String stepKey,
            String scopeKey,
            int attempt,
            int maxAttempts,
            AgentStepStatus status) {}
}
