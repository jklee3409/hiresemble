package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubSourceKind;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Page;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Snapshot;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.SourceUnit;
import com.hiresemble.githubsource.domain.GitHubSourceStatus;
import com.hiresemble.githubsource.domain.GitHubUrl;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@org.springframework.stereotype.Repository
public class GitHubSourceStore {

    private static final String SOURCE_COLUMNS = """
            source.id,source.user_id,source.source_kind,source.account_type,
            source.original_url,source.canonical_url,source.owner_login,source.repository_name,
            source.source_status,
            (SELECT count(*) FROM github_source_repository_links link
             WHERE link.user_id=source.user_id AND link.github_source_id=source.id
               AND link.available) AS discovered_repository_count,
            (SELECT count(*) FROM github_source_repository_links link
             WHERE link.user_id=source.user_id AND link.github_source_id=source.id
               AND link.available AND link.selected) AS selected_repository_count,
            source.repository_discovery_truncated,source.new_experience_count,
            source.corroborated_experience_count,source.review_required_count,
            source.rejected_candidate_count,source.snapshot_incomplete,
            source.latest_agent_run_id,source.source_revision,source.last_successful_sync_at,
            source.version,source.created_at,source.updated_at,source.deleted_at
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public GitHubSourceStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Source create(UUID sourceId, UUID userId, GitHubUrl url, Instant now) {
        try {
            jdbc.sql("""
                            INSERT INTO github_sources (
                                id,user_id,source_kind,account_type,original_url,canonical_url,
                                owner_login,repository_name,source_status,
                                repository_discovery_truncated,latest_agent_run_id,source_revision,
                                last_successful_sync_at,version,created_at,updated_at,deleted_at
                            ) VALUES (
                                :id,:userId,:sourceKind,NULL,:originalUrl,:canonicalUrl,
                                :ownerLogin,:repositoryName,'DISCOVERING',false,NULL,0,
                                NULL,0,:now,:now,NULL
                            )
                            """)
                    .param("id", sourceId)
                    .param("userId", userId)
                    .param("sourceKind", url.sourceKind().name())
                    .param("originalUrl", url.originalUrl())
                    .param("canonicalUrl", url.canonicalUrl())
                    .param("ownerLogin", url.ownerLogin())
                    .param("repositoryName", url.repositoryName())
                    .param("now", utc(now))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            if (rootMessage(exception).contains("github_sources_active_canonical_url_uk")) {
                throw new BusinessException(ErrorCode.GITHUB_SOURCE_ALREADY_EXISTS, exception);
            }
            throw exception;
        }
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public Source attachLatestRun(UUID userId, UUID sourceId, UUID runId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET latest_agent_run_id=:runId,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                        """)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .update();
        if (updated != 1) throw notFound();
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<Source> findActive(UUID userId, UUID sourceId) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + " FROM github_sources source"
                        + " WHERE source.user_id=:userId AND source.id=:sourceId"
                        + " AND source.deleted_at IS NULL")
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(this::source)
                .optional();
    }

    @Transactional(readOnly = true)
    public Optional<Source> findAny(UUID userId, UUID sourceId) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + " FROM github_sources source"
                        + " WHERE source.user_id=:userId AND source.id=:sourceId")
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(this::source)
                .optional();
    }

    @Transactional(readOnly = true)
    public Page<Source> list(
            UUID userId,
            GitHubSourceStatus status,
            GitHubSourceKind kind,
            int page,
            int size,
            String sort) {
        String order = switch (sort) {
            case "updatedAt,desc" -> "source.updated_at DESC,source.id DESC";
            case "createdAt,desc" -> "source.created_at DESC,source.id DESC";
            default -> throw invalid();
        };
        StringBuilder where = new StringBuilder(
                " WHERE source.user_id=:userId AND source.deleted_at IS NULL");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        if (status != null) {
            where.append(" AND source.source_status=:status");
            params.put("status", status.name());
        }
        if (kind != null) {
            where.append(" AND source.source_kind=:kind");
            params.put("kind", kind.name());
        }
        long total = bind(jdbc.sql("SELECT count(*) FROM github_sources source" + where), params)
                .query(Long.class)
                .single();
        JdbcClient.StatementSpec query = jdbc.sql("SELECT " + SOURCE_COLUMNS
                        + " FROM github_sources source" + where
                        + " ORDER BY " + order + " LIMIT :limit OFFSET :offset");
        query = bind(query, params).param("limit", size).param("offset", page * size);
        List<Source> items = query.query(this::source).list();
        return page(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public Page<Repository> repositories(
            UUID userId,
            UUID sourceId,
            String search,
            Boolean selected,
            int page,
            int size,
            String sort) {
        String order = switch (sort) {
            case "pushedAt,desc" -> "repository.pushed_at DESC NULLS LAST,repository.id";
            case "repositoryName,asc" -> "lower(repository.repository_name),repository.id";
            default -> throw invalid();
        };
        StringBuilder where = new StringBuilder("""
                 WHERE link.user_id=:userId AND link.github_source_id=:sourceId
                   AND link.available
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        params.put("sourceId", sourceId);
        if (search != null && !search.isBlank()) {
            where.append(" AND (lower(repository.repository_name) LIKE :search"
                    + " OR lower(repository.description) LIKE :search)");
            params.put("search", "%" + search.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (selected != null) {
            where.append(" AND link.selected=:selected");
            params.put("selected", selected);
        }
        String from = " FROM github_source_repository_links link"
                + " JOIN github_repositories repository"
                + " ON repository.user_id=link.user_id AND repository.id=link.github_repository_id";
        long total = bind(jdbc.sql("SELECT count(*)" + from + where), params)
                .query(Long.class)
                .single();
        JdbcClient.StatementSpec query = jdbc.sql("SELECT repository.*,link.selected,link.selection_order"
                + from + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset");
        query = bind(query, params).param("limit", size).param("offset", page * size);
        return page(query.query(this::repository).list(), page, size, total);
    }

    @Transactional(readOnly = true)
    public List<Repository> selectedRepositories(UUID userId, UUID sourceId) {
        return jdbc.sql("""
                        SELECT repository.*,link.selected,link.selection_order
                        FROM github_source_repository_links link
                        JOIN github_repositories repository
                          ON repository.user_id=link.user_id
                         AND repository.id=link.github_repository_id
                        WHERE link.user_id=:userId AND link.github_source_id=:sourceId
                          AND link.available AND link.selected
                        ORDER BY link.selection_order
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(this::repository)
                .list();
    }

    @Transactional
    public Source applyAccountDiscovery(
            UUID userId,
            UUID sourceId,
            UUID runId,
            GitHubAccountType accountType,
            List<RepositoryMetadata> repositories,
            boolean truncated,
            Instant now) {
        Source source = lockActive(userId, sourceId);
        requireLatestRun(source, runId);
        if (source.sourceKind() != GitHubSourceKind.ACCOUNT
                || !(source.status() == GitHubSourceStatus.DISCOVERING
                        || source.status() == GitHubSourceStatus.QUEUED
                        || source.status() == GitHubSourceStatus.RUNNING)) {
            throw stateConflict();
        }
        jdbc.sql("""
                        UPDATE github_source_repository_links
                        SET available=false,updated_at=:now
                        WHERE user_id=:userId AND github_source_id=:sourceId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .update();
        for (RepositoryMetadata metadata : repositories) {
            UUID repositoryId = upsertRepository(userId, metadata, now);
            upsertLink(userId, sourceId, repositoryId, false, null, true, now);
        }
        jdbc.sql("""
                        UPDATE github_source_repository_links
                        SET selected=false,selection_order=NULL,updated_at=:now
                        WHERE user_id=:userId AND github_source_id=:sourceId AND NOT available
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .update();
        normalizeSelectedOrder(userId, sourceId, now);
        int selected = selectedCount(userId, sourceId);
        String status = selected == 0 ? "WAITING_USER" : "RUNNING";
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET account_type=:accountType,source_status=:status,
                            repository_discovery_truncated=:truncated,
                            source_revision=source_revision+1,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                        """)
                .param("accountType", accountType.name())
                .param("status", status)
                .param("truncated", truncated)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("runId", runId)
                .update();
        if (updated != 1) throw stateConflict();
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public Source applyRepositoryDiscovery(
            UUID userId,
            UUID sourceId,
            UUID runId,
            RepositoryMetadata metadata,
            Instant now) {
        Source source = lockActive(userId, sourceId);
        requireLatestRun(source, runId);
        if (source.sourceKind() != GitHubSourceKind.REPOSITORY
                || metadata.privateRepository()
                || !source.ownerLogin().equalsIgnoreCase(metadata.ownerLogin())
                || !source.repositoryName().equalsIgnoreCase(metadata.repositoryName())) {
            throw new BusinessException(ErrorCode.GITHUB_SOURCE_NOT_ACCESSIBLE);
        }
        UUID repositoryId = upsertRepository(userId, metadata, now);
        jdbc.sql("""
                        UPDATE github_source_repository_links
                        SET available=false,selected=false,selection_order=NULL,updated_at=:now
                        WHERE user_id=:userId AND github_source_id=:sourceId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .update();
        upsertLink(userId, sourceId, repositoryId, true, 1, true, now);
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET source_status='RUNNING',repository_discovery_truncated=false,
                            source_revision=source_revision+1,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("runId", runId)
                .update();
        if (updated != 1) throw stateConflict();
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public Source replaceSelection(
            UUID userId,
            UUID sourceId,
            long expectedVersion,
            List<UUID> repositoryIds,
            UUID runId,
            Instant now) {
        Source source = lockActive(userId, sourceId);
        if (source.sourceKind() != GitHubSourceKind.ACCOUNT
                || source.status() != GitHubSourceStatus.WAITING_USER
                || source.version() != expectedVersion
                || !runId.equals(source.latestAgentRunId())) {
            if (source.version() != expectedVersion) {
                throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
            }
            throw stateConflict();
        }
        long selectable = jdbc.sql("""
                        SELECT count(*) FROM github_source_repository_links
                        WHERE user_id=:userId AND github_source_id=:sourceId
                          AND available AND github_repository_id IN (:repositoryIds)
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("repositoryIds", repositoryIds)
                .query(Long.class)
                .single();
        if (selectable != repositoryIds.size()) throw notFound();
        jdbc.sql("""
                        UPDATE github_source_repository_links
                        SET selected=false,selection_order=NULL,updated_at=:now
                        WHERE user_id=:userId AND github_source_id=:sourceId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .update();
        for (int index = 0; index < repositoryIds.size(); index++) {
            int updated = jdbc.sql("""
                            UPDATE github_source_repository_links
                            SET selected=true,selection_order=:selectionOrder,updated_at=:now
                            WHERE user_id=:userId AND github_source_id=:sourceId
                              AND github_repository_id=:repositoryId AND available
                            """)
                    .param("selectionOrder", index + 1)
                    .param("now", utc(now))
                    .param("userId", userId)
                    .param("sourceId", sourceId)
                    .param("repositoryId", repositoryIds.get(index))
                    .update();
            if (updated != 1) throw notFound();
        }
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET source_status='QUEUED',source_revision=source_revision+1,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND version=:expectedVersion AND source_status='WAITING_USER'
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public Source markRunning(UUID userId, UUID sourceId, UUID runId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET source_status='RUNNING',version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                          AND source_status IN ('QUEUED','DISCOVERING','RUNNING')
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("runId", runId)
                .update();
        if (updated != 1) throw stateConflict();
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public Source queueRefresh(
            UUID userId, UUID sourceId, long expectedVersion, UUID runId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET source_status='QUEUED',latest_agent_run_id=:runId,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND version=:expectedVersion AND source_status IN ('READY','PARTIAL','FAILED')
                        """)
                .param("runId", runId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) {
            Source source = findActive(userId, sourceId).orElseThrow(this::notFound);
            if (source.version() != expectedVersion) {
                throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
            }
            throw stateConflict();
        }
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public Source finalizeSource(
            UUID userId,
            UUID sourceId,
            UUID runId,
            boolean partial,
            int newCount,
            int corroboratedCount,
            int reviewRequiredCount,
            int rejectedCount,
            Instant now) {
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET source_status=:status,new_experience_count=:newCount,
                            corroborated_experience_count=:corroboratedCount,
                            review_required_count=:reviewRequiredCount,
                            rejected_candidate_count=:rejectedCount,
                            snapshot_incomplete=:partial,last_successful_sync_at=:now,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId AND source_status='RUNNING'
                        """)
                .param("status", partial ? "PARTIAL" : "READY")
                .param("newCount", newCount)
                .param("corroboratedCount", corroboratedCount)
                .param("reviewRequiredCount", reviewRequiredCount)
                .param("rejectedCount", rejectedCount)
                .param("partial", partial)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("runId", runId)
                .update();
        if (updated != 1) throw stateConflict();
        return findActive(userId, sourceId).orElseThrow();
    }

    @Transactional
    public void fail(UUID userId, UUID sourceId, UUID runId, Instant now) {
        jdbc.sql("""
                        UPDATE github_sources
                        SET source_status='FAILED',version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND latest_agent_run_id=:runId
                          AND source_status NOT IN ('READY','PARTIAL','FAILED')
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("runId", runId)
                .update();
    }

    @Transactional
    public Source softDelete(UUID userId, UUID sourceId, long expectedVersion, Instant now) {
        Source source = lockActive(userId, sourceId);
        if (source.version() != expectedVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        int updated = jdbc.sql("""
                        UPDATE github_sources
                        SET deleted_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                          AND version=:expectedVersion
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        return findAny(userId, sourceId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> findSnapshot(
            UUID userId, UUID repositoryId, String commitSha, String policyVersion) {
        return jdbc.sql("""
                        SELECT * FROM github_repository_snapshots
                        WHERE user_id=:userId AND github_repository_id=:repositoryId
                          AND commit_sha=:commitSha AND retrieval_policy_version=:policyVersion
                        """)
                .param("userId", userId)
                .param("repositoryId", repositoryId)
                .param("commitSha", commitSha)
                .param("policyVersion", policyVersion)
                .query(this::snapshot)
                .optional();
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> latestSnapshot(
            UUID userId, UUID repositoryId, String policyVersion) {
        return jdbc.sql("""
                        SELECT * FROM github_repository_snapshots
                        WHERE user_id=:userId AND github_repository_id=:repositoryId
                          AND retrieval_policy_version=:policyVersion
                        ORDER BY captured_at DESC,id DESC LIMIT 1
                        """)
                .param("userId", userId)
                .param("repositoryId", repositoryId)
                .param("policyVersion", policyVersion)
                .query(this::snapshot)
                .optional();
    }

    @Transactional
    public SnapshotInsert insertSnapshot(
            UUID snapshotId,
            UUID userId,
            UUID repositoryId,
            String commitSha,
            String treeSha,
            String apiVersion,
            String policyVersion,
            boolean selectionComplete,
            boolean upstreamTruncated,
            String storageKey,
            String checksum,
            long sanitizedBytes,
            List<SourceUnitDraft> units,
            Instant capturedAt) {
        int inserted = jdbc.sql("""
                        INSERT INTO github_repository_snapshots (
                            id,user_id,github_repository_id,commit_sha,tree_sha,
                            github_api_version,retrieval_policy_version,selection_complete,
                            upstream_truncated,snapshot_storage_key,checksum_sha256,
                            sanitized_bytes,captured_at
                        ) VALUES (
                            :id,:userId,:repositoryId,:commitSha,:treeSha,
                            :apiVersion,:policyVersion,:selectionComplete,
                            :upstreamTruncated,:storageKey,:checksum,:sanitizedBytes,:capturedAt
                        )
                        ON CONFLICT (user_id,github_repository_id,commit_sha,retrieval_policy_version)
                        DO NOTHING
                        """)
                .param("id", snapshotId)
                .param("userId", userId)
                .param("repositoryId", repositoryId)
                .param("commitSha", commitSha)
                .param("treeSha", treeSha)
                .param("apiVersion", apiVersion)
                .param("policyVersion", policyVersion)
                .param("selectionComplete", selectionComplete)
                .param("upstreamTruncated", upstreamTruncated)
                .param("storageKey", storageKey)
                .param("checksum", checksum)
                .param("sanitizedBytes", sanitizedBytes)
                .param("capturedAt", utc(capturedAt))
                .update();
        if (inserted == 1) {
            for (int index = 0; index < units.size(); index++) {
                SourceUnitDraft unit = units.get(index);
                jdbc.sql("""
                                INSERT INTO github_source_units (
                                    id,user_id,snapshot_id,unit_type,repository_path,blob_sha,
                                    language,line_start,line_end,content_hash,excerpt,
                                    snapshot_ordinal,created_at
                                ) VALUES (
                                    :id,:userId,:snapshotId,:unitType,:repositoryPath,:blobSha,
                                    :language,:lineStart,:lineEnd,:contentHash,:excerpt,
                                    :ordinal,:createdAt
                                )
                                """)
                        .param("id", unit.id())
                        .param("userId", userId)
                        .param("snapshotId", snapshotId)
                        .param("unitType", unit.unitType())
                        .param("repositoryPath", unit.repositoryPath())
                        .param("blobSha", unit.blobSha())
                        .param("language", unit.language())
                        .param("lineStart", unit.lineStart())
                        .param("lineEnd", unit.lineEnd())
                        .param("contentHash", unit.contentHash())
                        .param("excerpt", unit.excerpt())
                        .param("ordinal", index + 1)
                        .param("createdAt", utc(capturedAt))
                        .update();
            }
            return new SnapshotInsert(findSnapshot(userId, repositoryId, commitSha, policyVersion)
                    .orElseThrow(), true);
        }
        return new SnapshotInsert(findSnapshot(userId, repositoryId, commitSha, policyVersion)
                .orElseThrow(), false);
    }

    @Transactional(readOnly = true)
    public List<SourceUnit> sourceUnits(UUID userId, UUID snapshotId) {
        return jdbc.sql("""
                        SELECT * FROM github_source_units
                        WHERE user_id=:userId AND snapshot_id=:snapshotId
                        ORDER BY snapshot_ordinal
                        """)
                .param("userId", userId)
                .param("snapshotId", snapshotId)
                .query(this::sourceUnit)
                .list();
    }

    @Transactional(readOnly = true)
    public Set<UUID> validSourceUnitIds(
            UUID userId, UUID sourceId, UUID repositoryId, UUID snapshotId, long sourceRevision) {
        Long activeRevision = jdbc.sql("""
                        SELECT source_revision FROM github_sources
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (activeRevision == null || activeRevision != sourceRevision) return Set.of();
        return Set.copyOf(jdbc.sql("""
                        SELECT unit.id
                        FROM github_source_units unit
                        JOIN github_repository_snapshots snapshot
                          ON snapshot.user_id=unit.user_id AND snapshot.id=unit.snapshot_id
                        JOIN github_source_repository_links link
                          ON link.user_id=snapshot.user_id
                         AND link.github_repository_id=snapshot.github_repository_id
                        WHERE unit.user_id=:userId AND unit.snapshot_id=:snapshotId
                          AND snapshot.github_repository_id=:repositoryId
                          AND link.github_source_id=:sourceId AND link.selected
                        """)
                .param("userId", userId)
                .param("snapshotId", snapshotId)
                .param("repositoryId", repositoryId)
                .param("sourceId", sourceId)
                .query(UUID.class)
                .list());
    }

    @Transactional(readOnly = true)
    public List<SnapshotObject> snapshotObjectsForExclusiveSource(
            UUID userId, UUID sourceId) {
        return jdbc.sql("""
                        SELECT DISTINCT snapshot.id,snapshot.snapshot_storage_key
                        FROM github_source_repository_links owned
                        JOIN github_repository_snapshots snapshot
                          ON snapshot.user_id=owned.user_id
                         AND snapshot.github_repository_id=owned.github_repository_id
                        WHERE owned.user_id=:userId AND owned.github_source_id=:sourceId
                          AND NOT EXISTS (
                              SELECT 1 FROM github_source_repository_links other_link
                              JOIN github_sources other_source
                                ON other_source.user_id=other_link.user_id
                               AND other_source.id=other_link.github_source_id
                              WHERE other_link.user_id=owned.user_id
                                AND other_link.github_repository_id=owned.github_repository_id
                                AND other_link.github_source_id<>owned.github_source_id
                                AND other_link.selected AND other_link.available
                                AND other_source.deleted_at IS NULL
                          )
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query((rs, row) -> new SnapshotObject(
                        rs.getObject("id", UUID.class), rs.getString("snapshot_storage_key")))
                .list();
    }

    private Source lockActive(UUID userId, UUID sourceId) {
        jdbc.sql("""
                        SELECT id FROM github_sources
                        WHERE user_id=:userId AND id=:sourceId AND deleted_at IS NULL
                        FOR UPDATE
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(UUID.class)
                .optional()
                .orElseThrow(this::notFound);
        return findActive(userId, sourceId).orElseThrow();
    }

    private UUID upsertRepository(UUID userId, RepositoryMetadata value, Instant now) {
        if (value.privateRepository()) {
            throw new BusinessException(ErrorCode.GITHUB_SOURCE_NOT_ACCESSIBLE);
        }
        String topicsJson;
        try {
            topicsJson = objectMapper.writeValueAsString(value.topics());
        } catch (Exception exception) {
            throw new IllegalStateException("repository topics cannot be serialized", exception);
        }
        return jdbc.sql("""
                        INSERT INTO github_repositories (
                            id,user_id,external_repository_id,node_id,owner_login,repository_name,
                            canonical_url,default_branch,is_private,is_fork,is_archived,description,
                            topics,metadata_etag,pushed_at,created_at,updated_at
                        ) VALUES (
                            :id,:userId,:externalId,:nodeId,:ownerLogin,:repositoryName,
                            :canonicalUrl,:defaultBranch,false,:fork,:archived,:description,
                            CAST(:topics AS jsonb),:etag,:pushedAt,:now,:now
                        )
                        ON CONFLICT (user_id,external_repository_id) DO UPDATE SET
                            node_id=EXCLUDED.node_id,owner_login=EXCLUDED.owner_login,
                            repository_name=EXCLUDED.repository_name,
                            canonical_url=EXCLUDED.canonical_url,
                            default_branch=EXCLUDED.default_branch,is_private=false,
                            is_fork=EXCLUDED.is_fork,is_archived=EXCLUDED.is_archived,
                            description=EXCLUDED.description,topics=EXCLUDED.topics,
                            metadata_etag=EXCLUDED.metadata_etag,pushed_at=EXCLUDED.pushed_at,
                            updated_at=EXCLUDED.updated_at
                        RETURNING id
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("externalId", value.externalId())
                .param("nodeId", value.nodeId())
                .param("ownerLogin", value.ownerLogin())
                .param("repositoryName", value.repositoryName())
                .param("canonicalUrl", value.canonicalUrl())
                .param("defaultBranch", value.defaultBranch())
                .param("fork", value.fork())
                .param("archived", value.archived())
                .param("description", value.description())
                .param("topics", topicsJson)
                .param("etag", value.etag())
                .param("pushedAt", utc(value.pushedAt()))
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    private void upsertLink(
            UUID userId,
            UUID sourceId,
            UUID repositoryId,
            boolean selected,
            Integer selectionOrder,
            boolean available,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO github_source_repository_links (
                            id,user_id,github_source_id,github_repository_id,available,
                            selected,selection_order,discovered_at,updated_at
                        ) VALUES (
                            :id,:userId,:sourceId,:repositoryId,:available,
                            :selected,:selectionOrder,:now,:now
                        )
                        ON CONFLICT (user_id,github_source_id,github_repository_id) DO UPDATE SET
                            available=EXCLUDED.available,
                            selected=github_source_repository_links.selected OR EXCLUDED.selected,
                            selection_order=CASE
                                WHEN EXCLUDED.selected THEN EXCLUDED.selection_order
                                ELSE github_source_repository_links.selection_order
                            END,
                            updated_at=EXCLUDED.updated_at
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("repositoryId", repositoryId)
                .param("available", available)
                .param("selected", selected)
                .param("selectionOrder", selectionOrder)
                .param("now", utc(now))
                .update();
    }

    private void normalizeSelectedOrder(UUID userId, UUID sourceId, Instant now) {
        List<UUID> selected = jdbc.sql("""
                        SELECT github_repository_id
                        FROM github_source_repository_links
                        WHERE user_id=:userId AND github_source_id=:sourceId
                          AND available AND selected
                        ORDER BY selection_order,github_repository_id
                        LIMIT 10
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(UUID.class)
                .list();
        jdbc.sql("""
                        UPDATE github_source_repository_links
                        SET selected=false,selection_order=NULL,updated_at=:now
                        WHERE user_id=:userId AND github_source_id=:sourceId AND selected
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceId", sourceId)
                .update();
        for (int index = 0; index < selected.size(); index++) {
            jdbc.sql("""
                            UPDATE github_source_repository_links
                            SET selected=true,selection_order=:position,updated_at=:now
                            WHERE user_id=:userId AND github_source_id=:sourceId
                              AND github_repository_id=:repositoryId AND available
                            """)
                    .param("position", index + 1)
                    .param("now", utc(now))
                    .param("userId", userId)
                    .param("sourceId", sourceId)
                    .param("repositoryId", selected.get(index))
                    .update();
        }
    }

    private int selectedCount(UUID userId, UUID sourceId) {
        return jdbc.sql("""
                        SELECT count(*) FROM github_source_repository_links
                        WHERE user_id=:userId AND github_source_id=:sourceId
                          AND available AND selected
                        """)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .query(Integer.class)
                .single();
    }

    private void requireLatestRun(Source source, UUID runId) {
        if (runId == null || !runId.equals(source.latestAgentRunId())) {
            throw stateConflict();
        }
    }

    private Source source(ResultSet rs, int row) throws SQLException {
        return new Source(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                GitHubSourceKind.valueOf(rs.getString("source_kind")),
                enumOrNull(GitHubAccountType.class, rs.getString("account_type")),
                rs.getString("original_url"),
                rs.getString("canonical_url"),
                rs.getString("owner_login"),
                rs.getString("repository_name"),
                GitHubSourceStatus.valueOf(rs.getString("source_status")),
                rs.getInt("discovered_repository_count"),
                rs.getInt("selected_repository_count"),
                rs.getBoolean("repository_discovery_truncated"),
                rs.getInt("new_experience_count"),
                rs.getInt("corroborated_experience_count"),
                rs.getInt("review_required_count"),
                rs.getInt("rejected_candidate_count"),
                rs.getBoolean("snapshot_incomplete"),
                rs.getObject("latest_agent_run_id", UUID.class),
                rs.getLong("source_revision"),
                instant(rs, "last_successful_sync_at"),
                rs.getLong("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "deleted_at"));
    }

    private Repository repository(ResultSet rs, int row) throws SQLException {
        List<String> topics = new ArrayList<>();
        try {
            JsonNode value = objectMapper.readTree(rs.getString("topics"));
            if (value.isArray()) value.forEach(item -> topics.add(item.asText()));
        } catch (Exception exception) {
            throw new SQLException("stored GitHub topics are invalid", exception);
        }
        return new Repository(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getLong("external_repository_id"),
                rs.getString("node_id"),
                rs.getString("owner_login"),
                rs.getString("repository_name"),
                rs.getString("canonical_url"),
                rs.getString("default_branch"),
                rs.getBoolean("is_fork"),
                rs.getBoolean("is_archived"),
                rs.getString("description"),
                topics,
                rs.getString("metadata_etag"),
                rs.getBoolean("selected"),
                rs.getObject("selection_order", Integer.class),
                instant(rs, "pushed_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Snapshot snapshot(ResultSet rs, int row) throws SQLException {
        return new Snapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("github_repository_id", UUID.class),
                rs.getString("commit_sha"),
                rs.getString("tree_sha"),
                rs.getString("github_api_version"),
                rs.getString("retrieval_policy_version"),
                rs.getBoolean("selection_complete"),
                rs.getBoolean("upstream_truncated"),
                rs.getString("snapshot_storage_key"),
                rs.getString("checksum_sha256"),
                rs.getLong("sanitized_bytes"),
                instant(rs, "captured_at"));
    }

    private SourceUnit sourceUnit(ResultSet rs, int row) throws SQLException {
        return new SourceUnit(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("snapshot_id", UUID.class),
                rs.getString("unit_type"),
                rs.getString("repository_path"),
                rs.getString("blob_sha"),
                rs.getString("language"),
                rs.getObject("line_start", Integer.class),
                rs.getObject("line_end", Integer.class),
                rs.getString("content_hash"),
                rs.getString("excerpt"),
                rs.getInt("snapshot_ordinal"),
                instant(rs, "created_at"));
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

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement, Map<String, Object> params) {
        JdbcClient.StatementSpec result = statement;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.param(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private <T> Page<T> page(List<T> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new Page<>(items, page, size, total, totalPages);
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private BusinessException stateConflict() {
        return new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
    }

    public record SourceUnitDraft(
            UUID id,
            String unitType,
            String repositoryPath,
            String blobSha,
            String language,
            Integer lineStart,
            Integer lineEnd,
            String contentHash,
            String excerpt) {}

    public record SnapshotInsert(Snapshot snapshot, boolean created) {}

    public record SnapshotObject(UUID snapshotId, String storageKey) {}
}
