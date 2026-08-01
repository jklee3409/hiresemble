package com.hiresemble.job.infrastructure;

import com.hiresemble.job.application.port.JobPageFetchException;
import com.hiresemble.job.application.port.JobPageFetchGateway.CharsetDetectionSource;
import com.hiresemble.job.application.port.JobPageFetchGateway.CharsetMetadata;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic HTML charset selection over the bounded response bytes. */
final class HtmlCharsetDecoder {

    private static final int META_SCAN_LIMIT = 16 * 1024;
    private static final Pattern META_CHARSET = Pattern.compile(
            "(?is)<meta\\b[^>]*?charset\\s*=\\s*[\\\"']?\\s*([^\\s\\\"'/>;]+)");
    private static final Pattern HTTP_EQUIV = Pattern.compile(
            "(?is)<meta\\b(?=[^>]*http-equiv\\s*=\\s*[\\\"']?content-type[\\\"']?)[^>]*"
                    + "content\\s*=\\s*[\\\"'][^\\\"']*charset\\s*=\\s*([^\\s\\\"';>]+)[^\\\"']*[\\\"']");
    private static final Map<String, String> KOREAN_ALIASES = Map.of(
            "euc-kr", "MS949",
            "ks_c_5601-1987", "MS949",
            "korean", "MS949",
            "windows-949", "MS949",
            "cp949", "MS949",
            "ms949", "MS949");

    DecodedHtml decode(byte[] raw, String contentType) {
        Declaration header = headerDeclaration(contentType);
        Bom bom = bom(raw);
        Declaration meta = metaDeclaration(raw);
        if (header != null) return decodeDeclared(raw, header, CharsetDetectionSource.HEADER, 0);
        if (bom != null) {
            return result(raw, bom.label(), bom.charset(), CharsetDetectionSource.BOM, bom.skip());
        }
        if (meta != null) return decodeDeclared(raw, meta, CharsetDetectionSource.META, 0);
        try {
            return result(raw, null, StandardCharsets.UTF_8, CharsetDetectionSource.DEFAULT, 0);
        } catch (JobPageFetchException failure) {
            DecodedHtml decoded = result(
                    raw, null, Charset.forName("MS949"), CharsetDetectionSource.FALLBACK, 0);
            if (!credibleKoreanFallback(decoded.html())) {
                throw failure("JOB_PAGE_CHARSET_UNDETECTABLE", false, null);
            }
            return decoded;
        }
    }

    private DecodedHtml decodeDeclared(
            byte[] raw, Declaration declaration, CharsetDetectionSource source, int skip) {
        Charset resolved;
        try {
            resolved = Charset.forName(normalizeAlias(declaration.label()));
        } catch (RuntimeException exception) {
            throw failure("JOB_PAGE_CHARSET_UNSUPPORTED", false, exception);
        }
        return result(raw, declaration.label(), resolved, source, skip);
    }

    private DecodedHtml result(
            byte[] raw, String declared, Charset charset, CharsetDetectionSource source, int skip) {
        try {
            CharBuffer decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw, skip, raw.length - skip));
            String html = decoded.toString();
            int replacements = (int) html.chars().filter(value -> value == 0xfffd).count();
            double ratio = html.isEmpty() ? 0d : (double) replacements / html.length();
            return new DecodedHtml(
                    html,
                    new CharsetMetadata(
                            declared,
                            charset.name(),
                            source,
                            raw.length,
                            html.length(),
                            replacements,
                            ratio,
                            sha256(raw)));
        } catch (CharacterCodingException exception) {
            throw failure("JOB_PAGE_CHARSET_DECODE_FAILED", false, exception);
        }
    }

    private Declaration headerDeclaration(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                String label = stripQuotes(trimmed.substring("charset=".length()).trim());
                if (!label.isBlank()) return new Declaration(label);
            }
        }
        return null;
    }

    private Declaration metaDeclaration(byte[] raw) {
        int length = Math.min(raw.length, META_SCAN_LIMIT);
        String prefix = new String(raw, 0, length, StandardCharsets.ISO_8859_1);
        Matcher direct = META_CHARSET.matcher(prefix);
        if (direct.find()) return new Declaration(direct.group(1));
        Matcher legacy = HTTP_EQUIV.matcher(prefix);
        return legacy.find() ? new Declaration(legacy.group(1)) : null;
    }

    private Bom bom(byte[] raw) {
        if (starts(raw, 0xef, 0xbb, 0xbf)) return new Bom("UTF-8", StandardCharsets.UTF_8, 3);
        if (starts(raw, 0xfe, 0xff)) return new Bom("UTF-16BE", StandardCharsets.UTF_16BE, 2);
        if (starts(raw, 0xff, 0xfe)) return new Bom("UTF-16LE", StandardCharsets.UTF_16LE, 2);
        return null;
    }

    private boolean starts(byte[] raw, int... expected) {
        if (raw.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((raw[index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private boolean credibleKoreanFallback(String value) {
        long hangul = value.chars().filter(c -> c >= 0xac00 && c <= 0xd7a3).count();
        long controls = value.chars().filter(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t').count();
        return hangul >= 4 && controls == 0;
    }

    private String normalizeAlias(String label) {
        String normalized = stripQuotes(label).strip().toLowerCase(Locale.ROOT);
        return KOREAN_ALIASES.getOrDefault(normalized, normalized);
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private JobPageFetchException failure(String code, boolean retryable, Throwable cause) {
        return new JobPageFetchException(code, retryable, cause);
    }

    record DecodedHtml(String html, CharsetMetadata metadata) {}
    private record Declaration(String label) {}
    private record Bom(String label, Charset charset, int skip) {}
}
