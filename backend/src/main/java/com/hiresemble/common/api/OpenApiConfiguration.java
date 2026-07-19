package com.hiresemble.common.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Hiresemble API",
                version = "1.1",
                description = "P1 authentication API. Successful DTOs are returned directly without an envelope."),
        tags = @Tag(
                name = "Authentication",
                description = """
                        Browser Session and CSRF authentication. In the same-origin Swagger UI, first call
                        GET /api/v1/auth/csrf so the browser stores the anonymous SESSION cookie. Copy the
                        JSON token into Authorize > csrfToken, then run a mutation. After signup or login,
                        keep the browser-managed rotated SESSION cookie and replace csrfToken with the new
                        csrf.token from AuthSessionDto. Swagger UI cookie/local-storage CSRF automation is
                        intentionally not enabled because this API returns its token in JSON.
                        """))
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
    OpenApiCustomizer authenticationSecurityRequirements() {
        return openApi -> openApi.getPaths()
                .get("/api/v1/auth/logout")
                .getPost()
                .setSecurity(List.of(new SecurityRequirement()
                        .addList("sessionCookie")
                        .addList("csrfToken")));
    }
}
