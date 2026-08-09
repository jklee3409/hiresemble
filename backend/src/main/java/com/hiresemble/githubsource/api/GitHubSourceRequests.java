package com.hiresemble.githubsource.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import com.hiresemble.githubsource.domain.GitHubAccessMode;

public final class GitHubSourceRequests {

    private GitHubSourceRequests() {}

    @Schema(name = "CreateGitHubSourceRequest")
    public record CreateGitHubSourceRequest(
            @NotBlank @Size(max = 500) String url,
            @AssertTrue boolean participationConfirmed,
            GitHubAccessMode accessMode,
            UUID connectionId) {}

    @Schema(name = "GitHubRepositorySelectionRequest")
    public record RepositorySelectionRequest(
            @NotEmpty @Size(max = 10) List<@NotNull UUID> repositoryIds,
            @PositiveOrZero long version) {
        public RepositorySelectionRequest {
            repositoryIds = repositoryIds == null ? null : List.copyOf(repositoryIds);
        }
    }

    @Schema(name = "GitHubRefreshRequest")
    public record RefreshRequest(@PositiveOrZero long version) {}
}
