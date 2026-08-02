package com.hiresemble.dashboard.infrastructure;

import com.hiresemble.dashboard.application.DashboardModels.CareerGuidePost;
import com.hiresemble.dashboard.application.DashboardModels.DeadlineJob;
import com.hiresemble.dashboard.application.DashboardModels.EducationSnapshot;
import com.hiresemble.job.domain.JobStatus;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class DashboardReadStore {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public DashboardReadStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<ProfileProjection> profile(UUID userId) {
        return jdbc.sql("""
                        SELECT u.display_name,
                               p.legal_name,
                               p.desired_roles::text AS desired_roles,
                               p.desired_industries::text AS desired_industries,
                               p.desired_locations::text AS desired_locations,
                               e.school_name,
                               e.major,
                               e.degree,
                               e.education_level,
                               e.education_status
                        FROM users u
                        JOIN user_profiles p ON p.user_id = u.id
                        LEFT JOIN educations e
                          ON e.user_id = u.id
                         AND e.is_primary
                         AND e.deleted_at IS NULL
                        WHERE u.id = :userId
                          AND u.status = 'ACTIVE'
                        """)
                .param("userId", userId)
                .query((resultSet, rowNumber) -> mapProfile(resultSet))
                .optional();
    }

    public SummaryCounts summaryCounts(UUID userId) {
        return jdbc.sql("""
                        SELECT
                          (SELECT count(*) FROM documents
                           WHERE user_id = :userId AND deleted_at IS NULL) AS document_registered_count,
                          (SELECT count(*) FROM documents
                           WHERE user_id = :userId AND deleted_at IS NULL
                             AND (parse_status IN ('UPLOADED', 'PARSING')
                                  OR evidence_extraction_status IN ('QUEUED', 'EXTRACTING'))) AS document_processing_count,
                          (SELECT count(*) FROM documents
                           WHERE user_id = :userId AND deleted_at IS NULL
                             AND (parse_status IN ('NEEDS_MANUAL_TEXT', 'FAILED')
                                  OR evidence_extraction_status = 'FAILED')) AS document_needs_action_count,
                          (SELECT count(*) FROM job_postings
                           WHERE user_id = :userId AND deleted_at IS NULL) AS job_registered_count,
                          (SELECT count(*) FROM job_postings
                           WHERE user_id = :userId AND deleted_at IS NULL
                             AND status = 'IN_PROGRESS') AS job_preparing_count,
                          (SELECT count(*) FROM job_postings
                           WHERE user_id = :userId AND deleted_at IS NULL
                             AND status = 'SUBMITTED') AS job_submitted_count,
                          (SELECT count(*) FROM agent_runs
                           WHERE user_id = :userId AND deleted_at IS NULL
                             AND status IN ('QUEUED', 'RUNNING', 'WAITING_USER')) AS active_run_count
                        """)
                .param("userId", userId)
                .query((resultSet, rowNumber) -> new SummaryCounts(
                        resultSet.getLong("document_registered_count"),
                        resultSet.getLong("document_processing_count"),
                        resultSet.getLong("document_needs_action_count"),
                        resultSet.getLong("job_registered_count"),
                        resultSet.getLong("job_preparing_count"),
                        resultSet.getLong("job_submitted_count"),
                        resultSet.getLong("active_run_count")))
                .single();
    }

    public List<DeadlineJob> deadlines(UUID userId, Instant fromInclusive, Instant toExclusive) {
        return jdbc.sql("""
                        SELECT j.id,
                               c.display_name AS company_name,
                               j.title,
                               j.position_name,
                               j.status,
                               j.deadline_at
                        FROM job_postings j
                        LEFT JOIN companies c ON c.id = j.company_id
                        WHERE j.user_id = :userId
                          AND j.deleted_at IS NULL
                          AND j.status <> 'CLOSED'
                          AND j.deadline_at >= :fromInclusive
                          AND j.deadline_at < :toExclusive
                        ORDER BY j.deadline_at ASC, j.id ASC
                        """)
                .param("userId", userId)
                .param("fromInclusive", utc(fromInclusive))
                .param("toExclusive", utc(toExclusive))
                .query((resultSet, rowNumber) -> new DeadlineJob(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("company_name"),
                        resultSet.getString("title"),
                        resultSet.getString("position_name"),
                        JobStatus.valueOf(resultSet.getString("status")),
                        instant(resultSet, "deadline_at")))
                .list();
    }

    public List<CareerGuidePost> publishedGuides(Instant publishedAtOrBefore) {
        return jdbc.sql("""
                        SELECT id, status, display_order, category, title, summary, body,
                               published_at, version
                        FROM career_guide_posts
                        WHERE status = 'PUBLISHED'
                          AND published_at <= :publishedAtOrBefore
                        ORDER BY display_order ASC, published_at DESC, id ASC
                        """)
                .param("publishedAtOrBefore", utc(publishedAtOrBefore))
                .query((resultSet, rowNumber) -> new CareerGuidePost(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getInt("display_order"),
                        resultSet.getString("category"),
                        resultSet.getString("title"),
                        resultSet.getString("summary"),
                        resultSet.getString("body"),
                        instant(resultSet, "published_at"),
                        resultSet.getLong("version")))
                .list();
    }

    private ProfileProjection mapProfile(ResultSet resultSet) throws SQLException {
        EducationSnapshot education = resultSet.getString("school_name") == null
                ? null
                : new EducationSnapshot(
                        resultSet.getString("school_name"),
                        resultSet.getString("major"),
                        resultSet.getString("degree"),
                        EducationLevel.valueOf(resultSet.getString("education_level")),
                        EducationStatus.valueOf(resultSet.getString("education_status")));
        return new ProfileProjection(
                resultSet.getString("display_name"),
                resultSet.getString("legal_name"),
                strings(resultSet.getString("desired_roles")),
                strings(resultSet.getString("desired_industries")),
                strings(resultSet.getString("desired_locations")),
                education);
    }

    private List<String> strings(String value) throws SQLException {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JacksonException exception) {
            throw new SQLException("Invalid profile list projection", exception);
        }
    }

    private static OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record ProfileProjection(
            String displayName,
            String legalName,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations,
            EducationSnapshot primaryEducation) {}

    public record SummaryCounts(
            long documentRegisteredCount,
            long documentProcessingCount,
            long documentNeedsActionCount,
            long jobRegisteredCount,
            long jobPreparingCount,
            long jobSubmittedCount,
            long activeRunCount) {}
}
