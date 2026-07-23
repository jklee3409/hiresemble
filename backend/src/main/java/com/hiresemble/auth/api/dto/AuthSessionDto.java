package com.hiresemble.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "AuthSessionDto",
        description = "Authenticated user plus the new CSRF token issued after Session rotation")
public record AuthSessionDto(
        @Schema(description = "Authenticated user projection") CurrentUserDto user,
        @Schema(description = "Replace the client CSRF token with this value") CsrfDto csrf) {}
