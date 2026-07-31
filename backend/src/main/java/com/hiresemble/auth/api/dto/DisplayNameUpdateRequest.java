package com.hiresemble.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
        name = "DisplayNameUpdateRequest",
        description = "Authenticated account display-name update input")
public record DisplayNameUpdateRequest(
        @Schema(description = "Trimmed display name", example = "Sample Candidate")
                @NotBlank
                @Size(max = 100)
                @Pattern(regexp = "^[^\\p{Cc}/\\\\]+$")
                String displayName) {}
