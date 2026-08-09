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
import com.hiresemble.githubsource.application.GitHubAccessContext;
import com.hiresemble.githubsource.application.GitHubInstallationTokenProvider;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void privateAccessUsesRepositoryScopedBearerForMetadataTreeLanguageAndBlobReads() {
        UUID connectionId = UUID.randomUUID();
        long repositoryId = 8123L;
        RecordingTokenProvider provider = new RecordingTokenProvider();
        HttpGitHubRestGateway gateway = privateGateway(provider);
        GitHubAccessContext discovery = GitHubAccessContext.appDiscovery(connectionId);
        GitHubAccessContext repositoryAccess =
                GitHubAccessContext.appRepository(connectionId, repositoryId);

        server.stubFor(get(urlEqualTo("/installation/repositories?per_page=100&page=1"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer discovery-memory-fixture"))
                .willReturn(json("{\"repositories\":["
                        + repositoryJson(repositoryId, "acme", "private-repo", true) + "]}")));
        server.stubFor(get(urlEqualTo("/repos/acme/private-repo"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer contents-memory-fixture"))
                .willReturn(json(repositoryJson(repositoryId, "acme", "private-repo", true))));
        server.stubFor(get(urlEqualTo("/repos/acme/private-repo/commits/main"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer contents-memory-fixture"))
                .willReturn(json("{\"sha\":\"" + COMMIT_SHA
                        + "\",\"commit\":{\"tree\":{\"sha\":\"" + TREE_SHA + "\"}}}")));
        server.stubFor(get(urlEqualTo(
                        "/repos/acme/private-repo/git/trees/" + TREE_SHA + "?recursive=1"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer contents-memory-fixture"))
                .willReturn(json("{\"sha\":\"" + TREE_SHA
                        + "\",\"truncated\":false,\"tree\":[{\"path\":\"README.md\","
                        + "\"mode\":\"100644\",\"type\":\"blob\",\"size\":4,\"sha\":\""
                        + BLOB_SHA + "\"}]}")));
        server.stubFor(get(urlEqualTo("/repos/acme/private-repo/languages"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer contents-memory-fixture"))
                .willReturn(json("{\"Java\":42}")));
        String content = Base64.getEncoder().encodeToString(
                "safe".getBytes(StandardCharsets.UTF_8));
        server.stubFor(get(urlEqualTo("/repos/acme/private-repo/git/blobs/" + BLOB_SHA))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer contents-memory-fixture"))
                .willReturn(json("{\"sha\":\"" + BLOB_SHA
                        + "\",\"encoding\":\"base64\",\"content\":\"" + content + "\"}")));

        assertThat(gateway.discoverAccount(discovery, "acme").repositories())
                .singleElement()
                .satisfies(repository -> assertThat(repository.privateRepository()).isTrue());
        assertThat(gateway.repository(repositoryAccess, "acme", "private-repo", null)
                        .repository().externalId())
                .isEqualTo(repositoryId);
        assertThat(gateway.defaultBranchCommit(repositoryAccess, "acme", "private-repo", "main")
                        .treeSha())
                .isEqualTo(TREE_SHA);
        assertThat(gateway.tree(repositoryAccess, "acme", "private-repo", TREE_SHA).entries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.path()).isEqualTo("README.md"));
        assertThat(gateway.languages(repositoryAccess, "acme", "private-repo"))
                .containsEntry("Java", 42L);
        assertThat(gateway.blob(repositoryAccess, "acme", "private-repo", BLOB_SHA).content())
                .isEqualTo("safe".getBytes(StandardCharsets.UTF_8));
        assertThat(provider.discoveryRequests.get()).isEqualTo(2);
        assertThat(provider.contentsRequests.get()).isEqualTo(5);
    }

    @Test
    void privateReadInvalidatesAndReissuesOnceAfterAuthenticationFailure() {
        UUID connectionId = UUID.randomUUID();
        long repositoryId = 9123L;
        RotatingTokenProvider provider = new RotatingTokenProvider();
        HttpGitHubRestGateway gateway = privateGateway(provider);
        String path = "/repos/acme/private-repo";
        server.stubFor(get(urlEqualTo(path))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer expired-memory-fixture"))
                .willReturn(aResponse().withStatus(401)));
        server.stubFor(get(urlEqualTo(path))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "Bearer renewed-memory-fixture"))
                .willReturn(json(repositoryJson(repositoryId, "acme", "private-repo", true))));

        assertThat(gateway.repository(
                                GitHubAccessContext.appRepository(connectionId, repositoryId),
                                "acme",
                                "private-repo",
                                null)
                        .repository().externalId())
                .isEqualTo(repositoryId);
        assertThat(provider.authorizationRequests.get()).isEqualTo(2);
        assertThat(provider.invalidations.get()).isEqualTo(1);
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

    private HttpGitHubRestGateway privateGateway(GitHubInstallationTokenProvider provider) {
        return new HttpGitHubRestGateway(
                URI.create(server.baseUrl()),
                "2026-03-10",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                2_000_000,
                64 * 1024,
                2,
                new ObjectMapper(),
                true,
                provider);
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

    private static final class RecordingTokenProvider implements GitHubInstallationTokenProvider {
        private final AtomicInteger discoveryRequests = new AtomicInteger();
        private final AtomicInteger contentsRequests = new AtomicInteger();

        @Override
        public AuthorizedToken authorize(
                UUID connectionId, Long externalRepositoryId, boolean contentsRead) {
            if (contentsRead) {
                contentsRequests.incrementAndGet();
                assertThat(externalRepositoryId).isEqualTo(8123L);
                return new AuthorizedToken(
                        "contents-memory-fixture",
                        Instant.parse("2026-08-09T01:00:00Z"),
                        GitHubAccountType.ORGANIZATION);
            }
            discoveryRequests.incrementAndGet();
            assertThat(externalRepositoryId).isNull();
            return new AuthorizedToken(
                    "discovery-memory-fixture",
                    Instant.parse("2026-08-09T01:00:00Z"),
                    GitHubAccountType.ORGANIZATION);
        }

        @Override
        public void invalidate(UUID connectionId, Long externalRepositoryId) {}
    }

    private static final class RotatingTokenProvider implements GitHubInstallationTokenProvider {
        private final AtomicInteger authorizationRequests = new AtomicInteger();
        private final AtomicInteger invalidations = new AtomicInteger();

        @Override
        public AuthorizedToken authorize(
                UUID connectionId, Long externalRepositoryId, boolean contentsRead) {
            int attempt = authorizationRequests.incrementAndGet();
            return new AuthorizedToken(
                    attempt == 1 ? "expired-memory-fixture" : "renewed-memory-fixture",
                    Instant.parse("2026-08-09T01:00:00Z"),
                    GitHubAccountType.ORGANIZATION);
        }

        @Override
        public void invalidate(UUID connectionId, Long externalRepositoryId) {
            invalidations.incrementAndGet();
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
