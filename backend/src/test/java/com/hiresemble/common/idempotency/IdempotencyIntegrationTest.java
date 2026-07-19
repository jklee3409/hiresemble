package com.hiresemble.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.support.PostgresIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

class IdempotencyIntegrationTest extends PostgresIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String KEY = "fixture-key-001";

    @Autowired private IdempotencyService service;
    @Autowired private IdempotencyRepository repository;
    @Autowired private IdempotencyRequestHasher hasher;
    @Autowired private IdempotencyProperties properties;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void insertUser() {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, email, password_hash, display_name, role, status,
                    terms_agreed_at, ai_consent_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?, ?)
                """,
                USER_ID,
                "idempotency@example.com",
                "$2a$12$fixture-hash-not-used",
                "Fixture",
                now,
                now,
                now,
                now);
    }

    @Test
    void sameScopeKeyAndHashReplaysTheOriginalStatusAndDto() {
        AtomicInteger calls = new AtomicInteger();
        IdempotencyScope scope = scope(KEY);

        IdempotentResponse<FixtureResponse> first = service.execute(
                scope,
                "{\"value\":\"same\"}",
                FixtureResponse.class,
                () -> {
                    calls.incrementAndGet();
                    return new OriginalResponse<>(202, new FixtureResponse(UUID.randomUUID(), "accepted"));
                });
        IdempotentResponse<FixtureResponse> replay = service.execute(
                scope,
                "{\"value\":\"same\"}",
                FixtureResponse.class,
                () -> {
                    calls.incrementAndGet();
                    return new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "wrong"));
                });

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.status()).isEqualTo(202);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(calls).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT state FROM idempotency_records WHERE idempotency_key = ?",
                        String.class,
                        KEY))
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiredCompletedRecordIsAtomicallyReclaimedWithoutReplayingOldHashStatusOrBody() {
        IdempotencyScope scope = scope(KEY);
        IdempotentResponse<FixtureResponse> expired = service.execute(
                scope,
                "{\"value\":\"expired\"}",
                FixtureResponse.class,
                () -> new OriginalResponse<>(
                        202, new FixtureResponse(UUID.randomUUID(), "expired-response")));
        UUID expiredRecordId = jdbcTemplate.queryForObject(
                "SELECT id FROM idempotency_records WHERE idempotency_key = ?",
                UUID.class,
                KEY);
        expireRecord(KEY);
        AtomicInteger calls = new AtomicInteger();

        IdempotentResponse<FixtureResponse> replacement = service.execute(
                scope,
                "{\"value\":\"replacement\"}",
                FixtureResponse.class,
                () -> {
                    calls.incrementAndGet();
                    return new OriginalResponse<>(
                            201, new FixtureResponse(UUID.randomUUID(), "replacement-response"));
                });

        assertThat(replacement.replayed()).isFalse();
        assertThat(replacement.status()).isEqualTo(201).isNotEqualTo(expired.status());
        assertThat(replacement.body().outcome())
                .isEqualTo("replacement-response")
                .isNotEqualTo(expired.body().outcome());
        assertThat(calls).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT id FROM idempotency_records WHERE idempotency_key = ?",
                        UUID.class,
                        KEY))
                .isNotEqualTo(expiredRecordId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM idempotency_records WHERE idempotency_key = ?",
                        Long.class,
                        KEY))
                .isEqualTo(1L);
    }

    @Test
    void concurrentExpiredRecordReclaimAllowsOnlyOneNewOperation() throws Exception {
        IdempotencyScope scope = scope(KEY);
        service.execute(
                scope,
                "{\"value\":\"expired\"}",
                FixtureResponse.class,
                () -> new OriginalResponse<>(
                        202, new FixtureResponse(UUID.randomUUID(), "expired-response")));
        expireRecord(KEY);
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<IdempotentResponse<FixtureResponse>> first = executor.submit(() -> service.execute(
                    scope,
                    "{\"value\":\"replacement\"}",
                    FixtureResponse.class,
                    () -> {
                        operationEntered.countDown();
                        await(allowCompletion);
                        return new OriginalResponse<>(
                                201, new FixtureResponse(UUID.randomUUID(), "replacement"));
                    }));
            assertThat(operationEntered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.execute(
                            scope,
                            "{\"value\":\"replacement\"}",
                            FixtureResponse.class,
                            () -> new OriginalResponse<>(
                                    201, new FixtureResponse(UUID.randomUUID(), "duplicate"))))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

            allowCompletion.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM idempotency_records WHERE idempotency_key = ?",
                            Long.class,
                            KEY))
                    .isEqualTo(1L);
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredInProgressRecordLinkedToARunIsNotReclaimedBlindly() {
        IdempotencyScope scope = scope(KEY);
        service.execute(
                scope,
                "{\"value\":\"linked\"}",
                FixtureResponse.class,
                () -> new OriginalResponse<>(
                        202, new FixtureResponse(UUID.randomUUID(), "linked")));
        UUID originalRecordId = jdbcTemplate.queryForObject(
                "SELECT id FROM idempotency_records WHERE idempotency_key = ?",
                UUID.class,
                KEY);
        UUID linkedRunId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO agent_runs (
                    id,user_id,workflow_type,status,progress_percent,workflow_version,
                    canonical_input_hash,input_reference_snapshot,budget_policy_version,
                    price_version,requested_quality_mode,estimated_cost_usd,reserved_cost_usd,
                    actual_cost_usd,root_run_id,run_attempt_no,retryable_failure,state_version,
                    queued_at,updated_at
                ) VALUES (? ,?,'JOB_ANALYSIS','QUEUED',0,'fixture-v1',repeat('a',64),'{}',
                    1,NULL,'ECONOMY',0,0,0,?,1,false,0,now(),now())
                """, linkedRunId, USER_ID, linkedRunId);
        jdbcTemplate.update(
                """
                UPDATE idempotency_records
                SET state = 'IN_PROGRESS',
                    response_status = NULL,
                    response_json = NULL,
                    completed_at = NULL,
                    agent_run_id = ?,
                    created_at = CURRENT_TIMESTAMP - INTERVAL '25 hours',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE idempotency_key = ?
                """,
                linkedRunId,
                KEY);

        assertThatThrownBy(() -> service.execute(
                        scope,
                        "{\"value\":\"linked\"}",
                        FixtureResponse.class,
                        () -> new OriginalResponse<>(
                                201, new FixtureResponse(UUID.randomUUID(), "must-not-run"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT id FROM idempotency_records WHERE idempotency_key = ?",
                        UUID.class,
                        KEY))
                .isEqualTo(originalRecordId);
    }

    @Test
    void sameKeyWithDifferentHashIsRejectedAndSensitiveCanonicalBodyIsNeverStored() {
        String sensitiveCanonical = "{\"password\":\"plain-secret\",\"value\":\"one\"}";
        service.execute(
                scope(KEY),
                sensitiveCanonical,
                FixtureResponse.class,
                () -> new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "safe")));

        assertThatThrownBy(() -> service.execute(
                        scope(KEY),
                        "{\"password\":\"another-secret\",\"value\":\"two\"}",
                        FixtureResponse.class,
                        () -> new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "wrong"))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));

        String stored = jdbcTemplate.queryForObject(
                """
                SELECT request_hash || '|' || response_json::text
                FROM idempotency_records WHERE idempotency_key = ?
                """,
                String.class,
                KEY);
        assertThat(stored)
                .doesNotContain("plain-secret")
                .doesNotContain("another-secret")
                .doesNotContain(sensitiveCanonical)
                .doesNotContain(properties.keyFor(1));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT char_length(request_hash) FROM idempotency_records WHERE idempotency_key = ?",
                        Integer.class,
                        KEY))
                .isEqualTo(64);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT EXTRACT(EPOCH FROM (expires_at - completed_at))::bigint
                        FROM idempotency_records WHERE idempotency_key = ?
                        """,
                        Long.class,
                        KEY))
                .isBetween(86_399L, 86_401L);
    }

    @Test
    void concurrentSameHashRequestObservesDurableInProgressReservation() throws Exception {
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<IdempotentResponse<FixtureResponse>> first = executor.submit(() -> service.execute(
                    scope(KEY),
                    "{\"value\":\"same\"}",
                    FixtureResponse.class,
                    () -> {
                        operationEntered.countDown();
                        await(allowCompletion);
                        return new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "created"));
                    }));
            assertThat(operationEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT state FROM idempotency_records WHERE idempotency_key = ?",
                            String.class,
                            KEY))
                    .isEqualTo("IN_PROGRESS");

            assertThatThrownBy(() -> service.execute(
                            scope(KEY),
                            "{\"value\":\"same\"}",
                            FixtureResponse.class,
                            () -> new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "duplicate"))))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

            allowCompletion.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(201);
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void aFreshServiceInstanceReplaysTheDatabaseRecordAfterAProcessRestartBoundary() {
        IdempotentResponse<FixtureResponse> first = service.execute(
                scope(KEY),
                "{\"value\":\"durable\"}",
                FixtureResponse.class,
                () -> new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "durable")));
        IdempotencyService restarted = new IdempotencyService(
                repository, hasher, properties, objectMapper, transactionManager);

        IdempotentResponse<FixtureResponse> replay = restarted.execute(
                scope(KEY),
                "{\"value\":\"durable\"}",
                FixtureResponse.class,
                () -> new OriginalResponse<>(500, new FixtureResponse(UUID.randomUUID(), "must-not-run")));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
    }

    @Test
    void validationAuthenticationAndOwnershipFixtureFailuresHappenBeforeReservation() {
        IdempotencyApplicationFixture fixture = new IdempotencyApplicationFixture(service);

        assertThatThrownBy(() -> fixture.execute(false, true, true, scope("auth-failure-001")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
        assertThatThrownBy(() -> fixture.execute(true, false, true, scope("validation-001")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> fixture.execute(true, true, false, scope("ownership-001")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_records", Long.class))
                .isZero();
    }

    private IdempotencyScope scope(String key) {
        return new IdempotencyScope(
                USER_ID, "POST", "/api/v1/test-resources", IdempotencyScope.ROOT_SCOPE_ID, key);
    }

    private void expireRecord(String key) {
        jdbcTemplate.update(
                """
                UPDATE idempotency_records
                SET created_at = CURRENT_TIMESTAMP - INTERVAL '25 hours',
                    completed_at = CURRENT_TIMESTAMP - INTERVAL '25 hours',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE idempotency_key = ?
                """,
                key);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test fixture");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test fixture", exception);
        }
    }

    record FixtureResponse(UUID resourceId, String outcome) {}

    private static final class IdempotencyApplicationFixture {
        private final IdempotencyService service;

        private IdempotencyApplicationFixture(IdempotencyService service) {
            this.service = service;
        }

        private IdempotentResponse<FixtureResponse> execute(
                boolean authenticated,
                boolean valid,
                boolean owner,
                IdempotencyScope scope) {
            if (!authenticated) {
                throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
            }
            if (!valid) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            if (!owner) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return service.execute(
                    scope,
                    "{\"value\":\"fixture\"}",
                    FixtureResponse.class,
                    () -> new OriginalResponse<>(201, new FixtureResponse(UUID.randomUUID(), "created")));
        }
    }
}
