package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.Attempt;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.Connection;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.TokenTarget;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.VerifiedInstallation;
import com.hiresemble.githubsource.domain.GitHubAppConnectionStatus;
import com.hiresemble.githubsource.domain.GitHubRepositorySelectionKind;
import com.hiresemble.githubsource.domain.GitHubUrl;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.ObjectMapper;

@Repository
public class GitHubAppConnectionStore {

    private static final String PERMISSIONS = "{\"metadata\":\"read\",\"contents\":\"read\"}";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public GitHubAppConnectionStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Attempt createAttempt(
            UUID id,
            UUID userId,
            String sessionBindingDigest,
            String stateDigest,
            Instant expiresAt,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO github_connection_attempts (
                            id,user_id,session_binding_digest,state_digest,phase,
                            pending_installation_id,expires_at,consumed_at,created_at,updated_at
                        ) VALUES (
                            :id,:userId,:sessionBindingDigest,:stateDigest,'INSTALL_PENDING',
                            NULL,:expiresAt,NULL,:now,:now
                        )
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("sessionBindingDigest", sessionBindingDigest)
                .param("stateDigest", stateDigest)
                .param("expiresAt", utc(expiresAt))
                .param("now", utc(now))
                .update();
        return findAttempt(id).orElseThrow();
    }

