package com.hiresemble.research.application.model;

import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.ResearchRunStatus;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
import com.hiresemble.research.domain.SourceCoverage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResearchModels {

    private ResearchModels() {}

    public record ResearchRunRow(
            UUID id,
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            UUID retryOfResearchRunId,
            ResearchQuality researchQuality,
            ResearchRunStatus status,
            SourceCoverage sourceCoverage,
            List<String> missingCoverageTopics,
            String summary,
            UUID agentRunId,
            boolean retryable,
            String safeErrorCode,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant updatedAt) {
        public ResearchRunRow {
            missingCoverageTopics = List.copyOf(missingCoverageTopics);
        }
    }

    public record ResearchSourceRow(
            UUID id,
            UUID researchRunId,
            ResearchTopic topic,
            String sourceUrl,
            String title,
            ResearchSourceType sourceType,
            Instant publishedAt,
            Instant retrievedAt,
            String snippet,
            String reliabilityNotice,
            int providerRank,
            String contentHash) {}

    public record TopicPlan(
            UUID id,
            ResearchTopic topic,
            String queryText,
            int topicOrder) {}

    public record SourceCandidate(
            UUID id,
            ResearchTopic topic,
            List<ResearchTopic> topics,
            String sourceUrl,
            String title,
            ResearchSourceType sourceType,
            Instant publishedAt,
            Instant retrievedAt,
            String snippet,
            String reliabilityNotice,
            int providerRank,
            String contentHash) {
        public SourceCandidate {
            topics = List.copyOf(topics);
        }
    }

    public record ResearchResult(
            List<TopicPlan> topics,
            List<SourceCandidate> sources,
            SourceCoverage coverage,
            List<String> missingCoverageTopics,
            String summary) {
        public ResearchResult {
            topics = List.copyOf(topics);
            sources = List.copyOf(sources);
            missingCoverageTopics = List.copyOf(missingCoverageTopics);
        }
    }

    public record AcceptedResearchRetry(
            UUID questionSetId,
            UUID researchRunId,
            UUID agentRunId,
            UUID retryOfResearchRunId) {}

    public record PageSlice<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }
}
