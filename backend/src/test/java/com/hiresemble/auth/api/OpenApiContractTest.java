package com.hiresemble.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.support.PostgresIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class OpenApiContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void generatedOpenApiContainsExactlyTheFiveP1AuthPaths() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());

        assertThat(fieldNames(document.get("paths")))
                .containsExactlyInAnyOrder(
                        "/api/v1/auth/csrf",
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/me");
        assertThat(document.at("/paths/~1api~1v1~1auth~1signup/post/responses/201").isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1auth~1login/post/responses/200").isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1auth~1logout/post/responses/204").isMissingNode())
                .isFalse();
        assertThat(document.at("/paths/~1api~1v1~1auth~1me/get/responses/401").isMissingNode())
                .isFalse();
    }

    @Test
    void generatedSchemasMatchDirectSuccessAndCommonErrorContracts() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        JsonNode schemas = document.at("/components/schemas");

        assertThat(fieldNames(schemas.get("CsrfDto")))
                .contains("properties");
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

        String signupSuccessRef = document
                .at("/paths/~1api~1v1~1auth~1signup/post/responses/201/content/application~1json/schema/$ref")
                .asText();
        assertThat(signupSuccessRef).endsWith("/AuthSessionDto");
        assertThat(signupSuccessRef).doesNotContain("BaseResponseDto");
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(node.propertyNames());
        return names;
    }
}
