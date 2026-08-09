package com.hiresemble.githubsource.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpGitHubPublicArchiveGatewayTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void downloadsOneBoundedArchiveInsteadOfOneRestRequestPerFile() throws Exception {
        server.stubFor(get(urlEqualTo("/acme/sample/archive/HEAD.zip"))
                .willReturn(aResponse().withStatus(302)
                        .withHeader("Location", server.baseUrl() + "/acme/sample/zip/" + SHA)));
        server.stubFor(get(urlEqualTo("/acme/sample/zip/" + SHA))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(zip(
                                "sample-root/README.md", "hello",
                                "sample-root/src/App.java", "class App {}"))));

        var archive = gateway(1024 * 1024).download("acme", "sample");

        assertThat(archive.commitSha()).isEqualTo(SHA);
        assertThat(archive.files()).extracting(file -> file.entry().path())
                .containsExactly("README.md", "src/App.java");
        assertThat(archive.files()).allSatisfy(file ->
                assertThat(file.entry().sha()).matches("[0-9a-f]{40}"));
    }

    @Test
    void rejectsRedirectOutsideTheConfiguredCodeloadOrigin() {
        server.stubFor(get(urlEqualTo("/acme/sample/archive/HEAD.zip"))
                .willReturn(aResponse().withStatus(302)
                        .withHeader("Location", "https://example.com/acme/sample/zip/" + SHA)));

        assertThatThrownBy(() -> gateway(1024).download("acme", "sample"))
                .isInstanceOf(GitHubGatewayException.class)
                .extracting(value -> ((GitHubGatewayException) value).kind())
                .isEqualTo(GitHubGatewayException.Kind.INVALID_RESPONSE);
    }

    @Test
    void rejectsCompressedArchiveAboveTheBound() throws Exception {
        server.stubFor(get(urlEqualTo("/acme/sample/archive/HEAD.zip"))
                .willReturn(aResponse().withStatus(302)
                        .withHeader("Location", server.baseUrl() + "/acme/sample/zip/" + SHA)));
        server.stubFor(get(urlEqualTo("/acme/sample/zip/" + SHA))
                .willReturn(aResponse().withStatus(200).withBody(zip(
                        "sample-root/README.md", "content larger than the tiny archive bound"))));

        assertThatThrownBy(() -> gateway(16).download("acme", "sample"))
                .isInstanceOf(GitHubGatewayException.class)
                .extracting(value -> ((GitHubGatewayException) value).kind())
                .isEqualTo(GitHubGatewayException.Kind.RESPONSE_LIMIT);
    }

    private HttpGitHubPublicArchiveGateway gateway(int maxArchiveBytes) {
        URI base = URI.create(server.baseUrl());
        return new HttpGitHubPublicArchiveGateway(
                base, base, Duration.ofSeconds(1), Duration.ofSeconds(2),
                maxArchiveBytes, 64 * 1024, 2, true);
    }

    private byte[] zip(String... pathAndContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int index = 0; index < pathAndContent.length; index += 2) {
                zip.putNextEntry(new ZipEntry(pathAndContent[index]));
                zip.write(pathAndContent[index + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
