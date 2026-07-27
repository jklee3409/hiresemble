package com.hiresemble.job.api;

import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.job.api.JobDtos.JobCreationAcceptedDto;
import com.hiresemble.job.api.JobDtos.JobDetailDto;
import com.hiresemble.job.api.JobDtos.JobPageDto;
import com.hiresemble.job.api.JobRequests.ChangeJobStatusRequest;
import com.hiresemble.job.api.JobRequests.CreateJobRequest;
import com.hiresemble.job.api.JobRequests.RetryJobExtractionRequest;
import com.hiresemble.job.api.JobRequests.UpdateJobRequest;
import com.hiresemble.job.application.JobApplicationService;
import com.hiresemble.job.application.model.JobApplicationResults.JobCreationAccepted;
import com.hiresemble.job.application.model.JobApplicationResults.RunAccepted;
import com.hiresemble.job.domain.JobCommands.JobListQuery;
import com.hiresemble.job.domain.JobCommands.UpdateJob;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobStatus;
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
import java.time.Instant;
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
@RequestMapping(value = "/api/v1/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Jobs", description = "Owner-scoped job posting registration, extraction, and status.")
@SecurityRequirement(name = "sessionCookie")
public class JobController {

    private static final Set<String> LIST_PARAMETERS = Set.of(
            "status",
            "extractionStatus",
            "query",
            "deadlineFrom",
            "deadlineTo",
            "deadlineWithinDays",
            "page",
            "size",
            "sort");
    private final JobApplicationService service;
    private final JobApiMapper mapper;

    public JobController(JobApplicationService service, JobApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createJob",
            summary = "Register a job posting",
            description = "Returns 201 for usable manual text or 202 with a JOB_POSTING_EXTRACTION run.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = JobCreationAcceptedDto.class))),
        @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = JobCreationAcceptedDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<JobCreationAcceptedDto> create(
            @Valid @RequestBody CreateJobRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<JobCreationAccepted> result = service.create(
                user.id(),
                request.sourceUrl(),
                request.companyName(),
                request.positionName(),
                request.descriptionText(),
                request.deadlineAt(),
                idempotencyKey);
        JobCreationAccepted value = result.body();
        return response(
                result.status(),
                new JobCreationAcceptedDto(
                        value.jobId(),
                        value.status(),
                        value.extractionStatus(),
                        value.agentRunId()),
                result.replayed());
    }

    @GetMapping
    @Operation(
            operationId = "listJobs",
            summary = "List job postings",
            description = "Filters active owner-scoped jobs by business status, extraction state, search, and deadline.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = JobPageDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public JobPageDto list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobExtractionStatus extractionStatus,
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) Instant deadlineFrom,
            @RequestParam(required = false) Instant deadlineTo,
            @RequestParam(required = false) @Min(1) @Max(30) Integer deadlineWithinDays,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest);
        var result = service.list(
                user.id(),
                new JobListQuery(
                        status,
                        extractionStatus,
                        query,
                        deadlineFrom,
                        deadlineTo,
                        deadlineWithinDays,
                        page,
                        size,
                        sort));
        return new JobPageDto(
                result.items().stream().map(mapper::summary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/{jobId}")
    @Operation(
            operationId = "getJob",
            summary = "Get a job posting",
            description = "Returns one active job owned by the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = JobDetailDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public JobDetailDto detail(
            @PathVariable UUID jobId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.detail(user.id(), jobId));
    }

    @PutMapping(value = "/{jobId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateJob",
            summary = "Update a job posting",
            description = "Applies user overrides with optimistic locking and resumes a waiting extraction run when text is supplied.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = JobDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public JobDetailDto update(
            @PathVariable UUID jobId,
            @Valid @RequestBody UpdateJobRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.update(
                user.id(),
                jobId,
                new UpdateJob(
                        request.companyName(),
                        request.title(),
                        request.positionName(),
                        request.descriptionText(),
                        request.deadlineAt(),
                        request.version())));
    }

    @PatchMapping(value = "/{jobId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "changeJobStatus",
            summary = "Change job status",
            description = "Applies an allowed business-state transition and appends history in one transaction.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = JobDetailDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public JobDetailDto changeStatus(
            @PathVariable UUID jobId,
            @Valid @RequestBody ChangeJobStatusRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(
                service.changeStatus(user.id(), jobId, request.status(), request.version()));
    }

    @PostMapping(
            value = "/{jobId}/retry-extraction",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "retryJobExtraction",
            summary = "Retry job extraction",
            description = "Resumes WAITING_USER in place or creates one lineage successor for a terminal retry.")
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
    public ResponseEntity<RunAcceptedDto> retryExtraction(
            @PathVariable UUID jobId,
            @Valid @RequestBody RetryJobExtractionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<RunAccepted> result = service.retryExtraction(
                user.id(), jobId, request.version(), idempotencyKey);
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

    @DeleteMapping("/{jobId}")
    @Operation(
            operationId = "deleteJob",
            summary = "Delete a job posting",
            description = "Soft-deletes the owner-scoped job and cooperatively cancels an active extraction.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", content = @Content),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> delete(
            @PathVariable UUID jobId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user.id(), jobId, version);
        return ResponseEntity.noContent().build();
    }

    private void rejectUnknownParameters(HttpServletRequest request) {
        if (!LIST_PARAMETERS.containsAll(request.getParameterMap().keySet())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private <T> ResponseEntity<T> response(int status, T body, boolean replayed) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (replayed) {
            builder.header("Idempotency-Replayed", "true");
        }
        return builder.body(body);
    }
}
