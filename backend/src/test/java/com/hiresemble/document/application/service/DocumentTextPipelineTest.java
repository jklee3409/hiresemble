package com.hiresemble.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.document.domain.model.DocumentRecords.ChunkDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentTextPipelineTest {

    private final DocumentTextNormalizer normalizer = new DocumentTextNormalizer();
    private final DocumentPrivacyMasker masker = new DocumentPrivacyMasker();
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void normalizationIsNfcCodePointBasedAndPreservesMeaningfulWhitespace() {
        var normalized = normalizer.normalize("e\u0301 first\r\nsecond\rthird 😀");

        assertThat(normalized.text()).isEqualTo("é first\nsecond\nthird 😀");
        assertThat(normalized.characterCount())
                .isEqualTo(normalized.text().codePointCount(0, normalized.text().length()));
        assertThat(normalized.needsManualText()).isTrue();
        assertThatThrownBy(() -> normalizer.normalize("invalid\0text"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void manualThresholdCountsOnlyNonWhitespaceUnicodeCodePoints() {
        assertThat(normalizer.normalize("가".repeat(99) + " \n\t").needsManualText()).isTrue();
        assertThat(normalizer.normalize("😀".repeat(100) + " \n\t").needsManualText()).isFalse();
    }

    @Test
    void maskingCoversPiiAndSecretsWithoutChangingOffsetsOrLineBreaks() {
        String raw = "mail jane@example.com\nphone 010-1234-5678\n"
                + "rrn 900101-1234567\n서울 강남구 테헤란로 123\n"
                + "api_key=sk-abcdefghijklmnopqrstuvwxyz123456\n"
                + "Bearer abcdefghijklmnopqrstuvwxyz";

        String masked = masker.mask(raw);

        assertThat(masked).hasSameSizeAs(raw);
        assertThat(masked.chars().filter(value -> value == '\n').count())
                .isEqualTo(raw.chars().filter(value -> value == '\n').count());
        assertThat(masked).doesNotContain("jane@example.com", "010-1234-5678",
                "900101-1234567", "테헤란로 123", "sk-abcdefghijklmnopqrstuvwxyz123456",
                "abcdefghijklmnopqrstuvwxyz");
        assertThat(masked).contains("mail ", "phone ", "rrn ");
    }

    @Test
    void deterministicChunksKeepContinuousIndexesPageRangesAndMaskedParity() {
        String pageOne = ("첫 번째 단락의 경력 설명입니다. ".repeat(70)) + "\n\n";
        String pageTwo = ("second@example.com 프로젝트 성과 42를 설명합니다. ".repeat(70));
        String raw = pageOne + pageTwo;
        String masked = masker.mask(raw);
        List<Integer> pageOffsets = List.of(0, pageOne.length());

        List<ChunkDraft> first = chunker.chunk(raw, masked, pageOffsets);
        List<ChunkDraft> second = chunker.chunk(raw, masked, pageOffsets);

        assertThat(first).isEqualTo(second).isNotEmpty();
        assertThat(first).extracting(ChunkDraft::chunkIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, first.size()).boxed().toList());
        assertThat(first).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.content()).hasSameSizeAs(chunk.maskedContent());
            assertThat(chunk.content().codePointCount(0, chunk.content().length()))
                    .isLessThanOrEqualTo(DocumentChunker.MAX_CODE_POINTS);
            assertThat(chunk.tokenCount()).isNotNegative();
            assertThat(chunk.metadata()).containsEntry("chunkPolicyVersion", DocumentChunker.POLICY_VERSION);
            assertThat(chunk.pageTo()).isGreaterThanOrEqualTo(chunk.pageFrom());
        });
        assertThat(first.getFirst().pageFrom()).isEqualTo(1);
        assertThat(first.getLast().pageTo()).isEqualTo(2);
        assertThat(first).allSatisfy(chunk ->
                assertThat(chunk.pageTo()).isEqualTo(chunk.pageFrom()));
    }

    @Test
    void chunkerRejectsRawAndMaskedPositionDrift() {
        assertThatThrownBy(() -> chunker.chunk("raw", "shorter", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
