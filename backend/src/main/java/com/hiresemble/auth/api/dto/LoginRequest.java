package com.hiresemble.auth.api.dto;

import com.hiresemble.common.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "P1 login input. All example values are non-production fixtures.")
public record LoginRequest(
        @Schema(format = "email", example = "candidate@example.com")
                @NotBlank
                @Email
                @Size(min = 3, max = 320)
                String email,
        @Schema(
                        description = "1 to 72 UTF-8 bytes; character count can differ from byte count",
                        format = "password",
                        example = "ExampleOnly-123",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotNull
                @Utf8ByteLength(min = 1, max = 72)
                String password) {}
