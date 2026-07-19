package com.hiresemble.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.support.PostgresIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

    @Test
    void generatedOpenApiHasStableMetadataTagOperationsAndExactlyFiveP1Paths()
            throws Exception {
        JsonNode document = openApi();

        assertThat(document.at("/info/title").asText()).isEqualTo("Hiresemble API");
        assertThat(document.at("/info/version").asText()).isEqualTo("1.1");
        assertThat(document.at("/tags/0/name").asText()).isEqualTo("Authentication");
        assertThat(document.at("/tags/0/description").asText())
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
                        "/api/v1/auth/me");
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

        JsonNode logoutSecurity = document.at(LOGOUT_PATH).get("security");
        assertThat(logoutSecurity).isNotNull();
        assertThat(logoutSecurity.isArray()).isTrue();
        assertThat(logoutSecurity.size())
                .as("multiple OpenAPI security array entries would mean OR")
                .isEqualTo(1);
        assertThat(fieldNames(logoutSecurity.get(0)))
                .as("sessionCookie and csrfToken must share one requirement object for AND")
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
}
