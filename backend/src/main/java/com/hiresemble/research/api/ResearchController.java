package com.hiresemble.research.api;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.idempotency.IdempotentResponse;
import com.hiresemble.profile.api.dto.PageResponse;
import com.hiresemble.research.api.ResearchDtos.ResearchRetryAcceptedDto;
import com.hiresemble.research.api.ResearchDtos.ResearchRunDto;
import com.hiresemble.research.api.ResearchDtos.ResearchSourceDto;
import com.hiresemble.research.application.model.ResearchModels.AcceptedResearchRetry;
import com.hiresemble.research.application.service.ResearchApplicationService;
import com.hiresemble.research.application.service.ResearchRetryApplicationService;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
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
        name = "Interview research",
        description =
                "Owner-scoped classified public sources, source coverage, and immutable retry lineage.")
@SecurityRequirement(name = "sessionCookie")
public class ResearchController {

    private static final Set<String> SOURCE_PARAMETERS =
            Set.of("topic", "sourceType", "page", "size", "sort");

    private final ResearchApplicationService service;
    private final ResearchRetryApplicationService retryService;
    private final ResearchApiMapper mapper;

    public ResearchController(
            ResearchApplicationService service,
            ResearchRetryApplicationService retryService,
            ResearchApiMapper mapper) {
        this.service = service;
        this.retryService = retryService;
        this.mapper = mapper;
    }

    @GetMapping("/research-runs/{researchRunId}")
    @Operation(
            operationId = "getResearchRun",
            summary = "Get interview research",
            description =
                    "Returns one owned research run with deterministic coverage and safe retry state.")
    public ResearchRunDto get(
            @PathVariable UUID researchRunId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.run(service.get(user.id(), researchRunId));
    }

    @GetMapping("/research-runs/{researchRunId}/sources")
    @Operation(
            operationId = "listResearchSources",
            summary = "List classified sources",
            description =
                    "Lists canonicalized sources with public type, topic, dates, snippet, and reliability notice.")
    public PageResponse<ResearchSourceDto> sources(
            @PathVariable UUID researchRunId,
            @RequestParam(required = false) ResearchTopic topic,
            @RequestParam(required = false) ResearchSourceType sourceType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "providerRank,asc") String sort,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        rejectUnknownParameters(servletRequest);
        var result =
                service.sources(user.id(), researchRunId, topic, sourceType, page, size, sort);
        return new PageResponse<>(
                result.items().stream().map(mapper::source).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping(
            value = "/research-runs/{researchRunId}/retry",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "retryResearchRun",
            summary = "Retry failed research",
            description =
                    "Creates or replays exactly one successor research run and question set without mutating predecessor results.")
    @ApiResponse(
            responseCode = "202",
            content =
                    @Content(
                            schema =
                                    @Schema(implementation = ResearchRetryAcceptedDto.class)))
    public ResponseEntity<ResearchRetryAcceptedDto> retry(
            @PathVariable UUID researchRunId,
            @Valid @RequestBody com.hiresemble.interview.api.InterviewRequests.ResearchRetryRequest
                    request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        IdempotentResponse<AcceptedResearchRetry> result = retryService.retry(
                user.id(),
                researchRunId,
                request.researchQuality(),
                request.qualityMode(),
                idempotencyKey);
        AcceptedResearchRetry value = result.body();
        return ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ResearchRetryAcceptedDto(
                        value.questionSetId(),
                        value.researchRunId(),
                        value.agentRunId(),
                        value.retryOfResearchRunId(),
                        "QUEUED"));
    }

    private void rejectUnknownParameters(HttpServletRequest request) {
        if (request.getParameterMap().keySet().stream()
                .anyMatch(parameter -> !SOURCE_PARAMETERS.contains(parameter))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
