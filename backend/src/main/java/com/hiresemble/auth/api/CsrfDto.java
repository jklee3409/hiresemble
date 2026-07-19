package com.hiresemble.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CsrfDto", description = "CSRF transport metadata and the current opaque token")
public record CsrfDto(
        @Schema(minLength = 1, maxLength = 100, example = "X-CSRF-TOKEN") String headerName,
        @Schema(minLength = 1, maxLength = 100, example = "_csrf") String parameterName,
        @Schema(
                        description = "Opaque token; signup and login return a replacement token",
                        minLength = 1,
                        example = "00000000-0000-0000-0000-000000000000")
                String token) {}
