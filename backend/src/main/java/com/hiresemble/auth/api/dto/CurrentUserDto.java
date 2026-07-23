package com.hiresemble.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "CurrentUserDto", description = "Minimal authenticated user projection")
public record CurrentUserDto(
        @Schema(example = "00000000-0000-0000-0000-000000000001") UUID id,
        @Schema(minLength = 3, maxLength = 320, example = "candidate@example.com") String email,
        @Schema(minLength = 1, maxLength = 100, example = "Sample Candidate") String displayName) {}
