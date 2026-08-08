package com.hiresemble.careerartifact.infrastructure;

import com.hiresemble.careerartifact.application.OfficeValidation;
import com.hiresemble.careerartifact.application.PortfolioPresentationRenderer;
import com.hiresemble.careerartifact.application.RenderedOfficeFile;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioSlide;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

@Component
public final class PoiPortfolioPresentationRenderer implements PortfolioPresentationRenderer {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;
    private static final String MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
    private final CareerArtifactProperties properties;

    public PoiPortfolioPresentationRenderer(CareerArtifactProperties properties) {
        this.properties = properties;
    }

    @Override
    public RenderedOfficeFile render(
            PortfolioContent content, CareerArtifactRenderProfile renderProfile) {
        try (XMLSlideShow show = new XMLSlideShow();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            show.setPageSize(new Dimension(WIDTH, HEIGHT));
            for (PortfolioSlide contentSlide : content.slides()) {
                XSLFSlide slide = show.createSlide();
                background(slide);
                title(slide, contentSlide.title());
                if (contentSlide.subtitle() != null) subtitle(slide, contentSlide.subtitle());
                body(slide, contentSlide.items());
                visual(slide, contentSlide);
                footer(slide, contentSlide.slideNo(), renderProfile);
            }
            show.getProperties().getCoreProperties().setCreator("Hiresemble");
            show.getProperties().getCoreProperties().setTitle("Career Artifact Portfolio");
            show.write(output);
            byte[] bytes = output.toByteArray();
            OfficeValidation validation = validate(bytes);
            if (!validation.valid()) {
                throw new IllegalArgumentException(
                        "PPTX_VALIDATION_FAILED:" + String.join(",", validation.warnings()));
            }
            return new RenderedOfficeFile(
                    bytes,
                    CareerArtifactTypes.PPTX_MIME,
                    bytes.length,
                    validation.checksumSha256());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("PPTX_RENDER_FAILED", exception);
        }
    }

