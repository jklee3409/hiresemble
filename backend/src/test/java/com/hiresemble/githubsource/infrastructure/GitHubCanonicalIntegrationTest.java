package com.hiresemble.githubsource.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.document.infrastructure.persistence.DocumentStore;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator.AllowedSourceUnit;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator.RejectionReason;
import com.hiresemble.githubsource.application.GitHubEvidenceCandidate;
import com.hiresemble.profile.application.service.ExperienceApplicationService;
import com.hiresemble.profile.application.service.GitHubCanonicalEvidenceService;
import com.hiresemble.profile.domain.model.EvidenceSourceType;
import com.hiresemble.profile.domain.model.EvidenceVerificationStatus;
import com.hiresemble.profile.domain.model.ExperienceCommands.ExperienceVerification;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.infrastructure.persistence.ExperienceStore;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubCanonicalIntegrationTest extends PostgresIntegrationTest {

    private static final AtomicLong EXTERNAL_ID = new AtomicLong(10_000);
    private static final String PROJECT_KO = "\ud504\ub85c\uc81d\ud2b8";

    @Autowired private GitHubCandidateProvenanceValidator validator;
    @Autowired private GitHubCanonicalEvidenceService canonicalService;
    @Autowired private ExperienceStore experienceStore;
    @Autowired private ExperienceApplicationService experienceService;
    @Autowired private DocumentStore documentStore;

    private UUID owner;
    private Graph first;
    private Graph second;

    @BeforeEach
    void setUpGraphs() {
        owner = UUID.randomUUID();
        insertUser(owner, "canonical-" + owner + "@example.com");
        first = insertGraph(owner, "canonical-owner", "first-repo");
        second = insertGraph(owner, "canonical-owner", "second-repo");
    }

    @Test
    void exactAliasRefreshAndSourceDeletionPreserveApprovedCanonicalExperience() {
        String title = "Payment API cache optimization";
        String content = "Payment Redis cache reduced latency to 42 ms.";
        var firstValidated = validate(first, PROJECT_KO, title, content, vector(1.0));
        var firstApply = apply(first, firstValidated);
        assertThat(firstApply.experienceMatchCounts())
                .containsOnly(Map.entry(ExperienceMatchKind.NEW, 1));

        UUID itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM experience_items WHERE user_id=?", UUID.class, owner);
        var pending = experienceStore.findActive(owner, itemId).orElseThrow();
        assertThat(pending.verificationStatus()).isEqualTo(EvidenceVerificationStatus.PENDING);
        assertThat(pending.sourceCount()).isEqualTo(1);
        assertThat(pending.githubRepositorySourceCount()).isEqualTo(1);

        var refreshed = apply(first, firstValidated);
        assertThat(refreshed.experienceMatchCounts())
                .containsOnly(Map.entry(ExperienceMatchKind.SAME_EXPERIENCE, 1));
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM profile_evidence
                        WHERE user_id=? AND source_type='GITHUB_REPOSITORY'
                        """, Integer.class, owner))
                .isEqualTo(1);
        assertThat(experienceStore.findActive(owner, itemId).orElseThrow().sourceCount())
                .isEqualTo(1);

        var corroborating = apply(
                second,
                validate(second, "PROJECT", title, content, vector(1.0)));
        assertThat(corroborating.experienceMatchCounts())
                .containsOnly(Map.entry(ExperienceMatchKind.SAME_EXPERIENCE, 1));
        var withTwoRepositories = experienceStore.findDetail(owner, itemId).orElseThrow();
        assertThat(withTwoRepositories.item().sourceCount()).isEqualTo(2);
        assertThat(withTwoRepositories.item().githubRepositorySourceCount()).isEqualTo(2);
        assertThat(withTwoRepositories.sources())
                .hasSize(2)
                .allSatisfy(source -> {
                    assertThat(source.sourceType()).isEqualTo(EvidenceSourceType.GITHUB_REPOSITORY);
                    assertThat(source.githubSourceId()).isNotNull();
                    assertThat(source.githubRepositoryId()).isNotNull();
                    assertThat(source.repositoryUrl()).startsWith("https://github.com/");
                    assertThat(source.commitShaShort()).hasSize(10);
                    assertThat(source.sourceExcerpt()).doesNotContain("secret");
                });

        experienceService.verify(
                owner,
                itemId,
                new ExperienceVerification(EvidenceVerificationStatus.VERIFIED, pending.version()));
        canonicalService.retireSource(owner, first.sourceId(), Instant.now());
        canonicalService.retireSource(owner, second.sourceId(), Instant.now());

        var retained = experienceStore.findActive(owner, itemId).orElseThrow();
        assertThat(retained.verificationStatus()).isEqualTo(EvidenceVerificationStatus.VERIFIED);
        assertThat(retained.sourceCount()).isZero();
        assertThat(retained.githubRepositorySourceCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM profile_evidence
                        WHERE user_id=? AND source_type='EXPERIENCE'
                          AND verification_status='VERIFIED'
                        """, Integer.class, owner))
                .isEqualTo(1);
    }

    @Test
    void githubCandidatesUseSharedSameRelatedConflictAndNewDecisions() {
        assertSemanticDecision(0.95, 42, ExperienceMatchKind.SAME_EXPERIENCE);
        assertSemanticDecision(0.85, 42, ExperienceMatchKind.RELATED_DIFFERENT);
        assertSemanticDecision(0.95, 99, ExperienceMatchKind.CONFLICT);
        assertSemanticDecision(0.80, 42, ExperienceMatchKind.NEW);
    }

    @Test
    void provenanceValidatorRejectsForeignUnitsStaleRevisionUngroundedNumbersAndLimits() {
        var accepted = validate(first, "STRENGTH", "Backend strength", "Reduced latency to 42 ms.", vector(1));
        assertThat(accepted.accepted()).hasSize(1);

        GitHubEvidenceCandidate candidate = candidate(
                "STRENGTH", "Backend strength", "Reduced latency to 42 ms.", "FOREIGN", vector(1));
        var foreign = validator.validate(
                owner,
                first.sourceId(),
                first.repositoryId(),
                first.snapshotId(),
                1,
                Map.of("FOREIGN", new AllowedSourceUnit(second.unitId(), "Reduced latency to 42 ms.")),
                List.of(candidate));
        assertThat(foreign.accepted()).isEmpty();
        assertThat(foreign.rejectionReasonCounts())
                .containsEntry(RejectionReason.INVALID_PROVENANCE, 1);

        var stale = validator.validate(
                owner,
                first.sourceId(),
                first.repositoryId(),
                first.snapshotId(),
                0,
                Map.of("U1", new AllowedSourceUnit(first.unitId(), "Reduced latency to 42 ms.")),
                List.of(candidate("STRENGTH", "Backend strength", "Reduced latency to 42 ms.", "U1", vector(1))));
        assertThat(stale.rejectionReasonCounts())
                .containsEntry(RejectionReason.INVALID_PROVENANCE, 1);

        var ungrounded = validator.validate(
                owner,
                first.sourceId(),
                first.repositoryId(),
                first.snapshotId(),
                1,
                Map.of("U1", new AllowedSourceUnit(first.unitId(), "Reduced latency to 42 ms.")),
                List.of(candidate(
                        "STRENGTH",
                        "Backend strength",
                        "Reduced latency to 999 ms.",
                        "U1",
                        vector(1))));
        assertThat(ungrounded.accepted()).isEmpty();
        assertThat(ungrounded.rejectionReasonCounts())
                .containsEntry(RejectionReason.UNGROUNDED_NUMBER, 1);

        List<GitHubEvidenceCandidate> excessive = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            excessive.add(candidate(
                    "PROJECT",
                    "Bounded candidate " + index,
                    "Repository evidence 42 item " + index,
                    "U1",
                    vector(1)));
        }
        var bounded = validator.validate(
                owner,
                first.sourceId(),
                first.repositoryId(),
                first.snapshotId(),
                1,
                Map.of("U1", new AllowedSourceUnit(
                        first.unitId(), "Repository evidence 42 item 0 1 2 3 4 5 6 7 8 9 10 11 12")),
                excessive);
        assertThat(bounded.accepted()).hasSize(12);
        assertThat(bounded.rejectionReasonCounts()).containsEntry(RejectionReason.LIMIT_EXCEEDED, 1);
    }

    private void assertSemanticDecision(
            double similarity, int candidateMetric, ExperienceMatchKind expected) {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "semantic-" + userId + "@example.com");
        Graph baseline = insertGraph(userId, "semantic-owner", "baseline-" + expected.name().toLowerCase());
        Graph incoming = insertGraph(userId, "semantic-owner", "incoming-" + expected.name().toLowerCase());
        apply(
                userId,
                baseline,
                validate(
                        userId,
                        baseline,
                        "PROJECT",
                        "Payment Redis cache platform",
                        "Payment Redis cache reduced latency to 42 ms.",
                        vector(1.0)));
        var result = apply(
                userId,
                incoming,
                validate(
                        userId,
                        incoming,
                        "PROJECT",
                        "Payment Redis cache optimization",
                        "Payment Redis cache lowered latency to " + candidateMetric + " ms.",
                        vector(similarity)));
        assertThat(result.experienceMatchCounts()).containsOnly(Map.entry(expected, 1));
    }

    private GitHubCandidateProvenanceValidator.ValidationResult validate(
            Graph graph, String category, String title, String content, List<Double> embedding) {
        return validate(owner, graph, category, title, content, embedding);
    }

    private GitHubCandidateProvenanceValidator.ValidationResult validate(
            UUID userId,
            Graph graph,
            String category,
            String title,
            String content,
            List<Double> embedding) {
        return validator.validate(
                userId,
                graph.sourceId(),
                graph.repositoryId(),
                graph.snapshotId(),
                1,
                Map.of("U1", new AllowedSourceUnit(graph.unitId(), content)),
                List.of(candidate(category, title, content, "U1", embedding)));
    }

    private GitHubEvidenceCandidate candidate(
            String category,
            String title,
            String content,
            String reference,
            List<Double> embedding) {
        return new GitHubEvidenceCandidate(
                category,
                title,
                content,
                Map.of(),
                new BigDecimal("0.900"),
                List.of(reference),
                embedding);
    }

    private com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.ApplyResult apply(
            Graph graph, GitHubCandidateProvenanceValidator.ValidationResult validation) {
        return apply(owner, graph, validation);
    }

    private com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.ApplyResult apply(
            UUID userId,
            Graph graph,
            GitHubCandidateProvenanceValidator.ValidationResult validation) {
        assertThat(validation.accepted()).isNotEmpty();
        return canonicalService.apply(
                userId,
                graph.sourceId(),
                graph.repositoryId(),
                graph.snapshotId(),
                validation.accepted(),
                documentStore.activeEmbeddingPolicy(),
                Instant.now());
    }

    private Graph insertGraph(UUID userId, String repositoryOwner, String repositoryName) {
        UUID sourceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        String canonical = "https://github.com/" + repositoryOwner + "/" + repositoryName;
        jdbcTemplate.update("""
                INSERT INTO github_sources (
                    id,user_id,source_kind,account_type,original_url,canonical_url,owner_login,
                    repository_name,source_status,source_revision,last_successful_sync_at,
                    created_at,updated_at
                ) VALUES (?,?,'REPOSITORY',NULL,?,?,?,?, 'READY',1,now(),now(),now())
                """, sourceId, userId, canonical, canonical, repositoryOwner, repositoryName);
        long externalId = EXTERNAL_ID.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO github_repositories (
                    id,user_id,external_repository_id,node_id,owner_login,repository_name,
                    canonical_url,default_branch,is_private,is_fork,is_archived,topics,
                    created_at,updated_at
                ) VALUES (?,?,?,'node-' || ?,?,?,?,'main',false,false,false,'[]',now(),now())
                """, repositoryId, userId, externalId, externalId,
                repositoryOwner, repositoryName, canonical);
        jdbcTemplate.update("""
                INSERT INTO github_source_repository_links (
                    id,user_id,github_source_id,github_repository_id,available,selected,
                    selection_order,discovered_at,updated_at
                ) VALUES (gen_random_uuid(),?,?,?,true,true,1,now(),now())
                """, userId, sourceId, repositoryId);
        String storageKey = "users/" + userId + "/github-sources/" + sourceId
                + "/snapshots/" + snapshotId + "/snapshot.json.gz";
        jdbcTemplate.update("""
                INSERT INTO github_repository_snapshots (
                    id,user_id,github_repository_id,commit_sha,tree_sha,github_api_version,
                    retrieval_policy_version,selection_complete,upstream_truncated,
                    snapshot_storage_key,checksum_sha256,sanitized_bytes,captured_at
                ) VALUES (?,?,?,repeat('a',40),repeat('b',40),'2026-03-10','github-snapshot-v1',
                    true,false,?,repeat('c',64),1024,now())
                """, snapshotId, userId, repositoryId, storageKey);
        jdbcTemplate.update("""
                INSERT INTO github_source_units (
                    id,user_id,snapshot_id,unit_type,repository_path,blob_sha,language,
                    line_start,line_end,content_hash,excerpt,snapshot_ordinal,created_at
                ) VALUES (?, ?, ?, 'README', 'README.md', repeat('d',40), 'Markdown', 1, 3,
                    repeat('e',64), 'sanitized repository excerpt', 1, now())
                """, unitId, userId, snapshotId);
        return new Graph(sourceId, repositoryId, snapshotId, unitId);
    }

    private void insertUser(UUID userId, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id,email,password_hash,display_name,role,status,terms_agreed_at,
                    ai_consent_at,created_at,updated_at
                ) VALUES (?,?,'fixture-password-hash','Fixture','USER','ACTIVE',now(),now(),now(),now())
                """, userId, email);
    }

    private List<Double> vector(double cosine) {
        List<Double> values = new ArrayList<>(java.util.Collections.nCopies(1536, 0d));
        values.set(0, cosine);
        values.set(1, Math.sqrt(Math.max(0, 1 - cosine * cosine)));
        return List.copyOf(values);
    }

    private record Graph(UUID sourceId, UUID repositoryId, UUID snapshotId, UUID unitId) {}
}
