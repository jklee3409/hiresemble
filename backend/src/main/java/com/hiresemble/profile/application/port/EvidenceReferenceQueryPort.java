package com.hiresemble.profile.application.port;

import java.util.UUID;

/** Future provenance contributors implement this without introducing P5 tables in P4. */
public interface EvidenceReferenceQueryPort {
    boolean isReferenced(UUID userId, UUID evidenceId);
}
