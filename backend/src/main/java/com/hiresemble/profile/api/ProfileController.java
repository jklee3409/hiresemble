package com.hiresemble.profile.api;

import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.profile.api.ProfileDtos.AwardDto;
import com.hiresemble.profile.api.ProfileDtos.CareerDto;
import com.hiresemble.profile.api.ProfileDtos.CertificationDto;
import com.hiresemble.profile.api.ProfileDtos.EducationDto;
import com.hiresemble.profile.api.ProfileDtos.EvidenceDto;
import com.hiresemble.profile.api.ProfileDtos.LanguageScoreDto;
import com.hiresemble.profile.api.ProfileDtos.ProfileDto;
import com.hiresemble.profile.api.ProfileRequests.AwardCreateRequest;
import com.hiresemble.profile.api.ProfileRequests.AwardUpdateRequest;
import com.hiresemble.profile.api.ProfileRequests.CareerCreateRequest;
import com.hiresemble.profile.api.ProfileRequests.CareerUpdateRequest;
import com.hiresemble.profile.api.ProfileRequests.CertificationCreateRequest;
import com.hiresemble.profile.api.ProfileRequests.CertificationUpdateRequest;
import com.hiresemble.profile.api.ProfileRequests.EducationCreateRequest;
import com.hiresemble.profile.api.ProfileRequests.EducationUpdateRequest;
import com.hiresemble.profile.api.ProfileRequests.EvidenceUpdateRequest;
import com.hiresemble.profile.api.ProfileRequests.EvidenceVerificationRequest;
import com.hiresemble.profile.api.ProfileRequests.LanguageScoreCreateRequest;
import com.hiresemble.profile.api.ProfileRequests.LanguageScoreUpdateRequest;
import com.hiresemble.profile.api.ProfileRequests.ProfileUpdateRequest;
import com.hiresemble.profile.application.ProfileApplicationService;
import com.hiresemble.profile.domain.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.ProfileCommands.AwardWrite;
import com.hiresemble.profile.domain.ProfileCommands.CareerWrite;
import com.hiresemble.profile.domain.ProfileCommands.CertificationWrite;
import com.hiresemble.profile.domain.ProfileCommands.EducationWrite;
import com.hiresemble.profile.domain.ProfileCommands.EvidenceWrite;
import com.hiresemble.profile.domain.ProfileCommands.LanguageScoreWrite;
import com.hiresemble.profile.domain.ProfileCommands.ProfileUpdate;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value = "/api/v1/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Authenticated user profile, structured resources, and direct evidence")
@SecurityRequirement(name = "sessionCookie")
public class ProfileController {

    private final ProfileApplicationService service;

