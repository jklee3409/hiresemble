package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.AiQualityMode;
import com.hiresemble.agentrun.domain.model.ModelTier;
import com.hiresemble.agentrun.domain.model.WorkflowType;
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
    public static final String DOCUMENT_INGESTION_VERSION = "document-ingestion-v2";
    public static final String DOCUMENT_INGESTION_LEGACY_VERSION = VERSION;
    public static final String JOB_POSTING_EXTRACTION_VERSION =
            "job-posting-extraction-v3";
    public static final String JOB_POSTING_EXTRACTION_V2_VERSION =
            "job-posting-extraction-v2";
    public static final String JOB_POSTING_EXTRACTION_LEGACY_VERSION =
            "job-posting-extraction-v1";
    public static final String JOB_ANALYSIS_VERSION = "job-analysis-v1";
    public static final String COVER_LETTER_GENERATION_VERSION =
            "cover-letter-generation-v4";
    public static final String COVER_LETTER_GENERATION_V3_VERSION =
            "cover-letter-generation-v3";
    public static final String COVER_LETTER_GENERATION_V2_VERSION =
            "cover-letter-generation-v2";
    public static final String COVER_LETTER_GENERATION_LEGACY_VERSION =
            "cover-letter-generation-v1";
    public static final String COVER_LETTER_VERIFICATION_VERSION =
            "cover-letter-verification-v4";
    public static final String COVER_LETTER_VERIFICATION_V3_VERSION =
            "cover-letter-verification-v3";
    public static final String COVER_LETTER_VERIFICATION_V2_VERSION =
            "cover-letter-verification-v2";
    public static final String COVER_LETTER_VERIFICATION_LEGACY_VERSION =
            "cover-letter-verification-v1";
    public static final String INTERVIEW_PREPARATION_VERSION =
            "interview-preparation-v1";
    public static final String INTERVIEW_ANSWER_FEEDBACK_VERSION =
            "interview-answer-feedback-v1";
    private static final Set<FailureKind> RETRYABLE = EnumSet.of(
            FailureKind.RATE_LIMIT,
            FailureKind.PROVIDER_5XX,
            FailureKind.NETWORK,
            FailureKind.TIMEOUT,
            FailureKind.STRUCTURED_OUTPUT);

    private CanonicalWorkflowDefinitions() {}

    public static List<WorkflowDefinition> all() {
        return List.of(
                documentIngestion(),
                documentIngestionLegacy(),
                jobPostingExtraction(),
                jobPostingExtractionV2(),
                jobPostingExtractionLegacy(),
                jobAnalysis(),
                coverLetterGeneration(),
                coverLetterGenerationV3(),
                coverLetterGenerationV2(),
                coverLetterGenerationLegacy(),
                coverLetterVerification(),
                coverLetterVerificationV3(),
                coverLetterVerificationV2(),
                coverLetterVerificationLegacy(),
                interviewPreparation(),
                interviewAnswerFeedback(),
                definition(WorkflowType.MOCK_INTERVIEW_FEEDBACK, Set.of(AiQualityMode.BALANCED),
                        "LOAD_SESSION_SNAPSHOT", "ANALYZE_TURNS", "SYNTHESIZE_SESSION_FEEDBACK",
                        "VALIDATE_FEEDBACK", "PERSIST_FEEDBACK"));
    }

    private static WorkflowDefinition documentIngestion() {
        return definition(
                WorkflowType.DOCUMENT_INGESTION,
                DOCUMENT_INGESTION_VERSION,
                true,
                economyBalanced(),
                "LOAD_DOCUMENT_SOURCE",
                "EXTRACT_OR_ACCEPT_TEXT",
                "MASK_TEXT",
                "CHUNK_TEXT",
                "EMBED_CHUNKS",
                "EXTRACT_EVIDENCE_CANDIDATES",
                "EMBED_EVIDENCE_CANDIDATES",
                "APPLY_EVIDENCE_CANDIDATES",
                "FINALIZE_DOCUMENT");
    }

    private static WorkflowDefinition documentIngestionLegacy() {
        return definition(
                WorkflowType.DOCUMENT_INGESTION,
                DOCUMENT_INGESTION_LEGACY_VERSION,
                false,
                economyBalanced(),
                "LOAD_DOCUMENT_SOURCE",
                "EXTRACT_OR_ACCEPT_TEXT",
                "MASK_TEXT",
                "CHUNK_TEXT",
                "EMBED_CHUNKS",
                "EXTRACT_EVIDENCE_CANDIDATES",
                "APPLY_EVIDENCE_CANDIDATES",
                "FINALIZE_DOCUMENT");
    }

    private static WorkflowDefinition interviewPreparation() {
        String[] keys = {
            "VALIDATE_PREREQUISITES",
            "BUILD_PUBLIC_SEARCH_PLAN",
            "SEARCH_OFFICIAL_SOURCES",
            "SEARCH_INTERVIEW_SOURCES",
            "DEDUPE_CLASSIFY_SOURCES",
            "ASSESS_SOURCE_COVERAGE",
            "BUILD_QUESTION_CONTEXT",
            "GENERATE_QUESTIONS",
            "VALIDATE_QUESTION_PROVENANCE",
            "PERSIST_RESEARCH_AND_QUESTION_SET"
        };
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(keys.length);
        List<StepDefinition> steps = new ArrayList<>(keys.length);
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            boolean search = key.startsWith("SEARCH_");
            boolean generate = "GENERATE_QUESTIONS".equals(key);
            steps.add(new StepDefinition(
                    key,
                    agentName(key),
                    "interview-preparation-input-v1",
                    interviewPreparationOutputSchema(key),
                    search ? Set.of("WEB_SEARCH") : Set.of(),
                    search || generate ? 1 : 0,
                    1,
                    generate ? ModelTier.BALANCED : ModelTier.LOW_COST,
                    search || generate ? RETRYABLE : Set.of(),
                    weights.get(index)));
        }
        return new WorkflowDefinition(
                WorkflowType.INTERVIEW_PREPARATION,
                INTERVIEW_PREPARATION_VERSION,
                true,
                economyBalanced(),
                steps);
    }

    private static String interviewPreparationOutputSchema(String key) {
        if ("PERSIST_RESEARCH_AND_QUESTION_SET".equals(key)) {
            return "interview-persist-preparation-output-v1";
        }
        return "interview-"
                + key.toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                + "-output-v1";
    }

    private static WorkflowDefinition interviewAnswerFeedback() {
        String[] keys = {
            "LOAD_ANSWER_VERSION",
            "BUILD_FEEDBACK_CONTEXT",
            "ANALYZE_ANSWER",
            "VALIDATE_FEEDBACK",
            "PERSIST_FEEDBACK"
        };
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(keys.length);
        List<StepDefinition> steps = new ArrayList<>(keys.length);
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            boolean analyze = "ANALYZE_ANSWER".equals(key);
            steps.add(new StepDefinition(
                    key,
                    agentName(key),
                    "interview-feedback-input-v1",
                    "interview-" + key.toLowerCase().replace('_', '-') + "-output-v1",
                    Set.of(),
                    analyze ? 1 : 0,
                    1,
                    analyze ? ModelTier.BALANCED : ModelTier.LOW_COST,
                    analyze ? RETRYABLE : Set.of(),
                    weights.get(index)));
        }
        return new WorkflowDefinition(
                WorkflowType.INTERVIEW_ANSWER_FEEDBACK,
                INTERVIEW_ANSWER_FEEDBACK_VERSION,
                true,
                allQuality(),
                steps);
    }

    private static WorkflowDefinition definition(
            WorkflowType type, Set<AiQualityMode> allowedQuality, String... keys) {
        return definition(type, VERSION, true, allowedQuality, keys);
    }

    private static WorkflowDefinition definition(
            WorkflowType type,
            String version,
            Set<AiQualityMode> allowedQuality,
            String... keys) {
        return definition(type, version, true, allowedQuality, keys);
    }

    private static WorkflowDefinition definition(
            WorkflowType type,
            String version,
            boolean canonical,
            Set<AiQualityMode> allowedQuality,
            String... keys) {
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
                    : key.equals("EMBED_CHUNKS") || key.equals("EMBED_EVIDENCE_CANDIDATES")
                            ? Set.of("EMBEDDING")
                            : Set.of();
            int modelCalls = isModelStep(key) || !tools.isEmpty() ? 1 : 0;
            steps.add(new StepDefinition(
                    key,
                    agentName(key),
                    "input-v1",
                    type == WorkflowType.DOCUMENT_INGESTION
                                    && "EXTRACT_EVIDENCE_CANDIDATES".equals(key)
                            ? "document-evidence-provider-output-v2"
                            : type == WorkflowType.DOCUMENT_INGESTION
                                            && "EMBED_EVIDENCE_CANDIDATES".equals(key)
                                    ? "document-evidence-embedding-output-v1"
                            : type == WorkflowType.DOCUMENT_INGESTION
                                            && "APPLY_EVIDENCE_CANDIDATES".equals(key)
                                    ? DOCUMENT_INGESTION_VERSION.equals(version)
                                            ? "document-evidence-apply-output-v3"
                                            : "document-evidence-apply-output-v2"
                            : "output-v1",
                    tools,
                    modelCalls,
                    fanOut ? 20 : 1,
                    preferredTier(key),
                    modelCalls == 0 ? Set.of() : RETRYABLE,
                    weights.get(index)));
        }
        return new WorkflowDefinition(type, version, canonical, allowedQuality, steps);
    }

    private static WorkflowDefinition jobPostingExtraction() {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(9);
        return new WorkflowDefinition(
                WorkflowType.JOB_POSTING_EXTRACTION,
                JOB_POSTING_EXTRACTION_VERSION,
                true,
                economyBalanced(),
                List.of(
                        jobStep(
                                "FETCH_JOB_PAGE",
                                "job-fetch-input-v3",
                                "job-fetch-output-v3",
                                0,
                                EnumSet.of(
                                        FailureKind.PROVIDER_5XX,
                                        FailureKind.NETWORK,
                                        FailureKind.TIMEOUT),
                                weights.get(0)),
                        jobStep(
                                "INSPECT_JOB_PAGE",
                                "job-page-inspection-input-v3",
                                "job-page-inspection-output-v3",
                                0,
                                Set.of(),
                                weights.get(1)),
                        jobStep("FETCH_JOB_IMAGES", "job-images-fetch-input-v3",
                                "job-images-fetch-output-v3", 0,
                                EnumSet.of(FailureKind.PROVIDER_5XX, FailureKind.NETWORK, FailureKind.TIMEOUT),
                                weights.get(2)),
                        jobStep("EXTRACT_JOB_IMAGE_TEXT", "job-image-text-input-v3",
                                "job-image-text-output-v3", 1, RETRYABLE, weights.get(3)),
                        jobStep("COMPOSE_JOB_SOURCE_TEXT", "job-source-compose-input-v3",
                                "job-source-compose-output-v3", 0, Set.of(), weights.get(4)),
                        jobStep(
                                "EXTRACT_JOB_FIELDS",
                                "job-fields-input-v3",
                                "job-fields-output-v3",
                                1,
                                RETRYABLE,
                                weights.get(5)),
                        jobStep(
                                "MERGE_USER_OVERRIDES",
                                "job-merge-input-v3",
                                "job-merge-output-v3",
                                0,
                                Set.of(),
                                weights.get(6)),
                        jobStep("VALIDATE_JOB_EXTRACTION", "job-extraction-validation-input-v3",
                                "job-extraction-validation-output-v3", 0, Set.of(), weights.get(7)),
                        jobStep(
                                "APPLY_JOB_EXTRACTION",
                                "job-apply-input-v3",
                                "job-apply-output-v3",
                                0,
                                Set.of(),
                        weights.get(8))));
    }

    private static WorkflowDefinition jobPostingExtractionV2() {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(9);
        return new WorkflowDefinition(
                WorkflowType.JOB_POSTING_EXTRACTION,
                JOB_POSTING_EXTRACTION_V2_VERSION,
                false,
                economyBalanced(),
                List.of(
                        jobStep("FETCH_JOB_PAGE", "job-fetch-input-v1", "job-fetch-output-v2", 0,
                                EnumSet.of(FailureKind.PROVIDER_5XX, FailureKind.NETWORK, FailureKind.TIMEOUT), weights.get(0)),
                        jobStep("INSPECT_JOB_PAGE", "job-page-inspection-input-v2", "job-page-inspection-output-v2", 0, Set.of(), weights.get(1)),
                        jobStep("FETCH_JOB_IMAGES", "job-images-fetch-input-v2", "job-images-fetch-output-v2", 0,
                                EnumSet.of(FailureKind.PROVIDER_5XX, FailureKind.NETWORK, FailureKind.TIMEOUT), weights.get(2)),
                        jobStep("EXTRACT_JOB_IMAGE_TEXT", "job-image-text-input-v2", "job-image-text-output-v2", 1, RETRYABLE, weights.get(3)),
                        jobStep("COMPOSE_JOB_SOURCE_TEXT", "job-source-compose-input-v2", "job-source-compose-output-v2", 0, Set.of(), weights.get(4)),
                        jobStep("EXTRACT_JOB_FIELDS", "job-fields-input-v2", "job-fields-output-v2", 1, RETRYABLE, weights.get(5)),
                        jobStep("MERGE_USER_OVERRIDES", "job-merge-input-v1", "job-merge-output-v2", 0, Set.of(), weights.get(6)),
                        jobStep("VALIDATE_JOB_EXTRACTION", "job-extraction-validation-input-v2", "job-extraction-validation-output-v2", 0, Set.of(), weights.get(7)),
                        jobStep("APPLY_JOB_EXTRACTION", "job-apply-input-v1", "job-apply-output-v2", 0, Set.of(), weights.get(8))));
    }

    private static WorkflowDefinition jobPostingExtractionLegacy() {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(5);
        return new WorkflowDefinition(
                WorkflowType.JOB_POSTING_EXTRACTION,
                JOB_POSTING_EXTRACTION_LEGACY_VERSION,
                false,
                economyBalanced(),
                List.of(
                        jobStep("FETCH_JOB_PAGE", "job-fetch-input-v1", "job-fetch-output-v1", 0, Set.of(), weights.get(0)),
                        jobStep("SANITIZE_PAGE_TEXT", "job-sanitize-input-v1", "job-sanitize-output-v1", 0, Set.of(), weights.get(1)),
                        jobStep("EXTRACT_JOB_FIELDS", "job-fields-input-v1", "job-fields-output-v1", 1, RETRYABLE, weights.get(2)),
                        jobStep("MERGE_USER_OVERRIDES", "job-merge-input-v1", "job-merge-output-v1", 0, Set.of(), weights.get(3)),
                        jobStep("APPLY_JOB_EXTRACTION", "job-apply-input-v1", "job-apply-output-v1", 0, Set.of(), weights.get(4))));
    }

    private static WorkflowDefinition jobAnalysis() {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(8);
        return new WorkflowDefinition(
                WorkflowType.JOB_ANALYSIS,
                JOB_ANALYSIS_VERSION,
                true,
                economyBalanced(),
                List.of(
                        analysisStep(
                                "BUILD_JOB_SNAPSHOT",
                                "job-analysis-build-output-v1",
                                Set.of(),
                                0,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(0)),
                        analysisStep(
                                "EXTRACT_REQUIREMENTS",
                                "job-analysis-requirements-source-output-v6",
                                Set.of(),
                                1,
                                RETRYABLE,
                                ModelTier.LOW_COST,
                                weights.get(1)),
                        analysisStep(
                                "ASSESS_ELIGIBILITY",
                                "job-analysis-eligibility-output-v3",
                                Set.of(),
                                1,
                                RETRYABLE,
                                ModelTier.BALANCED,
                                weights.get(2)),
                        analysisStep(
                                "RETRIEVE_VERIFIED_EVIDENCE",
                                "job-analysis-retrieval-output-v2",
                                Set.of("EMBEDDING"),
                                1,
                                RETRYABLE,
                                ModelTier.LOW_COST,
                                weights.get(3)),
                        analysisStep(
                                "MATCH_EVIDENCE",
                                "job-analysis-match-output-v3",
                                Set.of(),
                                1,
                                RETRYABLE,
                                ModelTier.BALANCED,
                                weights.get(4)),
                        analysisStep(
                                "SCORE_FIT",
                                "job-analysis-score-output-v2",
                                Set.of(),
                                0,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(5)),
                        analysisStep(
                                "VALIDATE_ANALYSIS",
                                "job-analysis-validation-output-v1",
                                Set.of(),
                                0,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(6)),
                        analysisStep(
                                "PERSIST_ANALYSIS",
                                "job-analysis-persist-output-v1",
                                Set.of(),
                                0,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(7))));
    }

    private static WorkflowDefinition coverLetterGeneration() {
        return coverLetterGeneration(
                COVER_LETTER_GENERATION_VERSION,
                true,
                "cover-letter-input-v4",
                3);
    }

    private static WorkflowDefinition coverLetterGenerationV3() {
        return coverLetterGeneration(
                COVER_LETTER_GENERATION_V3_VERSION,
                false,
                "cover-letter-input-v3",
                3);
    }

    private static WorkflowDefinition coverLetterGenerationV2() {
        return coverLetterGeneration(
                COVER_LETTER_GENERATION_V2_VERSION,
                false,
                "cover-letter-input-v2",
                2);
    }

    private static WorkflowDefinition coverLetterGenerationLegacy() {
        return coverLetterGeneration(
                COVER_LETTER_GENERATION_LEGACY_VERSION,
                false,
                "cover-letter-input-v1",
                1);
    }

    private static WorkflowDefinition coverLetterGeneration(
            String version, boolean canonical, String inputSchemaVersion, int generation) {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(8);
        return new WorkflowDefinition(
                WorkflowType.COVER_LETTER_GENERATION,
                version,
                canonical,
                allQuality(),
                List.of(
                        coverLetterStep(
                                "BUILD_GENERATION_CONTEXT",
                                "cover-generation-build-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                0,
                                1,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(0)),
                        coverLetterStep(
                                "PLAN_QUESTIONS",
                                generation == 3 ? "cover-generation-plan-output-v3"
                                        : generation == 2 ? "cover-generation-plan-output-v2"
                                                : "cover-generation-plan-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                1,
                                RETRYABLE,
                                ModelTier.LOW_COST,
                                weights.get(1)),
                        coverLetterStep(
                                "ANALYZE_QUESTION",
                                generation == 3 ? "cover-generation-question-analysis-output-v3"
                                        : generation == 2 ? "cover-generation-question-analysis-output-v2"
                                                : "cover-generation-question-analysis-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                20,
                                RETRYABLE,
                                ModelTier.LOW_COST,
                                weights.get(2)),
                        coverLetterStep(
                                "RETRIEVE_EVIDENCE",
                                "cover-generation-retrieval-output-v1",
                                inputSchemaVersion,
                                Set.of("EMBEDDING"),
                                1,
                                20,
                                RETRYABLE,
                                ModelTier.LOW_COST,
                                weights.get(3)),
                        coverLetterStep(
                                "ALLOCATE_EXPERIENCES",
                                generation >= 2 ? "cover-generation-allocation-output-v2" : "cover-generation-allocation-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                1,
                                RETRYABLE,
                                ModelTier.LOW_COST,
                                weights.get(4)),
                        coverLetterStep(
                                "WRITE_ANSWER",
                                generation == 3 ? "cover-generation-answer-output-v3"
                                        : generation == 2 ? "cover-generation-answer-output-v2"
                                                : "cover-generation-answer-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                20,
                                RETRYABLE,
                                ModelTier.BALANCED,
                                weights.get(5)),
                        coverLetterStep(
                                "FACT_CHECK_ANSWER",
                                generation == 3 ? "cover-generation-fact-check-output-v3"
                                        : generation == 2 ? "cover-generation-fact-check-output-v2"
                                                : "cover-generation-fact-check-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                20,
                                RETRYABLE,
                                ModelTier.BALANCED,
                                weights.get(6)),
                        coverLetterStep(
                                "APPLY_ANSWER_VERSION",
                                "cover-generation-apply-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                0,
                                20,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(7))));
    }

    private static WorkflowDefinition coverLetterVerification() {
        return coverLetterVerification(
                COVER_LETTER_VERIFICATION_VERSION,
                true,
                "cover-letter-input-v4",
                3);
    }

    private static WorkflowDefinition coverLetterVerificationV3() {
        return coverLetterVerification(
                COVER_LETTER_VERIFICATION_V3_VERSION,
                false,
                "cover-letter-input-v3",
                3);
    }

    private static WorkflowDefinition coverLetterVerificationV2() {
        return coverLetterVerification(
                COVER_LETTER_VERIFICATION_V2_VERSION,
                false,
                "cover-letter-input-v2",
                2);
    }

    private static WorkflowDefinition coverLetterVerificationLegacy() {
        return coverLetterVerification(
                COVER_LETTER_VERIFICATION_LEGACY_VERSION,
                false,
                "cover-letter-input-v1",
                1);
    }

    private static WorkflowDefinition coverLetterVerification(
            String version, boolean canonical, String inputSchemaVersion, int generation) {
        List<BigDecimal> weights = WorkflowRegistry.distributedWeights(6);
        return new WorkflowDefinition(
                WorkflowType.COVER_LETTER_VERIFICATION,
                version,
                canonical,
                allQuality(),
                List.of(
                        coverLetterStep(
                                "LOAD_ANSWER_VERSION",
                                "cover-verification-load-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                0,
                                1,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(0)),
                        coverLetterStep(
                                "BUILD_PROVENANCE_CONTEXT",
                                "cover-verification-provenance-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                0,
                                1,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(1)),
                        coverLetterStep(
                                "CHECK_FACTS",
                                generation == 3 ? "cover-verification-facts-output-v3"
                                        : generation == 2 ? "cover-verification-facts-output-v2"
                                                : "cover-verification-facts-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                1,
                                RETRYABLE,
                                ModelTier.BALANCED,
                                weights.get(2)),
                        coverLetterStep(
                                "CHECK_REQUIREMENTS_AND_LENGTH",
                                generation == 3 ? "cover-verification-requirements-output-v3"
                                        : generation == 2 ? "cover-verification-requirements-output-v2"
                                                : "cover-verification-requirements-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                1,
                                1,
                                RETRYABLE,
                                ModelTier.BALANCED,
                                weights.get(3)),
                        coverLetterStep(
                                "AGGREGATE_VERIFICATION",
                                "cover-verification-aggregate-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                0,
                                1,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(4)),
                        coverLetterStep(
                                "PERSIST_VERIFICATION",
                                "cover-verification-persist-output-v1",
                                inputSchemaVersion,
                                Set.of(),
                                0,
                                1,
                                Set.of(),
                                ModelTier.LOW_COST,
                                weights.get(5))));
    }

    private static StepDefinition coverLetterStep(
            String key,
            String outputSchemaVersion,
            String inputSchemaVersion,
            Set<String> tools,
            int modelCalls,
            int maxFanOut,
            Set<FailureKind> retryableFailures,
            ModelTier preferredTier,
            BigDecimal weight) {
        return new StepDefinition(
                key,
                agentName(key),
                inputSchemaVersion,
                outputSchemaVersion,
                tools,
                modelCalls,
                maxFanOut,
                preferredTier,
                retryableFailures,
                weight);
    }

    private static StepDefinition analysisStep(
            String key,
            String outputSchemaVersion,
            Set<String> tools,
            int modelCalls,
            Set<FailureKind> retryableFailures,
            ModelTier preferredTier,
            BigDecimal weight) {
        return new StepDefinition(
                key,
                agentName(key),
                "job-analysis-input-v1",
                outputSchemaVersion,
                tools,
                modelCalls,
                1,
                preferredTier,
                retryableFailures,
                weight);
    }

    private static StepDefinition jobStep(
            String key,
            String inputSchemaVersion,
            String outputSchemaVersion,
            int modelCalls,
            Set<FailureKind> retryableFailures,
            BigDecimal weight) {
        return new StepDefinition(
                key,
                agentName(key),
                inputSchemaVersion,
                outputSchemaVersion,
                Set.of(),
                modelCalls,
                1,
                ModelTier.LOW_COST,
                retryableFailures,
                weight);
    }

    private static boolean isModelStep(String key) {
        if (key.equals("EXTRACT_OR_ACCEPT_TEXT")) {
            return false;
        }
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
