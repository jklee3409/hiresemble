package com.hiresemble.agentrun.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record SafeError(String code, String message) {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");

    public SafeError {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("safe error code is invalid");
        }
        if (message == null || message.isBlank() || message.length() > 500
                || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0
                || containsInternalMarker(message)) {
            throw new IllegalArgumentException("safe error message is invalid");
        }
    }

    private static boolean containsInternalMarker(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("exception")
                || normalized.contains("stack trace")
                || normalized.contains("jdbc:")
                || normalized.contains("select *")
                || normalized.contains("api key")
                || normalized.contains("bearer ")
                || normalized.contains("password=");
    }
}
