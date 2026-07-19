package com.hiresemble.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "CurrentUserDto")
public record CurrentUserDto(
        UUID id,
        @Schema(minLength = 3, maxLength = 320) String email,
        @Schema(minLength = 1, maxLength = 100) String displayName) {}
