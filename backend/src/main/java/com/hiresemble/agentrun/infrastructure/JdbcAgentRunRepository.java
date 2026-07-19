package com.hiresemble.agentrun.infrastructure;

import com.hiresemble.agentrun.application.AgentRunCreationPort;
import com.hiresemble.agentrun.application.AgentRunEventPublisher;
import com.hiresemble.agentrun.application.AgentRunEventType;
import com.hiresemble.agentrun.application.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.AgentRunListCriteria;
import com.hiresemble.agentrun.application.AgentRunPage;
import com.hiresemble.agentrun.application.AgentRunQueryPort;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.AgentRunSort;
import com.hiresemble.agentrun.application.AgentStepSnapshot;
import com.hiresemble.agentrun.application.ReusableStepSnapshot;
import com.hiresemble.agentrun.application.WorkflowLaunchCommand;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAgentRunRepository implements AgentRunCreationPort, AgentRunQueryPort {

    private final JdbcClient jdbcClient;
    private final AgentRunJdbcMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentRunEventPublisher eventPublisher;

    public JdbcAgentRunRepository(
            JdbcClient jdbcClient,
            AgentRunJdbcMapper mapper,
            ObjectMapper objectMapper,
            AgentRunEventPublisher eventPublisher) {
        this.jdbcClient = jdbcClient;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AgentRunSnapshot createQueued(
            UUID agentRunId,
            WorkflowLaunchCommand command,
            long budgetPolicyVersion,
            Instant queuedAt) {
        String resourceType = command.resource() == null ? null : command.resource().resourceType();
        UUID resourceId = command.resource() == null ? null : command.resource().resourceId();
        jdbcClient.sql("""
                        INSERT INTO agent_runs (
                            id, user_id, workflow_type, status, current_step, progress_percent,
                            workflow_version, canonical_input_hash, input_reference_snapshot,
                            budget_policy_version, price_version, requested_quality_mode,
                            highest_model_tier_used, estimated_cost_usd, reserved_cost_usd,
                            actual_cost_usd, resource_type, resource_id, retry_of_run_id,
                            root_run_id, run_attempt_no, retryable_failure, state_version,
                            queued_at, updated_at
                        ) VALUES (
                            :id, :userId, :workflowType, 'QUEUED', NULL, 0,
                            :workflowVersion, :inputHash, CAST(:inputRefs AS jsonb),
                            :budgetPolicyVersion, :priceVersion, :qualityMode,
                            NULL, :estimatedCost, 0, 0, :resourceType, :resourceId, NULL,
                            :id, 1, false, 0, :queuedAt, :queuedAt
                        )
                        """)
                .param("id", agentRunId)
                .param("userId", command.userId())
                .param("workflowType", command.workflowType().name())
                .param("workflowVersion", command.workflowVersion())
                .param("inputHash", command.canonicalInputHash())
                .param("inputRefs", mapper.write(command.inputReferenceSnapshot()))
                .param("budgetPolicyVersion", budgetPolicyVersion)
                .param("priceVersion", command.priceVersion())
                .param("qualityMode", command.requestedQualityMode() == null
                        ? null : command.requestedQualityMode().name())
                .param("estimatedCost", command.estimatedCostUsd())
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("queuedAt", utc(queuedAt))
                .update();
        return findByOwner(command.userId(), agentRunId).orElseThrow();
    }

    @Override
    @Transactional
    public AgentRunSnapshot createRetry(
            UUID successorId,
            AgentRunSnapshot predecessor,
            long budgetPolicyVersion,
            Instant queuedAt) {
        int inserted = jdbcClient.sql("""
                            INSERT INTO agent_runs (
                                id, user_id, workflow_type, status, current_step, progress_percent,
                                workflow_version, canonical_input_hash, input_reference_snapshot,
                                budget_policy_version, price_version, requested_quality_mode,
                                highest_model_tier_used, estimated_cost_usd, reserved_cost_usd,
                                actual_cost_usd, resource_type, resource_id, retry_of_run_id,
                                root_run_id, run_attempt_no, retryable_failure, state_version,
                                queued_at, updated_at
                            ) VALUES (
                                :id, :userId, :workflowType, 'QUEUED', NULL, 0,
                                :workflowVersion, :inputHash, CAST(:inputRefs AS jsonb),
                                :budgetPolicyVersion, :priceVersion, :qualityMode,
                                NULL, :estimatedCost, 0, 0, :resourceType, :resourceId, :retryOf,
                                :rootRunId, :runAttemptNo, false, 0, :queuedAt, :queuedAt
                            )
                            ON CONFLICT (user_id, retry_of_run_id)
                                WHERE retry_of_run_id IS NOT NULL
                            DO NOTHING
                            """)
                    .param("id", successorId)
                    .param("userId", predecessor.userId())
                    .param("workflowType", predecessor.workflowType().name())
                    .param("workflowVersion", predecessor.workflowVersion())
                    .param("inputHash", predecessor.canonicalInputHash())
                    .param("inputRefs", mapper.write(predecessor.inputReferenceSnapshot()))
                    .param("budgetPolicyVersion", budgetPolicyVersion)
                    .param("priceVersion", predecessor.priceVersion())
                    .param("qualityMode", predecessor.requestedQualityMode() == null
                            ? null : predecessor.requestedQualityMode().name())
                    .param("estimatedCost", predecessor.estimatedCostUsd())
                    .param("resourceType", predecessor.resourceType())
                    .param("resourceId", predecessor.resourceId())
                    .param("retryOf", predecessor.id())
                    .param("rootRunId", predecessor.rootRunId())
                    .param("runAttemptNo", predecessor.runAttemptNo() + 1)
                    .param("queuedAt", utc(queuedAt))
                    .update();
        if (inserted == 1) {
            return findByOwner(predecessor.userId(), successorId).orElseThrow();
        }
        AgentRunSnapshot existing = jdbcClient.sql("SELECT " + AgentRunJdbcMapper.RUN_COLUMNS
                        + " FROM agent_runs r WHERE r.user_id = :userId AND r.retry_of_run_id = :predecessorId")
                .param("userId", predecessor.userId())
                .param("predecessorId", predecessor.id())
                .query((rs, row) -> mapper.run(rs, List.of()))
                .single();
        if (!compatibleRetry(existing, predecessor)) {
            throw new BusinessException(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED);
        }
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentRunSnapshot> findByOwner(UUID userId, UUID agentRunId) {
        return findRun(userId, agentRunId).map(run -> withSteps(run, findSteps(userId, agentRunId)));
    }

    @Transactional(readOnly = true)
    public Optional<AgentRunSnapshot> findByIdInternal(UUID agentRunId) {
        String sql = "SELECT " + AgentRunJdbcMapper.RUN_COLUMNS
                + " FROM agent_runs r WHERE r.id = :agentRunId";
        return jdbcClient.sql(sql)
                .param("agentRunId", agentRunId)
                .query((rs, row) -> mapper.run(rs, List.of()))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRunPage findPage(AgentRunListCriteria criteria) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", criteria.userId());
        String where = where(criteria, parameters);
        long total = jdbcClient.sql("SELECT count(*) FROM agent_runs r " + where)
                .params(parameters)
                .query(Long.class)
                .single();
        parameters.put("limit", criteria.size());
        parameters.put("offset", criteria.page() * criteria.size());
        String order = criteria.sort() == AgentRunSort.UPDATED_AT_DESC
                ? "r.updated_at DESC, r.id DESC"
                : "r.queued_at DESC, r.id DESC";
        List<AgentRunSnapshot> items = jdbcClient.sql("SELECT " + AgentRunJdbcMapper.RUN_COLUMNS
                        + " FROM agent_runs r " + where + " ORDER BY " + order
                        + " LIMIT :limit OFFSET :offset")
                .params(parameters)
                .query((rs, row) -> mapper.run(rs, List.of()))
                .list();
        int totalPages = total == 0 ? 0 : (int) ((total + criteria.size() - 1) / criteria.size());
        return new AgentRunPage(items, criteria.page(), criteria.size(), total, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReusableStepSnapshot> findReusableStep(
            UUID userId,
            String stepKey,
            String scopeKey,
            String inputHash,
            AiQualityMode requestedQualityMode) {
        return jdbcClient.sql("""
                        SELECT id, output_hash, output_json::text AS output_json,
                               requested_quality_mode, model_tier_used
                        FROM agent_steps
                        WHERE user_id = :userId
                          AND step_key = :stepKey
                          AND scope_key IS NOT DISTINCT FROM :scopeKey
                          AND input_hash = :inputHash
                          AND requested_quality_mode IS NOT DISTINCT FROM :qualityMode
                          AND status IN ('SUCCEEDED', 'REUSED')
                          AND output_hash IS NOT NULL
                          AND output_json IS NOT NULL
                        ORDER BY completed_at DESC, id DESC
                        LIMIT 1
                        """)
                .param("userId", userId)
                .param("stepKey", stepKey)
                .param("scopeKey", scopeKey)
                .param("inputHash", inputHash)
                .param("qualityMode", requestedQualityMode == null ? null : requestedQualityMode.name())
                .query((rs, row) -> new ReusableStepSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getString("output_hash"),
                        readTree(rs.getString("output_json")),
                        enumOrNull(AiQualityMode.class, rs.getString("requested_quality_mode")),
                        enumOrNull(ModelTier.class, rs.getString("model_tier_used"))))
                .optional();
    }

    private Optional<AgentRunSnapshot> findRun(UUID userId, UUID agentRunId) {
        String sql = "SELECT " + AgentRunJdbcMapper.RUN_COLUMNS
                + " FROM agent_runs r WHERE r.user_id = :userId AND r.id = :agentRunId";
        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .query((rs, row) -> mapper.run(rs, List.of()))
                .optional();
    }

    private List<AgentStepSnapshot> findSteps(UUID userId, UUID agentRunId) {
        return jdbcClient.sql("""
                        SELECT id, step_key, scope_key, step_order, agent_name, status,
                               attempt, max_attempts, started_at, completed_at,
                               error_code, error_message_safe
                        FROM agent_steps
                        WHERE user_id = :userId AND agent_run_id = :agentRunId
                        ORDER BY step_order, scope_key NULLS FIRST, attempt
                        """)
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .query((rs, row) -> mapper.step(rs))
                .list();
    }

    private AgentRunSnapshot withSteps(AgentRunSnapshot run, List<AgentStepSnapshot> steps) {
        return new AgentRunSnapshot(
                run.id(), run.userId(), run.workflowType(), run.status(), run.currentStep(),
                run.progressPercent(), run.workflowVersion(), run.canonicalInputHash(),
                run.inputReferenceSnapshot(), run.budgetPolicyVersion(), run.priceVersion(),
                run.requestedQualityMode(), run.highestModelTierUsed(), run.estimatedCostUsd(),
                run.reservedCostUsd(), run.actualCostUsd(), run.resourceType(), run.resourceId(),
                run.retryOfRunId(), run.rootRunId(), run.runAttemptNo(), run.retryableFailure(),
                run.safeError(), run.partialResult(), run.claimToken(), run.claimedBy(),
                run.leaseExpiresAt(), run.heartbeatAt(), run.cancelRequestedAt(),
                run.requiredUserAction(), run.stateVersion(), run.queuedAt(), run.startedAt(),
                run.completedAt(), run.updatedAt(), steps);
    }

    private boolean compatibleRetry(AgentRunSnapshot successor, AgentRunSnapshot predecessor) {
        return successor.workflowType() == predecessor.workflowType()
                && successor.workflowVersion().equals(predecessor.workflowVersion())
                && successor.canonicalInputHash().equals(predecessor.canonicalInputHash())
                && successor.requestedQualityMode() == predecessor.requestedQualityMode()
                && java.util.Objects.equals(successor.resourceType(), predecessor.resourceType())
                && java.util.Objects.equals(successor.resourceId(), predecessor.resourceId());
    }

    private String where(AgentRunListCriteria criteria, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder("WHERE r.user_id = :userId");
        appendEnumFilter(where, parameters, "workflow_type", "workflowType", criteria.workflowTypes());
        appendEnumFilter(where, parameters, "status", "status", criteria.statuses());
        if (criteria.resourceType() != null) {
            where.append(" AND r.resource_type = :resourceType AND r.resource_id = :resourceId");
            parameters.put("resourceType", criteria.resourceType());
            parameters.put("resourceId", criteria.resourceId());
        }
        if (criteria.retryable() != null) {
            where.append(criteria.retryable()
                    ? " AND r.status IN ('FAILED','INTERRUPTED') AND r.retryable_failure"
                    : " AND NOT (r.status IN ('FAILED','INTERRUPTED') AND r.retryable_failure)");
        }
        return where.toString();
    }

    private void appendEnumFilter(
            StringBuilder where,
            Map<String, Object> parameters,
            String column,
            String prefix,
            List<? extends Enum<?>> values) {
        if (values.isEmpty()) {
            return;
        }
        List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String key = prefix + index;
            placeholders.add(":" + key);
            parameters.put(key, values.get(index).name());
        }
        where.append(" AND r.").append(column).append(" IN (")
                .append(String.join(",", placeholders)).append(")");
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored reusable output is invalid", exception);
        }
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
