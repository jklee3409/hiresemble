package com.hiresemble.agentrun.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AiPreferenceStore {

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
}
