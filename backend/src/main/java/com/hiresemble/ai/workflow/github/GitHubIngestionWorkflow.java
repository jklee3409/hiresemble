package com.hiresemble.ai.workflow.github;

import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.PartialResult;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.validation.StructuredOutputValidationException;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.TerminalPartialPolicy;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.document.domain.model.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator.RejectionReason;
import com.hiresemble.githubsource.application.GitHubCandidateProvenanceValidator.ValidationResult;
import com.hiresemble.githubsource.application.GitHubEvidenceCandidate;
import com.hiresemble.githubsource.application.GitHubGatewayException;
import com.hiresemble.githubsource.application.GitHubWorkflowCommandPort;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.ApplySummary;
import com.hiresemble.githubsource.application.GitHubWorkflowModels.SnapshotBundle;
import com.hiresemble.githubsource.application.GitHubWorkflowQueryPort;
import com.hiresemble.githubsource.domain.GitHubSourceKind;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Repository;
import com.hiresemble.githubsource.domain.GitHubSourceRecords.Source;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fixed, bounded, public-only GitHub ingestion workflow. */
public final class GitHubIngestionWorkflow {

    public static final String VALIDATE_GITHUB_SOURCE = "VALIDATE_GITHUB_SOURCE";
    public static final String DISCOVER_REPOSITORIES = "DISCOVER_REPOSITORIES";
    public static final String WAIT_FOR_REPOSITORY_SELECTION = "WAIT_FOR_REPOSITORY_SELECTION";
    public static final String CAPTURE_REPOSITORY_SNAPSHOTS = "CAPTURE_REPOSITORY_SNAPSHOTS";
    public static final String SANITIZE_AND_SELECT_SOURCE_UNITS = "SANITIZE_AND_SELECT_SOURCE_UNITS";
    public static final String EXTRACT_GITHUB_CANDIDATES = "EXTRACT_GITHUB_CANDIDATES";
    public static final String VALIDATE_GITHUB_CANDIDATES = "VALIDATE_GITHUB_CANDIDATES";
    public static final String EMBED_GITHUB_CANDIDATES = "EMBED_GITHUB_CANDIDATES";
    public static final String APPLY_CANONICAL_EXPERIENCES = "APPLY_CANONICAL_EXPERIENCES";
    public static final String FINALIZE_GITHUB_SOURCE = "FINALIZE_GITHUB_SOURCE";

    public static final int MAX_CANDIDATES_PER_REPOSITORY = 12;
    public static final int MAX_CANDIDATES_PER_RUN = 40;
    private static final int EMBEDDING_DIMENSION = 1536;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(30);

