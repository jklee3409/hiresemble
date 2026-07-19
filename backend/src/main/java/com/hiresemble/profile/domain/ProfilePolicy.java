package com.hiresemble.profile.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProfilePolicy {

    private ProfilePolicy() {}

    public static String requiredLabel(String value, int maximumLength) {
        String normalized = normalizeLabel(value, maximumLength);
        if (normalized == null) {
            throw invalid();
        }
        return normalized;
    }

    public static String optionalLabel(String value, int maximumLength) {
        return normalizeLabel(value, maximumLength);
    }

    public static String optionalBody(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maximumLength || value.indexOf('\0') >= 0) {
            throw invalid();
        }
        return value;
    }

    public static List<String> canonicalArray(List<String> values) {
        if (values == null || values.size() > 10) {
            throw invalid();
        }
        List<String> normalized = new ArrayList<>(values.size());
        Set<String> canonical = new HashSet<>();
        for (String value : values) {
            String item = requiredArrayItem(value);
            if (!canonical.add(item.toLowerCase(Locale.ROOT))) {
                throw invalid();
            }
            normalized.add(item);
        }
        return List.copyOf(normalized);
    }

    public static void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalid();
        }
    }

    public static void validateGpa(BigDecimal gpa, BigDecimal scale) {
        if ((gpa == null) != (scale == null)) {
            throw invalid();
        }
        if (gpa == null) {
            return;
        }
        if (gpa.compareTo(BigDecimal.ZERO) < 0
                || gpa.compareTo(BigDecimal.TEN) > 0
                || scale.compareTo(new BigDecimal("0.01")) < 0
                || scale.compareTo(BigDecimal.TEN) > 0
                || gpa.compareTo(scale) > 0) {
            throw invalid();
        }
    }

    public static void validateCareer(LocalDate start, LocalDate end, boolean current) {
        if (current && end != null) {
            throw invalid();
        }
        validateDateRange(start, end);
    }

    private static String normalizeLabel(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw invalid();
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isISOControl(character) || character == '/' || character == '\\') {
                throw invalid();
            }
        }
        return normalized;
    }

    private static String requiredArrayItem(String value) {
        if (value == null) {
            throw invalid();
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw invalid();
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw invalid();
            }
        }
        return normalized;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
