package com.hiresemble.ai.workflow.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.prompt.GitHubIngestionPromptDefinitions;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubIngestionWorkflowContractTest {

    @Test
    void extractionUsesKoreanCoreExperiencePolicyAndThreeCandidateLimit() {
        var prompt = GitHubIngestionPromptDefinitions.all().stream()
                .filter(value -> value.key()
                        .stepKey()
                        .equals(GitHubIngestionWorkflow.EXTRACT_GITHUB_CANDIDATES))
                .findFirst()
                .orElseThrow();

        assertThat(prompt.promptVersion()).isEqualTo("github-ingestion-prompt-v2");
        assertThat(prompt.instructions())
                .contains(
                        "natural Korean",
                        "repository-level core experiences",
                        "Consolidate related evidence",
                        "Return at most three",
                        "candidates, and return zero");
        assertThat(GitHubIngestionWorkflow.MAX_CANDIDATES_PER_REPOSITORY).isEqualTo(3);
    }

    @Test
    void candidateTitleAndContentMustBothContainKorean() {
        assertThat(GitHubIngestionWorkflow.isKoreanCandidate(candidate(
                        "결제 API 안정화",
                        "Redis cache를 적용해 결제 API의 장애 복원력을 높였습니다.")))
                .isTrue();
        assertThat(GitHubIngestionWorkflow.isKoreanCandidate(candidate(
                        "Payment API stabilization",
                        "결제 API의 장애 복원력을 높였습니다.")))
                .isFalse();
        assertThat(GitHubIngestionWorkflow.isKoreanCandidate(candidate(
                        "결제 API 안정화",
                        "Improved payment API resilience.")))
                .isFalse();
    }

    private GitHubIngestionWorkflow.ExtractedCandidate candidate(String title, String content) {
        return new GitHubIngestionWorkflow.ExtractedCandidate(
                "PROJECT", title, content, new BigDecimal("0.9"), List.of("U1"));
    }
}
