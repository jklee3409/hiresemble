package com.hiresemble.document.application;

import com.hiresemble.agentrun.domain.AgentRunStatus;
import com.hiresemble.document.domain.DocumentParseStatus;
import com.hiresemble.document.domain.EvidenceExtractionStatus;
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
