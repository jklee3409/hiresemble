package com.hiresemble.profile.domain.model;

import java.util.UUID;

public final class ExperienceCommands {

    private ExperienceCommands() {}

    public record ExperienceWrite(String title, String content, long version) {}

    public record ExperienceVerification(
            EvidenceVerificationStatus status, long version) {}

    public record ExperienceMatchDecision(
            ExperienceMatchResolution resolution,
            UUID targetExperienceItemId,
            long version) {}
}
