package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.workflow.JobAnalysisWorkflow.JobSourceBlock;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.ProviderSourceRequirement;
import com.hiresemble.ai.workflow.JobAnalysisWorkflow.RequirementSection;
import com.hiresemble.job.domain.CriterionSupportType;
import com.hiresemble.job.domain.FitCriterionCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobRequirementNormalizationPolicyTest {

    private final JobRequirementNormalizationPolicy policy =
            new JobRequirementNormalizationPolicy();

    @Test
    void complexPostingIsSplitClassifiedDeduplicatedAndTracedByOnePolicy() {
        String mixed = "인턴십·대외활동 우수자, 어학 우수자, 디지털 프로젝트 경험자";
        var normalized = normalize(List.of(
                source("지원 자격", "4년제 대학 또는 전문대학 졸업자 및 졸업 예정자", 0),
                source("지원 자격", "병역필 또는 면제자", 1),
                source("지원 자격", "2026년 8월부터 근무 가능한 자", 2),
                source("지원 자격", "해외여행 및 채용에 결격사유가 없는 자", 3),
                source("우대 사항", "금융 관련 자격증 보유자", 4),
                source("우대 사항", "IT·데이터 관련 자격증 보유자", 5),
                source("우대 사항", mixed, 6),
                source("우대 사항", "금융 관련 자격증 보유자", 7)));

        assertThat(normalized).hasSize(10);
        assertThat(normalized)
                .extracting(value -> value.supportType())
                .contains(
                        CriterionSupportType.EDUCATION,
                        CriterionSupportType.MILITARY_STATUS,
                        CriterionSupportType.WORK_AVAILABLE_DATE,
                        CriterionSupportType.OVERSEAS_TRAVEL_ELIGIBILITY,
                        CriterionSupportType.EMPLOYMENT_DISQUALIFICATION_STATUS,
                        CriterionSupportType.CERTIFICATION,
                        CriterionSupportType.LANGUAGE,
                        CriterionSupportType.EXPERIENCE_OR_SKILL);
        assertThat(normalized)
                .filteredOn(value -> value.text().equals("2026년 8월부터 근무 가능한 자"))
                .singleElement()
                .extracting(value -> value.requiredByDate())
                .isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(normalized)
                .filteredOn(value -> value.text().equals("어학 우수자"))
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.supportType()).isEqualTo(CriterionSupportType.LANGUAGE);
                    assertThat(value.category())
                            .isEqualTo(FitCriterionCategory.EDUCATION_CERTIFICATION_LANGUAGE);
                    assertThat(value.section())
                            .isEqualTo(RequirementSection.PREFERRED_QUALIFICATION);
                });
        assertThat(normalized)
                .filteredOn(value -> value.text().contains("인턴십·대외활동")
                        || value.text().contains("디지털 프로젝트"))
                .hasSize(2)
                .allSatisfy(value -> {
                    assertThat(value.supportType())
                            .isEqualTo(CriterionSupportType.EXPERIENCE_OR_SKILL);
                    assertThat(value.category())
                            .isEqualTo(FitCriterionCategory.PREFERRED_QUALIFICATION);
                    assertThat(value.sourceOrdinal()).isEqualTo(6);
                    assertThat(value.sourceText()).isEqualTo(mixed);
                });
        assertThat(normalized)
                .filteredOn(value -> value.text().equals("IT·데이터 관련 자격증 보유자"))
                .singleElement()
                .extracting(value -> value.supportType())
                .isEqualTo(CriterionSupportType.CERTIFICATION);
        assertThat(normalized)
                .filteredOn(value -> value.text().equals("금융 관련 자격증 보유자"))
                .hasSize(1);
        assertThat(normalized)
                .extracting(value -> value.text())
                .doesNotContain("ADSP", "IT", "데이터 관련 경험");
    }

    @Test
    void unclassifiedSourceCannotBecomeAScoringCriterion() {
        var normalized = normalize(List.of(
                new ProviderSourceRequirement(null, "조직과 잘 어울리는 분", null, 0)));

        assertThat(normalized).isEmpty();
    }

    @Test
    void parenthesesAndCategoryMiddleDotsAreNotBlindlySplit() {
        var normalized = normalize(List.of(
                source("우대 사항", "관련 자격증(금융, IT·데이터) 보유자", 0)));

        assertThat(normalized).singleElement().satisfies(value -> {
            assertThat(value.text()).isEqualTo("관련 자격증(금융, IT·데이터) 보유자");
            assertThat(value.supportType()).isEqualTo(CriterionSupportType.CERTIFICATION);
        });
    }

    @Test
    void lineBreaksSplitAtomicConditionsWhileAmbiguousConnectorsStayIntact() {
        var normalized = normalize(List.of(
                source("우대 사항", "어학 우수자\n디지털 프로젝트 경험자", 0),
                source("우대 사항", "IT/데이터 관련 자격증 보유자", 1),
                source("지원 자격", "4년제 대학 또는 전문대학 졸업자", 2),
                source("우대 사항", "인턴십·대외활동 우수자", 3)));

        assertThat(normalized)
                .extracting(value -> value.text())
                .containsExactly(
                        "어학 우수자",
                        "디지털 프로젝트 경험자",
                        "IT/데이터 관련 자격증 보유자",
                        "4년제 대학 또는 전문대학 졸업자",
                        "인턴십·대외활동 우수자");
        assertThat(normalized)
                .filteredOn(value -> value.sourceOrdinal() == 0)
                .hasSize(2)
                .allSatisfy(value -> assertThat(value.sourceText())
                        .isEqualTo("어학 우수자\n디지털 프로젝트 경험자"));
    }

    private ProviderSourceRequirement source(String section, String text, int ordinal) {
        return new ProviderSourceRequirement(section, text, section, ordinal);
    }

    private List<JobAnalysisWorkflow.RequirementCandidate> normalize(
            List<ProviderSourceRequirement> sources) {
        List<JobSourceBlock> blocks = sources.stream()
                .map(source -> new JobSourceBlock(
                        source.sourceBlockId(),
                        section(source.sourceSection()),
                        source.sourceText(),
                        source.sourceOrdinal()))
                .toList();
        return policy.normalize(sources, blocks);
    }

    private RequirementSection section(String section) {
        if ("지원 자격".equals(section)) {
            return RequirementSection.REQUIRED_QUALIFICATION;
        }
        if ("우대 사항".equals(section)) {
            return RequirementSection.PREFERRED_QUALIFICATION;
        }
        if ("주요 업무".equals(section)) {
            return RequirementSection.RESPONSIBILITY;
        }
        return RequirementSection.OTHER;
    }
}
