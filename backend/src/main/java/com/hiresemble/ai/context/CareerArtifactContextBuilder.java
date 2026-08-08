package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort;
import java.util.ArrayList;
import java.util.List;

/** Owner-scoped context made only from approved canonical evidence and selected profile rows. */
public final class CareerArtifactContextBuilder implements ContextBuilder {

    private final CareerArtifactWorkflowPort workflowPort;
    private final long modelPolicyVersion;

    public CareerArtifactContextBuilder(
            CareerArtifactWorkflowPort workflowPort, long modelPolicyVersion) {
        if (modelPolicyVersion < 1) throw new IllegalArgumentException("model policy is invalid");
        this.workflowPort = workflowPort;
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        CareerArtifactWorkflowPort.GenerationState state = workflowPort.load(run);
        List<ContextRef> refs = new ArrayList<>();
        state.evidence().forEach(value -> {
            refs.add(new ContextRef(
                    "EXPERIENCE_ITEM", value.experienceItemId(),
                    value.experienceVersion(), "VERIFIED"));
            refs.add(new ContextRef(
                    "PROFILE_EVIDENCE", value.evidenceId(),
                    value.evidenceVersion(), "VERIFIED"));
        });
        state.profileSnapshots().forEach(value -> refs.add(new ContextRef(
                "PROFILE_" + value.section(), value.id(), value.version(), "APPROVED_PROFILE")));
        long acceptedVersion = run.inputReferenceSnapshot().path("artifactVersion").asLong();
        return new ContextSnapshot(
                run.userId(),
                List.of(new ResourceSnapshotRef(
                        "CAREER_ARTIFACT", state.artifact().id(), acceptedVersion,
                        run.canonicalInputHash())),
                List.of(),
                refs,
                new TruncationSummary(
                        state.includedRefCount(), state.omittedRefCount(), state.omittedKinds()),
                state.contextHash(),
                "VERIFIED_CANONICAL_EXPERIENCE",
                modelPolicyVersion,
                false,
                true);
    }
}
