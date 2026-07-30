package com.hiresemble.ai.context;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.Objects;

/** Routes fixed workflow types to their owner-scoped context builders. */
public final class WorkflowContextBuilder implements ContextBuilder {

    private final ContextBuilder documentIngestion;
    private final ContextBuilder jobPostingExtraction;
    private final ContextBuilder jobAnalysis;
    private final ContextBuilder coverLetterGeneration;
    private final ContextBuilder coverLetterVerification;

    public WorkflowContextBuilder(
            ContextBuilder documentIngestion,
            ContextBuilder jobPostingExtraction,
            ContextBuilder jobAnalysis) {
        this(
                documentIngestion,
                jobPostingExtraction,
                jobAnalysis,
                unsupported(),
                unsupported());
    }

    public WorkflowContextBuilder(
            ContextBuilder documentIngestion,
            ContextBuilder jobPostingExtraction,
            ContextBuilder jobAnalysis,
            ContextBuilder coverLetterGeneration,
            ContextBuilder coverLetterVerification) {
        this.documentIngestion = Objects.requireNonNull(documentIngestion);
        this.jobPostingExtraction = Objects.requireNonNull(jobPostingExtraction);
        this.jobAnalysis = Objects.requireNonNull(jobAnalysis);
        this.coverLetterGeneration = Objects.requireNonNull(coverLetterGeneration);
        this.coverLetterVerification = Objects.requireNonNull(coverLetterVerification);
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        WorkflowType workflowType = request.run().workflowType();
        return switch (workflowType) {
            case DOCUMENT_INGESTION -> documentIngestion.build(request);
            case JOB_POSTING_EXTRACTION -> jobPostingExtraction.build(request);
            case JOB_ANALYSIS -> jobAnalysis.build(request);
            case COVER_LETTER_GENERATION -> coverLetterGeneration.build(request);
            case COVER_LETTER_VERIFICATION -> coverLetterVerification.build(request);
            default -> throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                "AI_CONTEXT_NOT_CONFIGURED",
                "AI 실행 구성이 준비되지 않았습니다.");
        };
    }

    private static ContextBuilder unsupported() {
        return ignored -> {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_CONTEXT_NOT_CONFIGURED",
                    "AI 실행 구성이 준비되지 않았습니다.");
        };
    }
}
