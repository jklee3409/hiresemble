package com.hiresemble.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.NarrativeFramework;
import com.hiresemble.ai.workflow.CoverLetterGenerationWorkflow.VerificationIssueKind;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.ClaimType;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionPlan;
import com.hiresemble.ai.workflow.CoverLetterWorkflowV3Policy.NarrativeSectionType;
import com.hiresemble.coverletter.domain.IssueSeverity;
import com.hiresemble.coverletter.domain.VerificationIssueCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoverLetterWorkflowV3PolicyTest {

    @Test
    void motivationUsesFrameworkNeutralSectionsAndRejectsStarOrWrongWeights() {
        CoverLetterWorkflowV3Policy.validateQuestionFramework(
                CoverLetterGenerationWorkflow.QuestionType.MOTIVATION,
                NarrativeFramework.MOTIVATION_CONNECTION);
        CoverLetterWorkflowV3Policy.validateSections(
                NarrativeFramework.MOTIVATION_CONNECTION,
                List.of(
                        section(NarrativeSectionType.COMPANY_REASON, 25),
                        section(NarrativeSectionType.ROLE_REASON, 25),
                        section(NarrativeSectionType.EXPERIENCE_CONNECTION, 25),
                        section(NarrativeSectionType.CONTRIBUTION, 25)));

        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateSections(
                        NarrativeFramework.MOTIVATION_CONNECTION,
                        List.of(
                                section(NarrativeSectionType.SITUATION, 50),
                                section(NarrativeSectionType.ACTION, 50))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateQuestionFramework(
                        CoverLetterGenerationWorkflow.QuestionType.MOTIVATION,
                        NarrativeFramework.TECHNICAL_DECISION_TRADEOFF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateSections(
                        NarrativeFramework.MOTIVATION_CONNECTION,
                        List.of(
                                section(NarrativeSectionType.COMPANY_REASON, 60),
                                section(NarrativeSectionType.ROLE_REASON, 30))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void technicalFrameworkRequiresDecisionAndTradeoff() {
        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateSections(
                        NarrativeFramework.TECHNICAL_DECISION_TRADEOFF,
                        List.of(
                                section(NarrativeSectionType.PROBLEM, 50),
                                section(NarrativeSectionType.RESULT, 50))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision and tradeoff");
    }

    @Test
    void boundedTextCarriesCountsTruncationTailAndStableFullHash() {
        String full = "가".repeat(4_500) + "TAIL";
        var bounded = CoverLetterWorkflowV3Policy.bound(full, 4_000);

        assertThat(bounded.originalCharacterCount()).isEqualTo(4_504);
        assertThat(bounded.providedCharacterCount()).isEqualTo(4_000);
        assertThat(bounded.truncated()).isTrue();
        assertThat(bounded.fullTextHash()).hasSize(64);
        assertThat(bounded.boundedPlainText()).endsWith("TAIL");
        assertThat(CoverLetterWorkflowV3Policy.bound(full, 4_000).fullTextHash())
                .isEqualTo(bounded.fullTextHash());
    }

    @Test
    void claimMustBeAnExactNormalizedAnswerExcerptAndCannotDuplicate() {
        UUID evidence = UUID.randomUUID();
        String answer = "Spring Boot로 안정적인 API를 구현했습니다.";
        var valid = new TestClaim(evidence, "안정적인 API를 구현했습니다.", ClaimType.ACHIEVEMENT);

        CoverLetterWorkflowV3Policy.validateDistinctClaims(List.of(valid), answer);
        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateDistinctClaims(
                        List.of(new TestClaim(evidence, "답변에 없는 성과", ClaimType.ACHIEVEMENT)),
                        answer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateDistinctClaims(
                        List.of(valid, valid), answer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issueKindCodeSeverityMatrixRejectsMeaningDrift() {
        CoverLetterWorkflowV3Policy.validateIssueCompatibility(
                VerificationIssueKind.QUALITY,
                VerificationIssueCode.OTHER,
                IssueSeverity.WARNING);

        assertIncompatible(
                VerificationIssueKind.QUALITY,
                VerificationIssueCode.CONTRADICTION,
                IssueSeverity.WARNING);
        assertIncompatible(
                VerificationIssueKind.DUPLICATION,
                VerificationIssueCode.UNVERIFIED_CLAIM,
                IssueSeverity.WARNING);
        assertIncompatible(
                VerificationIssueKind.FACTUAL,
                VerificationIssueCode.OTHER,
                IssueSeverity.WARNING);
        assertIncompatible(
                VerificationIssueKind.QUALITY,
                VerificationIssueCode.OTHER,
                IssueSeverity.ERROR);
    }

    @Test
    void relevanceSelectionKeepsHistoricalThenLexicalEvidenceBeyondUuidOrder() {
        List<TestEvidence> candidates = new ArrayList<>();
        for (int index = 0; index < 31; index++) {
            candidates.add(new TestEvidence(
                    new UUID(0, index + 1), "무관한 일반 경험 " + index));
        }
        TestEvidence relevant = new TestEvidence(
                new UUID(Long.MAX_VALUE, Long.MAX_VALUE),
                "Spring Boot PostgreSQL API 성능 개선");
        candidates.add(relevant);
        UUID historicalId = candidates.get(30).id();

        var selection = CoverLetterWorkflowV3Policy.selectRelevant(
                candidates,
                Set.of(historicalId),
                "Spring Boot와 PostgreSQL API 성능을 개선했습니다",
                30,
                TestEvidence::id,
                TestEvidence::text);

        assertThat(selection.selected()).contains(relevant, candidates.get(30));
        assertThat(selection.selected().get(0).id()).isEqualTo(historicalId);
        assertThat(selection.omittedCount()).isEqualTo(2);
        assertThat(selection.policyVersion())
                .isEqualTo(CoverLetterWorkflowV3Policy.EVIDENCE_SELECTION_POLICY_VERSION);
    }

    @Test
    void duplicationCombinesFullTextAndEvidenceSignals() {
        UUID evidence = UUID.randomUUID();
        var duplicate = CoverLetterWorkflowV3Policy.duplication(
                "요구사항을 분석하고 API를 설계하고 구현해 성능을 개선했습니다",
                Set.of(evidence),
                "API 성능 개선",
                "요구사항을 분석한 뒤 API를 설계하고 구현하여 성능을 개선했습니다",
                Set.of(evidence),
                "API 성능 개선",
                null);

        assertThat(duplicate.warningRequired()).isTrue();
        assertThat(duplicate.sharedEvidenceCount()).isOne();
        assertThat(duplicate.policyVersion())
                .isEqualTo(CoverLetterWorkflowV3Policy.DUPLICATION_POLICY_VERSION);
    }

    private NarrativeSectionPlan section(NarrativeSectionType type, int weight) {
        return new NarrativeSectionPlan(type, "section objective", weight);
    }

    private void assertIncompatible(
            VerificationIssueKind kind,
            VerificationIssueCode code,
            IssueSeverity severity) {
        assertThatThrownBy(() -> CoverLetterWorkflowV3Policy.validateIssueCompatibility(
                        kind, code, severity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private record TestClaim(UUID evidenceId, String exactAnswerExcerpt, ClaimType claimType)
            implements CoverLetterWorkflowV3Policy.ClaimView {}

    private record TestEvidence(UUID id, String text) {}
}
