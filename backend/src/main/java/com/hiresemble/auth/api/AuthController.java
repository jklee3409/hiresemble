package com.hiresemble.auth.api;

import com.hiresemble.auth.application.AuthService;
import com.hiresemble.auth.application.CsrfTokenService;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final CsrfTokenService csrfTokenService;

    public AuthController(AuthService authService, CsrfTokenService csrfTokenService) {
        this.authService = authService;
        this.csrfTokenService = csrfTokenService;
    }

    @GetMapping("/csrf")
    @Operation(
            operationId = "initializeCsrf",
            summary = "Initialize an anonymous Session and CSRF token",
            description = """
                    Call this anonymous endpoint first from the same-origin Swagger UI. The browser stores
                    the SESSION cookie automatically. Copy the response token into Authorize > csrfToken.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Anonymous Session initialized",
                content = @Content(schema = @Schema(implementation = CsrfDto.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected server error",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CsrfDto csrf(
            @Parameter(hidden = true) CsrfToken csrfToken,
            @Parameter(hidden = true) HttpServletRequest request) {
        request.getSession(true);
        return csrfTokenService.current(csrfToken);
    }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "signup",
            summary = "Create a user and authenticated Session",
            description = """
                    Requires the csrfToken returned by the CSRF bootstrap. Success rotates the browser-managed
                    SESSION cookie and returns a new csrf.token; replace the Authorize value before the next mutation.
                    """)
    @SecurityRequirement(name = "csrfToken")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "User, default profile, and authenticated Session created",
                content = @Content(schema = @Schema(implementation = AuthSessionDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Request validation or JSON parsing failed",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Normalized email is already registered",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AuthSessionDto> signup(
            @Valid @RequestBody SignupRequest signupRequest,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signup(signupRequest, request, response));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "login",
            summary = "Authenticate and rotate the Session",
            description = """
                    Requires the csrfToken returned by the CSRF bootstrap. Success rotates the browser-managed
                    SESSION cookie and returns a new csrf.token; replace the Authorize value before the next mutation.
                    """)
    @SecurityRequirement(name = "csrfToken")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Authenticated Session established",
                content = @Content(schema = @Schema(implementation = AuthSessionDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Request validation or JSON parsing failed",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Email or password is invalid",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AuthSessionDto login(
            @Valid @RequestBody LoginRequest loginRequest,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response) {
        return authService.login(loginRequest, request, response);
    }

    @PostMapping("/logout")
    @Operation(
            operationId = "logout",
            summary = "Invalidate the current Session",
            description = """
                    Requires both the browser-managed authenticated SESSION cookie and the current csrfToken.
                    After success, call the CSRF bootstrap again before another mutation.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Current Session invalidated"),
        @ApiResponse(
                responseCode = "401",
                description = "Authenticated Session is required",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "CSRF token is missing or invalid",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> logout(@Parameter(hidden = true) HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(
            operationId = "getCurrentUser",
            summary = "Return the current authenticated user",
            description = "The same-origin browser sends the authenticated SESSION cookie automatically.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Current user projection",
                content = @Content(schema = @Schema(implementation = CurrentUserDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Authenticated Session is required",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CurrentUserDto me(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return currentUser.toDto();
    }
}
