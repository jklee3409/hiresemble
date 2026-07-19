package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class P3MigrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.5-pg18-trixie")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("p3_migration_test")
            .withUsername("hiresemble")
            .withPassword("migration-test-password");

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanSchema() {
        flyway(null).clean();
    }

    @Test
    void emptyDatabaseMigratesThroughV4WithExactlyTheP3RuntimeTables() throws Exception {
        Flyway latest = flyway(null);
        assertThat(latest.migrate().success).isTrue();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();

        assertThat(appliedVersions()).containsExactly("1", "2", "3", "4");
        assertThat(publicTables()).contains(
                "agent_runs",
                "agent_steps",
                "ai_model_policies",
                "embedding_policy_versions",
                "ai_budget_policy_versions",
                "user_ai_preferences",
                "ai_price_versions",
                "ai_price_items",
                "ai_budget_ledgers",
                "ai_budget_reservations",
                "ai_usage_records");
        assertThat(publicTables()).doesNotContain(
                "documents",
                "document_texts",
                "document_chunks",
                "agent_run_resource_links",
                "job_postings",
                "cover_letters",
                "mock_interview_turns");
        assertThat(constraintNames("agent_runs")).contains(
                "agent_runs_user_id_id_uk",
                "agent_runs_retry_owner_fk",
                "agent_runs_root_owner_fk",
                "agent_runs_status_ck",
                "agent_runs_workflow_type_ck",
                "agent_runs_terminal_timestamp_ck",
                "agent_runs_state_version_ck");
        assertThat(indexNames("agent_runs")).contains("agent_runs_retry_predecessor_uk");
        assertThat(indexNames("ai_budget_reservations"))
                .contains("ai_budget_reservations_active_run_uk");
        assertThat(queryOne("SELECT count(*)::text FROM ai_price_versions")).isEqualTo("0");
        assertThat(queryOne("SELECT user_default_daily_budget_usd::text FROM ai_budget_policy_versions WHERE active"))
                .isEqualTo("1.000000");
        assertThat(queryOne("SELECT system_max_daily_budget_usd::text FROM ai_budget_policy_versions WHERE active"))
                .isEqualTo("2.000000");
        assertThat(queryOne("SELECT async_run_max_cost_usd::text FROM ai_budget_policy_versions WHERE active"))
                .isEqualTo("0.300000");
        assertThat(queryOne("SELECT reset_zone FROM ai_budget_policy_versions WHERE active"))
                .isEqualTo("Asia/Seoul");
    }

    @Test
    void v1V2AndV3ProductionLikeSchemasUpgradeForwardWithoutChangingHistory() throws Exception {
        for (String target : new String[] {"1", "2", "3"}) {
            flyway(null).clean();
            assertThat(flyway(target).migrate().success).isTrue();
            if (target.equals("3")) {
                seedUser("existing@example.com");
            }
            assertThat(flyway(null).migrate().success).isTrue();
            assertThat(appliedVersions()).containsExactly("1", "2", "3", "4");
            assertThat(publicTables()).contains("agent_runs", "ai_usage_records");
            if (target.equals("3")) {
                assertThat(queryOne("SELECT count(*)::text FROM user_ai_preferences"))
                        .isEqualTo("1");
                assertThat(queryOne("SELECT default_quality_mode FROM user_ai_preferences"))
                        .isEqualTo("ECONOMY");
                assertThat(queryOne("SELECT daily_budget_usd::text FROM user_ai_preferences"))
                        .isEqualTo("1.000000");
            }
        }
    }

    @Test
    void v1V2AndV3BytesRemainUnchanged() throws Exception {
        assertDigest("db/migration/V1__enable_extensions.sql",
                "9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c");
        assertDigest("db/migration/V2__create_identity_session_idempotency.sql",
                "c43f2d9a65426e6952d2b47f2908fb2c17c9b6093223f9c8b55ca346f9b21dcf");
        assertDigest("db/migration/V3__create_structured_profiles_and_direct_evidence.sql",
                "6ac81b6a6a55b51e5811b601dcd3b6b2c06d27911bfb539f37d399e071444347");
    }

    @Test
    void everyV4CheckConstraintIsInstalled() throws Exception {
        flyway(null).migrate();
        String migration;
        try (InputStream input = new ClassPathResource(
                "db/migration/V4__create_agent_runtime_and_ai_budget.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher matcher = Pattern.compile("CONSTRAINT\\s+([a-z0-9_]+_ck)\\s+CHECK")
                .matcher(migration);
        Set<String> declared = new LinkedHashSet<>();
        while (matcher.find()) {
            declared.add(matcher.group(1));
        }

        assertThat(declared).hasSize(71);
        assertThat(queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND constraint_type='CHECK'
                ORDER BY constraint_name
                """)).containsAll(declared);
    }

    @Test
    void checksOwnerForeignKeysRetryAndStepUniquenessAreDatabaseEnforced() throws Exception {
        flyway(null).migrate();
        UUID first = seedUser("first@example.com");
        UUID second = seedUser("second@example.com");
        seedPreference(first);
        seedPreference(second);
        seedModelPolicy(91);
        UUID failedRun = UUID.randomUUID();
        execute(runSql(failedRun, first, "FAILED", failedRun, null, 1, true));
        UUID successor = UUID.randomUUID();
        execute(runSql(successor, first, "QUEUED", failedRun, failedRun, 2, false));

        assertSqlFails(
                runSql(UUID.randomUUID(), first, "QUEUED", failedRun, failedRun, 2, false),
                "agent_runs_retry_predecessor_uk");
        assertSqlFails(stepSql(UUID.randomUUID(), second, failedRun, null, 1),
                "agent_steps_run_owner_fk");
        UUID running = UUID.randomUUID();
        execute(runSql(running, first, "QUEUED", running, null, 1, false));
        UUID step = UUID.randomUUID();
        execute(stepSql(step, first, running, null, 1));
        assertSqlFails(stepSql(UUID.randomUUID(), first, running, null, 1),
                "agent_steps_attempt_scope_uk");
        assertSqlFails("UPDATE agent_runs SET state_version=-1 WHERE id='" + running + "'",
                "agent_runs_state_version_ck");
        assertSqlFails("UPDATE agent_runs SET status='RUNNING', completed_at=NULL WHERE id='" + failedRun + "'",
                "terminal agent run cannot transition");
    }

    @Test
    void immutablePolicyAndPriceContentRejectMutationButActivePointerCanMove() throws Exception {
        flyway(null).migrate();
        assertSqlFails("UPDATE ai_budget_policy_versions SET async_run_max_cost_usd=0.4 WHERE version=1",
                "immutable AI policy content");
        assertSqlFails("DELETE FROM ai_budget_policy_versions WHERE version=1",
                "immutable AI policy content");
        execute("UPDATE ai_budget_policy_versions SET active=false WHERE version=1");
        execute("""
                INSERT INTO ai_budget_policy_versions VALUES (
                    gen_random_uuid(),2,1.0,2.0,0.3,0.03,0.3,'Asia/Seoul',true,now())
                """);
        assertThat(queryOne("SELECT version::text FROM ai_budget_policy_versions WHERE active"))
                .isEqualTo("2");

        execute("INSERT INTO ai_price_versions VALUES (gen_random_uuid(),1,'fixture','2026-01-01',NULL,now())");
        execute("""
                INSERT INTO ai_price_items VALUES (
                    gen_random_uuid(),1,'FAKE','fixture-chat','CHAT_INPUT_TOKEN',1000,0.001,now())
                """);
        assertSqlFails("UPDATE ai_price_items SET unit_price_usd=9 WHERE price_version=1",
                "immutable AI configuration");
        assertSqlFails("DELETE FROM ai_price_versions WHERE version=1",
                "immutable AI configuration");
    }

    @Test
    void ledgerPreferenceReservationAndUsageConstraintsProtectBudgetAccounting() throws Exception {
        flyway(null).migrate();
        UUID user = seedUser("budget-constraints@example.com");
        seedPreference(user);
        assertSqlFails("""
                INSERT INTO user_ai_preferences (
                    id,user_id,budget_policy_version,default_quality_mode,high_quality_enabled,
                    daily_budget_usd,active,version,created_at,updated_at
                ) VALUES (gen_random_uuid(),'%s',1,'ECONOMY',false,1,true,0,now(),now())
                """.formatted(user), "user_ai_preferences_active_user_uk");

        UUID run = UUID.randomUUID();
        execute(runSql(run, user, "QUEUED", run, null, 1, false));
        UUID ledger = UUID.randomUUID();
        execute(ledgerSql(ledger, user, 0, 0));
        assertSqlFails(ledgerSql(UUID.randomUUID(), user, 0, 0),
                "ai_budget_ledgers_user_date_zone_uk");
        assertSqlFails("UPDATE ai_budget_ledgers SET reserved_usd=-0.01 WHERE id='" + ledger + "'",
                "ai_budget_ledgers_cost_ck");

        UUID reservation = UUID.randomUUID();
        execute(reservationSql(reservation, user, ledger, run, "RESERVED", 0, 0));
        assertSqlFails(reservationSql(UUID.randomUUID(), user, ledger, run, "RESERVED", 0, 0),
                "ai_budget_reservations_active_run_uk");
        assertSqlFails("UPDATE ai_budget_reservations SET reserved_usd=-0.01 WHERE id='"
                        + reservation + "'",
                "ai_budget_reservations_cost_ck");
        execute("UPDATE ai_budget_reservations SET status='RELEASED', released_at=now(), "
                + "updated_at=now() WHERE id='" + reservation + "'");
        execute(reservationSql(UUID.randomUUID(), user, ledger, run, "RESERVED", 0, 0));

        UUID priceItem = UUID.randomUUID();
        execute("INSERT INTO ai_price_versions VALUES (gen_random_uuid(),1,'fixture','2026-01-01',NULL,now())");
        execute("INSERT INTO ai_price_versions VALUES (gen_random_uuid(),2,'fixture','2026-02-01',NULL,now())");
        execute("""
                INSERT INTO ai_price_items VALUES (
                    '%s',1,'FAKE','fixture-chat','CHAT_INPUT_TOKEN',1000,0.001,now())
                """.formatted(priceItem));
        assertSqlFails(usageSql(user, run, 2, priceItem, 0),
                "ai_usage_records_price_item_fk");
        assertSqlFails(usageSql(user, run, null, null, -0.01),
                "ai_usage_records_cost_ck");
        execute(usageSql(user, run, null, null, 0));
    }

    @Test
    void amountEnumAndTerminalShapeChecksRejectInvalidRows() throws Exception {
        flyway(null).migrate();
        UUID user = seedUser("checks@example.com");
        seedPreference(user);
        UUID run = UUID.randomUUID();
        execute(runSql(run, user, "QUEUED", run, null, 1, false));
        assertSqlFails("UPDATE agent_runs SET estimated_cost_usd=-0.1 WHERE id='" + run + "'",
                "agent_runs_cost_ck");
        assertSqlFails("UPDATE agent_runs SET workflow_type='FREE_LOOP' WHERE id='" + run + "'",
                "agent_runs_workflow_type_ck");
        assertSqlFails("UPDATE agent_runs SET status='SUCCEEDED' WHERE id='" + run + "'",
                "agent_runs_terminal_timestamp_ck");
        assertSqlFails("UPDATE user_ai_preferences SET daily_budget_usd=-1 WHERE user_id='" + user + "'",
                "user_ai_preferences_daily_budget_ck");
    }

    private UUID seedUser(String email) throws Exception {
        UUID userId = UUID.randomUUID();
        execute("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES ('%s','%s','hash','User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now());
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (gen_random_uuid(),'%s',NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """.formatted(userId, email, userId));
        return userId;
    }

    private void seedPreference(UUID userId) throws Exception {
        execute("""
                INSERT INTO user_ai_preferences (
                    id,user_id,budget_policy_version,default_quality_mode,high_quality_enabled,
                    daily_budget_usd,active,version,created_at,updated_at
                ) VALUES (gen_random_uuid(),'%s',1,'ECONOMY',false,1.0,true,0,now(),now())
                """.formatted(userId));
    }

    private void seedModelPolicy(long version) throws Exception {
        execute("INSERT INTO ai_model_policies VALUES (gen_random_uuid()," + version
                + ",'{}',false,now())");
    }

    private String ledgerSql(UUID id, UUID userId, double spent, double reserved) {
        return """
                INSERT INTO ai_budget_ledgers (
                    id,user_id,budget_date,budget_zone,spent_usd,reserved_usd,policy_version,
                    version,created_at,updated_at
                ) VALUES ('%s','%s','2026-07-19','Asia/Seoul',%s,%s,1,0,now(),now())
                """.formatted(id, userId, spent, reserved);
    }

    private String reservationSql(
            UUID id,
            UUID userId,
            UUID ledgerId,
            UUID runId,
            String status,
            double reserved,
            double settled) {
        return """
                INSERT INTO ai_budget_reservations (
                    id,user_id,ledger_id,operation_type,agent_run_id,reserved_usd,settled_usd,
                    status,expires_at,budget_policy_version,price_version,created_at,updated_at,
                    settled_at,released_at
                ) VALUES ('%s','%s','%s','FIXTURE','%s',%s,%s,'%s',now()+interval '1 hour',
                    1,NULL,now(),now(),NULL,NULL)
                """.formatted(id, userId, ledgerId, runId, reserved, settled, status);
    }

    private String usageSql(
            UUID userId,
            UUID runId,
            Integer priceVersion,
            UUID priceItemId,
            double cost) {
        String version = priceVersion == null ? "NULL" : priceVersion.toString();
        String item = priceItemId == null ? "NULL" : "'" + priceItemId + "'";
        return """
                INSERT INTO ai_usage_records (
                    id,user_id,agent_run_id,agent_step_id,operation_type,usage_type,provider_key,
                    product_key,model_tier,input_units,cached_input_units,output_units,
                    embedding_units,search_units,price_version,price_item_id,cost_usd,duration_ms,
                    created_at
                ) VALUES (gen_random_uuid(),'%s','%s',NULL,'FIXTURE','CHAT','FAKE','fixture-chat',
                    'LOW_COST',0,0,0,0,0,%s,%s,%s,0,now())
                """.formatted(userId, runId, version, item, cost);
    }

    private String runSql(
            UUID id,
            UUID userId,
            String status,
            UUID rootRunId,
            UUID retryOf,
            int runAttempt,
            boolean retryable) {
        String terminal = Set.of("FAILED", "SUCCEEDED", "CANCELLED", "INTERRUPTED").contains(status)
                ? "now()" : "NULL";
        return """
                INSERT INTO agent_runs (
                    id,user_id,workflow_type,status,current_step,progress_percent,workflow_version,
                    canonical_input_hash,input_reference_snapshot,budget_policy_version,price_version,
                    requested_quality_mode,highest_model_tier_used,estimated_cost_usd,reserved_cost_usd,
                    actual_cost_usd,resource_type,resource_id,retry_of_run_id,root_run_id,run_attempt_no,
                    retryable_failure,state_version,queued_at,started_at,completed_at,updated_at
                ) VALUES (
                    '%s','%s','JOB_ANALYSIS','%s',NULL,0,'fixture-v1',repeat('a',64),'{}',1,NULL,
                    'ECONOMY',NULL,0,0,0,NULL,NULL,%s,'%s',%d,%s,0,now(),NULL,%s,now())
                """.formatted(
                id, userId, status, retryOf == null ? "NULL" : "'" + retryOf + "'",
                rootRunId, runAttempt, retryable, terminal);
    }

    private String stepSql(UUID id, UUID userId, UUID runId, String scopeKey, int attempt) {
        return """
                INSERT INTO agent_steps (
                    id,user_id,agent_run_id,step_key,scope_key,step_order,agent_name,status,
                    attempt,max_attempts,input_hash,input_refs,output_schema_version,
                    model_policy_version,prompt_version,created_at,updated_at
                ) VALUES ('%s','%s','%s','FIXTURE',%s,1,'Fixture','PENDING',%d,3,
                    repeat('b',64),'{}','v1',91,'fixture-v1',now(),now())
                """.formatted(id, userId, runId, scopeKey == null ? "NULL" : "'" + scopeKey + "'", attempt);
    }

    private void assertDigest(String path, String expected) throws Exception {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(digest).isEqualTo(expected);
        }
    }

    private void assertSqlFails(String sql, String message) {
        assertThatThrownBy(() -> execute(sql))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(message);
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryOne(String sql) throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private Set<String> publicTables() throws Exception {
        return queryStrings("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' ORDER BY table_name
                """);
    }

    private Set<String> constraintNames(String table) throws Exception {
        return queryStrings("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema='public' AND table_name='%s' ORDER BY constraint_name
                """.formatted(table));
    }

    private Set<String> indexNames(String table) throws Exception {
        return queryStrings("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname='public' AND tablename='%s' ORDER BY indexname
                """.formatted(table));
    }

    private Set<String> appliedVersions() throws Exception {
        return queryStrings("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank");
    }

    private Set<String> queryStrings(String sql) throws Exception {
        Set<String> values = new LinkedHashSet<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) values.add(resultSet.getString(1));
        }
        return values;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false);
        configuration.target(target == null ? "4" : target);
        return configuration.load();
    }
}
