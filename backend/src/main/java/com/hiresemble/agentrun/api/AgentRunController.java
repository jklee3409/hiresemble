package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.application.AgentRunApplicationService;
import com.hiresemble.agentrun.application.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.AgentRunRetryPort;
import com.hiresemble.agentrun.application.WorkflowLaunchResult;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.api.ErrorResponseFactory;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/agent-runs")
@Validated
@Tag(name = "Agent Runs", description = "Durable Agent Run status, retry, cancellation, and progress events.")
@SecurityRequirement(name = "sessionCookie")
public class AgentRunController {

    private final AgentRunApplicationService applicationService;
    private final AgentRunRetryPort retryPort;
    private final AgentRunCancellationPort cancellationPort;
    private final AgentRunSseService sseService;
    private final AgentRunApiMapper mapper;
    private final ErrorResponseFactory errorResponseFactory;
    private final Clock clock;

    public AgentRunController(
            AgentRunApplicationService applicationService,
            AgentRunRetryPort retryPort,
            AgentRunCancellationPort cancellationPort,
            AgentRunSseService sseService,
            AgentRunApiMapper mapper,
            ErrorResponseFactory errorResponseFactory,
            Clock clock) {
        this.applicationService = applicationService;
        this.retryPort = retryPort;
        this.cancellationPort = cancellationPort;
        this.sseService = sseService;
        this.mapper = mapper;
        this.errorResponseFactory = errorResponseFactory;
        this.clock = clock;
    }

    @GetMapping
    @Operation(
            operationId = "listAgentRuns",
            summary = "List Agent Runs",
            description = "Lists only the authenticated user's durable Agent Runs using allowlisted filters and sort values.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent Run page"),
        @ApiResponse(responseCode = "400", description = "Invalid filter", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Resource filter is not visible", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AgentRunPageDto list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) List<WorkflowType> workflowType,
            @RequestParam(required = false) List<AgentRunStatus> status,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) Boolean retryable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "queuedAt,desc") String sort) {
        return mapper.page(applicationService.list(
                user.id(), workflowType, status, resourceType, resourceId,
                retryable, page, size, sort));
    }

    @GetMapping("/{agentRunId}")
    @Operation(
            operationId = "getAgentRun",
            summary = "Get an Agent Run",
            description = "Returns a safe owner-scoped Agent Run snapshot and step timeline.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent Run detail"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Agent Run not found", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AgentRunDetailDto detail(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID agentRunId) {
        return mapper.detail(applicationService.detail(user.id(), agentRunId));
    }

    @GetMapping(
            value = "/{agentRunId}/events",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            operationId = "streamAgentRunEvents",
            summary = "Stream Agent Run events",
            description = "Starts with a fresh database snapshot; Last-Event-ID durable replay is not provided.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "snapshot-first SSE stream"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Agent Run not found", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<?> events(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID agentRunId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sseService.open(user.id(), agentRunId));
        } catch (BusinessException exception) {
            if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponseFactory.create(ErrorCode.RESOURCE_NOT_FOUND, request));
            }
            throw exception;
        }
    }

    @PostMapping("/{agentRunId}/retry")
    @Operation(
            operationId = "retryAgentRun",
            summary = "Retry an Agent Run",
            description = "Creates or replays the single compatible successor for a retryable FAILED or INTERRUPTED run.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Retry accepted"),
        @ApiResponse(responseCode = "400", description = "Invalid Idempotency-Key", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", description = "CSRF invalid", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Agent Run not found", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", description = "Retry conflict", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "429", description = "Budget exceeded", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RunAcceptedDto> retry(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID agentRunId,
            @RequestHeader("Idempotency-Key")
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String idempotencyKey) {
        WorkflowLaunchResult result = retryPort.retry(user.id(), agentRunId, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.accepted();
        if (result.replayed()) {
            response.header("Idempotency-Replayed", "true");
        }
        return response.body(mapper.accepted(result));
    }

    @PostMapping("/{agentRunId}/cancel")
    @Operation(
            operationId = "cancelAgentRun",
            summary = "Cancel an Agent Run",
            description = "Records a cooperative cancellation request using stateVersion compare-and-set.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Cancellation requested"),
        @ApiResponse(responseCode = "400", description = "Invalid stateVersion", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", description = "CSRF invalid", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Agent Run not found", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", description = "State or version conflict", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AgentRunDetailDto> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID agentRunId,
            @Valid @RequestBody CancelAgentRunRequest request) {
        return ResponseEntity.accepted().body(mapper.detail(cancellationPort.requestCancellation(
                user.id(), agentRunId, request.stateVersion(), clock.instant())));
    }
}
