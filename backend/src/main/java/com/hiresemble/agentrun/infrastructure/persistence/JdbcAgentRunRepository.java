package com.hiresemble.agentrun.infrastructure.persistence;

import com.hiresemble.agentrun.application.port.AgentRunCreationPort;
import com.hiresemble.agentrun.application.port.AgentRunEventPublisher;
import com.hiresemble.agentrun.application.port.AgentRunHistoryDeletionPort;
import com.hiresemble.agentrun.application.model.AgentRunEventType;
import com.hiresemble.agentrun.application.model.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.query.AgentRunListCriteria;
import com.hiresemble.agentrun.application.model.AgentRunPage;
import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.query.AgentRunSort;
import com.hiresemble.agentrun.application.model.AgentStepSnapshot;
import com.hiresemble.agentrun.application.model.ReusableStepSnapshot;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class JdbcAgentRunRepository
        implements AgentRunCreationPort, AgentRunQueryPort, AgentRunHistoryDeletionPort {

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
            long priceVersion,
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
                .param("priceVersion", priceVersion)
                .param("qualityMode", command.requestedQualityMode() == null
                        ? null : command.requestedQualityMode().name())
                .param("estimatedCost", java.math.BigDecimal.ZERO)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("queuedAt", utc(queuedAt))
                .update();
        if (command.resource() != null
                && Set.of(
                                "DOCUMENT",
                                "JOB",
                                "COVER_LETTER",
                                "QUESTION_SET",
                                "INTERVIEW_ANSWER_VERSION",
                                "GITHUB_SOURCE")
                        .contains(command.resource().resourceType())) {
            insertTypedLink(
                    command.userId(),
                    agentRunId,
                    command.resource().resourceType(),
                    command.resource().resourceId(),
                    queuedAt);
        }
        return findByOwner(command.userId(), agentRunId).orElseThrow();
    }

    @Override
    @Transactional
    public AgentRunSnapshot createRetry(
            UUID successorId,
            AgentRunSnapshot predecessor,
            long budgetPolicyVersion,
            long priceVersion,
            Instant queuedAt) {
        UUID retryVerificationId = "COVER_LETTER".equals(predecessor.resourceType())
                        && predecessor.workflowType()
                                == WorkflowType.COVER_LETTER_VERIFICATION
                ? UUID.randomUUID()
                : null;
        JsonNode retryInput = retryInput(predecessor, retryVerificationId);
        PartialResult retryPartialResult = retryPartialResult(predecessor);
        WorkflowLaunchCommand command = new WorkflowLaunchCommand(
                successorId,
                predecessor.userId(),
                predecessor.workflowType(),
                predecessor.workflowVersion(),
                predecessor.canonicalInputHash(),
                retryInput,
                predecessor.requestedQualityMode(),
                predecessor.resourceType() == null ? null : new ResourceReference(
                        predecessor.resourceType(), predecessor.resourceId(), null));
        return createRetry(
                successorId,
                predecessor,
                command,
                budgetPolicyVersion,
                priceVersion,
                queuedAt,
                retryPartialResult,
                retryVerificationId);
    }

    @Override
    @Transactional
    public AgentRunSnapshot createRetry(
            UUID successorId,
            AgentRunSnapshot predecessor,
            WorkflowLaunchCommand successorCommand,
            long budgetPolicyVersion,
            long priceVersion,
            Instant queuedAt) {
        return createRetry(
                successorId,
                predecessor,
                successorCommand,
                budgetPolicyVersion,
                priceVersion,
                queuedAt,
                null,
                null);
    }

    private AgentRunSnapshot createRetry(
            UUID successorId,
            AgentRunSnapshot predecessor,
            WorkflowLaunchCommand command,
            long budgetPolicyVersion,
            long priceVersion,
            Instant queuedAt,
            PartialResult retryPartialResult,
            UUID retryVerificationId) {
        requireCompatibleRetryCommand(successorId, predecessor, command);
        ResourceReference resource = command.resource();
        int inserted = jdbcClient.sql("""
                            INSERT INTO agent_runs (
                                id, user_id, workflow_type, status, current_step, progress_percent,
                                workflow_version, canonical_input_hash, input_reference_snapshot,
                                budget_policy_version, price_version, requested_quality_mode,
                                highest_model_tier_used, estimated_cost_usd, reserved_cost_usd,
                                actual_cost_usd, resource_type, resource_id, retry_of_run_id,
                                root_run_id, run_attempt_no, retryable_failure,
                                partial_result_json, state_version, queued_at, updated_at
                            ) VALUES (
                                :id, :userId, :workflowType, 'QUEUED', NULL, 0,
                                :workflowVersion, :inputHash, CAST(:inputRefs AS jsonb),
                                :budgetPolicyVersion, :priceVersion, :qualityMode,
                                NULL, :estimatedCost, 0, 0, :resourceType, :resourceId, :retryOf,
                                :rootRunId, :runAttemptNo, false,
                                CAST(:partialResult AS jsonb), 0, :queuedAt, :queuedAt
                            )
                            ON CONFLICT (user_id, retry_of_run_id)
                                WHERE retry_of_run_id IS NOT NULL
                            DO NOTHING
                            """)
                    .param("id", successorId)
                    .param("userId", predecessor.userId())
                    .param("workflowType", command.workflowType().name())
                    .param("workflowVersion", command.workflowVersion())
                    .param("inputHash", command.canonicalInputHash())
                    .param("inputRefs", mapper.write(command.inputReferenceSnapshot()))
                    .param("budgetPolicyVersion", budgetPolicyVersion)
                    .param("priceVersion", priceVersion)
                    .param("qualityMode", command.requestedQualityMode() == null
                            ? null : command.requestedQualityMode().name())
                    .param("estimatedCost", java.math.BigDecimal.ZERO)
                    .param("resourceType", resource == null ? null : resource.resourceType())
                    .param("resourceId", resource == null ? null : resource.resourceId())
                    .param("retryOf", predecessor.id())
                    .param("rootRunId", predecessor.rootRunId())
                    .param("runAttemptNo", predecessor.runAttemptNo() + 1)
                    .param(
                            "partialResult",
                            retryPartialResult == null
                                    ? null
                                    : mapper.write(retryPartialResult))
                    .param("queuedAt", utc(queuedAt))
                    .update();
        if (inserted == 1) {
            if ("DOCUMENT".equals(predecessor.resourceType())) {
                int linked = jdbcClient.sql("""
                                INSERT INTO agent_run_resource_links (
                                    id,user_id,agent_run_id,resource_kind,document_id,primary_resource,created_at
                                )
                                SELECT :id,user_id,:successorId,resource_kind,document_id,true,:createdAt
                                FROM agent_run_resource_links
                                WHERE user_id=:userId AND agent_run_id=:predecessorId
                                  AND resource_kind='DOCUMENT' AND primary_resource
                                """)
                        .param("id", UUID.randomUUID())
                        .param("successorId", successorId)
                        .param("createdAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("predecessorId", predecessor.id())
                        .update();
                if (linked != 1) {
                    throw new IllegalStateException("document retry is missing its typed resource link");
                }
                int attached = jdbcClient.sql("""
                                UPDATE documents SET latest_agent_run_id=:successorId,
                                    version=version+1,updated_at=:updatedAt
                                WHERE user_id=:userId AND id=:documentId AND deleted_at IS NULL
                                """)
                        .param("successorId", successorId)
                        .param("updatedAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("documentId", predecessor.resourceId())
                        .update();
                if (attached != 1) {
                    throw new IllegalStateException("document retry resource is not active");
                }
            } else if ("GITHUB_SOURCE".equals(predecessor.resourceType())) {
                if (predecessor.workflowType() != WorkflowType.GITHUB_INGESTION) {
                    throw new IllegalStateException(
                            "unsupported workflow owns a GitHub source retry resource");
                }
                int linked = jdbcClient.sql("""
                                INSERT INTO agent_run_resource_links (
                                    id,user_id,agent_run_id,resource_kind,github_source_id,
                                    primary_resource,created_at
                                )
                                SELECT :id,user_id,:successorId,resource_kind,github_source_id,
                                       true,:createdAt
                                FROM agent_run_resource_links
                                WHERE user_id=:userId AND agent_run_id=:predecessorId
                                  AND resource_kind='GITHUB_SOURCE' AND primary_resource
                                """)
                        .param("id", UUID.randomUUID())
                        .param("successorId", successorId)
                        .param("createdAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("predecessorId", predecessor.id())
                        .update();
                if (linked != 1) {
                    throw new IllegalStateException(
                            "GitHub source retry is missing its typed resource link");
                }
                int attached = jdbcClient.sql("""
                                UPDATE github_sources
                                SET latest_agent_run_id=:successorId,source_status='QUEUED',
                                    version=version+1,updated_at=:updatedAt
                                WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                                  AND source_status='FAILED'
                                """)
                        .param("successorId", successorId)
                        .param("updatedAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("sourceId", predecessor.resourceId())
                        .update();
                if (attached != 1) {
                    throw new IllegalStateException("GitHub retry source is not retryable");
                }
            } else if ("JOB".equals(predecessor.resourceType())) {
                int linked = jdbcClient.sql("""
                                INSERT INTO agent_run_resource_links (
                                    id,user_id,agent_run_id,resource_kind,job_posting_id,
                                    primary_resource,created_at
                                )
                                SELECT :id,user_id,:successorId,resource_kind,job_posting_id,
                                       true,:createdAt
                                FROM agent_run_resource_links
                                WHERE user_id=:userId AND agent_run_id=:predecessorId
                                  AND resource_kind='JOB' AND primary_resource
                                """)
                        .param("id", UUID.randomUUID())
                        .param("successorId", successorId)
                        .param("createdAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("predecessorId", predecessor.id())
                        .update();
                if (linked != 1) {
                    throw new IllegalStateException("job retry is missing its typed resource link");
                }
                if (predecessor.workflowType() == WorkflowType.JOB_POSTING_EXTRACTION) {
                    int attached = jdbcClient.sql("""
                                    UPDATE job_postings SET latest_agent_run_id=:successorId,
                                        extraction_status='QUEUED',
                                        version=version+1,updated_at=:updatedAt
                                    WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                                    """)
                            .param("successorId", successorId)
                            .param("updatedAt", utc(queuedAt))
                            .param("userId", predecessor.userId())
                            .param("jobId", predecessor.resourceId())
                            .update();
                    if (attached != 1) {
                        throw new IllegalStateException("job retry resource is not active");
                    }
                } else if (predecessor.workflowType() == WorkflowType.JOB_ANALYSIS) {
                    boolean active = jdbcClient.sql("""
                                    SELECT EXISTS (
                                        SELECT 1 FROM job_postings
                                        WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                                    )
                                    """)
                            .param("userId", predecessor.userId())
                            .param("jobId", predecessor.resourceId())
                            .query(Boolean.class)
                            .single();
                    if (!active) {
                        throw new IllegalStateException("job analysis retry resource is not active");
                    }
                } else {
                    throw new IllegalStateException(
                            "unsupported workflow owns a typed job retry resource");
                }
            } else if ("COVER_LETTER".equals(predecessor.resourceType())) {
                int linked = jdbcClient.sql("""
                                INSERT INTO agent_run_resource_links (
                                    id,user_id,agent_run_id,resource_kind,cover_letter_id,
                                    primary_resource,created_at
                                )
                                SELECT :id,user_id,:successorId,resource_kind,cover_letter_id,
                                       true,:createdAt
                                FROM agent_run_resource_links
                                WHERE user_id=:userId AND agent_run_id=:predecessorId
                                  AND resource_kind='COVER_LETTER' AND primary_resource
                                """)
                        .param("id", UUID.randomUUID())
                        .param("successorId", successorId)
                        .param("createdAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("predecessorId", predecessor.id())
                        .update();
                if (linked != 1) {
                    throw new IllegalStateException(
                            "cover letter retry is missing its typed resource link");
                }
                boolean active = jdbcClient.sql("""
                                SELECT EXISTS (
                                    SELECT 1 FROM cover_letters
                                    WHERE user_id=:userId AND id=:coverLetterId
                                      AND status IN ('DRAFT','FINALIZED')
                                )
                                """)
                        .param("userId", predecessor.userId())
                        .param("coverLetterId", predecessor.resourceId())
                        .query(Boolean.class)
                        .single();
                if (!active) {
                    throw new IllegalStateException("cover letter retry resource is not active");
                }
                if (predecessor.workflowType() == WorkflowType.COVER_LETTER_VERIFICATION) {
                    copyVerificationRetryState(
                            predecessor,
                            successorId,
                            retryVerificationId,
                            queuedAt);
                } else if (predecessor.workflowType()
                        == WorkflowType.COVER_LETTER_GENERATION) {
                    copyGenerationRetryResults(predecessor, successorId, queuedAt);
                } else {
                    throw new IllegalStateException(
                            "unsupported workflow owns a typed cover letter retry resource");
                }
            } else if ("INTERVIEW_ANSWER_VERSION".equals(predecessor.resourceType())) {
                if (predecessor.workflowType() != WorkflowType.INTERVIEW_ANSWER_FEEDBACK) {
                    throw new IllegalStateException(
                            "unsupported workflow owns an interview answer retry resource");
                }
                int linked = jdbcClient.sql("""
                                INSERT INTO agent_run_resource_links (
                                    id,user_id,agent_run_id,resource_kind,
                                    interview_answer_version_id,primary_resource,created_at
                                )
                                SELECT :id,user_id,:successorId,resource_kind,
                                       interview_answer_version_id,true,:createdAt
                                FROM agent_run_resource_links
                                WHERE user_id=:userId AND agent_run_id=:predecessorId
                                  AND resource_kind='INTERVIEW_ANSWER_VERSION'
                                  AND primary_resource
                                """)
                        .param("id", UUID.randomUUID())
                        .param("successorId", successorId)
                        .param("createdAt", utc(queuedAt))
                        .param("userId", predecessor.userId())
                        .param("predecessorId", predecessor.id())
                        .update();
                if (linked != 1) {
                    throw new IllegalStateException(
                            "interview answer retry is missing its typed resource link");
                }
                boolean active = jdbcClient.sql("""
                                SELECT EXISTS (
                                    SELECT 1 FROM interview_answer_versions
                                    WHERE user_id=:userId AND id=:answerVersionId
                                )
                                """)
                        .param("userId", predecessor.userId())
                        .param("answerVersionId", predecessor.resourceId())
                        .query(Boolean.class)
                        .single();
                if (!active) {
                    throw new IllegalStateException(
                            "interview answer retry resource is missing");
                }
            }
            return findByOwner(predecessor.userId(), successorId).orElseThrow();
        }
        AgentRunSnapshot existing = jdbcClient.sql("SELECT " + AgentRunJdbcMapper.RUN_COLUMNS
                        + " FROM agent_runs r WHERE r.user_id = :userId"
                        + " AND r.retry_of_run_id = :predecessorId AND r.deleted_at IS NULL")
                .param("userId", predecessor.userId())
                .param("predecessorId", predecessor.id())
                .query((rs, row) -> mapper.run(rs, List.of()))
                .optional()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED));
        if (!compatibleRetry(existing, command)) {
            throw new BusinessException(ErrorCode.AGENT_RUN_RETRY_ALREADY_CREATED);
        }
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentRunSnapshot> findByOwner(UUID userId, UUID agentRunId) {
        return findRun(userId, agentRunId).map(run -> withSteps(run, findSteps(userId, agentRunId)));
    }

    @Override
    @Transactional
    public void softDeleteTerminalRuns(
            UUID userId, Set<UUID> agentRunIds, Instant deletedAt) {
        List<DeletionCandidate> candidates = jdbcClient.sql("""
                        SELECT id,status
                        FROM agent_runs
                        WHERE user_id=:userId AND id IN (:agentRunIds) AND deleted_at IS NULL
                        FOR UPDATE
                        """)
                .param("userId", userId)
                .param("agentRunIds", agentRunIds)
                .query((rs, row) -> new DeletionCandidate(
                        rs.getObject("id", UUID.class),
                        AgentRunStatus.valueOf(rs.getString("status"))))
                .list();
        if (candidates.size() != agentRunIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (candidates.stream().anyMatch(candidate -> !candidate.status().isTerminal())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        int updated = jdbcClient.sql("""
                        UPDATE agent_runs
                        SET deleted_at=:deletedAt
                        WHERE user_id=:userId AND id IN (:agentRunIds) AND deleted_at IS NULL
                        """)
                .param("deletedAt", utc(deletedAt))
                .param("userId", userId)
                .param("agentRunIds", agentRunIds)
                .update();
        if (updated != agentRunIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
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
                + " FROM agent_runs r WHERE r.user_id = :userId AND r.id = :agentRunId"
                + " AND r.deleted_at IS NULL";
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

    private boolean compatibleRetry(
            AgentRunSnapshot successor, WorkflowLaunchCommand command) {
        ResourceReference resource = command.resource();
        return successor.workflowType() == command.workflowType()
                && successor.workflowVersion().equals(command.workflowVersion())
                && successor.canonicalInputHash().equals(command.canonicalInputHash())
                && successor.requestedQualityMode() == command.requestedQualityMode()
                && java.util.Objects.equals(
                        successor.resourceType(), resource == null ? null : resource.resourceType())
                && java.util.Objects.equals(
                        successor.resourceId(), resource == null ? null : resource.resourceId());
    }

    private void requireCompatibleRetryCommand(
            UUID successorId,
            AgentRunSnapshot predecessor,
            WorkflowLaunchCommand command) {
        ResourceReference resource = command.resource();
        boolean resourceMismatch = predecessor.resourceType() == null
                ? resource != null
                : resource == null
                        || !java.util.Objects.equals(
                                predecessor.resourceType(), resource.resourceType())
                        || !java.util.Objects.equals(
                                predecessor.resourceId(), resource.resourceId());
        if (command.requestedAgentRunId() != null
                        && !successorId.equals(command.requestedAgentRunId())
                || !predecessor.userId().equals(command.userId())
                || predecessor.workflowType() != command.workflowType()
                || resourceMismatch) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }

    private void insertTypedLink(
            UUID userId, UUID runId, String resourceType, UUID resourceId, Instant createdAt) {
        String resourceColumn = switch (resourceType) {
            case "DOCUMENT" -> "document_id";
            case "JOB" -> "job_posting_id";
            case "COVER_LETTER" -> "cover_letter_id";
            case "QUESTION_SET" -> "question_set_id";
            case "INTERVIEW_ANSWER_VERSION" -> "interview_answer_version_id";
            case "GITHUB_SOURCE" -> "github_source_id";
            default -> throw new IllegalArgumentException("unsupported typed resource");
        };
        String insertSql = """
                INSERT INTO agent_run_resource_links (
                    id,user_id,agent_run_id,resource_kind,%s,primary_resource,created_at
                ) VALUES (:id,:userId,:runId,:resourceType,:resourceId,true,:createdAt)
                """.formatted(resourceColumn);
        int inserted = jdbcClient.sql(insertSql)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("runId", runId)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("createdAt", utc(createdAt))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("typed Agent Run resource link was not created");
        }
    }

    private JsonNode retryInput(AgentRunSnapshot predecessor, UUID retryVerificationId) {
        if ("COVER_LETTER".equals(predecessor.resourceType())
                && predecessor.workflowType() == WorkflowType.COVER_LETTER_GENERATION) {
            return generationRetryInput(predecessor);
        }
        if (retryVerificationId == null) {
            return predecessor.inputReferenceSnapshot();
        }
        if (!(predecessor.inputReferenceSnapshot() instanceof ObjectNode objectNode)) {
            throw new IllegalStateException(
                    "cover letter verification retry input must be an object");
        }
        ObjectNode copied = objectNode.deepCopy();
        copied.put("verificationId", retryVerificationId.toString());
        return copied;
    }

    private JsonNode generationRetryInput(AgentRunSnapshot predecessor) {
        if (!(predecessor.inputReferenceSnapshot() instanceof ObjectNode objectNode)) {
            throw new IllegalStateException(
                    "cover letter generation retry input must be an object");
        }
        long originalVersion = objectNode.path("coverLetterVersion").asLong(-1);
        if (originalVersion < 0) {
            throw new IllegalStateException(
                    "cover letter generation retry is missing its accepted version");
        }
        int lineageAppliedAnswers = jdbcClient.sql("""
                        SELECT count(DISTINCT verification.answer_version_id)
                        FROM agent_runs lineage
                        JOIN cover_letter_verifications verification
                          ON verification.user_id=lineage.user_id
                         AND verification.agent_run_id=lineage.id
                        WHERE lineage.user_id=:userId
                          AND lineage.root_run_id=:rootRunId
                          AND lineage.workflow_type='COVER_LETTER_GENERATION'
                          AND lineage.run_attempt_no <= :runAttemptNo
                        """)
                .param("userId", predecessor.userId())
                .param("rootRunId", predecessor.rootRunId())
                .param("runAttemptNo", predecessor.runAttemptNo())
                .query(Integer.class)
                .single();
        long currentVersion = jdbcClient.sql("""
                        SELECT version FROM cover_letters
                        WHERE user_id=:userId AND id=:coverLetterId
                        """)
                .param("userId", predecessor.userId())
                .param("coverLetterId", predecessor.resourceId())
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (currentVersion != originalVersion + lineageAppliedAnswers) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return predecessor.inputReferenceSnapshot();
    }

    private PartialResult retryPartialResult(AgentRunSnapshot predecessor) {
        if (!"COVER_LETTER".equals(predecessor.resourceType())
                || predecessor.workflowType() != WorkflowType.COVER_LETTER_GENERATION
                || predecessor.partialResult() == null) {
            return null;
        }
        PartialResult previous = predecessor.partialResult();
        if (previous.succeededScopeKeys().isEmpty() && previous.resultRefs().isEmpty()) {
            return null;
        }
        return new PartialResult(
                previous.succeededScopeKeys(),
                List.of(),
                previous.resultRefs());
    }

    private void copyGenerationRetryResults(
            AgentRunSnapshot predecessor, UUID successorId, Instant queuedAt) {
        List<UUID> answerVersionIds = jdbcClient.sql("""
                        SELECT DISTINCT link.cover_letter_answer_version_id
                        FROM agent_run_resource_links link
                        WHERE link.user_id=:userId AND link.agent_run_id=:predecessorId
                          AND link.resource_kind='COVER_LETTER_ANSWER_VERSION'
                        ORDER BY link.cover_letter_answer_version_id
                        """)
                .param("userId", predecessor.userId())
                .param("predecessorId", predecessor.id())
                .query(UUID.class)
                .list();
        for (UUID answerVersionId : answerVersionIds) {
            jdbcClient.sql("""
                            INSERT INTO agent_run_resource_links (
                                id,user_id,agent_run_id,resource_kind,
                                cover_letter_answer_version_id,primary_resource,created_at
                            ) VALUES (
                                :id,:userId,:successorId,'COVER_LETTER_ANSWER_VERSION',
                                :answerVersionId,false,:createdAt
                            )
                            """)
                    .param("id", UUID.randomUUID())
                    .param("userId", predecessor.userId())
                    .param("successorId", successorId)
                    .param("answerVersionId", answerVersionId)
                    .param("createdAt", utc(queuedAt))
                    .update();
        }
    }

    private void copyVerificationRetryState(
            AgentRunSnapshot predecessor,
            UUID successorId,
            UUID verificationId,
            Instant queuedAt) {
        int answerLinked = jdbcClient.sql("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,
                            cover_letter_answer_version_id,primary_resource,created_at
                        )
                        SELECT :id,user_id,:successorId,resource_kind,
                               cover_letter_answer_version_id,false,:createdAt
                        FROM agent_run_resource_links
                        WHERE user_id=:userId AND agent_run_id=:predecessorId
                          AND resource_kind='COVER_LETTER_ANSWER_VERSION'
                          AND NOT primary_resource
                        """)
                .param("id", UUID.randomUUID())
                .param("successorId", successorId)
                .param("createdAt", utc(queuedAt))
                .param("userId", predecessor.userId())
                .param("predecessorId", predecessor.id())
                .update();
        if (answerLinked != 1) {
            throw new IllegalStateException(
                    "cover letter verification retry requires one answer version link");
        }
        int pendingInserted = jdbcClient.sql("""
                        INSERT INTO cover_letter_verifications (
                            id,user_id,answer_version_id,agent_run_id,status,
                            issues,suggestions,verified_claims,created_at
                        )
                        SELECT :verificationId,user_id,cover_letter_answer_version_id,
                               :successorId,'PENDING','[]'::jsonb,'[]'::jsonb,
                               '[]'::jsonb,:createdAt
                        FROM agent_run_resource_links
                        WHERE user_id=:userId AND agent_run_id=:successorId
                          AND resource_kind='COVER_LETTER_ANSWER_VERSION'
                          AND NOT primary_resource
                        """)
                .param("verificationId", verificationId)
                .param("successorId", successorId)
                .param("createdAt", utc(queuedAt))
                .param("userId", predecessor.userId())
                .update();
        if (pendingInserted != 1) {
            throw new IllegalStateException(
                    "cover letter verification retry pending state was not created");
        }
    }

    private String where(AgentRunListCriteria criteria, Map<String, Object> parameters) {
        StringBuilder where =
                new StringBuilder("WHERE r.user_id = :userId AND r.deleted_at IS NULL");
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

    private record DeletionCandidate(UUID id, AgentRunStatus status) {}
}
