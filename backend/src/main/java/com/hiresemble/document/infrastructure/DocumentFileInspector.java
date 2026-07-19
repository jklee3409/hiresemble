package com.hiresemble.document.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Map;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public final class DocumentFileInspector {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "pdf", DocumentParser.PDF,
            "docx", DocumentParser.DOCX,
            "txt", DocumentParser.TXT);

    private final Tika tika = new Tika();
    private final DocumentParser parser;

    public DocumentFileInspector(DocumentParser parser) {
        this.parser = parser;
    }

    public InspectedFile inspect(String suppliedFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        String filename = safeFilename(suppliedFilename);
        String extension = extension(filename);
        String expectedMime = MIME_BY_EXTENSION.get(extension);
        if (expectedMime == null || extension.equals("docm")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        String detected;
        try {
            detected = tika.detect(content, filename).toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, exception);
        }
        if (!matches(expectedMime, detected)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        try {
            parser.parse(content, expectedMime);
        } catch (DocumentParsingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new InspectedFile(filename, extension, expectedMime);
    }

    private boolean matches(String expected, String detected) {
        if (expected.equals(detected)) return true;
        return expected.equals(DocumentParser.TXT)
                && (detected.equals("application/octet-stream") || detected.startsWith("text/plain"));
    }

    private String safeFilename(String supplied) {
        if (supplied == null || supplied.isBlank() || supplied.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = supplied.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String filename = (separator < 0 ? normalized : normalized.substring(separator + 1)).trim();
        if (filename.isEmpty() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return filename;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 1 || dot == filename.length() - 1
                ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record InspectedFile(String filename, String extension, String mimeType) {}
}
