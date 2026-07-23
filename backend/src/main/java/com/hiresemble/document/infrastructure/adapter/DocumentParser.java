package com.hiresemble.document.infrastructure.adapter;

import com.hiresemble.document.infrastructure.config.DocumentParserProperties;
import com.hiresemble.document.domain.model.DocumentRecords.ParsedDocument;
import com.hiresemble.document.domain.model.DocumentRecords.ParsedPage;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public final class DocumentParser {

    public static final String PDF = "application/pdf";
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String TXT = "text/plain";

    private final DocumentParserProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "document-parser");
        thread.setDaemon(true);
        return thread;
    });

    public DocumentParser(DocumentParserProperties properties) {
        this.properties = properties;
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(properties.getMaxUncompressedBytes());
        ZipSecureFile.setMaxTextSize(properties.getMaxUncompressedBytes());
    }

    public ParsedDocument parse(byte[] content, String mimeType) {
        Future<ParsedDocument> future = executor.submit(() -> switch (mimeType) {
            case PDF -> pdf(content);
            case DOCX -> docx(content);
            case TXT -> txt(content);
            default -> throw new DocumentParsingException("DOCUMENT_FORMAT_UNSUPPORTED");
        });
        try {
            return future.get(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new DocumentParsingException("DOCUMENT_PARSE_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new DocumentParsingException("DOCUMENT_PARSE_INTERRUPTED", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DocumentParsingException parsingException) {
                throw parsingException;
            }
            throw new DocumentParsingException("DOCUMENT_PARSE_FAILED", cause);
        }
    }

    private ParsedDocument pdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new DocumentParsingException("DOCUMENT_ENCRYPTED");
            }
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > properties.getMaxPages()) {
                throw new DocumentParsingException("DOCUMENT_PAGE_LIMIT");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedPage> parsedPages = new ArrayList<>(pages);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                parsedPages.add(new ParsedPage(page, pageText));
                appendPage(text, pageText);
                requireTextLimit(text);
            }
            return new ParsedDocument(parsedPages, text.toString(), pages, "PDFBox", "3");
        } catch (DocumentParsingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DocumentParsingException("DOCUMENT_PDF_CORRUPT", exception);
        }
    }

    private ParsedDocument docx(byte[] content) {
        inspectZip(content);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendLine(text, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
                requireTextLimit(text);
            }
            String value = text.toString();
            return new ParsedDocument(
                    List.of(new ParsedPage(1, value)), value, null, "Apache POI", "5");
        } catch (DocumentParsingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DocumentParsingException("DOCUMENT_DOCX_CORRUPT", exception);
        }
    }

    private ParsedDocument txt(byte[] content) {
        try {
            ByteBuffer source = ByteBuffer.wrap(content);
            if (content.length >= 3
                    && content[0] == (byte) 0xEF
                    && content[1] == (byte) 0xBB
                    && content[2] == (byte) 0xBF) {
                source.position(3);
            }
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(source)
                    .toString();
            if (value.codePointCount(0, value.length()) > 500_000) {
                throw new DocumentParsingException("DOCUMENT_TEXT_LIMIT");
            }
            return new ParsedDocument(
                    List.of(new ParsedPage(1, value)), value, null, "UTF-8", "1");
        } catch (CharacterCodingException exception) {
            throw new DocumentParsingException("DOCUMENT_TXT_ENCODING_INVALID", exception);
        }
    }

    void inspectZip(byte[] content) {
        int entries = 0;
        long total = 0;
        boolean documentXml = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > properties.getMaxZipEntries()) {
                    throw new DocumentParsingException("DOCUMENT_ZIP_LIMIT");
                }
                String name = entry.getName().replace('\\', '/').toLowerCase();
                if (name.equals("word/document.xml")) documentXml = true;
                if (name.contains("vbaproject.bin")
                        || name.startsWith("word/activex/")
                        || name.startsWith("word/embeddings/")
                        || name.contains("oleobject")
                        || name.equals("encryptedpackage")
                        || name.equals("encryptioninfo")) {
                    throw new DocumentParsingException("DOCUMENT_ACTIVE_CONTENT_REJECTED");
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > properties.getMaxUncompressedBytes()) {
                        throw new DocumentParsingException("DOCUMENT_ZIP_LIMIT");
                    }
                }
            }
        } catch (DocumentParsingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentParsingException("DOCUMENT_DOCX_CORRUPT", exception);
        }
        if (!documentXml) {
            throw new DocumentParsingException("DOCUMENT_DOCX_CORRUPT");
        }
    }

    private void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            boolean first = true;
            for (XWPFTableCell cell : row.getTableCells()) {
                if (!first) text.append('\t');
                text.append(cell.getText());
                first = false;
            }
            text.append('\n');
        }
    }

    private void appendLine(StringBuilder text, String line) {
        if (line != null && !line.isEmpty()) text.append(line);
        text.append('\n');
    }

    private void appendPage(StringBuilder text, String page) {
        if (!text.isEmpty()) text.append('\n');
        text.append(page);
    }

    private void requireTextLimit(StringBuilder text) {
        if (text.codePointCount(0, text.length()) > 500_000) {
            throw new DocumentParsingException("DOCUMENT_TEXT_LIMIT");
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
