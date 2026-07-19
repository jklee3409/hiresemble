package com.hiresemble.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthSessionDto")
public record AuthSessionDto(CurrentUserDto user, CsrfDto csrf) {}
