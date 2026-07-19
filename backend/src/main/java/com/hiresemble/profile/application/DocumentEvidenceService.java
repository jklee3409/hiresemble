package com.hiresemble.profile.application;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.document.application.DocumentEvidenceCandidate;
import com.hiresemble.profile.domain.ProfilePolicy;
import com.hiresemble.profile.domain.ProfileRecords.EvidenceRecord;
import com.hiresemble.profile.infrastructure.ProfileStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final EvidenceReferenceQueryPort referenceQuery;
    private final ObjectMapper objectMapper;

    public DocumentEvidenceService(
            ProfileStore store,
            EvidenceReferenceQueryPort referenceQuery,
            ObjectMapper objectMapper) {
        this.store = store;
        this.referenceQuery = referenceQuery;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ApplyResult applyCandidates(
            UUID userId,
            UUID documentId,
            long sourceRevision,
            List<DocumentEvidenceCandidate> candidates,
            Instant now) {
        List<UUID> applied = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        int rejected = 0;
        for (DocumentEvidenceCandidate candidate : candidates == null ? List.<DocumentEvidenceCandidate>of() : candidates) {
            ValidatedCandidate value;
            try {
                value = validate(userId, documentId, sourceRevision, candidate);
            } catch (BusinessException | IllegalArgumentException exception) {
                rejected++;
                continue;
            }
            String key = value.category().toLowerCase(java.util.Locale.ROOT) + "\u0000"
                    + value.title().toLowerCase(java.util.Locale.ROOT) + "\u0000"
                    + value.content() + "\u0000" + value.primaryChunkId();
            if (!dedupe.add(key)) {
                rejected++;
                continue;
            }
            UUID id = UUID.randomUUID();
            store.createDocumentEvidence(
                    id, userId, documentId, value.primaryChunkId(), value.category(), value.title(),
                    value.content(), value.metadata(), candidate.confidence(), now);
            applied.add(id);
        }
        return new ApplyResult(applied, rejected);
    }

    @Override
    @Transactional
    public void handleDocumentDeletion(UUID userId, UUID documentId, Instant deletedAt) {
        for (EvidenceRecord evidence : store.findDocumentEvidence(userId, documentId)) {
            if (referenceQuery.isReferenced(userId, evidence.id())) {
                store.tombstoneEvidence(userId, evidence.id(), deletedAt);
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
                || candidate.sourceChunkIds().size() > 20
                || candidate.confidence() == null
                || candidate.confidence().signum() < 0
                || candidate.confidence().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw invalid();
        }
        String category = ProfilePolicy.requiredLabel(candidate.evidenceCategory(), 80);
        String title = ProfilePolicy.requiredLabel(candidate.title(), 250);
        String content = requiredContent(candidate.content());
        Map<String, Object> metadata = metadata(candidate.metadata(), candidate.validationWarning());
        StringBuilder sources = new StringBuilder();
        for (UUID chunkId : candidate.sourceChunkIds()) {
            if (chunkId == null || !store.documentChunkExists(
                    userId, documentId, sourceRevision, chunkId)) {
                throw invalid();
            }
            sources.append(store.documentChunkContent(userId, documentId, sourceRevision, chunkId));
        }
        Matcher matcher = NUMBER.matcher(content);
        while (matcher.find()) {
            if (sources.indexOf(matcher.group()) < 0) throw invalid();
        }
        return new ValidatedCandidate(
                category, title, content, metadata, candidate.sourceChunkIds().getFirst());
    }

    private String requiredContent(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000 || value.indexOf('\0') >= 0) {
            throw invalid();
        }
        return value;
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
            throw invalid();
        }
        try {
            if (objectMapper.writeValueAsString(metadata).getBytes(StandardCharsets.UTF_8).length > 16_384) {
                throw invalid();
            }
        } catch (JacksonException exception) {
            throw invalid();
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private record ValidatedCandidate(
            String category,
            String title,
            String content,
            Map<String, Object> metadata,
            UUID primaryChunkId) {}
}
