package com.hiresemble.job.application.model;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.job.domain.JobAutoAnalysisStatus;
import java.time.Instant;
import java.util.UUID;

public final class JobAutoAnalysisModels {

    private JobAutoAnalysisModels() {}

    public enum AutomaticAnalysisState {
        WAITING_FOR_CONTENT,
        NOT_REQUESTED,
        PENDING,
        LAUNCHED,
        BLOCKED,
        SUPERSEDED
    }

    public record AutoAnalysisRequest(
            UUID id,
            UUID userId,
            UUID jobId,
            long jobVersion,
            String jobContentHash,
            AiQualityMode qualityMode,
            JobAutoAnalysisStatus status,
            int attemptCount,
            UUID claimToken,
            Instant leaseExpiresAt,
            Instant nextAttemptAt,
            UUID agentRunId,
            SafeError safeError,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {}

    public record AutomaticAnalysisProjection(
            AutomaticAnalysisState state,
            AiQualityMode qualityMode,
            UUID agentRunId,
            SafeError safeError) {}

    public record AutoAnalysisRequestedEvent(UUID requestId) {}
}
