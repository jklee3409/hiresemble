package com.hiresemble.research.infrastructure;

import com.hiresemble.research.application.model.ResearchModels.PageSlice;
import com.hiresemble.research.application.model.ResearchModels.ResearchResult;
import com.hiresemble.research.application.model.ResearchModels.ResearchRunRow;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import com.hiresemble.research.application.model.ResearchModels.SourceCandidate;
import com.hiresemble.research.application.model.ResearchModels.TopicPlan;
import com.hiresemble.research.domain.ResearchQuality;
import com.hiresemble.research.domain.ResearchRunStatus;
import com.hiresemble.research.domain.ResearchSourceType;
import com.hiresemble.research.domain.ResearchTopic;
import com.hiresemble.research.domain.SourceCoverage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ResearchStore {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ResearchStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public ResearchRunRow createQueued(
            UUID id,
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            UUID retryOfResearchRunId,
            ResearchQuality researchQuality,
            UUID agentRunId,
            Instant now) {
        return jdbcClient.sql("""
                        INSERT INTO research_runs (
                            id,user_id,job_posting_id,cover_letter_id,retry_of_research_run_id,
                            research_quality,status,source_coverage,missing_coverage_topics,
                            summary,agent_run_id,retryable,safe_error_code,
                            created_at,started_at,completed_at,updated_at
                        ) VALUES (
                            :id,:userId,:jobId,:coverLetterId,:retryOf,
                            :quality,'QUEUED',NULL,'[]'::jsonb,
                            NULL,:agentRunId,false,NULL,
                            :now,NULL,NULL,:now
                        )
                        RETURNING *, missing_coverage_topics::text AS missing_topics_text
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("jobId", jobId)
                .param("coverLetterId", coverLetterId)
                .param("retryOf", retryOfResearchRunId)
                .param("quality", researchQuality.name())
                .param("agentRunId", agentRunId)
                .param("now", utc(now))
                .query(this::run)
                .single();
    }

    public void attachSecondaryRunLink(
            UUID userId, UUID agentRunId, UUID researchRunId, Instant now) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,research_run_id,
                            primary_resource,created_at
                        ) VALUES (
                            :id,:userId,:agentRunId,'RESEARCH_RUN',:researchRunId,false,:now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .param("researchRunId", researchRunId)
                .param("now", utc(now))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("research Agent Run link was not created");
        }
    }

    public java.util.Optional<ResearchRunRow> findRun(UUID userId, UUID researchRunId) {
        return jdbcClient.sql("""
                        SELECT *, missing_coverage_topics::text AS missing_topics_text
                        FROM research_runs
                        WHERE user_id=:userId AND id=:researchRunId
                        """)
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .query(this::run)
                .optional();
    }

    public java.util.Optional<ResearchRunRow> findByAgentRun(UUID userId, UUID agentRunId) {
        return jdbcClient.sql("""
                        SELECT *, missing_coverage_topics::text AS missing_topics_text
                        FROM research_runs
                        WHERE user_id=:userId AND agent_run_id=:agentRunId
                        """)
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .query(this::run)
                .optional();
    }

    public void markRunning(UUID userId, UUID researchRunId, Instant now) {
        jdbcClient.sql("""
                        UPDATE research_runs
                        SET status='RUNNING',started_at=COALESCE(started_at,:now),updated_at=:now
                        WHERE user_id=:userId AND id=:researchRunId AND status='QUEUED'
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .update();
    }

    public void persistResult(
            UUID userId, UUID researchRunId, ResearchResult result, Instant now) {
        for (TopicPlan topic : result.topics()) {
            jdbcClient.sql("""
                            INSERT INTO research_topics (
                                id,user_id,research_run_id,topic,query_text,topic_order,created_at
                            ) VALUES (
                                :id,:userId,:researchRunId,:topic,:queryText,:topicOrder,:now
                            )
                            ON CONFLICT (user_id,research_run_id,topic,query_text) DO NOTHING
                            """)
                    .param("id", topic.id())
                    .param("userId", userId)
                    .param("researchRunId", researchRunId)
                    .param("topic", topic.topic().name())
                    .param("queryText", topic.queryText())
                    .param("topicOrder", topic.topicOrder())
                    .param("now", utc(now))
                    .update();
        }
        for (SourceCandidate source : result.sources()) {
            int inserted = jdbcClient.sql("""
                            INSERT INTO research_sources (
                                id,user_id,research_run_id,source_url,title,source_type,
                                published_at,retrieved_at,snippet,reliability_notice,
                                provider_rank,content_hash
                            ) VALUES (
                                :id,:userId,:researchRunId,:url,:title,:type,
                                :publishedAt,:retrievedAt,:snippet,:notice,
                                :rank,:hash
                            )
                            ON CONFLICT (user_id,research_run_id,source_url) DO NOTHING
                            """)
                    .param("id", source.id())
                    .param("userId", userId)
                    .param("researchRunId", researchRunId)
                    .param("url", source.sourceUrl())
                    .param("title", source.title())
                    .param("type", source.sourceType().name())
                    .param("publishedAt", source.publishedAt() == null
                            ? null
                            : utc(source.publishedAt()))
                    .param("retrievedAt", utc(source.retrievedAt()))
                    .param("snippet", source.snippet())
                    .param("notice", source.reliabilityNotice())
                    .param("rank", source.providerRank())
                    .param("hash", source.contentHash())
                    .update();
            UUID sourceId = source.id();
            if (inserted == 0) {
                sourceId = jdbcClient.sql("""
                                SELECT id FROM research_sources
                                WHERE user_id=:userId AND research_run_id=:researchRunId
                                  AND source_url=:url
                                """)
                        .param("userId", userId)
                        .param("researchRunId", researchRunId)
                        .param("url", source.sourceUrl())
                        .query(UUID.class)
                        .single();
            }
            for (ResearchTopic linkedTopic : source.topics()) {
                UUID topicId = result.topics().stream()
                        .filter(topic -> topic.topic() == linkedTopic)
                        .findFirst()
                        .map(TopicPlan::id)
                        .orElseThrow();
                jdbcClient.sql("""
                                INSERT INTO research_topic_source_links (
                                    id,user_id,research_topic_id,research_source_id,is_primary,created_at
                                ) VALUES (
                                    :id,:userId,:topicId,:sourceId,:primary,:now
                                )
                                ON CONFLICT (user_id,research_topic_id,research_source_id) DO NOTHING
                                """)
                        .param("id", UUID.randomUUID())
                        .param("userId", userId)
                        .param("topicId", topicId)
                        .param("sourceId", sourceId)
                        .param("primary", linkedTopic == source.topic())
                        .param("now", utc(now))
                        .update();
            }
        }
        int updated = jdbcClient.sql("""
                        UPDATE research_runs
                        SET status='SUCCEEDED',source_coverage=:coverage,
                            missing_coverage_topics=CAST(:missing AS jsonb),summary=:summary,
                            retryable=false,safe_error_code=NULL,
                            started_at=COALESCE(started_at,:now),completed_at=:now,updated_at=:now
                        WHERE user_id=:userId AND id=:researchRunId
                          AND status IN ('QUEUED','RUNNING')
                        """)
                .param("coverage", result.coverage().name())
                .param("missing", json(result.missingCoverageTopics()))
                .param("summary", result.summary())
                .param("now", utc(now))
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("research result could not be applied");
        }
    }

    public void fail(
            UUID userId,
            UUID researchRunId,
            String safeErrorCode,
            boolean retryable,
            Instant now) {
        jdbcClient.sql("""
                        UPDATE research_runs
                        SET status='FAILED',source_coverage=NULL,missing_coverage_topics='[]'::jsonb,
                            summary=NULL,retryable=:retryable,safe_error_code=:errorCode,
                            started_at=COALESCE(started_at,:now),completed_at=:now,updated_at=:now
                        WHERE user_id=:userId AND id=:researchRunId
                          AND status IN ('QUEUED','RUNNING')
                        """)
                .param("retryable", retryable)
                .param("errorCode", safeErrorCode)
                .param("now", utc(now))
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .update();
    }

    public void cancelByAgentRun(UUID userId, UUID agentRunId, Instant now) {
        jdbcClient.sql("""
                        UPDATE research_runs
                        SET status='CANCELLED',source_coverage=NULL,
                            missing_coverage_topics='[]'::jsonb,summary=NULL,
                            retryable=false,safe_error_code=NULL,
                            started_at=COALESCE(started_at,:now),completed_at=:now,updated_at=:now
                        WHERE user_id=:userId AND agent_run_id=:agentRunId
                          AND status IN ('QUEUED','RUNNING')
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .update();
    }

    public PageSlice<ResearchSourceRow> listSources(
            UUID userId,
            UUID researchRunId,
            ResearchTopic topic,
            ResearchSourceType sourceType,
            int page,
            int size,
            String order) {
        String filters = """
                  AND (
                    CAST(:topic AS varchar) IS NULL
                    OR topic.topic=CAST(:topic AS varchar)
                  )
                  AND (
                    CAST(:sourceType AS varchar) IS NULL
                    OR source.source_type=CAST(:sourceType AS varchar)
                  )
                """;
        long total = jdbcClient.sql("""
                        SELECT count(*)
                        FROM research_sources source
                        JOIN research_topic_source_links link
                          ON link.user_id=source.user_id
                         AND link.research_source_id=source.id
                         AND link.is_primary
                        JOIN research_topics topic
                          ON topic.user_id=link.user_id
                         AND topic.id=link.research_topic_id
                        WHERE source.user_id=:userId
                          AND source.research_run_id=:researchRunId
                        """ + filters)
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .param("topic", topic == null ? null : topic.name())
                .param("sourceType", sourceType == null ? null : sourceType.name())
                .query(Long.class)
                .single();
        List<ResearchSourceRow> items = jdbcClient.sql("""
                        SELECT source.*,topic.topic AS primary_topic
                        FROM research_sources source
                        JOIN research_topic_source_links link
                          ON link.user_id=source.user_id
                         AND link.research_source_id=source.id
                         AND link.is_primary
                        JOIN research_topics topic
                          ON topic.user_id=link.user_id
                         AND topic.id=link.research_topic_id
                        WHERE source.user_id=:userId
                          AND source.research_run_id=:researchRunId
                        """ + filters + " ORDER BY " + order + " LIMIT :size OFFSET :offset")
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .param("topic", topic == null ? null : topic.name())
                .param("sourceType", sourceType == null ? null : sourceType.name())
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::source)
                .list();
        int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageSlice<>(items, page, size, total, pages);
    }

    public List<ResearchSourceRow> allSources(UUID userId, UUID researchRunId) {
        return listSources(
                        userId,
                        researchRunId,
                        null,
                        null,
                        0,
                        100,
                        "source.provider_rank ASC,source.id ASC")
                .items();
    }

    private ResearchRunRow run(ResultSet rs, int row) throws SQLException {
        return new ResearchRunRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("job_posting_id", UUID.class),
                rs.getObject("cover_letter_id", UUID.class),
                rs.getObject("retry_of_research_run_id", UUID.class),
                ResearchQuality.valueOf(rs.getString("research_quality")),
                ResearchRunStatus.valueOf(rs.getString("status")),
                enumOrNull(SourceCoverage.class, rs.getString("source_coverage")),
                strings(rs.getString("missing_topics_text")),
                rs.getString("summary"),
                rs.getObject("agent_run_id", UUID.class),
                rs.getBoolean("retryable"),
                rs.getString("safe_error_code"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                instant(rs, "updated_at"));
    }

    private ResearchSourceRow source(ResultSet rs, int row) throws SQLException {
        return new ResearchSourceRow(
                rs.getObject("id", UUID.class),
                rs.getObject("research_run_id", UUID.class),
                ResearchTopic.valueOf(rs.getString("primary_topic")),
                rs.getString("source_url"),
                rs.getString("title"),
                ResearchSourceType.valueOf(rs.getString("source_type")),
                instant(rs, "published_at"),
                instant(rs, "retrieved_at"),
                rs.getString("snippet"),
                rs.getString("reliability_notice"),
                rs.getInt("provider_rank"),
                rs.getString("content_hash"));
    }

    private List<String> strings(String json) {
        try {
            return objectMapper.readValue(json, STRINGS);
        } catch (JacksonException exception) {
            throw new IllegalStateException("research JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("research JSON could not be written", exception);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
