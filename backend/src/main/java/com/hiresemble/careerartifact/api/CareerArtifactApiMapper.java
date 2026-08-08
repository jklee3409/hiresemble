package com.hiresemble.careerartifact.api;

import com.hiresemble.agentrun.api.mapper.AgentRunApiMapper;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactAiModelDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactDetailDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactDownloadUrlDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactEvidenceRefDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactPreviewDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactReadinessDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactSummaryDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.CareerArtifactVersionSummaryDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.PortfolioPreviewDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.PortfolioSlidePreviewDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.ResumeItemDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.ResumePreviewDto;
import com.hiresemble.careerartifact.api.CareerArtifactDtos.ResumeSectionDto;
import com.hiresemble.careerartifact.application.CareerArtifactApplicationService.Download;
import com.hiresemble.careerartifact.application.CareerArtifactCommands.Detail;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.EvidenceRef;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Artifact;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Readiness;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.ai.model.OpenAiChatModels;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CareerArtifactApiMapper {

    private final AgentRunApiMapper runMapper;
    private final ObjectMapper objectMapper;

    public CareerArtifactApiMapper(AgentRunApiMapper runMapper, ObjectMapper objectMapper) {
        this.runMapper = runMapper;
        this.objectMapper = objectMapper;
    }

    public CareerArtifactReadinessDto readiness(Readiness value) {
        return new CareerArtifactReadinessDto(
                value.hasUploadedResume(), value.hasUploadedPortfolio(),
                value.hasGeneratedResume(), value.hasGeneratedPortfolio(),
                value.verifiedExperienceCount(), value.verifiedGitHubExperienceCount(),
                value.verifiedStrengthCount(), value.canGenerateResume(),
                value.canGeneratePortfolio(), value.warnings());
    }

    public CareerArtifactAiModelDto model(OpenAiChatModels.Model value) {
        return new CareerArtifactAiModelDto(
                value.id(), value.displayName(), value.description(), value.recommended());
    }

    public CareerArtifactSummaryDto summary(Artifact value) {
        return new CareerArtifactSummaryDto(
                value.id(), value.artifactType(), value.title(), value.lifecycleStatus(),
                value.generationStatus(), value.currentVersionId(), value.currentVersionNo(),
                value.latestAgentRunId(), value.version(), value.createdAt(), value.updatedAt());
    }

    public CareerArtifactVersionSummaryDto version(Version value) {
        return new CareerArtifactVersionSummaryDto(
                value.id(), value.artifactId(), value.versionNo(), value.model(),
                value.templateKey(), value.mimeType(), value.sizeBytes(), value.createdAt());
    }

    public CareerArtifactDetailDto detail(Detail value) {
        return new CareerArtifactDetailDto(
                summary(value.artifact()),
                value.currentVersion() == null ? null : version(value.currentVersion()),
                preview(value.artifact().artifactType(), value.currentVersion()),
                value.latestRun() == null ? null : runMapper.summary(value.latestRun()));
    }

    public CareerArtifactDownloadUrlDto download(Download value) {
        return new CareerArtifactDownloadUrlDto(
                value.url(), value.expiresAt(), value.filename());
    }

    private CareerArtifactPreviewDto preview(ArtifactType type, Version version) {
        if (version == null) return null;
        try {
            if (type == ArtifactType.RESUME) {
                ResumeContent content = objectMapper.treeToValue(
                        version.content(), ResumeContent.class);
                return new ResumePreviewDto(
                        content.headline(), content.summary(),
                        content.sections().stream().map(section -> new ResumeSectionDto(
                                section.type(), section.title(),
                                section.items().stream().map(item -> new ResumeItemDto(
                                        item.heading(), item.subheading(), item.period(),
                                        item.bullets(), refs(item.evidenceRefs()))).toList()))
                                .toList(),
                        content.warnings());
            }
            PortfolioContent content = objectMapper.treeToValue(
                    version.content(), PortfolioContent.class);
            return new PortfolioPreviewDto(
                    content.slides().stream().map(slide -> new PortfolioSlidePreviewDto(
                            slide.slideNo(), slide.slideType(), slide.title(), slide.subtitle(),
                            slide.items(), slide.visualType(), refs(slide.evidenceRefs())))
                            .toList(),
                    content.warnings());
        } catch (Exception exception) {
            throw new IllegalStateException("Career Artifact preview is invalid", exception);
        }
    }

    private java.util.List<CareerArtifactEvidenceRefDto> refs(
            java.util.List<EvidenceRef> values) {
        return values.stream().map(value -> new CareerArtifactEvidenceRefDto(
                value.experienceItemId(), value.evidenceId(),
                value.usageType(), value.title())).toList();
    }
}
