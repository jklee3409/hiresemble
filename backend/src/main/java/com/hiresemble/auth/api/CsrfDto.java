package com.hiresemble.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CsrfDto")
public record CsrfDto(
        @Schema(minLength = 1, maxLength = 100) String headerName,
        @Schema(minLength = 1, maxLength = 100) String parameterName,
        @Schema(minLength = 1) String token) {}
