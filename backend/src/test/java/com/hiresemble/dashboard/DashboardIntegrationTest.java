package com.hiresemble.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(DashboardIntegrationTest.TestClock.class)
class DashboardIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-02T03:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void dashboardKeepsOwnerScopeSeoulMonthBoundariesAndClosedJobsOut() throws Exception {
        Session owner = authenticated("dashboard-owner@example.com", "이종규");
        Session other = authenticated("dashboard-other@example.com", "다른 사용자");
        prepareProfile(owner.userId());

        insertJob(owner.userId(), "Owner Start", "IN_PROGRESS", "2026-07-31T15:00:00Z", false);
        insertJob(owner.userId(), "Owner Same Day", "SUBMITTED", "2026-08-01T01:00:00Z", false);
        insertJob(owner.userId(), "Owner Closed", "CLOSED", "2026-08-10T00:00:00Z", false);
        insertJob(owner.userId(), "Owner Next Month", "IN_PROGRESS", "2026-08-31T15:00:00Z", false);
        insertJob(other.userId(), "Other Private", "IN_PROGRESS", "2026-08-01T03:00:00Z", false);

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard")
                        .queryParam("month", "2026-08")
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.profile.displayName").value("이종규"))
                .andExpect(jsonPath("$.profile.legalName").value("이종규"))
                .andExpect(jsonPath("$.profile.completionPercent").value(100))
                .andExpect(jsonPath("$.profile.primaryEducation.schoolName").value("한국대학교"))
                .andExpect(jsonPath("$.jobs.registeredCount").value(4))
                .andExpect(jsonPath("$.jobs.preparingCount").value(2))
                .andExpect(jsonPath("$.jobs.submittedCount").value(1))
                .andExpect(jsonPath("$.deadlineDays.length()").value(1))
                .andExpect(jsonPath("$.deadlineDays[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.deadlineDays[0].count").value(2))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response)
                .contains("Owner Start", "Owner Same Day")
                .doesNotContain("Owner Closed", "Owner Next Month", "Other Private");
    }

    @Test
    void careerGuidesExposeOnlyPublishedPostsInDisplayOrder() throws Exception {
        Session owner = authenticated("guide-reader@example.com", "가이드 사용자");
        jdbcTemplate.update("""
                INSERT INTO career_guide_posts (
                    id,slug,status,display_order,category,title,summary,body,
                    published_at,version,created_at,updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                "draft-hidden",
                "DRAFT",
                0,
                "숨김",
                "게시 전 글",
                "아직 게시되지 않은 요약",
                "아직 게시되지 않은 본문",
                null,
                0,
                utc(NOW),
                utc(NOW));

        MvcResult result = mockMvc.perform(get("/api/v1/career-guides").cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].displayOrder").value(10))
                .andExpect(jsonPath("$[4].displayOrder").value(50))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("공고에서 진짜 봐야 할 다섯 줄", "제출 버튼 누르기 전 마지막 10분")
                .doesNotContain("게시 전 글");
    }

    @Test
    void dashboardValidatesMonthAndRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").queryParam("month", "2026-8"))
                .andExpect(status().isUnauthorized());

        Session owner = authenticated("dashboard-validation@example.com", "검증 사용자");
        mockMvc.perform(get("/api/v1/dashboard")
                        .queryParam("month", "2026-8")
                        .cookie(owner.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void prepareProfile(UUID userId) {
        jdbcTemplate.update("""
                UPDATE user_profiles
                SET legal_name='이종규',
                    desired_roles='["백엔드 개발자"]'::jsonb,
                    desired_industries='["IT 서비스"]'::jsonb,
                    desired_locations='["서울"]'::jsonb,
                    updated_at=?
                WHERE user_id=?
                """, utc(NOW), userId);
        jdbcTemplate.update("""
                INSERT INTO educations (
                    id,user_id,school_name,major,degree,education_status,
                    admission_date,graduation_date,gpa,gpa_scale,is_primary,description,
                    version,created_at,updated_at,deleted_at,education_level
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                userId,
                "한국대학교",
                "컴퓨터공학",
                "학사",
                "GRADUATED",
                null,
                null,
                null,
                null,
                true,
                null,
                0,
                utc(NOW),
                utc(NOW),
                null,
                "BACHELOR");
    }

    private void insertJob(
            UUID userId, String title, String status, String deadlineAt, boolean deleted) {
        UUID id = UUID.randomUUID();
        Instant deadline = Instant.parse(deadlineAt);
        boolean closed = status.equals("CLOSED");
        jdbcTemplate.update("""
                INSERT INTO job_postings (
                    id,user_id,company_id,source_url,canonical_url,title,position_name,
                    role_category,employment_type,location,description_text,description_source,
                    deadline_at,deadline_source,deadline_confidence,status,extraction_status,
                    submitted_at,closed_at,closed_reason,content_hash,latest_agent_run_id,
                    company_user_override,title_user_override,position_user_override,
                    deadline_user_override,version,created_at,updated_at,deleted_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                userId,
                null,
                "https://example.com/jobs/" + id,
                "https://example.com/jobs/" + id,
                title,
                title,
                null,
                null,
                "서울",
                "지원 준비에 필요한 공고 본문입니다.",
                "USER_ENTERED",
                utc(deadline),
                "USER_ENTERED",
                null,
                status,
                "MANUAL_INPUT_PROVIDED",
                status.equals("SUBMITTED") ? utc(NOW) : null,
                closed ? utc(NOW) : null,
                closed ? "USER_CLOSED" : null,
                "a".repeat(64),
                null,
                false,
                false,
                false,
                true,
                0,
                utc(NOW.minusSeconds(60)),
                utc(NOW),
                deleted ? utc(NOW) : null);
    }

    private Session authenticated(String email, String displayName) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie anonymousCookie = requiredCookie(csrf);
        String anonymousToken = json(csrf).get("token").asText();
        String body = objectMapper.writeValueAsString(
                new SignupRequest(email, "password-123", displayName, true, true));
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(anonymousCookie)
                        .header("X-CSRF-TOKEN", anonymousToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return new Session(
                requiredCookie(signup),
                UUID.fromString(json(signup).at("/user/id").asText()));
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) throw new AssertionError("SESSION cookie missing");
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private record Session(Cookie cookie, UUID userId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {
        @Bean
        @Primary
        Clock dashboardClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
