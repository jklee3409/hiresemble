package com.hiresemble.document.application;

import com.hiresemble.agentrun.application.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.WorkflowLauncher;
import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ResourceReference;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.DocumentType;
import com.hiresemble.document.infrastructure.DocumentAiCostProperties;
import com.hiresemble.document.infrastructure.DocumentStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentCreationService {

    public static final String WORKFLOW_VERSION = "p0-contract-v1";
    private final DocumentStore store;
    private final WorkflowLauncher workflowLauncher;
    private final ObjectMapper objectMapper;
    private final DocumentAiCostProperties aiCost;
    private final Clock clock;

    public DocumentCreationService(
            DocumentStore store,
            WorkflowLauncher workflowLauncher,
            ObjectMapper objectMapper,
            DocumentAiCostProperties aiCost,
            Clock clock) {
        this.store = store;
        this.workflowLauncher = workflowLauncher;
        this.objectMapper = objectMapper;
        this.aiCost = aiCost;
        this.clock = clock;
    }

    @Transactional
    public CreatedDocument create(
            UUID id,
            UUID userId,
            DocumentType type,
            String originalFilename,
            String displayName,
            String storageKey,
            String mimeType,
            long size,
            String checksum,
            String canonicalInputHash) {
        Instant now = clock.instant();
        DocumentRecord document = store.create(
                id, userId, type, originalFilename, displayName, storageKey,
                mimeType, size, checksum, now);
        WorkflowLaunchResult run = launch(document, canonicalInputHash);
        store.attachLatestRun(userId, id, run.agentRunId(), now);
        return new CreatedDocument(document, run);
    }

    @Transactional
    public WorkflowLaunchResult launchRevision(DocumentRecord document, String canonicalInputHash) {
        WorkflowLaunchResult run = launch(document, canonicalInputHash);
        store.attachLatestRun(document.userId(), document.id(), run.agentRunId(), clock.instant());
        return run;
    }

    private WorkflowLaunchResult launch(DocumentRecord document, String canonicalInputHash) {
        var input = objectMapper.createObjectNode()
                .put("documentId", document.id().toString())
                .put("sourceRevision", document.sourceRevision())
                .put("documentType", document.documentType().name());
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                document.userId(), WorkflowType.DOCUMENT_INGESTION, WORKFLOW_VERSION,
                canonicalInputHash, input, AiQualityMode.ECONOMY,
                aiCost.estimatedCostUsd(), aiCost.priceVersion(),
                new ResourceReference("DOCUMENT", document.id(), document.displayName())));
    }

    public record CreatedDocument(DocumentRecord document, WorkflowLaunchResult run) {}
}
