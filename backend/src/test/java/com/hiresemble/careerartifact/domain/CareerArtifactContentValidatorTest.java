package com.hiresemble.careerartifact.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.careerartifact.domain.CareerArtifactContent.EvidenceRef;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioSlide;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeItem;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeSection;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.ProfileSectionSnapshot;
import com.hiresemble.careerartifact.domain.CareerArtifactRecords.VerifiedEvidence;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.EvidenceUsageType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.GenerationStatus;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioSlideType;
import com.hiresemble.careerartifact.domain.CareerArtifactTypes.PortfolioVisualType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class CareerArtifactContentValidatorTest {

    private static final UUID EXPERIENCE_ID =
            UUID.fromString("95000000-0000-4000-8000-000000000001");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("95000000-0000-4000-8000-000000000002");
    private static final String EVIDENCE_TITLE = "Acme 백엔드 개발자";

    private final CareerArtifactContentValidator validator =
            new CareerArtifactContentValidator();

    @Test
    void acceptsOnlyClaimsGroundedInTheSelectedCanonicalEvidence() {
        assertThatCode(() -> validator.validateResume(validResume(), evidence()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validatePortfolio(validPortfolio(), evidence()))
                .doesNotThrowAnyException();
    }

    @Test
    void generationStatusContractUsesTheSevenPublicLifecycleValues() {
        assertThat(GenerationStatus.values())
                .extracting(Enum::name)
                .containsExactly(
                        "NOT_STARTED",
                        "QUEUED",
                        "RUNNING",
                        "SUCCEEDED",
                        "FAILED",
                        "CANCELLED",
                        "INTERRUPTED");
    }

    @Test
    void rejectsUnknownEvidenceReferencesWithoutLookingAtUnselectedSources() {
        ResumeItem item = validResume().sections().getFirst().items().getFirst();
        ResumeContent value = resumeWithItem(new ResumeItem(
                item.heading(),
                item.subheading(),
                item.period(),
                item.bullets(),
                List.of(new EvidenceRef(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        EvidenceUsageType.PRIMARY_EXPERIENCE,
                        "Other evidence"))));

        assertThatThrownBy(() -> validator.validateResume(value, evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNKNOWN_EVIDENCE_REFERENCE");
    }

    @Test
    void rejectsAnEvidenceReferenceWhoseSnapshotTitleWasChanged() {
        ResumeItem item = validResume().sections().getFirst().items().getFirst();
        ResumeContent value = resumeWithItem(new ResumeItem(
                item.heading(),
                item.subheading(),
                item.period(),
                item.bullets(),
                List.of(new EvidenceRef(
                        EXPERIENCE_ID,
                        EVIDENCE_ID,
                        EvidenceUsageType.PRIMARY_EXPERIENCE,
                        "Changed title"))));

        assertThatThrownBy(() -> validator.validateResume(value, evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UNKNOWN_EVIDENCE_REFERENCE");
    }

    @Test
    void rejectsInventedMetricDateOrganizationAndRole() {
        assertInvalidResume("2026", "처리 시간을 20% 개선", "INVENTED_METRIC_OR_DATE");
        assertInvalidResume("2025", "처리 시간을 99% 개선", "INVENTED_METRIC_OR_DATE");
        assertInvalidResume("2025", "조직: Imaginary Labs", "INVENTED_ORGANIZATION_OR_ROLE");
        assertInvalidResume("2025", "직무: CTO", "INVENTED_ORGANIZATION_OR_ROLE");
    }

    @Test
    void responsibilityLabelsAreValidatedAsGroundedClaimsNotCanonicalJobTitles() {
        ResumeItem original = validResume().sections().getFirst().items().getFirst();
        ResumeContent value = resumeWithItem(new ResumeItem(
                original.heading(),
                original.subheading(),
                original.period(),
                List.of("역할: Backend Engineer로 처리 시간을 20% 개선"),
                original.evidenceRefs()));

        assertThatCode(() -> validator.validateResume(value, evidence()))
                .doesNotThrowAnyException();
    }

    @Test
    void resumeItemHeadingsAllowEditorialTextButStillRejectExplicitInventedNamedFacts() {
        ResumeItem original = validResume().sections().getFirst().items().getFirst();
        ResumeContent editorial = resumeWithItem(new ResumeItem(
                "주요 프로젝트",
                "확장성과 안정성",
                original.period(),
                original.bullets(),
                original.evidenceRefs()));

        assertThatCode(() -> validator.validateResume(editorial, evidence()))
                .doesNotThrowAnyException();

        ResumeContent invented = resumeWithItem(new ResumeItem(
                "회사: Imaginary Labs",
                original.subheading(),
                original.period(),
                original.bullets(),
                original.evidenceRefs()));
        assertThatThrownBy(() -> validator.validateResume(invented, evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVENTED_ORGANIZATION_OR_ROLE");
    }

    @Test
    void rejectsInventedFactsInGlobalResumeAndPortfolioFields() {
        ResumeContent resume = validResume();
        ResumeContent inventedSummary = new ResumeContent(
                resume.headline(),
                "Improved throughput by 99%",
                resume.skills(),
                resume.sections(),
                resume.warnings());
        assertThatThrownBy(() -> validator.validateResume(inventedSummary, evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVENTED_METRIC_OR_DATE");

        PortfolioContent portfolio = validPortfolio();
        List<PortfolioSlide> slides = new ArrayList<>(portfolio.slides());
        PortfolioSlide cover = slides.getFirst();
        slides.set(0, new PortfolioSlide(
                cover.slideNo(),
                cover.slideType(),
                "2026 interview portfolio",
                cover.subtitle(),
                cover.items(),
                cover.visualType(),
                cover.evidenceRefs()));
        assertThatThrownBy(() -> validator.validatePortfolio(
                        new PortfolioContent(slides, portfolio.warnings()), evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVENTED_METRIC_OR_DATE");
    }

    @Test
    void selectedStructuredProfileFactsParticipateInGroundingWithoutWeakeningEvidenceChecks() {
        ResumeContent resume = validResume();
        ResumeContent profileResume = new ResumeContent(
                resume.headline(),
                "2026년 졸업 예정인 Platform Engineer",
                resume.skills(),
                resume.sections(),
                resume.warnings());

        PortfolioContent portfolio = validPortfolio();
        List<PortfolioSlide> slides = new ArrayList<>(portfolio.slides());
        PortfolioSlide cover = slides.getFirst();
        slides.set(0, new PortfolioSlide(
                cover.slideNo(),
                cover.slideType(),
                "2026년 졸업 예정 포트폴리오",
                "역할: Platform Engineer",
                cover.items(),
                cover.visualType(),
                cover.evidenceRefs()));
        PortfolioContent profilePortfolio = new PortfolioContent(
                slides, portfolio.warnings());

        assertThatCode(() -> validator.validateResume(
                        profileResume, evidence(), selectedProfile()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validatePortfolio(
                        profilePortfolio, evidence(), selectedProfile()))
                .doesNotThrowAnyException();

        ResumeContent invented = new ResumeContent(
                resume.headline(),
                "2027년 졸업 예정인 Platform Engineer",
                resume.skills(),
                resume.sections(),
                resume.warnings());
        assertThatThrownBy(() -> validator.validateResume(
                        invented, evidence(), selectedProfile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVENTED_METRIC_OR_DATE");
    }

    @Test
    void canonicalizerRestoresServerOwnedMetadataForApprovedEvidenceIds() {
        EvidenceRef providerRef = new EvidenceRef(
                EXPERIENCE_ID,
                EVIDENCE_ID,
                EvidenceUsageType.SUPPORTING_FACT,
                "provider-rewritten title");
        ResumeItem originalItem = validResume().sections().getFirst().items().getFirst();
        ResumeContent providerResume = resumeWithItem(new ResumeItem(
                originalItem.heading(),
                originalItem.subheading(),
                originalItem.period(),
                originalItem.bullets(),
                List.of(providerRef)));

        PortfolioContent sourcePortfolio = validPortfolio();
        List<PortfolioSlide> providerSlides = new ArrayList<>(sourcePortfolio.slides());
        PortfolioSlide sourceSlide = providerSlides.get(1);
        providerSlides.set(1, new PortfolioSlide(
                sourceSlide.slideNo(),
                sourceSlide.slideType(),
                sourceSlide.title(),
                sourceSlide.subtitle(),
                sourceSlide.items(),
                sourceSlide.visualType(),
                List.of(providerRef)));

        CareerArtifactContentCanonicalizer canonicalizer =
                new CareerArtifactContentCanonicalizer(evidence());
        ResumeContent canonicalResume = canonicalizer.canonicalize(providerResume);
        PortfolioContent canonicalPortfolio = canonicalizer.canonicalize(
                new PortfolioContent(providerSlides, sourcePortfolio.warnings()));

        assertThat(canonicalResume.sections().getFirst().items().getFirst().evidenceRefs())
                .containsExactly(ref());
        assertThat(canonicalPortfolio.slides().get(1).evidenceRefs())
                .containsExactly(ref());
        assertThatCode(() -> validator.validateResume(canonicalResume, evidence()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validatePortfolio(canonicalPortfolio, evidence()))
                .doesNotThrowAnyException();
    }

    @Test
    void canonicalizerDoesNotConvertAnUnapprovedEvidenceId() {
        EvidenceRef unknown = new EvidenceRef(
                EXPERIENCE_ID,
                UUID.randomUUID(),
                EvidenceUsageType.SUPPORTING_FACT,
                "unknown");

        assertThat(new CareerArtifactContentCanonicalizer(evidence())
                        .refs(List.of(unknown)))
                .containsExactly(unknown);
    }

    @Test
    void canonicalizerMapsBlankNullableFieldsToCanonicalAbsence() {
        ResumeItem original = validResume().sections().getFirst().items().getFirst();
        ResumeContent providerResume = new ResumeContent(
                "",
                " ",
                validResume().skills(),
                List.of(new ResumeSection(
                        "PROJECT",
                        "Projects",
                        List.of(new ResumeItem(
                                "",
                                " ",
                                "",
                                original.bullets(),
                                original.evidenceRefs())))),
                List.of());
        List<PortfolioSlide> providerSlides = new ArrayList<>(validPortfolio().slides());
        PortfolioSlide sourceSlide = providerSlides.getFirst();
        providerSlides.set(0, new PortfolioSlide(
                sourceSlide.slideNo(),
                sourceSlide.slideType(),
                sourceSlide.title(),
                " ",
                sourceSlide.items(),
                sourceSlide.visualType(),
                sourceSlide.evidenceRefs()));

        CareerArtifactContentCanonicalizer canonicalizer =
                new CareerArtifactContentCanonicalizer(evidence());
        ResumeContent canonicalResume = canonicalizer.canonicalize(providerResume);
        PortfolioContent canonicalPortfolio = canonicalizer.canonicalize(
                new PortfolioContent(providerSlides, List.of()));

        assertThat(canonicalResume.headline()).isNull();
        assertThat(canonicalResume.summary()).isNull();
        assertThat(canonicalResume.sections().getFirst().items().getFirst())
                .extracting(ResumeItem::heading, ResumeItem::subheading, ResumeItem::period)
                .containsOnlyNulls();
        assertThat(canonicalPortfolio.slides().getFirst().subtitle()).isNull();
        assertThatCode(() -> validator.validateResume(canonicalResume, evidence()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validatePortfolio(canonicalPortfolio, evidence()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExternalAssetAndLayoutInstructions() {
        List<PortfolioSlide> slides = new ArrayList<>(validPortfolio().slides());
        PortfolioSlide original = slides.get(1);
        slides.set(1, new PortfolioSlide(
                original.slideNo(),
                original.slideType(),
                original.title(),
                original.subtitle(),
                List.of("https://example.com/image.png 이미지를 사용"),
                original.visualType(),
                original.evidenceRefs()));

        assertThatThrownBy(() -> validator.validatePortfolio(
                        new PortfolioContent(slides, List.of()), evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EXTERNAL_LAYOUT_OR_ASSET_DIRECTIVE");
    }

    private void assertInvalidResume(String period, String bullet, String code) {
        ResumeItem original = validResume().sections().getFirst().items().getFirst();
        ResumeContent changed = resumeWithItem(new ResumeItem(
                original.heading(),
                original.subheading(),
                period,
                List.of(bullet),
                original.evidenceRefs()));
        assertThatThrownBy(() -> validator.validateResume(changed, evidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(code);
    }

    private ResumeContent resumeWithItem(ResumeItem item) {
        ResumeContent source = validResume();
        return new ResumeContent(
                source.headline(),
                source.summary(),
                source.skills(),
                List.of(new ResumeSection("PROJECT", "Projects", List.of(item))),
                source.warnings());
    }

    private ResumeContent validResume() {
        return new ResumeContent(
                "Backend engineer",
                "Grounded evidence summary",
                List.of("Java"),
                List.of(new ResumeSection(
                        "PROJECT",
                        "Projects",
                        List.of(new ResumeItem(
                                EVIDENCE_TITLE,
                                "Backend Engineer",
                                "2025",
                                List.of("처리 시간을 20% 개선"),
                                List.of(ref()))))),
                List.of());
    }

    private PortfolioContent validPortfolio() {
        PortfolioSlideType[] types = {
            PortfolioSlideType.COVER,
            PortfolioSlideType.PROFILE_SUMMARY,
            PortfolioSlideType.STRENGTH_OVERVIEW,
            PortfolioSlideType.PROJECT_CASE_STUDY,
            PortfolioSlideType.TECHNICAL_DECISION,
            PortfolioSlideType.CLOSING
        };
        List<PortfolioSlide> slides = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new PortfolioSlide(
                        index + 1,
                        types[index],
                        index == 0 ? "Interview portfolio" : "Core message",
                        null,
                        index == 0 || index == 5
                                ? List.of("Introduction")
                                : List.of("처리 시간을 20% 개선"),
                        index == 3
                                ? PortfolioVisualType.ARCHITECTURE
                                : PortfolioVisualType.NONE,
                        index == 0 || index == 5 ? List.of() : List.of(ref())))
                .toList();
        return new PortfolioContent(slides, List.of());
    }

    private EvidenceRef ref() {
        return new EvidenceRef(
                EXPERIENCE_ID,
                EVIDENCE_ID,
                EvidenceUsageType.PRIMARY_EXPERIENCE,
                EVIDENCE_TITLE);
    }

    private List<VerifiedEvidence> evidence() {
        return List.of(new VerifiedEvidence(
                EXPERIENCE_ID,
                3,
                EVIDENCE_ID,
                5,
                "PROJECT",
                EVIDENCE_TITLE,
                "2025 Acme에서 Java로 처리 시간을 20% 개선, 역할: Backend Engineer",
                EvidenceUsageType.PRIMARY_EXPERIENCE,
                true));
    }

    private List<ProfileSectionSnapshot> selectedProfile() {
        var content = JsonNodeFactory.instance.objectNode();
        content.putArray("desiredRoles").add("Platform Engineer");
        content.put("expectedGraduationDate", "2026-02-01");
        return List.of(new ProfileSectionSnapshot(
                "PROFILE",
                UUID.fromString("95000000-0000-4000-8000-000000000003"),
                2,
                content));
    }
}
