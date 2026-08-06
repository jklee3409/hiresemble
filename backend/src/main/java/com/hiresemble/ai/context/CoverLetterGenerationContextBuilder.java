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
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.port.CoverLetterQueryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Builds a reference-only P7 generation context while the full approved snapshot stays in memory.
 */
public final class CoverLetterGenerationContextBuilder implements ContextBuilder {

    private static final int MAX_CONTEXT_EVIDENCE = 50;

    private final CoverLetterQueryPort queryPort;
    private final AiPreferenceQueryPort preferenceQueryPort;
    private final long modelPolicyVersion;

    public CoverLetterGenerationContextBuilder(
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
        if (run.workflowType() != WorkflowType.COVER_LETTER_GENERATION
                || (!CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION.equals(
                                run.workflowVersion())
                        && !CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V2_VERSION.equals(
                                run.workflowVersion())
                        && !CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_V3_VERSION.equals(
                                run.workflowVersion())
                        && !CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_LEGACY_VERSION.equals(
                                run.workflowVersion()))
                || !"COVER_LETTER".equals(run.resourceType())
                || run.resourceId() == null
                || !validSelection(run)) {
            throw configurationFailure();
        }
        InputReference input = input(run);
        if (!run.resourceId().equals(input.coverLetterId())
                || !selectionMatches(run, input.qualityMode(), input.model())) {
            throw ownerFailure();
        }
        GenerationSnapshot snapshot = load(run, input);
        if (!run.userId().equals(snapshot.userId())
                || !run.resourceId().equals(snapshot.coverLetterId())
                || !selectionMatches(run, snapshot.qualityMode(), snapshot.model())
                || snapshot.questions().size() > 20
                || snapshot.verifiedEvidence().stream()
                        .anyMatch(value -> value.id() == null)) {
            throw ownerFailure();
        }

        List<ContextRef> evidenceRefs = snapshot.verifiedEvidence().stream()
                .limit(MAX_CONTEXT_EVIDENCE)
                .map(evidence -> new ContextRef(
                        "PROFILE_EVIDENCE",
                        evidence.id(),
                        evidence.version(),
                        "VERIFIED"))
                .toList();
        int omittedEvidence =
                Math.max(0, snapshot.verifiedEvidence().size() - evidenceRefs.size());
        List<String> omittedKinds =
                omittedEvidence == 0 ? List.of() : List.of("VERIFIED_EVIDENCE_TAIL");
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
                                "JOB",
                                snapshot.job().jobId(),
                                snapshot.job().jobVersion(),
                                jobHash(snapshot))),
                List.of(),
                evidenceRefs,
                new TruncationSummary(
                        evidenceRefs.size(),
                        omittedEvidence,
                        omittedKinds),
                snapshot.snapshotHash(),
                "CURRENT_VERIFIED_EVIDENCE_ONLY",
                modelPolicyVersion,
                highQualityEnabled,
                budgetConfirmed(run));
    }

    private GenerationSnapshot load(AgentRunSnapshot run, InputReference input) {
        try {
            if (run.retryOfRunId() != null) {
                return queryPort.loadGenerationRetrySnapshot(
                        run.userId(), run.id(), null);
            }
            return isExactModel(run)
                    ? queryPort.loadGenerationSnapshotByModel(
                            run.userId(),
                            input.coverLetterId(),
                            input.coverLetterVersion(),
                            input.questionIds(),
                            input.preferredEvidenceIds(),
                            input.avoidExperienceDuplication(),
                            input.model(),
                            input.snapshotHash())
                    : queryPort.loadGenerationSnapshot(
                            run.userId(),
                            input.coverLetterId(),
                            input.coverLetterVersion(),
                            input.questionIds(),
                            input.preferredEvidenceIds(),
                            input.avoidExperienceDuplication(),
                            input.qualityMode(),
                            input.snapshotHash());
        } catch (BusinessException exception) {
            throw mapBusiness(exception);
        }
    }

    private InputReference input(AgentRunSnapshot run) {
        JsonNode input = run.inputReferenceSnapshot();
        try {
            UUID coverLetterId =
                    UUID.fromString(input.path("coverLetterId").asText());
            long coverLetterVersion = input.path("coverLetterVersion").asLong(-1);
            String snapshotHash = input.path("snapshotHash").asText();
            String model = isExactModel(run) ? input.path("model").asText(null) : null;
            AiQualityMode qualityMode = isExactModel(run)
                    ? null
                    : AiQualityMode.valueOf(input.path("qualityMode").asText());
            if (isExactModel(run)) {
                OpenAiChatModels.requireCoverLetter(model);
            }
            boolean avoidExperienceDuplication =
                    input.path("avoidExperienceDuplication").asBoolean(false);
            List<UUID> questionIds = uuidArray(input.path("questionIds"), 1, 20);
            List<UUID> preferredEvidenceIds =
                    uuidArray(input.path("preferredEvidenceIds"), 0, 50);
            if (coverLetterVersion < 0
                    || snapshotHash == null
                    || !snapshotHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("generation input is invalid");
            }
            return new InputReference(
                    coverLetterId,
                    coverLetterVersion,
                    snapshotHash,
                    qualityMode,
                    model,
                    avoidExperienceDuplication,
                    questionIds,
                    preferredEvidenceIds);
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private List<UUID> uuidArray(JsonNode node, int minimum, int maximum) {
        if (node == null || !node.isArray() || node.size() < minimum || node.size() > maximum) {
            throw new IllegalArgumentException("UUID array is invalid");
        }
        List<UUID> values = new ArrayList<>();
        node.forEach(value -> values.add(UUID.fromString(value.asText())));
        if (values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException("UUID array contains duplicates");
        }
        return List.copyOf(values);
    }

    private String jobHash(GenerationSnapshot snapshot) {
        return sha256(String.join(
                "|",
                snapshot.job().jobId().toString(),
                Long.toString(snapshot.job().jobVersion()),
                snapshot.job().analysisId().toString(),
                Integer.toString(snapshot.job().analysisVersion()),
                Boolean.toString(snapshot.job().analysisOutdated())));
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

    private AiExecutionException mapBusiness(BusinessException exception) {
        if (exception.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
            return ownerFailure();
        }
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                exception.errorCode().code(),
                exception.errorCode().defaultMessage());
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
        return CanonicalWorkflowDefinitions.COVER_LETTER_GENERATION_VERSION.equals(
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
            long coverLetterVersion,
            String snapshotHash,
            AiQualityMode qualityMode,
            String model,
            boolean avoidExperienceDuplication,
            List<UUID> questionIds,
            List<UUID> preferredEvidenceIds) {}
}
