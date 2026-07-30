package com.hiresemble.coverletter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.coverletter.domain.TipTapCanonicalizer.CanonicalContent;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapDocumentDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapMarkDto;
import com.hiresemble.coverletter.domain.TipTapContent.TipTapNodeDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class TipTapCanonicalizerTest {

    private final TipTapCanonicalizer canonicalizer = new TipTapCanonicalizer();

    @Test
    void canonicalizesStoredTreeAndPlainTextWithUnicodeCodePointCount() {
        TipTapNodeDto text = new TipTapNodeDto(
                "text",
                "A\r\nCafe\u0301\u00A0\u200B끝",
                List.of(new TipTapMarkDto("italic"), new TipTapMarkDto("bold")),
                List.of());
        TipTapDocumentDto input = new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(
                                text,
                                new TipTapNodeDto(
                                        "hardBreak", null, List.of(), List.of())))));

        CanonicalContent canonical = canonicalizer.canonicalize(input);

        assertThat(canonical.plainText()).isEqualTo("A\nCafé 끝\n");
        assertThat(canonical.characterCount()).isEqualTo(9);
        TipTapNodeDto canonicalText =
                canonical.document().content().getFirst().content().getFirst();
        assertThat(canonicalText.text()).isEqualTo("A\nCafé 끝");
        assertThat(canonicalText.marks())
                .extracting(TipTapMarkDto::type)
                .containsExactly("bold", "italic");
        assertThat(canonical.document().content().getFirst().content().get(1).type())
                .isEqualTo("hardBreak");
    }

    @Test
    void preservesStructuralAndExplicitTrailingNewlines() {
        TipTapDocumentDto input = new TipTapDocumentDto(
                "doc",
                List.of(
                        paragraph("첫 문단"),
                        new TipTapNodeDto(
                                "paragraph",
                                null,
                                List.of(),
                                List.of(
                                        new TipTapNodeDto(
                                                "text", "둘째", List.of(), List.of()),
                                        new TipTapNodeDto(
                                                "hardBreak", null, List.of(), List.of())))));

        assertThat(canonicalizer.canonicalize(input).plainText())
                .isEqualTo("첫 문단\n둘째\n");
    }

    @Test
    void rejectsUnsupportedNodesMarksAndEmptyCanonicalText() {
        assertInvalid(new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto("image", null, List.of(), List.of()))));
        assertInvalid(new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(new TipTapNodeDto(
                                "text",
                                "content",
                                List.of(new TipTapMarkDto("link")),
                                List.of()))))));
        assertInvalid(new TipTapDocumentDto(
                "doc",
                List.of(new TipTapNodeDto(
                        "paragraph",
                        null,
                        List.of(),
                        List.of(new TipTapNodeDto(
                                "text", "\u200B", List.of(), List.of()))))));
    }

    private TipTapNodeDto paragraph(String text) {
        return new TipTapNodeDto(
                "paragraph",
                null,
                List.of(),
                List.of(new TipTapNodeDto("text", text, List.of(), List.of())));
    }

    private void assertInvalid(TipTapDocumentDto document) {
        assertThatThrownBy(() -> canonicalizer.canonicalize(document))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
