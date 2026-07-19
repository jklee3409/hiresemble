package com.hiresemble.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FieldErrorDto", description = "A validation error without the rejected value")
public record FieldErrorDto(
        @Schema(minLength = 1, maxLength = 200) String field,
        @Schema(minLength = 1, maxLength = 100) String reason) {}
