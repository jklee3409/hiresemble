package com.hiresemble.agentrun.infrastructure.persistence;

import com.hiresemble.agentrun.application.port.AiPreferenceQueryPort;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AiPreferenceStore implements AiPreferenceQueryPort {

    private final JdbcClient jdbcClient;

    public AiPreferenceStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void createDefault(UUID userId, Instant now) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO user_ai_preferences (
                            id, user_id, default_quality_mode,
                            high_quality_enabled, active, version,
                            created_at, updated_at
                        )
                        VALUES (:id, :userId, 'ECONOMY', false, true, 0, :now, :now)
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("default AI preference was not created");
        }
    }

    @Override
    public AiPreferenceSnapshot activePreference(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return jdbcClient.sql("""
                        SELECT high_quality_enabled, version
                        FROM user_ai_preferences
                        WHERE user_id=:userId AND active
                        """)
                .param("userId", userId)
                .query((rs, row) -> new AiPreferenceSnapshot(
                        rs.getBoolean("high_quality_enabled"),
                        rs.getLong("version")))
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
