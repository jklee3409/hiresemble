package com.hiresemble.ai.context;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.Objects;

/** Routes fixed workflow types to their owner-scoped context builders. */
public final class WorkflowContextBuilder implements ContextBuilder {

    private final ContextBuilder documentIngestion;
    private final ContextBuilder jobPostingExtraction;

    public WorkflowContextBuilder(
            ContextBuilder documentIngestion,
            ContextBuilder jobPostingExtraction) {
        this.documentIngestion = Objects.requireNonNull(documentIngestion);
        this.jobPostingExtraction = Objects.requireNonNull(jobPostingExtraction);
    }

    @Override
    public ContextSnapshot build(ContextRequest request) {
        WorkflowType workflowType = request.run().workflowType();
        return switch (workflowType) {
            case DOCUMENT_INGESTION -> documentIngestion.build(request);
            case JOB_POSTING_EXTRACTION -> jobPostingExtraction.build(request);
            default -> throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_CONTEXT_NOT_CONFIGURED",
                    "AI 실행 구성이 준비되지 않았습니다.");
        };
    }
}
