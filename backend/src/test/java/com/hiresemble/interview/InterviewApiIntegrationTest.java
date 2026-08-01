package com.hiresemble.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.port.BudgetReservationPort;
import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class InterviewApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private BudgetReservationPort budgetReservationPort;

    @Test
    void preparationUsesPrerequisitesOwner404ValidationCsrfAndDurableIdempotency()
            throws Exception {
        Session owner = authenticated("p8-api-owner@example.com");
        Session other = authenticated("p8-api-other@example.com");
        UUID job = seedJob(owner.userId(), "p8-api-owner", "Example");

        mockMvc.perform(post("/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-missing-prerequisite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(UUID.randomUUID(), "BASIC", "BALANCED", "TECHNICAL", 3)))
                .andExpect(status().isNotFound());

        seedAnalysis(owner.userId(), job);
        CoverFixture cover = seedCoverWithCurrentAnswer(owner.userId(), job);
        MvcResult accepted = mockMvc.perform(post(
                                "/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-preparation-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "BASIC",
                                "BALANCED",
                                "TECHNICAL",
                                3)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.questionSetId").isString())
                .andExpect(jsonPath("$.researchRunId").isString())
                .andExpect(jsonPath("$.agentRunId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();
        JsonNode acceptedJson = json(accepted);
        UUID questionSet = UUID.fromString(acceptedJson.get("questionSetId").asText());
        UUID research = UUID.fromString(acceptedJson.get("researchRunId").asText());

        mockMvc.perform(post("/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-preparation-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "BASIC",
                                "BALANCED",
                                "TECHNICAL",
                                3)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.questionSetId").value(questionSet.toString()))
                .andExpect(jsonPath("$.researchRunId").value(research.toString()));

        mockMvc.perform(post("/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-follow-up-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "BASIC",
                                "BALANCED",
                                "FOLLOW_UP",
                                3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("Idempotency-Key", "p8-csrf-required")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "BASIC",
                                "BALANCED",
                                "TECHNICAL",
                                3)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/interview-question-sets/" + questionSet)
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/research-runs/" + research).cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/research-runs/" + research + "/retry")
                        .cookie(other.cookie())
                        .header("X-CSRF-TOKEN", other.csrfToken())
                        .header("Idempotency-Key", "p8-research-foreign-high")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qualityMode":"HIGH_QUALITY"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/interview-question-sets")
                        .cookie(owner.cookie())
                        .queryParam("sort", "title,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/research-runs/" + research + "/sources")
                        .cookie(owner.cookie())
                        .queryParam("sort", "providerRank,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void answerCasFeedbackAndHistoryDeletePreserveEveryP8DomainResource()
            throws Exception {
        Session owner = authenticated("p8-api-history@example.com");
        Session other = authenticated("p8-api-history-other@example.com");
        UUID job = seedJob(owner.userId(), "p8-api-history", "History Co");
        seedAnalysis(owner.userId(), job);
        CoverFixture cover = seedCoverWithCurrentAnswer(owner.userId(), job);
        JsonNode preparation = json(mockMvc.perform(post(
                                "/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-history-preparation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "ADVANCED",
                                "ECONOMY",
                                "BEHAVIORAL",
                                1)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID questionSet = UUID.fromString(preparation.get("questionSetId").asText());
        UUID research = UUID.fromString(preparation.get("researchRunId").asText());
        UUID preparationRun = UUID.fromString(preparation.get("agentRunId").asText());
        UUID question = seedInterviewQuestion(owner.userId(), questionSet);

        MvcResult firstResponse = mockMvc.perform(post(
                                "/api/v1/interview-questions/" + question + "/answer-versions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"First immutable answer.","parentVersionId":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.sourceType").value("USER_EDITED"))
                .andReturn();
        UUID first = UUID.fromString(json(firstResponse).get("id").asText());

        mockMvc.perform(post("/api/v1/interview-answer-versions/" + first + "/feedback")
                        .cookie(other.cookie())
                        .header("X-CSRF-TOKEN", other.csrfToken())
                        .header("Idempotency-Key", "p8-feedback-foreign-high")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qualityMode":"HIGH_QUALITY"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/interview-answer-versions/" + first + "/feedback")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-feedback-high-disabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qualityMode":"HIGH_QUALITY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUALITY_MODE_NOT_SUPPORTED"));
        jdbcTemplate.update(
                """
                UPDATE user_ai_preferences
                SET high_quality_enabled=true,version=version+1,updated_at=now()
                WHERE user_id=?
                """,
                owner.userId());
        JsonNode highQualityAccepted = json(mockMvc.perform(post(
                                "/api/v1/interview-answer-versions/" + first + "/feedback")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-feedback-high-enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qualityMode":"HIGH_QUALITY"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID highQualityRun =
                UUID.fromString(highQualityAccepted.get("agentRunId").asText());
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT requested_quality_mode
                        FROM agent_runs
                        WHERE user_id=? AND id=?
                        """,
                        String.class,
                        owner.userId(),
                        highQualityRun))
                .isEqualTo("HIGH_QUALITY");
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM ai_budget_reservations
                        WHERE user_id=? AND agent_run_id=? AND status='RESERVED'
                        """,
                        Long.class,
                        owner.userId(),
                        highQualityRun))
                .isEqualTo(1L);

        mockMvc.perform(post("/api/v1/interview-questions/" + question + "/answer-versions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Unsaved stale answer.","parentVersionId":null}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("parentVersionId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("STALE"));

        MvcResult secondResponse = mockMvc.perform(post(
                                "/api/v1/interview-questions/" + question + "/answer-versions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"Explicitly reapplied answer.",
                                  "parentVersionId":"%s"
                                }
                                """.formatted(first)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andReturn();
        UUID second = UUID.fromString(json(secondResponse).get("id").asText());
        mockMvc.perform(get("/api/v1/interview-questions/" + question + "/answer-versions")
                        .cookie(owner.cookie())
                        .queryParam("page", "0")
                        .queryParam("size", "100")
                        .queryParam("sort", "versionNo,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[0].isCurrent").value(true))
                .andExpect(jsonPath("$.items[1].id").value(first.toString()))
                .andExpect(jsonPath("$.items[1].isCurrent").value(false));

        JsonNode feedbackAccepted = json(mockMvc.perform(post(
                                "/api/v1/interview-answer-versions/" + first + "/feedback")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-feedback-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qualityMode":"BALANCED"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID feedbackRun = UUID.fromString(feedbackAccepted.get("agentRunId").asText());
        mockMvc.perform(post("/api/v1/interview-answer-versions/" + first + "/feedback")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-feedback-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qualityMode":"BALANCED"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.agentRunId").value(feedbackRun.toString()));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM interview_answer_feedbacks WHERE user_id=?",
                        Long.class,
                        owner.userId()))
                .isZero();

        markRunSucceeded(feedbackRun);
        UUID feedback = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer_feedbacks (
                    id,user_id,answer_version_id,scores,strengths,weaknesses,
                    suggestions,revised_example,agent_run_id,created_at
                ) VALUES (
                    ?,?,?,'[{"criterion":"clarity","score":85,"explanation":"Clear"}]',
                    '["Concrete"]','["Can be shorter"]','["Lead with impact"]',
                    'A concise revised answer.',?,now()
                )
                """,
                feedback,
                owner.userId(),
                first,
                feedbackRun);
        mockMvc.perform(get("/api/v1/interview-answer-versions/" + first + "/feedbacks")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(feedback.toString()))
                .andExpect(jsonPath("$.items[0].answerVersionId").value(first.toString()));
        mockMvc.perform(get("/api/v1/interview-answer-versions/" + second + "/feedbacks")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        jdbcTemplate.update(
                """
                UPDATE research_runs
                SET status='SUCCEEDED',source_coverage='NONE',
                    summary='No usable public sources.',retryable=false,
                    completed_at=GREATEST(now(),started_at),
                    updated_at=GREATEST(now(),started_at)
                WHERE user_id=? AND id=?
                """,
                owner.userId(),
                research);
        markRunSucceeded(preparationRun);
        mockMvc.perform(delete("/api/v1/agent-runs/" + preparationRun)
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/agent-runs/" + preparationRun)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/agent-runs/" + preparationRun + "/events")
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/agent-runs/" + preparationRun + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-deleted-generic-retry"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/research-runs/" + research + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-deleted-research-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/research-runs/" + research).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCoverage").value("NONE"));
        mockMvc.perform(get("/api/v1/interview-question-sets/" + questionSet)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].currentAnswer.id").value(second.toString()));
        mockMvc.perform(get("/api/v1/interview-answer-versions/" + first + "/feedbacks")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(feedback.toString()));
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM agent_run_resource_links
                        WHERE user_id=? AND agent_run_id=?
                        """,
                        Long.class,
                        owner.userId(),
                        preparationRun))
                .isEqualTo(2L);
    }

    @Test
    void researchAndGenericRetryShareOneSuccessorAndQueuedCancellationLeavesAPlaceholder()
            throws Exception {
        Session owner = authenticated("p8-api-retry-cancel@example.com");
        UUID job = seedJob(owner.userId(), "p8-api-retry-cancel", "Retry Co");
        seedAnalysis(owner.userId(), job);
        CoverFixture cover = seedCoverWithCurrentAnswer(owner.userId(), job);

        JsonNode failedPreparation = json(mockMvc.perform(post(
                                "/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-retry-preparation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "BASIC",
                                "BALANCED",
                                "TECHNICAL",
                                2)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID predecessorRun =
                UUID.fromString(failedPreparation.get("agentRunId").asText());
        UUID predecessorResearch =
                UUID.fromString(failedPreparation.get("researchRunId").asText());
        UUID predecessorQuestionSet =
                UUID.fromString(failedPreparation.get("questionSetId").asText());
        markPreparationFailed(owner.userId(), predecessorRun, predecessorResearch);

        JsonNode resourceRetry = json(mockMvc.perform(post(
                                "/api/v1/research-runs/" + predecessorResearch + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-resource-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.retryOfResearchRunId")
                        .value(predecessorResearch.toString()))
                .andReturn());
        UUID successorRun = UUID.fromString(resourceRetry.get("agentRunId").asText());
        UUID successorResearch =
                UUID.fromString(resourceRetry.get("researchRunId").asText());
        UUID successorQuestionSet =
                UUID.fromString(resourceRetry.get("questionSetId").asText());

        mockMvc.perform(post("/api/v1/agent-runs/" + predecessorRun + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-generic-retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.agentRunId").value(successorRun.toString()))
                .andExpect(jsonPath("$.resourceId").value(successorQuestionSet.toString()));
        mockMvc.perform(post("/api/v1/research-runs/" + predecessorResearch + "/retry")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-resource-retry-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "researchQuality":"ADVANCED",
                                  "qualityMode":"BALANCED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("AGENT_RUN_RETRY_ALREADY_CREATED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM agent_runs WHERE retry_of_run_id=?",
                        Long.class,
                        predecessorRun))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM research_runs
                        WHERE user_id=? AND retry_of_research_run_id=?
                        """,
                        Long.class,
                        owner.userId(),
                        predecessorResearch))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM interview_question_sets
                        WHERE user_id=? AND research_run_id=?
                        """,
                        Long.class,
                        owner.userId(),
                        successorResearch))
                .isEqualTo(1L);
        assertThat(successorResearch).isNotEqualTo(predecessorResearch);
        assertThat(successorQuestionSet).isNotEqualTo(predecessorQuestionSet);

        JsonNode cancellablePreparation = json(mockMvc.perform(post(
                                "/api/v1/jobs/" + job + "/interview-preparations")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "p8-cancel-preparation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preparationBody(
                                cover.coverLetterId(),
                                "ADVANCED",
                                "ECONOMY",
                                "BEHAVIORAL",
                                1)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID cancelledRun =
                UUID.fromString(cancellablePreparation.get("agentRunId").asText());
        UUID cancelledResearch =
                UUID.fromString(cancellablePreparation.get("researchRunId").asText());
        UUID cancelledQuestionSet =
                UUID.fromString(cancellablePreparation.get("questionSetId").asText());
        JsonNode runDetail = json(mockMvc.perform(get("/api/v1/agent-runs/" + cancelledRun)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andReturn());
        long stateVersion = runDetail.get("stateVersion").asLong();

        mockMvc.perform(post("/api/v1/agent-runs/" + cancelledRun + "/cancel")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stateVersion":%d}
                                """.formatted(stateVersion)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/research-runs/" + cancelledResearch)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/interview-question-sets/" + cancelledQuestionSet)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").isEmpty());
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM research_sources
                        WHERE user_id=? AND research_run_id=?
                        """,
                        Long.class,
                        owner.userId(),
                        cancelledResearch))
                .isZero();
    }

    private String preparationBody(
            UUID coverLetterId,
            String researchQuality,
            String qualityMode,
            String questionType,
            int questionCount) {
        return """
                {
                  "coverLetterId":"%s",
                  "researchQuality":"%s",
                  "qualityMode":"%s",
                  "questionTypes":["%s"],
                  "questionCount":%d
                }
                """.formatted(
                coverLetterId, researchQuality, qualityMode, questionType, questionCount);
    }

    private Session authenticated(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = requiredCookie(csrf);
        String token = json(csrf).get("token").asText();
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(
                                email, "password-123", "Candidate", true, true))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = json(signup);
        return new Session(
                requiredCookie(signup),
                response.at("/csrf/token").asText(),
                UUID.fromString(response.at("/user/id").asText()));
    }

    private UUID seedJob(UUID owner, String key, String companyName) {
        UUID company = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO companies (
                    id,normalized_name,display_name,created_at,updated_at
                ) VALUES (?, ?, ?, now(), now())
                """,
                company,
                key,
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
                    'Build reliable Java services.','USER_ENTERED','UNKNOWN','IN_PROGRESS',
                    'MANUAL_INPUT_PROVIDED',?,0,now(),now()
                )
                """,
                job,
                owner,
                company,
                "https://jobs.example.com/" + key,
                "https://jobs.example.com/" + key,
                "a".repeat(64));
        return job;
    }

    private void seedAnalysis(UUID owner, UUID job) {
        UUID run = seedTerminalRun(owner, "JOB_ANALYSIS");
        UUID analysis = UUID.randomUUID();
        transaction().executeWithoutResult(status -> {
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
                        '[]','[]','[]','[]','[]','P8 analysis',
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
                        'MATCHED',80.00,'P8 criterion',NULL,0
                    )
                    """,
                    UUID.randomUUID(),
                    owner,
                    analysis);
            jdbcTemplate.update("UPDATE job_analyses SET sealed=true WHERE id=?", analysis);
        });
    }

    private CoverFixture seedCoverWithCurrentAnswer(UUID owner, UUID job) {
        UUID cover = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID answer = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO cover_letters (
                    id,user_id,job_posting_id,title,status,version,created_at,updated_at
                ) VALUES (?, ?, ?, 'Application', 'DRAFT', 0, now(), now())
                """,
                cover,
                owner,
                job);
        jdbcTemplate.update(
                """
                INSERT INTO cover_letter_questions (
                    id,user_id,cover_letter_id,question_order,question_text,max_length,
                    memo,version,created_at,updated_at
                ) VALUES (?, ?, ?, 1, 'Why this role?', 1000, NULL, 0, now(), now())
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
                    '{"type":"doc","content":[]}','I build reliable services.',26,
                    'USER_EDITED',true,'USER',now()
                )
                """,
                answer,
                owner,
                question);
        return new CoverFixture(cover, question, answer);
    }

    private UUID seedInterviewQuestion(UUID owner, UUID questionSet) {
        UUID question = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_questions (
                    id,user_id,question_set_id,question_order,question_type,question_text,
                    intent,evaluation_points,answer_guide,follow_up_questions,
                    source_based,created_at
                ) VALUES (
                    ?,?,?,1,'BEHAVIORAL','Describe a difficult incident.','Assess structure.',
                    '["clarity"]','Use STAR.','[]',false,now()
                )
                """,
                question,
                owner,
                questionSet);
        return question;
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
                    ?,?,?,'SUCCEEDED',NULL,100,'fixture-v1',
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

    private void markRunSucceeded(UUID run) {
        jdbcTemplate.update(
                """
                UPDATE agent_runs
                SET status='SUCCEEDED',progress_percent=100,reserved_cost_usd=0,
                    completed_at=GREATEST(now(),queued_at),
                    updated_at=GREATEST(now(),queued_at),
                    claim_token=NULL,claimed_by=NULL,lease_expires_at=NULL,heartbeat_at=NULL
                WHERE id=?
                """,
                run);
    }

    private void markPreparationFailed(
            UUID owner, UUID agentRunId, UUID researchRunId) {
        budgetReservationPort.releaseUnused(owner, agentRunId, Instant.now());
        jdbcTemplate.update(
                """
                UPDATE research_runs
                SET status='FAILED',source_coverage=NULL,
                    missing_coverage_topics='[]'::jsonb,summary=NULL,
                    retryable=true,safe_error_code='AI_SEARCH_TIMEOUT',
                    started_at=COALESCE(started_at,created_at),
                    completed_at=GREATEST(now(),created_at),
                    updated_at=GREATEST(now(),created_at)
                WHERE user_id=? AND id=?
                """,
                owner,
                researchRunId);
        jdbcTemplate.update(
                """
                UPDATE agent_runs
                SET status='FAILED',
                    error_code='AI_SEARCH_TIMEOUT',
                    error_message_safe='Public interview research timed out.',
                    retryable_failure=true,reserved_cost_usd=0,
                    completed_at=GREATEST(now(),queued_at),
                    state_version=state_version+1,
                    updated_at=GREATEST(now(),queued_at)
                WHERE user_id=? AND id=?
                """,
                owner,
                agentRunId);
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            throw new AssertionError("SESSION cookie missing");
        }
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record Session(Cookie cookie, String csrfToken, UUID userId) {}

    private record CoverFixture(
            UUID coverLetterId, UUID questionId, UUID answerVersionId) {}
}
