package com.hiresemble.job.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class JobUrlCanonicalizer {

    private static final Set<String> TRACKING_KEYS = Set.of(
            "gclid", "dclid", "fbclid", "msclkid", "mc_cid", "mc_eid", "_ga");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
    private static final String UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final String PATH_ALLOWED = UNRESERVED + "!$&'()*+,;=:@/";
    private static final String QUERY_PART_ALLOWED = UNRESERVED + "!$'()*+,;=:@/?";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public String canonicalize(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank() || sourceUrl.length() > 2000
                || sourceUrl.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        try {
            URI uri = new URI(sourceUrl.trim());
            String scheme = lower(uri.getScheme());
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw invalid();
            }
            if (!uri.isAbsolute() || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()
                    || uri.getRawUserInfo() != null || uri.getRawAuthority().contains("@")) {
                throw invalid();
            }
            HostPort hostPort = hostPort(uri);
            String host = canonicalHost(hostPort.host());
            int port = hostPort.port();
            if (port == defaultPort(scheme)) {
                port = -1;
            }
            String path = normalizedPath(uri.getRawPath());
            String query = normalizedQuery(uri.getRawQuery());
            String authorityHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
            String canonical = scheme
                    + "://"
                    + authorityHost
                    + (port < 0 ? "" : ":" + port)
                    + path
                    + (query == null ? "" : "?" + query);
            if (canonical.length() > 2000) {
                throw invalid();
            }
            return canonical;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private HostPort hostPort(URI uri) {
        if (uri.getHost() != null) {
            return new HostPort(uri.getHost(), uri.getPort());
        }
        String authority = uri.getRawAuthority();
        if (authority.startsWith("[")) {
            int closing = authority.indexOf(']');
            if (closing < 0) {
                throw invalid();
            }
            String host = authority.substring(1, closing);
            String remainder = authority.substring(closing + 1);
            if (remainder.isEmpty()) {
                return new HostPort(host, -1);
            }
            if (!remainder.startsWith(":")) {
                throw invalid();
            }
            return new HostPort(host, parsePort(remainder.substring(1)));
        }
        int separator = authority.lastIndexOf(':');
        if (separator > 0 && authority.indexOf(':') == separator) {
            return new HostPort(
                    authority.substring(0, separator),
                    parsePort(authority.substring(separator + 1)));
        }
        return new HostPort(authority, -1);
    }

    private int parsePort(String value) {
        if (!PORT.matcher(value).matches()) {
            throw invalid();
        }
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65_535) {
            throw invalid();
        }
        return port;
    }

    private String canonicalHost(String value) {
        if (value == null || value.isBlank()) {
            throw invalid();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.indexOf(':') >= 0) {
            if (value.indexOf('%') >= 0) {
                throw invalid();
            }
            return value.toLowerCase(Locale.ROOT);
        }
        String ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES)
                .toLowerCase(Locale.ROOT);
        if (ascii.isBlank() || ascii.length() > 253) {
            throw invalid();
        }
        return ascii;
    }

    private String normalizedPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        String path = normalizeComponent(rawPath, PATH_ALLOWED);
        if (!path.startsWith("/")) {
            throw invalid();
        }
        path = collapseLiteralSlashes(path);
        boolean trailingSlash = path.endsWith("/")
                || path.endsWith("/.")
                || path.endsWith("/..");
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.addLast(segment);
        }
        String result = "/" + String.join("/", segments);
        if (trailingSlash && result.length() > 1) {
            result += "/";
        }
        return result;
    }

    private String normalizedQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        List<QueryPart> parts = new ArrayList<>();
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            String rawKey = separator < 0 ? pair : pair.substring(0, separator);
            String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
            String key = normalizeComponent(rawKey, QUERY_PART_ALLOWED);
            String value = normalizeComponent(rawValue, QUERY_PART_ALLOWED);
            String lowerKey = key.toLowerCase(Locale.ROOT);
            if (lowerKey.startsWith("utm_") || TRACKING_KEYS.contains(lowerKey)) {
                continue;
            }
            parts.add(new QueryPart(key, value, separator >= 0));
        }
        parts.sort(Comparator.comparing(QueryPart::key)
                .thenComparing(QueryPart::value)
                .thenComparing(QueryPart::hasEquals));
        if (parts.isEmpty()) {
            return null;
        }
        return parts.stream()
                .map(part -> part.hasEquals()
                        ? part.key() + "=" + part.value()
                        : part.key())
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String normalizeComponent(String raw, String allowedCharacters) {
        StringBuilder normalized = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); ) {
            char current = raw.charAt(index);
            if (current == '%') {
                if (index + 2 >= raw.length()) {
                    throw invalid();
                }
                int high = Character.digit(raw.charAt(index + 1), 16);
                int low = Character.digit(raw.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw invalid();
                }
                int decoded = (high << 4) | low;
                if (decoded < 128 && isUnreserved(decoded)) {
                    normalized.append((char) decoded);
                } else {
                    appendEscaped(normalized, decoded);
                }
                index += 3;
                continue;
            }
            int codePoint = raw.codePointAt(index);
            if (codePoint < 128 && allowedCharacters.indexOf(codePoint) >= 0) {
                normalized.append((char) codePoint);
            } else {
                byte[] bytes = new String(Character.toChars(codePoint))
                        .getBytes(StandardCharsets.UTF_8);
                for (byte value : bytes) {
                    appendEscaped(normalized, value & 0xff);
                }
            }
            index += Character.charCount(codePoint);
        }
        return normalized.toString();
    }

    private String collapseLiteralSlashes(String value) {
        StringBuilder collapsed = new StringBuilder(value.length());
        boolean previousSlash = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '/') {
                if (!previousSlash) {
                    collapsed.append(current);
                }
                previousSlash = true;
            } else {
                collapsed.append(current);
                previousSlash = false;
            }
        }
        return collapsed.toString();
    }

    private boolean isUnreserved(int value) {
        return UNRESERVED.indexOf(value) >= 0;
    }

    private void appendEscaped(StringBuilder target, int value) {
        target.append('%')
                .append(HEX[(value >>> 4) & 0xf])
                .append(HEX[value & 0xf]);
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private int defaultPort(String scheme) {
        return "http".equals(scheme) ? 80 : 443;
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private record HostPort(String host, int port) {}

    private record QueryPart(String key, String value, boolean hasEquals) {}
}
