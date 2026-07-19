package com.hiresemble.common.idempotency;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdempotencyService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private final IdempotencyRepository repository;
    private final IdempotencyRequestHasher hasher;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public IdempotencyService(
            IdempotencyRepository repository,
            IdempotencyRequestHasher hasher,
            IdempotencyProperties properties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.hasher = hasher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> IdempotentResponse<T> execute(
            IdempotencyScope scope,
            byte[] canonicalRequest,
            Class<T> responseType,
            Supplier<OriginalResponse<T>> operation) {
        validate(scope);
        int currentVersion = hasher.currentVersion();
        String currentHash = hasher.hash(currentVersion, scope, canonicalRequest);
        UUID recordId = UUID.randomUUID();
        Instant now = Instant.now();
        boolean inserted = Boolean.TRUE.equals(requiresNew.execute(status -> repository.tryReserve(
                recordId,
                scope,
                currentHash,
                currentVersion,
                now,
                now.plus(properties.getTtl()))));

        if (!inserted) {
            IdempotencyRecord existing = requiresNew.execute(status -> repository.find(scope)
                    .orElseThrow(() -> new IllegalStateException("Conflicting record disappeared")));
            String comparableHash =
                    hasher.hash(existing.hashKeyVersion(), scope, canonicalRequest);
            if (!hashesEqual(existing.requestHash(), comparableHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (!existing.completed()) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            }
            return new IdempotentResponse<>(
                    existing.responseStatus(), read(existing.responseJson(), responseType), true);
        }

        OriginalResponse<T> original = operation.get();
        String responseJson = write(original.body());
        Instant completedAt = Instant.now();
        requiresNew.executeWithoutResult(status -> repository.complete(
                recordId,
                original.status(),
                responseJson,
                original.resourceType(),
                original.resourceId(),
                original.agentRunId(),
                completedAt,
                completedAt.plus(properties.getTtl())));
        return new IdempotentResponse<>(original.status(), original.body(), false);
    }

    public <T> IdempotentResponse<T> execute(
            IdempotencyScope scope,
            String canonicalRequest,
            Class<T> responseType,
            Supplier<OriginalResponse<T>> operation) {
        return execute(
                scope,
                canonicalRequest.getBytes(StandardCharsets.UTF_8),
                responseType,
                operation);
    }

    private void validate(IdempotencyScope scope) {
        if (scope.userId() == null
                || scope.resourceScopeId() == null
                || scope.httpMethod() == null
                || scope.routeScope() == null
                || scope.routeScope().isBlank()
                || scope.routeScope().length() > 500
                || scope.idempotencyKey() == null
                || !KEY_PATTERN.matcher(scope.idempotencyKey()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean hashesEqual(String left, String right) {
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(left), HexFormat.of().parseHex(right));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Idempotent response could not be serialized", exception);
        }
    }

    private <T> T read(String json, Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored idempotent response could not be read", exception);
        }
    }
}
