package com.hiresemble.githubsource.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubGatewayException.Kind;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HttpGitHubRestGatewayTest {

    private static final String COMMIT_SHA = "a".repeat(40);
    private static final String TREE_SHA = "b".repeat(40);
    private static final String BLOB_SHA = "c".repeat(40);

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void discoversUserAndOrganizationPublicRepositoriesWithAHardTwoHundredLimit() {
        server.stubFor(get(urlEqualTo("/users/octocat"))
                .willReturn(json("{\"type\":\"User\"}")));
        server.stubFor(get(urlEqualTo(
                        "/users/octocat/repos?type=public&sort=pushed&direction=desc&per_page=100&page=1"))
                .willReturn(json(repositoriesJson(1, 100))
                        .withHeader("Link", "<" + server.baseUrl()
                                + "/users/octocat/repos?page=2>; rel=\"next\"")));
        server.stubFor(get(urlEqualTo(
                        "/users/octocat/repos?type=public&sort=pushed&direction=desc&per_page=100&page=2"))
                .willReturn(json(repositoriesJson(101, 100))
                        .withHeader("Link", "<" + server.baseUrl()
                                + "/users/octocat/repos?page=3>; rel=\"next\"")));

        var user = gateway(Duration.ofSeconds(2), 2_000_000).discoverAccount("octocat");
        assertThat(user.accountType()).isEqualTo(GitHubAccountType.USER);
        assertThat(user.repositories()).hasSize(200);
        assertThat(user.truncated()).isTrue();
        assertThat(user.repositories()).allSatisfy(repository ->
                assertThat(repository.privateRepository()).isFalse());
        server.verify(getRequestedFor(urlEqualTo("/users/octocat"))
                .withHeader("X-GitHub-Api-Version", com.github.tomakehurst.wiremock.client.WireMock.equalTo("2026-03-10"))
                .withoutHeader("Authorization"));

        server.resetAll();
        server.stubFor(get(urlEqualTo("/users/acme"))
                .willReturn(json("{\"type\":\"Organization\"}")));
        server.stubFor(get(urlEqualTo(
                        "/orgs/acme/repos?type=public&sort=pushed&direction=desc&per_page=100&page=1"))
                .willReturn(json("[" + repositoryJson(301, "acme", "platform", false) + "]")));

        var organization = gateway(Duration.ofSeconds(2), 100_000).discoverAccount("acme");
        assertThat(organization.accountType()).isEqualTo(GitHubAccountType.ORGANIZATION);
        assertThat(organization.repositories()).singleElement()
                .satisfies(repository -> {
                    assertThat(repository.ownerLogin()).isEqualTo("acme");
                    assertThat(repository.repositoryName()).isEqualTo("platform");
                });
    }

    @Test
    void honorsConditionalRequestsAndReturnsBoundedTreeLanguageAndBlobData() {
        server.stubFor(get(urlEqualTo("/repos/octocat/hello-world"))
                .withHeader("If-None-Match", com.github.tomakehurst.wiremock.client.WireMock.equalTo("\"v1\""))
                .willReturn(aResponse().withStatus(304).withHeader("ETag", "\"v1\"")));
        assertThat(gateway(Duration.ofSeconds(2), 100_000)
                        .repository("octocat", "hello-world", "\"v1\"")
                        .notModified())
                .isTrue();

        server.stubFor(get(urlEqualTo("/repos/octocat/hello-world"))
                .atPriority(10)
                .willReturn(json(repositoryJson(1, "octocat", "hello-world", false))
                        .withHeader("ETag", "\"v2\"")));
        server.stubFor(get(urlEqualTo("/repos/octocat/hello-world/commits/main"))
                .willReturn(json("""
                        {"sha":"%s","commit":{"tree":{"sha":"%s"}}}
                        """.formatted(COMMIT_SHA, TREE_SHA))));
        server.stubFor(get(urlEqualTo("/repos/octocat/hello-world/git/trees/" + TREE_SHA + "?recursive=1"))
                .willReturn(json("""
                        {"sha":"%s","truncated":true,"tree":[
                          {"path":"README.md","mode":"100644","type":"blob","size":12,"sha":"%s"}
                        ]}
                        """.formatted(TREE_SHA, BLOB_SHA))));
        server.stubFor(get(urlEqualTo("/repos/octocat/hello-world/languages"))
                .willReturn(json("{\"Java\":1200,\"TypeScript\":800}")));
        String content = Base64.getEncoder().encodeToString("safe readme".getBytes(StandardCharsets.UTF_8));
        server.stubFor(get(urlEqualTo("/repos/octocat/hello-world/git/blobs/" + BLOB_SHA))
                .willReturn(json("""
                        {"sha":"%s","encoding":"base64","content":"%s"}
                        """.formatted(BLOB_SHA, content))));

        HttpGitHubRestGateway gateway = gateway(Duration.ofSeconds(2), 100_000);
        var repository = gateway.repository("octocat", "hello-world", null);
        var commit = gateway.defaultBranchCommit("octocat", "hello-world", "main");
        var tree = gateway.tree("octocat", "hello-world", TREE_SHA);
        var languages = gateway.languages("octocat", "hello-world");
        var blob = gateway.blob("octocat", "hello-world", BLOB_SHA);

        assertThat(repository.notModified()).isFalse();
        assertThat(repository.repository().etag()).isEqualTo("\"v2\"");
        assertThat(commit.commitSha()).isEqualTo(COMMIT_SHA);
        assertThat(tree.truncated()).isTrue();
        assertThat(tree.entries()).singleElement()
                .extracting(entry -> entry.path())
                .isEqualTo("README.md");
        assertThat(languages).containsEntry("Java", 1200L);
        assertThat(blob.content()).isEqualTo("safe readme".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void mapsNotFoundRateLimitServerTimeoutOversizeAndRedirectWithoutFollowing() {
        server.stubFor(get(urlEqualTo("/repos/octocat/missing"))
                .willReturn(aResponse().withStatus(404).withBody("upstream body must stay private")));
        assertFailure(() -> gateway(Duration.ofSeconds(1), 10_000)
                .repository("octocat", "missing", null), Kind.NOT_FOUND);

        server.resetAll();
        server.stubFor(get(urlEqualTo("/repos/octocat/rate-limited"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "17")));
        assertThatThrownBy(() -> gateway(Duration.ofSeconds(1), 10_000)
                        .repository("octocat", "rate-limited", null))
                .isInstanceOf(GitHubGatewayException.class)
                .satisfies(error -> {
                    GitHubGatewayException failure = (GitHubGatewayException) error;
                    assertThat(failure.kind()).isEqualTo(Kind.RATE_LIMITED);
                    assertThat(failure.retryAfter()).isEqualTo(Duration.ofSeconds(17));
                    assertThat(failure.getMessage()).doesNotContain("Retry-After", server.baseUrl());
                });

        server.resetAll();
        server.stubFor(get(urlEqualTo("/repos/octocat/server-error"))
                .willReturn(aResponse().withStatus(503)));
        assertFailure(() -> gateway(Duration.ofSeconds(1), 10_000)
                .repository("octocat", "server-error", null), Kind.UPSTREAM_5XX);

        server.resetAll();
        server.stubFor(get(urlEqualTo("/repos/octocat/slow"))
                .willReturn(json(repositoryJson(1, "octocat", "slow", false)).withFixedDelay(250)));
        assertFailure(() -> gateway(Duration.ofMillis(50), 10_000)
                .repository("octocat", "slow", null), Kind.TIMEOUT);

        server.resetAll();
        server.stubFor(get(urlEqualTo("/repos/octocat/oversize"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(2_000))));
        assertFailure(() -> gateway(Duration.ofSeconds(1), 1024)
                .repository("octocat", "oversize", null), Kind.RESPONSE_LIMIT);

        server.resetAll();
        server.stubFor(get(urlEqualTo("/repos/octocat/redirect"))
                .willReturn(aResponse().withStatus(302)
                        .withHeader("Location", "https://evil.example/steal")));
        assertFailure(() -> gateway(Duration.ofSeconds(1), 10_000)
                .repository("octocat", "redirect", null), Kind.INVALID_RESPONSE);
        server.verify(0, getRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.urlMatching("/steal.*")));
    }

    private void assertFailure(ThrowingCall call, Kind expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GitHubGatewayException.class)
                .satisfies(error -> assertThat(((GitHubGatewayException) error).kind())
                        .isEqualTo(expected));
    }

    private HttpGitHubRestGateway gateway(Duration timeout, int maxResponseBytes) {
        return new HttpGitHubRestGateway(
                URI.create(server.baseUrl()),
                "2026-03-10",
                Duration.ofSeconds(1),
                timeout,
                maxResponseBytes,
                64 * 1024,
                2,
                new ObjectMapper(),
                true);
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private String repositoriesJson(int firstId, int count) {
        StringBuilder body = new StringBuilder("[");
        for (int index = 0; index < count; index++) {
            if (index > 0) body.append(',');
            body.append(repositoryJson(
                    firstId + index,
                    "octocat",
                    "repo-" + (firstId + index),
                    false));
        }
        return body.append(']').toString();
    }

    private String repositoryJson(long id, String owner, String name, boolean privateRepository) {
        return """
                {
                  "id":%d,
                  "node_id":"node-%d",
                  "owner":{"login":"%s"},
                  "name":"%s",
                  "default_branch":"main",
                  "private":%s,
                  "fork":false,
                  "archived":false,
                  "description":"Fixture repository",
                  "topics":["java","backend"],
                  "pushed_at":"2026-08-01T00:00:00Z"
                }
                """.formatted(id, id, owner, name, privateRepository);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
