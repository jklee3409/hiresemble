package com.hiresemble.profile.domain.model;

/** Server classification of an extracted candidate against the owner's experience library. */
public enum ExperienceMatchKind {
    NEW,
    SAME_EXPERIENCE,
    RELATED_DIFFERENT,
    CONFLICT
}
