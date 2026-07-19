package com.hiresemble.document.application;

import com.hiresemble.document.domain.DocumentRecords.ChunkDraft;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class DocumentChunker {

    public static final String POLICY_VERSION = "paragraph-page-v1";
    static final int MAX_CODE_POINTS = 2_000;
    static final int OVERLAP_CODE_POINTS = 200;

    public List<ChunkDraft> chunk(String raw, String masked, List<Integer> pageOffsets) {
        if (raw == null || masked == null || raw.length() != masked.length()) {
            throw new IllegalArgumentException("raw and masked text must have positional parity");
        }
        List<ChunkDraft> chunks = new ArrayList<>();
        int cursor = 0;
        int index = 0;
        while (cursor < raw.length()) {
            while (cursor < raw.length() && Character.isWhitespace(raw.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= raw.length()) break;
            int limit = offsetByCodePoints(raw, cursor, MAX_CODE_POINTS);
            int end = boundary(raw, cursor, limit, pageOffsets);
            if (end <= cursor) end = limit;
            String content = raw.substring(cursor, end);
            String maskedContent = masked.substring(cursor, end);
            if (!content.isBlank()) {
                int pageFrom = pageAt(pageOffsets, cursor);
                int pageTo = pageAt(pageOffsets, Math.max(cursor, end - 1));
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("chunkPolicyVersion", POLICY_VERSION);
                metadata.put("startOffset", cursor);
                metadata.put("endOffset", end);
                chunks.add(new ChunkDraft(
                        index++, pageFrom == 0 ? null : pageFrom, pageTo == 0 ? null : pageTo,
                        content, maskedContent, approximateTokens(maskedContent), metadata));
            }
            if (end >= raw.length()) break;
            if (pageOffsets != null && pageOffsets.contains(end)) {
                cursor = end;
            } else {
                int overlapStart = offsetByCodePoints(raw, end, -OVERLAP_CODE_POINTS);
                cursor = Math.max(cursor + 1, overlapStart);
            }
        }
        return List.copyOf(chunks);
    }

    private int boundary(String value, int start, int limit, List<Integer> pageOffsets) {
        if (limit >= value.length()) return value.length();
        if (pageOffsets != null) {
            for (int pageOffset : pageOffsets) {
                if (pageOffset > start && pageOffset <= limit) return pageOffset;
            }
        }
        int paragraph = value.lastIndexOf("\n\n", limit);
        if (paragraph > start) return paragraph + 2;
        int line = value.lastIndexOf('\n', limit);
        if (line > start) return line + 1;
        int space = value.lastIndexOf(' ', limit);
        return space > start ? space + 1 : limit;
    }

    private int offsetByCodePoints(String value, int offset, int delta) {
        if (delta >= 0) {
            int available = value.codePointCount(offset, value.length());
            return value.offsetByCodePoints(offset, Math.min(delta, available));
        }
        int available = value.codePointCount(0, offset);
        return value.offsetByCodePoints(offset, -Math.min(-delta, available));
    }

    private int pageAt(List<Integer> pageOffsets, int offset) {
        if (pageOffsets == null || pageOffsets.isEmpty()) return 0;
        int page = 1;
        for (int index = 0; index < pageOffsets.size(); index++) {
            if (offset < pageOffsets.get(index)) return Math.max(1, index);
            page = index + 1;
        }
        return page;
    }

    private int approximateTokens(String value) {
        int codePoints = value.codePointCount(0, value.length());
        return (codePoints + 3) / 4;
    }
}
