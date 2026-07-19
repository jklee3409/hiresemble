package com.hiresemble.agentrun.api;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record CancelAgentRunRequest(@NotNull @PositiveOrZero Long stateVersion) {}
