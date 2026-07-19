package com.hiresemble.profile.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.auth.api.SignupRequest;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class ProfileIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void incompleteProfileIsReadableAndDoesNotGateOtherProfileRoutes() throws Exception {
        Session session = authenticated("incomplete@example.com");

        mockMvc.perform(get("/api/v1/profile").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(false))
                .andExpect(jsonPath("$.missingCompletionItems.length()").value(5))
                .andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(get("/api/v1/profile/careers").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void profileAndPrimaryEducationCompleteAllFiveServerComputedItems() throws Exception {
        Session session = authenticated("complete@example.com");

        MvcResult profile = mutation(
                put("/api/v1/profile").content(profileBody(0)), session, 200);
        assertThat(json(profile).at("/profileCompleted").asBoolean()).isFalse();
        assertThat(json(profile).at("/missingCompletionItems/0").asText())
                .isEqualTo("PRIMARY_EDUCATION");
        assertThat(json(profile).at("/desiredRoles/0").asText()).isEqualTo("Backend");

        mutation(post("/api/v1/profile/educations").content(educationBody("School", true, null)), session, 201);

        mockMvc.perform(get("/api/v1/profile").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andExpect(jsonPath("$.missingCompletionItems").isEmpty());
    }

    @Test
    void educationCrudSwitchesPrimaryAndKeepsOneVerifiedEvidencePerSource() throws Exception {
        Session session = authenticated("education@example.com");
        JsonNode first = json(mutation(
                post("/api/v1/profile/educations").content(educationBody("First School", true, null)),
                session,
                201));
        JsonNode second = json(mutation(
                post("/api/v1/profile/educations").content(educationBody("Second School", true, null)),
                session,
                201));

        MvcResult listResult = mockMvc.perform(get("/api/v1/profile/educations")
                        .cookie(session.cookie())
                        .queryParam("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode items = json(listResult).get("items");
        JsonNode demoted = find(items, first.get("id").asText());
        assertThat(demoted.get("isPrimary").asBoolean()).isFalse();
        assertThat(demoted.get("version").asLong()).isEqualTo(1);
        assertThat(find(items, second.get("id").asText()).get("isPrimary").asBoolean()).isTrue();

        JsonNode updated = json(mutation(
                put("/api/v1/profile/educations/" + first.get("id").asText())
                        .content(educationBody("First Updated", false, demoted.get("version").asLong())),
                session,
                200));
        assertThat(updated.get("version").asLong()).isEqualTo(2);

        JsonNode evidence = evidence(session, "EDUCATION");
        assertThat(evidence.get("items")).hasSize(2);
        assertThat(findBySource(evidence.get("items"), first.get("id").asText()).get("title").asText())
                .isEqualTo("First Updated");
        assertThat(findBySource(evidence.get("items"), first.get("id").asText())
                        .get("verificationStatus")
                        .asText())
                .isEqualTo("VERIFIED");

        mutation(
                delete("/api/v1/profile/educations/" + first.get("id").asText())
                        .queryParam("version", updated.get("version").asText()),
                session,
                204);
        assertThat(evidence(session, "EDUCATION").get("items")).hasSize(1);
    }

    @Test
    void certificationLanguageAwardAndCareerExposeFullCrudAndPagination() throws Exception {
        Session session = authenticated("resources@example.com");

        JsonNode certification = create(session, "/certifications", certificationBody("Certificate", null));
        mockMvc.perform(get("/api/v1/profile/certifications").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        JsonNode certificationUpdated = update(
                session,
                "/certifications/" + certification.get("id").asText(),
                certificationBody("Certificate Updated", certification.get("version").asLong()));

        JsonNode language = create(session, "/language-scores", languageBody("TOEIC", null));
        mockMvc.perform(get("/api/v1/profile/language-scores").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        JsonNode languageUpdated = update(
                session,
                "/language-scores/" + language.get("id").asText(),
                languageBody("TOEFL", language.get("version").asLong()));

        JsonNode award = create(session, "/awards", awardBody("Award", null));
        create(session, "/awards", awardBody("Second Award", null));
        mockMvc.perform(get("/api/v1/profile/awards")
                        .cookie(session.cookie())
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .queryParam("sort", "awardedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        JsonNode awardUpdated = update(
                session,
                "/awards/" + award.get("id").asText(),
                awardBody("Award Updated", award.get("version").asLong()));

        JsonNode career = create(session, "/careers", careerBody("Company", true, null));
        mockMvc.perform(get("/api/v1/profile/careers").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        JsonNode careerUpdated = update(
                session,
                "/careers/" + career.get("id").asText(),
                careerBody("Company Updated", false, career.get("version").asLong()));

        remove(session, "/certifications/", certificationUpdated);
        remove(session, "/language-scores/", languageUpdated);
        remove(session, "/awards/", awardUpdated);
        remove(session, "/careers/", careerUpdated);
        assertThat(evidence(session, null).get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void evidenceEditAndVerificationAreOverriddenByLaterSourceSynchronization() throws Exception {
        Session session = authenticated("evidence@example.com");
        JsonNode career = create(session, "/careers", careerBody("Original Company", true, null));
        JsonNode evidence = findBySource(
                evidence(session, "CAREER").get("items"), career.get("id").asText());

        JsonNode edited = json(mutation(
                put("/api/v1/profile/evidence/" + evidence.get("id").asText())
                        .content("""
                                {"title":"User title","content":"User content","metadata":{"custom":"value","nullable":null},"version":0}
                                """),
                session,
                200));
        assertThat(edited.get("title").asText()).isEqualTo("User title");
        assertThat(edited.at("/metadata/nullable").isNull()).isTrue();

        JsonNode rejected = json(mutation(
                patch("/api/v1/profile/evidence/" + evidence.get("id").asText() + "/verification")
                        .content("{\"status\":\"REJECTED\",\"version\":1}"),
                session,
                200));
        assertThat(rejected.get("verificationStatus").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("verifiedAt").isNull()).isTrue();

        update(
                session,
                "/careers/" + career.get("id").asText(),
                careerBody("Source Wins", true, career.get("version").asLong()));
        JsonNode synchronizedEvidence = findBySource(
                evidence(session, "CAREER").get("items"), career.get("id").asText());
        assertThat(synchronizedEvidence.get("title").asText()).isEqualTo("Source Wins");
        assertThat(synchronizedEvidence.get("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(synchronizedEvidence.get("verifiedAt").isNull()).isFalse();
        assertThat(synchronizedEvidence.get("metadata").has("custom")).isFalse();
        assertThat(synchronizedEvidence.get("version").asLong()).isEqualTo(3);
    }

    @Test
    void sourceDeletedEvidenceRejectsEditAndVerification() throws Exception {
        Session session = authenticated("deleted-evidence@example.com");
        UUID evidenceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO profile_evidence (
                    id,user_id,source_type,source_entity_id,document_id,evidence_category,title,content,
                    metadata,confidence,verification_status,verified_at,source_deleted_at,version,created_at,updated_at
                ) VALUES (?,?,'MANUAL',NULL,NULL,'MANUAL','Deleted','Deleted','{}',NULL,
                          'SOURCE_DELETED',NULL,now(),0,now(),now())
                """,
                evidenceId,
                session.userId());

        MvcResult edit = mutation(
                put("/api/v1/profile/evidence/" + evidenceId)
                        .content("{\"title\":\"Edit\",\"content\":\"Edit\",\"metadata\":{},\"version\":0}"),
                session,
                409);
        assertThat(json(edit).get("code").asText()).isEqualTo("EVIDENCE_SOURCE_DELETED");
        MvcResult verify = mutation(
                patch("/api/v1/profile/evidence/" + evidenceId + "/verification")
                        .content("{\"status\":\"VERIFIED\",\"version\":0}"),
                session,
                409);
        assertThat(json(verify).get("code").asText()).isEqualTo("EVIDENCE_SOURCE_DELETED");
    }

    @Test
    void ownerScopedQueriesHideForeignAndMissingIdsAndVersionConflictsAreExplicit() throws Exception {
        Session owner = authenticated("owner@example.com");
        Session stranger = authenticated("stranger@example.com");
        JsonNode education = create(owner, "/educations", educationBody("Owner School", false, null));
        JsonNode evidence = findBySource(
                evidence(owner, "EDUCATION").get("items"), education.get("id").asText());

        MvcResult foreignUpdate = mutation(
                put("/api/v1/profile/educations/" + education.get("id").asText())
                        .content(educationBody("Foreign", false, 0L)),
                stranger,
                404);
        assertThat(json(foreignUpdate).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        MvcResult foreignDelete = mutation(
                delete("/api/v1/profile/educations/" + education.get("id").asText())
                        .queryParam("version", "0"),
                stranger,
                404);
        assertThat(json(foreignDelete).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        MvcResult foreignEvidence = mutation(
                put("/api/v1/profile/evidence/" + evidence.get("id").asText())
                        .content("{\"title\":\"x\",\"content\":\"x\",\"metadata\":{},\"version\":0}"),
                stranger,
                404);
        assertThat(json(foreignEvidence).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");

        MvcResult stale = mutation(
                put("/api/v1/profile/educations/" + education.get("id").asText())
                        .content(educationBody("Stale", false, 99L)),
                owner,
                409);
        assertThat(json(stale).get("code").asText()).isEqualTo("RESOURCE_VERSION_CONFLICT");
        assertThat(json(stale).at("/fieldErrors/0/field").asText()).isEqualTo("version");
        MvcResult missing = mutation(
                delete("/api/v1/profile/educations/" + UUID.randomUUID()).queryParam("version", "0"),
                owner,
                404);
        assertThat(json(missing).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void validationPaginationDocumentDeferralAuthenticationAndCsrfBoundariesAreSafe() throws Exception {
        Session session = authenticated("boundaries@example.com");

        mockMvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/profile")
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(get("/api/v1/profile/educations")
                        .cookie(session.cookie())
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/profile/educations")
                        .cookie(session.cookie())
                        .queryParam("sort", "schoolName,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        MvcResult duplicateArray = mutation(
                put("/api/v1/profile").content("""
                        {"legalName":"Name","introduction":null,"desiredRoles":["Backend"," backend "],
                         "desiredIndustries":[],"desiredLocations":[],"expectedGraduationDate":null,"version":0}
                        """),
                session,
                400);
        assertThat(duplicateArray.getResponse().getContentAsString())
                .doesNotContain("backend ")
                .doesNotContain("constraint")
                .doesNotContain("SQL");
        mutation(
                post("/api/v1/profile/educations").content("""
                        {"schoolName":"School","major":null,"degree":null,"educationStatus":"GRADUATED",
                         "admissionDate":"2025-01-01","graduationDate":"2024-01-01","gpa":4.5,
                         "gpaScale":4.0,"isPrimary":false,"description":null}
                        """),
                session,
                400);

        UUID documentId = UUID.randomUUID();
        MvcResult documentCreate = mutation(
                post("/api/v1/profile/certifications")
                        .content(certificationBodyWithDocument("Certificate", documentId)),
                session,
                404);
        assertThat(json(documentCreate).get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        mockMvc.perform(get("/api/v1/profile/evidence")
                        .cookie(session.cookie())
                        .queryParam("documentId", documentId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void sourceAndEvidenceRollBackTogetherWhenEvidenceInsertFails() throws Exception {
        Session session = authenticated("rollback-profile@example.com");
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_direct_evidence_fixture() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only direct evidence failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_direct_evidence_fixture_trigger
                BEFORE INSERT ON profile_evidence
                FOR EACH ROW EXECUTE FUNCTION reject_direct_evidence_fixture()
                """);
        try {
            MvcResult failure = mutation(
                    post("/api/v1/profile/careers").content(careerBody("Rollback", true, null)),
                    session,
                    500);
            assertThat(json(failure).get("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(failure.getResponse().getContentAsString())
                    .doesNotContain("test-only direct evidence failure")
                    .doesNotContain("profile_evidence");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM careers", Long.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM profile_evidence", Long.class))
                    .isZero();
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_direct_evidence_fixture_trigger ON profile_evidence");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_direct_evidence_fixture()");
        }
    }

    private Session authenticated(String email) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie anonymousCookie = requiredCookie(csrf);
        String anonymousToken = json(csrf).get("token").asText();
        String body = objectMapper.writeValueAsString(
                new SignupRequest(email, "password-123", "Candidate", true, true));
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(anonymousCookie)
                        .header("X-CSRF-TOKEN", anonymousToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = json(signup);
        return new Session(
                requiredCookie(signup),
                response.at("/csrf/token").asText(),
                UUID.fromString(response.at("/user/id").asText()));
    }

    private MvcResult mutation(
            MockHttpServletRequestBuilder request, Session session, int expectedStatus)
            throws Exception {
        return mockMvc.perform(request.cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private JsonNode create(Session session, String path, String body) throws Exception {
        return json(mutation(post("/api/v1/profile" + path).content(body), session, 201));
    }

    private JsonNode update(Session session, String path, String body) throws Exception {
        return json(mutation(put("/api/v1/profile" + path).content(body), session, 200));
    }

    private void remove(Session session, String path, JsonNode resource) throws Exception {
        mutation(
                delete("/api/v1/profile" + path + resource.get("id").asText())
                        .queryParam("version", resource.get("version").asText()),
                session,
                204);
    }

    private JsonNode evidence(Session session, String category) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/profile/evidence")
                .cookie(session.cookie())
                .queryParam("sort", "updatedAt,desc");
        if (category != null) {
            request.queryParam("evidenceCategory", category);
        }
        return json(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private String profileBody(long version) {
        return """
                {"legalName":" Candidate ","introduction":"Introduction","desiredRoles":[" Backend "],
                 "desiredIndustries":["Software"],"desiredLocations":["Seoul"],
                 "expectedGraduationDate":"2027-02-28","version":%d}
                """.formatted(version);
    }

    private String educationBody(String school, boolean primary, Long version) {
        return """
                {"schoolName":"%s","major":"Computer Science","degree":"Bachelor",
                 "educationStatus":"GRADUATED","admissionDate":"2020-03-01",
                 "graduationDate":"2024-02-29","gpa":4.0,"gpaScale":4.5,
                 "isPrimary":%s,"description":"Education"%s}
                """.formatted(school, primary, version == null ? "" : ",\"version\":" + version);
    }

    private String certificationBody(String name, Long version) {
        return """
                {"name":"%s","issuer":"Issuer","credentialNumber":"C-1",
                 "acquiredDate":"2024-01-01","expiresAt":"2025-01-01",
                 "description":"Description","evidenceDocumentId":null%s}
                """.formatted(name, version == null ? "" : ",\"version\":" + version);
    }

    private String certificationBodyWithDocument(String name, UUID documentId) {
        return """
                {"name":"%s","issuer":null,"credentialNumber":null,"acquiredDate":null,
                 "expiresAt":null,"description":null,"evidenceDocumentId":"%s"}
                """.formatted(name, documentId);
    }

    private String languageBody(String testName, Long version) {
        return """
                {"testName":"%s","score":"900","grade":"A","testedAt":"2024-01-01",
                 "expiresAt":"2025-01-01","evidenceDocumentId":null%s}
                """.formatted(testName, version == null ? "" : ",\"version\":" + version);
    }

    private String awardBody(String name, Long version) {
        return """
                {"name":"%s","organizer":"Organizer","awardedAt":"2024-01-01",
                 "description":"Description","evidenceDocumentId":null%s}
                """.formatted(name, version == null ? "" : ",\"version\":" + version);
    }

    private String careerBody(String organization, boolean current, Long version) {
        return """
                {"organization":"%s","position":"Developer","employmentType":"FULL_TIME",
                 "startedAt":"2024-01-01","endedAt":%s,"isCurrent":%s,
                 "responsibilities":"Responsibilities","achievements":"Achievements"%s}
                """.formatted(
                organization,
                current ? "null" : "\"2025-01-01\"",
                current,
                version == null ? "" : ",\"version\":" + version);
    }

    private JsonNode find(JsonNode items, String id) {
        for (JsonNode item : items) {
            if (item.get("id").asText().equals(id)) {
                return item;
            }
        }
        throw new AssertionError("resource not found in response: " + id);
    }

    private JsonNode findBySource(JsonNode items, String sourceEntityId) {
        for (JsonNode item : items) {
            if (item.get("sourceEntityId").asText().equals(sourceEntityId)) {
                return item;
            }
        }
        throw new AssertionError("evidence not found for source: " + sourceEntityId);
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Session(Cookie cookie, String csrfToken, UUID userId) {}
}
