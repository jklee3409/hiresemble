package com.hiresemble.agentrun.infrastructure;

import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.AgentStepSnapshot;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.AgentStepStatus;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.PartialResult;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.agentrun.domain.RequiredUserActionType;
import com.hiresemble.agentrun.domain.ResourceReference;
import com.hiresemble.agentrun.domain.SafeError;
import com.hiresemble.agentrun.domain.WorkflowType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class AgentRunJdbcMapper {

    static final String RUN_COLUMNS = """
            r.id, r.user_id, r.workflow_type, r.status, r.current_step,
            r.progress_percent, r.workflow_version, r.canonical_input_hash,
            r.input_reference_snapshot::text AS input_snapshot_json,
            r.budget_policy_version, r.price_version, r.requested_quality_mode,
            r.highest_model_tier_used, r.estimated_cost_usd, r.reserved_cost_usd,
            r.actual_cost_usd, r.resource_type, r.resource_id, r.retry_of_run_id,
            r.root_run_id, r.run_attempt_no, r.retryable_failure,
            r.error_code, r.error_message_safe,
            r.partial_result_json::text AS partial_result_json,
            r.claim_token, r.claimed_by, r.lease_expires_at, r.heartbeat_at,
            r.cancel_requested_at, r.waiting_action_type, r.waiting_action_route,
            r.waiting_action_message, r.state_version, r.queued_at, r.started_at,
            r.completed_at, r.updated_at
            """;

    private final ObjectMapper objectMapper;

    AgentRunJdbcMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AgentRunSnapshot run(ResultSet rs, List<AgentStepSnapshot> steps) throws SQLException {
        String resourceType = rs.getString("resource_type");
        UUID resourceId = rs.getObject("resource_id", UUID.class);
        ResourceReference actionResource = resourceType == null
                ? null
                : new ResourceReference(resourceType, resourceId, null);
        String actionType = rs.getString("waiting_action_type");
        RequiredUserAction requiredAction = actionType == null
                ? null
                : new RequiredUserAction(
                        RequiredUserActionType.valueOf(actionType),
                        actionResource,
                        rs.getString("waiting_action_route"),
                        rs.getString("waiting_action_message"));
        String errorCode = rs.getString("error_code");
        SafeError safeError = errorCode == null
                ? null
                : new SafeError(errorCode, rs.getString("error_message_safe"));
        return new AgentRunSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                WorkflowType.valueOf(rs.getString("workflow_type")),
                AgentRunStatus.valueOf(rs.getString("status")),
                rs.getString("current_step"),
                rs.getInt("progress_percent"),
                rs.getString("workflow_version"),
                rs.getString("canonical_input_hash"),
                readTree(rs.getString("input_snapshot_json")),
                rs.getLong("budget_policy_version"),
                rs.getObject("price_version", Long.class),
                enumOrNull(AiQualityMode.class, rs.getString("requested_quality_mode")),
                enumOrNull(ModelTier.class, rs.getString("highest_model_tier_used")),
                rs.getBigDecimal("estimated_cost_usd"),
                rs.getBigDecimal("reserved_cost_usd"),
                rs.getBigDecimal("actual_cost_usd"),
                resourceType,
                resourceId,
                rs.getObject("retry_of_run_id", UUID.class),
                rs.getObject("root_run_id", UUID.class),
                rs.getInt("run_attempt_no"),
                rs.getBoolean("retryable_failure"),
                safeError,
                readPartial(rs.getString("partial_result_json")),
                rs.getObject("claim_token", UUID.class),
                rs.getString("claimed_by"),
                instant(rs, "lease_expires_at"),
                instant(rs, "heartbeat_at"),
                instant(rs, "cancel_requested_at"),
                requiredAction,
                rs.getLong("state_version"),
                instant(rs, "queued_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                instant(rs, "updated_at"),
                steps);
    }

    AgentStepSnapshot step(ResultSet rs) throws SQLException {
        String errorCode = rs.getString("error_code");
        return new AgentStepSnapshot(
                rs.getObject("id", UUID.class),
                rs.getString("step_key"),
                rs.getString("scope_key"),
                rs.getInt("step_order"),
                rs.getString("agent_name"),
                AgentStepStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt"),
                rs.getInt("max_attempts"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                errorCode == null ? null : new SafeError(errorCode, rs.getString("error_message_safe")));
    }

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("safe Agent Run metadata could not be serialized", exception);
        }
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored Agent Run metadata is invalid", exception);
        }
    }

    private PartialResult readPartial(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PartialResult.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored partial result metadata is invalid", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
