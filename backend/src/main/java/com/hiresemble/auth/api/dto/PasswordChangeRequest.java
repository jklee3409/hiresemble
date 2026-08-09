package com.hiresemble.auth.api.dto;

import com.hiresemble.common.validation.PasswordPolicy;
import com.hiresemble.common.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "PasswordChangeRequest")
public record PasswordChangeRequest(
        @NotNull @Utf8ByteLength(min = 1, max = 72)
                @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
                String currentPassword,
        @NotNull @PasswordPolicy @Utf8ByteLength(min = 1, max = 72)
                @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
                String newPassword) {}
