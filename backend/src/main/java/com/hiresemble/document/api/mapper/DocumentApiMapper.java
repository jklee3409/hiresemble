package com.hiresemble.document.api.mapper;

import com.hiresemble.agentrun.api.dto.SafeErrorDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentDetailDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentSummaryDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentTextDto;
import com.hiresemble.document.domain.model.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.model.DocumentRecords.DocumentTextRecord;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class DocumentApiMapper {

    public DocumentSummaryDto summary(DocumentRecord document) {
        return new DocumentSummaryDto(
                document.id(), document.documentType(), document.displayName(), document.mimeType(),
                document.fileSizeBytes(), document.parseStatus(), document.evidenceExtractionStatus(),
                document.manualTextProvided(), error(document), document.latestAgentRunId(),
                document.version(), document.uploadedAt(), document.updatedAt());
    }

    public DocumentDetailDto detail(
            DocumentRecord document, Optional<DocumentTextRecord> text) {
        return new DocumentDetailDto(
                document.id(), document.documentType(), document.displayName(), document.mimeType(),
                document.fileSizeBytes(), document.parseStatus(), document.evidenceExtractionStatus(),
                document.manualTextProvided(), error(document), document.latestAgentRunId(),
                document.version(), document.uploadedAt(), document.updatedAt(),
                text.map(DocumentTextRecord::pageCount).orElse(null),
                text.map(DocumentTextRecord::characterCount).orElse(null),
                text.map(DocumentTextRecord::parsedAt).orElse(null));
    }

    public DocumentTextDto text(DocumentRecord document, DocumentTextRecord text) {
        return new DocumentTextDto(
                document.id(), text.extractedText(), text.characterCount(),
                document.manualTextProvided(), text.version(), text.updatedAt());
    }

    private SafeErrorDto error(DocumentRecord document) {
        if (document.parseErrorCode() != null) {
            return new SafeErrorDto(
                    document.parseErrorCode(), "문서 텍스트를 추출하지 못했습니다.");
        }
        if (document.evidenceErrorCode() != null) {
            return new SafeErrorDto(
                    document.evidenceErrorCode(), "문서 근거 추출을 완료하지 못했습니다.");
        }
        return null;
    }
}
