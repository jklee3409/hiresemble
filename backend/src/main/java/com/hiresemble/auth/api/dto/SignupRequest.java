package com.hiresemble.auth.api.dto;

import com.hiresemble.common.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "SignupRequest", description = "P1 signup input. All example values are non-production fixtures.")
public record SignupRequest(
        @Schema(
                        description = "Email normalized to lowercase before uniqueness checks",
                        format = "email",
                        example = "candidate@example.com")
                @NotBlank
                @Email
                @Size(min = 3, max = 320)
                String email,
        @Schema(
                        description = "10 to 72 UTF-8 bytes; character count can differ from byte count",
                        format = "password",
                        example = "ExampleOnly-123",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotNull
                @Utf8ByteLength(min = 10, max = 72)
                String password,
        @Schema(description = "Trimmed display name", example = "Sample Candidate")
                @NotBlank
                @Size(max = 100)
                @Pattern(regexp = "^[^\\p{Cc}/\\\\]+$")
                String displayName,
        @Schema(description = "Terms acceptance must be true", example = "true", allowableValues = "true")
                @AssertTrue
                boolean termsAgreed,
        @Schema(description = "AI processing consent must be true", example = "true", allowableValues = "true")
                @AssertTrue
                boolean aiConsent) {}
