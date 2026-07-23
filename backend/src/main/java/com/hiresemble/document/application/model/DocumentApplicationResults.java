package com.hiresemble.document.application.model;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.document.domain.model.DocumentParseStatus;
import com.hiresemble.document.domain.model.EvidenceExtractionStatus;
import java.time.Instant;
import java.util.UUID;

public final class DocumentApplicationResults {

    private DocumentApplicationResults() {}

    public record UploadAccepted(
            UUID documentId,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceExtractionStatus,
            UUID agentRunId,
            AgentRunStatus status) {}

    public record RunAccepted(
            UUID agentRunId,
            AgentRunStatus status,
            String resourceType,
            UUID resourceId) {}

    public record DownloadUrl(String url, Instant expiresAt) {}
}
