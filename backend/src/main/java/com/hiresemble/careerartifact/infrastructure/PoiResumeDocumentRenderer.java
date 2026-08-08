package com.hiresemble.careerartifact.infrastructure;

import com.hiresemble.careerartifact.application.OfficeValidation;
import com.hiresemble.careerartifact.application.RenderedOfficeFile;
import com.hiresemble.careerartifact.application.ResumeDocumentRenderer;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeItem;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.springframework.stereotype.Component;

@Component
public final class PoiResumeDocumentRenderer implements ResumeDocumentRenderer {

    private static final String MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private final CareerArtifactProperties properties;

    public PoiResumeDocumentRenderer(CareerArtifactProperties properties) {
        this.properties = properties;
    }

    @Override
    public RenderedOfficeFile render(
            ResumeContent content, CareerArtifactRenderProfile renderProfile) {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configureA4(document);
            heading(document, renderProfile.displayName(), 1, 20);
            if (renderProfile.includeContact()) renderContact(document, renderProfile);
            if (content.headline() != null) centered(document, content.headline(), 12);
            if (content.summary() != null) {
                heading(document, "요약", 2, 14);
                paragraph(document, content.summary(), false, 10);
            }
            if (content.skills() != null && !content.skills().isEmpty()) {
                heading(document, "핵심 역량", 2, 14);
                content.skills().forEach(value -> paragraph(document, value, true, 10));
            }
            content.sections().forEach(section -> {
                heading(document, section.title(), 2, 14);
                section.items().forEach(item -> renderItem(document, item));
            });
            document.getProperties().getCoreProperties().setCreator("Hiresemble");
            document.getProperties().getCoreProperties().setTitle("Career Artifact Resume");
            document.write(output);
            byte[] bytes = output.toByteArray();
            OfficeValidation validation = validate(bytes);
            if (!validation.valid()) {
                throw new IllegalArgumentException(
                        "DOCX_VALIDATION_FAILED:" + String.join(",", validation.warnings()));
            }
            return new RenderedOfficeFile(
                    bytes,
                    CareerArtifactTypes.DOCX_MIME,
                    bytes.length,
                    validation.checksumSha256());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("DOCX_RENDER_FAILED", exception);
        }
    }

    @Override
    public OfficeValidation validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0
                || bytes.length > properties.getMaxGeneratedFileBytes()) {
            return invalid(bytes, "DOCX_SIZE_INVALID");
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            OPCPackage packageFile = document.getPackage();
            PackagePart main = packageFile.getPart(
                    PackagingURIHelper.createPartName("/word/document.xml"));
            String mainXml = main == null
                    ? ""
                    : new String(main.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            CTSectPr section = document.getDocument().getBody().getSectPr();
            boolean a4SingleColumn = section != null
                    && section.isSetPgSz()
                    && BigInteger.valueOf(11906).equals(section.getPgSz().getW())
                    && BigInteger.valueOf(16838).equals(section.getPgSz().getH())
                    && section.isSetCols()
                    && BigInteger.ONE.equals(section.getCols().getNum());
            if (main == null || !MAIN_CONTENT_TYPE.equals(main.getContentType())
                    || !a4SingleColumn
                    || hasUnsafePartOrRelationship(packageFile)
                    || containsUnsupportedDrawing(mainXml)
                    || document.getParagraphs().stream().noneMatch(value -> !value.getText().isBlank())) {
                return invalid(bytes, "DOCX_PACKAGE_INVALID");
            }
            int units = document.getParagraphs().size() + document.getTables().size();
            if (units > 300) return invalid(bytes, "DOCX_CONTENT_OVERFLOW");
            return new OfficeValidation(
                    true,
                    CareerArtifactTypes.DOCX_MIME,
                    bytes.length,
                    sha256(bytes),
                    units,
                    List.of());
        } catch (Exception exception) {
            return invalid(bytes, "DOCX_REOPEN_FAILED");
        }
    }

    private void configureA4(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz page = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        page.setW(BigInteger.valueOf(11906));
        page.setH(BigInteger.valueOf(16838));
        page.setOrient(STPageOrientation.PORTRAIT);
        if (!section.isSetCols()) section.addNewCols();
        section.getCols().setNum(BigInteger.ONE);
    }

    private void renderContact(XWPFDocument document, CareerArtifactRenderProfile profile) {
        List<String> values = new java.util.ArrayList<>();
        if (profile.email() != null) values.add(profile.email());
        if (profile.phone() != null) values.add(profile.phone());
        profile.links().forEach(link -> values.add(link.label() + ": " + link.url()));
        if (!values.isEmpty()) centered(document, String.join(" | ", values), 9);
    }

    private void renderItem(XWPFDocument document, ResumeItem item) {
        String title = java.util.stream.Stream.of(
                        item.heading(), item.subheading(), item.period())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" · "));
        if (!title.isBlank()) paragraph(document, title, false, 11);
        item.bullets().forEach(value -> paragraph(document, value, true, 10));
    }

    private void heading(XWPFDocument document, String text, int level, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + level);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(size);
        font(run);
        run.setText(text);
    }

    private void centered(XWPFDocument document, String text, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(size);
        font(run);
        run.setText(text);
    }

    private void paragraph(XWPFDocument document, String text, boolean bullet, int size) {
        XWPFParagraph paragraph = document.createParagraph();
        if (bullet) {
            paragraph.setIndentationLeft(360);
            text = "• " + text;
        }
        XWPFRun run = paragraph.createRun();
        run.setFontSize(size);
        font(run);
        run.setText(text);
    }

    private void font(XWPFRun run) {
        run.setFontFamily("Arial");
        run.setFontFamily("Noto Sans KR", XWPFRun.FontCharRange.eastAsia);
    }

    private boolean hasUnsafePartOrRelationship(OPCPackage packageFile) throws Exception {
        for (PackageRelationship relationship : packageFile.getRelationships()) {
            if (relationship.getTargetMode() == TargetMode.EXTERNAL) return true;
        }
        for (PackagePart part : packageFile.getParts()) {
            String name = part.getPartName().getName().toLowerCase(java.util.Locale.ROOT);
            String type = part.getContentType().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("vba")
                    || name.contains("embeddings")
                    || name.contains("/media/")
                    || name.contains("/charts/")
                    || name.contains("activex")
                    || name.endsWith(".exe")
                    || type.startsWith("image/")
                    || type.contains("chart")
                    || type.contains("macroenabled")
                    || type.contains("oleobject")) return true;
            if (!part.isRelationshipPart()) {
                for (PackageRelationship relationship : part.getRelationships()) {
                    if (relationship.getTargetMode() == TargetMode.EXTERNAL) return true;
                }
            }
        }
        return false;
    }

    private boolean containsUnsupportedDrawing(String xml) {
        String compact = xml.toLowerCase(java.util.Locale.ROOT);
        return compact.contains("<w:drawing")
                || compact.contains("<w:pict")
                || compact.contains("<w:txbxcontent")
                || compact.contains("<v:textbox");
    }

    private OfficeValidation invalid(byte[] bytes, String warning) {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        return new OfficeValidation(
                false,
                CareerArtifactTypes.DOCX_MIME,
                safe.length,
                sha256(safe),
                0,
                List.of(warning));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
