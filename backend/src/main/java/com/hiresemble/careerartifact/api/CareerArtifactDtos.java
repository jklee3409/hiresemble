package com.hiresemble.careerartifact.api;

import com.hiresemble.agentrun.api.dto.AgentRunSummaryDto;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.EvidenceUsageType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.GenerationStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.LifecycleStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioSlideType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioVisualType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CareerArtifactDtos {

    private CareerArtifactDtos() {}

    public record CareerArtifactReadinessDto(
            boolean hasUploadedResume,
            boolean hasUploadedPortfolio,
            boolean hasGeneratedResume,
            boolean hasGeneratedPortfolio,
            int verifiedExperienceCount,
            int verifiedGitHubExperienceCount,
            int verifiedStrengthCount,
            boolean canGenerateResume,
            boolean canGeneratePortfolio,
            List<String> warnings) {}

    public record CareerArtifactAiModelDto(
            String id,
            String displayName,
            String description,
            boolean recommended) {}

    public record CareerArtifactSummaryDto(
            UUID id,
            ArtifactType artifactType,
            String title,
            LifecycleStatus lifecycleStatus,
            GenerationStatus generationStatus,
            UUID currentVersionId,
            Integer currentVersionNo,
            UUID latestAgentRunId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record CareerArtifactVersionSummaryDto(
            UUID id,
            UUID artifactId,
            int versionNo,
            String model,
            String templateKey,
            String mimeType,
            long fileSizeBytes,
            Instant createdAt) {}

    public record CareerArtifactEvidenceRefDto(
            UUID experienceItemId,
            UUID evidenceId,
            EvidenceUsageType usageType,
            String title) {}

    @Schema(oneOf = {ResumePreviewDto.class, PortfolioPreviewDto.class})
    public sealed interface CareerArtifactPreviewDto
            permits ResumePreviewDto, PortfolioPreviewDto {}

    public record ResumePreviewDto(
            String headline,
            String summary,
            List<ResumeSectionDto> sections,
            List<String> warnings) implements CareerArtifactPreviewDto {}

    public record ResumeSectionDto(
            String type,
            String title,
            List<ResumeItemDto> items) {}

    public record ResumeItemDto(
            String heading,
            String subheading,
            String period,
            List<String> bullets,
            List<CareerArtifactEvidenceRefDto> evidenceRefs) {}

    public record PortfolioPreviewDto(
            List<PortfolioSlidePreviewDto> slides,
            List<String> warnings) implements CareerArtifactPreviewDto {}

    public record PortfolioSlidePreviewDto(
            int slideNo,
            PortfolioSlideType slideType,
            String title,
            String subtitle,
            List<String> items,
            PortfolioVisualType visualType,
            List<CareerArtifactEvidenceRefDto> evidenceRefs) {}

    public record CareerArtifactDetailDto(
            CareerArtifactSummaryDto artifact,
            CareerArtifactVersionSummaryDto currentVersion,
            CareerArtifactPreviewDto preview,
            AgentRunSummaryDto latestRun) {}

    public record CareerArtifactDownloadUrlDto(
            String url,
            Instant expiresAt,
            String filename) {}
}
