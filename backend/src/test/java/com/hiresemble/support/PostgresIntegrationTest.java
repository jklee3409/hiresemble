package com.hiresemble.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=false")
public abstract class PostgresIntegrationTest {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("hiresemble_test")
            .withUsername("hiresemble")
            .withPassword("hiresemble-test-password");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("hiresemble.idempotency.hmac-keys[1]", () -> "test-hmac-key-with-enough-entropy");
        registry.add("spring.session.jdbc.initialize-schema", () -> "never");
    }

    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanApplicationTables() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM spring_session_attributes");
            jdbcTemplate.update("DELETE FROM spring_session");
            jdbcTemplate.update("DELETE FROM object_deletion_outbox");
            jdbcTemplate.update("DELETE FROM profile_evidence");
            jdbcTemplate.update("UPDATE certifications SET evidence_document_id=NULL WHERE evidence_document_id IS NOT NULL");
            jdbcTemplate.update("UPDATE language_scores SET evidence_document_id=NULL WHERE evidence_document_id IS NOT NULL");
            jdbcTemplate.update("UPDATE awards SET evidence_document_id=NULL WHERE evidence_document_id IS NOT NULL");
            jdbcTemplate.update("DELETE FROM document_chunks");
            jdbcTemplate.update("DELETE FROM document_texts");
            jdbcTemplate.update("UPDATE documents SET latest_agent_run_id=NULL");
            jdbcTemplate.update("DELETE FROM agent_run_resource_links");
            jdbcTemplate.update("DELETE FROM documents");
            jdbcTemplate.update("DELETE FROM ai_usage_records");
            jdbcTemplate.update("DELETE FROM ai_budget_reservations");
            jdbcTemplate.update("DELETE FROM ai_budget_ledgers");
            jdbcTemplate.update("DELETE FROM agent_steps");
            jdbcTemplate.update("DELETE FROM idempotency_records");
            jdbcTemplate.update("DELETE FROM agent_runs");
            jdbcTemplate.update("DELETE FROM user_ai_preferences");
            jdbcTemplate.update("DELETE FROM careers");
            jdbcTemplate.update("DELETE FROM awards");
            jdbcTemplate.update("DELETE FROM language_scores");
            jdbcTemplate.update("DELETE FROM certifications");
            jdbcTemplate.update("DELETE FROM educations");
            jdbcTemplate.update("DELETE FROM user_profiles");
            jdbcTemplate.update("DELETE FROM users");
        });
    }
}
