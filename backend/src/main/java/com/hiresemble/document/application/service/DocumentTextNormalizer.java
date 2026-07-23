package com.hiresemble.document.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public final class DocumentTextNormalizer {

    public static final int MAX_CODE_POINTS = 500_000;
    public static final int MIN_NON_WHITESPACE_CODE_POINTS = 100;

    public NormalizedText normalize(String source) {
        if (source == null || source.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = Normalizer.normalize(
                source.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        int characterCount = normalized.codePointCount(0, normalized.length());
        if (characterCount > MAX_CODE_POINTS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int nonWhitespace = (int) normalized.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .count();
        return new NormalizedText(normalized, characterCount, nonWhitespace);
    }

    public record NormalizedText(String text, int characterCount, int nonWhitespaceCount) {
        public boolean needsManualText() {
            return nonWhitespaceCount < MIN_NON_WHITESPACE_CODE_POINTS;
        }
    }
}
