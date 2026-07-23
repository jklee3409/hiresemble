package com.hiresemble.document.infrastructure.worker;

import com.hiresemble.document.application.port.ObjectDeletionAlertPort;
import com.hiresemble.document.application.port.ObjectStorageException;
import com.hiresemble.document.application.port.ObjectStoragePort;
import static org.assertj.core.api.Assertions.assertThat;
import com.hiresemble.document.infrastructure.persistence.ObjectDeletionOutboxStore;
import com.hiresemble.support.PostgresIntegrationTest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(ObjectDeletionOutboxIntegrationTest.FakePorts.class)
class ObjectDeletionOutboxIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ObjectDeletionOutboxService service;
    @Autowired private ObjectDeletionOutboxStore store;
    @Autowired private FailingStorage storage;
    @Autowired private RecordingAlert alert;

    private UUID userId;

    @BeforeEach
    void seedOwnerAndResetFakes() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,?,'Outbox User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """, userId, "outbox-" + userId + "@example.com", "hash");
        jdbcTemplate.update("""
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (?,?,NULL,NULL,'[]','[]','[]',NULL,0,now(),now())
                """, UUID.randomUUID(), userId);
        storage.failuresRemaining.set(0);
        storage.deleteAttempts.set(0);
        alert.deadId.set(null);
    }

    @Test
    void transientFailureUsesOneMinuteThenSucceedsAndStoresOnlySafeError() {
        storage.failuresRemaining.set(1);
        UUID id = service.enqueueOrphan(userId, "users/orphan/safe-key", Instant.now());

        service.processDue();

        var first = jdbcTemplate.queryForMap("""
                SELECT status,attempt_count,last_error_code,
                       EXTRACT(EPOCH FROM (next_attempt_at-created_at)) AS delay_seconds
                FROM object_deletion_outbox WHERE id=?
                """, id);
        assertThat(first.get("status")).isEqualTo("PENDING");
        assertThat(first.get("attempt_count")).isEqualTo(1);
        assertThat(first.get("last_error_code")).isEqualTo("OBJECT_STORAGE_DELETE_FAILED");
        assertThat(((Number) first.get("delay_seconds")).doubleValue()).isBetween(58d, 62d);

        jdbcTemplate.update(
                "UPDATE object_deletion_outbox SET next_attempt_at=now()-interval '1 second' WHERE id=?", id);
        service.processDue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM object_deletion_outbox WHERE id=?", String.class, id))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM object_deletion_outbox WHERE id=?", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    void leaseExpiryRecoversClaimAndConcurrentClaimDoesNotDuplicateWork() {
        UUID id = service.enqueueOrphan(userId, "users/orphan/lease-key", Instant.now());
        Instant now = Instant.now().plusSeconds(1);

        var first = store.claimDue(now, Duration.ofMinutes(2)).orElseThrow();
        assertThat(first.id()).isEqualTo(id);
        assertThat(store.claimDue(now, Duration.ofMinutes(2))).isEmpty();

        var recovered = store.claimDue(now.plus(Duration.ofMinutes(3)), Duration.ofMinutes(2)).orElseThrow();
        assertThat(recovered.id()).isEqualTo(id);
        assertThat(recovered.claimToken()).isNotEqualTo(first.claimToken());
        assertThat(recovered.attemptCount()).isEqualTo(2);
        assertThat(store.markSucceeded(id, first.claimToken(), now.plusSeconds(1))).isFalse();
        assertThat(store.markSucceeded(id, recovered.claimToken(), now.plus(Duration.ofMinutes(3)))).isTrue();
    }

    @Test
    void tenthFailureMovesToDeadAndCallsOperationsHookOnce() {
        storage.failuresRemaining.set(20);
        UUID id = service.enqueueOrphan(userId, "users/orphan/dead-key", Instant.now());

        for (int attempt = 1; attempt <= 10; attempt++) {
            jdbcTemplate.update(
                    "UPDATE object_deletion_outbox SET next_attempt_at=now()-interval '1 second' WHERE id=?", id);
            service.processDue();
        }

        var dead = jdbcTemplate.queryForMap(
                "SELECT status,attempt_count,completed_at IS NOT NULL AS completed FROM object_deletion_outbox WHERE id=?",
                id);
        assertThat(dead.get("status")).isEqualTo("DEAD");
        assertThat(dead.get("attempt_count")).isEqualTo(10);
        assertThat(dead.get("completed")).isEqualTo(true);
        assertThat(alert.deadId.get()).isEqualTo(id);
        assertThat(storage.deleteAttempts.get()).isEqualTo(10);
    }

    @Test
    void duplicateEnqueueReturnsTheActiveRowAndAbsentObjectIsSuccessful() {
        String key = "users/orphan/already-absent";
        UUID first = service.enqueueOrphan(userId, key, Instant.now());
        UUID duplicate = service.enqueueOrphan(userId, key, Instant.now().plusSeconds(1));
        assertThat(duplicate).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM object_deletion_outbox WHERE storage_key=?", Long.class, key))
                .isEqualTo(1L);

        service.processDue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM object_deletion_outbox WHERE id=?", String.class, first))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void retryScheduleMatchesTheApprovedBackoff() {
        assertThat(ObjectDeletionOutboxService.delay(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(ObjectDeletionOutboxService.delay(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(ObjectDeletionOutboxService.delay(3)).isEqualTo(Duration.ofMinutes(30));
        assertThat(ObjectDeletionOutboxService.delay(4)).isEqualTo(Duration.ofHours(2));
        assertThat(ObjectDeletionOutboxService.delay(5)).isEqualTo(Duration.ofHours(12));
        assertThat(ObjectDeletionOutboxService.delay(6)).isEqualTo(Duration.ofHours(24));
        assertThat(ObjectDeletionOutboxService.delay(10)).isEqualTo(Duration.ofHours(24));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakePorts {
        @Bean
        @Primary
        FailingStorage failingStorage() {
            return new FailingStorage();
        }

        @Bean
        RecordingAlert recordingAlert() {
            return new RecordingAlert();
        }
    }

    static final class RecordingAlert implements ObjectDeletionAlertPort {
        final AtomicReference<UUID> deadId = new AtomicReference<>();

        @Override
        public void deadLetter(UUID outboxId, UUID userId) {
            deadId.set(outboxId);
        }
    }

    static final class FailingStorage implements ObjectStoragePort {
        final AtomicInteger failuresRemaining = new AtomicInteger();
        final AtomicInteger deleteAttempts = new AtomicInteger();

        @Override
        public void upload(String storageKey, byte[] content, String mimeType, String checksumSha256) {}

        @Override
        public byte[] read(String storageKey) {
            return new byte[0];
        }

        @Override
        public void delete(String storageKey) {
            deleteAttempts.incrementAndGet();
            if (failuresRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new ObjectStorageException(new IllegalStateException("provider-secret-error"));
            }
        }

        @Override
        public ObjectMetadata metadata(String storageKey) {
            throw new ObjectStorageException(new IllegalStateException("absent"));
        }

        @Override
        public PresignedObject presignGet(String storageKey, Duration ttl) {
            return new PresignedObject(URI.create("https://unused.invalid"), Instant.now().plus(ttl));
        }
    }
}
