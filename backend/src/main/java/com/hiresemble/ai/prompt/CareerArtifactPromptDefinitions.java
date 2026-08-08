package com.hiresemble.ai.prompt;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.prompt.PromptRegistry.PromptKey;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.ai.workflow.careerartifact.CareerArtifactGenerationWorkflow;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioFactCheckResult;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.PortfolioPlan;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeContent;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumeFactCheckResult;
import com.hiresemble.careerartifact.domain.CareerArtifactContent.ResumePlan;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Versioned, tool-free system contracts for grounded Career Artifact generation. */
public final class CareerArtifactPromptDefinitions {

    public static final String RESUME_PROMPT_VERSION = "resume-generation-prompt-v1";
    public static final String PORTFOLIO_PROMPT_VERSION = "portfolio-generation-prompt-v1";

    private static final String PORTFOLIO_SYSTEM_CONTRACT = """
            독자는 채용 담당자와 면접관이다. 첫 60초 안에 지원자의 역할, 주요 강점,
            핵심 프로젝트를 파악할 수 있게 구성한다. case study는 반드시
            문제 → 내 역할 → 행동 → 기술적 판단 → 결과 → 드러난 강점 순서를 따른다.
            강점마다 최소 하나의 승인 근거를 연결한다. 기술 stack 단순 나열 금지이며,
            사용 맥락과 기술적 의사결정을 강조한다. 한 slide 한 핵심 message만 둔다.
            긴 문단, 작은 글자, 과도한 장식은 금지한다. 근거 없는 성과 수치를 만들지
            않는다. 고유명사와 기술 용어를 보존하고 자연스러운 한국어를 사용한다.
            source가 부족하면 해당 내용을 생략하거나 warning을 반환하고 창작하지 않는다.
            좌표, font, 색상, OOXML, image URL, 외부 asset 지시는 절대 출력하지 않는다.
            """;

    private CareerArtifactPromptDefinitions() {}

    public static List<PromptDefinition> all() {
        List<PromptDefinition> values = new ArrayList<>();
        values.addAll(forWorkflow(
                WorkflowType.RESUME_GENERATION,
                CanonicalWorkflowDefinitions.RESUME_GENERATION_VERSION));
        values.addAll(forWorkflow(
                WorkflowType.PORTFOLIO_GENERATION,
                CanonicalWorkflowDefinitions.PORTFOLIO_GENERATION_VERSION));
        return List.copyOf(values);
    }

    private static List<PromptDefinition> forWorkflow(
            WorkflowType type, String version) {
        var workflow = CanonicalWorkflowDefinitions.all().stream()
                .filter(value -> value.type() == type && version.equals(value.version()))
                .findFirst()
                .orElseThrow();
        return workflow.steps().stream()
                .map(step -> definition(type, version, step))
                .toList();
    }

    private static PromptDefinition definition(
            WorkflowType type, String version, StepDefinition step) {
        boolean provider = step.requiresProvider();
        return new PromptDefinition(
                new PromptKey(type, version, step.stepKey()),
                type == WorkflowType.RESUME_GENERATION
                        ? RESUME_PROMPT_VERSION : PORTFOLIO_PROMPT_VERSION,
                JsonNode.class,
                outputType(step.stepKey()),
                step.outputSchemaVersion(),
                step.toolAllowlist(),
                provider ? 32_000 : 2_000,
                provider ? 12_000 : 1_000,
                step.maxModelCalls(),
                instructions(type, step.stepKey()));
    }

