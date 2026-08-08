package com.hiresemble.careerartifact.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.careerartifact.application.RenderedOfficeFile;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioSlide;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeItem;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeSection;
import com.hiresemble.careerartifact.domain.CareerArtifactRenderProfile;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioSlideType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioVisualType;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class CareerArtifactOfficeRendererTest {

    private final CareerArtifactProperties properties = new CareerArtifactProperties();

    @Test
    void docxIsA4OneColumnGroundedTextWithRendererOnlyPlainTextContact() throws Exception {
        PoiResumeDocumentRenderer renderer = new PoiResumeDocumentRenderer(properties);
        RenderedOfficeFile file = renderer.render(resume(), profile(true));

        assertThat(file.mimeType()).isEqualTo(CareerArtifactTypes.DOCX_MIME);
        assertThat(file.sizeBytes()).isPositive().isLessThanOrEqualTo(10 * 1024 * 1024);
        assertThat(renderer.validate(file.bytes()).valid()).isTrue();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.bytes()))) {
            OPCPackage packageFile = document.getPackage();
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text)
                    .contains("홍길동", "dev@example.com", "010-1234-5678")
                    .contains("GitHub: https://example.com/profile")
                    .contains("승인된 프로젝트", "성능을 20% 개선");
            assertThat(document.getParagraphs())
                    .anyMatch(paragraph -> "Heading1".equals(paragraph.getStyle()))
                    .anyMatch(paragraph -> paragraph.getText().startsWith("•"));
            var section = document.getDocument().getBody().getSectPr();
            assertThat(section.getPgSz().getW().toString()).isEqualTo("11906");
            assertThat(section.getPgSz().getH().toString()).isEqualTo("16838");
            assertThat(section.getCols().getNum().intValue()).isEqualTo(1);
            assertSafePackage(packageFile);
        }
    }

    @Test
    void docxExcludesEmailPhoneAndLinksWhenContactIsDisabled() throws Exception {
        PoiResumeDocumentRenderer renderer = new PoiResumeDocumentRenderer(properties);
        RenderedOfficeFile file = renderer.render(resume(), profile(false));

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(file.bytes()))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + right);
            assertThat(text).contains("홍길동");
            assertThat(text).doesNotContain(
                    "dev@example.com", "010-1234-5678", "https://example.com/profile");
        }
    }

    @Test
    void pptxIsSixteenByNineUsesSixSlidesAndHasNoExternalAssets() throws Exception {
        PoiPortfolioPresentationRenderer renderer =
                new PoiPortfolioPresentationRenderer(properties);
        RenderedOfficeFile file = renderer.render(portfolio(), profile(true));

        assertThat(file.mimeType()).isEqualTo(CareerArtifactTypes.PPTX_MIME);
        assertThat(renderer.validate(file.bytes()).valid()).isTrue();
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(file.bytes()))) {
            OPCPackage packageFile = show.getPackage();
            assertThat(show.getPageSize()).isEqualTo(new Dimension(960, 540));
            assertThat(show.getSlides()).hasSize(6);
            String text = show.getSlides().stream()
                    .flatMap(slide -> slide.getShapes().stream())
                    .filter(XSLFTextShape.class::isInstance)
                    .map(XSLFTextShape.class::cast)
                    .map(XSLFTextShape::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text)
                    .contains("핵심 프로젝트", "dev@example.com", "010-1234-5678")
                    .contains("GitHub: https://example.com/profile");
            show.getSlides().forEach(slide -> slide.getShapes().stream()
                    .filter(XSLFTextShape.class::isInstance)
                    .map(XSLFTextShape.class::cast)
                    .flatMap(shape -> shape.getTextParagraphs().stream())
                    .flatMap(paragraph -> paragraph.getTextRuns().stream())
                    .filter(run -> run.getFontSize() != null
                            && run.getRawText() != null && !run.getRawText().isBlank())
                    .forEach(run -> {
                        assertThat(run.getFontSize()).isGreaterThanOrEqualTo(18d);
                        assertThat(run.getFontFamily(FontGroup.LATIN)).isEqualTo("Arial");
                        assertThat(run.getFontFamily(FontGroup.EAST_ASIAN))
                                .isEqualTo("Noto Sans KR");
                    }));
            show.getSlides().forEach(slide -> {
                XSLFTextShape title = slide.getShapes().stream()
                        .filter(XSLFTextShape.class::isInstance)
                        .map(XSLFTextShape.class::cast)
                        .filter(shape -> !shape.getText().isBlank()
                                && shape.getAnchor().getY() < 100)
                        .findFirst().orElseThrow();
                title.getTextParagraphs().stream()
                        .flatMap(paragraph -> paragraph.getTextRuns().stream())
                        .filter(run -> run.getRawText() != null && !run.getRawText().isBlank())
                        .forEach(run -> assertThat(run.getFontSize()).isGreaterThanOrEqualTo(28d));
            });
            assertSafePackage(packageFile);
            assertThat(packageFile.getParts())
                    .noneMatch(part -> part.getPartName().getName().contains("/media/"));
        }
    }

    @Test
    void pptxExcludesContactFieldsWhenContactIsDisabled() throws Exception {
        PoiPortfolioPresentationRenderer renderer =
                new PoiPortfolioPresentationRenderer(properties);
        RenderedOfficeFile file = renderer.render(portfolio(), profile(false));

        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(file.bytes()))) {
            String text = show.getSlides().stream()
                    .flatMap(slide -> slide.getShapes().stream())
                    .filter(XSLFTextShape.class::isInstance)
                    .map(XSLFTextShape.class::cast)
                    .map(XSLFTextShape::getText)
                    .reduce("", (left, right) -> left + right);
            assertThat(text).contains("홍길동");
            assertThat(text).doesNotContain(
                    "dev@example.com", "010-1234-5678", "https://example.com/profile");
        }
    }

    @Test
    void validatorsRejectMalformedAndConfiguredOversizeFiles() {
        PoiResumeDocumentRenderer resumeRenderer = new PoiResumeDocumentRenderer(properties);
        PoiPortfolioPresentationRenderer portfolioRenderer =
                new PoiPortfolioPresentationRenderer(properties);
        assertThat(resumeRenderer.validate("not-docx".getBytes()).valid()).isFalse();
        assertThat(portfolioRenderer.validate("not-pptx".getBytes()).valid()).isFalse();

        CareerArtifactProperties small = new CareerArtifactProperties();
        small.setMaxGeneratedFileBytes(1024);
        assertThatThrownBy(() -> new PoiResumeDocumentRenderer(small)
                .render(resume(), profile(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("DOCX_VALIDATION_FAILED:");
        assertThatThrownBy(() -> new PoiPortfolioPresentationRenderer(small)
                .render(portfolio(), profile(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("PPTX_VALIDATION_FAILED:");
    }

    private ResumeContent resume() {
        return new ResumeContent(
                "백엔드 엔지니어",
                "승인된 경험만 사용한 요약",
                List.of("Java", "PostgreSQL"),
                List.of(new ResumeSection(
                        "PROJECT",
                        "프로젝트",
                        List.of(new ResumeItem(
                                "승인된 프로젝트",
                                "백엔드 개발자",
                                "2025",
                                List.of("성능을 20% 개선"),
                                List.of())))),
                List.of());
    }

    private PortfolioContent portfolio() {
        PortfolioSlideType[] slideTypes = {
            PortfolioSlideType.COVER,
            PortfolioSlideType.PROFILE_SUMMARY,
            PortfolioSlideType.STRENGTH_OVERVIEW,
            PortfolioSlideType.PROJECT_CASE_STUDY,
            PortfolioSlideType.TECHNICAL_DECISION,
            PortfolioSlideType.CLOSING
        };
        PortfolioVisualType[] visualTypes = {
            PortfolioVisualType.NONE,
            PortfolioVisualType.PROCESS,
            PortfolioVisualType.IMPACT_METRICS,
            PortfolioVisualType.ARCHITECTURE,
            PortfolioVisualType.TIMELINE,
            PortfolioVisualType.NONE
        };
        List<PortfolioSlide> slides = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new PortfolioSlide(
                        index + 1,
                        slideTypes[index],
                        index == 3 ? "핵심 프로젝트" : "슬라이드 " + (index + 1),
                        "한 가지 핵심 메시지",
                        List.of("승인 근거에 연결된 내용"),
                        visualTypes[index],
                        List.of()))
                .toList();
        return new PortfolioContent(slides, List.of());
    }

    private CareerArtifactRenderProfile profile(boolean includeContact) {
        return new CareerArtifactRenderProfile(
                "홍길동",
                "dev@example.com",
                "010-1234-5678",
                List.of(new CareerArtifactRenderProfile.Link(
                        "GitHub", "https://example.com/profile")),
                includeContact);
    }

    private void assertSafePackage(OPCPackage packageFile) throws Exception {
        assertThat(packageFile.getRelationships())
                .noneMatch(relationship -> relationship.getTargetMode() == TargetMode.EXTERNAL);
        for (PackagePart part : packageFile.getParts()) {
            String name = part.getPartName().getName().toLowerCase(java.util.Locale.ROOT);
            String contentType = part.getContentType().toLowerCase(java.util.Locale.ROOT);
            assertThat(name).doesNotContain("vba", "embeddings").doesNotEndWith(".exe");
            assertThat(contentType).doesNotContain("macroenabled", "oleobject");
            if (!part.isRelationshipPart()) {
                for (PackageRelationship relationship : part.getRelationships()) {
                    assertThat(relationship.getTargetMode()).isNotEqualTo(TargetMode.EXTERNAL);
                }
            }
        }
    }
}
