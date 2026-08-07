package com.hiresemble.profile.application.port;

import com.hiresemble.document.application.model.DocumentEvidenceCandidate;
import com.hiresemble.document.application.model.DocumentEvidenceApplyResult;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentEvidenceCommandPort {

    DocumentEvidenceApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            Instant now);

    default DocumentEvidenceApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            EmbeddingPolicy embeddingPolicy,
            Instant now) {
        return applyCandidates(userId, documentId, sourceRevision, candidates, now);
    }

    void retireDocumentEvidence(UUID userId, UUID documentId, Instant retiredAt);

}
