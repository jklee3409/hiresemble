package com.hiresemble.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InterviewMigrationIntegrationTest extends PostgresIntegrationTest {

    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void ownerScopedPreparationCardinalityAndSourceProvenanceAreDatabaseContracts() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM flyway_schema_history
                        WHERE version='12' AND success
                        """,
                        Long.class))
                .isEqualTo(1L);
        UUID owner = seedUser("p8-owner@example.com");
        UUID other = seedUser("p8-other@example.com");
        UUID job = seedJob(owner, "p8-owner");
        UUID cover = seedCover(owner, job);
        Lineage lineage = seedLineage(owner, job, cover, "QUEUED", "QUEUED");

        assertThatThrownBy(() -> seedQuestionSet(
                        owner, job, cover, lineage.researchRunId(), lineage.agentRunId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> seedResearch(
                        other,
                        job,
                        cover,
                        seedAgentRun(other, "INTERVIEW_PREPARATION", "QUEUED"),
                        "QUEUED"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID topic = seedTopic(owner, lineage.researchRunId(), "COMPANY", 1);
        UUID source = seedSource(
                owner,
                lineage.researchRunId(),
                "https://example.com/company?utm_source=test",
                "OFFICIAL",
                1);
        seedTopicSource(owner, topic, source, true);
        assertThatThrownBy(() -> seedSource(
                        owner,
                        lineage.researchRunId(),
                        "https://example.com/company?utm_source=test",
                        "OFFICIAL",
                        2))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID otherJob = seedJob(owner, "p8-owner-2");
        UUID otherCover = seedCover(owner, otherJob);
        Lineage otherLineage =
                seedLineage(owner, otherJob, otherCover, "QUEUED", "QUEUED");
        UUID otherTopic =
                seedTopic(owner, otherLineage.researchRunId(), "COMPANY", 1);
        assertThatThrownBy(() -> seedTopicSource(owner, otherTopic, source, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void questionLinksAcceptOnlySameRunSourcesAndActiveVerifiedNonEducationEvidence() {
        UUID owner = seedUser("p8-provenance@example.com");
        UUID other = seedUser("p8-provenance-other@example.com");
        UUID job = seedJob(owner, "p8-provenance");
        UUID cover = seedCover(owner, job);
        Lineage lineage = seedLineage(owner, job, cover, "QUEUED", "QUEUED");
        UUID question = seedQuestion(owner, lineage.questionSetId(), 1, false);
        UUID topic = seedTopic(owner, lineage.researchRunId(), "COMPANY", 1);
        UUID source = seedSource(
                owner,
                lineage.researchRunId(),
                "https://example.com/official",
                "OFFICIAL",
                1);
        seedTopicSource(owner, topic, source, true);

        transaction().executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE interview_questions SET source_based=true WHERE id=?",
                    question);
            seedQuestionSource(owner, question, source);
        });

        UUID verified = seedEvidence(owner, "MANUAL", "VERIFIED", null);
        seedQuestionEvidence(owner, question, verified);
        UUID rejected = seedEvidence(owner, "MANUAL", "REJECTED", null);
        assertThatThrownBy(() -> seedQuestionEvidence(owner, question, rejected))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID education = seedEvidence(owner, "EDUCATION", "SOURCE_DELETED", "now()");
        assertThatThrownBy(() -> seedQuestionEvidence(owner, question, education))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID foreign = seedEvidence(other, "MANUAL", "VERIFIED", null);
        assertThatThrownBy(() -> seedQuestionEvidence(owner, question, foreign))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID otherJob = seedJob(owner, "p8-provenance-other-run");
        UUID otherCover = seedCover(owner, otherJob);
        Lineage otherLineage =
                seedLineage(owner, otherJob, otherCover, "QUEUED", "QUEUED");
        UUID otherTopic =
                seedTopic(owner, otherLineage.researchRunId(), "COMPANY", 1);
        UUID otherSource = seedSource(
                owner,
                otherLineage.researchRunId(),
                "https://other.example.com/source",
                "OFFICIAL",
                1);
        seedTopicSource(owner, otherTopic, otherSource, true);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
                    UUID crossRunQuestion =
                            seedQuestion(owner, lineage.questionSetId(), 2, true);
                    seedQuestionSource(owner, crossRunQuestion, otherSource);
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void answerLineageCurrentCasAndFeedbackAreImmutable() {
        UUID owner = seedUser("p8-answer@example.com");
        UUID job = seedJob(owner, "p8-answer");
        UUID cover = seedCover(owner, job);
        Lineage lineage = seedLineage(owner, job, cover, "SUCCEEDED", "SUCCEEDED");
        UUID question = seedQuestion(owner, lineage.questionSetId(), 1, false);
        UUID first = seedAnswer(owner, question, null, 1, true, "first answer");

        assertThatThrownBy(() ->
                        seedAnswer(owner, question, first, 2, true, "stale second"))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID second = transaction().execute(status -> {
            jdbcTemplate.update(
                    "UPDATE interview_answer_versions SET is_current=false WHERE id=?",
                    first);
            return seedAnswer(owner, question, first, 2, true, "second answer");
        });
        assertThat(second).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT version_no FROM interview_answer_versions
                        WHERE user_id=? AND interview_question_id=? AND is_current
                        """,
                        Integer.class,
                        owner,
                        question))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE interview_answer_versions SET content='mutated' WHERE id=?",
                        first))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID feedbackRun =
                seedAgentRun(owner, "INTERVIEW_ANSWER_FEEDBACK", "SUCCEEDED");
        UUID feedback = seedFeedback(owner, first, feedbackRun);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE interview_answer_feedbacks SET revised_example='changed' WHERE id=?",
                        feedback))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT answer_version_id FROM interview_answer_feedbacks WHERE id=?
                        """,
                        UUID.class,
                        feedback))
                .isEqualTo(first);
    }

    @Test
    void terminalAgentRunHistoryDeletePreservesP8DomainAndTypedAuditLinks() {
        UUID owner = seedUser("p8-history@example.com");
        UUID job = seedJob(owner, "p8-history");
        UUID cover = seedCover(owner, job);
        Lineage lineage = seedLineage(owner, job, cover, "SUCCEEDED", "SUCCEEDED");
        UUID question = seedQuestion(owner, lineage.questionSetId(), 1, false);
        UUID answer = seedAnswer(owner, question, null, 1, true, "answer");
        UUID feedbackRun =
                seedAgentRun(owner, "INTERVIEW_ANSWER_FEEDBACK", "SUCCEEDED");
        UUID feedback = seedFeedback(owner, answer, feedbackRun);

        transaction().executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    UPDATE agent_runs
                    SET resource_type='QUESTION_SET',resource_id=?
                    WHERE user_id=? AND id=?
                    """,
                    lineage.questionSetId(),
                    owner,
                    lineage.agentRunId());
            seedRunLink(
                    owner,
                    lineage.agentRunId(),
                    "QUESTION_SET",
                    "question_set_id",
                    lineage.questionSetId(),
                    true);
            seedRunLink(
                    owner,
                    lineage.agentRunId(),
                    "RESEARCH_RUN",
                    "research_run_id",
                    lineage.researchRunId(),
                    false);
            jdbcTemplate.update(
                    """
                    UPDATE agent_runs
                    SET resource_type='INTERVIEW_ANSWER_VERSION',resource_id=?
                    WHERE user_id=? AND id=?
                    """,
                    answer,
                    owner,
                    feedbackRun);
            seedRunLink(
                    owner,
                    feedbackRun,
                    "INTERVIEW_ANSWER_VERSION",
                    "interview_answer_version_id",
                    answer,
                    true);
        });

        jdbcTemplate.update(
                "UPDATE agent_runs SET deleted_at=now() WHERE user_id=? AND id IN (?,?)",
                owner,
                lineage.agentRunId(),
                feedbackRun);

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_runs
                        WHERE user_id=? AND id IN (?,?) AND deleted_at IS NOT NULL
                        """,
                        Long.class,
                        owner,
                        lineage.agentRunId(),
                        feedbackRun))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM research_runs research
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=research.user_id
                         AND question_set.research_run_id=research.id
                        JOIN interview_questions question
                          ON question.user_id=question_set.user_id
                         AND question.question_set_id=question_set.id
                        JOIN interview_answer_versions answer
                          ON answer.user_id=question.user_id
                         AND answer.interview_question_id=question.id
                        JOIN interview_answer_feedbacks feedback
                          ON feedback.user_id=answer.user_id
                         AND feedback.answer_version_id=answer.id
                        WHERE research.user_id=? AND feedback.id=?
                        """,
                        Long.class,
                        owner,
                        feedback))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_run_resource_links
                        WHERE user_id=? AND agent_run_id IN (?,?)
                        """,
                        Long.class,
                        owner,
                        lineage.agentRunId(),
                        feedbackRun))
                .isEqualTo(3L);
    }

    private UUID seedUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,'hash','P8 User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """,
                id,
                email);
        return id;
    }

    private UUID seedJob(UUID owner, String key) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,version,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,'Backend Engineer','Backend Engineer',
                    'Build reliable services.','USER_ENTERED','UNKNOWN','IN_PROGRESS',
                    'MANUAL_INPUT_PROVIDED',0,now(),now()
                )
                """,
                id,
                owner,
                "https://jobs.example.com/" + key,
                "https://jobs.example.com/" + key);
        return id;
    }

    private UUID seedCover(UUID owner, UUID job) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letters (
                    id,user_id,job_posting_id,title,status,version,created_at,updated_at
                ) VALUES (?, ?, ?, 'Application', 'DRAFT', 0, now(), now())
                """,
                id,
                owner,
                job);
        return id;
    }

    private UUID seedAgentRun(UUID owner, String workflow, String status) {
        UUID id = UUID.randomUUID();
        boolean terminal = "SUCCEEDED".equals(status);
        jdbcTemplate.update(
                """
                INSERT INTO agent_runs (
                    id,user_id,workflow_type,status,current_step,progress_percent,
                    workflow_version,canonical_input_hash,input_reference_snapshot,
                    budget_policy_version,requested_quality_mode,estimated_cost_usd,
                    reserved_cost_usd,actual_cost_usd,root_run_id,run_attempt_no,
                    retryable_failure,state_version,queued_at,completed_at,updated_at
                ) VALUES (
                    ?,?,?,?,NULL,?,'p8-test-v1',
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    '{}',(SELECT version FROM ai_budget_policy_versions WHERE active),
                    'BALANCED',0,0,0,?,1,false,0,now(),
                    CASE WHEN ? THEN now() ELSE NULL END,now()
                )
                """,
                id,
                owner,
                workflow,
                status,
                terminal ? 100 : 0,
                id,
                terminal);
        return id;
    }

    private Lineage seedLineage(
            UUID owner,
            UUID job,
            UUID cover,
            String agentStatus,
            String researchStatus) {
        UUID run = seedAgentRun(owner, "INTERVIEW_PREPARATION", agentStatus);
        UUID research = seedResearch(owner, job, cover, run, researchStatus);
        UUID questionSet = seedQuestionSet(owner, job, cover, research, run);
        return new Lineage(run, research, questionSet);
    }

    private UUID seedResearch(
            UUID owner, UUID job, UUID cover, UUID run, String status) {
        UUID id = UUID.randomUUID();
        boolean succeeded = "SUCCEEDED".equals(status);
        jdbcTemplate.update(
                """
                INSERT INTO research_runs (
                    id,user_id,job_posting_id,cover_letter_id,research_quality,status,
                    source_coverage,missing_coverage_topics,summary,agent_run_id,retryable,
                    created_at,completed_at,updated_at
                ) VALUES (
                    ?,?,?,?,'BASIC',?,
                    CASE WHEN ? THEN 'NONE' ELSE NULL END,'[]',
                    CASE WHEN ? THEN 'No usable public sources.' ELSE NULL END,
                    ?,false,now(),CASE WHEN ? THEN now() ELSE NULL END,now()
                )
                """,
                id,
                owner,
                job,
                cover,
                status,
                succeeded,
                succeeded,
                run,
                succeeded);
        return id;
    }

    private UUID seedQuestionSet(
            UUID owner, UUID job, UUID cover, UUID research, UUID run) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_question_sets (
                    id,user_id,job_posting_id,cover_letter_id,research_run_id,title,
                    generation_config,agent_run_id,created_at,updated_at
                ) VALUES (?, ?, ?, ?, ?, 'Expected questions', '{}', ?, now(), now())
                """,
                id,
                owner,
                job,
                cover,
                research,
                run);
        return id;
    }

    private UUID seedTopic(UUID owner, UUID research, String topic, int order) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO research_topics (
                    id,user_id,research_run_id,topic,query_text,topic_order,created_at
                ) VALUES (?, ?, ?, ?, ?, ?, now())
                """,
                id,
                owner,
                research,
                topic,
                "public query " + order,
                order);
        return id;
    }

    private UUID seedSource(
            UUID owner, UUID research, String url, String type, int rank) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO research_sources (
                    id,user_id,research_run_id,source_url,title,source_type,retrieved_at,
                    snippet,reliability_notice,provider_rank,content_hash
                ) VALUES (
                    ?, ?, ?, ?, 'Source', ?, now(), 'Snippet', 'Check reliability.',
                    ?, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
                )
                """,
                id,
                owner,
                research,
                url,
                type,
                rank);
        return id;
    }

    private void seedTopicSource(UUID owner, UUID topic, UUID source, boolean primary) {
        jdbcTemplate.update(
                """
                INSERT INTO research_topic_source_links (
                    id,user_id,research_topic_id,research_source_id,is_primary,created_at
                ) VALUES (?, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                owner,
                topic,
                source,
                primary);
    }

    private UUID seedQuestion(
            UUID owner, UUID questionSet, int order, boolean sourceBased) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_questions (
                    id,user_id,question_set_id,question_order,question_type,question_text,
                    evaluation_points,follow_up_questions,source_based,created_at
                ) VALUES (
                    ?, ?, ?, ?, 'TECHNICAL', 'Explain your design.',
                    '["clarity"]','[]',?,now()
                )
                """,
                id,
                owner,
                questionSet,
                order,
                sourceBased);
        return id;
    }

    private void seedQuestionSource(UUID owner, UUID question, UUID source) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question_source_links (
                    id,user_id,interview_question_id,research_source_id,created_at
                ) VALUES (?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                owner,
                question,
                source);
    }

    private UUID seedEvidence(
            UUID owner, String sourceType, String status, String deletedExpression) {
        UUID id = UUID.randomUUID();
        String deleted = deletedExpression == null ? "NULL" : deletedExpression;
        jdbcTemplate.execute("""
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    '%s','%s','%s',NULL,NULL,'EXPERIENCE','Evidence','Built services.',
                    '{}',NULL,'%s',
                    CASE WHEN '%s'='VERIFIED' THEN now() ELSE NULL END,
                    %s,0,now(),now()
                )
                """.formatted(id, owner, sourceType, status, status, deleted));
        return id;
    }

    private void seedQuestionEvidence(UUID owner, UUID question, UUID evidence) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question_evidence_links (
                    id,user_id,interview_question_id,profile_evidence_id,created_at
                ) VALUES (?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                owner,
                question,
                evidence);
    }

    private UUID seedAnswer(
            UUID owner,
            UUID question,
            UUID parent,
            int version,
            boolean current,
            String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer_versions (
                    id,user_id,interview_question_id,parent_version_id,version_no,
                    content,source_type,is_current,created_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'USER_EDITED', ?, now())
                """,
                id,
                owner,
                question,
                parent,
                version,
                content,
                current);
        return id;
    }

    private UUID seedFeedback(UUID owner, UUID answer, UUID run) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer_feedbacks (
                    id,user_id,answer_version_id,scores,strengths,weaknesses,
                    suggestions,revised_example,agent_run_id,created_at
                ) VALUES (
                    ?, ?, ?, '[{"criterion":"clarity","score":80,"explanation":"Clear"}]',
                    '["Specific"]','["Brief"]','["Add an example"]','Revised answer.',?,now()
                )
                """,
                id,
                owner,
                answer,
                run);
        return id;
    }

    private void seedRunLink(
            UUID owner,
            UUID run,
            String kind,
            String column,
            UUID resource,
            boolean primary) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_run_resource_links (
                    id,user_id,agent_run_id,resource_kind,%s,primary_resource,created_at
                ) VALUES (?, ?, ?, ?, ?, ?, now())
                """.formatted(column),
                UUID.randomUUID(),
                owner,
                run,
                kind,
                resource,
                primary);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record Lineage(UUID agentRunId, UUID researchRunId, UUID questionSetId) {}
}
