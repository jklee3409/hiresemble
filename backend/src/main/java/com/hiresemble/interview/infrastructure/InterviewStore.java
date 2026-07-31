package com.hiresemble.interview.infrastructure;

import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.AgentRunSnapshot;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.domain.CoverLetterStatus;
import com.hiresemble.interview.application.model.InterviewModels.AnswerVersionRow;
import com.hiresemble.interview.application.model.InterviewModels.CoverAnswerContext;
import com.hiresemble.interview.application.model.InterviewModels.EvidenceContext;
import com.hiresemble.interview.application.model.InterviewModels.EvidenceRefRow;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackContext;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackResult;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackRow;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackScore;
import com.hiresemble.interview.application.model.InterviewModels.FinalEducationContext;
import com.hiresemble.interview.application.model.InterviewModels.InterviewJobProjection;
import com.hiresemble.interview.application.model.InterviewModels.GeneratedQuestion;
import com.hiresemble.interview.application.model.InterviewModels.PageSlice;
import com.hiresemble.interview.application.model.InterviewModels.PreparationContext;
import com.hiresemble.interview.application.model.InterviewModels.QuestionRow;
import com.hiresemble.interview.application.model.InterviewModels.QuestionSetRow;
import com.hiresemble.interview.application.model.InterviewModels.StructuredProfileContext;
import com.hiresemble.interview.domain.InterviewAnswerVersionSource;
import com.hiresemble.interview.domain.InterviewQuestionType;
import com.hiresemble.profile.domain.model.EducationLevel;
import com.hiresemble.profile.domain.model.EducationStatus;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.research.application.model.ResearchModels.ResearchSourceRow;
import com.hiresemble.research.domain.SourceCoverage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class InterviewStore {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<FeedbackScore>> SCORES = new TypeReference<>() {};
    private static final String QUESTION_SET_SELECT = """
            SELECT question_set.id,
                   question_set.job_posting_id,
                   company.display_name AS company_name,
                   job.position_name,
                   job.title AS job_title,
                   question_set.cover_letter_id,
                   cover.title AS cover_letter_title,
                   cover.status AS cover_letter_status,
                   question_set.title,
                   (SELECT count(*) FROM interview_questions question
                    WHERE question.user_id=question_set.user_id
                      AND question.question_set_id=question_set.id) AS question_count,
                   question_set.research_run_id,
                   research.source_coverage,
                   question_set.agent_run_id,
                   run.status AS agent_run_status,
                   run.current_step,
                   run.progress_percent,
                   question_set.created_at,
                   question_set.updated_at
            FROM interview_question_sets question_set
            JOIN job_postings job
              ON job.user_id=question_set.user_id
             AND job.id=question_set.job_posting_id
            LEFT JOIN companies company
              ON company.id=job.company_id
            JOIN cover_letters cover
              ON cover.user_id=question_set.user_id
             AND cover.id=question_set.cover_letter_id
            JOIN research_runs research
              ON research.user_id=question_set.user_id
             AND research.id=question_set.research_run_id
            JOIN agent_runs run
              ON run.user_id=question_set.user_id
             AND run.id=question_set.agent_run_id
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public InterviewStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public Optional<PreparationContext> loadPreparationContext(
            UUID userId, UUID jobId, UUID coverLetterId) {
        Optional<JobInput> job = jdbcClient.sql("""
                        SELECT job.id,job.version,
                               company.display_name AS company_name,
                               job.position_name,job.title,
                               job.role_category,job.description_text,
                               analysis.id AS analysis_id,analysis.analysis_summary
                        FROM job_postings job
                        LEFT JOIN companies company ON company.id=job.company_id
                        LEFT JOIN LATERAL (
                            SELECT id,analysis_summary
                            FROM job_analyses
                            WHERE user_id=job.user_id AND job_posting_id=job.id
                            ORDER BY analysis_version DESC,id DESC
                            LIMIT 1
                        ) analysis ON true
                        WHERE job.user_id=:userId AND job.id=:jobId AND job.deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query((rs, row) -> new JobInput(
                        rs.getObject("id", UUID.class),
                        rs.getLong("version"),
                        rs.getString("company_name"),
                        rs.getString("position_name"),
                        rs.getString("title"),
                        rs.getString("role_category"),
                        rs.getString("description_text"),
                        rs.getObject("analysis_id", UUID.class),
                        rs.getString("analysis_summary")))
                .optional();
        if (job.isEmpty()) {
            return Optional.empty();
        }
        Optional<CoverInput> cover = jdbcClient.sql("""
                        SELECT id,title,status
                        FROM cover_letters
                        WHERE user_id=:userId AND id=:coverLetterId
                          AND job_posting_id=:jobId AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("coverLetterId", coverLetterId)
                .param("jobId", jobId)
                .query((rs, row) -> new CoverInput(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        CoverLetterStatus.valueOf(rs.getString("status"))))
                .optional();
        if (cover.isEmpty()) {
            return Optional.empty();
        }
        List<CoverAnswerContext> answers = jdbcClient.sql("""
                        SELECT question.id AS question_id,question.question_text,
                               answer.id AS answer_id,answer.content_text
                        FROM cover_letter_questions question
                        JOIN cover_letter_answer_versions answer
                          ON answer.user_id=question.user_id
                         AND answer.question_id=question.id
                         AND answer.is_current
                        WHERE question.user_id=:userId
                          AND question.cover_letter_id=:coverLetterId
                          AND question.deleted_at IS NULL
                        ORDER BY question.question_order,question.id
                        """)
                .param("userId", userId)
                .param("coverLetterId", coverLetterId)
                .query((rs, row) -> new CoverAnswerContext(
                        rs.getObject("question_id", UUID.class),
                        rs.getString("question_text"),
                        rs.getObject("answer_id", UUID.class),
                        rs.getString("content_text")))
                .list();
        ProfileInput profile = jdbcClient.sql("""
                        SELECT introduction,desired_roles::text AS desired_roles,
                               desired_industries::text AS desired_industries,
                               desired_locations::text AS desired_locations
                        FROM user_profiles WHERE user_id=:userId
                        """)
                .param("userId", userId)
                .query((rs, row) -> new ProfileInput(
                        rs.getString("introduction"),
                        strings(rs.getString("desired_roles")),
                        strings(rs.getString("desired_industries")),
                        strings(rs.getString("desired_locations"))))
                .single();
        FinalEducationContext education = jdbcClient.sql("""
                        SELECT id,school_name,major,degree,education_level,education_status,
                               graduation_date
                        FROM educations
                        WHERE user_id=:userId AND is_primary AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .query((rs, row) -> new FinalEducationContext(
                        rs.getObject("id", UUID.class),
                        rs.getString("school_name"),
                        rs.getString("major"),
                        rs.getString("degree"),
                        EducationLevel.valueOf(rs.getString("education_level")),
                        EducationStatus.valueOf(rs.getString("education_status")),
                        rs.getObject("graduation_date", java.time.LocalDate.class)))
                .optional()
                .orElse(null);
        List<EvidenceContext> evidence = jdbcClient.sql("""
                        SELECT id,source_type,evidence_category,title,content,verification_status
                        FROM profile_evidence
                        WHERE user_id=:userId
                          AND verification_status='VERIFIED'
                          AND source_deleted_at IS NULL
                          AND source_type <> 'EDUCATION'
                          AND NOT (
                              upper(regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g'))
                                  IN ('EDUCATION', 'EDUCATIONHISTORY', 'EDUCATIONALBACKGROUND',
                                      'ACADEMIC', 'ACADEMICBACKGROUND', 'ACADEMICRECORD')
                              OR regexp_replace(evidence_category, '[[:space:]_-]+', '', 'g')
                                  IN ('학력', '학력사항', '학력정보', '교육', '교육이력', '교육사항')
                          )
                        ORDER BY id
                        """)
                .param("userId", userId)
                .query((rs, row) -> new EvidenceContext(
                        rs.getObject("id", UUID.class),
                        EvidenceSourceType.valueOf(rs.getString("source_type")),
                        rs.getString("evidence_category"),
                        rs.getString("title"),
                        rs.getString("content"),
                        EvidenceVerificationStatus.valueOf(rs.getString("verification_status"))))
                .list();
        JobInput value = job.orElseThrow();
        CoverInput coverValue = cover.orElseThrow();
        return Optional.of(new PreparationContext(
                userId,
                jobId,
                value.version(),
                value.companyName(),
                value.positionName(),
                value.title(),
                value.roleCategory(),
                value.description(),
                value.analysisId(),
                value.analysisSummary(),
                coverValue.id(),
                coverValue.title(),
                coverValue.status(),
                answers,
                new StructuredProfileContext(
                        profile.introduction(),
                        profile.desiredRoles(),
                        profile.desiredIndustries(),
                        profile.desiredLocations(),
                        education),
                evidence));
    }

    public void createQuestionSet(
            UUID id,
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            UUID researchRunId,
            String title,
            String generationConfig,
            UUID agentRunId,
            Instant now) {
        jdbcClient.sql("""
                        INSERT INTO interview_question_sets (
                            id,user_id,job_posting_id,cover_letter_id,research_run_id,
                            title,generation_config,agent_run_id,created_at,updated_at
                        ) VALUES (
                            :id,:userId,:jobId,:coverLetterId,:researchRunId,
                            :title,CAST(:config AS jsonb),:agentRunId,:now,:now
                        )
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("jobId", jobId)
                .param("coverLetterId", coverLetterId)
                .param("researchRunId", researchRunId)
                .param("title", title)
                .param("config", generationConfig)
                .param("agentRunId", agentRunId)
                .param("now", utc(now))
                .update();
    }

    public boolean insertPreparationRetryAgentRun(
            UUID successorId,
            AgentRunSnapshot predecessor,
            WorkflowLaunchCommand command,
            long budgetPolicyVersion,
            Instant queuedAt) {
        return jdbcClient.sql("""
                        INSERT INTO agent_runs (
                            id,user_id,workflow_type,status,current_step,progress_percent,
                            workflow_version,canonical_input_hash,input_reference_snapshot,
                            budget_policy_version,price_version,requested_quality_mode,
                            highest_model_tier_used,estimated_cost_usd,reserved_cost_usd,
                            actual_cost_usd,resource_type,resource_id,retry_of_run_id,
                            root_run_id,run_attempt_no,retryable_failure,state_version,
                            queued_at,updated_at
                        ) VALUES (
                            :id,:userId,:workflowType,'QUEUED',NULL,0,
                            :workflowVersion,:inputHash,CAST(:inputRefs AS jsonb),
                            :budgetPolicyVersion,:priceVersion,:qualityMode,
                            NULL,:estimatedCost,0,0,:resourceType,:resourceId,:retryOf,
                            :rootRunId,:runAttemptNo,false,0,:queuedAt,:queuedAt
                        )
                        ON CONFLICT (user_id,retry_of_run_id)
                            WHERE retry_of_run_id IS NOT NULL
                        DO NOTHING
                        """)
                .param("id", successorId)
                .param("userId", predecessor.userId())
                .param("workflowType", predecessor.workflowType().name())
                .param("workflowVersion", command.workflowVersion())
                .param("inputHash", command.canonicalInputHash())
                .param("inputRefs", json(command.inputReferenceSnapshot()))
                .param("budgetPolicyVersion", budgetPolicyVersion)
                .param("priceVersion", command.priceVersion())
                .param("qualityMode", command.requestedQualityMode().name())
                .param("estimatedCost", command.estimatedCostUsd())
                .param("resourceType", command.resource().resourceType())
                .param("resourceId", command.resource().resourceId())
                .param("retryOf", predecessor.id())
                .param("rootRunId", predecessor.rootRunId())
                .param("runAttemptNo", predecessor.runAttemptNo() + 1)
                .param("queuedAt", utc(queuedAt))
                .update() == 1;
    }

    public void attachPrimaryQuestionSetRunLink(
            UUID userId, UUID agentRunId, UUID questionSetId, Instant now) {
        jdbcClient.sql("""
                        INSERT INTO agent_run_resource_links (
                            id,user_id,agent_run_id,resource_kind,question_set_id,
                            primary_resource,created_at
                        ) VALUES (
                            :id,:userId,:agentRunId,'QUESTION_SET',:questionSetId,true,:now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .param("questionSetId", questionSetId)
                .param("now", utc(now))
                .update();
    }

    public Optional<UUID> findVisibleRetrySuccessorId(
            UUID userId, UUID predecessorRunId) {
        return jdbcClient.sql("""
                        SELECT id FROM agent_runs
                        WHERE user_id=:userId AND retry_of_run_id=:predecessorRunId
                          AND deleted_at IS NULL
                        """)
                .param("userId", userId)
                .param("predecessorRunId", predecessorRunId)
                .query(UUID.class)
                .optional();
    }

    public Optional<QuestionSetRow> findQuestionSet(UUID userId, UUID questionSetId) {
        return jdbcClient.sql(QUESTION_SET_SELECT
                        + " WHERE question_set.user_id=:userId AND question_set.id=:questionSetId")
                .param("userId", userId)
                .param("questionSetId", questionSetId)
                .query(this::questionSet)
                .optional();
    }

    public Optional<QuestionSetRow> findQuestionSetByResearch(
            UUID userId, UUID researchRunId) {
        return jdbcClient.sql(QUESTION_SET_SELECT
                        + " WHERE question_set.user_id=:userId"
                        + " AND question_set.research_run_id=:researchRunId")
                .param("userId", userId)
                .param("researchRunId", researchRunId)
                .query(this::questionSet)
                .optional();
    }

    public Optional<QuestionSetRow> findQuestionSetByAgentRun(
            UUID userId, UUID agentRunId) {
        return jdbcClient.sql(QUESTION_SET_SELECT
                        + " WHERE question_set.user_id=:userId"
                        + " AND question_set.agent_run_id=:agentRunId")
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .query(this::questionSet)
                .optional();
    }

    public PageSlice<QuestionSetRow> listQuestionSets(
            UUID userId,
            UUID jobId,
            UUID coverLetterId,
            String query,
            SourceCoverage sourceCoverage,
            String researchStatus,
            int page,
            int size,
            String order) {
        String filters = """
                 WHERE question_set.user_id=:userId
                   AND (
                     CAST(:jobId AS uuid) IS NULL
                     OR question_set.job_posting_id=CAST(:jobId AS uuid)
                   )
                   AND (
                     CAST(:coverLetterId AS uuid) IS NULL
                     OR question_set.cover_letter_id=CAST(:coverLetterId AS uuid)
                   )
                   AND (
                     CAST(:coverage AS varchar) IS NULL
                     OR research.source_coverage=CAST(:coverage AS varchar)
                   )
                   AND (
                     CAST(:researchStatus AS varchar) IS NULL
                     OR research.status=CAST(:researchStatus AS varchar)
                   )
                   AND (CAST(:query AS varchar) IS NULL OR (
                       lower(question_set.title) LIKE lower(:queryPattern)
                       OR lower(COALESCE(company.display_name,'')) LIKE lower(:queryPattern)
                       OR lower(COALESCE(job.position_name,'')) LIKE lower(:queryPattern)
                       OR lower(COALESCE(job.title,'')) LIKE lower(:queryPattern)
                   ))
                """;
        var countSql = """
                SELECT count(*)
                FROM interview_question_sets question_set
                JOIN job_postings job
                  ON job.user_id=question_set.user_id AND job.id=question_set.job_posting_id
                LEFT JOIN companies company ON company.id=job.company_id
                JOIN research_runs research
                  ON research.user_id=question_set.user_id
                 AND research.id=question_set.research_run_id
                """ + filters;
        long total = jdbcClient.sql(countSql)
                .param("userId", userId)
                .param("jobId", jobId)
                .param("coverLetterId", coverLetterId)
                .param("coverage", sourceCoverage == null ? null : sourceCoverage.name())
                .param("researchStatus", researchStatus)
                .param("query", query)
                .param("queryPattern", query == null ? null : "%" + query + "%")
                .query(Long.class)
                .single();
        List<QuestionSetRow> items = jdbcClient.sql(
                        QUESTION_SET_SELECT + filters + " ORDER BY " + order
                                + " LIMIT :size OFFSET :offset")
                .param("userId", userId)
                .param("jobId", jobId)
                .param("coverLetterId", coverLetterId)
                .param("coverage", sourceCoverage == null ? null : sourceCoverage.name())
                .param("researchStatus", researchStatus)
                .param("query", query)
                .param("queryPattern", query == null ? null : "%" + query + "%")
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::questionSet)
                .list();
        return new PageSlice<>(
                items,
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    public boolean activeJobExists(UUID userId, UUID jobId) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM job_postings
                            WHERE user_id=:userId AND id=:jobId AND deleted_at IS NULL
                        )
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query(Boolean.class)
                .single());
    }

    public InterviewJobProjection projectionForJob(UUID userId, UUID jobId) {
        return jdbcClient.sql("""
                        SELECT count(*)::integer AS preparation_count,
                               (array_agg(id ORDER BY created_at DESC,id DESC))[1]
                                   AS latest_question_set_id
                        FROM interview_question_sets
                        WHERE user_id=:userId AND job_posting_id=:jobId
                        """)
                .param("userId", userId)
                .param("jobId", jobId)
                .query((rs, row) -> new InterviewJobProjection(
                        rs.getInt("preparation_count"),
                        rs.getObject("latest_question_set_id", UUID.class)))
                .single();
    }

    public boolean coverExists(UUID userId, UUID coverLetterId) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM cover_letters
                            WHERE user_id=:userId AND id=:coverLetterId AND deleted_at IS NULL
                        )
                        """)
                .param("userId", userId)
                .param("coverLetterId", coverLetterId)
                .query(Boolean.class)
                .single());
    }

    public boolean questionSetExists(UUID userId, UUID questionSetId) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM interview_question_sets
                            WHERE user_id=:userId AND id=:questionSetId
                        )
                        """)
                .param("userId", userId)
                .param("questionSetId", questionSetId)
                .query(Boolean.class)
                .single());
    }

    public boolean evidenceIsReferenced(UUID userId, UUID evidenceId) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM interview_question_evidence_links
                            WHERE user_id=:userId AND profile_evidence_id=:evidenceId
                        )
                        """)
                .param("userId", userId)
                .param("evidenceId", evidenceId)
                .query(Boolean.class)
                .single());
    }

    public List<QuestionRow> listQuestions(UUID userId, UUID questionSetId) {
        return jdbcClient.sql("""
                        SELECT id,question_set_id,question_order,question_type,question_text,
                               intent,evaluation_points::text AS evaluation_points_text,
                               answer_guide,follow_up_questions::text AS follow_up_questions_text,
                               source_based,created_at
                        FROM interview_questions
                        WHERE user_id=:userId AND question_set_id=:questionSetId
                        ORDER BY question_order,id
                        """)
                .param("userId", userId)
                .param("questionSetId", questionSetId)
                .query(this::question)
                .list();
    }

    public Optional<QuestionRow> findQuestion(UUID userId, UUID questionId) {
        return jdbcClient.sql("""
                        SELECT id,question_set_id,question_order,question_type,question_text,
                               intent,evaluation_points::text AS evaluation_points_text,
                               answer_guide,follow_up_questions::text AS follow_up_questions_text,
                               source_based,created_at
                        FROM interview_questions
                        WHERE user_id=:userId AND id=:questionId
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(this::question)
                .optional();
    }

    public void persistQuestions(
            UUID userId,
            UUID questionSetId,
            List<GeneratedQuestion> questions,
            Instant now) {
        List<UUID> existing = questionIds(userId, questionSetId);
        if (!existing.isEmpty()) {
            List<UUID> requested = questions.stream()
                    .sorted(java.util.Comparator.comparingInt(GeneratedQuestion::questionOrder))
                    .map(GeneratedQuestion::id)
                    .toList();
            if (existing.equals(requested)) {
                return;
            }
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        for (GeneratedQuestion question : questions) {
            jdbcClient.sql("""
                            INSERT INTO interview_questions (
                                id,user_id,question_set_id,question_order,question_type,
                                question_text,intent,evaluation_points,answer_guide,
                                follow_up_questions,source_based,created_at
                            ) VALUES (
                                :id,:userId,:questionSetId,:questionOrder,:questionType,
                                :questionText,:intent,CAST(:evaluationPoints AS jsonb),:answerGuide,
                                CAST(:followUps AS jsonb),:sourceBased,:now
                            )
                            """)
                    .param("id", question.id())
                    .param("userId", userId)
                    .param("questionSetId", questionSetId)
                    .param("questionOrder", question.questionOrder())
                    .param("questionType", question.questionType().name())
                    .param("questionText", question.questionText())
                    .param("intent", question.intent())
                    .param("evaluationPoints", json(question.evaluationPoints()))
                    .param("answerGuide", question.answerGuide())
                    .param("followUps", json(question.followUpQuestions()))
                    .param("sourceBased", question.sourceBased())
                    .param("now", utc(now))
                    .update();
            for (UUID evidenceId : question.evidenceIds()) {
                jdbcClient.sql("""
                                INSERT INTO interview_question_evidence_links (
                                    id,user_id,interview_question_id,profile_evidence_id,created_at
                                ) VALUES (:id,:userId,:questionId,:evidenceId,:now)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("userId", userId)
                        .param("questionId", question.id())
                        .param("evidenceId", evidenceId)
                        .param("now", utc(now))
                        .update();
            }
            for (UUID sourceId : question.sourceIds()) {
                jdbcClient.sql("""
                                INSERT INTO interview_question_source_links (
                                    id,user_id,interview_question_id,research_source_id,created_at
                                ) VALUES (:id,:userId,:questionId,:sourceId,:now)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("userId", userId)
                        .param("questionId", question.id())
                        .param("sourceId", sourceId)
                        .param("now", utc(now))
                        .update();
            }
        }
        jdbcClient.sql("""
                        UPDATE interview_question_sets
                        SET updated_at=:now
                        WHERE user_id=:userId AND id=:questionSetId
                        """)
                .param("now", utc(now))
                .param("userId", userId)
                .param("questionSetId", questionSetId)
                .update();
    }

    public List<UUID> questionIds(UUID userId, UUID questionSetId) {
        return jdbcClient.sql("""
                        SELECT id FROM interview_questions
                        WHERE user_id=:userId AND question_set_id=:questionSetId
                        ORDER BY question_order
                        """)
                .param("userId", userId)
                .param("questionSetId", questionSetId)
                .query(UUID.class)
                .list();
    }

    public Set<UUID> verifiedEvidenceIds(UUID userId, List<UUID> evidenceIds) {
        if (evidenceIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbcClient.sql("""
                        SELECT id FROM profile_evidence
                        WHERE user_id=:userId AND id IN (:ids)
                          AND verification_status='VERIFIED'
                          AND source_deleted_at IS NULL
                          AND source_type <> 'EDUCATION'
                        """)
                .param("userId", userId)
                .param("ids", evidenceIds)
                .query(UUID.class)
                .list());
    }

    public Set<UUID> sourceIdsForQuestionSet(
            UUID userId, UUID questionSetId, List<UUID> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbcClient.sql("""
                        SELECT source.id
                        FROM interview_question_sets question_set
                        JOIN research_sources source
                          ON source.user_id=question_set.user_id
                         AND source.research_run_id=question_set.research_run_id
                        WHERE question_set.user_id=:userId
                          AND question_set.id=:questionSetId
                          AND source.id IN (:sourceIds)
                        """)
                .param("userId", userId)
                .param("questionSetId", questionSetId)
                .param("sourceIds", sourceIds)
                .query(UUID.class)
                .list());
    }

    public List<EvidenceRefRow> evidenceRefs(UUID userId, UUID questionId) {
        return jdbcClient.sql("""
                        SELECT evidence.id,evidence.title,evidence.evidence_category,
                               evidence.verification_status,evidence.source_type,
                               (evidence.source_deleted_at IS NOT NULL
                                   OR evidence.verification_status='SOURCE_DELETED') AS source_deleted
                        FROM interview_question_evidence_links link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=link.user_id
                         AND evidence.id=link.profile_evidence_id
                        WHERE link.user_id=:userId
                          AND link.interview_question_id=:questionId
                        ORDER BY evidence.id
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query((rs, row) -> new EvidenceRefRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("evidence_category"),
                        EvidenceVerificationStatus.valueOf(rs.getString("verification_status")),
                        EvidenceSourceType.valueOf(rs.getString("source_type")),
                        rs.getBoolean("source_deleted")))
                .list();
    }

    public List<ResearchSourceRow> sourceRefs(UUID userId, UUID questionId) {
        return jdbcClient.sql("""
                        SELECT source.*,topic.topic AS primary_topic
                        FROM interview_question_source_links question_link
                        JOIN research_sources source
                          ON source.user_id=question_link.user_id
                         AND source.id=question_link.research_source_id
                        JOIN research_topic_source_links topic_link
                          ON topic_link.user_id=source.user_id
                         AND topic_link.research_source_id=source.id
                         AND topic_link.is_primary
                        JOIN research_topics topic
                          ON topic.user_id=topic_link.user_id
                         AND topic.id=topic_link.research_topic_id
                        WHERE question_link.user_id=:userId
                          AND question_link.interview_question_id=:questionId
                        ORDER BY source.provider_rank,source.id
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query((rs, row) -> new ResearchSourceRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("research_run_id", UUID.class),
                        com.hiresemble.research.domain.ResearchTopic.valueOf(
                                rs.getString("primary_topic")),
                        rs.getString("source_url"),
                        rs.getString("title"),
                        com.hiresemble.research.domain.ResearchSourceType.valueOf(
                                rs.getString("source_type")),
                        instant(rs, "published_at"),
                        instant(rs, "retrieved_at"),
                        rs.getString("snippet"),
                        rs.getString("reliability_notice"),
                        rs.getInt("provider_rank"),
                        rs.getString("content_hash")))
                .list();
    }

    public Optional<AnswerVersionRow> currentAnswer(UUID userId, UUID questionId) {
        return jdbcClient.sql("""
                        SELECT * FROM interview_answer_versions
                        WHERE user_id=:userId AND interview_question_id=:questionId
                          AND is_current
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(this::answer)
                .optional();
    }

    public Optional<AnswerVersionRow> findAnswer(UUID userId, UUID answerVersionId) {
        return jdbcClient.sql("""
                        SELECT * FROM interview_answer_versions
                        WHERE user_id=:userId AND id=:answerVersionId
                        """)
                .param("userId", userId)
                .param("answerVersionId", answerVersionId)
                .query(this::answer)
                .optional();
    }

    public AnswerVersionRow insertAnswer(
            UUID userId, UUID questionId, UUID parentVersionId, String content, Instant now) {
        Optional<UUID> locked = jdbcClient.sql("""
                        SELECT id FROM interview_questions
                        WHERE user_id=:userId AND id=:questionId
                        FOR UPDATE
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(UUID.class)
                .optional();
        if (locked.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        AnswerVersionRow current = currentAnswer(userId, questionId).orElse(null);
        UUID currentId = current == null ? null : current.id();
        if (!java.util.Objects.equals(currentId, parentVersionId)) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    java.util.Map.of(
                            "field", "parentVersionId",
                            "reason", "STALE",
                            "currentVersionId", currentId == null ? "" : currentId.toString()),
                    null);
        }
        if (current != null) {
            jdbcClient.sql("""
                            UPDATE interview_answer_versions SET is_current=false
                            WHERE user_id=:userId AND id=:currentId AND is_current
                            """)
                    .param("userId", userId)
                    .param("currentId", current.id())
                    .update();
        }
        int nextVersion = current == null ? 1 : current.versionNo() + 1;
        return jdbcClient.sql("""
                        INSERT INTO interview_answer_versions (
                            id,user_id,interview_question_id,parent_version_id,version_no,
                            content,source_type,is_current,created_at
                        ) VALUES (
                            :id,:userId,:questionId,:parentVersionId,:versionNo,
                            :content,'USER_EDITED',true,:now
                        )
                        RETURNING *
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("questionId", questionId)
                .param("parentVersionId", parentVersionId)
                .param("versionNo", nextVersion)
                .param("content", content)
                .param("now", utc(now))
                .query(this::answer)
                .single();
    }

    public PageSlice<AnswerVersionRow> listAnswers(
            UUID userId, UUID questionId, int page, int size, String order) {
        long total = jdbcClient.sql("""
                        SELECT count(*) FROM interview_answer_versions
                        WHERE user_id=:userId AND interview_question_id=:questionId
                        """)
                .param("userId", userId)
                .param("questionId", questionId)
                .query(Long.class)
                .single();
        List<AnswerVersionRow> items = jdbcClient.sql(
                        """
                        SELECT * FROM interview_answer_versions
                        WHERE user_id=:userId AND interview_question_id=:questionId
                        """
                                + " ORDER BY "
                                + order
                                + " LIMIT :size OFFSET :offset")
                .param("userId", userId)
                .param("questionId", questionId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::answer)
                .list();
        return new PageSlice<>(
                items,
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    public FeedbackRow persistFeedback(
            UUID id,
            UUID userId,
            UUID answerVersionId,
            UUID agentRunId,
            FeedbackResult result,
            Instant now) {
        Optional<FeedbackRow> inserted = jdbcClient.sql("""
                        INSERT INTO interview_answer_feedbacks (
                            id,user_id,answer_version_id,scores,strengths,weaknesses,
                            suggestions,revised_example,agent_run_id,created_at
                        ) VALUES (
                            :id,:userId,:answerVersionId,CAST(:scores AS jsonb),
                            CAST(:strengths AS jsonb),CAST(:weaknesses AS jsonb),
                            CAST(:suggestions AS jsonb),:revisedExample,:agentRunId,:now
                        )
                        ON CONFLICT (user_id,agent_run_id) DO NOTHING
                        RETURNING *,scores::text AS scores_text,
                                  strengths::text AS strengths_text,
                                  weaknesses::text AS weaknesses_text,
                                  suggestions::text AS suggestions_text
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("answerVersionId", answerVersionId)
                .param("scores", json(result.scores()))
                .param("strengths", json(result.strengths()))
                .param("weaknesses", json(result.weaknesses()))
                .param("suggestions", json(result.suggestions()))
                .param("revisedExample", result.revisedExample())
                .param("agentRunId", agentRunId)
                .param("now", utc(now))
                .query(this::feedback)
                .optional();
        if (inserted.isPresent()) {
            return inserted.get();
        }
        FeedbackRow existing = findFeedbackByRun(userId, agentRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "feedback idempotency result is missing"));
        if (!existing.answerVersionId().equals(answerVersionId)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        return existing;
    }

    public Optional<FeedbackRow> findFeedbackByRun(UUID userId, UUID agentRunId) {
        return jdbcClient.sql("""
                        SELECT *,scores::text AS scores_text,
                               strengths::text AS strengths_text,
                               weaknesses::text AS weaknesses_text,
                               suggestions::text AS suggestions_text
                        FROM interview_answer_feedbacks
                        WHERE user_id=:userId AND agent_run_id=:agentRunId
                        """)
                .param("userId", userId)
                .param("agentRunId", agentRunId)
                .query(this::feedback)
                .optional();
    }

    public Optional<FeedbackRow> latestFeedback(UUID userId, UUID answerVersionId) {
        return jdbcClient.sql("""
                        SELECT *,scores::text AS scores_text,
                               strengths::text AS strengths_text,
                               weaknesses::text AS weaknesses_text,
                               suggestions::text AS suggestions_text
                        FROM interview_answer_feedbacks
                        WHERE user_id=:userId AND answer_version_id=:answerVersionId
                        ORDER BY created_at DESC,id DESC
                        LIMIT 1
                        """)
                .param("userId", userId)
                .param("answerVersionId", answerVersionId)
                .query(this::feedback)
                .optional();
    }

    public PageSlice<FeedbackRow> listFeedbacks(
            UUID userId, UUID answerVersionId, int page, int size) {
        long total = jdbcClient.sql("""
                        SELECT count(*) FROM interview_answer_feedbacks
                        WHERE user_id=:userId AND answer_version_id=:answerVersionId
                        """)
                .param("userId", userId)
                .param("answerVersionId", answerVersionId)
                .query(Long.class)
                .single();
        List<FeedbackRow> items = jdbcClient.sql("""
                        SELECT *,scores::text AS scores_text,
                               strengths::text AS strengths_text,
                               weaknesses::text AS weaknesses_text,
                               suggestions::text AS suggestions_text
                        FROM interview_answer_feedbacks
                        WHERE user_id=:userId AND answer_version_id=:answerVersionId
                        ORDER BY created_at DESC,id DESC
                        LIMIT :size OFFSET :offset
                        """)
                .param("userId", userId)
                .param("answerVersionId", answerVersionId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::feedback)
                .list();
        return new PageSlice<>(
                items,
                page,
                size,
                total,
                total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    public Optional<FeedbackContext> loadFeedbackContext(
            UUID userId, UUID answerVersionId) {
        return jdbcClient.sql("""
                        SELECT answer.id AS answer_id,answer.interview_question_id,
                               answer.content,question.question_text,question.intent,
                               question.evaluation_points::text AS evaluation_points_text,
                               question.answer_guide,question_set.job_posting_id,
                               company.display_name AS company_name,job.position_name,
                               question_set.cover_letter_id
                        FROM interview_answer_versions answer
                        JOIN interview_questions question
                          ON question.user_id=answer.user_id
                         AND question.id=answer.interview_question_id
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=question.user_id
                         AND question_set.id=question.question_set_id
                        JOIN job_postings job
                          ON job.user_id=question_set.user_id
                         AND job.id=question_set.job_posting_id
                        LEFT JOIN companies company ON company.id=job.company_id
                        WHERE answer.user_id=:userId AND answer.id=:answerVersionId
                        """)
                .param("userId", userId)
                .param("answerVersionId", answerVersionId)
                .query((rs, row) -> new FeedbackContext(
                        userId,
                        rs.getObject("answer_id", UUID.class),
                        rs.getObject("interview_question_id", UUID.class),
                        rs.getString("question_text"),
                        rs.getString("intent"),
                        strings(rs.getString("evaluation_points_text")),
                        rs.getString("answer_guide"),
                        rs.getString("content"),
                        rs.getObject("job_posting_id", UUID.class),
                        rs.getString("company_name"),
                        rs.getString("position_name"),
                        rs.getObject("cover_letter_id", UUID.class)))
                .optional();
    }

    private QuestionSetRow questionSet(ResultSet rs, int row) throws SQLException {
        return new QuestionSetRow(
                rs.getObject("id", UUID.class),
                rs.getObject("job_posting_id", UUID.class),
                rs.getString("company_name"),
                rs.getString("position_name"),
                rs.getString("job_title"),
                rs.getObject("cover_letter_id", UUID.class),
                rs.getString("cover_letter_title"),
                CoverLetterStatus.valueOf(rs.getString("cover_letter_status")),
                rs.getString("title"),
                rs.getInt("question_count"),
                rs.getObject("research_run_id", UUID.class),
                enumOrNull(SourceCoverage.class, rs.getString("source_coverage")),
                rs.getObject("agent_run_id", UUID.class),
                AgentRunStatus.valueOf(rs.getString("agent_run_status")),
                rs.getString("current_step"),
                rs.getInt("progress_percent"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private QuestionRow question(ResultSet rs, int row) throws SQLException {
        return new QuestionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("question_set_id", UUID.class),
                rs.getInt("question_order"),
                InterviewQuestionType.valueOf(rs.getString("question_type")),
                rs.getString("question_text"),
                rs.getString("intent"),
                strings(rs.getString("evaluation_points_text")),
                rs.getString("answer_guide"),
                strings(rs.getString("follow_up_questions_text")),
                rs.getBoolean("source_based"),
                instant(rs, "created_at"));
    }

    private AnswerVersionRow answer(ResultSet rs, int row) throws SQLException {
        return new AnswerVersionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("interview_question_id", UUID.class),
                rs.getObject("parent_version_id", UUID.class),
                rs.getInt("version_no"),
                rs.getString("content"),
                InterviewAnswerVersionSource.valueOf(rs.getString("source_type")),
                rs.getBoolean("is_current"),
                instant(rs, "created_at"));
    }

    private FeedbackRow feedback(ResultSet rs, int row) throws SQLException {
        return new FeedbackRow(
                rs.getObject("id", UUID.class),
                rs.getObject("answer_version_id", UUID.class),
                scores(rs.getString("scores_text")),
                strings(rs.getString("strengths_text")),
                strings(rs.getString("weaknesses_text")),
                strings(rs.getString("suggestions_text")),
                rs.getString("revised_example"),
                rs.getObject("agent_run_id", UUID.class),
                instant(rs, "created_at"));
    }

    private List<String> strings(String json) {
        try {
            return objectMapper.readValue(json, STRINGS);
        } catch (JacksonException exception) {
            throw new IllegalStateException("interview string JSON is invalid", exception);
        }
    }

    private List<FeedbackScore> scores(String json) {
        try {
            return objectMapper.readValue(json, SCORES);
        } catch (JacksonException exception) {
            throw new IllegalStateException("interview score JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("interview JSON could not be written", exception);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record JobInput(
            UUID id,
            long version,
            String companyName,
            String positionName,
            String title,
            String roleCategory,
            String description,
            UUID analysisId,
            String analysisSummary) {}

    private record CoverInput(UUID id, String title, CoverLetterStatus status) {}

    private record ProfileInput(
            String introduction,
            List<String> desiredRoles,
            List<String> desiredIndustries,
            List<String> desiredLocations) {}
}
