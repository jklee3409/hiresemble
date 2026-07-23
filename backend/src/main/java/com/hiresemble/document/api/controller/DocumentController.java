package com.hiresemble.document.api.controller;

import com.hiresemble.document.api.mapper.DocumentApiMapper;
import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentDetailDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentPageDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentSummaryDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentTextDto;
import com.hiresemble.document.api.dto.DocumentDtos.DocumentUploadAcceptedDto;
import com.hiresemble.document.api.dto.DocumentDtos.DownloadUrlDto;
import com.hiresemble.document.api.dto.DocumentRequests.ManualTextRequest;
import com.hiresemble.document.api.dto.DocumentRequests.ReparseRequest;
import com.hiresemble.document.application.model.DocumentApplicationResults.RunAccepted;
import com.hiresemble.document.application.model.DocumentApplicationResults.UploadAccepted;
import com.hiresemble.document.application.service.DocumentApplicationService;
import com.hiresemble.document.domain.model.DocumentParseStatus;
import com.hiresemble.document.domain.model.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.model.DocumentType;
import com.hiresemble.document.domain.model.EvidenceExtractionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Owner-scoped document parsing and evidence pipeline.")
@SecurityRequirement(name = "sessionCookie")
public class DocumentController {

    private final DocumentApplicationService service;
    private final DocumentApiMapper mapper;

    public DocumentController(DocumentApplicationService service, DocumentApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadDocument", summary = "Upload a document")
    @ApiResponses({
        @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = DocumentUploadAcceptedDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "413", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "415", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DocumentUploadAcceptedDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam DocumentType documentType,
            @RequestParam(required = false) @Size(min = 1, max = 255) String displayName,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<UploadAccepted> result = service.upload(
                user.id(), file, documentType, displayName, idempotencyKey);
        UploadAccepted value = result.body();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DocumentUploadAcceptedDto(
                value.documentId(), value.parseStatus(), value.evidenceExtractionStatus(),
                value.agentRunId(), value.status()));
    }

    @GetMapping
    @Operation(operationId = "listDocuments", summary = "List documents")
    public DocumentPageDto list(
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) DocumentParseStatus parseStatus,
            @RequestParam(required = false) EvidenceExtractionStatus evidenceExtractionStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "uploadedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var values = service.list(
                user.id(), documentType, parseStatus, evidenceExtractionStatus, page, size, sort);
        return new DocumentPageDto(
                values.items().stream().map(mapper::summary).toList(), values.page(), values.size(),
                values.totalElements(), values.totalPages());
    }

    @GetMapping("/{documentId}")
    @Operation(operationId = "getDocument", summary = "Get document detail")
    public DocumentDetailDto detail(
            @PathVariable UUID documentId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        DocumentRecord document = service.detail(user.id(), documentId);
        return mapper.detail(document, service.detailText(user.id(), documentId));
    }

    @GetMapping("/{documentId}/text")
    @Operation(operationId = "getDocumentText", summary = "Get extracted document text")
    public DocumentTextDto text(
            @PathVariable UUID documentId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        DocumentRecord document = service.detail(user.id(), documentId);
        return mapper.text(document, service.text(user.id(), documentId));
    }

    @PutMapping(value = "/{documentId}/manual-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "provideDocumentManualText", summary = "Provide manual document text")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> manualText(
            @PathVariable UUID documentId,
            @Valid @RequestBody ManualTextRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return accepted(service.manualText(
                user.id(), documentId, request.text(), request.version(), idempotencyKey));
    }

    @PostMapping(value = "/{documentId}/reparse", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "reparseDocument", summary = "Reparse a document")
    @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> reparse(
            @PathVariable UUID documentId,
            @Valid @RequestBody ReparseRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return accepted(service.reparse(
                user.id(), documentId, request.version(), idempotencyKey));
    }

    @PostMapping("/{documentId}/download-url")
    @Operation(operationId = "createDocumentDownloadUrl", summary = "Create a five-minute download URL")
    public DownloadUrlDto downloadUrl(
            @PathVariable UUID documentId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var value = service.downloadUrl(user.id(), documentId);
        return new DownloadUrlDto(value.url(), value.expiresAt());
    }

    @DeleteMapping("/{documentId}")
    @Operation(operationId = "deleteDocument", summary = "Delete a document")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> delete(
            @PathVariable UUID documentId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user.id(), documentId, version);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<RunAcceptedDto> accepted(IdempotentResponse<RunAccepted> response) {
        RunAccepted value = response.body();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RunAcceptedDto(
                value.agentRunId(), value.status(), value.resourceType(), value.resourceId(),
                response.replayed()));
    }
}
