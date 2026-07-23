package com.hiresemble.agentrun.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record CancelAgentRunRequest(@NotNull @PositiveOrZero Long stateVersion) {}
