package com.hiresemble.common.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Hiresemble API",
                version = "1.4",
                description = "P4 authentication, profile, Agent Run, and document APIs. Successful DTOs are returned directly without an envelope."),
        tags = {
            @Tag(
                    name = "Authentication",
                    description = """
                            Browser Session and CSRF authentication. In the same-origin Swagger UI, first call
                            GET /api/v1/auth/csrf so the browser stores the anonymous SESSION cookie. Copy the
                            JSON token into Authorize > csrfToken, then run a mutation. After signup or login,
                            keep the browser-managed rotated SESSION cookie and replace csrfToken with the new
                            csrf.token from AuthSessionDto. Swagger UI cookie/local-storage CSRF automation is
                            intentionally not enabled because this API returns its token in JSON.
                            """),
            @Tag(
                    name = "Profile",
                    description = "Authenticated profile, structured profile resources, and direct evidence."),
            @Tag(
                    name = "Agent Runs",
                    description = "Durable Agent Run status, retry, cancellation, and progress events."),
            @Tag(
                    name = "Documents",
                    description = "Owner-scoped upload, parsing, text, download, and deletion pipeline.")
        })
@SecuritySchemes({
    @SecurityScheme(
            name = "sessionCookie",
            type = SecuritySchemeType.APIKEY,
            in = SecuritySchemeIn.COOKIE,
            paramName = "SESSION",
            description = """
                    HttpOnly Session cookie managed and sent automatically by the same-origin browser.
                    Do not paste a cookie value into Authorize; bootstrap it with GET /api/v1/auth/csrf.
                    """),
    @SecurityScheme(
            name = "csrfToken",
            type = SecuritySchemeType.APIKEY,
            in = SecuritySchemeIn.HEADER,
            paramName = "X-CSRF-TOKEN",
            description = """
                    Paste the token returned by CsrfDto or the latest AuthSessionDto.csrf.token.
                    Authorize sends it as X-CSRF-TOKEN for documented mutation operations.
                    """)
})
public class OpenApiConfiguration {

    @Bean
    OpenApiCustomizer mutationSecurityRequirements() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap()
                .forEach((method, operation) -> {
                    boolean protectedMutation = path.equals("/api/v1/auth/logout")
                            || (path.startsWith("/api/v1/profile") && !method.name().equals("GET"))
                            || (path.startsWith("/api/v1/agent-runs") && method == HttpMethod.POST)
                            || (path.startsWith("/api/v1/documents") && method != HttpMethod.GET);
                    if (protectedMutation) {
                        operation.setSecurity(List.of(new SecurityRequirement()
                                .addList("sessionCookie")
                                .addList("csrfToken")));
                    }
                    if (path.startsWith("/api/v1/profile")) {
                        addProfileErrorResponses(path, method, operation);
                    }
                    if (path.startsWith("/api/v1/documents")) {
                        addDocumentErrorResponses(path, method, operation);
                    }
                }));
    }

    private void addDocumentErrorResponses(
            String path, HttpMethod method, Operation operation) {
        addError(operation, "401");
        if (method != HttpMethod.GET) addError(operation, "403");
        if (method == HttpMethod.GET && path.equals("/api/v1/documents")) {
            addError(operation, "400");
            return;
        }
        if (!path.equals("/api/v1/documents")) addError(operation, "404");
        if (path.endsWith("/text") || path.endsWith("/manual-text")
                || path.endsWith("/reparse") || path.endsWith("/download-url")
                || method == HttpMethod.DELETE) {
            addError(operation, "409");
        }
        if (path.equals("/api/v1/documents") && method == HttpMethod.POST) {
            addError(operation, "400");
            addError(operation, "413");
            addError(operation, "415");
            addError(operation, "429");
            addError(operation, "503");
        }
        if (path.endsWith("/manual-text") || path.endsWith("/reparse")) {
            addError(operation, "400");
            addError(operation, "429");
        }
        if (path.endsWith("/download-url")) addError(operation, "503");
    }

    private void addProfileErrorResponses(
            String path, HttpMethod method, Operation operation) {
        addError(operation, "401");
        if (method != HttpMethod.GET) {
            addError(operation, "403");
        }

        boolean profileRoot = path.equals("/api/v1/profile");
        boolean evidenceCollection = path.equals("/api/v1/profile/evidence");
        boolean deferredDocumentCollection = path.equals("/api/v1/profile/certifications")
                || path.equals("/api/v1/profile/language-scores")
                || path.equals("/api/v1/profile/awards");
        if (method == HttpMethod.GET && !profileRoot) {
            addError(operation, "400");
            if (evidenceCollection) {
                addError(operation, "404");
            }
        } else if (method == HttpMethod.POST) {
            addError(operation, "400");
            if (deferredDocumentCollection) {
                addError(operation, "404");
            }
            if (path.equals("/api/v1/profile/educations")) {
                addError(operation, "409");
            }
        } else if (method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            addError(operation, "400");
            addError(operation, "409");
            if (!profileRoot) {
                addError(operation, "404");
            }
        } else if (method == HttpMethod.DELETE) {
            addError(operation, "404");
            addError(operation, "409");
        }
    }

    private void addError(Operation operation, String status) {
        operation.getResponses().addApiResponse(
                status,
                new ApiResponse()
                        .description("Error response")
                        .content(new Content().addMediaType(
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(new Schema<>()
                                        .$ref("#/components/schemas/ErrorResponseDto")))));
    }
}
