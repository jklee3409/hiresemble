package com.hiresemble.coverletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.auth.api.dto.SignupRequest;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class CoverLetterApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void publicEndpointsUseDirectDtosCasIdempotencyAndArchived409() throws Exception {
        Session owner = authenticated("cover-api-owner@example.com");
        Session other = authenticated("cover-api-other@example.com");
        UUID jobId = seedJob(owner.userId(), "cover-api");

        MvcResult created = mockMvc.perform(post("/api/v1/jobs/" + jobId + "/cover-letter")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "cover-api-create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend application"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();
        UUID coverId = UUID.fromString(json(created).get("id").asText());

        mockMvc.perform(post("/api/v1/jobs/" + jobId + "/cover-letter")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "cover-api-create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend application"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(coverId.toString()));

        mockMvc.perform(get("/api/v1/cover-letters/" + coverId).cookie(other.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/cover-letters")
                        .cookie(owner.cookie())
                        .queryParam("unsupported", "value"))
                .andExpect(status().isBadRequest());

        JsonNode firstQuestion = json(mockMvc.perform(post(
                                "/api/v1/cover-letters/" + coverId + "/questions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionOrder":1,
                                  "questionText":"첫 번째 문항",
                                  "maxLength":1000,
                                  "memo":null,
                                  "coverLetterVersion":0
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        UUID firstQuestionId = UUID.fromString(firstQuestion.get("id").asText());
        JsonNode secondQuestion = json(mockMvc.perform(post(
                                "/api/v1/cover-letters/" + coverId + "/questions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionOrder":2,
                                  "questionText":"두 번째 문항",
                                  "maxLength":1000,
                                  "memo":null,
                                  "coverLetterVersion":1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        UUID secondQuestionId = UUID.fromString(secondQuestion.get("id").asText());

        mockMvc.perform(patch("/api/v1/cover-letters/" + coverId + "/questions/order")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionIds":["%s","%s"],
                                  "version":2
                                }
                                """.formatted(secondQuestionId, firstQuestionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].id").value(secondQuestionId.toString()))
                .andExpect(jsonPath("$.questions[1].id").value(firstQuestionId.toString()));

        MvcResult firstVersion = mockMvc.perform(post(
                                "/api/v1/cover-letter-questions/" + firstQuestionId + "/versions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody("A\r\nCafé \u200B끝", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("USER_EDITED"))
                .andExpect(jsonPath("$.plainText").value("A\nCafé 끝"))
                .andExpect(jsonPath("$.contentJson.content[0].content[0].text")
                        .value("A\nCafé 끝"))
                .andReturn();
        UUID firstVersionId =
                UUID.fromString(json(firstVersion).get("id").asText());

        mockMvc.perform(post(
                                "/api/v1/cover-letter-questions/" + firstQuestionId + "/versions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentJson":{
                                    "type":"doc",
                                    "content":[{"type":"paragraph","content":[{"type":"image"}]}]
                                  },
                                  "parentVersionId":"%s"
                                }
                                """.formatted(firstVersionId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/jobs/" + jobId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverLetterStatus").value("DRAFT"))
                .andExpect(jsonPath("$.coverLetterId").value(coverId.toString()));

        long coverVersion = json(mockMvc.perform(get("/api/v1/cover-letters/" + coverId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andReturn())
                .get("version")
                .asLong();
        MvcResult archived = mockMvc.perform(post(
                                "/api/v1/cover-letters/" + coverId + "/archive")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + coverVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andReturn();
        assertThat(json(archived).get("canEdit").asBoolean()).isFalse();

        mockMvc.perform(post(
                                "/api/v1/cover-letter-questions/" + firstQuestionId + "/versions")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody("Archived mutation", firstVersionId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COVER_LETTER_ARCHIVED"));
    }

    @Test
    void authenticationAndValidationUseTheSharedErrorPath() throws Exception {
        mockMvc.perform(get("/api/v1/cover-letters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists());

        Session owner = authenticated("cover-api-validation@example.com");
        UUID jobId = seedJob(owner.userId(), "cover-api-validation");
        mockMvc.perform(post("/api/v1/jobs/" + jobId + "/cover-letter")
                        .cookie(owner.cookie())
                        .header("X-CSRF-TOKEN", owner.csrfToken())
                        .header("Idempotency-Key", "cover-api-invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String answerBody(String text, UUID parentVersionId) throws Exception {
        var body = objectMapper.createObjectNode();
        var document = body.putObject("contentJson");
        document.put("type", "doc");
        var paragraph = document.putArray("content").addObject();
        paragraph.put("type", "paragraph");
        var textNode = paragraph.putArray("content").addObject();
        textNode.put("type", "text");
        textNode.put("text", text);
        if (parentVersionId == null) {
            body.putNull("parentVersionId");
        } else {
            body.put("parentVersionId", parentVersionId.toString());
        }
        return objectMapper.writeValueAsString(body);
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

    private UUID seedJob(UUID userId, String key) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_postings (
                    id,user_id,source_url,canonical_url,title,position_name,
                    description_text,description_source,deadline_source,status,
                    extraction_status,version,created_at,updated_at
                ) VALUES (
                    ?,?,?,?,'Backend Engineer','Backend Engineer',
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
}
