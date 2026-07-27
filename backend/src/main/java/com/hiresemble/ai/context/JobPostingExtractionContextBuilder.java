package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.context.ContextBuilder.ContextRequest;
import com.hiresemble.ai.context.ContextBuilder.ContextSnapshot;
import com.hiresemble.ai.context.ContextBuilder.ResourceSnapshotRef;
import com.hiresemble.ai.context.ContextBuilder.TruncationSummary;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.job.application.port.JobWorkflowQueryPort;
import com.hiresemble.job.domain.JobRecords.UserOverrides;
import com.hiresemble.job.domain.JobRecords.WorkflowSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owner-scoped, body-free context for JOB_POSTING_EXTRACTION. */
public final class JobPostingExtractionContextBuilder implements ContextBuilder {

    private final JobWorkflowQueryPort jobQuery;
    private final long modelPolicyVersion;

    public JobPostingExtractionContextBuilder(
            JobWorkflowQueryPort jobQuery, long modelPolicyVersion) {
        if (modelPolicyVersion < 1) {
            throw new IllegalArgumentException("model policy is invalid");
        }
        this.jobQuery = Objects.requireNonNull(jobQuery);
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.JOB_POSTING_EXTRACTION
                || !"JOB".equals(run.resourceType())
                || run.resourceId() == null) {
            throw configurationFailure();
        }
        UUID inputJobId = parseJobId(run);
        if (!run.resourceId().equals(inputJobId)) {
            throw ownerFailure();
        }
        WorkflowSnapshot job = jobQuery.snapshot(run.userId(), inputJobId);
        if (!run.userId().equals(job.userId())
                || !run.id().equals(job.latestAgentRunId())) {
            throw ownerFailure();
        }

        String semanticContentHash = semanticContentHash(job);
        return new ContextSnapshot(
                run.userId(),
                List.of(new ResourceSnapshotRef(
                        "JOB", job.jobId(), job.version(), semanticContentHash)),
                List.of(),
                List.of(),
                new TruncationSummary(0, 0, List.of()),
                sha256(String.join(
                        "|",
                        run.userId().toString(),
                        job.jobId().toString(),
                        semanticContentHash)),
                "UNVERIFIED_JOB_SOURCE",
                modelPolicyVersion,
                false,
                true);
    }

    private String semanticContentHash(WorkflowSnapshot job) {
        UserOverrides overrides = job.userOverrides();
        return sha256(String.join(
                "|",
                nullSafe(job.sourceUrl()),
                nullSafe(job.canonicalUrl()),
                nullSafe(job.contentHash()),
                nullSafe(overrides.companyName()),
                nullSafe(overrides.title()),
                nullSafe(overrides.positionName()),
                hashOrDash(overrides.descriptionText()),
                instant(overrides.deadlineAt())));
    }

    private UUID parseJobId(AgentRunSnapshot run) {
        try {
            return UUID.fromString(run.inputReferenceSnapshot().path("jobId").asText());
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private String hashOrDash(String value) {
        return value == null ? "-" : sha256(value);
    }

    private String instant(Instant value) {
        return value == null ? "-" : value.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
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
                "요청한 채용 공고를 찾을 수 없습니다.");
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "AI 실행 구성이 준비되지 않았습니다.");
    }
}
