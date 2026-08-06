package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.application.port.AiPreferenceQueryPort;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationSnapshot;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Builds a body-free provenance projection for an immutable P7 answer verification. */
public final class CoverLetterVerificationContextBuilder implements ContextBuilder {

    private final CoverLetterQueryPort queryPort;
    private final AiPreferenceQueryPort preferenceQueryPort;
    private final long modelPolicyVersion;

    public CoverLetterVerificationContextBuilder(
            CoverLetterQueryPort queryPort,
            AiPreferenceQueryPort preferenceQueryPort,
            long modelPolicyVersion) {
        if (modelPolicyVersion < 1) {
            throw new IllegalArgumentException("model policy is invalid");
        }
        this.queryPort = Objects.requireNonNull(queryPort);
        this.preferenceQueryPort = Objects.requireNonNull(preferenceQueryPort);
        this.modelPolicyVersion = modelPolicyVersion;
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        AgentRunSnapshot run = request.run();
        if (run.workflowType() != WorkflowType.COVER_LETTER_VERIFICATION
                || (!CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(
                                run.workflowVersion())
                        && !CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V2_VERSION.equals(
                                run.workflowVersion())
                        && !CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V3_VERSION.equals(
                                run.workflowVersion())
                        && !CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_LEGACY_VERSION.equals(
                                run.workflowVersion()))
                || !"COVER_LETTER".equals(run.resourceType())
                || run.resourceId() == null
                || !validSelection(run)) {
            throw configurationFailure();
        }
        InputReference input = input(run);
        VerificationSnapshot snapshot = load(run, input);
        if (!run.userId().equals(snapshot.userId())
                || !run.resourceId().equals(snapshot.coverLetterId())
                || !input.answerVersionId().equals(snapshot.answerVersion().id())
                || !selectionMatches(run, snapshot.qualityMode(), snapshot.model())) {
            throw ownerFailure();
        }

        List<ContextRef> refs = new ArrayList<>();
        snapshot.historicalEvidence().forEach(evidence -> refs.add(new ContextRef(
                "HISTORICAL_EVIDENCE",
                evidence.id(),
                null,
                evidence.currentStatus().name())));
        snapshot.currentVerifiedEvidence().forEach(evidence -> refs.add(new ContextRef(
                "PROFILE_EVIDENCE",
                evidence.id(),
                evidence.version(),
                "VERIFIED")));
        boolean highQualityEnabled = isExactModel(run)
                ? false
                : preferenceQueryPort.activePreference(run.userId()).highQualityEnabled();
        return new ContextSnapshot(
                run.userId(),
                List.of(
                        new ResourceSnapshotRef(
                                "COVER_LETTER",
                                snapshot.coverLetterId(),
                                snapshot.coverLetterVersion(),
                                snapshot.snapshotHash()),
                        new ResourceSnapshotRef(
                                "COVER_LETTER_ANSWER_VERSION",
                                snapshot.answerVersion().id(),
                                snapshot.answerVersion().versionNo(),
                                answerHash(snapshot))),
                List.of(),
                refs,
                new TruncationSummary(refs.size(), 0, List.of()),
                snapshot.snapshotHash(),
                "HISTORICAL_PROVENANCE_WITH_CURRENT_STATUS",
                modelPolicyVersion,
                highQualityEnabled,
                budgetConfirmed(run));
    }

    private VerificationSnapshot load(AgentRunSnapshot run, InputReference input) {
        try {
            if (run.retryOfRunId() != null) {
                return isModern(run.workflowVersion())
                        ? queryPort.loadVerificationRetrySnapshotV2(
                                run.userId(), run.id(), null)
                        : queryPort.loadVerificationRetrySnapshot(
                                run.userId(), run.id(), null);
            }
            return isExactModel(run)
                    ? queryPort.loadVerificationSnapshotByModel(
                            run.userId(),
                            input.answerVersionId(),
                            input.model(),
                            input.snapshotHash())
                    : isModern(run.workflowVersion())
                    ? queryPort.loadVerificationSnapshotV2(
                            run.userId(),
                            input.answerVersionId(),
                            input.qualityMode(),
                            input.snapshotHash())
                    : queryPort.loadVerificationSnapshot(
                            run.userId(),
                            input.answerVersionId(),
                            input.qualityMode(),
                            input.snapshotHash());
        } catch (BusinessException exception) {
            if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                throw ownerFailure();
            }
            throw AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    exception.errorCode().code(),
                    exception.errorCode().defaultMessage());
        }
    }

    private boolean isModern(String workflowVersion) {
        return CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(workflowVersion)
                || CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V3_VERSION.equals(
                        workflowVersion)
                || CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_V2_VERSION.equals(
                        workflowVersion);
    }

    private InputReference input(AgentRunSnapshot run) {
        JsonNode input = run.inputReferenceSnapshot();
        try {
            UUID coverLetterId =
                    UUID.fromString(input.path("coverLetterId").asText());
            UUID answerVersionId =
                    UUID.fromString(input.path("answerVersionId").asText());
            UUID verificationId =
                    UUID.fromString(input.path("verificationId").asText());
            String snapshotHash = input.path("snapshotHash").asText();
            String model = isExactModel(run) ? input.path("model").asText(null) : null;
            AiQualityMode qualityMode = isExactModel(run)
                    ? null
                    : AiQualityMode.valueOf(input.path("qualityMode").asText());
            if (isExactModel(run)) {
                OpenAiChatModels.requireCoverLetter(model);
            }
            if (!run.resourceId().equals(coverLetterId)
                    || !selectionMatches(run, qualityMode, model)
                    || snapshotHash == null
                    || !snapshotHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("verification input is invalid");
            }
            return new InputReference(
                    coverLetterId,
                    answerVersionId,
                    verificationId,
                    snapshotHash,
                    qualityMode,
                    model);
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private String answerHash(VerificationSnapshot snapshot) {
        return sha256(String.join(
                "|",
                snapshot.answerVersion().id().toString(),
                Integer.toString(snapshot.answerVersion().versionNo()),
                snapshot.answerVersion().plainText(),
                snapshot.answerVersion().contentJson().toString()));
    }

    private boolean budgetConfirmed(AgentRunSnapshot run) {
        return run.priceVersion() != null
                && run.reservedCostUsd() != null
                && run.reservedCostUsd().signum() >= 0;
    }

    private String sha256(String material) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                ErrorCode.RESOURCE_NOT_FOUND.code(),
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "AI 실행 구성이 준비되지 않았습니다.");
    }

    private boolean isExactModel(AgentRunSnapshot run) {
        return CanonicalWorkflowDefinitions.COVER_LETTER_VERIFICATION_VERSION.equals(
                run.workflowVersion());
    }

    private boolean validSelection(AgentRunSnapshot run) {
        return isExactModel(run)
                ? run.requestedQualityMode() == null
                        && OpenAiChatModels.supportsCoverLetter(run.requestedModel())
                : run.requestedQualityMode() != null && run.requestedModel() == null;
    }

    private boolean selectionMatches(
            AgentRunSnapshot run, AiQualityMode qualityMode, String model) {
        return isExactModel(run)
                ? qualityMode == null && run.requestedModel().equals(model)
                : model == null && run.requestedQualityMode() == qualityMode;
    }

    private record InputReference(
            UUID coverLetterId,
            UUID answerVersionId,
            UUID verificationId,
            String snapshotHash,
            AiQualityMode qualityMode,
            String model) {}
}
