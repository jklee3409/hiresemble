package com.hiresemble.document.api.dto;

import com.hiresemble.agentrun.api.dto.SafeErrorDto;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.document.domain.model.DocumentParseStatus;
import com.hiresemble.document.domain.model.DocumentType;
import com.hiresemble.document.domain.model.EvidenceExtractionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {}

    @Schema(name = "DocumentUploadAcceptedDto")
    public record DocumentUploadAcceptedDto(
            UUID documentId,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceExtractionStatus,
            UUID agentRunId,
            AgentRunStatus status) {}

    @Schema(name = "DocumentSummaryDto")
    public record DocumentSummaryDto(
            UUID id,
            DocumentType documentType,
            @Schema(minLength = 1, maxLength = 255) String displayName,
            @Schema(minLength = 1, maxLength = 100) String mimeType,
            @Schema(minimum = "1", maximum = "20971520") long fileSizeBytes,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceExtractionStatus,
            boolean manualTextProvided,
            @Schema(nullable = true) SafeErrorDto safeError,
            @Schema(nullable = true) UUID latestAgentRunId,
            long version,
            Instant uploadedAt,
            Instant updatedAt) {}

    @Schema(name = "DocumentPageDto")
    public record DocumentPageDto(
            List<DocumentSummaryDto> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
        public DocumentPageDto {
            items = List.copyOf(items);
        }
    }

    @Schema(name = "DocumentDetailDto")
    public record DocumentDetailDto(
            UUID id,
            DocumentType documentType,
            @Schema(minLength = 1, maxLength = 255) String displayName,
            @Schema(minLength = 1, maxLength = 100) String mimeType,
            @Schema(minimum = "1", maximum = "20971520") long fileSizeBytes,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceExtractionStatus,
            boolean manualTextProvided,
            @Schema(nullable = true) SafeErrorDto safeError,
            @Schema(nullable = true) UUID latestAgentRunId,
            long version,
            Instant uploadedAt,
            Instant updatedAt,
            @Schema(nullable = true, minimum = "1") Integer pageCount,
            @Schema(nullable = true, minimum = "0") Integer characterCount,
            @Schema(nullable = true) Instant parsedAt) {}

    @Schema(name = "DocumentTextDto")
    public record DocumentTextDto(
            UUID documentId,
            @Schema(maxLength = 500000) String text,
            @Schema(minimum = "0") int characterCount,
            boolean manualTextProvided,
            long version,
            Instant updatedAt) {}

    @Schema(name = "DownloadUrlDto")
    public record DownloadUrlDto(
            @Schema(minLength = 1, maxLength = 4096) String url,
            Instant expiresAt) {}
}
