package com.hiresemble.coverletter.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceReference;
import com.hiresemble.coverletter.application.model.CoverLetterModels.EvidenceUse;
import com.hiresemble.coverletter.application.model.CoverLetterModels.HistoricalEvidence;
import com.hiresemble.coverletter.application.model.CoverLetterModels.Verification;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationIssue;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerifiedClaim;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterEvidenceUsageType;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CoverLetterStore {

    private static final TypeReference<List<VerificationIssue>> ISSUES =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<VerifiedClaim>> CLAIMS =
            new TypeReference<>() {};
    private static final String COVER_SELECT = """
            cl.*, j.title AS job_title, j.position_name,
            c.display_name AS company_name
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public CoverLetterStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public CoverRow create(
            UUID userId, UUID jobId, String title, Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                            INSERT INTO cover_letters (
                                id,user_id,job_posting_id,title,status,version,
                                created_at,updated_at
                            ) VALUES (
                                :id,:userId,:jobId,:title,'DRAFT',0,:now,:now
                            )
                            """)
                    .param("id", id)
                    .param("userId", userId)
                    .param("jobId", jobId)
                    .param("title", title)
                    .param("now", utc(now))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            if (causedByConstraint(
                    exception, "23505", "cover_letters_active_job_uk")) {
                throw new BusinessException(
                        ErrorCode.ACTIVE_COVER_LETTER_EXISTS, exception);
            }
            throw exception;
        }
        return find(userId, id).orElseThrow();
    }

    public Optional<CoverRow> find(UUID userId, UUID coverLetterId) {
        return coverQuery(
                "WHERE cl.user_id=:userId AND cl.id=:id AND cl.deleted_at IS NULL",
                Map.of("userId", userId, "id", coverLetterId),
                false);
    }

    public Optional<CoverRow> lock(UUID userId, UUID coverLetterId) {
        return coverQuery(
                "WHERE cl.user_id=:userId AND cl.id=:id AND cl.deleted_at IS NULL",
                Map.of("userId", userId, "id", coverLetterId),
                true);
    }

    public Optional<CoverRow> findActiveForJob(UUID userId, UUID jobId) {
        return coverQuery(
                """
                WHERE cl.user_id=:userId AND cl.job_posting_id=:jobId
                  AND cl.deleted_at IS NULL AND cl.status IN ('DRAFT','FINALIZED')
                """,
                Map.of("userId", userId, "jobId", jobId),
                false);
    }

    public boolean existsActiveForJob(UUID userId, UUID jobId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM cover_letters
                            WHERE user_id=:userId AND job_posting_id=:jobId
                              AND deleted_at IS NULL
                              AND status IN ('DRAFT','FINALIZED')
                        )
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query(Boolean.class)
                .single();
    }

    public List<CoverRow> list(
            UUID userId,
            UUID jobId,
            CoverLetterStatus status,
            String query,
            int page,
            int size,
            String order) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("limit", size);
        params.put("offset", page * size);
        StringBuilder where = new StringBuilder(
                "WHERE cl.user_id=:userId AND cl.deleted_at IS NULL");
        if (jobId != null) {
            where.append(" AND cl.job_posting_id=:jobId");
            params.put("jobId", jobId);
        }
        if (status != null) {
            where.append(" AND cl.status=:status");
            params.put("status", status.name());
        }
        if (query != null && !query.isBlank()) {
            where.append("""
                     AND (
                        lower(cl.title) LIKE :query
                        OR lower(COALESCE(c.display_name,'')) LIKE :query
                        OR lower(COALESCE(j.position_name,'')) LIKE :query
                        OR lower(COALESCE(j.title,'')) LIKE :query
                     )
                    """);
            params.put("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        return jdbc.sql("""
                        SELECT %s
                        FROM cover_letters cl
                        JOIN job_postings j
                          ON j.user_id=cl.user_id AND j.id=cl.job_posting_id
                        LEFT JOIN companies c ON c.id=j.company_id
                        %s
                        ORDER BY %s
                        LIMIT :limit OFFSET :offset
                        """.formatted(COVER_SELECT, where, order))
                .params(params)
                .query(this::cover)
                .list();
    }

    public long count(
            UUID userId, UUID jobId, CoverLetterStatus status, String query) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        StringBuilder where = new StringBuilder(
                "WHERE cl.user_id=:userId AND cl.deleted_at IS NULL");
        if (jobId != null) {
            where.append(" AND cl.job_posting_id=:jobId");
            params.put("jobId", jobId);
        }
        if (status != null) {
            where.append(" AND cl.status=:status");
            params.put("status", status.name());
        }
        if (query != null && !query.isBlank()) {
            where.append("""
                     AND (
                        lower(cl.title) LIKE :query
                        OR lower(COALESCE(c.display_name,'')) LIKE :query
                        OR lower(COALESCE(j.position_name,'')) LIKE :query
                        OR lower(COALESCE(j.title,'')) LIKE :query
                     )
                    """);
            params.put("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        return jdbc.sql("""
                        SELECT count(*)
                        FROM cover_letters cl
                        JOIN job_postings j
                          ON j.user_id=cl.user_id AND j.id=cl.job_posting_id
                        LEFT JOIN companies c ON c.id=j.company_id
                        %s
                        """.formatted(where))
                .params(params)
                .query(Long.class)
                .single();
    }

    public CoverRow updateTitle(
            UUID userId, UUID coverLetterId, long expectedVersion, String title, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE cover_letters
                        SET title=:title,
                            status=CASE WHEN status='FINALIZED' THEN 'DRAFT' ELSE status END,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                          AND version=:version AND status <> 'ARCHIVED'
                        """)
                .param("title", title)
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", coverLetterId)
                .param("version", expectedVersion)
                .update();
        if (updated != 1) {
            throwMutationFailure(userId, coverLetterId, expectedVersion);
        }
        return find(userId, coverLetterId).orElseThrow();
    }

    public void touchDraft(UUID userId, UUID coverLetterId, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE cover_letters
                        SET status=CASE WHEN status='FINALIZED' THEN 'DRAFT' ELSE status END,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                          AND status <> 'ARCHIVED'
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", coverLetterId)
                .update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.COVER_LETTER_ARCHIVED);
        }
    }

    public CoverRow archive(
            UUID userId, UUID coverLetterId, long expectedVersion, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE cover_letters
                        SET status='ARCHIVED',archived_at=:now,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                          AND version=:version AND status IN ('DRAFT','FINALIZED')
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", coverLetterId)
                .param("version", expectedVersion)
                .update();
        if (updated != 1) {
            throwMutationFailure(userId, coverLetterId, expectedVersion);
        }
        return find(userId, coverLetterId).orElseThrow();
    }

    public CoverRow unarchive(
            UUID userId, UUID coverLetterId, long expectedVersion, Instant now) {
        CoverRow current = lock(userId, coverLetterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (current.version() != expectedVersion) {
            throw versionConflict("version");
        }
        if (current.status() != CoverLetterStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        if (existsActiveForJob(userId, current.jobId())) {
            throw new BusinessException(ErrorCode.ACTIVE_COVER_LETTER_EXISTS);
        }
        try {
            int updated = jdbc.sql("""
                            UPDATE cover_letters
                            SET status='DRAFT',archived_at=NULL,
                                version=version+1,updated_at=:now
                            WHERE user_id=:userId AND id=:id AND version=:version
                            """)
                    .param("now", utc(now))
                    .param("userId", userId)
                    .param("id", coverLetterId)
                    .param("version", expectedVersion)
                    .update();
            if (updated != 1) {
                throwMutationFailure(userId, coverLetterId, expectedVersion);
            }
        } catch (DataIntegrityViolationException exception) {
            if (causedByConstraint(
                    exception, "23505", "cover_letters_active_job_uk")) {
                throw new BusinessException(
                        ErrorCode.ACTIVE_COVER_LETTER_EXISTS, exception);
            }
            throw exception;
        }
        return find(userId, coverLetterId).orElseThrow();
    }

    public CoverRow finalizeCover(
            UUID userId, UUID coverLetterId, long expectedVersion, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE cover_letters
                        SET status='FINALIZED',finalized_at=:now,
                            version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                          AND version=:version AND status='DRAFT'
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", coverLetterId)
                .param("version", expectedVersion)
                .update();
        if (updated != 1) {
            throwMutationFailure(userId, coverLetterId, expectedVersion);
        }
        return find(userId, coverLetterId).orElseThrow();
    }

    public QuestionRow insertQuestion(
            UUID userId,
            UUID coverLetterId,
            int order,
            String text,
            Integer maxLength,
            String memo,
            Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                            INSERT INTO cover_letter_questions (
                                id,user_id,cover_letter_id,question_order,question_text,
                                max_length,memo,version,created_at,updated_at
                            ) VALUES (
                                :id,:userId,:coverId,:questionOrder,:questionText,
                                :maxLength,:memo,0,:now,:now
                            )
                            """)
                    .param("id", id)
                    .param("userId", userId)
                    .param("coverId", coverLetterId)
                    .param("questionOrder", order)
                    .param("questionText", text)
                    .param("maxLength", maxLength)
                    .param("memo", memo)
                    .param("now", utc(now))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return findQuestion(userId, id, true).orElseThrow();
    }

    public Optional<QuestionRow> findQuestion(
            UUID userId, UUID questionId, boolean includeDeleted) {
        String deleted = includeDeleted ? "" : " AND deleted_at IS NULL";
        return jdbc.sql("""
                        SELECT * FROM cover_letter_questions
                        WHERE user_id=:userId AND id=:id%s
                        """.formatted(deleted))
                .param("userId", userId)
                .param("id", questionId)
                .query(this::question)
                .optional();
    }

    public Optional<QuestionRow> lockQuestion(UUID userId, UUID questionId) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_questions
                        WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                        FOR UPDATE
                        """)
                .param("userId", userId)
                .param("id", questionId)
                .query(this::question)
                .optional();
    }

    public List<QuestionRow> findQuestions(
            UUID userId, UUID coverLetterId, boolean includeDeleted) {
        String deleted = includeDeleted ? "" : " AND deleted_at IS NULL";
        return jdbc.sql("""
                        SELECT * FROM cover_letter_questions
                        WHERE user_id=:userId AND cover_letter_id=:coverId%s
                        ORDER BY question_order,created_at,id
                        """.formatted(deleted))
                .param("userId", userId)
                .param("coverId", coverLetterId)
                .query(this::question)
                .list();
    }

    public QuestionRow updateQuestion(
            UUID userId,
            UUID questionId,
            long expectedVersion,
            int order,
            String text,
            Integer maxLength,
            String memo,
            Instant now) {
        try {
            int updated = jdbc.sql("""
                            UPDATE cover_letter_questions
                            SET question_order=:questionOrder,question_text=:questionText,
                                max_length=:maxLength,memo=:memo,
                                version=version+1,updated_at=:now
                            WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                              AND version=:version
                            """)
                    .param("questionOrder", order)
                    .param("questionText", text)
                    .param("maxLength", maxLength)
                    .param("memo", memo)
                    .param("now", utc(now))
                    .param("userId", userId)
                    .param("id", questionId)
                    .param("version", expectedVersion)
                    .update();
            if (updated != 1) {
                throw questionMutationFailure(userId, questionId, expectedVersion);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return findQuestion(userId, questionId, false).orElseThrow();
    }

    public void deleteQuestion(
            UUID userId, UUID questionId, long expectedVersion, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE cover_letter_questions
                        SET deleted_at=:now,version=version+1,updated_at=:now
                        WHERE user_id=:userId AND id=:id AND deleted_at IS NULL
                          AND version=:version
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("id", questionId)
                .param("version", expectedVersion)
                .update();
        if (updated != 1) {
            throw questionMutationFailure(userId, questionId, expectedVersion);
        }
    }

    public void reorderQuestions(
            UUID userId, UUID coverLetterId, List<UUID> questionIds, Instant now) {
        jdbc.sql("""
                        UPDATE cover_letter_questions
                        SET deleted_at=:now,updated_at=:now
                        WHERE user_id=:userId AND cover_letter_id=:coverId
                          AND deleted_at IS NULL
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("coverId", coverLetterId)
                .update();
        for (int index = 0; index < questionIds.size(); index++) {
            int updated = jdbc.sql("""
                            UPDATE cover_letter_questions
                            SET question_order=:questionOrder,deleted_at=NULL,
                                version=version+1,updated_at=:now
                            WHERE user_id=:userId AND cover_letter_id=:coverId
                              AND id=:id AND deleted_at=:now
                            """)
                    .param("questionOrder", index + 1)
                    .param("now", utc(now))
                    .param("userId", userId)
                    .param("coverId", coverLetterId)
                    .param("id", questionIds.get(index))
                    .update();
            if (updated != 1) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
        }
    }

    public Optional<AnswerVersion> findAnswer(UUID userId, UUID answerVersionId) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_answer_versions
                        WHERE user_id=:userId AND id=:id
                        """)
                .param("userId", userId)
                .param("id", answerVersionId)
                .query(this::answer)
                .optional();
    }

    public Optional<AnswerVersion> currentAnswer(UUID userId, UUID questionId) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_answer_versions
                        WHERE user_id=:userId AND question_id=:questionId AND is_current
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(this::answer)
                .optional();
    }

    public List<AnswerVersion> listAnswers(
            UUID userId, UUID questionId, int page, int size, String order) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_answer_versions
                        WHERE user_id=:userId AND question_id=:questionId
                        ORDER BY %s
                        LIMIT :limit OFFSET :offset
                        """.formatted(order))
                .param("userId", userId)
                .param("questionId", questionId)
                .param("limit", size)
                .param("offset", page * size)
                .query(this::answer)
                .list();
    }

    public long countAnswers(UUID userId, UUID questionId) {
        return jdbc.sql("""
                        SELECT count(*) FROM cover_letter_answer_versions
                        WHERE user_id=:userId AND question_id=:questionId
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(Long.class)
                .single();
    }

    public AnswerVersion insertAnswer(
            UUID userId,
            UUID questionId,
            UUID parentVersionId,
            UUID restoredFromVersionId,
            TipTapDocumentDto content,
            String plainText,
            int count,
            CoverLetterVersionSource source,
            AnswerCreatedBy createdBy,
            Instant now) {
        currentAnswer(userId, questionId).ifPresent(current -> jdbc.sql("""
                        UPDATE cover_letter_answer_versions
                        SET is_current=false
                        WHERE user_id=:userId AND id=:id AND is_current
                        """)
                .param("userId", userId)
                .param("id", current.id())
                .update());
        int versionNo = jdbc.sql("""
                        SELECT COALESCE(max(version_no),0)+1
                        FROM cover_letter_answer_versions
                        WHERE user_id=:userId AND question_id=:questionId
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(Integer.class)
                .single();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO cover_letter_answer_versions (
                            id,user_id,question_id,parent_version_id,restored_from_version_id,
                            version_no,content_json,content_text,character_count,
                            source_type,is_current,created_by,created_at
                        ) VALUES (
                            :id,:userId,:questionId,:parentId,:restoredId,
                            :versionNo,CAST(:content AS jsonb),:plainText,:characterCount,
                            :sourceType,true,:createdBy,:now
                        )
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("questionId", questionId)
                .param("parentId", parentVersionId)
                .param("restoredId", restoredFromVersionId)
                .param("versionNo", versionNo)
                .param("content", write(content))
                .param("plainText", plainText)
                .param("characterCount", count)
                .param("sourceType", source.name())
                .param("createdBy", createdBy.name())
                .param("now", utc(now))
                .update();
        return findAnswer(userId, id).orElseThrow();
    }

    public Optional<AnswerVersion> findRunAnswer(
            UUID userId, UUID agentRunId, UUID questionId) {
        return jdbc.sql("""
                        SELECT answer.*
                        FROM agent_run_resource_links link
                        JOIN cover_letter_answer_versions answer
                          ON answer.user_id=link.user_id
                         AND answer.id=link.cover_letter_answer_version_id
                        WHERE link.user_id=:userId AND link.agent_run_id=:runId
                          AND link.resource_kind='COVER_LETTER_ANSWER_VERSION'
                          AND answer.question_id=:questionId
                        """)
                .param("userId", userId)
                .param("runId", agentRunId)
                .param("questionId", questionId)
                .query(this::answer)
                .optional();
    }

    public Optional<AnswerVersion> findRunAppliedAnswer(
            UUID userId, UUID agentRunId, UUID questionId) {
        return jdbc.sql("""
                        SELECT answer.*
                        FROM agent_run_resource_links link
                        JOIN cover_letter_answer_versions answer
                          ON answer.user_id=link.user_id
                         AND answer.id=link.cover_letter_answer_version_id
                        JOIN cover_letter_verifications verification
                          ON verification.user_id=link.user_id
                         AND verification.answer_version_id=answer.id
                         AND verification.agent_run_id=link.agent_run_id
                        WHERE link.user_id=:userId AND link.agent_run_id=:runId
                          AND link.resource_kind='COVER_LETTER_ANSWER_VERSION'
                          AND answer.question_id=:questionId
                        """)
                .param("userId", userId)
                .param("runId", agentRunId)
                .param("questionId", questionId)
                .query(this::answer)
                .optional();
    }

    public void attachAnswerToRun(
            UUID userId, UUID agentRunId, UUID answerVersionId, Instant now) {
        jdbc.sql("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,
                            cover_letter_answer_version_id,primary_resource,created_at
                        ) VALUES (
                            :id,:userId,:runId,'COVER_LETTER_ANSWER_VERSION',
                            :answerId,false,:now
                        )
                        ON CONFLICT (user_id,agent_run_id,cover_letter_answer_version_id)
                            WHERE resource_kind='COVER_LETTER_ANSWER_VERSION'
                        DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("runId", agentRunId)
                .param("answerId", answerVersionId)
                .param("now", utc(now))
                .update();
    }

    public boolean runHasAnswerLink(
            UUID userId, UUID agentRunId, UUID answerVersionId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM agent_run_resource_links
                            WHERE user_id=:userId AND agent_run_id=:runId
                              AND resource_kind='COVER_LETTER_ANSWER_VERSION'
                              AND cover_letter_answer_version_id=:answerId
                        )
                        """)
                .param("userId", userId)
                .param("runId", agentRunId)
                .param("answerId", answerVersionId)
                .query(Boolean.class)
                .single();
    }

    public void insertEvidenceLinks(
            UUID userId, UUID answerVersionId, List<EvidenceUse> uses, Instant now) {
        for (EvidenceUse use : uses) {
            jdbc.sql("""
                            INSERT INTO cover_letter_evidence_links (
                                id,user_id,answer_version_id,profile_evidence_id,
                                claim_text,usage_type,created_at
                            ) VALUES (
                                :id,:userId,:answerId,:evidenceId,
                                :claimText,:usageType,:now
                            )
                            """)
                    .param("id", UUID.randomUUID())
                    .param("userId", userId)
                    .param("answerId", answerVersionId)
                    .param("evidenceId", use.evidenceId())
                    .param("claimText", use.claimText())
                    .param("usageType", use.usageType().name())
                    .param("now", utc(now))
                    .update();
        }
    }

    public boolean isEvidenceReferenced(UUID userId, UUID evidenceId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM cover_letter_evidence_links
                            WHERE user_id=:userId
                              AND profile_evidence_id=:evidenceId
                        )
                        """)
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .query(Boolean.class)
                .single();
    }

    public void copyEvidenceLinks(
            UUID userId,
            UUID restoredFromVersionId,
            UUID restoredAnswerVersionId,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO cover_letter_evidence_links (
                            id,user_id,answer_version_id,profile_evidence_id,
                            claim_text,usage_type,created_at
                        )
                        SELECT gen_random_uuid(),user_id,:restoredAnswerId,profile_evidence_id,
                               claim_text,usage_type,:now
                        FROM cover_letter_evidence_links
                        WHERE user_id=:userId AND answer_version_id=:sourceAnswerId
                        """)
                .param("restoredAnswerId", restoredAnswerVersionId)
                .param("now", utc(now))
                .param("userId", userId)
                .param("sourceAnswerId", restoredFromVersionId)
                .update();
    }

    public int countRunAppliedAnswers(UUID userId, UUID agentRunId) {
        return jdbc.sql("""
                        SELECT count(DISTINCT link.cover_letter_answer_version_id)
                        FROM agent_run_resource_links link
                        JOIN cover_letter_verifications verification
                          ON verification.user_id=link.user_id
                         AND verification.answer_version_id=link.cover_letter_answer_version_id
                         AND verification.agent_run_id=link.agent_run_id
                        WHERE link.user_id=:userId AND link.agent_run_id=:runId
                          AND link.resource_kind='COVER_LETTER_ANSWER_VERSION'
                        """)
                .param("userId", userId)
                .param("runId", agentRunId)
                .query(Integer.class)
                .single();
    }

    public int countPriorLineageAppliedAnswers(UUID userId, UUID agentRunId) {
        return jdbc.sql("""
                        SELECT count(DISTINCT verification.answer_version_id)
                        FROM agent_runs current_run
                        JOIN agent_runs lineage
                          ON lineage.user_id=current_run.user_id
                         AND lineage.root_run_id=current_run.root_run_id
                         AND lineage.workflow_type='COVER_LETTER_GENERATION'
                         AND lineage.run_attempt_no < current_run.run_attempt_no
                        JOIN cover_letter_verifications verification
                          ON verification.user_id=lineage.user_id
                         AND verification.agent_run_id=lineage.id
                        WHERE current_run.user_id=:userId
                          AND current_run.id=:runId
                        """)
                .param("userId", userId)
                .param("runId", agentRunId)
                .query(Integer.class)
                .single();
    }

    public List<HistoricalEvidence> historicalEvidence(
            UUID userId, UUID answerVersionId) {
        return jdbc.sql("""
                        SELECT evidence.id,evidence.title,evidence.evidence_category,
                               evidence.source_type,evidence.verification_status,
                               evidence.source_deleted_at,
                               link.claim_text,link.usage_type
                        FROM cover_letter_evidence_links link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=link.user_id
                         AND evidence.id=link.profile_evidence_id
                        WHERE link.user_id=:userId AND link.answer_version_id=:answerId
                        ORDER BY link.created_at,link.id
                        """)
                .param("userId", userId)
                .param("answerId", answerVersionId)
                .query((rs, row) -> new HistoricalEvidence(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("evidence_category"),
                        EvidenceSourceType.valueOf(rs.getString("source_type")),
                        EvidenceVerificationStatus.valueOf(rs.getString("verification_status")),
                        rs.getObject("source_deleted_at") != null,
                        rs.getString("claim_text"),
                        CoverLetterEvidenceUsageType.valueOf(rs.getString("usage_type"))))
                .list();
    }

    public Verification insertVerification(
            UUID id,
            UUID userId,
            UUID answerVersionId,
            VerificationResult result,
            UUID agentRunId,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO cover_letter_verifications (
                            id,user_id,answer_version_id,status,issues,suggestions,
                            verified_claims,agent_run_id,created_at
                        ) VALUES (
                            :id,:userId,:answerId,:status,CAST(:issues AS jsonb),
                            CAST(:suggestions AS jsonb),CAST(:claims AS jsonb),:runId,:now
                        )
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("answerId", answerVersionId)
                .param("status", result.status().name())
                .param("issues", write(result.issues()))
                .param("suggestions", write(result.suggestions()))
                .param("claims", write(result.verifiedClaims()))
                .param("runId", agentRunId)
                .param("now", utc(now))
                .update();
        return findVerification(userId, id).orElseThrow();
    }

    public Verification insertPendingVerification(
            UUID id,
            UUID userId,
            UUID answerVersionId,
            UUID agentRunId,
            Instant now) {
        return insertVerification(
                id,
                userId,
                answerVersionId,
                new VerificationResult(
                        VerificationStatus.PENDING, List.of(), List.of(), List.of()),
                agentRunId,
                now);
    }

    public Verification completePendingVerification(
            UUID userId,
            UUID verificationId,
            UUID agentRunId,
            VerificationResult result) {
        int updated = jdbc.sql("""
                        UPDATE cover_letter_verifications
                        SET status=:status,issues=CAST(:issues AS jsonb),
                            suggestions=CAST(:suggestions AS jsonb),
                            verified_claims=CAST(:claims AS jsonb)
                        WHERE user_id=:userId AND id=:id AND agent_run_id=:runId
                          AND status='PENDING'
                        """)
                .param("status", result.status().name())
                .param("issues", write(result.issues()))
                .param("suggestions", write(result.suggestions()))
                .param("claims", write(result.verifiedClaims()))
                .param("userId", userId)
                .param("id", verificationId)
                .param("runId", agentRunId)
                .update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return findVerification(userId, verificationId).orElseThrow();
    }

    public void failPendingVerification(UUID userId, UUID agentRunId) {
        jdbc.sql("""
                        UPDATE cover_letter_verifications
                        SET status='FAILED',
                            issues='[{"code":"OTHER","severity":"ERROR","message":"검증 작업이 완료되지 않았습니다.","relatedText":null,"evidenceIds":[]}]'::jsonb,
                            suggestions='[]'::jsonb,
                            verified_claims='[]'::jsonb
                        WHERE user_id=:userId AND agent_run_id=:runId
                          AND status='PENDING'
                        """)
                .param("userId", userId)
                .param("runId", agentRunId)
                .update();
    }

    public Optional<Verification> findVerification(
            UUID userId, UUID verificationId) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_verifications
                        WHERE user_id=:userId AND id=:id
                        """)
                .param("userId", userId)
                .param("id", verificationId)
                .query(this::verification)
                .optional();
    }

    public Optional<Verification> latestVerification(
            UUID userId, UUID answerVersionId) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_verifications
                        WHERE user_id=:userId AND answer_version_id=:answerId
                        ORDER BY created_at DESC,id DESC LIMIT 1
                        """)
                .param("userId", userId)
                .param("answerId", answerVersionId)
                .query(this::verification)
                .optional();
    }

    public List<Verification> listVerifications(
            UUID userId, UUID answerVersionId, int page, int size) {
        return jdbc.sql("""
                        SELECT * FROM cover_letter_verifications
                        WHERE user_id=:userId AND answer_version_id=:answerId
                        ORDER BY created_at DESC,id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("userId", userId)
                .param("answerId", answerVersionId)
                .param("limit", size)
                .param("offset", page * size)
                .query(this::verification)
                .list();
    }

    public long countVerifications(UUID userId, UUID answerVersionId) {
        return jdbc.sql("""
                        SELECT count(*) FROM cover_letter_verifications
                        WHERE user_id=:userId AND answer_version_id=:answerId
                        """)
                .param("userId", userId)
                .param("answerId", answerVersionId)
                .query(Long.class)
                .single();
    }

    public void acknowledge(
            UUID userId, UUID coverLetterId, UUID verificationId, Instant now) {
        jdbc.sql("""
                        INSERT INTO cover_letter_verification_acknowledgements (
                            id,user_id,cover_letter_id,verification_id,acknowledged_at
                        ) VALUES (:id,:userId,:coverId,:verificationId,:now)
                        ON CONFLICT (user_id,cover_letter_id,verification_id) DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("coverId", coverLetterId)
                .param("verificationId", verificationId)
                .param("now", utc(now))
                .update();
    }

    public int activeQuestionCount(UUID userId, UUID coverLetterId) {
        return jdbc.sql("""
                        SELECT count(*) FROM cover_letter_questions
                        WHERE user_id=:userId AND cover_letter_id=:coverId
                          AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("coverId", coverLetterId)
                .query(Integer.class)
                .single();
    }

    private Optional<CoverRow> coverQuery(
            String where, Map<String, ?> params, boolean lock) {
        String suffix = lock ? " FOR UPDATE OF cl" : "";
        return jdbc.sql("""
                        SELECT %s
                        FROM cover_letters cl
                        JOIN job_postings j
                          ON j.user_id=cl.user_id AND j.id=cl.job_posting_id
                        LEFT JOIN companies c ON c.id=j.company_id
                        %s%s
                        """.formatted(COVER_SELECT, where, suffix))
                .params(params)
                .query(this::cover)
                .optional();
    }

    private void throwMutationFailure(
            UUID userId, UUID coverLetterId, long expectedVersion) {
        CoverRow current = find(userId, coverLetterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (current.status() == CoverLetterStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.COVER_LETTER_ARCHIVED);
        }
        if (current.version() != expectedVersion) {
            throw versionConflict("version");
        }
        throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
    }

    private BusinessException questionMutationFailure(
            UUID userId, UUID questionId, long expectedVersion) {
        QuestionRow current = findQuestion(userId, questionId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (current.deletedAt() != null) {
            return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (current.version() != expectedVersion) {
            return versionConflict("version");
        }
        return new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
    }

    private BusinessException versionConflict(String field) {
        return new BusinessException(
                ErrorCode.RESOURCE_VERSION_CONFLICT,
                Map.of("field", field, "reason", "STALE"),
                null);
    }

    private CoverRow cover(ResultSet rs, int row) throws SQLException {
        return new CoverRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("job_posting_id", UUID.class),
                rs.getString("company_name"),
                rs.getString("job_title"),
                rs.getString("position_name"),
                rs.getString("title"),
                CoverLetterStatus.valueOf(rs.getString("status")),
                instant(rs, "finalized_at"),
                instant(rs, "archived_at"),
                instant(rs, "deleted_at"),
                rs.getLong("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private QuestionRow question(ResultSet rs, int row) throws SQLException {
        return new QuestionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("cover_letter_id", UUID.class),
                rs.getInt("question_order"),
                rs.getString("question_text"),
                (Integer) rs.getObject("max_length"),
                rs.getString("memo"),
                rs.getLong("version"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "deleted_at"));
    }

    private AnswerVersion answer(ResultSet rs, int row) throws SQLException {
        return new AnswerVersion(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("question_id", UUID.class),
                rs.getObject("parent_version_id", UUID.class),
                rs.getObject("restored_from_version_id", UUID.class),
                rs.getInt("version_no"),
                read(rs.getString("content_json"), TipTapDocumentDto.class),
                rs.getString("content_text"),
                rs.getInt("character_count"),
                CoverLetterVersionSource.valueOf(rs.getString("source_type")),
                rs.getBoolean("is_current"),
                AnswerCreatedBy.valueOf(rs.getString("created_by")),
                instant(rs, "created_at"));
    }

    private Verification verification(ResultSet rs, int row) throws SQLException {
        UUID userId = rs.getObject("user_id", UUID.class);
        UUID answerVersionId = rs.getObject("answer_version_id", UUID.class);
        List<VerificationIssue> issues = read(rs.getString("issues"), ISSUES);
        List<VerifiedClaim> verifiedClaims =
                read(rs.getString("verified_claims"), CLAIMS);
        return new Verification(
                rs.getObject("id", UUID.class),
                userId,
                answerVersionId,
                VerificationStatus.valueOf(rs.getString("status")),
                issues,
                read(rs.getString("suggestions"), STRINGS),
                verifiedClaims,
                verificationEvidenceReferences(
                        userId, answerVersionId, issues, verifiedClaims),
                rs.getObject("agent_run_id", UUID.class),
                instant(rs, "created_at"));
    }

    private List<EvidenceReference> verificationEvidenceReferences(
            UUID userId,
            UUID answerVersionId,
            List<VerificationIssue> issues,
            List<VerifiedClaim> verifiedClaims) {
        List<EvidenceReference> references =
                new ArrayList<>(evidenceReferences(userId, answerVersionId));
        List<UUID> referencedIds = new ArrayList<>();
        issues.forEach(issue -> issue.evidenceIds().forEach(id -> {
            if (!referencedIds.contains(id)) {
                referencedIds.add(id);
            }
        }));
        verifiedClaims.forEach(claim -> claim.evidenceIds().forEach(id -> {
            if (!referencedIds.contains(id)) {
                referencedIds.add(id);
            }
        }));
        if (referencedIds.isEmpty()) {
            return List.copyOf(references);
        }
        List<EvidenceReference> verificationReferences = jdbc.sql("""
                        SELECT evidence.id,evidence.title,evidence.evidence_category,
                               evidence.verification_status,evidence.source_type,
                               evidence.source_deleted_at
                        FROM profile_evidence evidence
                        WHERE evidence.user_id=:userId
                          AND evidence.id IN (:evidenceIds)
                        ORDER BY evidence.id
                        """)
                .param("userId", userId)
                .param("evidenceIds", referencedIds)
                .query((result, ignored) -> new EvidenceReference(
                        result.getObject("id", UUID.class),
                        result.getString("title"),
                        result.getString("evidence_category"),
                        EvidenceVerificationStatus.valueOf(
                                result.getString("verification_status")),
                        EvidenceSourceType.valueOf(result.getString("source_type")),
                        result.getObject("source_deleted_at") != null))
                .list();
        verificationReferences.forEach(candidate -> {
            if (references.stream()
                    .noneMatch(existing -> existing.id().equals(candidate.id()))) {
                references.add(candidate);
            }
        });
        return List.copyOf(references);
    }

    private List<EvidenceReference> evidenceReferences(
            UUID userId, UUID answerVersionId) {
        return jdbc.sql("""
                        SELECT DISTINCT evidence.id,evidence.title,evidence.evidence_category,
                               evidence.verification_status,evidence.source_type,
                               evidence.source_deleted_at
                        FROM cover_letter_evidence_links link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=link.user_id
                         AND evidence.id=link.profile_evidence_id
                        WHERE link.user_id=:userId AND link.answer_version_id=:answerId
                        ORDER BY evidence.id
                        """)
                .param("userId", userId)
                .param("answerId", answerVersionId)
                .query((rs, row) -> new EvidenceReference(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("evidence_category"),
                        EvidenceVerificationStatus.valueOf(rs.getString("verification_status")),
                        EvidenceSourceType.valueOf(rs.getString("source_type")),
                        rs.getObject("source_deleted_at") != null))
                .list();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("cover letter JSON could not be serialized", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored cover letter JSON is invalid", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored cover letter JSON is invalid", exception);
        }
    }

    private boolean causedByConstraint(
            Throwable failure, String sqlState, String constraintName) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlState.equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    public record CoverRow(
            UUID id,
            UUID userId,
            UUID jobId,
            String companyName,
            String jobTitle,
            String positionName,
            String title,
            CoverLetterStatus status,
            Instant finalizedAt,
            Instant archivedAt,
            Instant deletedAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record QuestionRow(
            UUID id,
            UUID userId,
            UUID coverLetterId,
            int questionOrder,
            String questionText,
            Integer maxLength,
            String memo,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {}
}
