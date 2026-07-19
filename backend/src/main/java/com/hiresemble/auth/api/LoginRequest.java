package com.hiresemble.auth.api;

import com.hiresemble.common.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank @Email @Size(min = 3, max = 320) String email,
        @NotNull @Utf8ByteLength(min = 1, max = 72) String password) {}
