package com.hiresemble.auth.api;

import com.hiresemble.auth.api.dto.AuthSessionDto;
import com.hiresemble.auth.api.dto.CsrfDto;
import com.hiresemble.auth.api.dto.CurrentUserDto;
import com.hiresemble.auth.api.dto.LoginRequest;
import com.hiresemble.auth.api.dto.SignupRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.hiresemble.support.PostgresIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class OpenApiContractTest extends PostgresIntegrationTest {

    private static final String CSRF_PATH = "/paths/~1api~1v1~1auth~1csrf/get";
    private static final String SIGNUP_PATH = "/paths/~1api~1v1~1auth~1signup/post";
    private static final String LOGIN_PATH = "/paths/~1api~1v1~1auth~1login/post";
    private static final String LOGOUT_PATH = "/paths/~1api~1v1~1auth~1logout/post";
    private static final String ME_PATH = "/paths/~1api~1v1~1auth~1me/get";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void liveSpringMappingsHaveExactlyFortyThreeP1ThroughP4OperationsAndThirtyPaths() {
        Set<String> paths = new LinkedHashSet<>();
        int[] operations = {0};

        handlerMapping.getHandlerMethods().forEach((mapping, method) -> {
            Set<String> apiPaths = new LinkedHashSet<>();
            for (String path : mapping.getPatternValues()) {
                if (path.startsWith("/api/v1/")) apiPaths.add(path);
            }
            if (apiPaths.isEmpty()) return;
            int methodCount = mapping.getMethodsCondition().getMethods().size();
            assertThat(methodCount).as(method.toString()).isGreaterThan(0);
            paths.addAll(apiPaths);
            operations[0] += apiPaths.size() * methodCount;
        });

        assertThat(paths).hasSize(30);
        assertThat(operations[0]).isEqualTo(43);
    }

    @Test
    void generatedOpenApiHasStableMetadataAndExactlyFortyThreeP1ThroughP4Operations()
            throws Exception {
        JsonNode document = openApi();

        assertThat(document.at("/info/title").asText()).isEqualTo("Hiresemble API");
        assertThat(document.at("/info/version").asText()).isEqualTo("1.4");
        assertThat(fieldValues(document.get("tags"), "name"))
                .containsExactlyInAnyOrder("Authentication", "Profile", "Agent Runs", "Documents");
        assertThat(findTag(document.get("tags"), "Authentication").get("description").asText())
                .contains(
                        "GET /api/v1/auth/csrf",
                        "Authorize > csrfToken",
                        "rotated SESSION",
                        "intentionally not enabled");

        assertThat(fieldNames(document.get("paths")))
                .containsExactlyInAnyOrder(
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/me",
                        "/api/v1/profile",
                        "/api/v1/profile/educations",
                        "/api/v1/profile/educations/{educationId}",
                        "/api/v1/profile/certifications",
                        "/api/v1/profile/certifications/{certificationId}",
                        "/api/v1/profile/language-scores",
                        "/api/v1/profile/language-scores/{languageScoreId}",
                        "/api/v1/profile/awards",
                        "/api/v1/profile/awards/{awardId}",
                        "/api/v1/profile/careers",
                        "/api/v1/profile/careers/{careerId}",
                        "/api/v1/profile/evidence",
                        "/api/v1/profile/evidence/{evidenceId}",
                        "/api/v1/profile/evidence/{evidenceId}/verification",
                        "/api/v1/agent-runs",
                        "/api/v1/agent-runs/{agentRunId}",
                        "/api/v1/agent-runs/{agentRunId}/events",
                        "/api/v1/agent-runs/{agentRunId}/retry",
                        "/api/v1/agent-runs/{agentRunId}/cancel",
                        "/api/v1/documents",
                        "/api/v1/documents/{documentId}",
                        "/api/v1/documents/{documentId}/text",
                        "/api/v1/documents/{documentId}/manual-text",
                        "/api/v1/documents/{documentId}/reparse",
                        "/api/v1/documents/{documentId}/download-url");
        assertThat(operationCount(document.get("paths"))).isEqualTo(43);
        assertOperation(document.at(CSRF_PATH), "initializeCsrf");
        assertOperation(document.at(SIGNUP_PATH), "signup");
        assertOperation(document.at(LOGIN_PATH), "login");
        assertOperation(document.at(LOGOUT_PATH), "logout");
        assertOperation(document.at(ME_PATH), "getCurrentUser");

        assertResponseCodes(document.at(CSRF_PATH), "200", "500");
        assertResponseCodes(document.at(SIGNUP_PATH), "201", "400", "403", "409");
        assertResponseCodes(document.at(LOGIN_PATH), "200", "400", "401", "403");
        assertResponseCodes(document.at(LOGOUT_PATH), "204", "401", "403");
        assertResponseCodes(document.at(ME_PATH), "200", "401");

        assertProfileOperation(document, "/api/v1/profile", "get", "getProfile");
        assertProfileOperation(document, "/api/v1/profile", "put", "updateProfile");
        assertProfileOperation(document, "/api/v1/profile/educations", "get", "listEducations");
        assertProfileOperation(document, "/api/v1/profile/educations", "post", "createEducation");
        assertProfileOperation(document, "/api/v1/profile/educations/{educationId}", "put", "updateEducation");
        assertProfileOperation(document, "/api/v1/profile/educations/{educationId}", "delete", "deleteEducation");
        assertProfileOperation(document, "/api/v1/profile/certifications", "get", "listCertifications");
        assertProfileOperation(document, "/api/v1/profile/certifications", "post", "createCertification");
        assertProfileOperation(document, "/api/v1/profile/certifications/{certificationId}", "put", "updateCertification");
        assertProfileOperation(document, "/api/v1/profile/certifications/{certificationId}", "delete", "deleteCertification");
        assertProfileOperation(document, "/api/v1/profile/language-scores", "get", "listLanguageScores");
        assertProfileOperation(document, "/api/v1/profile/language-scores", "post", "createLanguageScore");
        assertProfileOperation(document, "/api/v1/profile/language-scores/{languageScoreId}", "put", "updateLanguageScore");
        assertProfileOperation(document, "/api/v1/profile/language-scores/{languageScoreId}", "delete", "deleteLanguageScore");
        assertProfileOperation(document, "/api/v1/profile/awards", "get", "listAwards");
        assertProfileOperation(document, "/api/v1/profile/awards", "post", "createAward");
        assertProfileOperation(document, "/api/v1/profile/awards/{awardId}", "put", "updateAward");
        assertProfileOperation(document, "/api/v1/profile/awards/{awardId}", "delete", "deleteAward");
        assertProfileOperation(document, "/api/v1/profile/careers", "get", "listCareers");
        assertProfileOperation(document, "/api/v1/profile/careers", "post", "createCareer");
        assertProfileOperation(document, "/api/v1/profile/careers/{careerId}", "put", "updateCareer");
        assertProfileOperation(document, "/api/v1/profile/careers/{careerId}", "delete", "deleteCareer");
        assertProfileOperation(document, "/api/v1/profile/evidence", "get", "listProfileEvidence");
        assertProfileOperation(document, "/api/v1/profile/evidence/{evidenceId}", "put", "updateProfileEvidence");
        assertProfileOperation(document, "/api/v1/profile/evidence/{evidenceId}/verification", "patch", "verifyProfileEvidence");
        assertAgentRunOperation(document, "/api/v1/agent-runs", "get", "listAgentRuns");
        assertAgentRunOperation(document, "/api/v1/agent-runs/{agentRunId}", "get", "getAgentRun");
        assertAgentRunOperation(document, "/api/v1/agent-runs/{agentRunId}/events", "get", "streamAgentRunEvents");
        assertAgentRunOperation(document, "/api/v1/agent-runs/{agentRunId}/retry", "post", "retryAgentRun");
        assertAgentRunOperation(document, "/api/v1/agent-runs/{agentRunId}/cancel", "post", "cancelAgentRun");
        assertThat(document.at("/paths/~1api~1v1~1documents/post/operationId").asText())
                .isEqualTo("uploadDocument");
        assertThat(document.at("/paths/~1api~1v1~1documents/get/operationId").asText())
                .isEqualTo("listDocuments");
        assertThat(document.at("/paths/~1api~1v1~1documents~1{documentId}/get/operationId").asText())
                .isEqualTo("getDocument");
        assertThat(document.at("/paths/~1api~1v1~1documents~1{documentId}/delete/operationId").asText())
                .isEqualTo("deleteDocument");
        assertResponseCodes(document.at("/paths/~1api~1v1~1agent-runs/get"),
                "200", "400", "401", "404");
        assertResponseCodes(document.at("/paths/~1api~1v1~1agent-runs~1{agentRunId}/get"),
                "200", "401", "404");
        assertResponseCodes(document.at("/paths/~1api~1v1~1agent-runs~1{agentRunId}~1events/get"),
                "200", "401", "404");
        assertResponseCodes(document.at("/paths/~1api~1v1~1agent-runs~1{agentRunId}~1retry/post"),
                "202", "400", "401", "403", "404", "409", "429");
        assertResponseCodes(document.at("/paths/~1api~1v1~1agent-runs~1{agentRunId}~1cancel/post"),
                "202", "400", "401", "403", "404", "409");

        assertThat(document.at("/paths/~1api~1v1~1profile~1educations/post/responses/201")
                        .isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1profile~1educations~1{educationId}/delete/responses/204")
                        .isMissingNode())
                .isFalse();
    }

    @Test
    void securitySchemesAndPerOperationRequirementsMatchBrowserSessionAndCsrfFlow()
            throws Exception {
        JsonNode document = openApi();
        JsonNode schemes = document.at("/components/securitySchemes");

        assertThat(fieldNames(schemes)).containsExactlyInAnyOrder("sessionCookie", "csrfToken");
        assertSecurityScheme(
                schemes.get("sessionCookie"), "cookie", "SESSION", "managed and sent automatically");
        assertSecurityScheme(
                schemes.get("csrfToken"), "header", "X-CSRF-TOKEN", "AuthSessionDto.csrf.token");

        JsonNode csrfSecurity = document.at(CSRF_PATH).get("security");
        assertThat(csrfSecurity == null || (csrfSecurity.isArray() && csrfSecurity.isEmpty()))
                .as("CSRF bootstrap must remain anonymous")
                .isTrue();
        assertSingleSecurityRequirement(document.at(SIGNUP_PATH), "csrfToken");
        assertSingleSecurityRequirement(document.at(LOGIN_PATH), "csrfToken");
        assertSingleSecurityRequirement(document.at(ME_PATH), "sessionCookie");
        assertSingleSecurityRequirement(
                document.at("/paths/~1api~1v1~1profile/get"), "sessionCookie");

        JsonNode logoutSecurity = document.at(LOGOUT_PATH).get("security");
        assertThat(logoutSecurity).isNotNull();
        assertThat(logoutSecurity.isArray()).isTrue();
        assertThat(logoutSecurity.size())
                .as("multiple OpenAPI security array entries would mean OR")
                .isEqualTo(1);
        assertThat(fieldNames(logoutSecurity.get(0)))
                .as("sessionCookie and csrfToken must share one requirement object for AND")
                .containsExactlyInAnyOrder("sessionCookie", "csrfToken");
        JsonNode profileMutationSecurity =
                document.at("/paths/~1api~1v1~1profile~1educations/post/security");
        assertThat(profileMutationSecurity).hasSize(1);
        assertThat(fieldNames(profileMutationSecurity.get(0)))
                .containsExactlyInAnyOrder("sessionCookie", "csrfToken");
        JsonNode agentRunMutationSecurity =
                document.at("/paths/~1api~1v1~1agent-runs~1{agentRunId}~1cancel/post/security");
        assertThat(agentRunMutationSecurity).hasSize(1);
        assertThat(fieldNames(agentRunMutationSecurity.get(0)))
                .containsExactlyInAnyOrder("sessionCookie", "csrfToken");
    }

    @Test
    void generatedSchemasKeepDirectResponsesSafeRequestExamplesAndHiddenRuntimeArguments()
            throws Exception {
        JsonNode document = openApi();
        JsonNode schemas = document.at("/components/schemas");

        assertThat(fieldNames(schemas.at("/CsrfDto/properties")))
                .containsExactlyInAnyOrder("headerName", "parameterName", "token");
        assertThat(fieldNames(schemas.at("/CurrentUserDto/properties")))
                .containsExactlyInAnyOrder("id", "email", "displayName");
        assertThat(fieldNames(schemas.at("/AuthSessionDto/properties")))
                .containsExactlyInAnyOrder("user", "csrf");
        assertThat(fieldNames(schemas.at("/ErrorResponseDto/properties")))
                .containsExactlyInAnyOrder(
                        "timestamp", "status", "code", "message", "fieldErrors", "requestId");
        assertThat(fieldNames(schemas.at("/FieldErrorDto/properties")))
                .containsExactlyInAnyOrder("field", "reason");
        assertThat(fieldNames(schemas)).doesNotContain("BaseResponseDto", "DashboardDto");
        assertThat(fieldNames(schemas.at("/AgentRunDetailDto/properties")))
                .containsExactlyInAnyOrder(
                        "id", "workflowType", "resourceType", "resourceId", "status",
                        "currentStep", "progressPercent", "requestedQualityMode",
                        "highestModelTierUsed", "estimatedCostUsd", "reservedCostUsd",
                        "actualCostUsd", "retryable", "cancellable", "requiredUserAction",
                        "stateVersion", "queuedAt", "updatedAt", "retryOfRunId", "rootRunId",
                        "runAttemptNo", "durationMs", "startedAt", "completedAt", "safeError",
                        "partialResult", "steps")
                .doesNotContain(
                        "claimToken", "claimedBy", "leaseExpiresAt", "heartbeatAt",
                        "canonicalInputHash", "inputReferenceSnapshot", "priceVersion",
                        "provider", "model", "promptVersion", "reusedStepId", "outputJson");

        assertThat(fieldNames(schemas.at("/ProfileDto/properties")))
                .containsExactlyInAnyOrder(
                        "legalName",
                        "introduction",
                        "desiredRoles",
                        "desiredIndustries",
                        "desiredLocations",
                        "expectedGraduationDate",
                        "profileCompleted",
                        "missingCompletionItems",
                        "version",
                        "createdAt",
                        "updatedAt");
        assertThat(schemas.at("/ProfileDto/properties/profileCompleted/readOnly").asBoolean())
                .isTrue();
        assertThat(schemas.at("/ProfileDto/properties/missingCompletionItems/readOnly").asBoolean())
                .isTrue();
        assertThat(fieldNames(schemas.at("/EducationStatus/enum"))).isEmpty();
        assertThat(fieldNames(schemas.at("/DocumentSummaryDto/properties")))
                .containsExactlyInAnyOrder(
                        "id", "documentType", "displayName", "mimeType", "fileSizeBytes",
                        "parseStatus", "evidenceExtractionStatus", "manualTextProvided",
                        "safeError", "latestAgentRunId", "version", "uploadedAt", "updatedAt");
        assertThat(fieldNames(schemas.at("/DocumentDetailDto/properties")))
                .containsExactlyInAnyOrder(
                        "id", "documentType", "displayName", "mimeType", "fileSizeBytes",
                        "parseStatus", "evidenceExtractionStatus", "manualTextProvided",
                        "safeError", "latestAgentRunId", "version", "uploadedAt", "updatedAt",
                        "pageCount", "characterCount", "parsedAt");
        assertThat(document.toString())
                .doesNotContain("/api/v1/jobs")
                .doesNotContain("/api/v1/dashboard")
                .doesNotContain("/api/v1/settings/ai")
                .doesNotContain("createProfileEvidence")
                .doesNotContain("deleteProfileEvidence")
                .doesNotContain("storageKey", "checksumSha256", "parserName", "embeddingProvider");

        assertResponseSchema(document, CSRF_PATH, "200", "CsrfDto");
        assertResponseSchema(document, CSRF_PATH, "500", "ErrorResponseDto");
        assertResponseSchema(document, SIGNUP_PATH, "201", "AuthSessionDto");
        assertResponseSchema(document, SIGNUP_PATH, "400", "ErrorResponseDto");
        assertResponseSchema(document, SIGNUP_PATH, "403", "ErrorResponseDto");
        assertResponseSchema(document, SIGNUP_PATH, "409", "ErrorResponseDto");
        assertResponseSchema(document, LOGIN_PATH, "200", "AuthSessionDto");
        assertResponseSchema(document, LOGIN_PATH, "400", "ErrorResponseDto");
        assertResponseSchema(document, LOGIN_PATH, "401", "ErrorResponseDto");
        assertResponseSchema(document, LOGIN_PATH, "403", "ErrorResponseDto");
        assertResponseSchema(document, LOGOUT_PATH, "401", "ErrorResponseDto");
        assertResponseSchema(document, LOGOUT_PATH, "403", "ErrorResponseDto");
        assertResponseSchema(document, ME_PATH, "200", "CurrentUserDto");
        assertResponseSchema(document, ME_PATH, "401", "ErrorResponseDto");

        assertThat(document.at(SIGNUP_PATH + "/requestBody/content/application~1json/schema/$ref")
                        .asText())
                .endsWith("/SignupRequest");
        assertThat(document.at(LOGIN_PATH + "/requestBody/content/application~1json/schema/$ref")
                        .asText())
                .endsWith("/LoginRequest");
        JsonNode signupPassword = schemas.at("/SignupRequest/properties/password");
        JsonNode loginPassword = schemas.at("/LoginRequest/properties/password");
        assertThat(signupPassword.get("format").asText()).isEqualTo("password");
        assertThat(signupPassword.get("writeOnly").asBoolean()).isTrue();
        assertThat(signupPassword.get("description").asText()).contains("10 to 72 UTF-8 bytes");
        assertThat(signupPassword.get("example").asText()).isEqualTo("ExampleOnly-123");
        assertThat(loginPassword.get("format").asText()).isEqualTo("password");
        assertThat(loginPassword.get("writeOnly").asBoolean()).isTrue();
        assertThat(loginPassword.get("description").asText()).contains("1 to 72 UTF-8 bytes");
        assertThat(loginPassword.get("example").asText()).isEqualTo("ExampleOnly-123");
        assertThat(schemas.at("/SignupRequest/properties/email/example").asText())
                .isEqualTo("candidate@example.com");
        assertThat(schemas.at("/SignupRequest/properties/termsAgreed/example").asBoolean())
                .isTrue();
        assertThat(schemas.at("/SignupRequest/properties/aiConsent/example").asBoolean())
                .isTrue();

        assertNoDocumentedParameters(document.at(CSRF_PATH));
        assertNoDocumentedParameters(document.at(SIGNUP_PATH));
        assertNoDocumentedParameters(document.at(LOGIN_PATH));
        assertNoDocumentedParameters(document.at(LOGOUT_PATH));
        assertNoDocumentedParameters(document.at(ME_PATH));
    }

    @Test
    void anonymousSwaggerUiIsReachableAndTryItOutIsEnabled() throws Exception {
        MvcResult redirect = mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = redirect.getResponse().getRedirectedUrl();
        assertThat(location).isNotBlank();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        JsonNode swaggerConfig = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(swaggerConfig.get("tryItOutEnabled").asBoolean()).isTrue();
        assertThat(swaggerConfig.get("csrf")).isNull();
    }

    private JsonNode openApi() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    private void assertOperation(JsonNode operation, String operationId) {
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.get("operationId").asText()).isEqualTo(operationId);
        assertThat(operation.get("summary").asText()).isNotBlank();
        assertThat(operation.get("description").asText()).isNotBlank();
        assertThat(operation.at("/tags/0").asText()).isEqualTo("Authentication");
    }

    private void assertProfileOperation(
            JsonNode document, String path, String method, String operationId) {
        JsonNode operation = document.get("paths").get(path).get(method);
        assertThat(operation).isNotNull();
        assertThat(operation.get("operationId").asText()).isEqualTo(operationId);
        assertThat(operation.get("summary").asText()).isNotBlank();
        assertThat(operation.get("description").asText()).isNotBlank();
        assertThat(operation.at("/tags/0").asText()).isEqualTo("Profile");
    }

    private void assertAgentRunOperation(
            JsonNode document, String path, String method, String operationId) {
        JsonNode operation = document.get("paths").get(path).get(method);
        assertThat(operation).isNotNull();
        assertThat(operation.get("operationId").asText()).isEqualTo(operationId);
        assertThat(operation.get("summary").asText()).isNotBlank();
        assertThat(operation.get("description").asText()).isNotBlank();
        assertThat(operation.at("/tags/0").asText()).isEqualTo("Agent Runs");
    }

    private int operationCount(JsonNode paths) {
        int count = 0;
        for (JsonNode path : paths) {
            for (String method : Set.of("get", "post", "put", "patch", "delete")) {
                if (path.has(method)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void assertResponseCodes(JsonNode operation, String... expectedCodes) {
        assertThat(fieldNames(operation.get("responses")))
                .containsExactlyInAnyOrder(expectedCodes);
    }

    private void assertSecurityScheme(
            JsonNode scheme, String location, String name, String descriptionFragment) {
        assertThat(scheme.get("type").asText()).isEqualTo("apiKey");
        assertThat(scheme.get("in").asText()).isEqualTo(location);
        assertThat(scheme.get("name").asText()).isEqualTo(name);
        assertThat(scheme.get("description").asText()).contains(descriptionFragment);
    }

    private void assertSingleSecurityRequirement(JsonNode operation, String expectedScheme) {
        JsonNode security = operation.get("security");
        assertThat(security).isNotNull();
        assertThat(security.isArray()).isTrue();
        assertThat(security.size()).isEqualTo(1);
        assertThat(fieldNames(security.get(0))).containsExactly(expectedScheme);
    }

    private void assertResponseSchema(
            JsonNode document, String operationPath, String statusCode, String schemaName) {
        String reference = document.at(operationPath
                        + "/responses/"
                        + statusCode
                        + "/content/application~1json/schema/$ref")
                .asText();
        assertThat(reference).endsWith("/" + schemaName);
        assertThat(reference).doesNotContain("BaseResponseDto");
    }

    private void assertNoDocumentedParameters(JsonNode operation) {
        JsonNode parameters = operation.get("parameters");
        assertThat(parameters == null || (parameters.isArray() && parameters.isEmpty())).isTrue();
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(node.propertyNames());
        return names;
    }

    private Set<String> fieldValues(JsonNode nodes, String field) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            values.add(node.get(field).asText());
        }
        return values;
    }

    private JsonNode findTag(JsonNode tags, String name) {
        for (JsonNode tag : tags) {
            if (tag.get("name").asText().equals(name)) {
                return tag;
            }
        }
        throw new AssertionError("OpenAPI tag not found: " + name);
    }
}