    @Transactional
    public Optional<Attempt> transitionToOAuth(
            UUID userId,
            String sessionBindingDigest,
            String installStateDigest,
            long installationId,
            String oauthStateDigest,
            Instant expiresAt,
            Instant now) {
        return jdbc.sql("""
                        UPDATE github_connection_attempts
                        SET phase='OAUTH_PENDING',pending_installation_id=:installationId,
                            state_digest=:oauthStateDigest,expires_at=:expiresAt,updated_at=:now
                        WHERE user_id=:userId
                          AND session_binding_digest=:sessionBindingDigest
                          AND state_digest=:installStateDigest
                          AND phase='INSTALL_PENDING'
                          AND consumed_at IS NULL AND expires_at>:now
                        RETURNING *
                        """)
                .param("installationId", installationId)
                .param("oauthStateDigest", oauthStateDigest)
                .param("expiresAt", utc(expiresAt))
                .param("now", utc(now))
                .param("userId", userId)
                .param("sessionBindingDigest", sessionBindingDigest)
                .param("installStateDigest", installStateDigest)
                .query(this::attempt)
                .optional();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Attempt> consumeOAuth(
            UUID userId,
            String sessionBindingDigest,
            String oauthStateDigest,
            Instant now) {
        return jdbc.sql("""
                        UPDATE github_connection_attempts
                        SET consumed_at=:now,updated_at=:now
                        WHERE user_id=:userId
                          AND session_binding_digest=:sessionBindingDigest
                          AND state_digest=:oauthStateDigest
                          AND phase='OAUTH_PENDING'
                          AND consumed_at IS NULL AND expires_at>:now
                        RETURNING *
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sessionBindingDigest", sessionBindingDigest)
                .param("oauthStateDigest", oauthStateDigest)
                .query(this::attempt)
                .optional();
    }

    @Transactional(readOnly = true)
    public Optional<Attempt> findAttempt(UUID id) {
        return jdbc.sql("SELECT * FROM github_connection_attempts WHERE id=:id")
                .param("id", id)
                .query(this::attempt)
                .optional();
    }

    @Transactional
    public int cleanupAttemptsBefore(Instant cutoff) {
        return jdbc.sql("""
                        DELETE FROM github_connection_attempts
                        WHERE expires_at<:cutoff
                           OR (consumed_at IS NOT NULL AND consumed_at<:cutoff)
                        """)
                .param("cutoff", utc(cutoff))
                .update();
    }

    @Transactional
    public Connection createConnection(
            UUID connectionId, UUID userId, VerifiedInstallation installation, Instant now) {
        if (installation.suspended()) {
            throw new BusinessException(ErrorCode.GITHUB_INSTALLATION_SUSPENDED);
        }
        try {
            jdbc.sql("""
                            INSERT INTO github_app_connections (
                                id,user_id,github_installation_id,target_account_id,target_account_login,
                                target_account_type,repository_selection,status,permission_snapshot,
                                version,connected_at,verified_at,last_checked_at,disconnected_at,
                                created_at,updated_at
                            ) VALUES (
                                :id,:userId,:installationId,:accountId,:accountLogin,
                                :accountType,:repositorySelection,'ACTIVE',CAST(:permissions AS jsonb),
                                0,:now,:now,:now,NULL,:now,:now
                            )
                            """)
                    .param("id", connectionId)
                    .param("userId", userId)
                    .param("installationId", installation.installationId())
                    .param("accountId", installation.targetAccountId())
                    .param("accountLogin", installation.targetAccountLogin())
                    .param("accountType", installation.targetAccountType().name())
                    .param("repositorySelection", installation.repositorySelection().name())
                    .param("permissions", PERMISSIONS)
                    .param("now", utc(now))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE, exception);
        }
        syncRepositories(userId, connectionId, installation.repositories(), now);
        return findOwner(userId, connectionId).orElseThrow();
    }

    @Transactional
    public Connection refresh(
            UUID userId,
            UUID connectionId,
            long expectedVersion,
            VerifiedInstallation installation,
            Instant now) {
        Connection current = findOwnerForUpdate(userId, connectionId);
        if (current.version() != expectedVersion || current.status() == GitHubAppConnectionStatus.DISCONNECTING
                || current.status() == GitHubAppConnectionStatus.DISCONNECTED) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        GitHubAppConnectionStatus status = installation.suspended()
                ? GitHubAppConnectionStatus.SUSPENDED : GitHubAppConnectionStatus.ACTIVE;
        int updated = jdbc.sql("""
                        UPDATE github_app_connections
                        SET target_account_id=:accountId,target_account_login=:accountLogin,
                            target_account_type=:accountType,repository_selection=:selection,
                            status=:status,permission_snapshot=CAST(:permissions AS jsonb),
                            verified_at=:now,last_checked_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:connectionId AND version=:expectedVersion
                        """)
                .param("accountId", installation.targetAccountId())
                .param("accountLogin", installation.targetAccountLogin())
                .param("accountType", installation.targetAccountType().name())
                .param("selection", installation.repositorySelection().name())
                .param("status", status.name())
                .param("permissions", PERMISSIONS)
                .param("now", utc(now))
                .param("userId", userId)
                .param("connectionId", connectionId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        syncRepositories(userId, connectionId, installation.repositories(), now);
        return findOwner(userId, connectionId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Connection> list(UUID userId) {
        return jdbc.sql("""
                        SELECT * FROM github_app_connections
                        WHERE user_id=:userId
                        ORDER BY connected_at DESC,id DESC
                        """)
                .param("userId", userId)
                .query(this::connection)
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<Connection> findOwner(UUID userId, UUID connectionId) {
        return jdbc.sql("""
                        SELECT * FROM github_app_connections
                        WHERE user_id=:userId AND id=:connectionId
                        """)
                .param("userId", userId)
                .param("connectionId", connectionId)
                .query(this::connection)
                .optional();
    }

    @Transactional(readOnly = true)
    public TokenTarget tokenTarget(UUID connectionId, Long externalRepositoryId) {
        Connection connection = jdbc.sql("SELECT * FROM github_app_connections WHERE id=:id")
                .param("id", connectionId)
                .query(this::connection)
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_CONNECTION_REQUIRED));
        if (!connection.status().allowsTokenMint()) {
            throw stateError(connection.status());
        }
        if (externalRepositoryId != null) {
            long access = jdbc.sql("""
                            SELECT count(*)
                            FROM github_app_connection_repository_access access
                            WHERE access.user_id=:userId
                              AND access.github_app_connection_id=:connectionId
                              AND access.external_repository_id=:externalRepositoryId
                              AND access.available
                            """)
                    .param("userId", connection.userId())
                    .param("connectionId", connectionId)
                    .param("externalRepositoryId", externalRepositoryId)
                    .query(Long.class)
                    .single();
            if (access != 1) {
                throw new BusinessException(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE);
            }
        }
        return new TokenTarget(
                connection.id(),
                connection.installationId(),
                connection.targetAccountType(),
                connection.status());
    }

    @Transactional(readOnly = true)
    public Connection requireActiveForSource(UUID userId, UUID connectionId, GitHubUrl url) {
        Connection connection = findOwner(userId, connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!connection.status().allowsTokenMint()) throw stateError(connection.status());
        if (!connection.targetAccountLogin().equalsIgnoreCase(url.ownerLogin())) {
            throw new BusinessException(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE);
        }
        if (url.repositoryName() != null) {
            long allowed = jdbc.sql("""
                            SELECT count(*)
                            FROM github_app_connection_repository_access access
                            JOIN github_repositories repository
                              ON repository.user_id=access.user_id
                             AND repository.id=access.github_repository_id
                            WHERE access.user_id=:userId
                              AND access.github_app_connection_id=:connectionId
                              AND lower(repository.owner_login)=lower(:ownerLogin)
                              AND lower(repository.repository_name)=lower(:repositoryName)
                              AND access.available
                            """)
                    .param("userId", userId)
                    .param("connectionId", connectionId)
                    .param("ownerLogin", url.ownerLogin())
                    .param("repositoryName", url.repositoryName())
                    .query(Long.class)
                    .single();
            if (allowed != 1) {
                throw new BusinessException(ErrorCode.GITHUB_INSTALLATION_NOT_ACCESSIBLE);
            }
        }
        return connection;
    }

    @Transactional
    public Connection beginDisconnect(
            UUID userId, UUID connectionId, long expectedVersion, String reason, Instant now) {
        Connection current = findOwnerForUpdate(userId, connectionId);
        if (current.version() != expectedVersion) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        if (current.status() == GitHubAppConnectionStatus.DISCONNECTED) return current;
        if (current.status() == GitHubAppConnectionStatus.DISCONNECTING) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        jdbc.sql("""
                        UPDATE github_app_connections
                        SET status='DISCONNECTING',version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:connectionId AND version=:expectedVersion
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("connectionId", connectionId)
                .param("expectedVersion", expectedVersion)
                .update();
        jdbc.sql("""
                        INSERT INTO github_installation_revocation_outbox (
                            id,user_id,github_app_connection_id,github_installation_id,reason,
                            status,attempt_count,next_attempt_at,claim_token,lease_expires_at,
                            last_error_code,created_at,completed_at
                        ) VALUES (
                            :id,:userId,:connectionId,:installationId,:reason,
                            'PENDING',0,:now,NULL,NULL,NULL,:now,NULL
                        )
                        ON CONFLICT (github_app_connection_id)
                            WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
                        DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("connectionId", connectionId)
                .param("installationId", current.installationId())
                .param("reason", reason)
                .param("now", utc(now))
                .update();
        return findOwner(userId, connectionId).orElseThrow();
    }

    @Transactional
    public void markRevoked(long installationId, Instant now) {
        jdbc.sql("""
                        UPDATE github_app_connections
                        SET status='REVOKED',version=version+1,last_checked_at=:now,updated_at=:now
                        WHERE github_installation_id=:installationId AND status IN ('ACTIVE','SUSPENDED')
                        """)
                .param("now", utc(now))
                .param("installationId", installationId)
                .update();
    }

    private Connection findOwnerForUpdate(UUID userId, UUID connectionId) {
        return jdbc.sql("""
                        SELECT * FROM github_app_connections
                        WHERE user_id=:userId AND id=:connectionId FOR UPDATE
                        """)
                .param("userId", userId)
                .param("connectionId", connectionId)
                .query(this::connection)
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void syncRepositories(
            UUID userId, UUID connectionId, List<RepositoryMetadata> repositories, Instant now) {
        jdbc.sql("""
                        UPDATE github_app_connection_repository_access
                        SET available=false,checked_at=:now,updated_at=:now
                        WHERE user_id=:userId AND github_app_connection_id=:connectionId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("connectionId", connectionId)
                .update();
        for (RepositoryMetadata repository : repositories) {
            UUID repositoryId = upsertRepository(userId, repository, now);
            jdbc.sql("""
                            INSERT INTO github_app_connection_repository_access (
                                id,user_id,github_app_connection_id,github_repository_id,
                                external_repository_id,available,checked_at,created_at,updated_at
                            ) VALUES (
                                :id,:userId,:connectionId,:repositoryId,
                                :externalId,true,:now,:now,:now
                            )
                            ON CONFLICT (user_id,github_app_connection_id,github_repository_id)
                            DO UPDATE SET external_repository_id=EXCLUDED.external_repository_id,
                                available=true,checked_at=EXCLUDED.checked_at,updated_at=EXCLUDED.updated_at
                            """)
                    .param("id", UUID.randomUUID())
                    .param("userId", userId)
                    .param("connectionId", connectionId)
                    .param("repositoryId", repositoryId)
                    .param("externalId", repository.externalId())
                    .param("now", utc(now))
                    .update();
        }
    }

    private UUID upsertRepository(UUID userId, RepositoryMetadata value, Instant now) {
        String topics;
        try {
            topics = objectMapper.writeValueAsString(value.topics());
        } catch (Exception exception) {
            throw new IllegalStateException("repository topics cannot be serialized", exception);
        }
        return jdbc.sql("""
                        INSERT INTO github_repositories (
                            id,user_id,external_repository_id,node_id,owner_login,repository_name,
                            canonical_url,default_branch,is_private,visibility,is_fork,is_archived,
                            description,topics,metadata_etag,pushed_at,created_at,updated_at
                        ) VALUES (
                            :id,:userId,:externalId,:nodeId,:ownerLogin,:repositoryName,
                            :canonicalUrl,:defaultBranch,:privateRepository,:visibility,:fork,:archived,
                            :description,CAST(:topics AS jsonb),:etag,:pushedAt,:now,:now
                        )
                        ON CONFLICT (user_id,external_repository_id) DO UPDATE SET
                            node_id=EXCLUDED.node_id,owner_login=EXCLUDED.owner_login,
                            repository_name=EXCLUDED.repository_name,canonical_url=EXCLUDED.canonical_url,
                            default_branch=EXCLUDED.default_branch,is_private=EXCLUDED.is_private,
                            visibility=EXCLUDED.visibility,is_fork=EXCLUDED.is_fork,
                            is_archived=EXCLUDED.is_archived,description=EXCLUDED.description,
                            topics=EXCLUDED.topics,metadata_etag=EXCLUDED.metadata_etag,
                            pushed_at=EXCLUDED.pushed_at,updated_at=EXCLUDED.updated_at
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
                .param("privateRepository", value.privateRepository())
                .param("visibility", value.privateRepository() ? "PRIVATE" : "PUBLIC")
                .param("fork", value.fork())
                .param("archived", value.archived())
                .param("description", value.description())
                .param("topics", topics)
                .param("etag", value.etag())
                .param("pushedAt", utc(value.pushedAt()))
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    private Attempt attempt(ResultSet rs, int row) throws SQLException {
        return new Attempt(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("session_binding_digest"),
                rs.getString("state_digest"),
                rs.getString("phase"),
                rs.getObject("pending_installation_id", Long.class),
                instant(rs, "expires_at"),
                instant(rs, "consumed_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Connection connection(ResultSet rs, int row) throws SQLException {
        return new Connection(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getLong("github_installation_id"),
                rs.getLong("target_account_id"),
                rs.getString("target_account_login"),
                GitHubAccountType.valueOf(rs.getString("target_account_type")),
                GitHubRepositorySelectionKind.valueOf(rs.getString("repository_selection")),
                GitHubAppConnectionStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                instant(rs, "connected_at"),
                instant(rs, "verified_at"),
                instant(rs, "last_checked_at"),
                instant(rs, "disconnected_at"));
    }

    private BusinessException stateError(GitHubAppConnectionStatus status) {
        return switch (status) {
            case SUSPENDED -> new BusinessException(ErrorCode.GITHUB_INSTALLATION_SUSPENDED);
            case REVOKED, DISCONNECTED ->
                    new BusinessException(ErrorCode.GITHUB_INSTALLATION_REVOKED);
            case DISCONNECTING -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            case ACTIVE -> new BusinessException(ErrorCode.INTERNAL_ERROR);
        };
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
