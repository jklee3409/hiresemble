package com.hiresemble.careerartifact.api;

import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ProfileSection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CareerArtifactRequests {

    private CareerArtifactRequests() {}

    public record CreateCareerArtifactRequest(
            @NotNull ArtifactType artifactType,
            @NotBlank @Size(max = 120) String title,
            @NotEmpty @Size(max = 20) List<@NotNull UUID> experienceItemIds,
            @NotBlank @Size(max = 64) String model,
            @NotBlank @Size(max = 80) String templateKey,
            @Size(max = 7) List<@NotNull ProfileSection> includeProfileSections,
            @NotNull @Valid CareerArtifactRenderProfileWrite renderProfile) {}

    public record GenerateCareerArtifactRequest(
            @NotEmpty @Size(max = 20) List<@NotNull UUID> experienceItemIds,
            @NotBlank @Size(max = 64) String model,
            @NotBlank @Size(max = 80) String templateKey,
            @Size(max = 7) List<@NotNull ProfileSection> includeProfileSections,
            @NotNull @Valid CareerArtifactRenderProfileWrite renderProfile,
            @PositiveOrZero long version) {}

    public record CareerArtifactVersionRequest(@PositiveOrZero long version) {}

    public record CareerArtifactRenderProfileWrite(
            @NotBlank @Size(max = 100) String displayName,
            @Size(min = 3, max = 320) String email,
            @Size(max = 30) String phone,
            @Size(max = 5) List<@Valid @NotNull RenderProfileLinkWrite> links,
            boolean includeContact) {}

    public record RenderProfileLinkWrite(
            @NotBlank @Size(max = 50) String label,
            @NotBlank @Size(max = 500)
                    @Pattern(regexp = "^https://.+", flags = Pattern.Flag.CASE_INSENSITIVE)
                    String url) {}
}
