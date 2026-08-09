package com.hiresemble.githubsource.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubGatewayException.Kind;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpGitHubAppRemoteGatewayTest {

    private WireMockServer server;
    private GitHubAppJwtSigner jwtSigner;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        jwtSigner = mock(GitHubAppJwtSigner.class);
        when(jwtSigner.create()).thenReturn("app-jwt-fixture");
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void createsOnlyFixedInstallationAndPkceOAuthUrls() {
        HttpGitHubAppRemoteGateway gateway = gateway();

        assertThat(gateway.installationUrl("install-state"))
                .isEqualTo(URI.create(server.baseUrl()
                        + "/apps/hiresemble-test/installations/new?state=install-state"));
        assertThat(gateway.oauthAuthorizationUrl("oauth-state", "challenge-value"))
                .isEqualTo(URI.create(server.baseUrl()
                        + "/login/oauth/authorize?client_id=client_id_12345"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Fgithub-app-connections%2Foauth%2Fcallback"
                        + "&state=oauth-state&code_challenge=challenge-value&code_challenge_method=S256"));

        assertThatThrownBy(() -> new HttpGitHubAppRemoteGateway(
                        properties(), jwtSigner, new ObjectMapper(),
                        URI.create("http://attacker.example"), URI.create(server.baseUrl()), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exchangesOAuthCodeWithPkceAndVerifiesUserInstallationWithoutPersistingTokens() {
        server.stubFor(post(urlEqualTo("/login/oauth/access_token"))
                .withRequestBody(equalToJson("""
                        {
                          "client_id":"client_id_12345",
                          "client_secret":"safe-test-client-secret",
                          "code":"one-time-code",
                          "redirect_uri":"http://localhost:8080/api/v1/github-app-connections/oauth/callback",
                          "code_verifier":"pkce-verifier"
                        }
                        """))
                .willReturn(json("{\"access_token\":\"user-token-fixture\"}")));
        server.stubFor(get(urlEqualTo("/user/installations/91/repositories?per_page=100&page=1"))
                .withHeader("Authorization", equalTo("Bearer user-token-fixture"))
                .willReturn(json(repositories())));
        server.stubFor(get(urlEqualTo("/app/installations/91"))
                .withHeader("Authorization", equalTo("Bearer app-jwt-fixture"))
                .willReturn(json(installation())));

        HttpGitHubAppRemoteGateway gateway = gateway();
        var authorization = gateway.exchangeUserCode("one-time-code", "pkce-verifier");
        var verified = gateway.verifyUserInstallation(authorization.accessToken(), 91L);

        assertThat(verified.installationId()).isEqualTo(91L);
        assertThat(verified.targetAccountLogin()).isEqualTo("acme");
        assertThat(verified.repositories()).singleElement().satisfies(repository -> {
            assertThat(repository.externalId()).isEqualTo(7101L);
            assertThat(repository.privateRepository()).isTrue();
        });
        server.verify(postRequestedFor(urlEqualTo("/login/oauth/access_token"))
                .withoutHeader("Authorization"));
        server.verify(getRequestedFor(urlEqualTo(
                "/user/installations/91/repositories?per_page=100&page=1")));
    }

    @Test
    void installationTokensAreRepositoryScopedAndReadOnly() {
        server.stubFor(post(urlEqualTo("/app/installations/91/access_tokens"))
                .withHeader("Authorization", equalTo("Bearer app-jwt-fixture"))
                .withRequestBody(equalToJson("""
                        {"repository_ids":[7101],"permissions":{"contents":"read"}}
                        """))
                .willReturn(json("""
                        {
                          "token":"installation-token-fixture",
                          "expires_at":"2026-08-09T01:00:00Z",
                          "permissions":{"metadata":"read","contents":"read"}
                        }
                        """)));

        var token = gateway().mintInstallationToken(91L, 7101L, true);

        assertThat(token.value()).isEqualTo("installation-token-fixture");
        assertThat(token.expiresAt()).hasToString("2026-08-09T01:00:00Z");
        server.verify(postRequestedFor(urlEqualTo("/app/installations/91/access_tokens")));
        assertThatThrownBy(() -> gateway().mintInstallationToken(91L, null, true))
                .isInstanceOfSatisfying(GitHubGatewayException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(Kind.PERMISSION));
    }

    @Test
    void permissionMismatchAndUninstallResponsesAreMappedSafely() {
        server.stubFor(post(urlEqualTo("/app/installations/91/access_tokens"))
                .willReturn(json("""
                        {
                          "token":"installation-token-fixture",
                          "expires_at":"2026-08-09T01:00:00Z",
                          "permissions":{"metadata":"read","contents":"write"}
                        }
                        """)));
        assertThatThrownBy(() -> gateway().mintInstallationToken(91L, 7101L, true))
                .isInstanceOfSatisfying(GitHubGatewayException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(Kind.PERMISSION));

        server.resetAll();
        server.stubFor(delete(urlEqualTo("/app/installations/91"))
                .willReturn(aResponse().withStatus(404)));
        gateway().uninstall(91L);
        server.verify(deleteRequestedFor(urlEqualTo("/app/installations/91"))
                .withHeader("Authorization", equalTo("Bearer app-jwt-fixture")));

        server.resetAll();
        server.stubFor(delete(urlEqualTo("/app/installations/91"))
                .willReturn(aResponse().withStatus(503).withBody("unsafe upstream body")));
        assertThatThrownBy(() -> gateway().uninstall(91L))
                .isInstanceOfSatisfying(GitHubGatewayException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.UPSTREAM_5XX);
                    assertThat(exception.getMessage()).doesNotContain("unsafe upstream body");
                });
    }

    private HttpGitHubAppRemoteGateway gateway() {
        URI base = URI.create(server.baseUrl());
        return new HttpGitHubAppRemoteGateway(
                properties(), jwtSigner, new ObjectMapper(), base, base, true);
    }

    private GitHubProperties properties() {
        GitHubProperties properties = new GitHubProperties();
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(2));
        properties.getApp().setSlug("hiresemble-test");
        properties.getApp().setClientId("client_id_12345");
        properties.getApp().setClientSecret("safe-test-client-secret");
        properties.getApp().setBackendBaseUrl(URI.create("http://localhost:8080"));
        return properties;
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private String installation() {
        return """
                {
                  "id":91,
                  "account":{"id":801,"login":"acme","type":"Organization"},
                  "repository_selection":"selected",
                  "permissions":{"metadata":"read","contents":"read"},
                  "suspended_at":null
                }
                """;
    }

    private String repositories() {
        return """
                {
                  "repositories":[{
                    "id":7101,
                    "node_id":"repo-node-7101",
                    "owner":{"login":"acme"},
                    "name":"private-platform",
                    "default_branch":"main",
                    "private":true,
                    "fork":false,
                    "archived":false,
                    "description":"Private platform",
                    "topics":["java"],
                    "pushed_at":"2026-08-08T00:00:00Z"
                  }]
                }
                """;
    }
}
