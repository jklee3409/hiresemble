package com.hiresemble.careerartifact.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Artifact;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.ProfileSectionSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ArtifactType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.ProfileSection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CareerArtifactCommands {

    private CareerArtifactCommands() {}

    public record GenerationInput(
            ArtifactType artifactType,
            String title,
            List<UUID> experienceItemIds,
            String model,
            String templateKey,
            Set<ProfileSection> includeProfileSections,
            CareerArtifactRenderProfile renderProfile,
            Long artifactVersion) {}

    public record PreparedGeneration(
            UUID userId,
            UUID artifactId,
            UUID agentRunId,
            UUID targetVersionId,
            ArtifactType artifactType,
            String title,
            long acceptedArtifactVersion,
            String model,
            String templateKey,
            String templateVersion,
            Set<ProfileSection> includeProfileSections,
            List<VerifiedEvidence> evidence,
            List<ProfileSectionSnapshot> profileSnapshots,
            CareerArtifactRenderProfile renderProfile,
            String renderProfileHash,
            String canonicalMaterial) {}

    public record Detail(
            Artifact artifact,
            Version currentVersion,
            AgentRunSnapshot latestRun) {}

    public record Accepted(WorkflowLaunchResult run) {}
}
