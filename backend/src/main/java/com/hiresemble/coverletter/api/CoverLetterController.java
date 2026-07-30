package com.hiresemble.coverletter.api;

import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterAnswerVersionDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterDetailDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterQuestionDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.CoverLetterSummaryDto;
import com.hiresemble.coverletter.api.CoverLetterDtos.VerificationDto;
import com.hiresemble.coverletter.api.CoverLetterRequests.CreateCoverLetterRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.CreateQuestionRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.FinalizeCoverLetterRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.GenerateCoverLetterRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.ReorderQuestionsRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.RestoreAnswerVersionRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.SaveAnswerVersionRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.UpdateCoverLetterRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.UpdateQuestionRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.VerifyAnswerVersionRequest;
import com.hiresemble.coverletter.api.CoverLetterRequests.VersionCommandRequest;
import com.hiresemble.coverletter.application.CoverLetterApplicationService;
import com.hiresemble.coverletter.application.model.CoverLetterModels.RunAccepted;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.profile.api.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Cover Letters",
        description = "Owner-scoped cover letter questions, immutable versions, verification, and lifecycle.")
@SecurityRequirement(name = "sessionCookie")
public class CoverLetterController {

    private static final Set<String> COVER_LIST_PARAMETERS =
            Set.of("jobId", "status", "query", "page", "size", "sort");
    private static final Set<String> VERSION_LIST_PARAMETERS =
            Set.of("page", "size", "sort");
    private static final Set<String> VERIFICATION_LIST_PARAMETERS =
            Set.of("page", "size", "sort");

    private final CoverLetterApplicationService service;
    private final CoverLetterApiMapper mapper;

    public CoverLetterController(
            CoverLetterApplicationService service, CoverLetterApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(
            value = "/jobs/{jobId}/cover-letter",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createCoverLetter",
            summary = "Create the active cover letter for a job",
            description = "Idempotently creates the job's single active DRAFT cover letter.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<CoverLetterDetailDto> create(
            @PathVariable UUID jobId,
            @Valid @RequestBody CreateCoverLetterRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<com.hiresemble.coverletter.application.model.CoverLetterModels.Detail>
                result = service.create(user.id(), jobId, request.title(), idempotencyKey);
        return response(result.status(), mapper.detail(result.body()), result.replayed());
    }

    @GetMapping("/cover-letters")
    @Operation(
            operationId = "listCoverLetters",
            summary = "List cover letters",
            description = "Lists owner-scoped cover letters with allowlisted filters and sorting.")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public PageResponse<CoverLetterSummaryDto> list(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) CoverLetterStatus status,
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest, COVER_LIST_PARAMETERS);
        var result = service.list(user.id(), jobId, status, query, page, size, sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::summary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/cover-letters/{coverLetterId}")
    @Operation(
            operationId = "getCoverLetter",
            summary = "Get a cover letter",
            description = "Returns the owner-scoped aggregate, questions, current answers, and verification state.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterDetailDto detail(
            @PathVariable UUID coverLetterId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.detail(user.id(), coverLetterId));
    }

