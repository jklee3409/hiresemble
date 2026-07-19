package com.hiresemble.document.application;

import com.hiresemble.agentrun.application.ResourceCompensationPort;
import com.hiresemble.agentrun.application.AgentRunResourceOwnerResolver;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.domain.DocumentParseStatus;
import com.hiresemble.document.domain.DocumentRecords.ChunkDraft;
import com.hiresemble.document.domain.DocumentRecords.DocumentChunkRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentTextRecord;
import com.hiresemble.document.domain.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.document.domain.DocumentRecords.ParsedDocument;
import com.hiresemble.document.domain.DocumentRecords.ParsedPage;
import com.hiresemble.document.domain.DocumentRecords.SimilarChunk;
import com.hiresemble.document.infrastructure.DocumentParser;
import com.hiresemble.document.infrastructure.DocumentParsingException;
import com.hiresemble.document.infrastructure.DocumentStore;
import com.hiresemble.profile.application.DocumentEvidenceCommandPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentWorkflowService
        implements DocumentWorkflowQueryPort, DocumentWorkflowCommandPort,
                ResourceCompensationPort, AgentRunResourceOwnerResolver {

    private static final String RESOURCE_TYPE = "DOCUMENT";
    private final DocumentStore store;
    private final ObjectStoragePort storage;
    private final DocumentParser parser;
    private final DocumentTextNormalizer normalizer;
    private final DocumentPrivacyMasker masker;
    private final DocumentChunker chunker;
    private final DocumentEvidenceCommandPort evidencePort;
    private final Clock clock;

    public DocumentWorkflowService(
            DocumentStore store,
            ObjectStoragePort storage,
            DocumentParser parser,
            DocumentTextNormalizer normalizer,
            DocumentPrivacyMasker masker,
            DocumentChunker chunker,
            DocumentEvidenceCommandPort evidencePort,
            Clock clock) {
        this.store = store;
        this.storage = storage;
        this.parser = parser;
        this.normalizer = normalizer;
        this.masker = masker;
        this.chunker = chunker;
        this.evidencePort = evidencePort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentRecord snapshot(UUID userId, UUID documentId) {
        return store.findActive(userId, documentId).orElseThrow(this::notFound);
    }

    @Override
    public byte[] source(UUID userId, UUID documentId) {
        DocumentRecord document = snapshot(userId, documentId);
        return storage.read(document.storageKey());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<DocumentTextRecord> text(
            UUID userId, UUID documentId, long sourceRevision) {
        snapshot(userId, documentId);
        return store.findText(userId, documentId, sourceRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentChunkRecord> chunks(UUID userId, UUID documentId, long sourceRevision) {
        snapshot(userId, documentId);
        return store.findChunks(userId, documentId, sourceRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public EmbeddingPolicy activeEmbeddingPolicy() {
        return store.activeEmbeddingPolicy();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimilarChunk> exactCosineSearch(
            UUID userId,
            List<Double> queryVector,
            long policyVersion,
            int generation,
            int limit) {
        EmbeddingPolicy policy = store.activeEmbeddingPolicy();
        requireVector(queryVector, policy.dimension());
        if (policy.version() != policyVersion || policy.generation() != generation
                || limit < 1 || limit > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return store.exactCosineSearch(userId, queryVector, policyVersion, generation, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeleted(UUID userId, UUID documentId) {
        return store.findAny(userId, documentId).map(value -> value.deletedAt() != null).orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public String displayLabel(UUID userId, UUID documentId) {
        return snapshot(userId, documentId).displayName();
    }

    @Override
    @Transactional
    public DocumentRecord beginParsing(UUID userId, UUID documentId, UUID agentRunId) {
        requireLatestRun(snapshot(userId, documentId), agentRunId);
        return store.beginParsing(userId, documentId, agentRunId, clock.instant())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
    }

    @Override
    public DocumentTextRecord extractOrAcceptText(
            UUID userId, UUID documentId, UUID agentRunId) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        java.util.Optional<DocumentTextRecord> existing =
                store.findText(userId, documentId, document.sourceRevision());
        if (document.manualTextProvided() && existing.isPresent()
                && existing.get().extractedText() != null) {
            return existing.get();
        }
        try {
            ParsedDocument parsed = parser.parse(storage.read(document.storageKey()), document.mimeType());
            NormalizedPages normalized = normalizePages(parsed.pages());
            var normalizedText = normalizer.normalize(normalized.text());
            return store.saveExtractedTextAndState(
                    userId, documentId, agentRunId, document.sourceRevision(), normalizedText.text(),
                    normalizedText.characterCount(), parsed.pageCount(), normalized.pageOffsets(),
                    parsed.parserName(), parsed.parserVersion(), normalizedText.needsManualText(),
                    clock.instant());
        } catch (DocumentParsingException exception) {
            store.markParseFailed(userId, documentId, agentRunId, exception.safeCode(), clock.instant());
            throw exception;
        } catch (BusinessException exception) {
            String code = "DOCUMENT_TEXT_INVALID";
            store.markParseFailed(userId, documentId, agentRunId, code, clock.instant());
            throw new DocumentParsingException(code, exception);
        }
    }

    @Override
    @Transactional
    public DocumentTextRecord maskText(UUID userId, UUID documentId, UUID agentRunId) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        if (document.parseStatus() == DocumentParseStatus.NEEDS_MANUAL_TEXT) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        DocumentTextRecord text = store.findText(userId, documentId, document.sourceRevision())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
        String masked = masker.mask(text.extractedText());
        if (masked.length() != text.extractedText().length()) {
            throw new IllegalStateException("privacy masking changed positional length");
        }
        return store.saveMaskedText(
                userId, documentId, document.sourceRevision(), masked, clock.instant());
    }

    @Override
    @Transactional
    public List<DocumentChunkRecord> chunkText(UUID userId, UUID documentId, UUID agentRunId) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        DocumentTextRecord text = store.findText(userId, documentId, document.sourceRevision())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT));
        if (text.maskedText() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return replaceChunks(userId, documentId, agentRunId,
                chunker.chunk(text.extractedText(), text.maskedText(), text.pageOffsets()));
    }

    @Override
    @Transactional
    public List<DocumentChunkRecord> replaceChunks(
            UUID userId,
            UUID documentId,
            UUID agentRunId,
            List<ChunkDraft> drafts) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        validateChunks(drafts);
        List<DocumentChunkRecord> chunks = store.replaceChunks(
                userId, documentId, document.sourceRevision(), drafts, clock.instant());
        store.markParsed(userId, documentId, agentRunId, clock.instant());
        return chunks;
    }

    @Override
    @Transactional
    public void storeEmbeddings(
            UUID userId,
            UUID documentId,
            UUID agentRunId,
            EmbeddingPolicy policy,
            Map<UUID, List<Double>> embeddings) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        EmbeddingPolicy active = store.activeEmbeddingPolicy();
        if (!active.equals(policy)) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        List<DocumentChunkRecord> chunks =
                store.findChunks(userId, documentId, document.sourceRevision());
        Set<UUID> expected = chunks.stream().map(DocumentChunkRecord::id)
                .collect(java.util.stream.Collectors.toSet());
        if (embeddings == null || !expected.equals(embeddings.keySet())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        for (DocumentChunkRecord chunk : chunks) {
            List<Double> vector = embeddings.get(chunk.id());
            requireVector(vector, active.dimension());
            store.storeEmbedding(userId, documentId, chunk.id(), active, vector);
        }
    }

    @Override
    @Transactional
    public void beginEvidenceExtraction(UUID userId, UUID documentId, UUID agentRunId) {
        requireLatestRun(snapshot(userId, documentId), agentRunId);
        store.markExtracting(userId, documentId, agentRunId, clock.instant());
    }

    @Override
    @Transactional
    public EvidenceApplyResult applyEvidenceCandidates(
            UUID userId,
            UUID documentId,
            UUID agentRunId,
            List<DocumentEvidenceCandidate> candidates) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        var applied = evidencePort.applyCandidates(
                userId, documentId, document.sourceRevision(), candidates, clock.instant());
        store.markEvidenceSucceeded(userId, documentId, agentRunId, clock.instant());
        return new EvidenceApplyResult(applied.appliedEvidenceIds(), applied.rejectedCount());
    }

    @Override
    @Transactional
    public DocumentRecord finalizeDocument(UUID userId, UUID documentId, UUID agentRunId) {
        DocumentRecord document = snapshot(userId, documentId);
        requireLatestRun(document, agentRunId);
        if (document.parseStatus() != DocumentParseStatus.PARSED
                || document.evidenceExtractionStatus()
                        != com.hiresemble.document.domain.EvidenceExtractionStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return document;
    }

    @Override
    @Transactional
    public void failEvidenceExtraction(
            UUID userId, UUID documentId, UUID agentRunId, String safeErrorCode) {
        requireLatestRun(snapshot(userId, documentId), agentRunId);
        String code = safeErrorCode == null || safeErrorCode.isBlank()
                ? "DOCUMENT_EVIDENCE_EXTRACTION_FAILED"
                : safeErrorCode.substring(0, Math.min(100, safeErrorCode.length()));
        store.markEvidenceFailed(userId, documentId, agentRunId, code, clock.instant());
    }

    @Override
    @Transactional
    public void compensateToStableState(UUID userId, UUID documentId, UUID agentRunId) {
        store.stableCompensation(userId, documentId, agentRunId, clock.instant());
    }

    @Override
    public boolean supports(String resourceType) {
        return RESOURCE_TYPE.equals(resourceType);
    }

    @Override
    public void requireActiveOwner(UUID userId, UUID resourceId) {
        snapshot(userId, resourceId);
    }

    @Override
    public void compensate(UUID userId, UUID agentRunId, String resourceType, UUID resourceId) {
        if (!RESOURCE_TYPE.equals(resourceType)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        compensateToStableState(userId, resourceId, agentRunId);
    }

    private void requireLatestRun(DocumentRecord document, UUID agentRunId) {
        if (agentRunId == null || !agentRunId.equals(document.latestAgentRunId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
    }

    private NormalizedPages normalizePages(List<ParsedPage> pages) {
        StringBuilder combined = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        for (ParsedPage page : pages) {
            if (!combined.isEmpty()) combined.append('\n');
            offsets.add(combined.length());
            combined.append(normalizer.normalize(page.text()).text());
        }
        return new NormalizedPages(combined.toString(), List.copyOf(offsets));
    }

    private void validateChunks(List<ChunkDraft> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Set<Integer> indices = new HashSet<>();
        for (ChunkDraft chunk : chunks) {
            if (chunk == null || chunk.chunkIndex() < 0 || chunk.content().isBlank()
                    || chunk.maskedContent().isBlank()
                    || chunk.content().length() != chunk.maskedContent().length()
                    || !indices.add(chunk.chunkIndex())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }
        for (int index = 0; index < chunks.size(); index++) {
            if (!indices.contains(index)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void requireVector(List<Double> vector, int dimension) {
        if (vector == null || vector.size() != dimension
                || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private record NormalizedPages(String text, List<Integer> pageOffsets) {}
}
