package com.hiresemble.auth.api;

import com.hiresemble.common.validation.Utf8ByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "SignupRequest")
public record SignupRequest(
        @NotBlank @Email @Size(min = 3, max = 320) String email,
        @NotNull @Utf8ByteLength(min = 10, max = 72) String password,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^\\p{Cc}/\\\\]+$") String displayName,
        @AssertTrue boolean termsAgreed,
        @AssertTrue boolean aiConsent) {}