    private static Class<?> outputType(String stepKey) {
        return switch (stepKey) {
            case CareerArtifactGenerationWorkflow.LOAD_RESUME_REQUEST,
                    CareerArtifactGenerationWorkflow.LOAD_PORTFOLIO_REQUEST ->
                    CareerArtifactGenerationWorkflow.RequestLoadedOutput.class;
            case CareerArtifactGenerationWorkflow.BUILD_VERIFIED_CAREER_CONTEXT ->
                    CareerArtifactGenerationWorkflow.ContextBuiltOutput.class;
            case CareerArtifactGenerationWorkflow.PLAN_RESUME -> ResumePlan.class;
            case CareerArtifactGenerationWorkflow.DRAFT_RESUME_CONTENT -> ResumeContent.class;
            case CareerArtifactGenerationWorkflow.FACT_CHECK_RESUME_CONTENT ->
                    ResumeFactCheckResult.class;
            case CareerArtifactGenerationWorkflow.PLAN_PORTFOLIO_STORY -> PortfolioPlan.class;
            case CareerArtifactGenerationWorkflow.DRAFT_PORTFOLIO_SLIDES ->
                    PortfolioContent.class;
            case CareerArtifactGenerationWorkflow.FACT_CHECK_PORTFOLIO_CONTENT ->
                    PortfolioFactCheckResult.class;
            case CareerArtifactGenerationWorkflow.RENDER_DOCX,
                    CareerArtifactGenerationWorkflow.RENDER_PPTX ->
                    CareerArtifactGenerationWorkflow.RenderOutput.class;
            case CareerArtifactGenerationWorkflow.VALIDATE_DOCX,
                    CareerArtifactGenerationWorkflow.VALIDATE_PPTX ->
                    CareerArtifactGenerationWorkflow.ValidateOutput.class;
            case CareerArtifactGenerationWorkflow.PERSIST_RESUME_VERSION,
                    CareerArtifactGenerationWorkflow.PERSIST_PORTFOLIO_VERSION ->
                    CareerArtifactGenerationWorkflow.PersistOutput.class;
            default -> throw new IllegalArgumentException("unknown Career Artifact step");
        };
    }

    private static String instructions(WorkflowType type, String stepKey) {
        if (type == WorkflowType.PORTFOLIO_GENERATION) {
            return PORTFOLIO_SYSTEM_CONTRACT + switch (stepKey) {
                case CareerArtifactGenerationWorkflow.PLAN_PORTFOLIO_STORY -> """
                        승인된 canonical experience와 선택된 비연락 profile section만 사용해
                        6~12 slide의 면접관 중심 story plan을 만든다. 허용 evidence ID만 참조한다.
                        """;
                case CareerArtifactGenerationWorkflow.DRAFT_PORTFOLIO_SLIDES -> """
                        plan을 6~12개의 strict slide record로 작성한다. 허용 slideType과
                        visualType만 쓰고 모든 긍정 claim에 승인 evidence reference를 둔다.
                        evidence reference의 ID와 title은 제공된 값을 그대로 복사한다.
                        """;
                case CareerArtifactGenerationWorkflow.FACT_CHECK_PORTFOLIO_CONTENT -> """
                        승인 source와 draft를 대조해 renderer가 사용할 최종 groundedDraft를
                        반환한다. issue와 warning은 안전한 정보만 포함하고 boolean 승인으로
                        대체하지 않는다. 숫자, 날짜, 조직, 역할을 source 밖에서 만들지 않는다.
                        """;
                default -> """
                        이 단계는 server-owned local render, validation 또는 persistence다.
                        model이나 tool을 호출하지 않고 reference-only 결과만 반환한다.
                        """;
            };
        }
        return switch (stepKey) {
            case CareerArtifactGenerationWorkflow.PLAN_RESUME -> """
                    Use only approved canonical experiences and selected non-contact profile data.
                    Plan a concise ATS-friendly Korean resume. Preserve proper nouns and technical
                    terms, connect every proposed claim to an allowed evidence reference, and return
                    a warning instead of inventing missing material. No tools are available.
                    """;
            case CareerArtifactGenerationWorkflow.DRAFT_RESUME_CONTENT -> """
                    Produce the strict ResumeContent record from the approved plan and context.
                    Every positive bullet must cite an allowed evidence reference. Copy numbers,
                    dates, organizations, and roles only when explicitly supported. Do not include
                    contact data, layout coordinates, images, charts, text boxes, links, or OOXML.
                    Copy each evidence reference ID and title exactly from the supplied context.
                    """;
            case CareerArtifactGenerationWorkflow.FACT_CHECK_RESUME_CONTENT -> """
                    Return a final groundedDraft plus safe issues and warnings, not an approval
                    boolean. Remove or correct unsupported claims without inventing replacements.
                    Every retained positive bullet must have an allowed evidence reference.
                    """;
            default -> """
                    Execute only the fixed server-owned Career Artifact step. Do not call a model
                    or tool and return only bounded references, hashes, counts, or file metadata.
                    """;
        };
    }
}
