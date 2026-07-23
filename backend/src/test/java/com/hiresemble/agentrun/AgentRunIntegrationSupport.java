package com.hiresemble.agentrun;

import com.hiresemble.agentrun.application.port.AgentRunQueryPort;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.WorkflowLauncher;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

abstract class AgentRunIntegrationSupport extends PostgresIntegrationTest {

    protected static final String INPUT_HASH = "a".repeat(64);

    @Autowired protected WorkflowLauncher workflowLauncher;
    @Autowired protected AgentRunQueryPort queryPort;
    @Autowired protected ObjectMapper objectMapper;

    protected UUID seedUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,?,'Agent User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """, userId, email, "hash");
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), userId);
        jdbcTemplate.update("""
                INSERT INTO user_ai_preferences (
                    id,user_id,budget_policy_version,default_quality_mode,high_quality_enabled,
                    daily_budget_usd,active,version,created_at,updated_at
                ) VALUES (?,?,1,'ECONOMY',false,1.000000,true,0,now(),now())
                """, UUID.randomUUID(), userId);
        return userId;
    }

    protected WorkflowLaunchResult launch(UUID userId) {
        return launch(userId, new BigDecimal("0.000000"), null);
    }

    protected WorkflowLaunchResult launch(UUID userId, BigDecimal estimatedCost, Long priceVersion) {
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                userId,
                WorkflowType.JOB_ANALYSIS,
                "fixture-v1",
                INPUT_HASH,
                objectMapper.createObjectNode()
                        .put("fixtureRef", "safe-fixture")
                        .put("capturedAt", Instant.parse("2026-07-19T00:00:00Z").toString()),
                AiQualityMode.ECONOMY,
                estimatedCost,
                priceVersion,
                null));
    }

    protected AgentRunSnapshot run(UUID userId, UUID runId) {
        return queryPort.findByOwner(userId, runId).orElseThrow();
    }

    protected long seedPriceVersion() {
        long version = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L)
                + 1_000_000L;
        jdbcTemplate.update("""
                INSERT INTO ai_price_versions (
                    id,version,catalog_key,effective_from,effective_to,created_at
                ) VALUES (?,?,?,now(),NULL,now())
                """, UUID.randomUUID(), version, "fixture-" + version);
        return version;
    }

    protected long seedModelPolicy() {
        long version = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000L)
                + 1_000_000L;
        jdbcTemplate.update("""
                INSERT INTO ai_model_policies (id,version,policy_json,active,created_at)
                VALUES (?,?,'{}',false,now())
                """, UUID.randomUUID(), version);
        return version;
    }
}
