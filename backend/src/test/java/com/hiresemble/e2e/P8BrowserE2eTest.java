package com.hiresemble.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.workflow.InterviewAnswerFeedbackWorkflow.AnalyzeFeedbackInput;
import com.hiresemble.ai.workflow.InterviewAnswerFeedbackWorkflow.AnalyzeFeedbackOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.GenerateQuestionsInput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.GeneratedQuestionDraft;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.GeneratedQuestionsOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchBatchOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchHit;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchPurpose;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.interview.application.model.InterviewModels.FeedbackScore;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(P8BrowserE2eTest.FakeP8AiConfiguration.class)
@TestPropertySource(properties = "hiresemble.ai.runtime.enabled=true")
class P8BrowserE2eTest extends PostgresIntegrationTest {

    private static final String OWNER_EMAIL = "p8-actual-owner@example.com";
    private static final String OTHER_EMAIL = "p8-actual-other@example.com";
    private static final String PASSWORD = "password-123";
    private static final String PRIVATE_PROFILE =
            "Private profile phone 010-1234-5678 must never enter public search.";
    private static final String PRIVATE_EVIDENCE =
            "Private evidence p8-owner@example.com: reduced internal latency by 40 percent.";
    private static final String PRIVATE_COVER_ANSWER =
            "Private cover answer: led a confidential migration for an internal customer.";

    @LocalServerPort private int backendPort;

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private FakeP8ChatGateway chatGateway;
    @Autowired private FakeP8SearchGateway searchGateway;

