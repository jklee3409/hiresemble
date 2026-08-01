package com.hiresemble.document.application.model;

/** Stable, value-free classification for a candidate filtered before persistence. */
public enum DocumentEvidenceRejectionReason {
    INVALID_PROVENANCE,
    INVALID_CONFIDENCE,
    INVALID_CATEGORY,
    EDUCATION_CATEGORY,
    INVALID_CONTENT,
    INVALID_METADATA,
    UNGROUNDED_NUMBER,
    DUPLICATE,
    OTHER_SAFE_REJECTION
}
