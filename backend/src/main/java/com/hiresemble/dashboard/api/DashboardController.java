package com.hiresemble.dashboard.api;

import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.dashboard.api.DashboardDtos.CareerGuidePostDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardAgentRunsDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardDeadlineDayDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardDeadlineJobDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardDocumentsDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardEducationDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardJobsDto;
import com.hiresemble.dashboard.api.DashboardDtos.DashboardProfileDto;
import com.hiresemble.dashboard.application.DashboardModels.CareerGuidePost;
import com.hiresemble.dashboard.application.DashboardModels.DashboardView;
import com.hiresemble.dashboard.application.DashboardModels.EducationSnapshot;
import com.hiresemble.dashboard.application.DashboardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Dashboard", description = "Owner-scoped support preparation dashboard and published career guides.")
@SecurityRequirement(name = "sessionCookie")
public class DashboardController {

    private final DashboardQueryService service;

    public DashboardController(DashboardQueryService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(
            operationId = "getDashboard",
            summary = "Get the support preparation dashboard",
            description = "Returns exact owner-scoped summary counts and active deadlines for one Asia/Seoul calendar month.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DashboardDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public DashboardDto dashboard(
            @RequestParam
                    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$")
                    String month,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return dashboard(service.dashboard(user.id(), YearMonth.parse(month)));
    }

    @GetMapping("/career-guides")
    @Operation(
            operationId = "listCareerGuides",
            summary = "List published career guides",
            description = "Returns only currently published guide posts in configured display order.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = CareerGuidePostDto.class)))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public List<CareerGuidePostDto> guides(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.careerGuides().stream().map(this::guide).toList();
    }

    private DashboardDto dashboard(DashboardView view) {
        EducationSnapshot education = view.profile().primaryEducation();
        DashboardEducationDto educationDto = education == null
                ? null
                : new DashboardEducationDto(
                        education.schoolName(),
                        education.major(),
                        education.degree(),
                        education.educationLevel(),
                        education.educationStatus());
        return new DashboardDto(
                view.generatedAt(),
                view.month().toString(),
                new DashboardProfileDto(
                        view.profile().displayName(),
                        view.profile().legalName(),
                        view.profile().desiredRoles(),
                        view.profile().desiredLocations(),
                        view.profile().completed(),
                        view.profile().completionPercent(),
                        view.profile().missingItems(),
                        educationDto),
                new DashboardDocumentsDto(
                        view.documents().registeredCount(),
                        view.documents().processingCount(),
                        view.documents().needsActionCount()),
                new DashboardJobsDto(
                        view.jobs().registeredCount(),
                        view.jobs().preparingCount(),
                        view.jobs().submittedCount()),
                new DashboardAgentRunsDto(view.agentRuns().activeCount()),
                view.deadlineDays().stream()
                        .map(day -> new DashboardDeadlineDayDto(
                                day.date(),
                                day.items().size(),
                                day.items().stream()
                                        .map(job -> new DashboardDeadlineJobDto(
                                                job.id(),
                                                job.companyName(),
                                                job.title(),
                                                job.positionName(),
                                                job.status(),
                                                job.deadlineAt()))
                                        .toList()))
                        .toList());
    }

    private CareerGuidePostDto guide(CareerGuidePost post) {
        return new CareerGuidePostDto(
                post.id(),
                post.status(),
                post.displayOrder(),
                post.category(),
                post.title(),
                post.summary(),
                post.body(),
                post.publishedAt(),
                post.version());
    }
}
