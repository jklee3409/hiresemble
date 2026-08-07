package com.hiresemble.githubsource.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

public record GitHubUrl(
        String originalUrl,
        String canonicalUrl,
        GitHubSourceKind sourceKind,
        String ownerLogin,
        String repositoryName) {

    private static final Pattern OWNER = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?");
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    public static GitHubUrl parse(String value) {
        if (value == null || value.isBlank() || value.length() > 500
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        String input = value.trim();
        if (!input.equals(value) || input.indexOf('%') >= 0) {
            throw invalid();
        }
        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException exception) {
            throw invalid(exception);
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !("github.com".equals(host) || "www.github.com".equals(host))
                || uri.getUserInfo() != null
                || uri.getPort() != -1
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalid();
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.length() < 2 || rawPath.endsWith("/")
                || rawPath.contains("//")) {
            throw invalid();
        }
        String[] segments = rawPath.substring(1).split("/", -1);
        if (segments.length < 1 || segments.length > 2 || !OWNER.matcher(segments[0]).matches()) {
            throw invalid();
        }
        String repository = null;
        if (segments.length == 2) {
            repository = segments[1];
            if (repository.endsWith(".git")) {
                repository = repository.substring(0, repository.length() - 4);
            }
            if (!REPOSITORY.matcher(repository).matches()
                    || ".".equals(repository)
                    || "..".equals(repository)) {
                throw invalid();
            }
        }
        String canonical = "https://github.com/" + segments[0]
                + (repository == null ? "" : "/" + repository);
        return new GitHubUrl(
                input,
                canonical,
                repository == null ? GitHubSourceKind.ACCOUNT : GitHubSourceKind.REPOSITORY,
                segments[0],
                repository);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.GITHUB_URL_INVALID);
    }

    private static BusinessException invalid(Exception cause) {
        return new BusinessException(ErrorCode.GITHUB_URL_INVALID, cause);
    }
}
