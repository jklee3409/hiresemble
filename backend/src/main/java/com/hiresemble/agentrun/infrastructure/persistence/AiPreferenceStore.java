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
                            id, user_id, budget_policy_version, default_quality_mode,
                            high_quality_enabled, daily_budget_usd, active, version,
                            created_at, updated_at
                        )
                        SELECT :id, :userId, policy.version, 'ECONOMY', false,
                               policy.user_default_daily_budget_usd, true, 0, :now, :now
                        FROM ai_budget_policy_versions policy
                        WHERE policy.active
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("active AI budget policy is not configured");
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
