package com.hiresemble.document.application.model;

import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reference-only result and safe filtering statistics for document evidence candidates. */
public record DocumentEvidenceApplyResult(
        List<UUID> appliedEvidenceIds,
        int rejectedCount,
        Map<DocumentEvidenceRejectionReason, Integer> rejectionReasonCounts,
        Map<ExperienceMatchKind, Integer> experienceMatchCounts) {

    public DocumentEvidenceApplyResult {
        appliedEvidenceIds = appliedEvidenceIds == null
                ? List.of()
                : List.copyOf(appliedEvidenceIds);
        if (rejectedCount < 0) {
            throw new IllegalArgumentException("rejected count is invalid");
        }
        EnumMap<DocumentEvidenceRejectionReason, Integer> safeCounts =
                new EnumMap<>(DocumentEvidenceRejectionReason.class);
        if (rejectionReasonCounts != null) {
            rejectionReasonCounts.forEach((reason, count) -> {
                if (reason == null || count == null || count < 1) {
                    throw new IllegalArgumentException("rejection reason count is invalid");
                }
                safeCounts.put(reason, count);
            });
        }
        if (safeCounts.values().stream().mapToInt(Integer::intValue).sum() != rejectedCount) {
            throw new IllegalArgumentException("rejection reason total is invalid");
        }
        rejectionReasonCounts = Map.copyOf(safeCounts);
        EnumMap<ExperienceMatchKind, Integer> safeMatchCounts =
                new EnumMap<>(ExperienceMatchKind.class);
        if (experienceMatchCounts != null) {
            experienceMatchCounts.forEach((kind, count) -> {
                if (kind == null || count == null || count < 1) {
                    throw new IllegalArgumentException("experience match count is invalid");
                }
                safeMatchCounts.put(kind, count);
            });
        }
        if (safeMatchCounts.values().stream().mapToInt(Integer::intValue).sum()
                != appliedEvidenceIds.size()) {
            throw new IllegalArgumentException("experience match total is invalid");
        }
        experienceMatchCounts = Map.copyOf(safeMatchCounts);
    }

    public DocumentEvidenceApplyResult(
            List<UUID> appliedEvidenceIds,
            int rejectedCount,
            Map<DocumentEvidenceRejectionReason, Integer> rejectionReasonCounts) {
        this(
                appliedEvidenceIds,
                rejectedCount,
                rejectionReasonCounts,
                appliedEvidenceIds == null || appliedEvidenceIds.isEmpty()
                        ? Map.of()
                        : Map.of(ExperienceMatchKind.NEW, appliedEvidenceIds.size()));
    }

    public int appliedCount() {
        return appliedEvidenceIds.size();
    }

    public int candidateCount() {
        return appliedCount() + rejectedCount;
    }
}
