package com.hiresemble.profile.application.service;

import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.profile.domain.model.ExperienceLinkKind;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.policy.ExperienceSimilarityPolicy;
import com.hiresemble.profile.domain.policy.ExperienceSimilarityPolicy.MatchDecision;
import com.hiresemble.profile.infrastructure.persistence.ExperienceStore;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies already provenance-validated candidates to the canonical experience library. */
@Service
public class CanonicalExperienceCandidateService {

    private final ProfileStore profileStore;
    private final ExperienceStore experienceStore;

    public CanonicalExperienceCandidateService(ProfileStore profileStore, ExperienceStore experienceStore) {
        this.profileStore = profileStore;
        this.experienceStore = experienceStore;
    }

    @Transactional
    public ApplyResult apply(
            UUID userId,
            List<Candidate> candidates,
            EmbeddingPolicy embeddingPolicy,
            SourceEvidenceWriter sourceEvidenceWriter,
            Instant now) {
        List<UUID> applied = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        EnumMap<RejectionReason, Integer> rejected = new EnumMap<>(RejectionReason.class);
        EnumMap<ExperienceMatchKind, Integer> matches = new EnumMap<>(ExperienceMatchKind.class);
        experienceStore.lockUserMatching(userId);
        for (Candidate candidate : candidates == null ? List.<Candidate>of() : candidates) {
            String comparisonCategory = ExperienceSimilarityPolicy.comparisonGroup(candidate.category());
            String fingerprint = ExperienceSimilarityPolicy.fingerprint(
                    comparisonCategory, candidate.title(), candidate.content());
            if (!dedupe.add(fingerprint)) {
                rejected.merge(RejectionReason.DUPLICATE, 1, Integer::sum);
                continue;
            }
            if (embeddingPolicy != null && !validEmbedding(candidate.embedding(), embeddingPolicy)) {
                rejected.merge(RejectionReason.INVALID_EMBEDDING, 1, Integer::sum);
                continue;
            }
            List<String> categories = ExperienceSimilarityPolicy.comparisonCategories(candidate.category());
            List<String> fingerprints = new ArrayList<>(ExperienceSimilarityPolicy.comparisonFingerprints(
                    candidate.category(), candidate.title(), candidate.content()));
            if (!fingerprints.contains(fingerprint)) {
                fingerprints.add(fingerprint);
            }
            MatchDecision decision = experienceStore
                    .findActiveExact(userId, categories, fingerprints)
                    .map(existing -> new MatchDecision(
                            ExperienceMatchKind.SAME_EXPERIENCE,
                            existing.id(),
                            BigDecimal.ONE.setScale(5)))
                    .orElseGet(() -> semanticDecision(
                            userId, categories, candidate, embeddingPolicy));
            UUID evidenceId = sourceEvidenceWriter.create(userId, UUID.randomUUID(), candidate, now);
            var existingEvidenceLink = experienceStore.findBySourceEvidence(userId, evidenceId);
            if (existingEvidenceLink.isPresent()) {
                applied.add(evidenceId);
                matches.merge(ExperienceMatchKind.SAME_EXPERIENCE, 1, Integer::sum);
                continue;
            }
            if (decision.kind() == ExperienceMatchKind.SAME_EXPERIENCE) {
                experienceStore.addEvidenceLink(
                        userId,
                        decision.matchedExperienceItemId(),
                        evidenceId,
                        ExperienceLinkKind.CORROBORATING,
                        decision.similarity(),
                        ExperienceSimilarityPolicy.VERSION,
                        now);
            } else {
                UUID experienceItemId = UUID.randomUUID();
                UUID canonicalEvidenceId = UUID.randomUUID();
                experienceStore.createItem(
                        experienceItemId,
                        userId,
                        canonicalEvidenceId,
                        candidate.category(),
                        candidate.title(),
                        candidate.content(),
                        decision.kind(),
                        decision.matchedExperienceItemId(),
                        decision.similarity(),
                        ExperienceSimilarityPolicy.VERSION,
                        fingerprint,
                        now);
                profileStore.createExperienceEvidence(
                        canonicalEvidenceId,
                        userId,
                        experienceItemId,
                        candidate.category(),
                        candidate.title(),
                        candidate.content(),
                        candidate.metadata(),
                        candidate.confidence(),
                        now);
                experienceStore.addEvidenceLink(
                        userId,
                        experienceItemId,
                        evidenceId,
                        ExperienceLinkKind.PRIMARY_SOURCE,
                        null,
                        ExperienceSimilarityPolicy.VERSION,
                        now);
                if (embeddingPolicy != null) {
                    experienceStore.storeEmbedding(
                            userId,
                            experienceItemId,
                            0,
                            candidate.embedding(),
                            embeddingPolicy,
                            now);
                }
            }
            applied.add(evidenceId);
            matches.merge(decision.kind(), 1, Integer::sum);
        }
        return new ApplyResult(applied, rejected, matches);
    }

    private MatchDecision semanticDecision(
            UUID userId,
            List<String> categories,
            Candidate candidate,
            EmbeddingPolicy embeddingPolicy) {
        if (embeddingPolicy == null || candidate.embedding().isEmpty()) {
            return MatchDecision.newExperience();
        }
        return ExperienceSimilarityPolicy.decide(
                candidate.title(),
                candidate.content(),
                experienceStore.findSimilar(
                        userId,
                        categories,
                        candidate.embedding(),
                        embeddingPolicy,
                        ExperienceSimilarityPolicy.TOP_K));
    }

    private boolean validEmbedding(List<Double> embedding, EmbeddingPolicy policy) {
        return embedding != null
                && embedding.size() == policy.dimension()
                && embedding.stream().allMatch(value -> value != null && Double.isFinite(value));
    }

    public record Candidate(
            String category,
            String title,
            String content,
            Map<String, Object> metadata,
            BigDecimal confidence,
            UUID primarySourceReference,
            List<UUID> supportingSourceReferences,
            String sourceClaimKey,
            List<Double> embedding) {

        public Candidate {
            metadata = metadata == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            supportingSourceReferences = supportingSourceReferences == null
                    ? List.of()
                    : List.copyOf(supportingSourceReferences);
            embedding = embedding == null ? List.of() : List.copyOf(embedding);
        }
    }

    @FunctionalInterface
    public interface SourceEvidenceWriter {
        UUID create(UUID userId, UUID evidenceId, Candidate candidate, Instant now);
    }

    public enum RejectionReason {
        DUPLICATE,
        INVALID_EMBEDDING
    }

    public record ApplyResult(
            List<UUID> appliedEvidenceIds,
            Map<RejectionReason, Integer> rejectionReasonCounts,
            Map<ExperienceMatchKind, Integer> experienceMatchCounts) {

        public ApplyResult {
            appliedEvidenceIds = appliedEvidenceIds == null ? List.of() : List.copyOf(appliedEvidenceIds);
            rejectionReasonCounts = rejectionReasonCounts == null ? Map.of() : Map.copyOf(rejectionReasonCounts);
            experienceMatchCounts = experienceMatchCounts == null ? Map.of() : Map.copyOf(experienceMatchCounts);
        }
    }
}
