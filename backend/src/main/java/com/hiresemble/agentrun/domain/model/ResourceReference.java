package com.hiresemble.agentrun.domain.model;

import java.util.UUID;

public record ResourceReference(String resourceType, UUID resourceId, String displayLabel) {
    public ResourceReference {
        if (resourceType == null || resourceType.isBlank() || resourceType.length() > 50) {
            throw new IllegalArgumentException("resource type is invalid");
        }
        if (resourceId == null) {
            throw new IllegalArgumentException("resource id is required");
        }
        if (displayLabel != null && displayLabel.length() > 200) {
            throw new IllegalArgumentException("resource display label is too long");
        }
    }
}
