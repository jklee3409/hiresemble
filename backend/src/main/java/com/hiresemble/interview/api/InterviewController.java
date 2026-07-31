package com.hiresemble.interview.api;

import com.hiresemble.agentrun.api.dto.RunAcceptedDto;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.interview.api.InterviewDtos.InterviewAnswerVersionDto;
import com.hiresemble.interview.api.InterviewDtos.InterviewFeedbackDto;
import com.hiresemble.interview.api.InterviewDtos.InterviewPreparationAcceptedDto;
import com.hiresemble.interview.api.InterviewDtos.InterviewQuestionDto;
import com.hiresemble.interview.api.InterviewDtos.QuestionSetDetailDto;
import com.hiresemble.interview.api.InterviewDtos.QuestionSetSummaryDto;
import com.hiresemble.interview.api.InterviewRequests.CreateInterviewAnswerVersionRequest;
import com.hiresemble.interview.api.InterviewRequests.CreateInterviewPreparationRequest;
import com.hiresemble.interview.api.InterviewRequests.InterviewAnswerFeedbackRequest;
import com.hiresemble.interview.application.model.InterviewModels.AcceptedFeedback;
import com.hiresemble.interview.application.model.InterviewModels.AcceptedPreparation;
import com.hiresemble.interview.application.service.InterviewApplicationService;
import com.hiresemble.profile.api.dto.PageResponse;
import com.hiresemble.research.domain.ResearchRunStatus;
import com.hiresemble.research.domain.SourceCoverage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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

@Validated
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Interview preparation",
        description =
                "Owner-scoped expected questions, immutable answers, and successful feedback history.")
@SecurityRequirement(name = "sessionCookie")
public class InterviewController {

    private static final Set<String> QUESTION_SET_PARAMETERS = Set.of(
            "jobId",
            "coverLetterId",
            "query",
            "sourceCoverage",
            "researchStatus",
            "page",
            "size",
            "sort");
    private static final Set<String> PAGE_PARAMETERS = Set.of("page", "size", "sort");

    private final InterviewApplicationService service;
    private final InterviewApiMapper mapper;

    public InterviewController(
            InterviewApplicationService service, InterviewApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(
            value = "/jobs/{jobId}/interview-preparations",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createInterviewPreparation",
            summary = "Accept interview research and question generation",
            description =
                    "Validates the owned job, latest analysis, and active cover-letter answers before queuing one question-set lineage.")
    @ApiResponse(
            responseCode = "202",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    InterviewPreparationAcceptedDto.class)))
    public ResponseEntity<InterviewPreparationAcceptedDto> prepare(
            @PathVariable UUID jobId,
            @Valid @RequestBody CreateInterviewPreparationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<AcceptedPreparation> result = service.prepare(
                user.id(),
                jobId,
                request.coverLetterId(),
                request.researchQuality(),
                request.qualityMode(),
                request.questionTypes(),
                request.questionCount(),
                idempotencyKey);
        AcceptedPreparation value = result.body();
        return response(
                result.status(),
                new InterviewPreparationAcceptedDto(
                        value.questionSetId(),
                        value.researchRunId(),
                        value.agentRunId(),
                        "QUEUED"),
                result.replayed());
    }

    @GetMapping("/interview-question-sets")
    @Operation(
            operationId = "listInterviewQuestionSets",
            summary = "List question sets",
            description =
                    "Lists owner-visible question sets with allowlisted filters and sorting.")
    public PageResponse<QuestionSetSummaryDto> listQuestionSets(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) UUID coverLetterId,
            @RequestParam(required = false) @Size(max = 200) String query,
            @RequestParam(required = false) SourceCoverage sourceCoverage,
            @RequestParam(required = false) ResearchRunStatus researchStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest, QUESTION_SET_PARAMETERS);
        var result = service.listQuestionSets(
                user.id(),
                jobId,
                coverLetterId,
                query,
                sourceCoverage,
                researchStatus,
                page,
                size,
                sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::summary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/interview-question-sets/{questionSetId}")
    @Operation(
            operationId = "getInterviewQuestionSet",
            summary = "Get a question set",
            description =
                    "Returns one owned question set with research coverage, questions, provenance, and current answers.")
    public QuestionSetDetailDto questionSet(
            @PathVariable UUID questionSetId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.detail(service.questionSet(user.id(), questionSetId));
    }

    @GetMapping("/interview-questions/{questionId}")
    @Operation(
            operationId = "getInterviewQuestion",
            summary = "Get an interview question",
            description =
                    "Returns one owned expected question with evidence, source, answer, and latest feedback references.")
    public InterviewQuestionDto question(
            @PathVariable UUID questionId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.question(service.question(user.id(), questionId));
    }

    @GetMapping("/interview-questions/{questionId}/answer-versions")
    @Operation(
            operationId = "listInterviewAnswerVersions",
            summary = "List immutable interview answer versions",
            description =
                    "Lists immutable answer versions for one owned interview question.")
    public PageResponse<InterviewAnswerVersionDto> answerVersions(
            @PathVariable UUID questionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "versionNo,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest, PAGE_PARAMETERS);
        var result = service.answerVersions(user.id(), questionId, page, size, sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::answer).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping(
            value = "/interview-questions/{questionId}/answer-versions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createInterviewAnswerVersion",
            summary = "Save an immutable interview answer version",
            description =
                    "Appends a USER_EDITED answer version when parentVersionId matches the current answer.")
    @ApiResponse(
            responseCode = "201",
            content =
                    @Content(
                            schema = @Schema(implementation = InterviewAnswerVersionDto.class)))
    public ResponseEntity<InterviewAnswerVersionDto> saveAnswer(
            @PathVariable UUID questionId,
            @Valid @RequestBody CreateInterviewAnswerVersionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(mapper.answer(service.saveAnswer(
                        user.id(), questionId, request.content(), request.parentVersionId())));
    }

    @PostMapping(
            value = "/interview-answer-versions/{versionId}/feedback",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createInterviewAnswerFeedback",
            summary = "Accept feedback for one immutable answer version",
            description =
                    "Queues feedback against the exact owned immutable answer version.")
    @ApiResponse(
            responseCode = "202",
            content = @Content(schema = @Schema(implementation = RunAcceptedDto.class)))
    public ResponseEntity<RunAcceptedDto> feedback(
            @PathVariable UUID versionId,
            @Valid @RequestBody InterviewAnswerFeedbackRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<AcceptedFeedback> result = service.requestFeedback(
                user.id(), versionId, request.qualityMode(), idempotencyKey);
        AcceptedFeedback value = result.body();
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

    @GetMapping("/interview-answer-versions/{versionId}/feedbacks")
    @Operation(
            operationId = "listInterviewAnswerFeedbacks",
            summary = "List successful feedback history",
            description =
                    "Lists only feedback rows committed by successful workflows for the selected answer version.")
    public PageResponse<InterviewFeedbackDto> feedbacks(
            @PathVariable UUID versionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest, PAGE_PARAMETERS);
        var result = service.feedbacks(user.id(), versionId, page, size, sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::feedback).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    private <T> ResponseEntity<T> response(int status, T body, boolean replayed) {
        return ResponseEntity.status(status)
                .header("Idempotency-Replayed", Boolean.toString(replayed))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private void rejectUnknownParameters(HttpServletRequest request, Set<String> allowed) {
        if (request.getParameterMap().keySet().stream()
                .anyMatch(parameter -> !allowed.contains(parameter))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
