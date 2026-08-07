package com.hiresemble.profile.application.service;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.document.application.model.DocumentEvidenceCandidate;
import com.hiresemble.document.application.model.DocumentEvidenceRejectionReason;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import com.hiresemble.profile.infrastructure.persistence.ProfileStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Validates document ownership, revision, chunk grounding, and safe candidate fields. */
@Component
public class DocumentCandidateProvenanceValidator {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?%?");

    private final ProfileStore store;
    private final ObjectMapper objectMapper;

    public DocumentCandidateProvenanceValidator(ProfileStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public Candidate validate(
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
                candidate.evidenceCategory(), 80, DocumentEvidenceRejectionReason.INVALID_CATEGORY);
        if (ProfilePolicy.isEducationEvidenceCategory(category)) {
            throw rejected(DocumentEvidenceRejectionReason.EDUCATION_CATEGORY);
        }
        String title = requiredLabel(
                candidate.title(), 250, DocumentEvidenceRejectionReason.INVALID_CONTENT);
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
        return new Candidate(
                category,
                title,
                content,
                metadata,
                candidate.confidence(),
                candidate.sourceChunkIds().getFirst(),
                candidate.sourceChunkIds().stream().skip(1).toList(),
                null,
                candidate.embedding());
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

    private Rejection rejected(DocumentEvidenceRejectionReason reason) {
        return new Rejection(reason);
    }

    public static final class Rejection extends RuntimeException {
        private final DocumentEvidenceRejectionReason reason;

        private Rejection(DocumentEvidenceRejectionReason reason) {
            this.reason = java.util.Objects.requireNonNull(reason);
        }

        public DocumentEvidenceRejectionReason reason() {
            return reason;
        }
    }
}
