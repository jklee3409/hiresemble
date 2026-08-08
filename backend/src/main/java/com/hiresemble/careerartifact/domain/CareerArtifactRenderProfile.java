package com.hiresemble.careerartifact.domain;

import java.net.URI;
import java.util.List;

public record CareerArtifactRenderProfile(
        String displayName,
        String email,
        String phone,
        List<Link> links,
        boolean includeContact) {

    public CareerArtifactRenderProfile {
        displayName = required(displayName, 100);
        email = optional(email, 320);
        phone = optional(phone, 30);
        links = links == null ? List.of() : links.stream().map(Link::normalized).toList();
        if (links.size() > 5) throw new IllegalArgumentException("too many render links");
    }

    public record Link(String label, String url) {
        private Link normalized() {
            String safeLabel = required(label, 50);
            String safeUrl = required(url, 500);
            URI uri;
            try {
                uri = URI.create(safeUrl);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("render profile link is invalid", exception);
            }
            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("render profile link is invalid");
            }
            return new Link(safeLabel, uri.normalize().toASCIIString());
        }
    }

    /** Stable, length-prefixed material used only for the private request digest. */
    public String canonicalDigestMaterial() {
        StringBuilder value = new StringBuilder();
        append(value, displayName);
        append(value, email);
        append(value, phone);
        value.append(includeContact ? "1;" : "0;");
        value.append(links.size()).append(';');
        links.forEach(link -> {
            append(value, link.label());
            append(value, link.url());
        });
        return value.toString();
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:;");
        } else {
            target.append(value.length()).append(':').append(value).append(';');
        }
    }

    private static String required(String value, int max) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isEmpty() || normalized.length() > max
                || normalized.chars().anyMatch(valueCode -> valueCode < 0x20 || valueCode == 0x7f)) {
            throw new IllegalArgumentException("render profile text is invalid");
        }
        return normalized;
    }

    private static String optional(String value, int max) {
        return value == null || value.isBlank() ? null : required(value, max);
    }
}
