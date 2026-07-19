package com.hiresemble.common.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "ErrorResponseDto", description = "The common error contract used by MVC and Security")
public record ErrorResponseDto(
        Instant timestamp,
        @Schema(minimum = "400", maximum = "599") int status,
        @Schema(minLength = 1, maxLength = 100) String code,
        @Schema(minLength = 1, maxLength = 500) String message,
        @ArraySchema(maxItems = 100, schema = @Schema(implementation = FieldErrorDto.class))
                List<FieldErrorDto> fieldErrors,
        UUID requestId) {

    public ErrorResponseDto {
        fieldErrors = List.copyOf(fieldErrors);
    }
}
