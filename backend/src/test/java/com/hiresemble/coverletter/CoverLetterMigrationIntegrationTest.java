package com.hiresemble.coverletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CoverLetterMigrationIntegrationTest extends PostgresIntegrationTest {

    @Test
    void migrationIsAppliedAndOwnerScopedActiveCardinalityIsEnforced() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM flyway_schema_history
                        WHERE version='8' AND success
                        """,
                        Long.class))
                .isEqualTo(1L);
        UUID owner = seedUser("migration-owner@example.com");
        UUID other = seedUser("migration-other@example.com");
        UUID job = seedJob(owner, "migration-owner-job");

        UUID active = insertCover(owner, job, "DRAFT");
        assertThatThrownBy(() -> insertCover(owner, job, "FINALIZED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCover(other, job, "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                """
                UPDATE cover_letters
                SET status='ARCHIVED',archived_at=now(),version=version+1
                WHERE id=?
                """,
                active);
        insertCover(owner, job, "ARCHIVED");
        insertCover(owner, job, "DRAFT");

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letters
                        WHERE user_id=? AND job_posting_id=? AND status='ARCHIVED'
                        """,
                        Long.class,
                        owner,
                        job))
                .isEqualTo(2L);
    }

    @Test
    void questionOrderCurrentAnswerAndImmutableHistoryAreDatabaseContracts() {
        UUID owner = seedUser("migration-history@example.com");
        UUID other = seedUser("migration-history-other@example.com");
        UUID cover = insertCover(owner, seedJob(owner, "migration-history-job"), "DRAFT");
        UUID firstQuestion = insertQuestion(owner, cover, 1);
        assertThatThrownBy(() -> insertQuestion(owner, cover, 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID firstAnswer = insertAnswer(owner, firstQuestion, 1, true);
        UUID ownerEvidence = seedEvidence(owner, "Owner evidence");
        UUID otherEvidence = seedEvidence(other, "Other evidence");
        insertEvidenceLink(owner, firstAnswer, ownerEvidence);
        assertThatThrownBy(() -> insertEvidenceLink(owner, firstAnswer, otherEvidence))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAnswer(owner, firstQuestion, 2, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        UPDATE cover_letter_answer_versions
                        SET content_text='mutated'
                        WHERE id=?
                        """,
                        firstAnswer))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID verification = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_verifications (
                    id,user_id,answer_version_id,status,issues,suggestions,
                    verified_claims,agent_run_id,created_at
                ) VALUES (?, ?, ?, 'PENDING', '[]', '[]', '[]', NULL, now())
                """,
                verification,
                owner,
                firstAnswer);
        jdbcTemplate.update(
                """
                UPDATE cover_letter_verifications
                SET status='WARNING',
                    issues='[{"code":"OTHER","severity":"WARNING","message":"확인","relatedText":null,"evidenceIds":[]}]'
                WHERE id=?
                """,
                verification);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE cover_letter_verifications SET status='FAILED' WHERE id=?",
                        verification))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                "UPDATE cover_letter_answer_versions SET is_current=false WHERE id=?",
                firstAnswer);
        UUID secondAnswer = insertAnswer(owner, firstQuestion, 2, true);
        jdbcTemplate.update(
                """
                UPDATE profile_evidence
                SET verification_status='REJECTED',verified_at=NULL,version=version+1,updated_at=now()
                WHERE id=?
                """,
                ownerEvidence);
        UUID restoredAnswer = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_answer_versions (
                    id,user_id,question_id,parent_version_id,restored_from_version_id,
                    version_no,content_json,content_text,character_count,source_type,
                    is_current,created_by,created_at
                ) VALUES (
                    ?,?,?,?, ?,3,
                    '{"type":"doc","content":[]}','answer',6,'RESTORED',false,'USER',now()
                )
                """,
                restoredAnswer,
                owner,
                firstQuestion,
                secondAnswer,
                firstAnswer);
        insertEvidenceLink(owner, restoredAnswer, ownerEvidence);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_answer_versions
                        WHERE user_id=? AND question_id=?
                        """,
                        Long.class,
                        owner,
                        firstQuestion))
                .isEqualTo(3L);
        assertThat(secondAnswer).isNotEqualTo(firstAnswer);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_evidence_links
                        WHERE user_id=? AND profile_evidence_id=?
                        """,
                        Long.class,
                        owner,
                        ownerEvidence))
                .isEqualTo(2L);
    }

    private UUID seedUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,ai_consent_at,
                    last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (?,?,'hash','Cover User','USER','ACTIVE',now(),now(),NULL,NULL,now(),now())
                """,
                userId,
                email);
        return userId;
    }

    private UUID seedJob(UUID userId, String key) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,version,created_at,updated_at
                ) VALUES (
                    ?,? ,?,?, 'Backend Engineer','Backend Engineer',
                    'Build reliable Java services.','USER_ENTERED','UNKNOWN','IN_PROGRESS',
                    'MANUAL_INPUT_PROVIDED',0,now(),now()
                )
                """,
                jobId,
                userId,
                "https://jobs.example.com/" + key,
                "https://jobs.example.com/" + key);
        return jobId;
    }

    private UUID insertCover(UUID userId, UUID jobId, String status) {
        UUID coverId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letters (
                    id,user_id,job_posting_id,title,status,finalized_at,archived_at,
                    version,created_at,updated_at
                ) VALUES (
                    ?,?,?,'Application',?,
                    CASE WHEN ?='FINALIZED' THEN now() ELSE NULL END,
                    CASE WHEN ?='ARCHIVED' THEN now() ELSE NULL END,
                    0,now(),now()
                )
                """,
                coverId,
                userId,
                jobId,
                status,
                status,
                status);
        return coverId;
    }

    private UUID insertQuestion(UUID userId, UUID coverId, int order) {
        UUID questionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_questions (
                    id,user_id,cover_letter_id,question_order,question_text,max_length,
                    memo,version,created_at,updated_at
                ) VALUES (?, ?, ?, ?, 'Describe your experience.', 1000, NULL, 0, now(), now())
                """,
                questionId,
                userId,
                coverId,
                order);
        return questionId;
    }

    private UUID insertAnswer(UUID userId, UUID questionId, int version, boolean current) {
        UUID answerId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_answer_versions (
                    id,user_id,question_id,parent_version_id,restored_from_version_id,
                    version_no,content_json,content_text,character_count,source_type,
                    is_current,created_by,created_at
                ) VALUES (
                    ?,?,?,NULL,NULL,?,
                    '{"type":"doc","content":[]}','answer',6,'USER_EDITED',?,'USER',now()
                )
                """,
                answerId,
                userId,
                questionId,
                version,
                current);
        return answerId;
    }

    private UUID seedEvidence(UUID userId, String title) {
        UUID evidenceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    ?,?,'MANUAL',NULL,NULL,'EXPERIENCE',?,'Built Java services.',
                    '{}',NULL,'VERIFIED',now(),NULL,0,now(),now()
                )
                """,
                evidenceId,
                userId,
                title);
        return evidenceId;
    }

    private void insertEvidenceLink(UUID userId, UUID answerId, UUID evidenceId) {
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_evidence_links (
                    id,user_id,answer_version_id,profile_evidence_id,
                    claim_text,usage_type,created_at
                ) VALUES (
                    ?,?,?,?,'Built Java services.','SUPPORTING_CLAIM',now()
                )
                """,
                UUID.randomUUID(),
                userId,
                answerId,
                evidenceId);
    }
}
