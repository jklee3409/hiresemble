package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.AiQualityMode;
import com.hiresemble.agentrun.domain.ModelTier;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowRegistry.StepDefinition;
import com.hiresemble.ai.workflow.WorkflowRegistry.WorkflowDefinition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Canonical P0 workflow contracts. These definitions deliberately have no executable handlers. */
public final class CanonicalWorkflowDefinitions {

    public static final String VERSION = "p0-contract-v1";
    private static final Set<FailureKind> RETRYABLE = EnumSet.of(
            FailureKind.RATE_LIMIT,
            FailureKind.PROVIDER_5XX,
            FailureKind.NETWORK,
            FailureKind.TIMEOUT,
            FailureKind.STRUCTURED_OUTPUT);

    private CanonicalWorkflowDefinitions() {}

    public static List<WorkflowDefinition> all() {
        return List.of(
                definition(WorkflowType.DOCUMENT_INGESTION, economyBalanced(),
                        "LOAD_DOCUMENT_SOURCE", "EXTRACT_OR_ACCEPT_TEXT", "MASK_TEXT", "CHUNK_TEXT",
                        "EMBED_CHUNKS", "EXTRACT_EVIDENCE_CANDIDATES", "APPLY_EVIDENCE_CANDIDATES",
                        "FINALIZE_DOCUMENT"),
                definition(WorkflowType.JOB_POSTING_EXTRACTION, economyBalanced(),
                        "FETCH_JOB_PAGE", "SANITIZE_PAGE_TEXT", "EXTRACT_JOB_FIELDS",
                        "MERGE_USER_OVERRIDES", "APPLY_JOB_EXTRACTION"),
                definition(WorkflowType.JOB_ANALYSIS, economyBalanced(),
                        "BUILD_JOB_SNAPSHOT", "EXTRACT_REQUIREMENTS", "ASSESS_ELIGIBILITY",
                        "RETRIEVE_VERIFIED_EVIDENCE", "MATCH_EVIDENCE", "SCORE_FIT",
                        "VALIDATE_ANALYSIS", "PERSIST_ANALYSIS"),
                definition(WorkflowType.COVER_LETTER_GENERATION, allQuality(),
                        "BUILD_GENERATION_CONTEXT", "PLAN_QUESTIONS", "ANALYZE_QUESTION",
                        "RETRIEVE_EVIDENCE", "ALLOCATE_EXPERIENCES", "WRITE_ANSWER",
                        "FACT_CHECK_ANSWER", "APPLY_ANSWER_VERSION"),
                definition(WorkflowType.COVER_LETTER_VERIFICATION, allQuality(),
                        "LOAD_ANSWER_VERSION", "BUILD_PROVENANCE_CONTEXT", "CHECK_FACTS",
                        "CHECK_REQUIREMENTS_AND_LENGTH", "AGGREGATE_VERIFICATION",
                        "PERSIST_VERIFICATION"),
                definition(WorkflowType.INTERVIEW_PREPARATION, economyBalanced(),
                        "VALIDATE_PREREQUISITES", "BUILD_PUBLIC_SEARCH_PLAN", "SEARCH_OFFICIAL_SOURCES",
                        "SEARCH_INTERVIEW_SOURCES", "DEDUPE_CLASSIFY_SOURCES", "ASSESS_SOURCE_COVERAGE",
                        "BUILD_QUESTION_CONTEXT", "GENERATE_QUESTIONS",
                        "VALIDATE_QUESTION_PROVENANCE", "PERSIST_RESEARCH_AND_QUESTION_SET"),
                definition(WorkflowType.INTERVIEW_ANSWER_FEEDBACK, allQuality(),
                        "LOAD_ANSWER_VERSION", "BUILD_FEEDBACK_CONTEXT", "ANALYZE_ANSWER",
                        "VALIDATE_FEEDBACK", "PERSIST_FEEDBACK"),
                definition(WorkflowType.MOCK_INTERVIEW_FEEDBACK, Set.of(AiQualityMode.BALANCED),
                        "LOAD_SESSION_SNAPSHOT", "ANALYZE_TURNS", "SYNTHESIZE_SESSION_FEEDBACK",
                        "VALIDATE_FEEDBACK", "PERSIST_FEEDBACK"));
    }

    private static WorkflowDefinition definition(
            WorkflowType type, Set<AiQualityMode> allowedQuality, String... keys) {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(keys.length);
        List<StepDefinition> steps = new ArrayList<>(keys.length);
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            boolean fanOut = switch (key) {
                case "ANALYZE_QUESTION", "RETRIEVE_EVIDENCE", "WRITE_ANSWER",
                        "FACT_CHECK_ANSWER", "APPLY_ANSWER_VERSION" -> true;
                default -> false;
            };
            Set<String> tools = key.startsWith("SEARCH_")
                    ? Set.of("WEB_SEARCH")
                    : key.equals("EMBED_CHUNKS") ? Set.of("EMBEDDING") : Set.of();
            int modelCalls = isModelStep(key) || !tools.isEmpty() ? 1 : 0;
            steps.add(new StepDefinition(
                    key,
                    agentName(key),
                    "input-v1",
                    "output-v1",
                    tools,
                    modelCalls,
                    fanOut ? 20 : 1,
                    preferredTier(key),
                    modelCalls == 0 ? Set.of() : RETRYABLE,
                    weights.get(index)));
        }
        return new WorkflowDefinition(type, VERSION, true, allowedQuality, steps);
    }

    private static boolean isModelStep(String key) {
        return key.contains("EXTRACT_")
                || key.contains("ANALYZE_")
                || key.contains("ASSESS_")
                || key.contains("MATCH_")
                || key.contains("SCORE_")
                || key.contains("PLAN_")
                || key.contains("WRITE_")
                || key.contains("CHECK_")
                || key.contains("AGGREGATE_")
                || key.contains("GENERATE_")
                || key.contains("SYNTHESIZE_");
    }

    private static ModelTier preferredTier(String key) {
        return key.contains("WRITE_") || key.contains("SYNTHESIZE_")
                || key.contains("ANALYZE_") || key.contains("CHECK_")
                ? ModelTier.BALANCED : ModelTier.LOW_COST;
    }

    private static String agentName(String key) {
        String[] parts = key.toLowerCase().split("_");
        StringBuilder result = new StringBuilder("fixed");
        for (String part : parts) {
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.append("Agent").toString();
    }

    private static Set<AiQualityMode> economyBalanced() {
        return EnumSet.of(AiQualityMode.ECONOMY, AiQualityMode.BALANCED);
    }

    private static Set<AiQualityMode> allQuality() {
        return EnumSet.allOf(AiQualityMode.class);
    }
}
