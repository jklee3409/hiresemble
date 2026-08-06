package com.hiresemble.agentrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.agentrun.application.command.AgentRunTransitionCommand;
import com.hiresemble.agentrun.application.command.WorkflowLaunchCommand;
import com.hiresemble.agentrun.application.model.ClaimedAgentRun;
import com.hiresemble.agentrun.application.model.SafeInterruption;
import com.hiresemble.agentrun.application.model.WorkflowLaunchResult;
import com.hiresemble.agentrun.application.port.AgentRunRetryPort;
import com.hiresemble.agentrun.application.port.AgentRunStatePort;
import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.agentrun.application.service.AgentRunInterruptionService;
import com.hiresemble.agentrun.domain.model.AgentRunStatus;
import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.SafeError;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.coverletter.application.CoverLetterApplicationService;
import com.hiresemble.coverletter.application.model.CoverLetterModels.AnswerVersion;
import com.hiresemble.coverletter.application.model.CoverLetterModels.GenerationSnapshot;
import com.hiresemble.coverletter.application.model.CoverLetterModels.PersistGeneratedAnswer;
import com.hiresemble.coverletter.application.model.CoverLetterModels.VerificationResult;
import com.hiresemble.coverletter.domain.AnswerCreatedBy;
import com.hiresemble.coverletter.domain.CoverLetterVersionSource;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import com.hiresemble.coverletter.domain.VerificationStatus;
import com.hiresemble.coverletter.infrastructure.CoverLetterStore;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CoverLetterAgentRunIntegrationTest extends AgentRunIntegrationSupport {

    @Autowired private AgentRunStatePort statePort;
    @Autowired private BudgetReservationPort budgetPort;
    @Autowired private AgentRunRetryPort retryPort;
    @Autowired private AgentRunInterruptionService interruptionService;
    @Autowired private CoverLetterApplicationService coverLetterService;
    @Autowired private CoverLetterStore coverLetterStore;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void generationRetryPreservesOriginalInputAndReusesSuccessfulAnswerWithoutVersionDrift() {
        UUID userId = seedUser("cover-generation-retry@example.com");
        UUID jobId = seedJob(userId, "generation-retry");
        seedAnalysis(userId, jobId);
        UUID coverId = seedCover(userId, jobId);
        UUID firstQuestion = seedQuestion(userId, coverId, 1);
        UUID failedQuestion = seedQuestion(userId, coverId, 2);
        WorkflowLaunchResult launched =
                launchGeneration(userId, coverId, List.of(firstQuestion, failedQuestion));
        ClaimedAgentRun claimed = statePort.claim(
                        launched.agentRunId(),
                        "cover-generation-worker",
                        Instant.now(),
                        Duration.ofSeconds(60))
                .orElseThrow();

        AnswerVersion successful = coverLetterStore.insertAnswer(
                userId,
                firstQuestion,
                null,
                null,
                content("Generated success"),
                "Generated success",
                17,
                CoverLetterVersionSource.AI_GENERATED,
                AnswerCreatedBy.AI,
                Instant.now());
        coverLetterStore.attachAnswerToRun(
                userId, launched.agentRunId(), successful.id(), Instant.now());
        coverLetterStore.insertVerification(
                UUID.randomUUID(),
                userId,
                successful.id(),
                new VerificationResult(
                        VerificationStatus.PASSED, List.of(), List.of(), List.of()),
                launched.agentRunId(),
                Instant.now());
        jdbcTemplate.update(
                "UPDATE cover_letters SET version=version+1,updated_at=now() WHERE id=?",
                coverId);
        failRetryable(
                userId,
                claimed,
                new PartialResult(
                        List.of(firstQuestion.toString()),
                        List.of(failedQuestion.toString()),
                        List.of(new ResourceReference(
                                "COVER_LETTER_ANSWER_VERSION",
                                successful.id(),
                                "Generated success"))));

        var predecessor = run(userId, launched.agentRunId());
        WorkflowLaunchResult retried = retryPort.retry(
                userId, predecessor.id(), "cover-generation-retry-key");
        var successor = run(userId, retried.agentRunId());

        assertThat(successor.canonicalInputHash()).isEqualTo(predecessor.canonicalInputHash());
        assertThat(successor.inputReferenceSnapshot()).isEqualTo(predecessor.inputReferenceSnapshot());
        assertThat(successor.inputReferenceSnapshot().path("coverLetterVersion").asLong())
                .isZero();
        assertThat(successor.partialResult()).isNotNull();
        assertThat(successor.partialResult().succeededScopeKeys())
                .containsExactly(firstQuestion.toString());
        assertThat(successor.partialResult().failedScopeKeys()).isEmpty();
        assertThat(successor.partialResult().resultRefs())
                .extracting(ResourceReference::resourceId)
                .containsExactly(successful.id());
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_run_resource_links
                        WHERE user_id=? AND agent_run_id=?
                          AND resource_kind='COVER_LETTER_ANSWER_VERSION'
                          AND cover_letter_answer_version_id=?
                        """,
                        Long.class,
                        userId,
                        successor.id(),
                        successful.id()))
                .isEqualTo(1L);

        ClaimedAgentRun retryClaim = statePort.claim(
                        successor.id(),
                        "cover-generation-retry-worker",
                        Instant.now(),
                        Duration.ofSeconds(60))
                .orElseThrow();
        GenerationSnapshot retrySnapshot = coverLetterService.loadGenerationRetrySnapshot(
                userId, retryClaim.run().id(), null);
        assertThat(retrySnapshot.coverLetterVersion()).isEqualTo(1);
        assertThat(retrySnapshot.questions())
                .extracting(question -> question.questionId())
                .containsExactly(failedQuestion);
        assertThat(coverLetterStore.countPriorLineageAppliedAnswers(
                        userId, successor.id()))
                .isEqualTo(1);
        PersistGeneratedAnswer failedAnswer = new PersistGeneratedAnswer(
                coverId,
                failedQuestion,
                retrySnapshot.coverLetterVersion(),
                null,
                retrySnapshot.snapshotHash(),
                content("Retried answer"),
                List.of(),
                new VerificationResult(
                        VerificationStatus.PASSED, List.of(), List.of(), List.of()));
        var firstApply = coverLetterService.applyGeneratedAnswer(
                userId, successor.id(), failedAnswer);
        var replayApply = coverLetterService.applyGeneratedAnswer(
                userId, successor.id(), failedAnswer);
        assertThat(replayApply.answerVersion().id())
                .isEqualTo(firstApply.answerVersion().id());
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_answer_versions
                        WHERE user_id=? AND question_id=?
                        """,
                        Long.class,
                        userId,
                        failedQuestion))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM cover_letters WHERE id=?",
                        Long.class,
                        coverId))
                .isEqualTo(2L);
    }

    @Test
    void verificationRetryCreatesANewPendingRecordAndLeaseExpiryCompensatesAtomically() {
        UUID userId = seedUser("cover-verification-retry@example.com");
        UUID jobId = seedJob(userId, "verification-retry");
        seedAnalysis(userId, jobId);
        UUID coverId = seedCover(userId, jobId);
        UUID questionId = seedQuestion(userId, coverId, 1);
        AnswerVersion answer = coverLetterStore.insertAnswer(
                userId,
                questionId,
                null,
                null,
                content("User answer"),
                "User answer",
                11,
                CoverLetterVersionSource.USER_EDITED,
                AnswerCreatedBy.USER,
                Instant.now());
        UUID predecessorVerificationId = UUID.randomUUID();
        WorkflowLaunchResult launched = launchVerification(
                userId, coverId, answer.id(), predecessorVerificationId);
        coverLetterStore.attachAnswerToRun(
                userId, launched.agentRunId(), answer.id(), Instant.now());
        coverLetterStore.insertPendingVerification(
                predecessorVerificationId,
                userId,
                answer.id(),
                launched.agentRunId(),
                Instant.now());
        ClaimedAgentRun claimed = statePort.claim(
                        launched.agentRunId(),
                        "cover-verification-worker",
                        Instant.now(),
                        Duration.ofSeconds(60))
                .orElseThrow();
        failRetryable(userId, claimed);
        coverLetterService.failPendingVerification(userId, launched.agentRunId());

        WorkflowLaunchResult retried = retryPort.retry(
                userId, launched.agentRunId(), "cover-verification-retry-key");
        var successor = run(userId, retried.agentRunId());
        UUID successorVerificationId = UUID.fromString(
                successor.inputReferenceSnapshot().path("verificationId").asText());
        assertThat(successorVerificationId).isNotEqualTo(predecessorVerificationId);
        assertThat(successor.canonicalInputHash())
                .isEqualTo(run(userId, launched.agentRunId()).canonicalInputHash());
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM cover_letter_verifications
                        WHERE user_id=? AND id=? AND answer_version_id=?
                          AND agent_run_id=? AND status='PENDING'
                        """,
                        Long.class,
                        userId,
                        successorVerificationId,
                        answer.id(),
                        successor.id()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_run_resource_links
                        WHERE user_id=? AND agent_run_id=?
                          AND resource_kind='COVER_LETTER_ANSWER_VERSION'
                          AND cover_letter_answer_version_id=? AND NOT primary_resource
                        """,
                        Long.class,
                        userId,
                        successor.id(),
                        answer.id()))
                .isEqualTo(1L);

        Instant claimedAt = Instant.now();
        statePort.claim(
                        successor.id(),
                        "cover-verification-retry-worker",
                        claimedAt,
                        Duration.ofSeconds(1))
                .orElseThrow();
        assertThat(coverLetterService.loadVerificationRetrySnapshot(
                                userId, successor.id(), null)
                        .answerVersion()
                        .id())
                .isEqualTo(answer.id());
        interruptionService.interruptExpired(
                successor.id(),
                claimedAt.plusSeconds(2),
                new SafeInterruption(
                        new SafeError("AGENT_RUN_INTERRUPTED", "Retry timed out."),
                        true));

        assertThat(run(userId, successor.id()).status())
                .isEqualTo(AgentRunStatus.INTERRUPTED);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM cover_letter_verifications WHERE id=?",
                        String.class,
                        successorVerificationId))
                .isEqualTo("FAILED");
    }

    private WorkflowLaunchResult launchGeneration(
            UUID userId, UUID coverId, List<UUID> questionIds) {
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", coverId.toString())
                .put("coverLetterVersion", 0)
                .put("snapshotHash", "b".repeat(64))
                .put("qualityMode", "ECONOMY")
                .put("avoidExperienceDuplication", true);
        var questions = input.putArray("questionIds");
        questionIds.forEach(value -> questions.add(value.toString()));
        input.putArray("preferredEvidenceIds");
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                userId,
                WorkflowType.COVER_LETTER_GENERATION,
                "cover-letter-generation-v1",
                "c".repeat(64),
                input,
                AiQualityMode.ECONOMY,
                new ResourceReference("COVER_LETTER", coverId, "Cover generation")));
    }

    private WorkflowLaunchResult launchVerification(
            UUID userId, UUID coverId, UUID answerId, UUID verificationId) {
        var input = objectMapper.createObjectNode()
                .put("coverLetterId", coverId.toString())
                .put("coverLetterVersion", 0)
                .put("answerVersionId", answerId.toString())
                .put("verificationId", verificationId.toString())
                .put("snapshotHash", "d".repeat(64))
                .put("qualityMode", "ECONOMY");
        return workflowLauncher.launch(new WorkflowLaunchCommand(
                userId,
                WorkflowType.COVER_LETTER_VERIFICATION,
                "cover-letter-verification-v1",
                "e".repeat(64),
                input,
                AiQualityMode.ECONOMY,
                new ResourceReference("COVER_LETTER", coverId, "Cover verification")));
    }

    private void failRetryable(UUID userId, ClaimedAgentRun claimed) {
        failRetryable(userId, claimed, null);
    }

    private void failRetryable(
            UUID userId, ClaimedAgentRun claimed, PartialResult partialResult) {
        budgetPort.releaseUnused(userId, claimed.run().id(), Instant.now());
        statePort.transition(new AgentRunTransitionCommand(
                userId,
                claimed.run().id(),
                claimed.claimToken(),
                claimed.run().stateVersion(),
                AgentRunStatus.FAILED,
                "FIXTURE_STEP",
                50,
                ModelTier.LOW_COST,
                BigDecimal.ZERO,
                true,
                null,
                new SafeError("FIXTURE_TRANSIENT", "Retryable fixture failure."),
                partialResult,
                Instant.now()));
    }

    private UUID seedJob(UUID userId, String key) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,content_hash,version,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,'Backend Engineer','Backend Engineer',
                    'Build reliable Java services.','USER_ENTERED','UNKNOWN','IN_PROGRESS',
                    'MANUAL_INPUT_PROVIDED',?,0,now(),now()
                )
                """,
                jobId,
                userId,
                "https://jobs.example.com/" + key,
                "https://jobs.example.com/" + key,
                "a".repeat(64));
        return jobId;
    }

    private void seedAnalysis(UUID userId, UUID jobId) {
        UUID analysisRunId = launch(userId).agentRunId();
        UUID analysisId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    INSERT INTO job_analyses (
                        id,user_id,job_posting_id,analysis_version,job_version,
                        job_content_hash,profile_snapshot_hash,evidence_snapshot_hash,
                        context_hash,eligibility,fit_score,responsibilities,
                        required_qualifications,preferred_qualifications,strengths,gaps,
                        analysis_summary,rubric_version,workflow_version,quality_mode,
                        embedding_policy_version,embedding_generation,retrieval_policy_version,
                        agent_run_id,sealed,created_at
                    ) VALUES (
                        ?,?,?,1,0,?,?,?,?,'ELIGIBLE',80.00,
                        '[]','[]','[]','[]','[]','Retry context',
                        'job-fit-rubric-v1','job-analysis-v1','ECONOMY',1,1,
                        'verified-evidence-rag-v1',?,false,now()
                    )
                    """,
                    analysisId,
                    userId,
                    jobId,
                    "a".repeat(64),
                    "f".repeat(64),
                    "0".repeat(64),
                    "1".repeat(64),
                    analysisRunId);
            jdbcTemplate.update(
                    """
                    INSERT INTO job_analysis_score_criteria (
                        id,user_id,job_analysis_id,category,criterion,weight,
                        match_level,score,explanation,source_location,criterion_order
                    ) VALUES (
                        ?,?,?,'REQUIRED_QUALIFICATION','Java',100.00,
                        'MATCHED',80.00,'Fixture criterion',NULL,0
                    )
                    """,
                    UUID.randomUUID(),
                    userId,
                    analysisId);
            jdbcTemplate.update(
                    "UPDATE job_analyses SET sealed=true WHERE id=?",
                    analysisId);
        });
    }

    private UUID seedCover(UUID userId, UUID jobId) {
        UUID coverId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letters (
                    id,user_id,job_posting_id,title,status,version,created_at,updated_at
                ) VALUES (?, ?, ?, 'Application', 'DRAFT', 0, now(), now())
                """,
                coverId,
                userId,
                jobId);
        return coverId;
    }

    private UUID seedQuestion(UUID userId, UUID coverId, int order) {
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

    private TipTapDocumentDto content(String value) {
        return new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(new TipTapNodeDto(
                                "text", value, List.of(), List.of())))));
    }
}
