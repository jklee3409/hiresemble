package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.application.model.JobAnalysisModels.JobAnalysisSnapshot;
import com.hiresemble.job.application.port.JobAnalysisQueryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Builds an owner-scoped, body-free context for the fixed P6 Job Analysis workflow. */
public final class JobAnalysisContextBuilder implements ContextBuilder {

    private static final int MAX_JOB_CONTENT_CHARACTERS = 80_000;

    private final JobAnalysisQueryPort queryPort;
    private final long modelPolicyVersion;

    public JobAnalysisContextBuilder(
            JobAnalysisQueryPort queryPort, long modelPolicyVersion) {
        if (modelPolicyVersion < 1) {
            throw new IllegalArgumentException("model policy is invalid");
        }
        this.queryPort = Objects.requireNonNull(queryPort);
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.JOB_ANALYSIS
                || !CanonicalWorkflowDefinitions.JOB_ANALYSIS_VERSION.equals(run.workflowVersion())
                || !"JOB".equals(run.resourceType())
                || run.resourceId() == null
                || run.requestedQualityMode() == null) {
            throw configurationFailure();
        }
        InputReference input = input(run);
        if (!run.resourceId().equals(input.jobId())
                || run.requestedQualityMode() != input.qualityMode()) {
            throw ownerFailure();
        }
        JobAnalysisSnapshot snapshot = load(run, input);
        if (!run.userId().equals(snapshot.userId())
                || !input.jobId().equals(snapshot.jobId())
                || input.jobVersion() != snapshot.jobVersion()
                || !input.contextHash().equals(snapshot.contextHash())
                || !CanonicalWorkflowDefinitions.JOB_ANALYSIS_VERSION.equals(
                        snapshot.workflowVersion())) {
            throw ownerFailure();
        }

        List<ContextRef> evidenceRefs = snapshot.verifiedEvidence().stream()
                .map(evidence -> new ContextRef(
                        "PROFILE_EVIDENCE",
                        evidence.id(),
                        evidence.version(),
                        evidence.verificationStatus().name()))
                .toList();
        List<String> omittedKinds = new ArrayList<>();
        if (snapshot.descriptionText() != null
                && snapshot.descriptionText().length() > MAX_JOB_CONTENT_CHARACTERS) {
            omittedKinds.add("JOB_CONTENT_TAIL");
        }
        return new ContextSnapshot(
                run.userId(),
                List.of(new ResourceSnapshotRef(
                        "JOB",
                        snapshot.jobId(),
                        snapshot.jobVersion(),
                        snapshot.jobContentHash())),
                List.of(),
                evidenceRefs,
                new TruncationSummary(
                        evidenceRefs.size(),
                        omittedKinds.isEmpty() ? 0 : 1,
                        omittedKinds),
                snapshot.contextHash(),
                "VERIFIED_EVIDENCE_ONLY",
                modelPolicyVersion,
                false,
                true);
    }

    private JobAnalysisSnapshot load(AgentRunSnapshot run, InputReference input) {
        try {
            return queryPort.loadSnapshot(
                    run.userId(),
                    input.jobId(),
                    input.jobVersion(),
                    input.qualityMode(),
                    input.contextHash());
        } catch (BusinessException exception) {
            if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                throw ownerFailure();
            }
            if (exception.errorCode() == ErrorCode.INSUFFICIENT_JOB_DATA) {
                throw insufficientData();
            }
            throw AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    exception.errorCode().code(),
                    exception.errorCode().defaultMessage());
        }
    }

    private InputReference input(AgentRunSnapshot run) {
        JsonNode input = run.inputReferenceSnapshot();
        try {
            UUID jobId = UUID.fromString(input.path("jobId").asText());
            long jobVersion = input.path("jobVersion").asLong(-1);
            String contextHash = input.path("contextHash").asText();
            AiQualityMode qualityMode =
                    AiQualityMode.valueOf(input.path("qualityMode").asText());
            if (jobVersion < 0 || contextHash == null
                    || !contextHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("input snapshot is invalid");
            }
            return new InputReference(jobId, jobVersion, contextHash, qualityMode);
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                ErrorCode.RESOURCE_NOT_FOUND.code(),
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    private AiExecutionException insufficientData() {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                ErrorCode.INSUFFICIENT_JOB_DATA.code(),
                ErrorCode.INSUFFICIENT_JOB_DATA.defaultMessage());
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "AI 실행 구성이 준비되지 않았습니다.");
    }

    private record InputReference(
            UUID jobId,
            long jobVersion,
            String contextHash,
            AiQualityMode qualityMode) {}
}
