package com.hiresemble.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DocumentRecords {

    private DocumentRecords() {}

    public record DocumentRecord(
            UUID id,
            UUID userId,
            DocumentType documentType,
            String originalFilename,
            String displayName,
            String storageKey,
            String mimeType,
            long fileSizeBytes,
            String checksumSha256,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceExtractionStatus,
            String parseErrorCode,
            String evidenceErrorCode,
            boolean manualTextProvided,
            long sourceRevision,
            UUID latestAgentRunId,
            long version,
            Instant uploadedAt,
            Instant updatedAt,
            Instant deletedAt) {}

    public record DocumentTextRecord(
            UUID id,
            UUID userId,
            UUID documentId,
            long sourceRevision,
            String extractedText,
            String maskedText,
            int characterCount,
            Integer pageCount,
            List<Integer> pageOffsets,
            String parserName,
            String parserVersion,
            Instant parsedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        public DocumentTextRecord {
            pageOffsets = pageOffsets == null ? List.of() : List.copyOf(pageOffsets);
        }
    }

    public record DocumentChunkRecord(
            UUID id,
            UUID userId,
            UUID documentId,
            long sourceRevision,
            int chunkIndex,
            Integer pageFrom,
            Integer pageTo,
            String content,
            String maskedContent,
            int tokenCount,
            List<Double> embedding,
            Long embeddingPolicyVersion,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            Integer embeddingGeneration,
            Map<String, Object> metadata,
            Instant createdAt) {}

    public record PageSlice<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }

    public record ParsedPage(int pageNumber, String text) {}

    public record ParsedDocument(
            List<ParsedPage> pages,
            String text,
            Integer pageCount,
            String parserName,
            String parserVersion) {
        public ParsedDocument {
            pages = List.copyOf(pages);
        }
    }

    public record ChunkDraft(
            int chunkIndex,
            Integer pageFrom,
            Integer pageTo,
            String content,
            String maskedContent,
            int tokenCount,
            Map<String, Object> metadata) {
        public ChunkDraft {
            metadata = Map.copyOf(metadata);
        }
    }

    public record EmbeddingPolicy(
            long version,
            String provider,
            String model,
            int dimension,
            String distance,
            int generation) {}

    public record SimilarChunk(
            UUID chunkId,
            UUID documentId,
            int chunkIndex,
            String maskedContent,
            double distance) {}
}
