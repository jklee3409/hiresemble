package com.hiresemble.profile.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.document.application.model.DocumentEvidenceApplyResult;
import com.hiresemble.document.application.model.DocumentEvidenceCandidate;
import com.hiresemble.document.application.model.DocumentEvidenceRejectionReason;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.profile.application.port.DocumentEvidenceCommandPort;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.ApplyResult;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.RejectionReason;
import com.hiresemble.profile.domain.model.ExperienceMatchKind;
import com.hiresemble.profile.domain.model.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.infrastructure.persistence.ExperienceStore;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentEvidenceService implements DocumentEvidenceCommandPort {

    private final ProfileStore store;
    private final ExperienceStore experienceStore;
    private final List<EvidenceReferenceQueryPort> referenceQueries;
    private final DocumentCandidateProvenanceValidator validator;
    private final CanonicalExperienceCandidateService canonicalService;

    public DocumentEvidenceService(
            ProfileStore store,
            ExperienceStore experienceStore,
            List<EvidenceReferenceQueryPort> referenceQueries,
            DocumentCandidateProvenanceValidator validator,
            CanonicalExperienceCandidateService canonicalService) {
        this.store = store;
        this.experienceStore = experienceStore;
        this.referenceQueries = List.copyOf(referenceQueries);
        this.validator = validator;
        this.canonicalService = canonicalService;
    }

    @Override
    @Transactional
    public DocumentEvidenceApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            Instant now) {
        return applyCandidates(userId, documentId, sourceRevision, candidates, null, now);
    }

    @Override
    @Transactional
    public DocumentEvidenceApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            EmbeddingPolicy embeddingPolicy,
            Instant now) {
        List<Candidate> validated = new ArrayList<>();
        EnumMap<DocumentEvidenceRejectionReason, Integer> rejected =
                new EnumMap<>(DocumentEvidenceRejectionReason.class);
        for (DocumentEvidenceCandidate candidate : candidates == null
                ? List.<DocumentEvidenceCandidate>of()
                : candidates) {
            try {
                validated.add(validator.validate(userId, documentId, sourceRevision, candidate));
            } catch (DocumentCandidateProvenanceValidator.Rejection exception) {
                rejected.merge(exception.reason(), 1, Integer::sum);
            } catch (BusinessException | IllegalArgumentException exception) {
                rejected.merge(DocumentEvidenceRejectionReason.OTHER_SAFE_REJECTION, 1, Integer::sum);
            }
        }
        ApplyResult result = canonicalService.apply(
                userId,
                validated,
                embeddingPolicy,
                (ownerId, evidenceId, candidate, appliedAt) -> {
                    store.createDocumentEvidence(
                            evidenceId,
                            ownerId,
                            documentId,
                            candidate.primarySourceReference(),
                            candidate.category(),
                            candidate.title(),
                            candidate.content(),
                            candidate.metadata(),
                            candidate.confidence(),
                            appliedAt);
                    return evidenceId;
                },
                now);
        result.rejectionReasonCounts().forEach((reason, count) -> rejected.merge(
                reason == RejectionReason.DUPLICATE
                        ? DocumentEvidenceRejectionReason.DUPLICATE
                        : DocumentEvidenceRejectionReason.INVALID_EMBEDDING,
                count,
                Integer::sum));
        int rejectedCount = rejected.values().stream().mapToInt(Integer::intValue).sum();
        return new DocumentEvidenceApplyResult(
                result.appliedEvidenceIds(),
                rejectedCount,
                rejected,
                result.experienceMatchCounts());
    }

    @Override
    @Transactional
    public void retireDocumentEvidence(UUID userId, UUID documentId, Instant retiredAt) {
        for (EvidenceRecord evidence : store.findDocumentEvidence(userId, documentId)) {
            if (referenceQueries.stream().anyMatch(query -> query.isReferenced(userId, evidence.id()))) {
                store.tombstoneEvidence(userId, evidence.id(), retiredAt);
            } else {
                store.deleteEvidence(userId, evidence.id());
            }
        }
        deleteOrphanUnverifiedItems(userId, retiredAt);
    }

    private void deleteOrphanUnverifiedItems(UUID userId, Instant retiredAt) {
        for (UUID itemId : experienceStore.findOrphanUnverifiedItems(userId)) {
            experienceStore.findActive(userId, itemId).ifPresent(item -> {
                experienceStore.clearInboundMatches(userId, itemId, retiredAt);
                experienceStore.deleteItem(userId, itemId);
                store.deleteExperienceEvidence(userId, item.canonicalEvidenceId());
            });
        }
    }
}
