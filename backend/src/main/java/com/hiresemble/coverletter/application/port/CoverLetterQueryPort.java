package com.hiresemble.coverletter.application.port;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.coverletter.application.model.CoverLetterModels.CandidateChunk;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceSourceExcerpt;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.WritingInsights;
import java.util.List;
import java.util.UUID;

public interface CoverLetterQueryPort {

    GenerationSnapshot loadGenerationSnapshot(
            UUID userId,
            UUID coverLetterId,
            long expectedCoverLetterVersion,
            List<UUID> questionIds,
            List<UUID> preferredEvidenceIds,
            boolean avoidExperienceDuplication,
            AiQualityMode qualityMode,
            String expectedSnapshotHash);

    GenerationSnapshot loadGenerationRetrySnapshot(
            UUID userId, UUID agentRunId, String expectedSnapshotHash);

    default GenerationSnapshot loadGenerationSnapshotByModel(
            UUID userId,
            UUID coverLetterId,
            long expectedCoverLetterVersion,
            List<UUID> questionIds,
            List<UUID> preferredEvidenceIds,
            boolean avoidExperienceDuplication,
            String model,
            String expectedSnapshotHash) {
        throw new UnsupportedOperationException("exact model generation is not configured");
    }

    VerificationSnapshot loadVerificationSnapshot(
            UUID userId,
            UUID answerVersionId,
            AiQualityMode qualityMode,
            String expectedSnapshotHash);

    default VerificationSnapshot loadVerificationSnapshotV2(
            UUID userId,
            UUID answerVersionId,
            AiQualityMode qualityMode,
            String expectedSnapshotHash) {
        return loadVerificationSnapshot(
                userId, answerVersionId, qualityMode, expectedSnapshotHash);
    }

    default VerificationSnapshot loadVerificationSnapshotByModel(
            UUID userId,
            UUID answerVersionId,
            String model,
            String expectedSnapshotHash) {
        throw new UnsupportedOperationException("exact model verification is not configured");
    }

    VerificationSnapshot loadVerificationRetrySnapshot(
            UUID userId, UUID agentRunId, String expectedSnapshotHash);

    default VerificationSnapshot loadVerificationRetrySnapshotV2(
            UUID userId, UUID agentRunId, String expectedSnapshotHash) {
        return loadVerificationRetrySnapshot(userId, agentRunId, expectedSnapshotHash);
    }

    List<CandidateChunk> searchEvidenceCandidates(
            UUID userId, List<Double> queryVector, int limit);

    default WritingInsights loadWritingInsights(
            UUID userId, UUID jobId, UUID analysisId, UUID coverLetterId) {
        throw new UnsupportedOperationException("writing insights are not configured");
    }

    default List<EvidenceSourceExcerpt> findEvidenceSourceExcerpts(
            UUID userId, List<UUID> evidenceIds, int limit) {
        throw new UnsupportedOperationException("evidence source excerpts are not configured");
    }
}
