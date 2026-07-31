package com.hiresemble.auth.api.controller;

import com.hiresemble.auth.api.dto.CurrentUserDto;
import com.hiresemble.auth.api.dto.DisplayNameUpdateRequest;
import com.hiresemble.auth.application.service.AuthService;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/account", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication")
public class AccountController {

    private final AuthService authService;

    public AccountController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping(value = "/display-name", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "updateDisplayName",
            summary = "Update the current user's display name",
            description =
                    "Requires the authenticated SESSION cookie and current CSRF token. Returns the refreshed user projection.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Updated current user projection",
                content = @Content(schema = @Schema(implementation = CurrentUserDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Request validation or JSON parsing failed",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Authenticated Session is required",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CurrentUserDto updateDisplayName(
            @Valid @RequestBody DisplayNameUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return authService.updateDisplayName(currentUser.id(), request.displayName());
    }
}
