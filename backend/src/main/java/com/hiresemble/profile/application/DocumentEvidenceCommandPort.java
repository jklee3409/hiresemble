package com.hiresemble.profile.application;

import com.hiresemble.document.application.DocumentEvidenceCandidate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentEvidenceCommandPort {

    ApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            Instant now);

    void handleDocumentDeletion(UUID userId, UUID documentId, Instant deletedAt);

    record ApplyResult(List<UUID> appliedEvidenceIds, int rejectedCount) {
        public ApplyResult {
            appliedEvidenceIds = List.copyOf(appliedEvidenceIds);
        }
    }
}
