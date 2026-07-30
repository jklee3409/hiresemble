package com.hiresemble.job.domain;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisEvidence;
import com.hiresemble.profile.application.port.ProfileAnalysisQueryPort.AnalysisProfileSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class JobAnalysisHashing {

    private JobAnalysisHashing() {}

    public static String profileHash(UUID userId, AnalysisProfileSnapshot profile) {
        StringBuilder canonical = new StringBuilder()
                .append("profile-v1|")
                .append(userId).append('|')
                .append(profile.profileId()).append('|')
                .append(profile.version()).append('|')
                .append(nullSafe(profile.introduction())).append('|')
                .append(sorted(profile.desiredRoles())).append('|')
                .append(sorted(profile.desiredIndustries())).append('|')
                .append(sorted(profile.desiredLocations())).append('|')
                .append(nullSafe(profile.expectedGraduationDate()));
        return sha256(canonical.toString());
    }

    public static String evidenceHash(UUID userId, AnalysisEvidence evidence) {
        return sha256(String.join(
                "|",
                "evidence-v1",
                userId.toString(),
                evidence.id().toString(),
                Long.toString(evidence.version()),
                evidence.sourceType().name(),
                nullSafe(evidence.sourceEntityId()),
                nullSafe(evidence.documentId()),
                evidence.verificationStatus().name(),
                Boolean.toString(evidence.sourceDeleted()),
                nullSafe(evidence.evidenceCategory()),
                nullSafe(evidence.title()),
                nullSafe(evidence.content())));
    }

    public static String evidenceSnapshotHash(
            UUID userId, List<AnalysisEvidence> evidence) {
        StringBuilder canonical = new StringBuilder("evidence-snapshot-v1|")
                .append(userId);
        evidence.stream()
                .sorted(Comparator.comparing(AnalysisEvidence::id))
                .forEach(item -> canonical.append('|').append(evidenceHash(userId, item)));
        return sha256(canonical.toString());
    }

    public static String contextHash(
            UUID userId,
            UUID jobId,
            long jobVersion,
            String jobContentHash,
            String profileHash,
            String evidenceHash,
            String rubricVersion,
            String workflowVersion,
            AiQualityMode qualityMode,
            long embeddingPolicyVersion,
            int embeddingGeneration,
            String retrievalPolicyVersion) {
        return sha256(String.join(
                "|",
                "job-analysis-context-v1",
                userId.toString(),
                jobId.toString(),
                Long.toString(jobVersion),
                jobContentHash,
                profileHash,
                evidenceHash,
                rubricVersion,
                workflowVersion,
                qualityMode.name(),
                Long.toString(embeddingPolicyVersion),
                Integer.toString(embeddingGeneration),
                retrievalPolicyVersion));
    }

    public static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sorted(List<String> values) {
        return values.stream()
                .sorted()
                .map(JobAnalysisHashing::nullSafe)
                .collect(java.util.stream.Collectors.joining("\u001f"));
    }

    private static String nullSafe(Object value) {
        return value == null ? "-" : value.toString();
    }
}
