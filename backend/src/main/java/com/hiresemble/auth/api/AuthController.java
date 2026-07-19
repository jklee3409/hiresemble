package com.hiresemble.auth.api;

import com.hiresemble.auth.application.AuthService;
import com.hiresemble.auth.application.CsrfTokenService;
import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.api.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Initialize an anonymous session and return its CSRF token")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CsrfDto.class)))
    public CsrfDto csrf(CsrfToken csrfToken, HttpServletRequest request) {
        request.getSession(true);
        return csrfTokenService.current(csrfToken);
    }

    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a user, default profile, and authenticated session")
    @ApiResponses({
        @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = AuthSessionDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AuthSessionDto> signup(
            @Valid @RequestBody SignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.signup(signupRequest, request, response));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Authenticate and rotate the session")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AuthSessionDto.class))),
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public AuthSessionDto login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        return authService.login(loginRequest, request, response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current session")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
        @ApiResponse(responseCode = "204"),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the current authenticated user")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CurrentUserDto.class))),
        @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public CurrentUserDto me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return currentUser.toDto();
    }
}
