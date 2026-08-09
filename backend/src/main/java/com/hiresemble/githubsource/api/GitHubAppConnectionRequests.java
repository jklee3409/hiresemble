package com.hiresemble.githubsource.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

public final class GitHubAppConnectionRequests {

    private GitHubAppConnectionRequests() {}

    @Schema(name = "GitHubAppConnectionRefreshRequest")
    public record RefreshRequest(@PositiveOrZero long version) {}
}
