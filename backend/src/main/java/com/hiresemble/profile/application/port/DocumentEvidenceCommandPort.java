package com.hiresemble.profile.application.port;

import com.hiresemble.document.application.model.DocumentEvidenceCandidate;
import com.hiresemble.document.application.model.DocumentEvidenceApplyResult;
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

    void handleDocumentDeletion(UUID userId, UUID documentId, Instant deletedAt);

}