    private final GitHubWorkflowQueryPort queryPort;
    private final GitHubWorkflowCommandPort commandPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GitHubIngestionWorkflow(
            GitHubWorkflowQueryPort queryPort,
            GitHubWorkflowCommandPort commandPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.queryPort = queryPort;
        this.commandPort = commandPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.GITHUB_INGESTION,
                CanonicalWorkflowDefinitions.GITHUB_INGESTION_VERSION,
                TerminalPartialPolicy.succeed(),
                List.of(
                        step(VALIDATE_GITHUB_SOURCE, new ValidateSourceExecutor()),
                        step(DISCOVER_REPOSITORIES, new DiscoverExecutor()),
                        step(WAIT_FOR_REPOSITORY_SELECTION, new WaitSelectionExecutor()),
                        step(CAPTURE_REPOSITORY_SNAPSHOTS, new CaptureExecutor()),
                        step(SANITIZE_AND_SELECT_SOURCE_UNITS, new SanitizeExecutor()),
                        step(EXTRACT_GITHUB_CANDIDATES, new ExtractExecutor()),
                        step(VALIDATE_GITHUB_CANDIDATES, new ValidateCandidatesExecutor()),
                        step(EMBED_GITHUB_CANDIDATES, new EmbedCandidatesExecutor()),
                        step(APPLY_CANONICAL_EXPERIENCES, new ApplyExecutor()),
                        step(FINALIZE_GITHUB_SOURCE, new FinalizeExecutor())));
    }

    private ExecutableWorkflowStep step(String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class GitHubExecutor<T> implements WorkflowStepExecutor<T> {
        private final String stepKey;
        private final Class<T> outputType;
        private final Set<String> fields;

        private GitHubExecutor(String stepKey, Class<T> outputType) {
            this.stepKey = stepKey;
            this.outputType = outputType;
            this.fields = java.util.Arrays.stream(outputType.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public final Contract<T> outputContract() {
            return contract(null);
        }

        @Override
        public final Contract<T> outputContract(StepExecutionContext context) {
            return contract(context);
        }

        private Contract<T> contract(StepExecutionContext context) {
            return new Contract<>(
                    outputType,
                    outputSchemaVersion(stepKey),
                    node -> {
                        if (node == null
                                || !node.isObject()
                                || !fields.equals(Set.copyOf(node.propertyNames()))) {
                            throw StructuredOutputValidationException.deterministic(
                                    ValidationPhase.SCHEMA_SHAPE,
                                    "GITHUB_OUTPUT_SCHEMA_INVALID");
                        }
                    },
                    output -> validateRecord(output, context),
                    output -> validateWorkflow(output, context),
                    output -> {});
        }

        protected void validateRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflow(T output, StepExecutionContext context) {}

        protected final GitHubState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType() != WorkflowType.GITHUB_INGESTION
                    || !CanonicalWorkflowDefinitions.GITHUB_INGESTION_VERSION.equals(
                            context.run().workflowVersion())
                    || !"GITHUB_SOURCE".equals(context.run().resourceType())
                    || context.run().resourceId() == null) {
                throw configurationFailure();
            }
            UUID sourceId;
            try {
                sourceId = UUID.fromString(context.run().inputReferenceSnapshot()
                        .path("githubSourceId").asText());
            } catch (RuntimeException exception) {
                throw ownerFailure();
            }
            if (!sourceId.equals(context.run().resourceId())) throw ownerFailure();
            Source source = queryPort.source(context.run().userId(), sourceId);
            if (!context.run().id().equals(source.latestAgentRunId())) throw ownerFailure();
            return new GitHubState(source, context.run().id());
        }

        protected final JsonNode stableRefs(StepExecutionContext context) {
            JsonNode input = context.run().inputReferenceSnapshot();
            return objectMapper.createObjectNode()
                    .put("githubSourceId", context.run().resourceId().toString())
                    .put("inputSourceRevision", input.path("sourceRevision").asLong())
                    .put("sourceKind", input.path("sourceKind").asText())
                    .put("retrievalPolicyVersion", input.path("retrievalPolicyVersion").asText());
        }

        protected final StepInput localInput(
                StepExecutionContext context, JsonNode refs, String material) {
            return new StepInput(
                    context.scopeKey(),
                    refs,
                    stepKey + "|" + context.run().canonicalInputHash() + "|" + material,
                    refs.deepCopy(),
                    null,
                    state(context).source().version());
        }

        protected final AiGatewayResponse localResponse(Object output) {
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), List.of());
            } catch (Exception exception) {
                throw configurationFailure();
            }
        }

        protected final JsonNode tree(Object output) {
            return objectMapper.valueToTree(output);
        }
    }

    private final class ValidateSourceExecutor extends GitHubExecutor<SourceValidationOutput> {
        private ValidateSourceExecutor() {
            super(VALIDATE_GITHUB_SOURCE, SourceValidationOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return localInput(context, stableRefs(context), "validate");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GitHubState state = state(invocation.executionContext());
            Source source = commandPort.begin(
                    state.source().userId(), state.source().id(), state.runId(), clock.instant());
            return localResponse(new SourceValidationOutput(
                    source.id(), source.sourceRevision(), source.sourceKind()));
        }

        @Override
        public JsonNode minimalOutput(SourceValidationOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private final class DiscoverExecutor extends GitHubExecutor<DiscoveryOutput> {
        private DiscoverExecutor() {
            super(DISCOVER_REPOSITORIES, DiscoveryOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            return localInput(context, stableRefs(context), "discover");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GitHubState state = state(invocation.executionContext());
            try {
                var discovery = commandPort.discover(
                        state.source().userId(), state.source().id(), state.runId(), clock.instant());
                Source source = discovery.source();
                return localResponse(new DiscoveryOutput(
                        source.id(),
                        source.discoveredRepositoryCount(),
                        source.selectedRepositoryCount(),
                        source.repositoryDiscoveryTruncated(),
                        source.sourceKind() == GitHubSourceKind.ACCOUNT
                                && source.selectedRepositoryCount() == 0));
            } catch (GitHubGatewayException exception) {
                throw gatewayFailure(exception);
            }
        }

        @Override
        public JsonNode minimalOutput(DiscoveryOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private final class WaitSelectionExecutor extends GitHubExecutor<SelectionOutput> {
        private WaitSelectionExecutor() {
            super(WAIT_FOR_REPOSITORY_SELECTION, SelectionOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GitHubState state = state(context);
            JsonNode refs = stableRefs(context);
            RequiredUserAction action = null;
            if (state.source().sourceKind() == GitHubSourceKind.ACCOUNT
                    && queryPort.selectedRepositories(
                                    state.source().userId(), state.source().id())
                            .isEmpty()) {
                action = new RequiredUserAction(
                        RequiredUserActionType.SELECT_GITHUB_REPOSITORIES,
                        new ResourceReference("GITHUB_SOURCE", state.source().id(), null),
                        "/profile/github",
                        "Select between one and ten discovered public repositories.");
            }
            return new StepInput(
                    null,
                    refs,
                    WAIT_FOR_REPOSITORY_SELECTION + "|" + context.run().canonicalInputHash(),
                    refs.deepCopy(),
                    action,
                    state.source().version());
        }

        @Override
        public boolean skip(StepExecutionContext context) {
            return state(context).source().sourceKind() == GitHubSourceKind.REPOSITORY;
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GitHubState state = state(invocation.executionContext());
            Source running = commandPort.begin(
                    state.source().userId(), state.source().id(), state.runId(), clock.instant());
            int selected = queryPort.selectedRepositories(
                            running.userId(), running.id())
                    .size();
            if (selected < 1 || selected > 10) throw domainFailure("GITHUB_SELECTION_INVALID");
            return localResponse(new SelectionOutput(running.id(), selected, false));
        }

        @Override
        public JsonNode minimalOutput(SelectionOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private abstract class RepositoryExecutor<T> extends GitHubExecutor<T> {
        private RepositoryExecutor(String stepKey, Class<T> outputType) {
            super(stepKey, outputType);
        }

        protected List<StepInput> repositoryInputs(
                StepExecutionContext context, String upstreamStep, String material) {
            GitHubState state = state(context);
            List<Repository> repositories = queryPort.selectedRepositories(
                    state.source().userId(), state.source().id());
            Set<String> successfulScopes = upstreamStep == null
                    ? Set.of()
                    : context.scopedUpstream(upstreamStep).keySet();
            List<StepInput> inputs = new ArrayList<>();
            for (int index = 0; index < repositories.size(); index++) {
                String scopeKey = "R" + (index + 1);
                if (upstreamStep != null && !successfulScopes.contains(scopeKey)) continue;
                Repository repository = repositories.get(index);
                JsonNode refs = stableRefs(context).deepCopy();
                ((tools.jackson.databind.node.ObjectNode) refs)
                        .put("repositoryId", repository.id().toString())
                        .put("selectionOrder", index + 1);
                inputs.add(new StepInput(
                        scopeKey,
                        refs,
                        material + "|" + repository.id(),
                        refs.deepCopy(),
                        null,
                        state(context).source().version()));
            }
            return List.copyOf(inputs);
        }

        protected Repository repository(GatewayInvocation invocation) {
            UUID repositoryId = UUID.fromString(
                    invocation.input().sanitizedInputRefs().path("repositoryId").asText());
            GitHubState state = state(invocation.executionContext());
            return queryPort.selectedRepositories(state.source().userId(), state.source().id())
                    .stream()
                    .filter(value -> value.id().equals(repositoryId))
                    .findFirst()
                    .orElseThrow(() -> ownerFailure());
        }

        @Override
        public boolean continueAfterScopeFailure(
                AiExecutionException failure, StepExecutionContext context) {
            return failure.failureKind() != FailureKind.OWNER
                    && failure.failureKind() != FailureKind.CONFIGURATION
                    && failure.failureKind() != FailureKind.CANCELLATION
                    && failure.failureKind() != FailureKind.INTERRUPTION;
        }
    }

    private final class CaptureExecutor extends RepositoryExecutor<CaptureOutput> {
        private CaptureExecutor() {
            super(CAPTURE_REPOSITORY_SNAPSHOTS, CaptureOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StepInput> prepareInputs(StepExecutionContext context) {
            return repositoryInputs(context, null, "capture");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GitHubState state = state(invocation.executionContext());
            try {
                SnapshotBundle bundle = commandPort.captureAndStore(
                        state.source().userId(),
                        state.source().id(),
                        repository(invocation),
                        clock.instant());
                return localResponse(new CaptureOutput(
                        bundle.repository().id(),
                        bundle.reused(),
                        bundle.incomplete(),
                        bundle.units().size(),
                        abbreviated(bundle.snapshot().commitSha())));
            } catch (GitHubGatewayException exception) {
                throw gatewayFailure(exception);
            }
        }

        @Override
        public JsonNode minimalOutput(CaptureOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private final class SanitizeExecutor extends RepositoryExecutor<SanitizeOutput> {
        private SanitizeExecutor() {
            super(SANITIZE_AND_SELECT_SOURCE_UNITS, SanitizeOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StepInput> prepareInputs(StepExecutionContext context) {
            return repositoryInputs(context, CAPTURE_REPOSITORY_SNAPSHOTS, "sanitize");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GitHubState state = state(invocation.executionContext());
            Repository repository = repository(invocation);
            SnapshotBundle bundle = queryPort.snapshotBundle(
                    state.source().userId(), state.source().id(), repository.id());
            return localResponse(new SanitizeOutput(
                    repository.id(),
                    bundle.units().size(),
                    !bundle.incomplete(),
                    bundle.units().stream().mapToInt(value ->
                                    value.content().codePointCount(0, value.content().length()))
                            .sum()));
        }

        @Override
        public JsonNode minimalOutput(SanitizeOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private final class ExtractExecutor extends RepositoryExecutor<CandidateBatch> {
        private ExtractExecutor() {
            super(EXTRACT_GITHUB_CANDIDATES, CandidateBatch.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StepInput> prepareInputs(StepExecutionContext context) {
            List<StepInput> base = repositoryInputs(
                    context, SANITIZE_AND_SELECT_SOURCE_UNITS, "extract");
            List<StepInput> result = new ArrayList<>();
            for (StepInput input : base) {
                UUID repositoryId = UUID.fromString(input.sanitizedInputRefs()
                        .path("repositoryId").asText());
                GitHubState state = state(context);
                SnapshotBundle bundle = queryPort.snapshotBundle(
                        state.source().userId(), state.source().id(), repositoryId);
                var payload = objectMapper.createObjectNode()
                        .put("repository", bundle.repository().ownerLogin() + "/"
                                + bundle.repository().repositoryName())
                        .put("maxCandidates", MAX_CANDIDATES_PER_REPOSITORY);
                var units = payload.putArray("sourceUnits");
                bundle.units().forEach(unit -> units.addObject()
                        .put("sourceUnitRef", unit.opaqueReference())
                        .put("unitType", unit.unit().unitType())
                        .put("path", unit.unit().repositoryPath())
                        .put("content", "<untrusted_repository_content source_unit_ref=\""
                                + unit.opaqueReference() + "\">\n"
                                + unit.content()
                                + "\n</untrusted_repository_content>"));
                result.add(new StepInput(
                        input.scopeKey(),
                        input.sanitizedInputRefs(),
                        input.canonicalInputMaterial() + "|" + bundle.snapshot().checksumSha256(),
                        payload,
                        null,
                        input.expectedResourceVersion()));
            }
            return List.copyOf(result);
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    Set.of(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType(),
                    "low",
                    "low"));
        }

        @Override
        public JsonNode minimalOutput(CandidateBatch output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode().put("candidateCount", output.candidates().size());
            var hashes = result.putArray("candidateHashes");
            output.candidates().forEach(candidate -> hashes.add(candidateHash(candidate)));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateRecord(CandidateBatch output, StepExecutionContext context) {
            int alreadyAccepted = context == null
                    ? 0
                    : context.scopedEphemeral(EXTRACT_GITHUB_CANDIDATES).values().stream()
                            .filter(CandidateBatch.class::isInstance)
                            .map(CandidateBatch.class::cast)
                            .mapToInt(value -> value.candidates().size())
                            .sum();
            int maximum = Math.min(
                    MAX_CANDIDATES_PER_REPOSITORY,
                    Math.max(0, MAX_CANDIDATES_PER_RUN - alreadyAccepted));
            if (output == null || output.candidates() == null || output.candidates().size() > maximum) {
                throw StructuredOutputValidationException.repairable(
                        ValidationPhase.JAVA_RECORD,
                        "GITHUB_CANDIDATE_LIMIT_INVALID",
                        "Return at most " + maximum + " candidates using only the supplied source unit references.");
            }
        }
    }

    private final class ValidateCandidatesExecutor extends RepositoryExecutor<ValidatedBatch> {
        private ValidateCandidatesExecutor() {
            super(VALIDATE_GITHUB_CANDIDATES, ValidatedBatch.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StepInput> prepareInputs(StepExecutionContext context) {
            return repositoryInputs(context, EXTRACT_GITHUB_CANDIDATES, "validate-candidates");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            String scope = invocation.executionContext().scopeKey();
            Object handoff = invocation.executionContext().ephemeral(EXTRACT_GITHUB_CANDIDATES, scope);
            if (!(handoff instanceof CandidateBatch batch)) throw configurationFailure();
            GitHubState state = state(invocation.executionContext());
            Repository repository = repository(invocation);
            SnapshotBundle bundle = queryPort.snapshotBundle(
                    state.source().userId(), state.source().id(), repository.id());
            List<GitHubEvidenceCandidate> values = batch.candidates().stream()
                    .map(value -> new GitHubEvidenceCandidate(
                            value.evidenceCategory(),
                            value.title(),
                            value.content(),
                            Map.of(),
                            value.confidence(),
                            value.sourceUnitReferences(),
                            List.of()))
                    .toList();
            ValidationResult validated = commandPort.validateCandidates(
                    state.source().userId(),
                    state.source().id(),
                    state.source().sourceRevision(),
                    bundle,
                    values);
            return localResponse(new ValidatedBatch(
                    repository.id(),
                    bundle.snapshot().id(),
                    state.source().sourceRevision(),
                    validated.accepted(),
                    validated.rejectionReasonCounts()));
        }

        @Override
        public JsonNode minimalOutput(ValidatedBatch output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("repositoryId", output.repositoryId().toString())
                    .put("acceptedCount", output.candidates().size())
                    .put("rejectedCount", output.rejectionReasonCounts().values().stream()
                            .mapToInt(Integer::intValue).sum());
            var reasons = result.putObject("rejectionReasonCounts");
            output.rejectionReasonCounts().forEach((reason, count) -> reasons.put(reason.name(), count));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class EmbedCandidatesExecutor extends RepositoryExecutor<EmbeddedBatch> {
        private EmbedCandidatesExecutor() {
            super(EMBED_GITHUB_CANDIDATES, EmbeddedBatch.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StepInput> prepareInputs(StepExecutionContext context) {
            List<StepInput> base = repositoryInputs(
                    context, VALIDATE_GITHUB_CANDIDATES, "embed-candidates");
            List<StepInput> result = new ArrayList<>();
            for (StepInput input : base) {
                Object value = context.ephemeral(VALIDATE_GITHUB_CANDIDATES, input.scopeKey());
                if (!(value instanceof ValidatedBatch batch)) throw configurationFailure();
                EmbeddingPolicy policy = queryPort.activeEmbeddingPolicy();
                requireActivePolicy(policy);
                var payload = objectMapper.createObjectNode()
                        .put("policyVersion", policy.version())
                        .put("dimension", policy.dimension())
                        .put("generation", policy.generation());
                var maskedInputs = payload.putArray("maskedInputs");
                batch.candidates().forEach(candidate -> maskedInputs.add(
                        candidate.category() + "\n" + candidate.title() + "\n" + candidate.content()));
                result.add(new StepInput(
                        input.scopeKey(),
                        input.sanitizedInputRefs(),
                        input.canonicalInputMaterial() + "|" + policy.version() + "|" + policy.generation(),
                        payload,
                        null,
                        input.expectedResourceVersion()));
            }
            return List.copyOf(result);
        }

        @Override
        public boolean requiresProvider(StepExecutionContext context) {
            Object value = context.ephemeral(VALIDATE_GITHUB_CANDIDATES, context.scopeKey());
            return value instanceof ValidatedBatch batch && !batch.candidates().isEmpty();
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            Object value = invocation.executionContext()
                    .ephemeral(VALIDATE_GITHUB_CANDIDATES, invocation.executionContext().scopeKey());
            if (!(value instanceof ValidatedBatch batch)) throw configurationFailure();
            EmbeddingPolicy policy = queryPort.activeEmbeddingPolicy();
            requireActivePolicy(policy);
            if (batch.candidates().isEmpty()) {
                return localResponse(new EmbeddedBatch(
                        batch.repositoryId(),
                        batch.snapshotId(),
                        batch.sourceRevision(),
                        policy,
                        List.of(),
                        batch.rejectionReasonCounts()));
            }
            List<String> inputs = batch.candidates().stream()
                    .map(candidate -> candidate.category() + "\n"
                            + candidate.title() + "\n" + candidate.content())
                    .toList();
            AiGatewayResponse response = invocation.embeddingGateway().embed(new EmbeddingRequest(
                    policy.provider(),
                    policy.model(),
                    inputs,
                    policy.dimension(),
                    EMBEDDING_TIMEOUT,
                    invocation.executionContext().run().priceVersion()));
            try {
                EmbeddingValuesOutput vectors = objectMapper.readValue(
                        response.rawJson(), EmbeddingValuesOutput.class);
                if (vectors.vectors() == null || vectors.vectors().size() != batch.candidates().size()) {
                    throw structuredFailure("GITHUB_EMBEDDING_COUNT_INVALID");
                }
                List<com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate>
                        embedded = new ArrayList<>();
                for (int index = 0; index < batch.candidates().size(); index++) {
                    var candidate = batch.candidates().get(index);
                    embedded.add(new com.hiresemble.profile.application.service
                            .CanonicalExperienceCandidateService.Candidate(
                            candidate.category(),
                            candidate.title(),
                            candidate.content(),
                            candidate.metadata(),
                            candidate.confidence(),
                            candidate.primarySourceReference(),
                            candidate.supportingSourceReferences(),
                            candidate.sourceClaimKey(),
                            vectors.vectors().get(index)));
                }
                return new AiGatewayResponse(objectMapper.writeValueAsString(new EmbeddedBatch(
                        batch.repositoryId(),
                        batch.snapshotId(),
                        batch.sourceRevision(),
                        policy,
                        embedded,
                        batch.rejectionReasonCounts())), response.usages());
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (Exception exception) {
                throw structuredFailure("GITHUB_EMBEDDING_OUTPUT_INVALID");
            }
        }

        @Override
        public JsonNode minimalOutput(EmbeddedBatch output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("repositoryId", output.repositoryId().toString())
                    .put("embeddedCandidateCount", output.candidates().size())
                    .put("policyVersion", output.embeddingPolicy().version())
                    .put("dimension", output.embeddingPolicy().dimension())
                    .put("generation", output.embeddingPolicy().generation());
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class ApplyExecutor extends RepositoryExecutor<ApplyOutput> {
        private ApplyExecutor() {
            super(APPLY_CANONICAL_EXPERIENCES, ApplyOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StepInput> prepareInputs(StepExecutionContext context) {
            return repositoryInputs(context, EMBED_GITHUB_CANDIDATES, "apply");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            Object value = invocation.executionContext()
                    .ephemeral(EMBED_GITHUB_CANDIDATES, invocation.executionContext().scopeKey());
            if (!(value instanceof EmbeddedBatch batch)) throw configurationFailure();
            GitHubState state = state(invocation.executionContext());
            SnapshotBundle bundle = queryPort.snapshotBundle(
                    state.source().userId(), state.source().id(), batch.repositoryId());
            ValidationResult validation = new ValidationResult(
                    batch.candidates(), batch.rejectionReasonCounts());
            ApplySummary summary = commandPort.applyCandidates(
                    state.source().userId(),
                    state.source().id(),
                    bundle,
                    validation,
                    batch.embeddingPolicy(),
                    clock.instant());
            return localResponse(new ApplyOutput(
                    summary.repositoryId(),
                    summary.result().appliedEvidenceIds(),
                    summary.newCount(),
                    summary.corroboratedCount(),
                    summary.reviewRequiredCount(),
                    summary.rejectedCount()));
        }

        @Override
        public JsonNode minimalOutput(ApplyOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("repositoryId", output.repositoryId().toString())
                    .put("appliedCount", output.appliedEvidenceIds().size())
                    .put("newCount", output.newCount())
                    .put("corroboratedCount", output.corroboratedCount())
                    .put("reviewRequiredCount", output.reviewRequiredCount())
                    .put("rejectedCount", output.rejectedCount());
        }

        @Override
        public Optional<PartialResult> partialResult(
                ApplyOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            return Optional.of(new PartialResult(
                    List.of(context.scopeKey()), List.of(), List.of()));
        }

        @Override
        public boolean reusable() {
            return false;
        }
    }

    private final class FinalizeExecutor extends GitHubExecutor<FinalOutput> {
        private FinalizeExecutor() {
            super(FINALIZE_GITHUB_SOURCE, FinalOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            GitHubState state = state(context);
            JsonNode refs = stableRefs(context).deepCopy();
            ((tools.jackson.databind.node.ObjectNode) refs).put(
                    "selectedRepositoryCount",
                    queryPort.selectedRepositories(state.source().userId(), state.source().id()).size());
            return localInput(context, refs, "finalize");
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            GitHubState state = state(invocation.executionContext());
            List<Repository> selected = queryPort.selectedRepositories(
                    state.source().userId(), state.source().id());
            List<ApplyOutput> outputs = invocation.executionContext()
                    .scopedEphemeral(APPLY_CANONICAL_EXPERIENCES)
                    .values()
                    .stream()
                    .filter(ApplyOutput.class::isInstance)
                    .map(ApplyOutput.class::cast)
                    .toList();
            if (!selected.isEmpty() && outputs.isEmpty()) {
                throw domainFailure("GITHUB_ALL_REPOSITORIES_FAILED");
            }
            boolean incompleteSnapshot = invocation.executionContext()
                    .scopedUpstream(SANITIZE_AND_SELECT_SOURCE_UNITS)
                    .values()
                    .stream()
                    .anyMatch(value -> !value.path("selectionComplete").asBoolean());
            boolean partial = outputs.size() < selected.size() || incompleteSnapshot;
            List<ApplySummary> summaries = outputs.stream()
                    .map(output -> new ApplySummary(
                            output.repositoryId(),
                            null,
                            new com.hiresemble.profile.application.service
                                    .CanonicalExperienceCandidateService.ApplyResult(
                                    output.appliedEvidenceIds(), Map.of(), matchCounts(output)),
                            output.rejectedCount(),
                            output.newCount(),
                            output.corroboratedCount(),
                            output.reviewRequiredCount()))
                    .toList();
            var finalized = commandPort.finalizeSource(
                    state.source().userId(),
                    state.source().id(),
                    state.runId(),
                    partial,
                    summaries,
                    Math.max(0, selected.size() - outputs.size()),
                    clock.instant());
            Source source = finalized.source();
            return localResponse(new FinalOutput(
                    source.id(),
                    source.status().name(),
                    source.newExperienceCount(),
                    source.corroboratedExperienceCount(),
                    source.reviewRequiredCount(),
                    source.rejectedCandidateCount(),
                    finalized.partial()));
        }

        @Override
        public JsonNode minimalOutput(FinalOutput output, ObjectMapper ignored) {
            return tree(output);
        }
    }

    private Map<com.hiresemble.profile.domain.model.ExperienceMatchKind, Integer> matchCounts(
            ApplyOutput output) {
        EnumMap<com.hiresemble.profile.domain.model.ExperienceMatchKind, Integer> counts =
                new EnumMap<>(com.hiresemble.profile.domain.model.ExperienceMatchKind.class);
        if (output.newCount() > 0) counts.put(
                com.hiresemble.profile.domain.model.ExperienceMatchKind.NEW, output.newCount());
        if (output.corroboratedCount() > 0) counts.put(
                com.hiresemble.profile.domain.model.ExperienceMatchKind.SAME_EXPERIENCE,
                output.corroboratedCount());
        if (output.reviewRequiredCount() > 0) counts.put(
                com.hiresemble.profile.domain.model.ExperienceMatchKind.RELATED_DIFFERENT,
                output.reviewRequiredCount());
        return Map.copyOf(counts);
    }

    private void requireActivePolicy(EmbeddingPolicy policy) {
        if (policy == null
                || policy.version() < 1
                || !"openai".equals(policy.provider())
                || !"text-embedding-3-small".equals(policy.model())
                || policy.dimension() != EMBEDDING_DIMENSION
                || !"cosine".equalsIgnoreCase(policy.distance())
                || policy.generation() < 1) {
            throw configurationFailure();
        }
    }

    private String outputSchemaVersion(String stepKey) {
        return "github-" + stepKey.toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                + "-output-v1";
    }

    private String candidateHash(ExtractedCandidate candidate) {
        return sha256(candidate.evidenceCategory() + "|" + candidate.title() + "|"
                + candidate.content() + "|" + candidate.sourceUnitReferences());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String abbreviated(String commitSha) {
        return commitSha.substring(0, Math.min(10, commitSha.length()));
    }

    private AiExecutionException gatewayFailure(GitHubGatewayException exception) {
        return switch (exception.kind()) {
            case RATE_LIMITED -> AiExecutionException.retryable(
                    FailureKind.RATE_LIMIT,
                    "GITHUB_RATE_LIMITED",
                    "GitHub temporarily rate-limited the request.");
            case UPSTREAM_5XX -> AiExecutionException.retryable(
                    FailureKind.PROVIDER_5XX,
                    "GITHUB_UPSTREAM_UNAVAILABLE",
                    "GitHub is temporarily unavailable.");
            case TIMEOUT -> AiExecutionException.retryable(
                    FailureKind.TIMEOUT,
                    "GITHUB_REQUEST_TIMEOUT",
                    "GitHub did not respond within the bounded timeout.");
            case NOT_FOUND -> AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    "GITHUB_SOURCE_NOT_ACCESSIBLE",
                    "The public GitHub source is not accessible.");
            case RESPONSE_LIMIT -> AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    "GITHUB_SOURCE_LIMIT_EXCEEDED",
                    "The GitHub response exceeded the safe ingestion limit.");
            case INVALID_RESPONSE -> AiExecutionException.nonRetryable(
                    FailureKind.DOMAIN_VALIDATION,
                    "GITHUB_RESPONSE_INVALID",
                    "GitHub returned an invalid bounded response.");
        };
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                "RESOURCE_NOT_FOUND",
                "The requested GitHub source could not be found.");
    }

    private AiExecutionException configurationFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "GITHUB_WORKFLOW_CONFIGURATION_INVALID",
                "The GitHub ingestion workflow is not configured.");
    }

    private AiExecutionException domainFailure(String code) {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                code,
                "The GitHub source could not be safely ingested.");
    }

    private AiExecutionException structuredFailure(String code) {
        return AiExecutionException.deterministicStructuredOutput(
                code,
                "The GitHub AI result was not valid.",
                ValidationPhase.JAVA_RECORD);
    }

    private record GitHubState(Source source, UUID runId) {}

    public record SourceValidationOutput(
            UUID githubSourceId, long sourceRevision, GitHubSourceKind sourceKind) {}

    public record DiscoveryOutput(
            UUID githubSourceId,
            int discoveredRepositoryCount,
            int selectedRepositoryCount,
            boolean repositoryDiscoveryTruncated,
            boolean repositorySelectionRequired) {}

    public record SelectionOutput(UUID githubSourceId, int selectedRepositoryCount, boolean skipped) {}

    public record CaptureOutput(
            UUID repositoryId,
            boolean reused,
            boolean incomplete,
            int sourceUnitCount,
            String commitAbbreviation) {}

    public record SanitizeOutput(
            UUID repositoryId,
            int sourceUnitCount,
            boolean selectionComplete,
            int sanitizedCodePoints) {}

    public record ExtractedCandidate(
            String evidenceCategory,
            String title,
            String content,
            BigDecimal confidence,
            List<String> sourceUnitReferences) {
        public ExtractedCandidate {
            sourceUnitReferences = sourceUnitReferences == null
                    ? List.of()
                    : List.copyOf(sourceUnitReferences);
        }
    }

    public record CandidateBatch(List<ExtractedCandidate> candidates) {
        public CandidateBatch {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record ValidatedBatch(
            UUID repositoryId,
            UUID snapshotId,
            long sourceRevision,
            List<com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate>
                    candidates,
            Map<RejectionReason, Integer> rejectionReasonCounts) {
        public ValidatedBatch {
            candidates = List.copyOf(candidates);
            rejectionReasonCounts = Map.copyOf(rejectionReasonCounts);
        }
    }

    public record EmbeddedBatch(
            UUID repositoryId,
            UUID snapshotId,
            long sourceRevision,
            EmbeddingPolicy embeddingPolicy,
            List<com.hiresemble.profile.application.service.CanonicalExperienceCandidateService.Candidate>
                    candidates,
            Map<RejectionReason, Integer> rejectionReasonCounts) {
        public EmbeddedBatch {
            candidates = List.copyOf(candidates);
            rejectionReasonCounts = Map.copyOf(rejectionReasonCounts);
        }
    }

    public record ApplyOutput(
            UUID repositoryId,
            List<UUID> appliedEvidenceIds,
            int newCount,
            int corroboratedCount,
            int reviewRequiredCount,
            int rejectedCount) {
        public ApplyOutput {
            appliedEvidenceIds = List.copyOf(appliedEvidenceIds);
        }
    }

    public record FinalOutput(
            UUID githubSourceId,
            String status,
            int newExperienceCount,
            int corroboratedExperienceCount,
            int reviewRequiredCount,
            int rejectedCandidateCount,
            boolean partial) {}

    public record EmbeddingValuesOutput(List<List<Double>> vectors) {
        public EmbeddingValuesOutput {
            vectors = vectors == null ? List.of() : List.copyOf(vectors);
        }
    }
}