    @Override
    public OfficeValidation validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0
                || bytes.length > properties.getMaxGeneratedFileBytes()) {
            return invalid(bytes, "PPTX_SIZE_INVALID");
        }
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            OPCPackage packageFile = show.getPackage();
            PackagePart main = packageFile.getPart(
                    PackagingURIHelper.createPartName("/ppt/presentation.xml"));
            int count = show.getSlides().size();
            Dimension pageSize = show.getPageSize();
            if (main == null || !MAIN_CONTENT_TYPE.equals(main.getContentType())
                    || pageSize.width * 9 != pageSize.height * 16
                    || count < 6 || count > 12 || hasUnsafePartOrRelationship(packageFile)) {
                return invalid(bytes, "PPTX_PACKAGE_INVALID");
            }
            for (XSLFSlide slide : show.getSlides()) {
                if (slide.getShapes().isEmpty()) return invalid(bytes, "PPTX_EMPTY_SLIDE");
                int characters = slide.getShapes().stream()
                        .filter(shape -> shape instanceof XSLFTextShape)
                        .map(shape -> ((XSLFTextShape) shape).getText().length())
                        .reduce(0, Integer::sum);
                if (characters > 1800) return invalid(bytes, "PPTX_CONTENT_OVERFLOW");
                boolean titleFound = false;
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        if (textShape.getText().isBlank()) continue;
                        var anchor = textShape.getAnchor();
                        boolean title = textShape instanceof XSLFTextBox
                                && anchor != null && anchor.getY() >= 40 && anchor.getY() < 100;
                        if (title) titleFound = true;
                        for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
                            for (XSLFTextRun run : paragraph.getTextRuns()) {
                                if (run.getRawText() == null || run.getRawText().isBlank()) continue;
                                Double font = run.getFontSize();
                                if (font != null && font < (title ? 28d : 18d)) {
                                    return invalid(
                                            bytes,
                                            title
                                                    ? "PPTX_TITLE_FONT_TOO_SMALL"
                                                    : "PPTX_BODY_FONT_TOO_SMALL");
                                }
                            }
                        }
                    }
                }
                if (!titleFound) return invalid(bytes, "PPTX_TITLE_MISSING");
            }
            return new OfficeValidation(
                    true,
                    CareerArtifactTypes.PPTX_MIME,
                    bytes.length,
                    sha256(bytes),
                    count,
                    List.of());
        } catch (Exception exception) {
            return invalid(bytes, "PPTX_REOPEN_FAILED");
        }
    }

    private void background(XSLFSlide slide) {
        XSLFAutoShape background = slide.createAutoShape();
        background.setShapeType(ShapeType.RECT);
        background.setAnchor(new Rectangle(0, 0, WIDTH, HEIGHT));
        background.setFillColor(new Color(248, 250, 252));
        background.setLineColor(new Color(248, 250, 252));
    }

    private void title(XSLFSlide slide, String text) {
        textBox(slide, new Rectangle(64, 46, 832, 72), text, 32d, true, new Color(15, 23, 42));
    }

    private void subtitle(XSLFSlide slide, String text) {
        textBox(slide, new Rectangle(66, 114, 828, 44), text, 20d, false, new Color(71, 85, 105));
    }

    private void body(XSLFSlide slide, List<String> items) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(76, 176, 570, 280));
        box.setWordWrap(true);
        for (String item : items) {
            XSLFTextParagraph paragraph = box.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setLeftMargin(18d);
            paragraph.setIndent(-12d);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(item);
            style(run, 20d, false, new Color(30, 41, 59));
        }
    }

    private void visual(XSLFSlide slide, PortfolioSlide content) {
        if (content.visualType() == CareerArtifactTypes.PortfolioVisualType.NONE) return;
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(ShapeType.ROUND_RECT);
        shape.setAnchor(new Rectangle(690, 188, 190, 190));
        shape.setFillColor(new Color(224, 231, 255));
        shape.setLineColor(new Color(99, 102, 241));
        XSLFTextParagraph paragraph = shape.addNewTextParagraph();
        paragraph.setTextAlign(TextAlign.CENTER);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(content.visualType().name().replace('_', ' '));
        style(run, 18d, true, new Color(55, 48, 163));
    }

    private void footer(
            XSLFSlide slide, int slideNo, CareerArtifactRenderProfile renderProfile) {
        List<String> values = new java.util.ArrayList<>();
        values.add(renderProfile.displayName());
        if (renderProfile.includeContact() && slideNo == 1) {
            if (renderProfile.email() != null) values.add(renderProfile.email());
            if (renderProfile.phone() != null) values.add(renderProfile.phone());
            renderProfile.links().forEach(link -> values.add(link.label() + ": " + link.url()));
        }
        values.add(Integer.toString(slideNo));
        String text = String.join("  ·  ", values);
        textBox(slide, new Rectangle(68, 492, 824, 24), text, 18d, false, new Color(100, 116, 139));
    }

    private void textBox(
            XSLFSlide slide,
            Rectangle anchor,
            String text,
            double size,
            boolean bold,
            Color color) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(anchor);
        XSLFTextRun run = box.addNewTextParagraph().addNewTextRun();
        run.setText(text);
        style(run, size, bold, color);
    }

    private void style(XSLFTextRun run, double size, boolean bold, Color color) {
        run.setFontFamily("Arial", FontGroup.LATIN);
        run.setFontFamily("Noto Sans KR", FontGroup.EAST_ASIAN);
        run.setFontSize(size);
        run.setBold(bold);
        run.setFontColor(color);
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
                    || name.contains("media/")
                    || name.contains("/charts/")
                    || name.contains("/diagrams/")
                    || name.endsWith(".exe")
                    || type.startsWith("image/")
                            && !name.equals("/docprops/thumbnail.jpeg")
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

    private OfficeValidation invalid(byte[] bytes, String warning) {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        return new OfficeValidation(
                false,
                CareerArtifactTypes.PPTX_MIME,
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
