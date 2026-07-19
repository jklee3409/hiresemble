package com.hiresemble.document.application;

import com.hiresemble.agentrun.application.WorkflowLaunchResult;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotencyScope;
import com.hiresemble.common.idempotency.IdempotencyService;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.common.idempotency.OriginalResponse;
import com.hiresemble.document.application.DocumentApplicationResults.DownloadUrl;
import com.hiresemble.document.application.DocumentApplicationResults.RunAccepted;
import com.hiresemble.document.application.DocumentApplicationResults.UploadAccepted;
import com.hiresemble.document.domain.DocumentParseStatus;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentTextRecord;
import com.hiresemble.document.domain.DocumentRecords.PageSlice;
import com.hiresemble.document.domain.DocumentType;
import com.hiresemble.document.domain.EvidenceExtractionStatus;
import com.hiresemble.document.infrastructure.DocumentFileInspector;
import com.hiresemble.document.infrastructure.DocumentFileInspector.InspectedFile;
import com.hiresemble.document.infrastructure.DocumentStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentApplicationService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final Set<String> SORTS = Set.of("uploadedAt,desc", "updatedAt,desc");
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);
    private final DocumentStore store;
    private final DocumentFileInspector inspector;
    private final ObjectStoragePort storage;
    private final ObjectDeletionOutboxService outbox;
    private final DocumentCreationService creationService;
    private final DocumentMutationService mutationService;
    private final DocumentTextNormalizer normalizer;
    private final IdempotencyService idempotency;

    public DocumentApplicationService(
            DocumentStore store,
            DocumentFileInspector inspector,
            ObjectStoragePort storage,
            ObjectDeletionOutboxService outbox,
            DocumentCreationService creationService,
            DocumentMutationService mutationService,
            DocumentTextNormalizer normalizer,
            IdempotencyService idempotency) {
        this.store = store;
        this.inspector = inspector;
        this.storage = storage;
        this.outbox = outbox;
        this.creationService = creationService;
        this.mutationService = mutationService;
        this.normalizer = normalizer;
        this.idempotency = idempotency;
    }

    public IdempotentResponse<UploadAccepted> upload(
            UUID userId,
            MultipartFile file,
            DocumentType documentType,
            String requestedDisplayName,
            String idempotencyKey) {
        validateKey(idempotencyKey);
        if (file == null || documentType == null) throw invalid();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException exception) {
            throw invalid();
        }
        InspectedFile inspected = inspector.inspect(file.getOriginalFilename(), content);
        String displayName = displayName(requestedDisplayName, inspected.filename());
        String checksum = sha256(content);
        String canonical = String.join("|", documentType.name(), inspected.filename(), displayName, checksum,
                Integer.toString(content.length), inspected.mimeType());
        IdempotencyScope scope = new IdempotencyScope(
                userId, "POST", "/api/v1/documents", IdempotencyScope.ROOT_SCOPE_ID,
                idempotencyKey);
        return idempotency.executePrepared(
                scope,
                canonical,
                UploadAccepted.class,
                () -> {
                    UUID documentId = UUID.randomUUID();
                    String storageKey = storageKey(userId, documentId);
                    try {
                        storage.upload(storageKey, content, inspected.mimeType(), checksum);
                    } catch (ObjectStorageException exception) {
                        throw unavailable(exception);
                    }
                    return new PreparedUpload(documentId, storageKey);
                },
                prepared -> {
                    String workflowHash = sha256(String.join("|", userId.toString(),
                            prepared.documentId().toString(), "1", checksum, documentType.name()));
                    var created = creationService.create(
                            prepared.documentId(), userId, documentType, inspected.filename(), displayName,
                            prepared.storageKey(), inspected.mimeType(), content.length, checksum, workflowHash);
                    UploadAccepted response = new UploadAccepted(
                            prepared.documentId(), DocumentParseStatus.UPLOADED,
                            EvidenceExtractionStatus.NOT_STARTED,
                            created.run().agentRunId(), created.run().status());
                    return new OriginalResponse<>(
                            202, response, "DOCUMENT", prepared.documentId(), created.run().agentRunId());
                },
                prepared -> compensateUpload(userId, prepared.storageKey()));
    }

    public PageSlice<DocumentRecord> list(
            UUID userId,
            DocumentType type,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceStatus,
            int page,
            int size,
            String requestedSort) {
        String sort = requestedSort == null || requestedSort.isBlank()
                ? "uploadedAt,desc" : requestedSort;
        if (!SORTS.contains(sort)) throw invalid();
        return store.list(userId, type, parseStatus, evidenceStatus, page, size, sort);
    }

    public DocumentRecord detail(UUID userId, UUID documentId) {
        return active(userId, documentId);
    }

    public Optional<DocumentTextRecord> detailText(UUID userId, UUID documentId) {
        DocumentRecord document = active(userId, documentId);
        return store.findText(userId, documentId, document.sourceRevision());
    }

    public DocumentTextRecord text(UUID userId, UUID documentId) {
        DocumentRecord document = active(userId, documentId);
        DocumentTextRecord text = store.findText(userId, documentId, document.sourceRevision())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
        if (text.extractedText() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return text;
    }

    public IdempotentResponse<RunAccepted> manualText(
            UUID userId,
            UUID documentId,
            String text,
            long version,
            String idempotencyKey) {
        validateKey(idempotencyKey);
        var normalized = normalizer.normalize(text);
        if (normalized.needsManualText()) throw invalid();
        String textHash = sha256(normalized.text());
        String canonical = version + "|" + textHash;
        IdempotencyScope scope = new IdempotencyScope(
                userId, "PUT", "/api/v1/documents/{documentId}/manual-text",
                documentId, idempotencyKey);
        return idempotency.execute(scope, canonical, RunAccepted.class, () -> {
            DocumentRecord current = active(userId, documentId);
            requireVersion(current, version);
            String workflowHash = sha256(String.join("|", userId.toString(), documentId.toString(),
                    Long.toString(current.sourceRevision() + 1), current.checksumSha256(), textHash));
            WorkflowLaunchResult result = mutationService.manualText(
                    userId, documentId, version, normalized.text(),
                    normalized.characterCount(), workflowHash);
            RunAccepted response = accepted(result);
            return new OriginalResponse<>(202, response, "DOCUMENT", documentId, result.agentRunId());
        });
    }

    public IdempotentResponse<RunAccepted> reparse(
            UUID userId,
            UUID documentId,
            long version,
            String idempotencyKey) {
        validateKey(idempotencyKey);
        String canonical = Long.toString(version);
        IdempotencyScope scope = new IdempotencyScope(
                userId, "POST", "/api/v1/documents/{documentId}/reparse",
                documentId, idempotencyKey);
        return idempotency.execute(scope, canonical, RunAccepted.class, () -> {
            DocumentRecord current = active(userId, documentId);
            requireVersion(current, version);
            String workflowHash = sha256(String.join("|", userId.toString(), documentId.toString(),
                    Long.toString(current.sourceRevision() + 1), current.checksumSha256(), "reparse"));
            WorkflowLaunchResult result = mutationService.reparse(
                    userId, documentId, version, workflowHash);
            RunAccepted response = accepted(result);
            return new OriginalResponse<>(202, response, "DOCUMENT", documentId, result.agentRunId());
        });
    }

    public DownloadUrl downloadUrl(UUID userId, UUID documentId) {
        DocumentRecord document = active(userId, documentId);
        try {
            ObjectStoragePort.PresignedObject presigned =
                    storage.presignGet(document.storageKey(), DOWNLOAD_TTL);
            return new DownloadUrl(presigned.uri().toASCIIString(), presigned.expiresAt());
        } catch (ObjectStorageException exception) {
            throw unavailable(exception);
        }
    }

    public void delete(UUID userId, UUID documentId, long version) {
        mutationService.delete(userId, documentId, version);
    }

    private void compensateUpload(UUID userId, String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (ObjectStorageException deleteFailure) {
            try {
                outbox.enqueueOrphan(userId, storageKey, java.time.Instant.now());
            } catch (RuntimeException cleanupFailure) {
                deleteFailure.addSuppressed(cleanupFailure);
                throw deleteFailure;
            }
        }
    }

    private RunAccepted accepted(WorkflowLaunchResult result) {
        return new RunAccepted(
                result.agentRunId(), result.status(), result.resourceType(), result.resourceId());
    }

    private String displayName(String requested, String fallback) {
        String value = requested == null ? fallback : requested.trim();
        if (value.isBlank() || value.length() > 255 || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return value;
    }

    private void validateKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) throw invalid();
    }

    private String storageKey(UUID userId, UUID documentId) {
        return "users/" + userId + "/documents/" + documentId + "/content";
    }

    private void requireVersion(DocumentRecord document, long expected) {
        if (expected < 0 || document.version() != expected) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    java.util.Map.of("field", "version", "reason", "STALE"), null);
        }
    }

    private DocumentRecord active(UUID userId, UUID documentId) {
        return store.findActive(userId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException unavailable(Throwable cause) {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, cause);
    }

    private record PreparedUpload(UUID documentId, String storageKey) {}
}
