package com.hiresemble.githubsource.api;

import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubAppConnectionStatus;
import com.hiresemble.githubsource.domain.GitHubRepositorySelectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GitHubAppConnectionDtos {

    private GitHubAppConnectionDtos() {}

    @Schema(name = "GitHubAppCapabilityDto")
    public record CapabilityDto(
            boolean enabled, boolean configured, List<String> requiredPermissions) {
        public CapabilityDto {
            requiredPermissions = List.copyOf(requiredPermissions);
        }
    }

    @Schema(name = "GitHubInstallationRequestDto")
    public record InstallationRequestDto(String installationUrl, Instant expiresAt) {}

    @Schema(name = "GitHubAppConnectionDto")
    public record ConnectionDto(
            UUID id,
            String targetAccountLogin,
            GitHubAccountType targetAccountType,
            GitHubRepositorySelectionKind repositorySelection,
            GitHubAppConnectionStatus status,
            String manageUrl,
            long version,
            Instant connectedAt,
            Instant verifiedAt,
            Instant lastCheckedAt,
            Instant disconnectedAt) {}

    @Schema(name = "GitHubAppConnectionListDto")
    public record ConnectionListDto(List<ConnectionDto> items) {
        public ConnectionListDto {
            items = List.copyOf(items);
        }
    }
}
