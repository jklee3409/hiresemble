package com.hiresemble.document.application;

import com.hiresemble.agentrun.application.AgentRunCancellationPort;
import com.hiresemble.agentrun.application.AgentRunQueryPort;
import com.hiresemble.agentrun.application.AgentRunResumePort;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.application.WorkflowLaunchResult;
import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.agentrun.domain.RequiredUserActionType;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.infrastructure.DocumentStore;
import com.hiresemble.profile.application.DocumentEvidenceCommandPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentMutationService {

    private final DocumentStore store;
    private final DocumentCreationService creationService;
    private final AgentRunQueryPort runQuery;
    private final AgentRunResumePort resumePort;
    private final AgentRunCancellationPort cancellationPort;
    private final DocumentEvidenceCommandPort evidencePort;
    private final ObjectDeletionOutboxService outbox;
    private final Clock clock;

    public DocumentMutationService(
            DocumentStore store,
            DocumentCreationService creationService,
            AgentRunQueryPort runQuery,
            AgentRunResumePort resumePort,
            AgentRunCancellationPort cancellationPort,
            DocumentEvidenceCommandPort evidencePort,
            ObjectDeletionOutboxService outbox,
            Clock clock) {
        this.store = store;
        this.creationService = creationService;
        this.runQuery = runQuery;
        this.resumePort = resumePort;
        this.cancellationPort = cancellationPort;
        this.evidencePort = evidencePort;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public WorkflowLaunchResult manualText(
            UUID userId,
            UUID documentId,
            long expectedVersion,
            String normalizedText,
            int characterCount,
            String canonicalInputHash) {
        Instant now = clock.instant();
        DocumentRecord current = active(userId, documentId);
        if (current.version() != expectedVersion) throw versionConflict();
        AgentRunSnapshot latest = latestRun(current);
        boolean resumeWaiting = latest != null
                && latest.status() == AgentRunStatus.WAITING_USER
                && latest.requiredUserAction() != null
                && latest.requiredUserAction().type() == RequiredUserActionType.PROVIDE_DOCUMENT_TEXT;
        if (latest != null && !latest.status().isTerminal() && !resumeWaiting) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        DocumentRecord updated = store.setManualText(userId, documentId, expectedVersion, now)
                .orElseThrow(this::versionConflict);
        store.saveExtractedText(
                userId, documentId, updated.sourceRevision(), normalizedText, characterCount,
                null, List.of(0), "MANUAL", "1", false, now);
        if (resumeWaiting) {
            AgentRunSnapshot resumed = resumePort.resume(
                    userId, latest.id(), latest.stateVersion(), now);
            return new WorkflowLaunchResult(
                    resumed.id(), resumed.status(), resumed.resourceType(), resumed.resourceId(), false);
        }
        return creationService.launchRevision(updated, canonicalInputHash);
    }

    @Transactional
    public WorkflowLaunchResult reparse(
            UUID userId, UUID documentId, long expectedVersion, String canonicalInputHash) {
        DocumentRecord current = active(userId, documentId);
        if (current.version() != expectedVersion) throw versionConflict();
        AgentRunSnapshot latest = latestRun(current);
        if (latest != null && (latest.status() == AgentRunStatus.QUEUED
                || latest.status() == AgentRunStatus.RUNNING
                || latest.status() == AgentRunStatus.WAITING_USER)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        DocumentRecord updated = store.resetForReparse(
                        userId, documentId, expectedVersion, clock.instant())
                .orElseThrow(this::versionConflict);
        return creationService.launchRevision(updated, canonicalInputHash);
    }

    @Transactional
    public void delete(UUID userId, UUID documentId, long expectedVersion) {
        Instant now = clock.instant();
        DocumentRecord document = active(userId, documentId);
        if (document.version() != expectedVersion) throw versionConflict();
        AgentRunSnapshot latest = latestRun(document);
        if (!store.softDelete(userId, documentId, expectedVersion, now)) throw versionConflict();
        if (latest != null && latest.cancellable()) {
            cancellationPort.requestCancellation(userId, latest.id(), latest.stateVersion(), now);
        }
        evidencePort.handleDocumentDeletion(userId, documentId, now);
        store.deleteDerivedContent(userId, documentId);
        outbox.enqueueDocument(userId, documentId, document.storageKey(), now);
    }

    private AgentRunSnapshot latestRun(DocumentRecord document) {
        return document.latestAgentRunId() == null ? null : runQuery
                .findByOwner(document.userId(), document.latestAgentRunId()).orElse(null);
    }

    private DocumentRecord active(UUID userId, UUID documentId) {
        return store.findActive(userId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                java.util.Map.of("field", "version", "reason", "STALE"), null);
    }
}
