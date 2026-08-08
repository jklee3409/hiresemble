package com.hiresemble.careerartifact.application;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Artifact;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.ProfileSectionSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.Version;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public interface CareerArtifactWorkflowPort {

    GenerationState load(AgentRunSnapshot run);

    RenderedArtifact render(GenerationState state, Object groundedContent);

    RenderedArtifact validate(UUID agentRunId);

    PersistPreparation upload(UUID agentRunId);

    Version apply(UUID agentRunId, PersistPreparation preparation);

    void discard(UUID agentRunId);

    record GenerationState(
            AgentRunSnapshot run,
            Artifact artifact,
            UUID targetVersionId,
            String model,
            String templateKey,
            String templateVersion,
            String renderProfileHash,
            CareerArtifactRenderProfile renderProfile,
            List<VerifiedEvidence> evidence,
            List<ProfileSectionSnapshot> profileSnapshots,
            List<String> requestedProfileSections,
            JsonNode boundedContext,
            String contextHash,
            int includedRefCount,
            int omittedRefCount,
            List<String> omittedKinds) {

        public GenerationState {
            evidence = List.copyOf(evidence);
            profileSnapshots = List.copyOf(profileSnapshots);
            requestedProfileSections = List.copyOf(requestedProfileSections);
            omittedKinds = List.copyOf(omittedKinds);
        }
    }

    record RenderedArtifact(
            GenerationState state,
            Object groundedContent,
            RenderedOfficeFile file,
            OfficeValidation validation,
            String uploadedStorageKey) {}

    record PersistPreparation(
            UUID versionId,
            int versionNo,
            String checksumSha256) {}
}
