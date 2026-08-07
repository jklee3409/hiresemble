package com.hiresemble.profile.api.dto;

import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceMatchResolution;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ExperienceRequests {

    private ExperienceRequests() {}

    @Schema(name = "ExperienceItemUpdateRequest")
    public record ExperienceItemUpdateRequest(
            @NotBlank @Size(max = 250) String title,
            @NotBlank @Size(max = 20000) String content,
            @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "ExperienceVerificationRequest")
    public record ExperienceVerificationRequest(
            @NotNull EvidenceVerificationStatus status,
            @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "ExperienceMatchResolutionRequest")
    public record ExperienceMatchResolutionRequest(
            @NotNull ExperienceMatchResolution resolution,
            @Schema(nullable = true) UUID targetExperienceItemId,
            @NotNull @PositiveOrZero Long version) {}
}
