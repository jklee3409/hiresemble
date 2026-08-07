package com.hiresemble.githubsource.application;

import com.hiresemble.githubsource.infrastructure.GitHubSourceStore;
import com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate;
import com.hiresemble.profile.domain.policy.ExperienceSimilarityPolicy;
import com.hiresemble.profile.domain.policy.ProfilePolicy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Validates the server-owned GitHub source envelope and opaque source-unit allowlist. */
@Component
public class GitHubCandidateProvenanceValidator {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?%?");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "PROJECT", "프로젝트", "STRENGTH", "강점", "역량");

    private final GitHubSourceStore store;
    private final ObjectMapper objectMapper;

    public GitHubCandidateProvenanceValidator(GitHubSourceStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(
            UUID userId,
            UUID sourceId,
            UUID repositoryId,
            UUID snapshotId,
            long sourceRevision,
            Map<String, AllowedSourceUnit> allowlist,
            List<GitHubEvidenceCandidate> candidates) {
        Map<String, AllowedSourceUnit> safeAllowlist = allowlist == null ? Map.of() : Map.copyOf(allowlist);
        Set<UUID> validUnitIds = store.validSourceUnitIds(
                userId, sourceId, repositoryId, snapshotId, sourceRevision);
        List<Candidate> accepted = new ArrayList<>();
        EnumMap<RejectionReason, Integer> rejected = new EnumMap<>(RejectionReason.class);
        Set<String> claims = new HashSet<>();
        List<GitHubEvidenceCandidate> bounded = candidates == null ? List.of() : candidates;
        if (bounded.size() > 12) {
            bounded = bounded.subList(0, 12);
            rejected.merge(RejectionReason.LIMIT_EXCEEDED, candidates.size() - 12, Integer::sum);
        }
        for (GitHubEvidenceCandidate candidate : bounded) {
            try {
                Candidate value = validateOne(repositoryId, safeAllowlist, validUnitIds, candidate);
                if (!claims.add(value.sourceClaimKey())) {
                    rejected.merge(RejectionReason.DUPLICATE, 1, Integer::sum);
                } else {
                    accepted.add(value);
                }
            } catch (Rejection exception) {
                rejected.merge(exception.reason, 1, Integer::sum);
            } catch (IllegalArgumentException exception) {
                rejected.merge(RejectionReason.OTHER_SAFE_REJECTION, 1, Integer::sum);
            }
        }
        return new ValidationResult(accepted, rejected);
    }

    private Candidate validateOne(
            UUID repositoryId,
            Map<String, AllowedSourceUnit> allowlist,
            Set<UUID> validUnitIds,
            GitHubEvidenceCandidate candidate) {
        if (candidate == null
                || candidate.sourceUnitReferences().isEmpty()
                || candidate.sourceUnitReferences().size() > 20) {
            throw rejected(RejectionReason.INVALID_PROVENANCE);
        }
        if (candidate.confidence() == null
                || candidate.confidence().signum() < 0
                || candidate.confidence().compareTo(BigDecimal.ONE) > 0) {
            throw rejected(RejectionReason.INVALID_CONFIDENCE);
        }
        String category = requiredLabel(candidate.evidenceCategory(), 80, RejectionReason.INVALID_CATEGORY);
        if (!ALLOWED_CATEGORIES.contains(category)
                || ProfilePolicy.isEducationEvidenceCategory(category)) {
            throw rejected(RejectionReason.INVALID_CATEGORY);
        }
        String title = requiredLabel(candidate.title(), 250, RejectionReason.INVALID_CONTENT);
        String content = requiredContent(candidate.content());
        Map<String, Object> metadata = metadata(candidate.metadata());
        List<UUID> sourceUnitIds = new ArrayList<>();
        StringBuilder groundedContent = new StringBuilder();
        for (String reference : candidate.sourceUnitReferences()) {
            AllowedSourceUnit unit = allowlist.get(reference);
            if (unit == null || !validUnitIds.contains(unit.id()) || sourceUnitIds.contains(unit.id())) {
                throw rejected(RejectionReason.INVALID_PROVENANCE);
            }
            sourceUnitIds.add(unit.id());
            groundedContent.append(unit.content()).append('\n');
        }
        Matcher matcher = NUMBER.matcher(content);
        while (matcher.find()) {
            if (groundedContent.indexOf(matcher.group()) < 0) {
                throw rejected(RejectionReason.UNGROUNDED_NUMBER);
            }
        }
        String claimKey = sha256(repositoryId + "|"
                + ExperienceSimilarityPolicy.comparisonGroup(category) + "|"
                + normalize(title) + "|" + normalize(content));
        return new Candidate(
                category,
                title,
                content,
                metadata,
                candidate.confidence(),
                sourceUnitIds.getFirst(),
                sourceUnitIds.stream().skip(1).toList(),
                claimKey,
                candidate.embedding());
    }

    private String requiredContent(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000 || value.indexOf('\0') >= 0) {
            throw rejected(RejectionReason.INVALID_CONTENT);
        }
        return value;
    }

    private String requiredLabel(String value, int maxLength, RejectionReason reason) {
        try {
            return ProfilePolicy.requiredLabel(value, maxLength);
        } catch (RuntimeException exception) {
            throw rejected(reason);
        }
    }

    private Map<String, Object> metadata(Map<String, Object> values) {
        Map<String, Object> result = values == null ? Map.of() : new LinkedHashMap<>(values);
        if (result.values().stream().anyMatch(value -> value != null
                && !(value instanceof String)
                && !(value instanceof Number)
                && !(value instanceof Boolean))) {
            throw rejected(RejectionReason.INVALID_METADATA);
        }
        try {
            if (objectMapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8).length > 16_384) {
                throw rejected(RejectionReason.INVALID_METADATA);
            }
        } catch (JacksonException exception) {
            throw rejected(RejectionReason.INVALID_METADATA);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Rejection rejected(RejectionReason reason) {
        return new Rejection(reason);
    }

    public record AllowedSourceUnit(UUID id, String content) {
        public AllowedSourceUnit {
            java.util.Objects.requireNonNull(id);
            content = content == null ? "" : content;
        }
    }

    public record ValidationResult(
            List<Candidate> accepted,
            Map<RejectionReason, Integer> rejectionReasonCounts) {
        public ValidationResult {
            accepted = List.copyOf(accepted);
            rejectionReasonCounts = Map.copyOf(rejectionReasonCounts);
        }

        public int rejectedCount() {
            return rejectionReasonCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public enum RejectionReason {
        INVALID_PROVENANCE,
        INVALID_CONFIDENCE,
        INVALID_CATEGORY,
        INVALID_CONTENT,
        INVALID_METADATA,
        UNGROUNDED_NUMBER,
        DUPLICATE,
        LIMIT_EXCEEDED,
        OTHER_SAFE_REJECTION
    }

    private static final class Rejection extends RuntimeException {
        private final RejectionReason reason;

        private Rejection(RejectionReason reason) {
            this.reason = reason;
        }
    }
}
