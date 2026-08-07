package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.githubsource.application.GitHubWorkflowQueryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/** Stable, owner-scoped context for the same-run GitHub repository-selection pause. */
public final class GitHubIngestionContextBuilder implements ContextBuilder {

    private final GitHubWorkflowQueryPort queryPort;
    private final long modelPolicyVersion;

    public GitHubIngestionContextBuilder(
            GitHubWorkflowQueryPort queryPort, long modelPolicyVersion) {
        if (modelPolicyVersion < 1) throw new IllegalArgumentException("model policy is invalid");
        this.queryPort = queryPort;
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.GITHUB_INGESTION
                || !"GITHUB_SOURCE".equals(run.resourceType())
                || run.resourceId() == null) {
            throw configurationFailure();
        }
        UUID inputSourceId;
        long inputRevision;
        String policyVersion;
        String apiVersion;
        try {
            inputSourceId = UUID.fromString(
                    run.inputReferenceSnapshot().path("githubSourceId").asText());
            inputRevision = run.inputReferenceSnapshot().path("sourceRevision").asLong(-1);
            policyVersion = run.inputReferenceSnapshot()
                    .path("retrievalPolicyVersion").asText();
            apiVersion = run.inputReferenceSnapshot().path("githubApiVersion").asText();
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
        if (!run.resourceId().equals(inputSourceId)
                || inputRevision < 0
                || policyVersion.isBlank()
                || apiVersion.isBlank()) {
            throw ownerFailure();
        }
        var source = queryPort.source(run.userId(), inputSourceId);
        if (!run.id().equals(source.latestAgentRunId())) throw ownerFailure();
        String contextHash = sha256(String.join(
                "|",
                run.userId().toString(),
                inputSourceId.toString(),
                Long.toString(inputRevision),
                run.canonicalInputHash(),
                policyVersion,
                apiVersion));
        return new ContextSnapshot(
                run.userId(),
                List.of(new ResourceSnapshotRef(
                        "GITHUB_SOURCE", inputSourceId, inputRevision, run.canonicalInputHash())),
                List.of(),
                List.of(),
                new TruncationSummary(0, 0, List.of()),
                contextHash,
                "UNVERIFIED_GITHUB_SOURCE",
                modelPolicyVersion,
                false,
                true);
    }

    private String sha256(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                "RESOURCE_NOT_FOUND",
                "The requested GitHub source could not be found.");
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "The GitHub ingestion context is not configured.");
    }
}
