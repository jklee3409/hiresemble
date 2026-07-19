package com.hiresemble.ai.context;

import com.hiresemble.agentrun.application.AgentRunSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Builds an in-memory, provenance-only context snapshot; source bodies are not part of this contract. */
public interface ContextBuilder {

    ContextSnapshot build(ContextRequest request);

    record ContextRequest(AgentRunSnapshot run) {
        public ContextRequest {
            Objects.requireNonNull(run, "run");
        }
    }

    record ContextSnapshot(
            UUID userId,
            List<ResourceSnapshotRef> resources,
            List<OutputRef> upstreamOutputRefs,
            List<ContextRef> contextRefs,
            TruncationSummary truncationSummary,
            String contextHash,
            String verificationState,
            long modelPolicyVersion,
            boolean highQualityEnabled,
            boolean budgetReservationConfirmed) {

        private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

        public ContextSnapshot {
            Objects.requireNonNull(userId, "userId");
            resources = resources == null ? List.of() : List.copyOf(resources);
            upstreamOutputRefs = upstreamOutputRefs == null ? List.of() : List.copyOf(upstreamOutputRefs);
            contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs);
            truncationSummary = truncationSummary == null
                    ? new TruncationSummary(0, 0, List.of()) : truncationSummary;
            if (contextHash == null || !HASH.matcher(contextHash).matches()) {
                throw new IllegalArgumentException("context hash is invalid");
            }
            if (verificationState == null || verificationState.isBlank()
                    || verificationState.length() > 50 || modelPolicyVersion < 1) {
                throw new IllegalArgumentException("context policy projection is invalid");
            }
        }
    }

    record ResourceSnapshotRef(
            String resourceType,
            UUID resourceId,
            long resourceVersion,
            String contentHash) {
        public ResourceSnapshotRef {
            requireText(resourceType, 50, "resourceType");
            Objects.requireNonNull(resourceId, "resourceId");
            if (resourceVersion < 0 || contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("resource snapshot reference is invalid");
            }
        }
    }

    record OutputRef(String stepKey, String scopeKey, String outputHash) {
        public OutputRef {
            requireText(stepKey, 100, "stepKey");
            if (scopeKey != null) requireText(scopeKey, 100, "scopeKey");
            if (outputHash == null || !outputHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("output hash is invalid");
            }
        }
    }

    record ContextRef(
            String referenceType,
            UUID referenceId,
            Long version,
            String verificationState) {
        public ContextRef {
            requireText(referenceType, 50, "referenceType");
            Objects.requireNonNull(referenceId, "referenceId");
            if (version != null && version < 0) {
                throw new IllegalArgumentException("context version is invalid");
            }
            requireText(verificationState, 50, "verificationState");
        }
    }

    record TruncationSummary(int includedRefCount, int omittedRefCount, List<String> omittedKinds) {
        public TruncationSummary {
            omittedKinds = omittedKinds == null ? List.of() : List.copyOf(omittedKinds);
            if (includedRefCount < 0 || omittedRefCount < 0 || omittedKinds.size() > 50
                    || omittedKinds.stream().anyMatch(value -> value == null
                            || value.isBlank() || value.length() > 50)) {
                throw new IllegalArgumentException("truncation summary is invalid");
            }
        }
    }

    private static void requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
