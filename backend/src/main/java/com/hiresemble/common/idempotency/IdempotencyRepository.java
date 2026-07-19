package com.hiresemble.common.idempotency;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    private final JdbcClient jdbcClient;

    public IdempotencyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    boolean tryReserve(
            UUID id,
            IdempotencyScope scope,
            String requestHash,
            int hashKeyVersion,
            Instant createdAt,
            Instant expiresAt) {
        return jdbcClient
                        .sql("""
                                INSERT INTO idempotency_records (
                                    id, user_id, http_method, route_scope, resource_scope_id,
                                    idempotency_key, request_hash, hash_key_version, state,
                                    created_at, expires_at
                                ) VALUES (
                                    :id, :userId, :httpMethod, :routeScope, :resourceScopeId,
                                    :idempotencyKey, :requestHash, :hashKeyVersion, 'IN_PROGRESS',
                                    :createdAt, :expiresAt
                                )
                                ON CONFLICT (
                                    user_id, http_method, route_scope, resource_scope_id, idempotency_key
                                ) DO UPDATE SET
                                    id = EXCLUDED.id,
                                    request_hash = EXCLUDED.request_hash,
                                    hash_key_version = EXCLUDED.hash_key_version,
                                    state = 'IN_PROGRESS',
                                    response_status = NULL,
                                    response_json = NULL,
                                    resource_type = NULL,
                                    resource_id = NULL,
                                    agent_run_id = NULL,
                                    created_at = EXCLUDED.created_at,
                                    completed_at = NULL,
                                    expires_at = EXCLUDED.expires_at
                                WHERE idempotency_records.expires_at <= EXCLUDED.created_at
                                  AND (
                                      idempotency_records.state = 'COMPLETED'
                                      OR (
                                          idempotency_records.state = 'IN_PROGRESS'
                                          AND idempotency_records.agent_run_id IS NULL
                                      )
                                  )
                                """)
                        .param("id", id)
                        .param("userId", scope.userId())
                        .param("httpMethod", scope.httpMethod())
                        .param("routeScope", scope.routeScope())
                        .param("resourceScopeId", scope.resourceScopeId())
                        .param("idempotencyKey", scope.idempotencyKey())
                        .param("requestHash", requestHash)
                        .param("hashKeyVersion", hashKeyVersion)
                        .param("createdAt", utc(createdAt))
                        .param("expiresAt", utc(expiresAt))
                        .update()
                == 1;
    }

    Optional<IdempotencyRecord> find(IdempotencyScope scope) {
        return jdbcClient
                .sql("""
                        SELECT id, request_hash, hash_key_version, state,
                               response_status, response_json::text AS response_json
                        FROM idempotency_records
                        WHERE user_id = :userId
                          AND http_method = :httpMethod
                          AND route_scope = :routeScope
                          AND resource_scope_id = :resourceScopeId
                          AND idempotency_key = :idempotencyKey
                        """)
                .param("userId", scope.userId())
                .param("httpMethod", scope.httpMethod())
                .param("routeScope", scope.routeScope())
                .param("resourceScopeId", scope.resourceScopeId())
                .param("idempotencyKey", scope.idempotencyKey())
                .query((resultSet, rowNumber) -> new IdempotencyRecord(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("request_hash"),
                        resultSet.getInt("hash_key_version"),
                        resultSet.getString("state"),
                        resultSet.getObject("response_status", Integer.class),
                        resultSet.getString("response_json")))
                .optional();
    }

    void complete(
            UUID id,
            int responseStatus,
            String responseJson,
            String resourceType,
            UUID resourceId,
            UUID agentRunId,
            Instant completedAt,
            Instant expiresAt) {
        int updated = jdbcClient
                .sql("""
                        UPDATE idempotency_records
                        SET state = 'COMPLETED',
                            response_status = :responseStatus,
                            response_json = CAST(:responseJson AS jsonb),
                            resource_type = :resourceType,
                            resource_id = :resourceId,
                            agent_run_id = :agentRunId,
                            completed_at = :completedAt,
                            expires_at = :expiresAt
                        WHERE id = :id AND state = 'IN_PROGRESS'
                        """)
                .param("responseStatus", responseStatus)
                .param("responseJson", responseJson)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("agentRunId", agentRunId)
                .param("completedAt", utc(completedAt))
                .param("expiresAt", utc(expiresAt))
                .param("id", id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record completion lost its reservation");
        }
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