    @PutMapping(
            value = "/cover-letters/{coverLetterId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateCoverLetter",
            summary = "Update a cover letter title",
            description = "Updates the title with optimistic cover-letter version compare-and-set.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterDetailDto update(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody UpdateCoverLetterRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.updateTitle(
                user.id(), coverLetterId, request.title(), request.version()));
    }

    @PostMapping(
            value = "/cover-letters/{coverLetterId}/questions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createCoverLetterQuestion",
            summary = "Add a cover letter question",
            description = "Adds an active question using the aggregate version as compare-and-set.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = CoverLetterQuestionDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<CoverLetterQuestionDto> addQuestion(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody CreateQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201)
                .body(mapper.question(service.addQuestion(
                        user.id(),
                        coverLetterId,
                        request.questionOrder(),
                        request.questionText(),
                        request.maxLength(),
                        request.memo(),
                        request.coverLetterVersion())));
    }

    @PutMapping(
            value = "/cover-letters/{coverLetterId}/questions/{questionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateCoverLetterQuestion",
            summary = "Update a cover letter question",
            description = "Updates one question with both aggregate and question versions.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterQuestionDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterQuestionDto updateQuestion(
            @PathVariable UUID coverLetterId,
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.question(service.updateQuestion(
                user.id(),
                coverLetterId,
                questionId,
                request.questionOrder(),
                request.questionText(),
                request.maxLength(),
                request.memo(),
                request.version()));
    }

    @DeleteMapping("/cover-letters/{coverLetterId}/questions/{questionId}")
    @Operation(
            operationId = "deleteCoverLetterQuestion",
            summary = "Soft-delete a cover letter question",
            description = "Soft-deletes an active question while preserving immutable history.")
    @ApiResponses({
        @ApiResponse(responseCode = "204"),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID coverLetterId,
            @PathVariable UUID questionId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteQuestion(user.id(), coverLetterId, questionId, version);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(
            value = "/cover-letters/{coverLetterId}/questions/order",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "reorderCoverLetterQuestions",
            summary = "Replace the full active question order",
            description = "Atomically replaces the complete active question order using aggregate CAS.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterDetailDto reorder(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody ReorderQuestionsRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.reorderQuestions(
                user.id(), coverLetterId, request.questionIds(), request.version()));
    }

    @PostMapping(
            value = "/cover-letters/{coverLetterId}/generate",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "generateCoverLetter",
            summary = "Start fixed cover letter generation",
            description = "Idempotently accepts a bounded fixed-workflow generation run.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RunAcceptedDto> generate(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody GenerateCoverLetterRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<RunAccepted> result = service.acceptGeneration(
                user.id(),
                coverLetterId,
                request.questionIds(),
                request.preferredEvidenceIds(),
                request.qualityMode(),
                request.avoidExperienceDuplication(),
                request.coverLetterVersion(),
                idempotencyKey);
        return accepted(result);
    }

    @GetMapping("/cover-letter-questions/{questionId}/versions")
    @Operation(
            operationId = "listCoverLetterAnswerVersions",
            summary = "List immutable answer versions",
            description = "Lists immutable owner-scoped answer history for one question.")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public PageResponse<CoverLetterAnswerVersionDto> versions(
            @PathVariable UUID questionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "versionNo,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest, VERSION_LIST_PARAMETERS);
        var result = service.listVersions(user.id(), questionId, page, size, sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::answer).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping(
            value = "/cover-letter-questions/{questionId}/versions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "saveCoverLetterAnswerVersion",
            summary = "Explicitly save a USER_EDITED version",
            description = "Creates a server-canonical immutable version using current-answer parent CAS.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = CoverLetterAnswerVersionDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<CoverLetterAnswerVersionDto> saveVersion(
            @PathVariable UUID questionId,
            @Valid @RequestBody SaveAnswerVersionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201)
                .body(mapper.answer(service.saveUserVersion(
                        user.id(),
                        questionId,
                        request.contentJson(),
                        request.parentVersionId())));
    }

    @PostMapping(
            value = "/cover-letter-questions/{questionId}/versions/{versionId}/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "restoreCoverLetterAnswerVersion",
            summary = "Restore as a new immutable version",
            description = "Copies a historical version into a new RESTORED current version using CAS.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = CoverLetterAnswerVersionDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<CoverLetterAnswerVersionDto> restoreVersion(
            @PathVariable UUID questionId,
            @PathVariable UUID versionId,
            @Valid @RequestBody RestoreAnswerVersionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201)
                .body(mapper.answer(service.restoreVersion(
                        user.id(),
                        questionId,
                        versionId,
                        request.expectedCurrentVersionId())));
    }

    @PostMapping(
            value = "/cover-letter-answer-versions/{versionId}/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "verifyCoverLetterAnswerVersion",
            summary = "Start fixed answer verification",
            description = "Idempotently accepts verification of one immutable answer version.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = RunAcceptedDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RunAcceptedDto> verify(
            @PathVariable UUID versionId,
            @Valid @RequestBody VerifyAnswerVersionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return accepted(service.acceptVerification(
                user.id(), versionId, request.qualityMode(), idempotencyKey));
    }

    @GetMapping("/cover-letter-answer-versions/{versionId}/verifications")
    @Operation(
            operationId = "listCoverLetterVerifications",
            summary = "List immutable verification records",
            description = "Lists immutable verification history and current evidence state.")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public PageResponse<VerificationDto> verifications(
            @PathVariable UUID versionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest, VERIFICATION_LIST_PARAMETERS);
        var result = service.listVerifications(user.id(), versionId, page, size, sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::verification).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping(
            value = "/cover-letters/{coverLetterId}/finalize",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "finalizeCoverLetter",
            summary = "Finalize with fresh verification acknowledgement",
            description = "Finalizes using aggregate CAS, fresh verifications, and exact WARNING acknowledgements.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterDetailDto finalizeCoverLetter(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody FinalizeCoverLetterRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.finalizeCover(
                user.id(),
                coverLetterId,
                request.version(),
                request.acknowledgedWarningVerificationIds()));
    }

    @PostMapping(
            value = "/cover-letters/{coverLetterId}/archive",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "archiveCoverLetter",
            summary = "Archive a cover letter",
            description = "Archives the active aggregate with optimistic version compare-and-set.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterDetailDto archive(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody VersionCommandRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(
                service.archive(user.id(), coverLetterId, request.version()));
    }

    @PostMapping(
            value = "/cover-letters/{coverLetterId}/unarchive",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "unarchiveCoverLetter",
            summary = "Unarchive when no active cover letter exists",
            description = "Restores an archived aggregate to DRAFT when its job has no active cover letter.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CoverLetterDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CoverLetterDetailDto unarchive(
            @PathVariable UUID coverLetterId,
            @Valid @RequestBody VersionCommandRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(
                service.unarchive(user.id(), coverLetterId, request.version()));
    }

    private ResponseEntity<RunAcceptedDto> accepted(
            IdempotentResponse<RunAccepted> result) {
        RunAccepted value = result.body();
        return response(
                result.status(),
                new RunAcceptedDto(
                        value.agentRunId(),
                        value.status(),
                        value.resourceType(),
                        value.resourceId(),
                        result.replayed()),
                result.replayed());
    }

    private <T> ResponseEntity<T> response(int status, T body, boolean replayed) {
        return ResponseEntity.status(status)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private void rejectUnknownParameters(
            HttpServletRequest request, Set<String> allowed) {
        boolean unknown = request.getParameterMap().keySet().stream()
                .anyMatch(parameter -> !allowed.contains(parameter));
        if (unknown) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
