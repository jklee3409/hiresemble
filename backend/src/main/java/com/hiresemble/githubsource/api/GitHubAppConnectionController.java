package com.hiresemble.githubsource.api;

import com.hiresemble.auth.security.AuthenticatedUser;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.githubsource.api.GitHubAppConnectionDtos.CapabilityDto;
import com.hiresemble.githubsource.api.GitHubAppConnectionDtos.ConnectionDto;
import com.hiresemble.githubsource.api.GitHubAppConnectionDtos.ConnectionListDto;
import com.hiresemble.githubsource.api.GitHubAppConnectionDtos.InstallationRequestDto;
import com.hiresemble.githubsource.api.GitHubAppConnectionRequests.RefreshRequest;
import com.hiresemble.githubsource.application.GitHubAppConnectionService;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.Connection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/github-app-connections")
@ConditionalOnProperty(
        name = {"hiresemble.github.enabled", "hiresemble.github.private-enabled"},
        havingValue = "true")
@Tag(name = "GitHub App Connections", description = "Session-bound private GitHub App connections.")
@SecurityRequirement(name = "sessionCookie")
public class GitHubAppConnectionController {

    private final GitHubAppConnectionService service;

    public GitHubAppConnectionController(GitHubAppConnectionService service) {
        this.service = service;
    }

    @GetMapping("/capability")
    @Operation(operationId = "getGitHubAppCapability", summary = "Get private GitHub capability")
    public CapabilityDto capability() {
        var capability = service.capability();
        return new CapabilityDto(
                capability.enabled(), capability.configured(), capability.permissions());
    }

    @PostMapping("/installation-requests")
    @Operation(operationId = "startGitHubAppConnection", summary = "Start GitHub App installation")
    public InstallationRequestDto start(
            HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        var started = service.start(user.id(), sessionId(request));
        return new InstallationRequestDto(started.installationUrl().toString(), started.expiresAt());
    }

    @GetMapping("/setup/callback")
    @Operation(operationId = "handleGitHubAppSetupCallback", summary = "Validate GitHub setup callback")
    @ApiResponse(responseCode = "302")
    public ResponseEntity<Void> setupCallback(
            @RequestParam(required = false) String state,
            @RequestParam(name = "installation_id", required = false) Long installationId,
            @RequestParam(name = "setup_action", required = false) String setupAction,
            HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || currentSession(request) == null) {
            return redirect(service.frontendResult("expired"));
        }
        try {
            return redirect(service.setupCallback(
                    user.id(), currentSession(request).getId(), state, installationId, setupAction));
        } catch (BusinessException exception) {
            return redirect(service.frontendResult(result(exception.errorCode())));
        }
    }

    @GetMapping("/oauth/callback")
    @Operation(operationId = "handleGitHubAppOAuthCallback", summary = "Verify GitHub App installation")
    @ApiResponse(responseCode = "302")
    public ResponseEntity<Void> oauthCallback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        if (error != null) return redirect(service.frontendResult("cancelled"));
        if (user == null || currentSession(request) == null) {
            return redirect(service.frontendResult("expired"));
        }
        try {
            service.oauthCallback(user.id(), currentSession(request).getId(), state, code);
            return redirect(service.frontendResult("connected"));
        } catch (BusinessException exception) {
            return redirect(service.frontendResult(result(exception.errorCode())));
        }
    }

    @GetMapping
    @Operation(operationId = "listGitHubAppConnections", summary = "List GitHub App connections")
    public ConnectionListDto list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return new ConnectionListDto(service.list(user.id()).stream().map(this::dto).toList());
    }

    @PostMapping("/{connectionId}/refresh")
    @Operation(operationId = "refreshGitHubAppConnection", summary = "Refresh GitHub App connection")
    public ConnectionDto refresh(
            @PathVariable UUID connectionId,
            @Valid @RequestBody RefreshRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return dto(service.refresh(user.id(), connectionId, request.version()));
    }

    @DeleteMapping("/{connectionId}")
    @Operation(operationId = "disconnectGitHubAppConnection", summary = "Disconnect and uninstall GitHub App")
    @ApiResponse(responseCode = "202")
    public ResponseEntity<ConnectionDto> disconnect(
            @PathVariable UUID connectionId,
            @RequestParam @PositiveOrZero long version,
            @RequestParam boolean uninstallConfirmed,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.accepted().body(dto(service.disconnect(
                user.id(), connectionId, version, uninstallConfirmed)));
    }

    private ConnectionDto dto(Connection connection) {
        return new ConnectionDto(
                connection.id(),
                connection.targetAccountLogin(),
                connection.targetAccountType(),
                connection.repositorySelection(),
                connection.status(),
                "https://github.com/settings/installations/" + connection.installationId(),
                connection.version(),
                connection.connectedAt(),
                connection.verifiedAt(),
                connection.lastCheckedAt(),
                connection.disconnectedAt());
    }

    private String sessionId(HttpServletRequest request) {
        HttpSession session = currentSession(request);
        if (session == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return session.getId();
    }

    private HttpSession currentSession(HttpServletRequest request) {
        return request.getSession(false);
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(302).location(location).build();
    }

    private String result(ErrorCode code) {
        return switch (code) {
            case GITHUB_CONNECTION_STATE_INVALID_OR_EXPIRED -> "expired";
            case GITHUB_APP_PERMISSION_MISMATCH -> "permission";
            default -> "unavailable";
        };
    }
}