    @DynamicPropertySource
    static void p8Environment(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.ai.provider", () -> "fake");
        registry.add("hiresemble.ai.model-low-cost", () -> "fake-p8-low-cost");
        registry.add("hiresemble.ai.model-balanced", () -> "fake-p8-balanced");
        registry.add("hiresemble.ai.model-high-quality", () -> "fake-p8-high-quality");
        registry.add("hiresemble.ai.model-policy-version", () -> "1");
        registry.add("hiresemble.search.provider", () -> "none");
        registry.add(
                "hiresemble.interview.ai-cost.preparation-estimated-cost-usd",
                () -> "0.000000");
        registry.add(
                "hiresemble.interview.ai-cost.preparation-price-version",
                () -> "0");
        registry.add(
                "hiresemble.interview.ai-cost.feedback-estimated-cost-usd",
                () -> "0.000000");
        registry.add(
                "hiresemble.interview.ai-cost.feedback-price-version",
                () -> "0");
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "100ms");
        registry.add("hiresemble.agent-runtime.reconciliation-interval", () -> "1s");
        registry.add("hiresemble.agent-runtime.heartbeat-interval", () -> "1s");
    }

    @BeforeEach
    void resetFakes() {
        chatGateway.reset();
        searchGateway.reset();
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void actualP8VerticalSliceBranchesOwnershipHistoryDeleteAndDatabaseAssertions()
            throws Exception {
        Fixture fixture = seedFixture();
        Path frontend = frontendDirectory();
        int frontendPort = availablePort();
        String corepack = System.getProperty("os.name", "")
                        .toLowerCase(Locale.ROOT)
                        .contains("win")
                ? "corepack.cmd"
                : "corepack";
        ProcessBuilder builder = new ProcessBuilder(
                corepack,
                "pnpm",
                "exec",
                "playwright",
                "test",
                "e2e/interview-preparation.actual.spec.ts",
                "--project=chromium",
                "--workers=1",
                "--reporter=line",
                "--output=../output/playwright/p8");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("P8_E2E_ENABLED", "true");
        builder.environment().put("P8_FRONTEND_PORT", Integer.toString(frontendPort));
        builder.environment().put(
                "P8_FRONTEND_BASE_URL", "http://127.0.0.1:" + frontendPort);
        builder.environment().put(
                "VITE_API_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
        builder.environment().put("P8_OWNER_EMAIL", OWNER_EMAIL);
        builder.environment().put("P8_OTHER_EMAIL", OTHER_EMAIL);
        builder.environment().put("P8_PASSWORD", PASSWORD);
        builder.environment().put("P8_MAIN_JOB_ID", fixture.main().jobId().toString());
        builder.environment().put(
                "P8_MAIN_COVER_ID", fixture.main().coverLetterId().toString());
        builder.environment().put(
                "P8_LIMITED_JOB_ID", fixture.limited().jobId().toString());
        builder.environment().put(
                "P8_LIMITED_COVER_ID", fixture.limited().coverLetterId().toString());
        builder.environment().put("P8_NONE_JOB_ID", fixture.none().jobId().toString());
        builder.environment().put(
                "P8_NONE_COVER_ID", fixture.none().coverLetterId().toString());
        builder.environment().put(
                "P8_FAILURE_JOB_ID", fixture.failure().jobId().toString());
        builder.environment().put(
                "P8_FAILURE_COVER_ID", fixture.failure().coverLetterId().toString());
        builder.environment().put("PLAYWRIGHT_HTML_OPEN", "never");

        Process browser = builder.start();
        boolean finished = browser.waitFor(14, TimeUnit.MINUTES);
        if (!finished) {
            browser.destroyForcibly();
            throw new AssertionError("P8 Playwright process exceeded fourteen minutes");
        }
        assertThat(browser.exitValue()).isZero();

        assertThat(chatGateway.calls()).isGreaterThanOrEqualTo(4);
        assertThat(searchGateway.calls()).isGreaterThanOrEqualTo(8);
        assertThat(searchGateway.requests())
                .allSatisfy(request -> {
                    assertThat(request.queries()).hasSizeLessThanOrEqualTo(2);
                    assertThat(request.maxResultsPerQuery()).isIn(5, 8);
                    String query = String.join(" ", request.queries());
                    assertThat(query)
                            .doesNotContain(
                                    PRIVATE_PROFILE,
                                    PRIVATE_EVIDENCE,
                                    PRIVATE_COVER_ANSWER,
                                    OWNER_EMAIL,
                                    "010-1234-5678",
                                    fixture.ownerId().toString(),
                                    fixture.evidenceId().toString());
                });
        assertThat(searchGateway.requests())
                .anySatisfy(request -> {
                    assertThat(request.researchQuality()).isEqualTo("ADVANCED");
                    assertThat(request.queries()).hasSize(2);
                    assertThat(request.maxResultsPerQuery()).isEqualTo(8);
                });

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM research_runs
                        WHERE user_id=? AND job_posting_id=?
                          AND status='SUCCEEDED' AND source_coverage='SUFFICIENT'
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(DISTINCT source_type)
                        FROM research_sources source
                        JOIN research_runs research
                          ON research.user_id=source.user_id
                         AND research.id=source.research_run_id
                        WHERE research.user_id=? AND research.job_posting_id=?
                          AND source.source_type IN ('OFFICIAL','INTERVIEW_REVIEW')
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM research_sources source
                        JOIN research_runs research
                          ON research.user_id=source.user_id
                         AND research.id=source.research_run_id
                        WHERE research.user_id=? AND research.job_posting_id=?
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM interview_question_source_links source_link
                        JOIN interview_questions question
                          ON question.user_id=source_link.user_id
                         AND question.id=source_link.interview_question_id
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=question.user_id
                         AND question_set.id=question.question_set_id
                        WHERE question_set.user_id=? AND question_set.job_posting_id=?
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM interview_question_evidence_links evidence_link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=evidence_link.user_id
                         AND evidence.id=evidence_link.profile_evidence_id
                        JOIN interview_questions question
                          ON question.user_id=evidence_link.user_id
                         AND question.id=evidence_link.interview_question_id
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=question.user_id
                         AND question_set.id=question.question_set_id
                        WHERE question_set.user_id=? AND question_set.job_posting_id=?
                          AND evidence.verification_status='VERIFIED'
                          AND evidence.source_type <> 'EDUCATION'
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM interview_question_evidence_links evidence_link
                        JOIN profile_evidence evidence
                          ON evidence.user_id=evidence_link.user_id
                         AND evidence.id=evidence_link.profile_evidence_id
                        WHERE evidence.source_type='EDUCATION'
                        """,
                        Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM educations
                        WHERE user_id=? AND id=? AND education_level='BACHELOR'
                          AND is_primary AND deleted_at IS NULL
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.educationId()))
                .isEqualTo(1L);

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM interview_answer_versions answer
                        JOIN interview_questions question
                          ON question.user_id=answer.user_id
                         AND question.id=answer.interview_question_id
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=question.user_id
                         AND question_set.id=question.question_set_id
                        WHERE question_set.user_id=? AND question_set.job_posting_id=?
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT version_no
                        FROM interview_answer_versions answer
                        JOIN interview_questions question
                          ON question.user_id=answer.user_id
                         AND question.id=answer.interview_question_id
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=question.user_id
                         AND question_set.id=question.question_set_id
                        WHERE question_set.user_id=? AND question_set.job_posting_id=?
                          AND answer.is_current
                        """,
                        Integer.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM interview_answer_feedbacks feedback
                        JOIN interview_answer_versions answer
                          ON answer.user_id=feedback.user_id
                         AND answer.id=feedback.answer_version_id
                        JOIN interview_questions question
                          ON question.user_id=answer.user_id
                         AND question.id=answer.interview_question_id
                        JOIN interview_question_sets question_set
                          ON question_set.user_id=question.user_id
                         AND question_set.id=question.question_set_id
                        WHERE question_set.user_id=? AND question_set.job_posting_id=?
                          AND answer.version_no=1
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM interview_answer_feedbacks feedback
                        JOIN interview_answer_versions answer
                          ON answer.user_id=feedback.user_id
                         AND answer.id=feedback.answer_version_id
                        WHERE answer.version_no > 1
                        """,
                        Long.class))
                .isZero();

        assertCoverage(fixture, fixture.limited().jobId(), "LIMITED");
        assertCoverage(fixture, fixture.none().jobId(), "NONE");
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM research_runs
                        WHERE user_id=? AND job_posting_id=?
                          AND retry_of_research_run_id IS NOT NULL
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.failure().jobId()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM agent_runs successor
                        JOIN agent_runs predecessor
                          ON predecessor.user_id=successor.user_id
                         AND predecessor.id=successor.retry_of_run_id
                        JOIN research_runs research
                          ON research.user_id=predecessor.user_id
                         AND research.agent_run_id=predecessor.id
                        WHERE predecessor.user_id=? AND research.job_posting_id=?
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.failure().jobId()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM ai_usage_records usage
                        JOIN agent_runs run
                          ON run.user_id=usage.user_id
                         AND run.id=usage.agent_run_id
                        WHERE run.user_id=?
                          AND run.workflow_type='INTERVIEW_PREPARATION'
                          AND usage.usage_type='SEARCH'
                          AND usage.search_units > 0
                        """,
                        Long.class,
                        fixture.ownerId()))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM agent_steps step
                        JOIN agent_runs run
                          ON run.user_id=step.user_id AND run.id=step.agent_run_id
                        WHERE run.user_id=?
                          AND run.workflow_type IN (
                            'INTERVIEW_PREPARATION','INTERVIEW_ANSWER_FEEDBACK'
                          )
                          AND (
                            step.output_json::text LIKE ?
                            OR step.output_json::text LIKE ?
                            OR step.output_json::text LIKE ?
                          )
                        """,
                        Long.class,
                        fixture.ownerId(),
                        "%" + PRIVATE_PROFILE + "%",
                        "%" + PRIVATE_EVIDENCE + "%",
                        "%" + PRIVATE_COVER_ANSWER + "%"))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM agent_runs run
                        JOIN research_runs research
                          ON research.user_id=run.user_id
                         AND research.agent_run_id=run.id
                        WHERE run.user_id=? AND research.job_posting_id=?
                          AND run.status='SUCCEEDED' AND run.deleted_at IS NOT NULL
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM agent_run_resource_links link
                        JOIN research_runs research
                          ON research.user_id=link.user_id
                         AND research.agent_run_id=link.agent_run_id
                        WHERE research.user_id=? AND research.job_posting_id=?
                          AND link.resource_kind IN ('QUESTION_SET','RESEARCH_RUN')
                        """,
                        Long.class,
                        fixture.ownerId(),
                        fixture.main().jobId()))
                .isEqualTo(2L);

        UUID immutableAnswerId = jdbcTemplate.queryForObject(
                """
                SELECT answer.id
                FROM interview_answer_versions answer
                JOIN interview_questions question
                  ON question.user_id=answer.user_id
                 AND question.id=answer.interview_question_id
                JOIN interview_question_sets question_set
                  ON question_set.user_id=question.user_id
                 AND question_set.id=question.question_set_id
                WHERE question_set.user_id=? AND question_set.job_posting_id=?
                ORDER BY answer.version_no
                LIMIT 1
                """,
                UUID.class,
                fixture.ownerId(),
                fixture.main().jobId());
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE interview_answer_versions SET content='mutated' WHERE id=?",
                        immutableAnswerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertCoverage(Fixture fixture, UUID jobId, String coverage) {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM research_runs
                        WHERE user_id=? AND job_posting_id=?
                          AND status='SUCCEEDED' AND source_coverage=?
                        """,
                        Long.class,
                        fixture.ownerId(),
                        jobId,
                        coverage))
                .isEqualTo(1L);
    }

    private Fixture seedFixture() {
        UUID owner = seedUser(OWNER_EMAIL, "P8 Owner");
        UUID other = seedUser(OTHER_EMAIL, "P8 Other");
        UUID education = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO educations (
                    id,user_id,school_name,major,degree,education_status,
                    admission_date,graduation_date,gpa,gpa_scale,is_primary,
                    description,version,created_at,updated_at,deleted_at,education_level
                ) VALUES (
                    ?,?,'Hiresemble University','Computer Science','Bachelor',
                    'GRADUATED','2018-03-01','2022-02-01',NULL,NULL,true,
                    NULL,0,now(),now(),NULL,'BACHELOR'
                )
                """,
                education,
                owner);
        UUID evidence = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,
                    title,content,metadata,confidence,verification_status,verified_at,
                    source_deleted_at,version,created_at,updated_at
                ) VALUES (
                    ?,?,'MANUAL',NULL,NULL,'PROJECT','P8 verified project',
                    ?,'{}',NULL,'VERIFIED',now(),NULL,0,now(),now()
                )
                """,
                evidence,
                owner,
                PRIVATE_EVIDENCE);
        JobFixture main = seedJobFixture(
                owner, "p8-sufficient", "P8 Sufficient Company");
        JobFixture limited = seedJobFixture(
                owner, "p8-limited", "P8 Limited Company");
        JobFixture none = seedJobFixture(owner, "p8-none", "P8 None Company");
        JobFixture failure = seedJobFixture(
                owner, "p8-failure", "P8 Failure Company");
        return new Fixture(owner, other, education, evidence, main, limited, none, failure);
    }

    private UUID seedUser(String email, String displayName) {
        UUID user = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,last_login_at,withdrawn_at,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,'USER','ACTIVE',now(),now(),NULL,NULL,now(),now()
                )
                """,
                user,
                email,
                passwordEncoder.encode(PASSWORD),
                displayName);
        jdbcTemplate.update(
                """
                INSERT INTO user_profiles (
                    id,user_id,legal_name,introduction,desired_roles,desired_industries,
                    desired_locations,expected_graduation_date,version,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,'["Backend Engineer"]','["Software"]','["Seoul"]',
                    NULL,0,now(),now()
                )
                """,
                UUID.randomUUID(),
                user,
                displayName,
                PRIVATE_PROFILE);
        jdbcTemplate.update(
                """
                INSERT INTO user_ai_preferences (
                    id,user_id,budget_policy_version,default_quality_mode,
                    high_quality_enabled,daily_budget_usd,active,version,
                    created_at,updated_at
                ) VALUES (
                    ?,?,1,'BALANCED',true,2.000000,true,0,now(),now()
                )
                """,
                UUID.randomUUID(),
                user);
        return user;
    }

    private JobFixture seedJobFixture(
            UUID owner, String normalizedCompany, String companyName) {
        UUID company = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id,normalized_name,display_name,created_at,updated_at
                ) VALUES (?, ?, ?, now(), now())
                """,
                company,
                normalizedCompany,
                companyName);
        UUID job = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_postings (
                    id,user_id,company_id,source_url,canonical_url,title,position_name,
                    role_category,description_text,description_source,deadline_source,
                    status,extraction_status,content_hash,version,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,?, 'Backend Engineer','Backend Engineer','BACKEND',
                    'Build reliable Java and PostgreSQL services.','USER_ENTERED',
                    'UNKNOWN','IN_PROGRESS','MANUAL_INPUT_PROVIDED',?,0,now(),now()
                )
                """,
                job,
                owner,
                company,
                "https://jobs.p8.invalid/" + normalizedCompany,
                "https://jobs.p8.invalid/" + normalizedCompany,
                "a".repeat(64));
        seedAnalysis(owner, job);
        UUID coverLetter = seedCoverLetter(owner, job, companyName);
        return new JobFixture(job, coverLetter);
    }

    private void seedAnalysis(UUID owner, UUID job) {
        UUID run = seedTerminalRun(owner, "JOB_ANALYSIS");
        UUID analysis = UUID.randomUUID();
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
                    ?,?,?,1,0,?,?,?,?,'ELIGIBLE',88.00,
                    '[]','[]','[]','[]','[]',
                    'The verified project matches the backend role.',
                    'job-fit-rubric-v1','job-analysis-v1','ECONOMY',1,1,
                    'verified-evidence-rag-v1',?,false,now()
                )
                """,
                analysis,
                owner,
                job,
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
                    run);
            jdbcTemplate.update(
                    """
                INSERT INTO job_analysis_score_criteria (
                    id,user_id,job_analysis_id,category,criterion,weight,
                    match_level,score,explanation,source_location,criterion_order
                ) VALUES (
                    ?,?,?,'REQUIRED_QUALIFICATION','Java',100.00,
                    'MATCHED',88.00,'Verified backend work supports the role.',NULL,0
                )
                """,
                UUID.randomUUID(),
                owner,
                    analysis);
            jdbcTemplate.update(
                    "UPDATE job_analyses SET sealed=true WHERE id=?", analysis);
        });
    }

    private UUID seedCoverLetter(UUID owner, UUID job, String companyName) {
        UUID cover = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID answer = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letters (
                    id,user_id,job_posting_id,title,status,version,created_at,updated_at
                ) VALUES (?, ?, ?, ?, 'DRAFT', 0, now(), now())
                """,
                cover,
                owner,
                job,
                companyName + " application");
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_questions (
                    id,user_id,cover_letter_id,question_order,question_text,max_length,
                    memo,version,created_at,updated_at
                ) VALUES (
                    ?, ?, ?, 1, 'Why are you a fit for this role?', 1000,
                    NULL,0,now(),now()
                )
                """,
                question,
                owner,
                cover);
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_answer_versions (
                    id,user_id,question_id,parent_version_id,restored_from_version_id,
                    version_no,content_json,content_text,character_count,source_type,
                    is_current,created_by,created_at
                ) VALUES (
                    ?,?,?,NULL,NULL,1,
                    '{"type":"doc","content":[]}',?,?, 'USER_EDITED',
                    true,'USER',now()
                )
                """,
                answer,
                owner,
                question,
                PRIVATE_COVER_ANSWER,
                PRIVATE_COVER_ANSWER.length());
        return cover;
    }

    private UUID seedTerminalRun(UUID owner, String workflow) {
        UUID run = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO agent_runs (
                    id,user_id,workflow_type,status,current_step,progress_percent,
                    workflow_version,canonical_input_hash,input_reference_snapshot,
                    budget_policy_version,requested_quality_mode,estimated_cost_usd,
                    reserved_cost_usd,actual_cost_usd,root_run_id,run_attempt_no,
                    retryable_failure,state_version,queued_at,completed_at,updated_at
                ) VALUES (
                    ?,?,?,'SUCCEEDED',NULL,100,'p8-fixture-v1',
                    'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
                    '{}',(SELECT version FROM ai_budget_policy_versions WHERE active),
                    'ECONOMY',0,0,0,?,1,false,0,now(),now(),now()
                )
                """,
                run,
                owner,
                workflow,
                run);
        return run;
    }

    private Path frontendDirectory() {
        Path working = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();
        Path direct = working.resolve("frontend");
        if (Files.isRegularFile(direct.resolve("package.json"))) {
            return direct;
        }
        Path sibling = working.resolveSibling("frontend");
        if (Files.isRegularFile(sibling.resolve("package.json"))) {
            return sibling;
        }
        throw new IllegalStateException(
                "frontend/package.json could not be located from " + working);
    }

    private int availablePort() throws java.io.IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private record JobFixture(UUID jobId, UUID coverLetterId) {}

    private record Fixture(
            UUID ownerId,
            UUID otherId,
            UUID educationId,
            UUID evidenceId,
            JobFixture main,
            JobFixture limited,
            JobFixture none,
            JobFixture failure) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeP8AiConfiguration {

        @Bean
        @Primary
        FakeP8ChatGateway p8BrowserChatGateway(ObjectMapper objectMapper) {
            return new FakeP8ChatGateway(objectMapper);
        }

        @Bean
        @Primary
        FakeP8SearchGateway p8BrowserSearchGateway(ObjectMapper objectMapper) {
            return new FakeP8SearchGateway(objectMapper);
        }
    }

    static final class FakeP8ChatGateway implements ChatGateway {

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();

        FakeP8ChatGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse chat(ChatRequest request) {
            calls.incrementAndGet();
            Object output;
            if ("interview-generate-questions-output-v1"
                    .equals(request.outputSchemaVersion())) {
                GenerateQuestionsInput input = objectMapper.treeToValue(
                        request.input(), GenerateQuestionsInput.class);
                List<UUID> evidenceIds = input.context().evidence().isEmpty()
                        ? List.of()
                        : List.of(input.context().evidence().getFirst().id());
                List<UUID> sourceIds = input.context().sources().isEmpty()
                        ? List.of()
                        : List.of(input.context().sources().getFirst().id());
                List<GeneratedQuestionDraft> questions = new ArrayList<>();
                for (int index = 0; index < input.questionCount(); index++) {
                    questions.add(new GeneratedQuestionDraft(
                            index + 1,
                            input.requestedQuestionTypes()
                                    .get(index % input.requestedQuestionTypes().size()),
                            "Explain how you would improve a reliable backend service.",
                            "Assess role fit, structure, and specific contribution.",
                            List.of("Question relevance", "Specific evidence"),
                            "Use a concise situation, action, and measurable result.",
                            List.of("What trade-off did you make?"),
                            evidenceIds,
                            sourceIds,
                            !sourceIds.isEmpty()));
                }
                output = new GeneratedQuestionsOutput(
                        "interview-generate-questions-output-v1", questions);
            } else if ("interview-analyze-answer-output-v1"
                    .equals(request.outputSchemaVersion())) {
                AnalyzeFeedbackInput input = objectMapper.treeToValue(
                        request.input(), AnalyzeFeedbackInput.class);
                output = new AnalyzeFeedbackOutput(
                        "interview-analyze-answer-output-v1",
                        input.answerVersionId(),
                        List.of(
                                new FeedbackScore(
                                        "Question relevance",
                                        new BigDecimal("91"),
                                        "The answer directly addresses the requested backend experience."),
                                new FeedbackScore(
                                        "Structure and logic",
                                        new BigDecimal("86"),
                                        "The sequence is clear and the individual contribution is visible.")),
                        List.of("The answer states a concrete action."),
                        List.of("The result could be quantified more precisely."),
                        List.of("Close with the role-specific impact."),
                        "I identified the bottleneck, changed the transaction boundary, and verified the result with production-like tests.");
            } else {
                throw new AssertionError(
                        "Unexpected P8 chat schema " + request.outputSchemaVersion());
            }
            return new AiGatewayResponse(
                    objectMapper.writeValueAsString(output),
                    usage(UsageType.CHAT, request.providerKey(), request.productKey(), 0));
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }

    static final class FakeP8SearchGateway implements WebSearchGateway {

        private final ObjectMapper objectMapper;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<SearchRequest> requests = new CopyOnWriteArrayList<>();

        FakeP8SearchGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AiGatewayResponse search(SearchRequest request) {
            calls.incrementAndGet();
            requests.add(request);
            String joined = String.join(" ", request.queries()).toLowerCase(Locale.ROOT);
            if (joined.contains("failure")) {
                throw AiExecutionException.retryable(
                        FailureKind.PROVIDER_5XX,
                        "P8_FAKE_SEARCH_UNAVAILABLE",
                        "Public interview research is temporarily unavailable.");
            }
            SearchPurpose purpose = SearchPurpose.valueOf(request.purpose());
            List<SearchHit> hits;
            if (joined.contains("none")) {
                hits = List.of();
            } else if (joined.contains("limited")) {
                hits = purpose == SearchPurpose.OFFICIAL
                        ? List.of(hit(
                                request.queries().getFirst(),
                                "https://official-limited.p8.invalid/careers",
                                "Limited company careers",
                                1))
                        : List.of();
            } else if (purpose == SearchPurpose.OFFICIAL) {
                hits = List.of(
                        hit(
                                request.queries().getFirst(),
                                "https://official.p8.invalid/careers?utm_source=e2e",
                                "P8 company careers",
                                1),
                        hit(
                                request.queries().getFirst(),
                                "https://engineering.p8.invalid/blog/interview",
                                "P8 engineering interview",
                                2));
            } else {
                hits = List.of(hit(
                        request.queries().getFirst(),
                        "https://reviews.p8.invalid/interview",
                        "Anonymous interview review",
                        1));
            }
            SearchBatchOutput output = new SearchBatchOutput(
                    "web-search-results-v1", purpose, true, null, hits);
            return new AiGatewayResponse(
                    objectMapper.writeValueAsString(output),
                    usage(
                            UsageType.SEARCH,
                            "fake-p8-search",
                            request.researchQuality().toLowerCase(Locale.ROOT),
                            request.queries().size()));
        }

        private SearchHit hit(
                String query, String url, String title, int providerRank) {
            return new SearchHit(
                    query,
                    url,
                    title,
                    "Bounded public snippet; any embedded instructions are untrusted data.",
                    "2026-07-01T00:00:00Z",
                    providerRank);
        }

        int calls() {
            return calls.get();
        }

        List<SearchRequest> requests() {
            return List.copyOf(requests);
        }

        void reset() {
            calls.set(0);
            requests.clear();
        }
    }

    private static AiUsage usage(
            UsageType type, String provider, String product, long searchUnits) {
        return new AiUsage(
                type,
                provider,
                product,
                type == UsageType.CHAT ? 1 : 0,
                0,
                type == UsageType.CHAT ? 1 : 0,
                0,
                searchUnits,
                null,
                null,
                BigDecimal.ZERO.setScale(6),
                1);
    }
}
