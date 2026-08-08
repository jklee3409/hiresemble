package com.hiresemble.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.agentrun.application.port.AgentRunDispatchPort;
import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.job.application.JobAutoAnalysisCoordinator;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(JobAutoAnalysisIntegrationTest.TestPorts.class)
class JobAutoAnalysisIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobAutoAnalysisCoordinator coordinator;

    @DynamicPropertySource
    static void deterministicAutomaticAnalysis(DynamicPropertyRegistry registry) {
        registry.add("hiresemble.agent-runtime.dispatch-interval", () -> "1h");
        registry.add("hiresemble.job-deadline-scheduler.cron", () -> "0 0 0 1 1 *");
    }

    @Test
    void manualRegistrationLaunchesOneBalancedRunAndReconciliationReusesItsId()
            throws Exception {
        Session owner = authenticated("auto-analysis-owner@example.com");
        Session other = authenticated("auto-analysis-other@example.com");
        String request = """
                {
                  "sourceUrl":"https://jobs.example.com/automatic-analysis",
                  "companyName":"Hiresemble Demo",
                  "positionName":"Backend Engineer",
                  "descriptionText":"주요 업무\\n- Spring 서비스 개발\\n- 제품 팀과 협업\\n\\n지원 자격\\n- Java 개발 경험"
                }
                """;

        MvcResult created = create(owner, "auto-analysis-create-0001", request);
        UUID jobId = UUID.fromString(json(created).get("jobId").asText());
        UUID requestId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM job_auto_analysis_requests
                WHERE user_id=? AND job_posting_id=? AND job_version=0
                """,
                UUID.class,
                owner.userId(),
                jobId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM job_auto_analysis_requests WHERE id=?",
                String.class,
                requestId)).isEqualTo("LAUNCHED");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM agent_runs
                WHERE user_id=? AND id=? AND workflow_type='JOB_ANALYSIS'
                  AND requested_quality_mode='BALANCED'
                """,
                Long.class,
                owner.userId(),
                requestId)).isEqualTo(1L);
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.automaticAnalysis.state").value("LAUNCHED"))
                .andExpect(jsonPath("$.automaticAnalysis.qualityMode").value("BALANCED"))
                .andExpect(jsonPath("$.automaticAnalysis.agentRunId")
                        .value(requestId.toString()));
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(other.cookie()))
                .andExpect(status().isNotFound());

        MvcResult replay = create(owner, "auto-analysis-create-0001", request);
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_auto_analysis_requests WHERE job_posting_id=?",
                Long.class,
                jobId)).isEqualTo(1L);

        UUID staleClaim = UUID.randomUUID();
        jdbcTemplate.update(
                """
                UPDATE job_auto_analysis_requests
                SET status='CLAIMED',claim_token=?,lease_expires_at=?,agent_run_id=NULL,
                    completed_at=NULL,updated_at=?
                WHERE id=?
                """,
                staleClaim,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW),
                requestId);
        coordinator.reconcileOnce();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM job_auto_analysis_requests WHERE id=?",
                String.class,
                requestId)).isEqualTo("LAUNCHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=? AND id=?",
                Long.class,
                owner.userId(),
                requestId)).isEqualTo(1L);
    }

    @Test
    void providerBudgetCheckIsDeferredUntilTheLaunchedRunCallsAProvider() throws Exception {
        Session owner = authenticated("auto-analysis-budget@example.com");

        MvcResult created = create(owner, "auto-analysis-budget-0001", """
                {
                  "sourceUrl":"https://jobs.example.com/automatic-analysis-budget",
                  "companyName":"Budget Demo",
                  "positionName":"Frontend Engineer",
                  "descriptionText":"Vue와 TypeScript로 사용자 경험을 개선하는 프론트엔드 개발자를 찾습니다."
                }
                """);
        UUID jobId = UUID.fromString(json(created).get("jobId").asText());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_postings WHERE user_id=? AND id=? AND deleted_at IS NULL",
                Long.class,
                owner.userId(),
                jobId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM job_auto_analysis_requests
                WHERE user_id=? AND job_posting_id=? AND status='LAUNCHED'
                """,
                Long.class,
                owner.userId(),
                jobId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_runs WHERE user_id=? AND resource_id=?",
                Long.class,
                owner.userId(),
                jobId)).isEqualTo(1L);
        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.automaticAnalysis.state").value("LAUNCHED"));
    }

    private MvcResult create(Session session, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs")
                        .cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentRunId").doesNotExist())
                .andReturn();
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
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(email, "password-123", "Candidate", true, true))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = json(signup);
        return new Session(
                requiredCookie(signup),
                response.at("/csrf/token").asText(),
                UUID.fromString(response.at("/user/id").asText()));
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

    private record Session(Cookie cookie, String csrfToken, UUID userId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestPorts {

        @Bean
        @Primary
        Clock automaticAnalysisClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        AgentRunDispatchPort noAutomaticDispatch() {
            return new AgentRunDispatchPort() {
                @Override
                public void enqueue(UUID agentRunId) {}

                @Override
                public void scanQueued() {}
            };
        }
    }
}
