package com.hiresemble.profile.application.service;

import com.hiresemble.profile.application.port.DocumentEvidenceCommandPort;
import com.hiresemble.profile.application.port.EvidenceReferenceQueryPort;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.document.application.model.DocumentEvidenceCandidate;
import com.hiresemble.document.application.model.DocumentEvidenceApplyResult;
import com.hiresemble.document.application.model.DocumentEvidenceRejectionReason;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import com.hiresemble.profile.domain.model.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentEvidenceService implements DocumentEvidenceCommandPort {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?%?");
    private final ProfileStore store;
    private final List<EvidenceReferenceQueryPort> referenceQueries;
    private final ObjectMapper objectMapper;

    public DocumentEvidenceService(
            ProfileStore store,
            List<EvidenceReferenceQueryPort> referenceQueries,
            ObjectMapper objectMapper) {
        this.store = store;
        this.referenceQueries = List.copyOf(referenceQueries);
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public DocumentEvidenceApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            Instant now) {
        List<UUID> applied = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        EnumMap<DocumentEvidenceRejectionReason, Integer> rejected =
                new EnumMap<>(DocumentEvidenceRejectionReason.class);
        for (DocumentEvidenceCandidate candidate : candidates == null ? List.<DocumentEvidenceCandidate>of() : candidates) {
            ValidatedCandidate value;
            try {
                value = validate(userId, documentId, sourceRevision, candidate);
            } catch (CandidateRejectionException exception) {
                rejected.merge(exception.reason(), 1, Integer::sum);
                continue;
            } catch (BusinessException | IllegalArgumentException exception) {
                rejected.merge(
                        DocumentEvidenceRejectionReason.OTHER_SAFE_REJECTION,
                        1,
                        Integer::sum);
                continue;
            }
            String key = value.category().toLowerCase(java.util.Locale.ROOT) + "\u0000"
                    + value.title().toLowerCase(java.util.Locale.ROOT) + "\u0000"
                    + value.content() + "\u0000" + value.primaryChunkId();
            if (!dedupe.add(key)) {
                rejected.merge(DocumentEvidenceRejectionReason.DUPLICATE, 1, Integer::sum);
                continue;
            }
            UUID id = UUID.randomUUID();
            store.createDocumentEvidence(
                    id, userId, documentId, value.primaryChunkId(), value.category(), value.title(),
                    value.content(), value.metadata(), candidate.confidence(), now);
            applied.add(id);
        }
        int rejectedCount = rejected.values().stream().mapToInt(Integer::intValue).sum();
        return new DocumentEvidenceApplyResult(applied, rejectedCount, rejected);
    }

    @Override
    @Transactional
    public void retireDocumentEvidence(UUID userId, UUID documentId, Instant retiredAt) {
        for (EvidenceRecord evidence : store.findDocumentEvidence(userId, documentId)) {
            if (referenceQueries.stream()
                    .anyMatch(query -> query.isReferenced(userId, evidence.id()))) {
                store.tombstoneEvidence(userId, evidence.id(), retiredAt);
            } else {
                store.deleteEvidence(userId, evidence.id());
            }
        }
    }

    private ValidatedCandidate validate(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            DocumentEvidenceCandidate candidate) {
        if (candidate == null
                || candidate.sourceRevision() != sourceRevision
                || candidate.sourceChunkIds().isEmpty()
                || candidate.sourceChunkIds().size() > 20) {
            throw rejected(DocumentEvidenceRejectionReason.INVALID_PROVENANCE);
        }
        if (candidate.confidence() == null
                || candidate.confidence().signum() < 0
                || candidate.confidence().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw rejected(DocumentEvidenceRejectionReason.INVALID_CONFIDENCE);
        }
        String category = requiredLabel(
                candidate.evidenceCategory(), 80,
                DocumentEvidenceRejectionReason.INVALID_CATEGORY);
        if (ProfilePolicy.isEducationEvidenceCategory(category)) {
            throw rejected(DocumentEvidenceRejectionReason.EDUCATION_CATEGORY);
        }
        String title = requiredLabel(
                candidate.title(), 250,
                DocumentEvidenceRejectionReason.INVALID_CONTENT);
        String content = requiredContent(candidate.content());
        Map<String, Object> metadata = metadata(candidate.metadata(), candidate.validationWarning());
        StringBuilder sources = new StringBuilder();
        for (UUID chunkId : candidate.sourceChunkIds()) {
            if (chunkId == null || !store.documentChunkExists(
                    userId, documentId, sourceRevision, chunkId)) {
                throw rejected(DocumentEvidenceRejectionReason.INVALID_PROVENANCE);
            }
            sources.append(store.documentChunkContent(userId, documentId, sourceRevision, chunkId));
        }
        Matcher matcher = NUMBER.matcher(content);
        while (matcher.find()) {
            if (sources.indexOf(matcher.group()) < 0) {
                throw rejected(DocumentEvidenceRejectionReason.UNGROUNDED_NUMBER);
            }
        }
        return new ValidatedCandidate(
                category, title, content, metadata, candidate.sourceChunkIds().getFirst());
    }

    private String requiredContent(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000 || value.indexOf('\0') >= 0) {
            throw rejected(DocumentEvidenceRejectionReason.INVALID_CONTENT);
        }
        return value;
    }

    private String requiredLabel(
            String value, int maxLength, DocumentEvidenceRejectionReason reason) {
        try {
            return ProfilePolicy.requiredLabel(value, maxLength);
        } catch (BusinessException | IllegalArgumentException exception) {
            throw rejected(reason);
        }
    }

    private Map<String, Object> metadata(Map<String, Object> source, String warning) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source != null) metadata.putAll(source);
        if (warning != null && !warning.isBlank()) {
            metadata.put("validationWarning", warning.length() > 500 ? warning.substring(0, 500) : warning);
        }
        if (metadata.values().stream().anyMatch(value -> value != null
                && !(value instanceof String)
                && !(value instanceof Number)
                && !(value instanceof Boolean))) {
            throw rejected(DocumentEvidenceRejectionReason.INVALID_METADATA);
        }
        try {
            if (objectMapper.writeValueAsString(metadata).getBytes(StandardCharsets.UTF_8).length > 16_384) {
                throw rejected(DocumentEvidenceRejectionReason.INVALID_METADATA);
            }
        } catch (JacksonException exception) {
            throw rejected(DocumentEvidenceRejectionReason.INVALID_METADATA);
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private CandidateRejectionException rejected(DocumentEvidenceRejectionReason reason) {
        return new CandidateRejectionException(reason);
    }

    private record ValidatedCandidate(
            String category,
            String title,
            String content,
            Map<String, Object> metadata,
            UUID primaryChunkId) {}

    private static final class CandidateRejectionException extends RuntimeException {
        private final DocumentEvidenceRejectionReason reason;

        private CandidateRejectionException(DocumentEvidenceRejectionReason reason) {
            this.reason = java.util.Objects.requireNonNull(reason);
        }

        private DocumentEvidenceRejectionReason reason() {
            return reason;
        }
    }
}
