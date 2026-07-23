package com.hiresemble.ai.workflow.document;

import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.orchestration.WorkflowFailureHandler;
import com.hiresemble.document.application.port.DocumentWorkflowCommandPort;
import com.hiresemble.document.application.port.DocumentWorkflowQueryPort;
import com.hiresemble.document.domain.model.DocumentParseStatus;
import com.hiresemble.document.domain.model.EvidenceExtractionStatus;

/** Preserves deterministic parse results while safely failing the AI-only status axis. */
public final class DocumentIngestionFailureHandler implements WorkflowFailureHandler {

    private final DocumentWorkflowQueryPort queryPort;
    private final DocumentWorkflowCommandPort commandPort;

    public DocumentIngestionFailureHandler(
            DocumentWorkflowQueryPort queryPort,
            DocumentWorkflowCommandPort commandPort) {
        this.queryPort = queryPort;
        this.commandPort = commandPort;
    }

    @Override
    public boolean supports(AgentRunSnapshot run) {
        return run.workflowType() == WorkflowType.DOCUMENT_INGESTION
                && "DOCUMENT".equals(run.resourceType())
                && run.resourceId() != null;
    }

    @Override
    public void onFailure(AgentRunSnapshot run, AiExecutionException failure) {
        var document = queryPort.snapshot(run.userId(), run.resourceId());
        if (document.parseStatus() == DocumentParseStatus.PARSED
                && (document.evidenceExtractionStatus() == EvidenceExtractionStatus.QUEUED
                        || document.evidenceExtractionStatus()
                                == EvidenceExtractionStatus.EXTRACTING)) {
            commandPort.failEvidenceExtraction(
                    run.userId(), run.resourceId(), run.id(), failure.safeCode());
            return;
        }
        if (document.parseStatus() == DocumentParseStatus.PARSING) {
            commandPort.compensateToStableState(run.userId(), run.resourceId(), run.id());
        }
    }
}
