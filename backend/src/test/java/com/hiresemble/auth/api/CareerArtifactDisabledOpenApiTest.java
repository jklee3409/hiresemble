package com.hiresemble.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiresemble.support.PostgresIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@TestPropertySource(properties = "hiresemble.career-artifact.enabled=false")
class CareerArtifactDisabledOpenApiTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void disabledFeaturePreservesTheSeventyNinePathOneHundredSevenOperationBaseline()
            throws Exception {
        Set<String> livePaths = new LinkedHashSet<>();
        int[] liveOperations = {0};
        handlerMapping.getHandlerMethods().forEach((mapping, method) -> {
            Set<String> apiPaths = new LinkedHashSet<>();
            mapping.getPatternValues().stream()
                    .filter(path -> path.startsWith("/api/v1/"))
                    .forEach(apiPaths::add);
            livePaths.addAll(apiPaths);
            liveOperations[0] += apiPaths.size()
                    * mapping.getMethodsCondition().getMethods().size();
        });

        assertThat(livePaths).hasSize(79);
        assertThat(liveOperations[0]).isEqualTo(107);
        assertThat(livePaths).noneMatch(path -> path.startsWith("/api/v1/career-artifacts"));

        JsonNode openApi = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        Set<String> documentedPaths = new LinkedHashSet<>();
        documentedPaths.addAll(openApi.get("paths").propertyNames());
        assertThat(openApi.get("paths").size()).isEqualTo(79);
        assertThat(operationCount(openApi.get("paths"))).isEqualTo(107);
        assertThat(documentedPaths)
                .noneMatch(path -> path.startsWith("/api/v1/career-artifacts"));
        assertThat(openApi.get("paths").has("/api/v1/github-sources")).isTrue();
        assertThat(openApi.get("paths").has("/api/v1/documents")).isTrue();
        assertThat(openApi.get("paths").has("/api/v1/jobs")).isTrue();
        assertThat(openApi.get("paths").has("/api/v1/cover-letters")).isTrue();
        assertThat(openApi.get("paths").has("/api/v1/interview-question-sets")).isTrue();
    }

    private int operationCount(JsonNode paths) {
        int count = 0;
        for (JsonNode path : paths) {
            for (String method : Set.of("get", "post", "put", "patch", "delete")) {
                if (path.has(method)) count++;
            }
        }
        return count;
    }
}
