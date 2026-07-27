package com.hiresemble.job.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.job.domain.JobRecords.StatusChange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class JobPolicy {

    private JobPolicy() {}

    public static StatusChange transition(
            JobStatus current,
            JobStatus target,
            Instant existingSubmittedAt,
            Instant now,
            ClosedReason closeReason) {
        if (current == null || target == null || now == null || !allowed(current, target)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        }
        Instant submittedAt = existingSubmittedAt;
        if (target == JobStatus.SUBMITTED && submittedAt == null) {
            submittedAt = now;
        }
        if (target == JobStatus.CLOSED) {
            if (closeReason == null) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
            }
            return new StatusChange(current, target, submittedAt, now, closeReason);
        }
        return new StatusChange(current, target, submittedAt, null, null);
    }

    public static boolean allowed(JobStatus current, JobStatus target) {
        return switch (current) {
            case IN_PROGRESS -> target == JobStatus.SUBMITTED || target == JobStatus.CLOSED;
            case SUBMITTED -> target == JobStatus.CLOSED;
            case CLOSED -> target == JobStatus.IN_PROGRESS || target == JobStatus.SUBMITTED;
        };
    }

    public static String optionalName(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > maxLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    public static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isBlank() || normalized.length() > 200_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    public static boolean hasUsableText(String value) {
        return value != null && !value.isBlank();
    }

    public static String contentHash(String descriptionText) {
        if (!hasUsableText(descriptionText)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(descriptionText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
