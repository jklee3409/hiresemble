package com.hiresemble.profile.application.service;

import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.ApplyResult;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate;
import com.hiresemble.profile.domain.model.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.infrastructure.persistence.ExperienceStore;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GitHub-specific raw evidence persistence around the shared canonical apply policy. */
@Service
public class GitHubCanonicalEvidenceService {

    private final ProfileStore profileStore;
    private final ExperienceStore experienceStore;
    private final List<EvidenceReferenceQueryPort> referenceQueries;
    private final CanonicalExperienceCandidateService canonicalService;

    public GitHubCanonicalEvidenceService(
            ProfileStore profileStore,
            ExperienceStore experienceStore,
            List<EvidenceReferenceQueryPort> referenceQueries,
            CanonicalExperienceCandidateService canonicalService) {
        this.profileStore = profileStore;
        this.experienceStore = experienceStore;
        this.referenceQueries = List.copyOf(referenceQueries);
        this.canonicalService = canonicalService;
    }

    @Transactional
    public ApplyResult apply(
            UUID userId,
            UUID sourceId,
            UUID repositoryId,
            UUID snapshotId,
            List<Candidate> candidates,
            EmbeddingPolicy embeddingPolicy,
            Instant now) {
        return canonicalService.apply(
                userId,
                candidates,
                embeddingPolicy,
                (ownerId, evidenceId, candidate, appliedAt) -> profileStore.createOrRefreshGitHubEvidence(
                        evidenceId,
                        ownerId,
                        sourceId,
                        repositoryId,
                        snapshotId,
                        candidate.sourceClaimKey(),
                        candidate.primarySourceReference(),
                        candidate.supportingSourceReferences(),
                        candidate.category(),
                        candidate.title(),
                        candidate.content(),
                        candidate.metadata(),
                        candidate.confidence(),
                        appliedAt),
                now);
    }

    @Transactional
    public void retireSource(UUID userId, UUID sourceId, Instant retiredAt) {
        for (EvidenceRecord evidence : profileStore.findGitHubEvidence(userId, sourceId)) {
            if (referenceQueries.stream().anyMatch(query -> query.isReferenced(userId, evidence.id()))) {
                profileStore.tombstoneGitHubEvidence(userId, evidence.id(), retiredAt);
            } else {
                profileStore.deleteGitHubEvidence(userId, evidence.id());
            }
        }
        for (UUID itemId : experienceStore.findOrphanUnverifiedItems(userId)) {
            experienceStore.findActive(userId, itemId).ifPresent(item -> {
                experienceStore.clearInboundMatches(userId, itemId, retiredAt);
                experienceStore.deleteItem(userId, itemId);
                profileStore.deleteExperienceEvidence(userId, item.canonicalEvidenceId());
            });
        }
    }
}
