package com.hiresemble.ai.workflow.careerartifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.model.OpenAiChatModels;
import com.hiresemble.ai.prompt.CareerArtifactPromptDefinitions;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.careerartifact.application.CareerArtifactWorkflowPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CareerArtifactWorkflowContractTest {

    private static final List<String> RESUME_STEPS = List.of(
            "LOAD_RESUME_REQUEST",
            "BUILD_VERIFIED_CAREER_CONTEXT",
            "PLAN_RESUME",
            "DRAFT_RESUME_CONTENT",
            "FACT_CHECK_RESUME_CONTENT",
            "RENDER_DOCX",
            "VALIDATE_DOCX",
            "PERSIST_RESUME_VERSION");
    private static final List<String> PORTFOLIO_STEPS = List.of(
            "LOAD_PORTFOLIO_REQUEST",
            "BUILD_VERIFIED_CAREER_CONTEXT",
            "PLAN_PORTFOLIO_STORY",
            "DRAFT_PORTFOLIO_SLIDES",
            "FACT_CHECK_PORTFOLIO_CONTENT",
            "RENDER_PPTX",
            "VALIDATE_PPTX",
            "PERSIST_PORTFOLIO_VERSION");
    private static final Set<String> PROVIDER_STEPS = Set.of(
            "PLAN_RESUME",
            "DRAFT_RESUME_CONTENT",
            "FACT_CHECK_RESUME_CONTENT",
            "PLAN_PORTFOLIO_STORY",
            "DRAFT_PORTFOLIO_SLIDES",
            "FACT_CHECK_PORTFOLIO_CONTENT");

    @Test
    void canonicalDefinitionsHaveTheTwoExactEightStepToolFreeSequences() {
        assertDefinition(
                WorkflowType.RESUME_GENERATION,
                CanonicalWorkflowDefinitions.RESUME_GENERATION_VERSION,
                RESUME_STEPS);
        assertDefinition(
                WorkflowType.PORTFOLIO_GENERATION,
                CanonicalWorkflowDefinitions.PORTFOLIO_GENERATION_VERSION,
                PORTFOLIO_STEPS);
    }

    @Test
    void executableContributionsMatchCanonicalOrderAndUseExplicitRecordContracts() {
        CareerArtifactGenerationWorkflow workflow = new CareerArtifactGenerationWorkflow(
                mock(CareerArtifactWorkflowPort.class), new ObjectMapper());
        assertContribution(workflow.resumeContribution(), RESUME_STEPS);
        assertContribution(workflow.portfolioContribution(), PORTFOLIO_STEPS);
    }

    @Test
    void promptsFixPortfolioInterviewPolicyWithoutSnapshottingTheWholeString() {
        List<PromptDefinition> prompts = CareerArtifactPromptDefinitions.all();
        assertThat(prompts).hasSize(16);
        prompts.forEach(prompt -> {
            assertThat(prompt.toolAllowlist()).isEmpty();
            assertThat(prompt.maxModelCalls())
                    .isEqualTo(PROVIDER_STEPS.contains(prompt.key().stepKey()) ? 1 : 0);
            assertThat(prompt.outputType()).isNotEqualTo(Object.class);
            assertThat(prompt.outputType()).isNotEqualTo(Map.class);
            assertThat(prompt.outputType().isRecord()).isTrue();
        });

        String portfolio = prompts.stream()
                .filter(prompt -> prompt.key().workflowType()
                        == WorkflowType.PORTFOLIO_GENERATION)
                .map(PromptDefinition::instructions)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(portfolio)
                .contains("채용 담당자와 면접관", "첫 60초")
                .contains("문제 → 내 역할 → 행동 → 기술적 판단 → 결과 → 드러난 강점")
                .contains("강점마다 최소 하나의 승인 근거")
                .contains("기술 stack 단순 나열 금지")
                .contains("사용 맥락과 기술적 의사결정")
                .contains("한 slide 한 핵심 message")
                .contains("긴 문단, 작은 글자, 과도한 장식")
                .contains("근거 없는 성과 수치")
                .contains("고유명사와 기술 용어", "자연스러운 한국어")
                .contains("생략하거나 warning", "창작하지 않는다")
                .contains("좌표, font, 색상, OOXML, image URL")
                .contains("evidence reference의 ID와 title은 제공된 값을 그대로 복사");
    }

    @Test
    void resumeAndPortfolioExposeTheSameUnchangedExactModelCatalogAsCoverLetters() {
        assertThat(OpenAiChatModels.modelsFor(WorkflowType.RESUME_GENERATION))
                .containsExactlyElementsOf(OpenAiChatModels.coverLetterModels());
        assertThat(OpenAiChatModels.modelsFor(WorkflowType.PORTFOLIO_GENERATION))
                .containsExactlyElementsOf(OpenAiChatModels.coverLetterModels());
        assertThat(OpenAiChatModels.modelsFor(WorkflowType.RESUME_GENERATION))
                .filteredOn(OpenAiChatModels.Model::recommended)
                .singleElement()
                .extracting(OpenAiChatModels.Model::id)
                .isEqualTo(OpenAiChatModels.RECOMMENDED);
    }

    private void assertDefinition(
            WorkflowType type, String version, List<String> expectedSteps) {
        var definition = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == type && value.version().equals(version))
                .findFirst()
                .orElseThrow();
        assertThat(definition.canonical()).isTrue();
        assertThat(definition.steps()).extracting(StepDefinition::stepKey)
                .containsExactlyElementsOf(expectedSteps);
        definition.steps().forEach(step -> {
            boolean provider = PROVIDER_STEPS.contains(step.stepKey());
            assertThat(step.maxModelCalls()).isEqualTo(provider ? 1 : 0);
            assertThat(step.toolAllowlist()).isEmpty();
            if (provider) {
                assertThat(step.retryableFailures())
                        .contains(FailureKind.STRUCTURED_OUTPUT)
                        .allMatch(FailureKind::automaticallyRetryable);
            } else {
                assertThat(step.retryableFailures()).isEmpty();
            }
        });
    }

    private void assertContribution(
            ExecutableWorkflowContribution contribution, List<String> expectedSteps) {
        assertThat(contribution.steps()).extracting(value -> value.stepKey())
                .containsExactlyElementsOf(expectedSteps);
        contribution.steps().forEach(step -> {
            assertThat(step.executor().reusable()).isFalse();
            Class<?> output = step.executor().outputContract().javaType();
            assertThat(output.isRecord()).isTrue();
            assertThat(output).isNotEqualTo(Object.class).isNotEqualTo(Map.class);
        });
    }
}
