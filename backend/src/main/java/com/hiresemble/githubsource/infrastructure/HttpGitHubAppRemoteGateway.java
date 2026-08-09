package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.githubsource.application.GitHubAppRemoteGateway;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubGatewayException.Kind;
import com.hiresemble.githubsource.application.GitHubGatewayModels.RepositoryMetadata;
import com.hiresemble.githubsource.domain.GitHubAccountType;
import com.hiresemble.githubsource.domain.GitHubAppConnectionRecords.VerifiedInstallation;
import com.hiresemble.githubsource.domain.GitHubRepositorySelectionKind;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public final class HttpGitHubAppRemoteGateway implements GitHubAppRemoteGateway {

    private static final URI GITHUB = URI.create("https://github.com");
    private static final URI API = URI.create("https://api.github.com");
    private static final String ACCEPT = "application/vnd.github+json";

    private final GitHubProperties properties;
    private final GitHubAppJwtSigner jwtSigner;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final URI githubBase;
    private final URI apiBase;

    @Autowired
    public HttpGitHubAppRemoteGateway(
            GitHubProperties properties, GitHubAppJwtSigner jwtSigner, ObjectMapper objectMapper) {
        this(properties, jwtSigner, objectMapper, GITHUB, API, false);
    }

    HttpGitHubAppRemoteGateway(
            GitHubProperties properties,
            GitHubAppJwtSigner jwtSigner,
            ObjectMapper objectMapper,
            URI githubBase,
            URI apiBase,
            boolean allowLoopbackTestBaseUrl) {
        requireBase(githubBase, "github.com", allowLoopbackTestBaseUrl);
        requireBase(apiBase, "api.github.com", allowLoopbackTestBaseUrl);
        this.properties = properties;
        this.jwtSigner = jwtSigner;
        this.objectMapper = objectMapper;
        this.githubBase = githubBase;
        this.apiBase = apiBase;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public URI installationUrl(String state) {
        return githubBase.resolve("/apps/"
                + properties.getApp().getSlug()
                + "/installations/new?state=" + query(state));
    }

    @Override
    public URI oauthAuthorizationUrl(String state, String codeChallenge) {
        String callback = properties.getApp().getBackendBaseUrl()
                .resolve("/api/v1/github-app-connections/oauth/callback")
                .toString();
        return githubBase.resolve("/login/oauth/authorize?client_id="
                + query(properties.getApp().getClientId())
                + "&redirect_uri=" + query(callback)
                + "&state=" + query(state)
                + "&code_challenge=" + query(codeChallenge)
                + "&code_challenge_method=S256");
    }

    @Override
    public UserAuthorization exchangeUserCode(String code, String codeVerifier) {
        JsonNode body = postJson(
                githubBase.resolve("/login/oauth/access_token"),
                null,
                Map.of(
                        "client_id", properties.getApp().getClientId(),
                        "client_secret", properties.getApp().getClientSecret(),
                        "code", code,
                        "redirect_uri", properties.getApp().getBackendBaseUrl()
                                .resolve("/api/v1/github-app-connections/oauth/callback")
                                .toString(),
                        "code_verifier", codeVerifier));
        String token = body.path("access_token").asText();
        if (token.isBlank() || body.hasNonNull("error")) {
            throw new GitHubGatewayException(Kind.AUTHENTICATION);
        }
        return new UserAuthorization(token);
    }

    @Override
    public VerifiedInstallation verifyUserInstallation(
            String userAccessToken, long installationId) {
        List<RepositoryMetadata> repositories = repositories(
                "/user/installations/" + positive(installationId)
                        + "/repositories?per_page=100&page=",
                userAccessToken);
        InstallationDetails details = details(installationId);
        return details.verified(repositories);
    }

    @Override
    public VerifiedInstallation inspectInstallation(long installationId) {
        InstallationDetails details = details(installationId);
        if (details.suspended()) return details.verified(List.of());
        InstallationToken token = mintInstallationToken(installationId, null, false);
        List<RepositoryMetadata> repositories = repositories(
                "/installation/repositories?per_page=100&page=", token.value());
        return details.verified(repositories);
    }

    @Override
    public InstallationToken mintInstallationToken(
            long installationId, Long externalRepositoryId, boolean contentsRead) {
        if (contentsRead && externalRepositoryId == null) {
            throw new GitHubGatewayException(Kind.PERMISSION);
        }
        var body = objectMapper.createObjectNode();
        if (externalRepositoryId != null) {
            body.putArray("repository_ids").add(positive(externalRepositoryId));
        }
        var permissions = body.putObject("permissions");
        if (contentsRead) permissions.put("contents", "read");
        JsonNode response = postJson(
                apiBase.resolve("/app/installations/" + positive(installationId) + "/access_tokens"),
                jwtSigner.create(),
                body);
        String value = response.path("token").asText();
        Instant expiresAt = instant(response.path("expires_at").asText(null));
        if (value.isBlank() || expiresAt == null) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        JsonNode granted = response.path("permissions");
        if (!"read".equals(granted.path("metadata").asText())
                || (contentsRead && !"read".equals(granted.path("contents").asText()))) {
            throw new GitHubGatewayException(Kind.PERMISSION);
        }
        return new InstallationToken(value, expiresAt);
    }

    @Override
    public void uninstall(long installationId) {
        Response response = send(HttpRequest.newBuilder(
                        apiBase.resolve("/app/installations/" + positive(installationId)))
                .timeout(properties.getResponseTimeout())
                .header("Accept", ACCEPT)
                .header("Authorization", "Bearer " + jwtSigner.create())
                .header("X-GitHub-Api-Version", properties.getApiVersion())
                .header("User-Agent", "hiresemble-github-app/1")
                .DELETE()
                .build(), true);
        if (response.status() == 404 || response.status() == 204) return;
        requireSuccess(response);
    }

    private InstallationDetails details(long installationId) {
        JsonNode value = getJson(
                apiBase.resolve("/app/installations/" + positive(installationId)), jwtSigner.create());
        long id = value.path("id").asLong(-1);
        JsonNode account = value.path("account");
        long accountId = account.path("id").asLong(-1);
        String login = account.path("login").asText();
        String type = account.path("type").asText();
        String selection = value.path("repository_selection").asText();
        JsonNode permissions = value.path("permissions");
        if (id != installationId || accountId <= 0 || login.isBlank() || login.length() > 100
                || !("User".equalsIgnoreCase(type) || "Organization".equalsIgnoreCase(type))
                || !("all".equals(selection) || "selected".equals(selection))) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        if (!permissions.isObject()
                || permissions.size() != 2
                || !"read".equals(permissions.path("metadata").asText())
                || !"read".equals(permissions.path("contents").asText())) {
            throw new GitHubGatewayException(Kind.PERMISSION);
        }
        boolean suspended = value.has("suspended_at") && !value.path("suspended_at").isNull();
        return new InstallationDetails(
                id,
                accountId,
                login,
                "Organization".equalsIgnoreCase(type)
                        ? GitHubAccountType.ORGANIZATION : GitHubAccountType.USER,
                "all".equals(selection)
                        ? GitHubRepositorySelectionKind.ALL : GitHubRepositorySelectionKind.SELECTED,
                suspended);
    }

    private List<RepositoryMetadata> repositories(String pathPrefix, String token) {
        List<RepositoryMetadata> result = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            JsonNode value = getJson(apiBase.resolve(pathPrefix + page), token);
            JsonNode repositories = value.path("repositories");
            if (!repositories.isArray()) throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
            for (JsonNode repository : repositories) {
                if (result.size() >= properties.getMaxDiscoveredRepositories()) break;
                result.add(repository(repository));
            }
            if (repositories.size() < 100 || result.size() >= properties.getMaxDiscoveredRepositories()) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private RepositoryMetadata repository(JsonNode value) {
        long externalId = value.path("id").asLong(-1);
        String nodeId = value.path("node_id").asText();
        String owner = value.path("owner").path("login").asText();
        String name = value.path("name").asText();
        String defaultBranch = value.path("default_branch").asText();
        if (externalId <= 0 || nodeId.isBlank() || nodeId.length() > 100
                || owner.isBlank() || owner.length() > 100
                || name.isBlank() || name.length() > 100
                || defaultBranch.isBlank() || defaultBranch.length() > 255) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        List<String> topics = new ArrayList<>();
        if (value.path("topics").isArray()) {
            value.path("topics").forEach(topic -> {
                String text = topic.asText();
                if (!text.isBlank() && text.length() <= 100 && topics.size() < 100) {
                    topics.add(text);
                }
            });
        }
        String description = value.path("description").isNull()
                ? null : bounded(value.path("description").asText(), 500);
        return new RepositoryMetadata(
                externalId,
                nodeId,
                owner,
                name,
                "https://github.com/" + owner + "/" + name,
                defaultBranch,
                value.path("private").asBoolean(true),
                value.path("fork").asBoolean(false),
                value.path("archived").asBoolean(false),
                description,
                topics,
                instant(value.path("pushed_at").asText(null)),
                null);
    }

    private JsonNode getJson(URI uri, String token) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getResponseTimeout())
                .header("Accept", ACCEPT)
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", properties.getApiVersion())
                .header("User-Agent", "hiresemble-github-app/1")
                .GET()
                .build();
        Response response = send(request, false);
        requireSuccess(response);
        return json(response.body());
    }

    private JsonNode postJson(URI uri, String token, Object body) {
        byte[] encoded;
        try {
            encoded = objectMapper.writeValueAsBytes(body);
        } catch (Exception exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.getResponseTimeout())
                .header("Accept", ACCEPT)
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", properties.getApiVersion())
                .header("User-Agent", "hiresemble-github-app/1")
                .POST(HttpRequest.BodyPublishers.ofByteArray(encoded));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        Response response = send(builder.build(), false);
        requireSuccess(response);
        return json(response.body());
    }

    private Response send(HttpRequest request, boolean allowNotFound) {
        requireAllowed(request.uri());
        try {
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(properties.getMaxResponseBytes() + 1);
            }
            if (body.length > properties.getMaxResponseBytes()) {
                throw new GitHubGatewayException(Kind.RESPONSE_LIMIT);
            }
            if (allowNotFound && response.statusCode() == 404) {
                return new Response(404, body);
            }
            return new Response(response.statusCode(), body);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new GitHubGatewayException(Kind.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitHubGatewayException(Kind.TIMEOUT, exception);
        } catch (IOException exception) {
            throw new GitHubGatewayException(Kind.UPSTREAM_5XX, exception);
        }
    }

    private void requireSuccess(Response response) {
        if (response.status() >= 200 && response.status() < 300) return;
        if (response.status() == 401) throw new GitHubGatewayException(Kind.AUTHENTICATION);
        if (response.status() == 403) throw new GitHubGatewayException(Kind.PERMISSION);
        if (response.status() == 404) throw new GitHubGatewayException(Kind.NOT_FOUND);
        if (response.status() == 429) throw new GitHubGatewayException(Kind.RATE_LIMITED);
        if (response.status() >= 500) throw new GitHubGatewayException(Kind.UPSTREAM_5XX);
        throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
    }

    private JsonNode json(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
    }

    private void requireAllowed(URI uri) {
        boolean api = sameOrigin(uri, apiBase);
        boolean oauth = sameOrigin(uri, githubBase)
                && uri.getPath().startsWith("/login/oauth/");
        if ((!api && !oauth) || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
    }

    private boolean sameOrigin(URI value, URI base) {
        return value.getScheme().equalsIgnoreCase(base.getScheme())
                && value.getHost().equalsIgnoreCase(base.getHost())
                && value.getPort() == base.getPort();
    }

    private static void requireBase(URI value, String productionHost, boolean allowLoopback) {
        if (value == null || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null
                || (value.getPath() != null && !value.getPath().isEmpty())) {
            throw new IllegalArgumentException("GitHub App base URL is invalid");
        }
        boolean production = "https".equalsIgnoreCase(value.getScheme())
                && productionHost.equalsIgnoreCase(value.getHost())
                && value.getPort() == -1;
        boolean loopback = allowLoopback
                && "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(value.getHost())
                        || "127.0.0.1".equals(value.getHost()))
                && value.getPort() > 0;
        if (!production && !loopback) {
            throw new IllegalArgumentException("GitHub App base URL is invalid");
        }
    }

    private long positive(long value) {
        if (value <= 0) throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        return value;
    }

    private String query(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE);
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new GitHubGatewayException(Kind.INVALID_RESPONSE, exception);
        }
    }

    private String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Response(int status, byte[] body) {}

    private record InstallationDetails(
            long installationId,
            long targetAccountId,
            String targetAccountLogin,
            GitHubAccountType targetAccountType,
            GitHubRepositorySelectionKind selection,
            boolean suspended) {
        private VerifiedInstallation verified(List<RepositoryMetadata> repositories) {
            return new VerifiedInstallation(
                    installationId,
                    targetAccountId,
                    targetAccountLogin,
                    targetAccountType,
                    selection,
                    suspended,
                    repositories);
        }
    }
}
