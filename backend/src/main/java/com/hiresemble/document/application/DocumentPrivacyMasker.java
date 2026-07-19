package com.hiresemble.document.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class DocumentPrivacyMasker {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
            Pattern.compile("(?<!\\d)(?:\\+?82[- .]?)?0?1[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)"),
            Pattern.compile("(?i)\\b(?:api[_-]?key|secret|password|passwd|credential)\\s*[:=]\\s*[^\\s,;]{6,}"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{8,}={0,2}"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
            Pattern.compile("\\b(?:sk|pk|rk)[-_][A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)[^\\n,]{0,50}(?:로|길|동|읍|면)\\s*\\d{1,5}(?:-\\d{1,5})?(?:\\s*\\d{1,4}호)?"));

    public String mask(String source) {
        List<Range> ranges = new ArrayList<>();
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) ranges.add(new Range(matcher.start(), matcher.end()));
        }
        if (ranges.isEmpty()) return source;
        ranges.sort(Comparator.comparingInt(Range::start).thenComparingInt(Range::end));
        List<Range> merged = new ArrayList<>();
        for (Range range : ranges) {
            if (merged.isEmpty() || range.start() > merged.getLast().end()) {
                merged.add(range);
            } else {
                Range previous = merged.removeLast();
                merged.add(new Range(previous.start(), Math.max(previous.end(), range.end())));
            }
        }
        char[] masked = source.toCharArray();
        for (Range range : merged) {
            for (int index = range.start(); index < range.end(); index++) {
                if (masked[index] != '\n' && masked[index] != '\r') masked[index] = '*';
            }
        }
        return new String(masked);
    }

    private record Range(int start, int end) {}
}
