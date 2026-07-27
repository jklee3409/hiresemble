package com.hiresemble.job.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.domain.ClosedReason;
import com.hiresemble.job.domain.DeadlineSource;
import com.hiresemble.job.domain.JobCommands.CreateJob;
import com.hiresemble.job.domain.JobCommands.JobListQuery;
import com.hiresemble.job.domain.JobDescriptionSource;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobHistoryActor;
import com.hiresemble.job.domain.JobRecords.JobPage;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.domain.JobRecords.StatusChange;
import com.hiresemble.job.domain.JobStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JobStore {

    private static final String SELECT_COLUMNS = """
            j.*, c.display_name AS company_name,
            ar.error_code AS extraction_error_code,
            ar.error_message_safe AS extraction_error_message
            """;
    private final JdbcClient jdbc;

    public JobStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public JobRecord create(CreateJob command, Instant now) {
        if (existsActiveCanonical(command.userId(), command.canonicalUrl())) {
            throw new BusinessException(ErrorCode.DUPLICATE_JOB_URL);
        }
        UUID companyId = upsertCompany(command.companyName(), now);
        String descriptionSource = command.descriptionText() == null ? null : "USER_ENTERED";
        String deadlineSource = command.deadlineAt() == null ? "UNKNOWN" : "USER_ENTERED";
        String extractionStatus =
                command.descriptionText() == null ? "QUEUED" : "MANUAL_INPUT_PROVIDED";
        try {
            jdbc.sql("""
                            INSERT INTO job_postings (
                                id,user_id,company_id,source_url,canonical_url,title,position_name,
                                role_category,employment_type,location,description_text,
                                description_source,deadline_at,deadline_source,deadline_confidence,
                                status,extraction_status,submitted_at,closed_at,closed_reason,
                                content_hash,latest_agent_run_id,company_user_override,
                                title_user_override,position_user_override,deadline_user_override,
                                version,created_at,updated_at,deleted_at
                            ) VALUES (
                                :id,:userId,:companyId,:sourceUrl,:canonicalUrl,NULL,:positionName,
                                NULL,NULL,NULL,:descriptionText,:descriptionSource,:deadlineAt,
                                :deadlineSource,NULL,'IN_PROGRESS',:extractionStatus,NULL,NULL,NULL,
                                :contentHash,NULL,:companyOverride,false,:positionOverride,
                                :deadlineOverride,0,:now,:now,NULL
                            )
                            """)
                    .param("id", command.jobId())
                    .param("userId", command.userId())
                    .param("companyId", companyId)
                    .param("sourceUrl", command.sourceUrl())
                    .param("canonicalUrl", command.canonicalUrl())
                    .param("positionName", command.positionName())
                    .param("descriptionText", command.descriptionText())
                    .param("descriptionSource", descriptionSource)
                    .param("deadlineAt", utc(command.deadlineAt()))
                    .param("deadlineSource", deadlineSource)
                    .param("extractionStatus", extractionStatus)
                    .param("contentHash", com.hiresemble.job.domain.JobPolicy.contentHash(
                            command.descriptionText()))
                    .param("companyOverride", command.companyName() != null)
                    .param("positionOverride", command.positionName() != null)
                    .param("deadlineOverride", command.deadlineAt() != null)
                    .param("now", utc(now))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            if (causedByConstraint(
                    exception, "23505", "job_postings_active_canonical_url_uk")) {
                throw new BusinessException(ErrorCode.DUPLICATE_JOB_URL, exception);
            }
            throw exception;
        }
        insertHistory(
                command.userId(),
                command.jobId(),
                null,
                JobStatus.IN_PROGRESS,
                "CREATED",
                JobHistoryActor.SYSTEM,
                now);
        return findActive(command.userId(), command.jobId()).orElseThrow();
    }

    public boolean existsActiveCanonical(UUID userId, String canonicalUrl) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM job_postings
                            WHERE user_id=:userId AND canonical_url=:canonicalUrl
                              AND deleted_at IS NULL
                        )
                        """)
                .param("userId", userId)
                .param("canonicalUrl", canonicalUrl)
                .query(Boolean.class)
                .single();
    }

    public Optional<JobRecord> findActive(UUID userId, UUID jobId) {
        return queryOne("""
                WHERE j.user_id=:userId AND j.id=:jobId AND j.deleted_at IS NULL
                """, Map.of("userId", userId, "jobId", jobId), false);
    }

    public Optional<JobRecord> lockActive(UUID userId, UUID jobId) {
        return queryOne("""
                WHERE j.user_id=:userId AND j.id=:jobId AND j.deleted_at IS NULL
                """, Map.of("userId", userId, "jobId", jobId), true);
    }

    public Optional<JobRecord> lockDue(UUID jobId, Instant deadlineCutoff) {
        return queryOne("""
                WHERE j.id=:jobId AND j.deleted_at IS NULL
                  AND j.status IN ('IN_PROGRESS','SUBMITTED')
                  AND j.deadline_at <= :deadlineCutoff
                """, Map.of("jobId", jobId, "deadlineCutoff", utc(deadlineCutoff)), true);
    }

    public JobPage list(
            UUID userId,
            JobListQuery criteria,
            Instant relativeDeadlineFrom,
            Instant relativeDeadlineTo) {
        StringBuilder where = new StringBuilder("j.user_id=:userId AND j.deleted_at IS NULL");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("userId", userId);
        if (criteria.status() != null) {
            where.append(" AND j.status=:status");
            parameters.put("status", criteria.status().name());
        }
        if (criteria.extractionStatus() != null) {
            where.append(" AND j.extraction_status=:extractionStatus");
            parameters.put("extractionStatus", criteria.extractionStatus().name());
        }
        if (criteria.query() != null) {
            where.append("""
                     AND (
                         lower(COALESCE(c.display_name,'')) LIKE :query ESCAPE '\\'
                         OR lower(COALESCE(j.title,'')) LIKE :query ESCAPE '\\'
                         OR lower(COALESCE(j.position_name,'')) LIKE :query ESCAPE '\\'
                     )
                    """);
            parameters.put("query", "%" + escapeLike(criteria.query().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        if (criteria.deadlineFrom() != null) {
            where.append(" AND j.deadline_at >= :deadlineFrom");
            parameters.put("deadlineFrom", utc(criteria.deadlineFrom()));
        }
        if (criteria.deadlineTo() != null) {
            where.append(" AND j.deadline_at <= :deadlineTo");
            parameters.put("deadlineTo", utc(criteria.deadlineTo()));
        }
        if (relativeDeadlineTo != null) {
            where.append("""
                     AND j.deadline_at IS NOT NULL
                     AND j.deadline_at >= :relativeDeadlineFrom
                     AND j.deadline_at <= :relativeDeadlineTo
                    """);
            parameters.put("relativeDeadlineFrom", utc(relativeDeadlineFrom));
            parameters.put("relativeDeadlineTo", utc(relativeDeadlineTo));
        }
        String countSql = """
                SELECT count(*)
                FROM job_postings j
                LEFT JOIN companies c ON c.id=j.company_id
                WHERE %s
                """.formatted(where);
        long total = jdbc.sql(countSql)
                .params(parameters)
                .query(Long.class)
                .single();
        parameters.put("limit", criteria.size());
        parameters.put("offset", (long) criteria.page() * criteria.size());
        String order = switch (criteria.sort()) {
            case "createdAt,desc" -> "j.created_at DESC, j.id DESC";
            case "deadlineAt,asc" -> "j.deadline_at ASC NULLS LAST, j.id ASC";
            case "updatedAt,desc" -> "j.updated_at DESC, j.id DESC";
            default -> throw new IllegalArgumentException("unsupported job sort");
        };
        String listSql = """
                SELECT %s
                FROM job_postings j
                LEFT JOIN companies c ON c.id=j.company_id
                LEFT JOIN agent_runs ar
                  ON ar.user_id=j.user_id AND ar.id=j.latest_agent_run_id
                WHERE %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(SELECT_COLUMNS, where, order);
        List<JobRecord> items = jdbc.sql(listSql)
                .params(parameters)
                .query(this::job)
                .list();
        int totalPages = total == 0 ? 0 : (int) ((total + criteria.size() - 1) / criteria.size());
        return new JobPage(items, criteria.page(), criteria.size(), total, totalPages);
    }

    public Optional<JobRecord> updateUserFields(
            UUID userId,
            UUID jobId,
            long expectedVersion,
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            JobExtractionStatus extractionStatus,
            Instant now) {
        UUID companyId = upsertCompany(companyName, now);
        return jdbc.sql("""
                        UPDATE job_postings SET
                            company_id=:companyId,title=:title,position_name=:positionName,
                            description_text=:descriptionText,
                            description_source=:descriptionSource,
                            deadline_at=:deadlineAt,deadline_source=:deadlineSource,
                            deadline_confidence=NULL,
                            extraction_status=:extractionStatus,
                            content_hash=:contentHash,
                            company_user_override=:companyOverride,
                            title_user_override=:titleOverride,
                            position_user_override=:positionOverride,
                            deadline_user_override=:deadlineOverride,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                          AND version=:expectedVersion
                        RETURNING id
                        """)
                .param("companyId", companyId)
                .param("title", title)
                .param("positionName", positionName)
                .param("descriptionText", descriptionText)
                .param("descriptionSource", descriptionText == null ? null : "USER_ENTERED")
                .param("deadlineAt", utc(deadlineAt))
                .param("deadlineSource", deadlineAt == null ? "UNKNOWN" : "USER_ENTERED")
                .param("extractionStatus", extractionStatus.name())
                .param("contentHash", com.hiresemble.job.domain.JobPolicy.contentHash(descriptionText))
                .param("companyOverride", companyName != null)
                .param("titleOverride", title != null)
                .param("positionOverride", positionName != null)
                .param("deadlineOverride", deadlineAt != null)
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("expectedVersion", expectedVersion)
                .query(UUID.class)
                .optional()
                .flatMap(ignored -> findActive(userId, jobId));
    }

    public Optional<JobRecord> applyStatus(
            UUID userId,
            UUID jobId,
            long expectedVersion,
            StatusChange change,
            String reason,
            JobHistoryActor actor,
            Instant now) {
        Optional<UUID> updated = jdbc.sql("""
                        UPDATE job_postings SET status=:status,submitted_at=:submittedAt,
                            closed_at=:closedAt,closed_reason=:closedReason,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                          AND version=:expectedVersion AND status=:fromStatus
                        RETURNING id
                        """)
                .param("status", change.toStatus().name())
                .param("submittedAt", utc(change.submittedAt()))
                .param("closedAt", utc(change.closedAt()))
                .param("closedReason", change.closedReason() == null ? null : change.closedReason().name())
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("expectedVersion", expectedVersion)
                .param("fromStatus", change.fromStatus().name())
                .query(UUID.class)
                .optional();
        if (updated.isEmpty()) {
            return Optional.empty();
        }
        insertHistory(userId, jobId, change.fromStatus(), change.toStatus(), reason, actor, now);
        return findActive(userId, jobId);
    }

    public void attachLatestRun(UUID userId, UUID jobId, UUID runId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE job_postings SET latest_agent_run_id=:runId,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                        """)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("job disappeared during run attachment");
        }
    }

    public Optional<JobRecord> markExtracting(
            UUID userId, UUID jobId, UUID runId, long expectedVersion, Instant now) {
        return workflowState(userId, jobId, runId, expectedVersion, "EXTRACTING", now);
    }

    public Optional<JobRecord> queueExistingRun(
            UUID userId, UUID jobId, UUID runId, long expectedVersion, Instant now) {
        return workflowState(userId, jobId, runId, expectedVersion, "QUEUED", now);
    }

    public Optional<JobRecord> markNeedsManual(
            UUID userId, UUID jobId, UUID runId, long expectedVersion, Instant now) {
        return workflowState(userId, jobId, runId, expectedVersion, "NEEDS_MANUAL_INPUT", now);
    }

    public Optional<JobRecord> markFailed(
            UUID userId, UUID jobId, UUID runId, long expectedVersion, Instant now) {
        return workflowState(userId, jobId, runId, expectedVersion, "FAILED", now);
    }

    public Optional<JobRecord> applyExtraction(
            UUID userId,
            UUID jobId,
            UUID runId,
            long expectedVersion,
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            BigDecimal deadlineConfidence,
            String roleCategory,
            String employmentType,
            String location,
            Instant now) {
        JobRecord current = lockActive(userId, jobId).orElseThrow();
        if (current.version() != expectedVersion || !runId.equals(current.latestAgentRunId())) {
            return Optional.empty();
        }
        String mergedCompany = current.companyUserOverride() ? current.companyName() : companyName;
        UUID companyId = upsertCompany(mergedCompany, now);
        String mergedTitle = current.titleUserOverride() ? current.title() : title;
        String mergedPosition = current.positionUserOverride() ? current.positionName() : positionName;
        String mergedDescription = current.descriptionSource() == JobDescriptionSource.USER_ENTERED
                ? current.descriptionText()
                : descriptionText;
        Instant mergedDeadline = current.deadlineUserOverride() ? current.deadlineAt() : deadlineAt;
        String deadlineSource = current.deadlineUserOverride()
                ? "USER_ENTERED"
                : mergedDeadline == null ? "UNKNOWN" : "AUTO_EXTRACTED";
        BigDecimal mergedConfidence =
                "AUTO_EXTRACTED".equals(deadlineSource) ? deadlineConfidence : null;
        String descriptionSource = current.descriptionSource() == JobDescriptionSource.USER_ENTERED
                ? "USER_ENTERED"
                : mergedDescription == null ? null : "AUTO_EXTRACTED";
        JobExtractionStatus status = current.descriptionSource() == JobDescriptionSource.USER_ENTERED
                ? JobExtractionStatus.MANUAL_INPUT_PROVIDED
                : JobExtractionStatus.EXTRACTED;
        Optional<UUID> updated = jdbc.sql("""
                        UPDATE job_postings SET company_id=:companyId,title=:title,
                            position_name=:positionName,role_category=:roleCategory,
                            employment_type=:employmentType,location=:location,
                            description_text=:descriptionText,description_source=:descriptionSource,
                            deadline_at=:deadlineAt,deadline_source=:deadlineSource,
                            deadline_confidence=:deadlineConfidence,extraction_status=:status,
                            content_hash=:contentHash,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId AND version=:expectedVersion
                        RETURNING id
                        """)
                .param("companyId", companyId)
                .param("title", mergedTitle)
                .param("positionName", mergedPosition)
                .param("roleCategory", roleCategory)
                .param("employmentType", employmentType)
                .param("location", location)
                .param("descriptionText", mergedDescription)
                .param("descriptionSource", descriptionSource)
                .param("deadlineAt", utc(mergedDeadline))
                .param("deadlineSource", deadlineSource)
                .param("deadlineConfidence", mergedConfidence)
                .param("status", status.name())
                .param("contentHash", com.hiresemble.job.domain.JobPolicy.contentHash(mergedDescription))
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("runId", runId)
                .param("expectedVersion", expectedVersion)
                .query(UUID.class)
                .optional();
        return updated.flatMap(ignored -> findActive(userId, jobId));
    }

    public void compensateStable(UUID userId, UUID jobId, UUID runId, Instant now) {
        jdbc.sql("""
                        UPDATE job_postings SET extraction_status=CASE
                            WHEN description_text IS NULL THEN 'NEEDS_MANUAL_INPUT'
                            WHEN description_source='USER_ENTERED' THEN 'MANUAL_INPUT_PROVIDED'
                            ELSE 'EXTRACTED' END,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND latest_agent_run_id=:runId
                          AND deleted_at IS NULL
                          AND extraction_status IN ('QUEUED','EXTRACTING')
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("runId", runId)
                .update();
    }

    public boolean softDelete(UUID userId, UUID jobId, long expectedVersion, Instant now) {
        return jdbc.sql("""
                        UPDATE job_postings SET deleted_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND version=:expectedVersion
                          AND deleted_at IS NULL
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("expectedVersion", expectedVersion)
                .update() == 1;
    }

    public List<UUID> findDueIds(Instant deadlineCutoff, UUID afterId, int limit) {
        String cursorPredicate = afterId == null ? "" : " AND id > :afterId";
        String sql = """
                SELECT id FROM job_postings
                WHERE deleted_at IS NULL
                  AND status IN ('IN_PROGRESS','SUBMITTED')
                  AND deadline_at <= :deadlineCutoff%s
                ORDER BY id
                LIMIT :limit
                """.formatted(cursorPredicate);
        var statement = jdbc.sql(sql)
                .param("deadlineCutoff", utc(deadlineCutoff))
                .param("limit", limit);
        if (afterId != null) {
            statement.param("afterId", afterId);
        }
        return statement
                .query(UUID.class)
                .list();
    }

    public long historyCount(UUID userId, UUID jobId) {
        return jdbc.sql("""
                        SELECT count(*) FROM job_status_history
                        WHERE user_id=:userId AND job_posting_id=:jobId
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query(Long.class)
                .single();
    }

    private Optional<JobRecord> workflowState(
            UUID userId,
            UUID jobId,
            UUID runId,
            long expectedVersion,
            String status,
            Instant now) {
        return jdbc.sql("""
                        UPDATE job_postings SET extraction_status=:status,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId AND version=:expectedVersion
                        RETURNING id
                        """)
                .param("status", status)
                .param("now", utc(now))
                .param("userId", userId)
                .param("jobId", jobId)
                .param("runId", runId)
                .param("expectedVersion", expectedVersion)
                .query(UUID.class)
                .optional()
                .flatMap(id -> findActive(userId, id));
    }

    private Optional<JobRecord> queryOne(
            String where, Map<String, ?> parameters, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE OF j" : "";
        String selectSql = """
                SELECT %s
                FROM job_postings j
                LEFT JOIN companies c ON c.id=j.company_id
                LEFT JOIN agent_runs ar
                  ON ar.user_id=j.user_id AND ar.id=j.latest_agent_run_id
                %s%s
                """.formatted(SELECT_COLUMNS, where, lock);
        return jdbc.sql(selectSql)
                .params(parameters)
                .query(this::job)
                .optional();
    }

    private UUID upsertCompany(String companyName, Instant now) {
        if (companyName == null) {
            return null;
        }
        String normalized = companyName.toLowerCase(java.util.Locale.ROOT);
        return jdbc.sql("""
                        INSERT INTO companies (
                            id,normalized_name,display_name,official_website,created_at,updated_at
                        ) VALUES (:id,:normalizedName,:displayName,NULL,:now,:now)
                        ON CONFLICT (lower(normalized_name)) DO UPDATE SET
                            display_name=EXCLUDED.display_name,updated_at=EXCLUDED.updated_at
                        RETURNING id
                        """)
                .param("id", UUID.randomUUID())
                .param("normalizedName", normalized)
                .param("displayName", companyName)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    private void insertHistory(
            UUID userId,
            UUID jobId,
            JobStatus from,
            JobStatus to,
            String reason,
            JobHistoryActor actor,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO job_status_history (
                            id,user_id,job_posting_id,from_status,to_status,reason,changed_by,changed_at
                        ) VALUES (:id,:userId,:jobId,:fromStatus,:toStatus,:reason,:actor,:now)
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("jobId", jobId)
                .param("fromStatus", from == null ? null : from.name())
                .param("toStatus", to.name())
                .param("reason", reason)
                .param("actor", actor.name())
                .param("now", utc(now))
                .update();
    }

    private JobRecord job(ResultSet rs, int row) throws SQLException {
        return new JobRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("company_id", UUID.class),
                rs.getString("company_name"),
                rs.getString("source_url"),
                rs.getString("canonical_url"),
                rs.getString("title"),
                rs.getString("position_name"),
                rs.getString("role_category"),
                rs.getString("employment_type"),
                rs.getString("location"),
                rs.getString("description_text"),
                enumOrNull(JobDescriptionSource.class, rs.getString("description_source")),
                instant(rs, "deadline_at"),
                DeadlineSource.valueOf(rs.getString("deadline_source")),
                rs.getBigDecimal("deadline_confidence"),
                JobStatus.valueOf(rs.getString("status")),
                JobExtractionStatus.valueOf(rs.getString("extraction_status")),
                instant(rs, "submitted_at"),
                instant(rs, "closed_at"),
                enumOrNull(ClosedReason.class, rs.getString("closed_reason")),
                trimOrNull(rs.getString("content_hash")),
                rs.getObject("latest_agent_run_id", UUID.class),
                rs.getString("extraction_error_code"),
                rs.getString("extraction_error_message"),
                rs.getBoolean("company_user_override"),
                rs.getBoolean("title_user_override"),
                rs.getBoolean("position_user_override"),
                rs.getBoolean("deadline_user_override"),
                rs.getLong("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "deleted_at"));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private boolean causedByConstraint(
            Throwable failure, String sqlState, String constraintName) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlState.equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
