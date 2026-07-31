package com.hiresemble.agentrun.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record DeleteAgentRunsRequest(
        @NotEmpty
        @Size(max = 100)
        @Schema(description = "One to one hundred terminal Agent Run IDs to hide from history.")
        Set<@NotNull UUID> agentRunIds) {}
