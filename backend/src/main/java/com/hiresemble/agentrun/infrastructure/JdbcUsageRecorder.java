package com.hiresemble.agentrun.infrastructure;

import com.hiresemble.agentrun.application.UsageRecordCommand;
import com.hiresemble.agentrun.application.UsageRecorderPort;
import com.hiresemble.agentrun.application.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.AgentRunEventPublisher;
import com.hiresemble.agentrun.application.AgentRunEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcUsageRecorder implements UsageRecorderPort {

    private final JdbcClient jdbcClient;
    private final JdbcAgentRunRepository runRepository;
    private final AgentRunEventPublisher eventPublisher;

    public JdbcUsageRecorder(
            JdbcClient jdbcClient,
            JdbcAgentRunRepository runRepository,
            AgentRunEventPublisher eventPublisher) {
        this.jdbcClient = jdbcClient;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public UUID record(UsageRecordCommand command) {
        validate(command);
        boolean activeClaim = jdbcClient.sql("""
                        SELECT count(*) FROM agent_runs
                        WHERE user_id = :userId AND id = :runId
                          AND status = 'RUNNING' AND claim_token = :claimToken
                        """)
                .param("userId", command.userId()).param("runId", command.agentRunId())
                .param("claimToken", command.claimToken()).query(Long.class).single() == 1L;
        if (!activeClaim) {
            throw new IllegalStateException("usage requires the active run claim");
        }
        UUID id = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                        INSERT INTO ai_usage_records (
                            id, user_id, agent_run_id, agent_step_id, operation_type, usage_type,
                            provider_key, product_key, model_tier, input_units, cached_input_units,
                            output_units, embedding_units, search_units, price_version, price_item_id,
                            cost_usd, duration_ms, created_at
                        ) VALUES (
                            :id, :userId, :runId, :stepId, :operationType, :usageType,
                            :providerKey, :productKey, :modelTier, :inputUnits, :cachedInputUnits,
                            :outputUnits, :embeddingUnits, :searchUnits, :priceVersion, :priceItemId,
                            :cost, :durationMs, :createdAt
                        )
                        """)
                .param("id", id).param("userId", command.userId())
                .param("runId", command.agentRunId()).param("stepId", command.agentStepId())
                .param("operationType", command.operationType()).param("usageType", command.usageType().name())
                .param("providerKey", command.providerKey()).param("productKey", command.productKey())
                .param("modelTier", command.modelTier() == null ? null : command.modelTier().name())
                .param("inputUnits", command.inputUnits()).param("cachedInputUnits", command.cachedInputUnits())
                .param("outputUnits", command.outputUnits()).param("embeddingUnits", command.embeddingUnits())
                .param("searchUnits", command.searchUnits()).param("priceVersion", command.priceVersion())
                .param("priceItemId", command.priceItemId()).param("cost", command.costUsd())
                .param("durationMs", command.durationMs())
                .param("createdAt", OffsetDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("usage record was not persisted");
        }
        int runUpdated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET actual_cost_usd = actual_cost_usd + :cost,
                            state_version = state_version + 1,
                            updated_at = :now
                        WHERE user_id = :userId AND id = :runId AND status = 'RUNNING'
                          AND claim_token = :claimToken
                        """)
                .param("cost", command.costUsd())
                .param("now", OffsetDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC))
                .param("userId", command.userId()).param("runId", command.agentRunId())
                .param("claimToken", command.claimToken()).update();
        if (runUpdated != 1) {
            throw new IllegalStateException("usage lost the active run claim");
        }
        runRepository.findByOwner(command.userId(), command.agentRunId()).ifPresent(run ->
                eventPublisher.publishAfterCommit(new AgentRunCommittedEvent(
                        AgentRunEventType.PROGRESS, run, null, command.occurredAt())));
        return id;
    }

    private void validate(UsageRecordCommand command) {
        if (command == null || command.userId() == null || command.agentRunId() == null
                || command.claimToken() == null
                || command.operationType() == null || command.operationType().isBlank()
                || command.usageType() == null || command.providerKey() == null
                || command.providerKey().isBlank() || command.productKey() == null
                || command.productKey().isBlank() || command.costUsd() == null
                || command.costUsd().signum() < 0 || command.durationMs() < 0
                || command.inputUnits() < 0 || command.cachedInputUnits() < 0
                || command.outputUnits() < 0 || command.embeddingUnits() < 0
                || command.searchUnits() < 0 || command.occurredAt() == null
                || (command.costUsd().signum() > 0
                    && (command.priceVersion() == null || command.priceItemId() == null))) {
            throw new IllegalArgumentException("usage record is invalid");
        }
    }
}
