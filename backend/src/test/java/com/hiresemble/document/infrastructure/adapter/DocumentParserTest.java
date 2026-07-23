package com.hiresemble.document.infrastructure.adapter;

import com.hiresemble.document.infrastructure.config.DocumentParserProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DocumentParserTest {

    private final DocumentParserProperties properties = new DocumentParserProperties();
    private final DocumentParser parser = new DocumentParser(properties);
    private final DocumentFileInspector inspector = new DocumentFileInspector(parser);

    @AfterEach
    void closeParser() {
        parser.close();
    }

    @Test
    void parsesPdfByPageAndPreservesPageOrder() throws Exception {
        byte[] pdf = pdf("first page", "second page");

        var parsed = parser.parse(pdf, DocumentParser.PDF);

        assertThat(parsed.pageCount()).isEqualTo(2);
        assertThat(parsed.pages()).extracting(page -> page.pageNumber()).containsExactly(1, 2);
        assertThat(parsed.pages().getFirst().text()).contains("first page");
        assertThat(parsed.pages().getLast().text()).contains("second page");
        assertThat(parsed.parserName()).isEqualTo("PDFBox");
        assertThat(inspector.inspect("resume.pdf", pdf).mimeType()).isEqualTo(DocumentParser.PDF);
    }

    @Test
    void parsesDocxParagraphsAndTables() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("career paragraph");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("role");
            table.getRow(0).getCell(1).setText("backend");
            document.write(output);
            docx = output.toByteArray();
        }

        var parsed = parser.parse(docx, DocumentParser.DOCX);

        assertThat(parsed.text()).contains("career paragraph", "role\tbackend");
        assertThat(parsed.parserName()).isEqualTo("Apache POI");
        assertThat(inspector.inspect("career.docx", docx).mimeType()).isEqualTo(DocumentParser.DOCX);
    }

    @Test
    void txtUsesStrictUtf8AndStripsBom() {
        byte[] body = "경력 내용".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);

        assertThat(parser.parse(withBom, DocumentParser.TXT).text()).isEqualTo("경력 내용");
        assertThatThrownBy(() -> parser.parse(new byte[] {(byte) 0xC3, 0x28}, DocumentParser.TXT))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_TXT_ENCODING_INVALID");
        assertThatThrownBy(() -> parser.parse(
                        "a".repeat(500_001).getBytes(StandardCharsets.UTF_8), DocumentParser.TXT))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_TEXT_LIMIT");
    }

    @Test
    void rejectsMacroActiveContentCorruptionAndMimeDisguise() throws Exception {
        byte[] macro = zip(
                new String[] {"word/document.xml", "word/vbaProject.bin"},
                new byte[][] {"<document/>".getBytes(StandardCharsets.UTF_8), {1, 2, 3}});
        byte[] encrypted = zip(
                new String[] {"word/document.xml", "EncryptionInfo", "EncryptedPackage"},
                new byte[][] {"<document/>".getBytes(StandardCharsets.UTF_8), {1}, {2}});

        assertThatThrownBy(() -> parser.inspectZip(macro))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_ACTIVE_CONTENT_REJECTED");
        assertThatThrownBy(() -> parser.inspectZip(encrypted))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_ACTIVE_CONTENT_REJECTED");
        assertThatThrownBy(() -> parser.parse("not a pdf".getBytes(StandardCharsets.UTF_8), DocumentParser.PDF))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_PDF_CORRUPT");
        assertThatThrownBy(() -> parser.parse("not a zip".getBytes(StandardCharsets.UTF_8), DocumentParser.DOCX))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_DOCX_CORRUPT");
        assertBusinessError(
                () -> inspector.inspect("spoof.pdf", "plain text".getBytes(StandardCharsets.UTF_8)),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertBusinessError(
                () -> inspector.inspect("macro.docm", macro), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsEmptyOversizedUnsupportedAndZipLimitsBeforePersistence() {
        assertBusinessError(() -> inspector.inspect("empty.txt", new byte[0]), ErrorCode.VALIDATION_ERROR);
        assertBusinessError(
                () -> inspector.inspect("large.txt", new byte[(int) DocumentFileInspector.MAX_FILE_SIZE + 1]),
                ErrorCode.PAYLOAD_TOO_LARGE);
        assertBusinessError(
                () -> inspector.inspect("resume.html", "<html/>".getBytes(StandardCharsets.UTF_8)),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);

        properties.setMaxZipEntries(1);
        byte[] tooManyEntries = zip(
                new String[] {"word/document.xml", "word/styles.xml"},
                new byte[][] {{1}, {2}});
        assertThatThrownBy(() -> parser.inspectZip(tooManyEntries))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessage("DOCUMENT_ZIP_LIMIT");
    }

    @Test
    void exactTwentyMebibyteBoundaryIsAcceptedBeforeParsing() throws Exception {
        byte[] validPdf = pdf("boundary document");
        byte[] boundary = Arrays.copyOf(validPdf, (int) DocumentFileInspector.MAX_FILE_SIZE);

        assertThat(inspector.inspect("boundary.pdf", boundary).mimeType())
                .isEqualTo(DocumentParser.PDF);
    }

    @Test
    void parserTimeoutCancelsAResourceBoundTaskWithOnlyASafeCode() {
        DocumentParserProperties timeoutProperties = new DocumentParserProperties();
        timeoutProperties.setTimeout(Duration.ZERO);
        DocumentParser timeoutParser = new DocumentParser(timeoutProperties);
        try {
            byte[] boundedLargeText = "a".repeat(10 * 1024 * 1024)
                    .getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> timeoutParser.parse(boundedLargeText, DocumentParser.TXT))
                    .isInstanceOfSatisfying(
                            DocumentParsingException.class,
                            exception -> assertThat(exception.safeCode())
                                    .isEqualTo("DOCUMENT_PARSE_TIMEOUT"))
                    .hasMessage("DOCUMENT_PARSE_TIMEOUT");
        } finally {
            timeoutParser.close();
        }
    }

    private void assertBusinessError(ThrowingRunnable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private byte[] pdf(String... pages) throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(40, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] zip(String[] names, byte[][] values) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < names.length; index++) {
                zip.putNextEntry(new ZipEntry(names[index]));
                zip.write(values[index]);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
