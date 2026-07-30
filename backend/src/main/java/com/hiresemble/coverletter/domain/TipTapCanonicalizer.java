package com.hiresemble.coverletter.domain;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapMarkDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class TipTapCanonicalizer {

    private static final Set<String> CONTAINERS =
            Set.of("paragraph", "bulletList", "orderedList", "listItem");
    private static final Set<String> LEAFS = Set.of("text", "hardBreak");
    private static final Set<String> MARKS = Set.of("bold", "italic");

    public CanonicalContent canonicalize(TipTapDocumentDto document) {
        if (document == null
                || !"doc".equals(document.type())
                || document.content() == null
                || document.content().size() > 1000) {
            throw invalid();
        }
        StringBuilder plain = new StringBuilder();
        List<TipTapNodeDto> content =
                canonicalizeChildren(document.content(), plain, true, 0);
        String value = plain.toString();
        int count = value.codePointCount(0, value.length());
        if (count > 20000) {
            throw invalid();
        }
        return new CanonicalContent(
                new TipTapDocumentDto("doc", content), value, count);
    }

    private List<TipTapNodeDto> canonicalizeChildren(
            List<TipTapNodeDto> nodes, StringBuilder output, boolean blockBoundary, int depth) {
        if (nodes == null || nodes.size() > 1000 || depth > 100) {
            throw invalid();
        }
        List<TipTapNodeDto> canonical = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            TipTapNodeDto node = nodes.get(index);
            canonical.add(canonicalize(node, output, depth + 1));
            if (blockBoundary
                    && index + 1 < nodes.size()
                    && isBlock(node)
                    && !endsWithNewline(output)) {
                output.append('\n');
            }
        }
        return List.copyOf(canonical);
    }

    private TipTapNodeDto canonicalize(
            TipTapNodeDto node, StringBuilder output, int depth) {
        if (node == null || node.type() == null || depth > 100) {
            throw invalid();
        }
        if ("text".equals(node.type())) {
            requireTextShape(node);
            String text = normalizeText(node.text());
            if (text.isEmpty()) {
                throw invalid();
            }
            output.append(text);
            List<TipTapMarkDto> marks = node.marks().stream()
                    .sorted(Comparator.comparing(TipTapMarkDto::type))
                    .map(mark -> new TipTapMarkDto(mark.type()))
                    .toList();
            return new TipTapNodeDto("text", text, marks, List.of());
        }
        if ("hardBreak".equals(node.type())) {
            if (node.text() != null || nonEmpty(node.marks()) || nonEmpty(node.content())) {
                throw invalid();
            }
            output.append('\n');
            return new TipTapNodeDto("hardBreak", null, List.of(), List.of());
        }
        if (!CONTAINERS.contains(node.type()) || node.text() != null || nonEmpty(node.marks())) {
            throw invalid();
        }
        List<TipTapNodeDto> content = node.content() == null ? List.of() : node.content();
        if (content.size() > 1000) {
            throw invalid();
        }
        if (("bulletList".equals(node.type()) || "orderedList".equals(node.type()))
                && content.stream().anyMatch(child -> child == null || !"listItem".equals(child.type()))) {
            throw invalid();
        }
        boolean boundary = "bulletList".equals(node.type())
                || "orderedList".equals(node.type())
                || "listItem".equals(node.type());
        List<TipTapNodeDto> canonical =
                canonicalizeChildren(content, output, boundary, depth);
        return new TipTapNodeDto(node.type(), null, List.of(), canonical);
    }

    private void requireTextShape(TipTapNodeDto node) {
        if (node.text() == null
                || node.text().isEmpty()
                || node.text().codePointCount(0, node.text().length()) > 20000
                || nonEmpty(node.content())
                || (node.marks() != null && node.marks().size() > 2)) {
            throw invalid();
        }
        Set<String> unique = new HashSet<>();
        for (TipTapMarkDto mark : node.marks() == null ? List.<TipTapMarkDto>of() : node.marks()) {
            if (mark == null || !MARKS.contains(mark.type()) || !unique.add(mark.type())) {
                throw invalid();
            }
        }
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(
                value.replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' '),
                Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> codePoint != 0x200B
                        && codePoint != 0x200C
                        && codePoint != 0x200D
                        && codePoint != 0x2060
                        && codePoint != 0xFEFF)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private boolean isBlock(TipTapNodeDto node) {
        return node != null && CONTAINERS.contains(node.type());
    }

    private boolean nonEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private boolean endsWithNewline(StringBuilder value) {
        return !value.isEmpty() && value.charAt(value.length() - 1) == '\n';
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    public record CanonicalContent(
            TipTapDocumentDto document, String plainText, int characterCount) {}
}
