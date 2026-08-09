package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubGatewayException.Kind;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubPublicArchiveGateway;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class HttpGitHubPublicArchiveGateway implements GitHubPublicArchiveGateway {

    private static final URI GITHUB_BASE_URL = URI.create("https://github.com");
    private static final URI CODELOAD_BASE_URL = URI.create("https://codeload.github.com");
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;

    private final URI githubBaseUrl;
    private final URI codeloadBaseUrl;
    private final Duration responseTimeout;
    private final int maxArchiveBytes;
    private final int maxTextFileBytes;
    private final HttpClient client;
    private final Semaphore concurrency;

    @Autowired
    public HttpGitHubPublicArchiveGateway(GitHubProperties properties) {
        this(
                GITHUB_BASE_URL,
                CODELOAD_BASE_URL,
                properties.getConnectTimeout(),
                properties.getResponseTimeout(),
                properties.getMaxArchiveBytes(),
                properties.getMaxTextFileBytes(),
                properties.getMaxConcurrentRequests(),
                false);
    }

    HttpGitHubPublicArchiveGateway(
            URI githubBaseUrl,
            URI codeloadBaseUrl,
            Duration connectTimeout,
            Duration responseTimeout,
            int maxArchiveBytes,
            int maxTextFileBytes,
            int maxConcurrentRequests,
            boolean allowLoopbackTestBaseUrl) {
        requireBase(githubBaseUrl, "github.com", allowLoopbackTestBaseUrl);
        requireBase(codeloadBaseUrl, "codeload.github.com", allowLoopbackTestBaseUrl);
        this.githubBaseUrl = githubBaseUrl;
        this.codeloadBaseUrl = codeloadBaseUrl;
        this.responseTimeout = responseTimeout;
        this.maxArchiveBytes = maxArchiveBytes;
        this.maxTextFileBytes = maxTextFileBytes;
        this.concurrency = new Semaphore(maxConcurrentRequests, true);
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public PublicArchive download(String ownerLogin, String repositoryName) {
        String owner = segment(ownerLogin);
        String repository = segment(repositoryName);
        URI archiveUri = withResponse(
                githubBaseUrl.resolve("/" + owner + "/" + repository + "/archive/HEAD.zip"),
                redirect -> {
                    if (redirect.statusCode() != 302) {
                        throw statusFailure(redirect.statusCode());
                    }
                    return redirect.headers()
                            .firstValue("location")
                            .map(githubBaseUrl::resolve)
                            .orElseThrow(() -> new GitHubGatewayException(Kind.INVALID_RESPONSE));
                });
        String commitSha = requireArchiveLocation(archiveUri, owner, repository);
        byte[] compressed = withResponse(archiveUri, archive -> {
            if (archive.statusCode() < 200 || archive.statusCode() >= 300) {
                throw statusFailure(archive.statusCode());
            }
            return boundedBody(archive.body());
        });
        return unzip(commitSha, compressed);
    }

    private PublicArchive unzip(String commitSha, byte[] compressed) {
        List<ArchiveFile> files = new ArrayList<>();
        long totalBytes = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(compressed))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++entries > MAX_ARCHIVE_ENTRIES) {
                    throw new GitHubGatewayException(Kind.RESPONSE_LIMIT);
                }
                if (entry.isDirectory()) continue;
                String path = relativePath(entry.getName());
                ByteArrayOutputStream retained = new ByteArrayOutputStream();
                long entryBytes = 0;
                byte[] buffer = new byte[8192];
                for (int read; (read = zip.read(buffer)) != -1; ) {
                    entryBytes += read;
                    totalBytes += read;
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw new GitHubGatewayException(Kind.RESPONSE_LIMIT);
                    }
                    if (retained.size() <= maxTextFileBytes) retained.write(buffer, 0, read);
                }
                if (path == null || entryBytes > maxTextFileBytes) continue;
                byte[] content = retained.toByteArray();
                files.add(new ArchiveFile(
                        new TreeEntry(path, "100644", "blob", entryBytes, gitBlobSha(content)),
                        content));
            }
        } catch (GitHubGatewayException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
        if (files.isEmpty()) throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        return new PublicArchive(commitSha, files, false);
    }

    private <T> T withResponse(
            URI target, Function<HttpResponse<InputStream>, T> responseHandler) {
        boolean acquired = false;
        try {
            acquired = concurrency.tryAcquire(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) throw new GitHubGatewayException(Kind.TIMEOUT);
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(responseTimeout)
                    .header("Accept", "application/zip")
                    .header("User-Agent", "hiresemble-github-ingestion/1")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream ignored = response.body()) {
                return responseHandler.apply(response);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new GitHubGatewayException(Kind.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitHubGatewayException(Kind.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new GitHubGatewayException(Kind.UPSTREAM_5XX, exception);
        } finally {
            if (acquired) concurrency.release();
        }
    }

    private byte[] boundedBody(InputStream body) {
        try (InputStream stream = body) {
            byte[] bytes = stream.readNBytes(maxArchiveBytes + 1);
            if (bytes.length > maxArchiveBytes) throw new GitHubGatewayException(Kind.RESPONSE_LIMIT);
            return bytes;
        } catch (GitHubGatewayException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new GitHubGatewayException(Kind.UPSTREAM_5XX, exception);
        }
    }

    private String requireArchiveLocation(URI value, String owner, String repository) {
        if (!sameOrigin(codeloadBaseUrl, value) || value.getQuery() != null || value.getFragment() != null) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        String prefix = "/" + owner + "/" + repository + "/zip/";
        String path = value.getPath();
        String sha = path != null && path.startsWith(prefix) ? path.substring(prefix.length()) : "";
        if (!sha.matches("[0-9a-f]{40}")) throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        return sha;
    }

    private String relativePath(String archivePath) {
        if (archivePath == null || archivePath.startsWith("/") || archivePath.contains("\\")
                || archivePath.codePoints().anyMatch(Character::isISOControl)) return null;
        int slash = archivePath.indexOf('/');
        if (slash < 1 || slash == archivePath.length() - 1) return null;
        String relative = archivePath.substring(slash + 1);
        for (String part : relative.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) return null;
        }
        return relative;
    }

    private String gitBlobSha(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + content.length + "\0").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-1 unavailable", exception);
        }
    }

    private GitHubGatewayException statusFailure(int status) {
        if (status == 404) return new GitHubGatewayException(Kind.NOT_FOUND);
        if (status == 403 || status == 429) return new GitHubGatewayException(Kind.RATE_LIMITED);
        if (status >= 500) return new GitHubGatewayException(Kind.UPSTREAM_5XX);
        return new GitHubGatewayException(Kind.INVALID_RESPONSE);
    }

    private String segment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,100}")) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        return value;
    }

    private static boolean sameOrigin(URI expected, URI actual) {
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && expected.getPort() == actual.getPort()
                && actual.getUserInfo() == null;
    }

    private static void requireBase(URI value, String productionHost, boolean allowLoopback) {
        boolean production = value != null && "https".equalsIgnoreCase(value.getScheme())
                && productionHost.equalsIgnoreCase(value.getHost()) && value.getPort() == -1;
        boolean loopback = allowLoopback && value != null
                && ("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))
                && ("localhost".equalsIgnoreCase(value.getHost()) || "127.0.0.1".equals(value.getHost()))
                && value.getUserInfo() == null;
        if ((!production && !loopback) || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("GitHub archive base URL is not allowed");
        }
    }
}
