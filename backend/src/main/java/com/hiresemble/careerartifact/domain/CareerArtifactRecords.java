package com.hiresemble.careerartifact.domain;

import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.EvidenceUsageType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.GenerationStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.LifecycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class CareerArtifactRecords {

    private CareerArtifactRecords() {}

    public record Artifact(
            UUID id,
            UUID userId,
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

    public record Version(
            UUID id,
            UUID userId,
            UUID artifactId,
            int versionNo,
            String contentSchemaVersion,
            JsonNode content,
            String templateKey,
            String templateVersion,
            String model,
            UUID agentRunId,
            String storageKey,
            String mimeType,
            long sizeBytes,
            String checksumSha256,
            Instant createdAt) {}

    public record GenerationRequest(
            UUID id,
            UUID userId,
            UUID artifactId,
            UUID agentRunId,
            UUID targetVersionId,
            JsonNode renderProfileSnapshot,
            String renderProfileHash,
            Instant createdAt,
            Instant consumedAt) {}

    public record VerifiedEvidence(
            UUID experienceItemId,
            long experienceVersion,
            UUID evidenceId,
            long evidenceVersion,
            String category,
            String title,
            String content,
            EvidenceUsageType usageType,
            boolean githubProvenance) {}

    public record ProfileSectionSnapshot(
            String section,
            UUID id,
            long version,
            JsonNode safeContent) {}

    public record Page<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {}

    public record Readiness(
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
}