    public ProfileController(ProfileApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "getProfile", summary = "Get the current profile", description = "Returns server-computed profile completion without gating protected routes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ProfileDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ProfileDto getProfile(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.profile(service.getProfile(user.id()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateProfile", summary = "Update the current profile", description = "Uses optimistic profile version and recalculates completion on the server.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ProfileDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ProfileDto updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.profile(service.updateProfile(
                user.id(),
                new ProfileUpdate(
                        request.legalName(), request.introduction(), request.desiredRoles(),
                        request.desiredIndustries(), request.desiredLocations(),
                        request.expectedGraduationDate(), request.version())));
    }

    @GetMapping("/educations")
    @Operation(operationId = "listEducations", summary = "List educations", description = "Lists active educations owned by the authenticated user.")
    public PageResponse<EducationDto> listEducations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.page(service.listEducations(user.id(), page, size, sort), ProfileDtoMapper::education);
    }

    @PostMapping(value = "/educations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createEducation", summary = "Create an education", description = "Creates one education and its VERIFIED direct evidence in one transaction.")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = EducationDto.class)))
    public ResponseEntity<EducationDto> createEducation(
            @Valid @RequestBody EducationCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        EducationWrite command = education(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileDtoMapper.education(service.createEducation(user.id(), command)));
    }

    @PutMapping(value = "/educations/{educationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateEducation", summary = "Update an education", description = "Updates the source and regenerates its direct evidence using optimistic versioning.")
    public EducationDto updateEducation(
            @PathVariable UUID educationId,
            @Valid @RequestBody EducationUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.education(service.updateEducation(user.id(), educationId, education(request), request.version()));
    }

    @DeleteMapping("/educations/{educationId}")
    @Operation(operationId = "deleteEducation", summary = "Delete an education", description = "Soft-deletes the source and removes its unreferenced P2 direct evidence.")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> deleteEducation(
            @PathVariable UUID educationId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteEducation(user.id(), educationId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/certifications")
    @Operation(operationId = "listCertifications", summary = "List certifications", description = "Lists active certifications owned by the authenticated user.")
    public PageResponse<CertificationDto> listCertifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "acquiredDate,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.page(service.listCertifications(user.id(), page, size, sort), ProfileDtoMapper::certification);
    }

    @PostMapping(value = "/certifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createCertification", summary = "Create a certification", description = "Creates a certification and VERIFIED direct evidence in one transaction.")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = CertificationDto.class)))
    public ResponseEntity<CertificationDto> createCertification(
            @Valid @RequestBody CertificationCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileDtoMapper.certification(service.createCertification(user.id(), certification(request))));
    }

    @PutMapping(value = "/certifications/{certificationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCertification", summary = "Update a certification", description = "Updates a certification and regenerates direct evidence.")
    public CertificationDto updateCertification(
            @PathVariable UUID certificationId,
            @Valid @RequestBody CertificationUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.certification(service.updateCertification(user.id(), certificationId, certification(request), request.version()));
    }

    @DeleteMapping("/certifications/{certificationId}")
    @Operation(operationId = "deleteCertification", summary = "Delete a certification", description = "Soft-deletes the certification and removes its P2 direct evidence.")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> deleteCertification(
            @PathVariable UUID certificationId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteCertification(user.id(), certificationId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/language-scores")
    @Operation(operationId = "listLanguageScores", summary = "List language scores", description = "Lists active language scores owned by the authenticated user.")
    public PageResponse<LanguageScoreDto> listLanguageScores(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "testedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.page(service.listLanguageScores(user.id(), page, size, sort), ProfileDtoMapper::languageScore);
    }

    @PostMapping(value = "/language-scores", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createLanguageScore", summary = "Create a language score", description = "Creates a language score and VERIFIED direct evidence in one transaction.")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = LanguageScoreDto.class)))
    public ResponseEntity<LanguageScoreDto> createLanguageScore(
            @Valid @RequestBody LanguageScoreCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileDtoMapper.languageScore(service.createLanguageScore(user.id(), languageScore(request))));
    }

    @PutMapping(value = "/language-scores/{languageScoreId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateLanguageScore", summary = "Update a language score", description = "Updates a language score and regenerates direct evidence.")
    public LanguageScoreDto updateLanguageScore(
            @PathVariable UUID languageScoreId,
            @Valid @RequestBody LanguageScoreUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.languageScore(service.updateLanguageScore(user.id(), languageScoreId, languageScore(request), request.version()));
    }

    @DeleteMapping("/language-scores/{languageScoreId}")
    @Operation(operationId = "deleteLanguageScore", summary = "Delete a language score", description = "Soft-deletes the language score and removes its P2 direct evidence.")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> deleteLanguageScore(
            @PathVariable UUID languageScoreId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteLanguageScore(user.id(), languageScoreId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/awards")
    @Operation(operationId = "listAwards", summary = "List awards", description = "Lists active awards owned by the authenticated user.")
    public PageResponse<AwardDto> listAwards(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "awardedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.page(service.listAwards(user.id(), page, size, sort), ProfileDtoMapper::award);
    }

    @PostMapping(value = "/awards", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createAward", summary = "Create an award", description = "Creates an award and VERIFIED direct evidence in one transaction.")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = AwardDto.class)))
    public ResponseEntity<AwardDto> createAward(
            @Valid @RequestBody AwardCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileDtoMapper.award(service.createAward(user.id(), award(request))));
    }

    @PutMapping(value = "/awards/{awardId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateAward", summary = "Update an award", description = "Updates an award and regenerates direct evidence.")
    public AwardDto updateAward(
            @PathVariable UUID awardId,
            @Valid @RequestBody AwardUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.award(service.updateAward(user.id(), awardId, award(request), request.version()));
    }

    @DeleteMapping("/awards/{awardId}")
    @Operation(operationId = "deleteAward", summary = "Delete an award", description = "Soft-deletes the award and removes its P2 direct evidence.")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> deleteAward(
            @PathVariable UUID awardId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteAward(user.id(), awardId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/careers")
    @Operation(operationId = "listCareers", summary = "List careers", description = "Lists active careers owned by the authenticated user.")
    public PageResponse<CareerDto> listCareers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "startedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.page(service.listCareers(user.id(), page, size, sort), ProfileDtoMapper::career);
    }

    @PostMapping(value = "/careers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createCareer", summary = "Create a career", description = "Creates a career and VERIFIED direct evidence in one transaction.")
    @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = CareerDto.class)))
    public ResponseEntity<CareerDto> createCareer(
            @Valid @RequestBody CareerCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProfileDtoMapper.career(service.createCareer(user.id(), career(request))));
    }

    @PutMapping(value = "/careers/{careerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCareer", summary = "Update a career", description = "Updates a career and regenerates direct evidence.")
    public CareerDto updateCareer(
            @PathVariable UUID careerId,
            @Valid @RequestBody CareerUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.career(service.updateCareer(user.id(), careerId, career(request), request.version()));
    }

    @DeleteMapping("/careers/{careerId}")
    @Operation(operationId = "deleteCareer", summary = "Delete a career", description = "Soft-deletes the career and removes its P2 direct evidence.")
    @ApiResponse(responseCode = "204", content = @Content)
    public ResponseEntity<Void> deleteCareer(
            @PathVariable UUID careerId,
            @RequestParam @PositiveOrZero long version,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        service.deleteCareer(user.id(), careerId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/evidence")
    @Operation(operationId = "listProfileEvidence", summary = "List profile evidence", description = "Lists direct evidence with optional status and category filters. Document filtering is deferred to P4.")
    public PageResponse<EvidenceDto> listEvidence(
            @RequestParam(required = false) EvidenceVerificationStatus verificationStatus,
            @RequestParam(required = false) @Size(min = 1, max = 80) String evidenceCategory,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.page(
                service.listEvidence(user.id(), verificationStatus, evidenceCategory, documentId, page, size, sort),
                ProfileDtoMapper::evidence);
    }

    @PutMapping(value = "/evidence/{evidenceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateProfileEvidence", summary = "Edit direct evidence", description = "Edits the evidence projection; a later source update regenerates and overrides these values.")
    public EvidenceDto updateEvidence(
            @PathVariable UUID evidenceId,
            @Valid @RequestBody EvidenceUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.evidence(service.updateEvidence(
                user.id(), evidenceId, new EvidenceWrite(request.title(), request.content(), request.metadata()), request.version()));
    }

    @PatchMapping(value = "/evidence/{evidenceId}/verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "verifyProfileEvidence", summary = "Verify or reject direct evidence", description = "Transitions active evidence to VERIFIED or REJECTED with optimistic versioning.")
    public EvidenceDto verifyEvidence(
            @PathVariable UUID evidenceId,
            @Valid @RequestBody EvidenceVerificationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ProfileDtoMapper.evidence(service.verifyEvidence(user.id(), evidenceId, request.status(), request.version()));
    }

    private EducationWrite education(ProfileRequests.EducationFields request) {
        return new EducationWrite(
                request.schoolName(), request.major(), request.degree(), request.educationStatus(),
                request.admissionDate(), request.graduationDate(), request.gpa(), request.gpaScale(),
                request.isPrimary(), request.description());
    }

    private CertificationWrite certification(ProfileRequests.CertificationFields request) {
        return new CertificationWrite(
                request.name(), request.issuer(), request.credentialNumber(), request.acquiredDate(),
                request.expiresAt(), request.description(), request.evidenceDocumentId());
    }

    private LanguageScoreWrite languageScore(ProfileRequests.LanguageScoreFields request) {
        return new LanguageScoreWrite(
                request.testName(), request.score(), request.grade(), request.testedAt(),
                request.expiresAt(), request.evidenceDocumentId());
    }

    private AwardWrite award(ProfileRequests.AwardFields request) {
        return new AwardWrite(
                request.name(), request.organizer(), request.awardedAt(), request.description(), request.evidenceDocumentId());
    }

    private CareerWrite career(ProfileRequests.CareerFields request) {
        return new CareerWrite(
                request.organization(), request.position(), request.employmentType(), request.startedAt(),
                request.endedAt(), request.isCurrent(), request.responsibilities(), request.achievements());
    }
}
