package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubGatewayException.Kind;
import com.hiresemble.githubsource.application.GitHubGatewayModels.AccountDiscovery;
import com.hiresemble.githubsource.application.GitHubGatewayModels.Blob;
import com.hiresemble.githubsource.application.GitHubGatewayModels.CommitMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.ConditionalRepository;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeEntry;
import com.hiresemble.githubsource.application.GitHubGatewayModels.TreeSnapshot;
import com.hiresemble.githubsource.application.GitHubRestGateway;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HttpGitHubRestGateway implements GitHubRestGateway {

    private static final String ACCEPT = "application/vnd.github+json";

    private final URI baseUrl;
    private final String apiVersion;
    private final Duration responseTimeout;
    private final int maxResponseBytes;
    private final int maxTextFileBytes;
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrency;

    @Autowired
    public HttpGitHubRestGateway(GitHubProperties properties, ObjectMapper objectMapper) {
        this(
                properties.getApiBaseUrl(),
                properties.getApiVersion(),
                properties.getConnectTimeout(),
                properties.getResponseTimeout(),
                properties.getMaxResponseBytes(),
                properties.getMaxTextFileBytes(),
                properties.getMaxConcurrentRequests(),
                objectMapper,
                false);
    }

    HttpGitHubRestGateway(
            URI baseUrl,
            String apiVersion,
            Duration connectTimeout,
            Duration responseTimeout,
            int maxResponseBytes,
            int maxTextFileBytes,
            int maxConcurrentRequests,
            ObjectMapper objectMapper,
            boolean allowLoopbackTestBaseUrl) {
        requireAllowedBaseUrl(baseUrl, allowLoopbackTestBaseUrl);
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.responseTimeout = responseTimeout;
        this.maxResponseBytes = maxResponseBytes;
        this.maxTextFileBytes = maxTextFileBytes;
        this.objectMapper = objectMapper;
        this.concurrency = new Semaphore(maxConcurrentRequests, true);
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public AccountDiscovery discoverAccount(String ownerLogin) {
        String owner = segment(ownerLogin);
        Response account = get("/users/" + owner, null);
        JsonNode accountJson = json(account.body());
        GitHubAccountType accountType = "Organization".equalsIgnoreCase(accountJson.path("type").asText())
                ? GitHubAccountType.ORGANIZATION
                : GitHubAccountType.USER;
        String repositoryPath = accountType == GitHubAccountType.ORGANIZATION
                ? "/orgs/" + owner + "/repos"
                : "/users/" + owner + "/repos";
        List<RepositoryMetadata> repositories = new ArrayList<>();
        Response first = get(repositoryPath
                + "?type=public&sort=pushed&direction=desc&per_page=100&page=1", null);
        repositories.addAll(repositoryPage(first));
        boolean hasSecond = hasNext(first) || repositories.size() == 100;
        Response second = null;
        if (hasSecond) {
            second = get(repositoryPath
                    + "?type=public&sort=pushed&direction=desc&per_page=100&page=2", null);
            repositories.addAll(repositoryPage(second));
        }
        boolean truncated = second != null && hasNext(second);
        if (repositories.size() > 200) {
            repositories = new ArrayList<>(repositories.subList(0, 200));
            truncated = true;
        }
        return new AccountDiscovery(accountType, repositories, truncated);
    }

    @Override
    public ConditionalRepository repository(
            String ownerLogin, String repositoryName, String etag) {
        Response response = get(
                "/repos/" + segment(ownerLogin) + "/" + segment(repositoryName), etag);
        if (response.status() == 304) {
            return new ConditionalRepository(null, true);
        }
        return new ConditionalRepository(repository(json(response.body()), response.etag()), false);
    }

    @Override
    public CommitMetadata defaultBranchCommit(
            String ownerLogin, String repositoryName, String defaultBranch) {
        Response response = get(
                "/repos/" + segment(ownerLogin) + "/" + segment(repositoryName)
                        + "/commits/" + encodedPathValue(defaultBranch),
                null);
        JsonNode value = json(response.body());
        return new CommitMetadata(
                sha(value.path("sha").asText()),
                sha(value.path("commit").path("tree").path("sha").asText()),
                response.etag());
    }

    @Override
    public TreeSnapshot tree(String ownerLogin, String repositoryName, String treeSha) {
        Response response = get(
                "/repos/" + segment(ownerLogin) + "/" + segment(repositoryName)
                        + "/git/trees/" + sha(treeSha) + "?recursive=1",
                null);
        JsonNode value = json(response.body());
        List<TreeEntry> entries = new ArrayList<>();
        JsonNode tree = value.path("tree");
        if (!tree.isArray()) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        for (JsonNode item : tree) {
            String path = item.path("path").asText();
            String type = item.path("type").asText();
            String mode = item.path("mode").asText();
            long size = item.path("size").canConvertToLong() ? item.path("size").asLong() : -1L;
            String itemSha = item.path("sha").asText();
            if (path.isBlank() || path.length() > 1000 || type.isBlank() || mode.isBlank()
                    || !itemSha.matches("[0-9a-f]{40}")) {
                throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
            }
            entries.add(new TreeEntry(path, mode, type, size, itemSha));
        }
        return new TreeSnapshot(
                sha(value.path("sha").asText()),
                entries,
                value.path("truncated").asBoolean(false),
                response.etag());
    }

    @Override
    public Map<String, Long> languages(String ownerLogin, String repositoryName) {
        Response response = get(
                "/repos/" + segment(ownerLogin) + "/" + segment(repositoryName) + "/languages",
                null);
        JsonNode value = json(response.body());
        if (!value.isObject()) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        Map<String, Long> result = new LinkedHashMap<>();
        value.properties().forEach(entry -> {
            long bytes = entry.getValue().asLong(-1);
            if (entry.getKey().length() <= 80 && bytes >= 0) {
                result.put(entry.getKey(), bytes);
            }
        });
        return Map.copyOf(result);
    }

    @Override
    public Blob blob(String ownerLogin, String repositoryName, String blobSha) {
        Response response = get(
                "/repos/" + segment(ownerLogin) + "/" + segment(repositoryName)
                        + "/git/blobs/" + sha(blobSha),
                null);
        JsonNode value = json(response.body());
        if (!"base64".equalsIgnoreCase(value.path("encoding").asText())) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        byte[] content;
        try {
            content = Base64.getMimeDecoder().decode(value.path("content").asText());
        } catch (IllegalArgumentException exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
        if (content.length > maxTextFileBytes) {
            throw new GitHubGatewayException(Kind.RESPONSE_LIMIT);
        }
        return new Blob(sha(value.path("sha").asText()), content);
    }

    private List<RepositoryMetadata> repositoryPage(Response response) {
        JsonNode values = json(response.body());
        if (!values.isArray()) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        List<RepositoryMetadata> repositories = new ArrayList<>();
        for (JsonNode value : values) {
            RepositoryMetadata metadata = repository(value, null);
            if (!metadata.privateRepository()) {
                repositories.add(metadata);
            }
        }
        return repositories;
    }

    private RepositoryMetadata repository(JsonNode value, String etag) {
        long externalId = value.path("id").asLong(-1L);
        String nodeId = value.path("node_id").asText();
        String owner = value.path("owner").path("login").asText();
        String name = value.path("name").asText();
        String defaultBranch = value.path("default_branch").asText();
        boolean privateRepository = value.path("private").asBoolean(true);
        if (externalId <= 0 || nodeId.isBlank() || nodeId.length() > 100
                || owner.isBlank() || owner.length() > 100
                || name.isBlank() || name.length() > 100
                || defaultBranch.isBlank() || defaultBranch.length() > 255) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        List<String> topics = new ArrayList<>();
        JsonNode topicValues = value.path("topics");
        if (topicValues.isArray()) {
            topicValues.forEach(topic -> {
                String text = topic.asText();
                if (!text.isBlank() && text.length() <= 100 && topics.size() < 100) {
                    topics.add(text);
                }
            });
        }
        String description = value.path("description").isNull()
                ? null : bounded(value.path("description").asText(), 500);
        Instant pushedAt = instantOrNull(value.path("pushed_at").asText(null));
        return new RepositoryMetadata(
                externalId,
                nodeId,
                owner,
                name,
                "https://github.com/" + owner + "/" + name,
                defaultBranch,
                privateRepository,
                value.path("fork").asBoolean(false),
                value.path("archived").asBoolean(false),
                description,
                topics,
                pushedAt,
                etag);
    }

    private Response get(String relativePath, String etag) {
        boolean acquired = false;
        try {
            acquired = concurrency.tryAcquire(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new GitHubGatewayException(Kind.TIMEOUT);
            }
            URI target = baseUrl.resolve(relativePath);
            requireSameOrigin(target);
            HttpRequest.Builder request = HttpRequest.newBuilder(target)
                    .timeout(responseTimeout)
                    .header("Accept", ACCEPT)
                    .header("X-GitHub-Api-Version", apiVersion)
                    .header("User-Agent", "hiresemble-github-ingestion/1")
                    .GET();
            if (etag != null && !etag.isBlank()) {
                request.header("If-None-Match", etag);
            }
            HttpResponse<InputStream> response = client.send(
                    request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(maxResponseBytes + 1);
            }
            if (body.length > maxResponseBytes) {
                throw new GitHubGatewayException(Kind.RESPONSE_LIMIT);
            }
            int status = response.statusCode();
            if (status == 304) {
                return new Response(status, body, response.headers().firstValue("etag").orElse(null),
                        response.headers().firstValue("link").orElse(null));
            }
            if (status >= 200 && status < 300) {
                return new Response(status, body, response.headers().firstValue("etag").orElse(null),
                        response.headers().firstValue("link").orElse(null));
            }
            if (status == 404) {
                throw new GitHubGatewayException(Kind.NOT_FOUND);
            }
            if (status == 403 || status == 429) {
                throw new GitHubGatewayException(
                        Kind.RATE_LIMITED, retryAfter(response.headers().firstValue("retry-after").orElse(null)));
            }
            if (status >= 500) {
                throw new GitHubGatewayException(Kind.UPSTREAM_5XX);
            }
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new GitHubGatewayException(Kind.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitHubGatewayException(Kind.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new GitHubGatewayException(Kind.UPSTREAM_5XX, exception);
        } finally {
            if (acquired) {
                concurrency.release();
            }
        }
    }

    private JsonNode json(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
    }

    private boolean hasNext(Response response) {
        return response.link() != null
                && response.link().toLowerCase(Locale.ROOT).contains("rel=\"next\"");
    }

    private String segment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,100}")) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        return value;
    }

    private String encodedPathValue(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String sha(String value) {
        if (value == null || !value.matches("[0-9a-f]{40}")) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        return value;
    }

    private Instant instantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
    }

    private String bounded(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Duration retryAfter(String header) {
        if (header == null) {
            return Duration.ofMinutes(1);
        }
        try {
            long seconds = Long.parseLong(header);
            return Duration.ofSeconds(Math.max(1, Math.min(seconds, 86_400)));
        } catch (NumberFormatException exception) {
            return Duration.ofMinutes(1);
        }
    }

    private void requireSameOrigin(URI target) {
        if (!baseUrl.getScheme().equalsIgnoreCase(target.getScheme())
                || !baseUrl.getHost().equalsIgnoreCase(target.getHost())
                || baseUrl.getPort() != target.getPort()
                || target.getUserInfo() != null
                || target.getFragment() != null) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
    }

    private static void requireAllowedBaseUrl(URI baseUrl, boolean allowLoopbackTestBaseUrl) {
        boolean production = baseUrl != null
                && "https".equalsIgnoreCase(baseUrl.getScheme())
                && "api.github.com".equalsIgnoreCase(baseUrl.getHost())
                && baseUrl.getPort() == -1;
        boolean loopback = allowLoopbackTestBaseUrl
                && baseUrl != null
                && ("http".equalsIgnoreCase(baseUrl.getScheme())
                        || "https".equalsIgnoreCase(baseUrl.getScheme()))
                && ("localhost".equalsIgnoreCase(baseUrl.getHost())
                        || "127.0.0.1".equals(baseUrl.getHost())
                        || "::1".equals(baseUrl.getHost()));
        if ((!production && !loopback)
                || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null
                || (baseUrl.getPath() != null && !baseUrl.getPath().isEmpty())) {
            throw new IllegalArgumentException("GitHub API base URL is not allowed");
        }
    }

    private record Response(int status, byte[] body, String etag, String link) {}
}
