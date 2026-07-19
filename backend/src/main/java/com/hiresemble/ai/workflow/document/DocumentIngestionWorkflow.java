package com.hiresemble.ai.workflow.document;

import com.hiresemble.agentrun.domain.PartialResult;
import com.hiresemble.agentrun.domain.RequiredUserAction;
import com.hiresemble.agentrun.domain.RequiredUserActionType;
import com.hiresemble.agentrun.domain.ResourceReference;
import com.hiresemble.agentrun.domain.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainApplyPlan;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.document.application.DocumentEvidenceCandidate;
import com.hiresemble.document.application.DocumentWorkflowCommandPort;
import com.hiresemble.document.application.DocumentWorkflowQueryPort;
import com.hiresemble.document.domain.DocumentParseStatus;
import com.hiresemble.document.domain.DocumentRecords.DocumentChunkRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentRecord;
import com.hiresemble.document.domain.DocumentRecords.DocumentTextRecord;
import com.hiresemble.document.domain.DocumentRecords.EmbeddingPolicy;
import com.hiresemble.document.domain.EvidenceExtractionStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The bounded P4 DOCUMENT_INGESTION contribution. Full source bodies never enter checkpoints. */
public final class DocumentIngestionWorkflow {

    public static final String LOAD_DOCUMENT_SOURCE = "LOAD_DOCUMENT_SOURCE";
    public static final String EXTRACT_OR_ACCEPT_TEXT = "EXTRACT_OR_ACCEPT_TEXT";
    public static final String MASK_TEXT = "MASK_TEXT";
    public static final String CHUNK_TEXT = "CHUNK_TEXT";
    public static final String EMBED_CHUNKS = "EMBED_CHUNKS";
    public static final String EXTRACT_EVIDENCE_CANDIDATES = "EXTRACT_EVIDENCE_CANDIDATES";
    public static final String APPLY_EVIDENCE_CANDIDATES = "APPLY_EVIDENCE_CANDIDATES";
    public static final String FINALIZE_DOCUMENT = "FINALIZE_DOCUMENT";

    private static final int EMBEDDING_DIMENSION = 1536;
    private static final int MAX_CANDIDATES = 50;
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);

    private final DocumentWorkflowQueryPort queryPort;
    private final DocumentWorkflowCommandPort commandPort;
    private final ObjectMapper objectMapper;

    public DocumentIngestionWorkflow(
            DocumentWorkflowQueryPort queryPort,
            DocumentWorkflowCommandPort commandPort,
            ObjectMapper objectMapper) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.DOCUMENT_INGESTION,
                CanonicalWorkflowDefinitions.VERSION,
                List.of(
                        step(LOAD_DOCUMENT_SOURCE, new LoadSourceExecutor()),
                        step(EXTRACT_OR_ACCEPT_TEXT, new ExtractTextExecutor()),
                        step(MASK_TEXT, new MaskTextExecutor()),
                        step(CHUNK_TEXT, new ChunkTextExecutor()),
                        step(EMBED_CHUNKS, new EmbedChunksExecutor()),
                        step(EXTRACT_EVIDENCE_CANDIDATES, new ExtractEvidenceExecutor()),
                        step(APPLY_EVIDENCE_CANDIDATES, new ApplyEvidenceExecutor()),
                        step(FINALIZE_DOCUMENT, new FinalizeDocumentExecutor())));
    }

    private ExecutableWorkflowStep step(String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class DocumentExecutor<T> implements WorkflowStepExecutor<T> {

        private final String stepKey;
        private final Class<T> outputType;

        private DocumentExecutor(String stepKey, Class<T> outputType) {
            this.stepKey = stepKey;
            this.outputType = outputType;
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
                    "output-v1",
                    value -> {
                        if (value == null || !value.isObject()) {
                            throw new IllegalArgumentException("structured output must be an object");
                        }
                    },
                    value -> validateJavaRecord(value, context),
                    value -> validateWorkflowOutput(value, context),
                    value -> validateDomainOutput(value, context));
        }

        protected void validateJavaRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflowOutput(T output, StepExecutionContext context) {}

        protected void validateDomainOutput(T output, StepExecutionContext context) {}

        protected final DocumentState state(StepExecutionContext context) {
            if (context.run().workflowType() != WorkflowType.DOCUMENT_INGESTION
                    || !"DOCUMENT".equals(context.run().resourceType())
                    || context.run().resourceId() == null) {
                throw ownerFailure();
            }
            UUID inputId = parseDocumentId(context.run().inputReferenceSnapshot());
            if (!context.run().resourceId().equals(inputId)) throw ownerFailure();
            DocumentRecord document = queryPort.snapshot(context.run().userId(), inputId);
            if (document.deletedAt() != null || queryPort.isDeleted(document.userId(), document.id())) {
                throw ownerFailure();
            }
            return new DocumentState(document, context.run().id());
        }

        protected final StepInput localInput(
                DocumentState state, JsonNode refs, String canonicalSuffix) {
            return new StepInput(
                    revisionScope(state.document().sourceRevision()),
                    refs,
                    stepKey + "|" + state.document().id() + "|"
                            + state.document().sourceRevision() + "|" + canonicalSuffix,
                    refs.deepCopy(),
                    null,
                    state.document().version());
        }

        protected final AiGatewayResponse localResponse(Object output) {
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), null);
            } catch (Exception exception) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        "DOCUMENT_WORKFLOW_SERIALIZATION_FAILED",
                        "문서 처리 결과를 안전하게 기록하지 못했습니다.");
            }
        }

        protected final JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
        }

        protected final JsonNode refs(DocumentRecord document) {
            return objectMapper.createObjectNode()
                    .put("documentId", document.id().toString())
                    .put("sourceRevision", document.sourceRevision());
        }
    }

    private final class LoadSourceExecutor extends DocumentExecutor<SourceOutput> {

        private LoadSourceExecutor() {
            super(LOAD_DOCUMENT_SOURCE, SourceOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            JsonNode refs = refs(state.document());
            return localInput(
                    state,
                    refs,
                    state.document().checksumSha256() + "|" + state.document().fileSizeBytes());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            DocumentState state = state(invocation.executionContext());
            DocumentRecord parsing = commandPort.beginParsing(
                    state.document().userId(), state.document().id(), state.agentRunId());
            byte[] source = queryPort.source(parsing.userId(), parsing.id());
            return localResponse(new SourceOutput(
                    parsing.id(), parsing.sourceRevision(), source.length, sha256(source)));
        }

        @Override
        public JsonNode minimalOutput(SourceOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        protected void validateJavaRecord(SourceOutput output, StepExecutionContext context) {
            if (output.documentId() == null || output.sourceRevision() < 0
                    || output.sourceSizeBytes() < 1 || !isHash(output.sourceChecksumSha256())) {
                throw new IllegalArgumentException("source output is invalid");
            }
        }
    }

    private final class ExtractTextExecutor extends DocumentExecutor<TextOutput> {

        private ExtractTextExecutor() {
            super(EXTRACT_OR_ACCEPT_TEXT, TextOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            JsonNode refs = refs(state.document());
            return localInput(state, refs, state.document().checksumSha256());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            DocumentState state = state(invocation.executionContext());
            DocumentTextRecord text = commandPort.extractOrAcceptText(
                    state.document().userId(), state.document().id(), state.agentRunId());
            DocumentRecord current = queryPort.snapshot(text.userId(), text.documentId());
            return localResponse(new TextOutput(
                    text.documentId(), text.id(), text.sourceRevision(), text.characterCount(),
                    text.pageCount(), current.parseStatus() == DocumentParseStatus.NEEDS_MANUAL_TEXT,
                    sha256(text.extractedText())));
        }

        @Override
        public JsonNode minimalOutput(TextOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public Optional<RequiredUserAction> requiredUserAction(
                TextOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            if (!output.needsManualText()) return Optional.empty();
            DocumentState state = state(context);
            return Optional.of(new RequiredUserAction(
                    RequiredUserActionType.PROVIDE_DOCUMENT_TEXT,
                    new ResourceReference(
                            "DOCUMENT", state.document().id(),
                            queryPort.displayLabel(state.document().userId(), state.document().id())),
                    "/documents/" + state.document().id(),
                    "추출된 텍스트가 부족합니다. 문서 텍스트를 직접 입력해 주세요."));
        }

        @Override
        protected void validateJavaRecord(TextOutput output, StepExecutionContext context) {
            if (output.documentId() == null || output.textId() == null
                    || output.sourceRevision() < 0 || output.characterCount() < 0
                    || output.characterCount() > 500_000
                    || (output.pageCount() != null && output.pageCount() < 1)
                    || !isHash(output.textHash())) {
                throw new IllegalArgumentException("text output is invalid");
            }
        }
    }

    private final class MaskTextExecutor extends DocumentExecutor<MaskOutput> {

        private MaskTextExecutor() {
            super(MASK_TEXT, MaskOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            DocumentTextRecord text = requiredText(state.document());
            var refs = objectMapper.createObjectNode()
                    .put("documentId", state.document().id().toString())
                    .put("textId", text.id().toString())
                    .put("sourceRevision", text.sourceRevision())
                    .put("characterCount", text.characterCount())
                    .put("textHash", sha256(text.extractedText()));
            return localInput(state, refs, refs.path("textHash").asText());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            DocumentState state = state(invocation.executionContext());
            DocumentTextRecord text = commandPort.maskText(
                    state.document().userId(), state.document().id(), state.agentRunId());
            return localResponse(new MaskOutput(
                    text.documentId(), text.id(), text.sourceRevision(), text.characterCount(),
                    sha256(text.maskedText())));
        }

        @Override
        public JsonNode minimalOutput(MaskOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        protected void validateJavaRecord(MaskOutput output, StepExecutionContext context) {
            if (output.documentId() == null || output.textId() == null
                    || output.sourceRevision() < 0 || output.characterCount() < 100
                    || output.characterCount() > 500_000 || !isHash(output.maskedTextHash())) {
                throw new IllegalArgumentException("mask output is invalid");
            }
        }
    }

    private final class ChunkTextExecutor extends DocumentExecutor<ChunkOutput> {

        private ChunkTextExecutor() {
            super(CHUNK_TEXT, ChunkOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            DocumentTextRecord text = requiredText(state.document());
            if (text.maskedText() == null) {
                throw domainFailure("DOCUMENT_MASKED_TEXT_MISSING");
            }
            var refs = objectMapper.createObjectNode()
                    .put("documentId", state.document().id().toString())
                    .put("textId", text.id().toString())
                    .put("sourceRevision", text.sourceRevision())
                    .put("maskedTextHash", sha256(text.maskedText()))
                    .put("chunkPolicyVersion", "paragraph-page-v1");
            return localInput(state, refs, refs.path("maskedTextHash").asText());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            DocumentState state = state(invocation.executionContext());
            List<DocumentChunkRecord> chunks = commandPort.chunkText(
                    state.document().userId(), state.document().id(), state.agentRunId());
            return localResponse(chunkOutput(state.document(), chunks));
        }

        @Override
        public JsonNode minimalOutput(ChunkOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        protected void validateJavaRecord(ChunkOutput output, StepExecutionContext context) {
            if (output.documentId() == null || output.sourceRevision() < 0
                    || output.chunks() == null || output.chunks().isEmpty()) {
                throw new IllegalArgumentException("chunk output is invalid");
            }
            for (int index = 0; index < output.chunks().size(); index++) {
                ChunkRef chunk = output.chunks().get(index);
                if (chunk == null || chunk.chunkId() == null || chunk.chunkIndex() != index
                        || chunk.tokenCount() < 0 || !isHash(chunk.maskedContentHash())
                        || (chunk.pageFrom() == null) != (chunk.pageTo() == null)
                        || (chunk.pageFrom() != null && (chunk.pageFrom() < 1
                                || chunk.pageTo() < chunk.pageFrom()))) {
                    throw new IllegalArgumentException("chunk reference is invalid");
                }
            }
        }
    }

    private final class EmbedChunksExecutor extends DocumentExecutor<EmbeddingBatch> {

        private EmbedChunksExecutor() {
            super(EMBED_CHUNKS, EmbeddingBatch.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            List<DocumentChunkRecord> chunks = requiredChunks(state.document());
            EmbeddingPolicy policy = queryPort.activeEmbeddingPolicy();
            requireActivePolicy(policy);
            var refs = objectMapper.createObjectNode()
                    .put("documentId", state.document().id().toString())
                    .put("sourceRevision", state.document().sourceRevision())
                    .put("policyVersion", policy.version())
                    .put("dimension", policy.dimension())
                    .put("generation", policy.generation());
            var chunkRefs = refs.putArray("chunks");
            var maskedInputs = objectMapper.createArrayNode();
            for (DocumentChunkRecord chunk : chunks) {
                chunkRefs.addObject()
                        .put("chunkId", chunk.id().toString())
                        .put("chunkIndex", chunk.chunkIndex())
                        .put("maskedContentHash", sha256(chunk.maskedContent()));
                maskedInputs.add(chunk.maskedContent());
            }
            var payload = objectMapper.createObjectNode()
                    .put("documentId", state.document().id().toString())
                    .put("sourceRevision", state.document().sourceRevision())
                    .put("policyVersion", policy.version())
                    .put("dimension", policy.dimension())
                    .put("generation", policy.generation());
            payload.set("chunkRefs", chunkRefs.deepCopy());
            payload.set("maskedInputs", maskedInputs);
            return new StepInput(
                    revisionScope(state.document().sourceRevision()),
                    refs,
                    EMBED_CHUNKS + "|" + state.document().id() + "|"
                            + state.document().sourceRevision() + "|" + policy.version() + "|"
                            + policy.generation() + "|" + hashChunkRefs(chunks),
                    payload,
                    null,
                    state.document().version());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            EmbeddingPolicy policy = queryPort.activeEmbeddingPolicy();
            requireActivePolicy(policy);
            List<String> maskedInputs = new ArrayList<>();
            invocation.input().gatewayPayload().path("maskedInputs")
                    .forEach(value -> maskedInputs.add(value.asText()));
            AiGatewayResponse gateway = invocation.embeddingGateway().embed(new EmbeddingRequest(
                    policy.provider(), policy.model(), maskedInputs, policy.dimension(),
                    EMBEDDING_TIMEOUT));
            try {
                EmbeddingValuesOutput values = objectMapper.readValue(
                        gateway.rawJson(), EmbeddingValuesOutput.class);
                JsonNode chunkRefs = invocation.input().gatewayPayload().path("chunkRefs");
                if (values.vectors() == null || values.vectors().size() != chunkRefs.size()) {
                    throw structuredFailure("DOCUMENT_EMBEDDING_COUNT_INVALID");
                }
                List<EmbeddingVector> vectors = new ArrayList<>();
                for (int index = 0; index < chunkRefs.size(); index++) {
                    vectors.add(new EmbeddingVector(
                            UUID.fromString(chunkRefs.get(index).path("chunkId").asText()),
                            values.vectors().get(index)));
                }
                EmbeddingBatch batch = new EmbeddingBatch(
                        UUID.fromString(invocation.input().gatewayPayload()
                                .path("documentId").asText()),
                        invocation.input().gatewayPayload().path("sourceRevision").asLong(),
                        policy.version(), policy.dimension(), policy.generation(), vectors);
                return new AiGatewayResponse(objectMapper.writeValueAsString(batch), gateway.usage());
            } catch (AiExecutionException exception) {
                throw exception;
            } catch (Exception exception) {
                throw structuredFailure("DOCUMENT_EMBEDDING_OUTPUT_INVALID");
            }
        }

        @Override
        public JsonNode minimalOutput(EmbeddingBatch output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("documentId", output.documentId().toString())
                    .put("sourceRevision", output.sourceRevision())
                    .put("embeddedChunkCount", output.vectors().size())
                    .put("policyVersion", output.policyVersion())
                    .put("dimension", output.dimension())
                    .put("generation", output.generation());
            var ids = result.putArray("chunkIds");
            output.vectors().forEach(vector -> ids.add(vector.chunkId().toString()));
            return result;
        }

        @Override
        public Optional<DomainApplyPlan> domainApply(
                EmbeddingBatch output, JsonNode minimalOutput, StepExecutionContext context) {
            DocumentState state = state(context);
            EmbeddingPolicy policy = queryPort.activeEmbeddingPolicy();
            Map<UUID, List<Double>> vectors = output.vectors().stream().collect(Collectors.toMap(
                    EmbeddingVector::chunkId,
                    EmbeddingVector::values,
                    (left, right) -> left,
                    LinkedHashMap::new));
            commandPort.storeEmbeddings(
                    state.document().userId(), state.document().id(), state.agentRunId(),
                    policy, vectors);
            return Optional.empty();
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(EmbeddingBatch output, StepExecutionContext context) {
            if (output.documentId() == null || output.sourceRevision() < 0
                    || output.policyVersion() < 1 || output.dimension() != EMBEDDING_DIMENSION
                    || output.generation() < 1
                    || output.vectors() == null || output.vectors().isEmpty()) {
                throw new IllegalArgumentException("embedding output is invalid");
            }
        }

        @Override
        protected void validateWorkflowOutput(
                EmbeddingBatch output, StepExecutionContext context) {
            DocumentState state = state(context);
            EmbeddingPolicy policy = queryPort.activeEmbeddingPolicy();
            requireActivePolicy(policy);
            if (!output.documentId().equals(state.document().id())
                    || output.sourceRevision() != state.document().sourceRevision()
                    || output.policyVersion() != policy.version()
                    || output.dimension() != policy.dimension()
                    || output.generation() != policy.generation()) {
                throw new IllegalArgumentException("embedding policy projection is invalid");
            }
            Set<UUID> expected = requiredChunks(state.document()).stream()
                    .map(DocumentChunkRecord::id).collect(Collectors.toSet());
            Set<UUID> actual = new HashSet<>();
            for (EmbeddingVector vector : output.vectors()) {
                if (vector == null || vector.chunkId() == null || !actual.add(vector.chunkId())
                        || vector.values() == null || vector.values().size() != policy.dimension()
                        || vector.values().stream().anyMatch(
                                value -> value == null || !Double.isFinite(value))) {
                    throw new IllegalArgumentException("embedding vector is invalid");
                }
            }
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("embedding chunk set is invalid");
            }
        }
    }

    private final class ExtractEvidenceExecutor extends DocumentExecutor<EvidenceCandidateBatch> {

        private ExtractEvidenceExecutor() {
            super(EXTRACT_EVIDENCE_CANDIDATES, EvidenceCandidateBatch.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            List<DocumentChunkRecord> chunks = requiredChunks(state.document());
            var refs = objectMapper.createObjectNode()
                    .put("documentId", state.document().id().toString())
                    .put("sourceRevision", state.document().sourceRevision());
            var refArray = refs.putArray("chunks");
            var maskedChunks = objectMapper.createArrayNode();
            for (DocumentChunkRecord chunk : chunks) {
                refArray.addObject()
                        .put("chunkId", chunk.id().toString())
                        .put("chunkIndex", chunk.chunkIndex())
                        .put("pageFrom", chunk.pageFrom())
                        .put("pageTo", chunk.pageTo())
                        .put("maskedContentHash", sha256(chunk.maskedContent()));
                maskedChunks.addObject()
                        .put("chunkId", chunk.id().toString())
                        .put("chunkIndex", chunk.chunkIndex())
                        .put("pageFrom", chunk.pageFrom())
                        .put("pageTo", chunk.pageTo())
                        .put("maskedContent", chunk.maskedContent());
            }
            var payload = objectMapper.createObjectNode()
                    .put("documentId", state.document().id().toString())
                    .put("sourceRevision", state.document().sourceRevision());
            payload.set("maskedChunks", maskedChunks);
            return new StepInput(
                    revisionScope(state.document().sourceRevision()),
                    refs,
                    EXTRACT_EVIDENCE_CANDIDATES + "|" + state.document().id() + "|"
                            + state.document().sourceRevision() + "|" + hashChunkRefs(chunks),
                    payload,
                    null,
                    state.document().version());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            DocumentState state = state(invocation.executionContext());
            commandPort.beginEvidenceExtraction(
                    state.document().userId(), state.document().id(), state.agentRunId());
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT));
        }

        @Override
        public JsonNode minimalOutput(EvidenceCandidateBatch output, ObjectMapper ignored) {
            var result = objectMapper.createObjectNode()
                    .put("documentId", output.documentId().toString())
                    .put("sourceRevision", output.sourceRevision())
                    .put("candidateCount", output.candidates().size());
            var hashes = result.putArray("candidateHashes");
            output.candidates().forEach(candidate -> hashes.add(candidateHash(candidate)));
            return result;
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                EvidenceCandidateBatch output, StepExecutionContext context) {
            if (output.documentId() == null || output.sourceRevision() < 0
                    || output.candidates() == null || output.candidates().size() > MAX_CANDIDATES) {
                throw new IllegalArgumentException("candidate batch is invalid");
            }
            for (EvidenceCandidatePayload candidate : output.candidates()) {
                validateCandidateShape(candidate);
            }
        }

        @Override
        protected void validateWorkflowOutput(
                EvidenceCandidateBatch output, StepExecutionContext context) {
            DocumentState state = state(context);
            if (output.sourceRevision() != state.document().sourceRevision()) {
                throw new IllegalArgumentException("candidate revision is stale");
            }
            Set<UUID> chunks = requiredChunks(state.document()).stream()
                    .map(DocumentChunkRecord::id).collect(Collectors.toSet());
            for (EvidenceCandidatePayload candidate : output.candidates()) {
                if (!chunks.containsAll(candidate.sourceChunkIds())) {
                    throw new IllegalArgumentException("candidate source is invalid");
                }
            }
        }
    }

    private final class ApplyEvidenceExecutor extends DocumentExecutor<EvidenceApplyOutput> {

        private ApplyEvidenceExecutor() {
            super(APPLY_EVIDENCE_CANDIDATES, EvidenceApplyOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            Object ephemeral = context.ephemeralOutputs().get(EXTRACT_EVIDENCE_CANDIDATES);
            if (!(ephemeral instanceof EvidenceCandidateBatch batch)
                    || !batch.documentId().equals(state.document().id())
                    || batch.sourceRevision() != state.document().sourceRevision()) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        "DOCUMENT_EVIDENCE_HANDOFF_MISSING",
                        "문서 근거 후보를 안전하게 이어서 처리하지 못했습니다.");
            }
            JsonNode safeRefs = objectMapper.createObjectNode()
                    .put("documentId", batch.documentId().toString())
                    .put("sourceRevision", batch.sourceRevision())
                    .put("candidateCount", batch.candidates().size())
                    .put("candidateBatchHash", candidateBatchHash(batch));
            return new StepInput(
                    revisionScope(state.document().sourceRevision()),
                    safeRefs,
                    APPLY_EVIDENCE_CANDIDATES + "|" + candidateBatchHash(batch),
                    tree(batch),
                    null,
                    state.document().version());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            EvidenceCandidateBatch batch;
            try {
                batch = objectMapper.treeToValue(
                        invocation.input().gatewayPayload(), EvidenceCandidateBatch.class);
            } catch (Exception exception) {
                throw domainFailure("DOCUMENT_EVIDENCE_HANDOFF_INVALID");
            }
            DocumentState state = state(invocation.executionContext());
            List<DocumentEvidenceCandidate> candidates = batch.candidates().stream()
                    .map(value -> new DocumentEvidenceCandidate(
                            value.evidenceCategory(), value.title(), value.content(), value.metadata(),
                            value.confidence(), value.sourceChunkIds(), value.sourceRevision(),
                            value.validationWarning()))
                    .toList();
            DocumentWorkflowCommandPort.EvidenceApplyResult applied =
                    commandPort.applyEvidenceCandidates(
                            state.document().userId(), state.document().id(), state.agentRunId(),
                            candidates);
            return localResponse(new EvidenceApplyOutput(
                    state.document().id(), state.document().sourceRevision(),
                    applied.appliedEvidenceIds(), applied.rejectedCount(), candidates.size()));
        }

        @Override
        public JsonNode minimalOutput(EvidenceApplyOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public Optional<PartialResult> partialResult(
                EvidenceApplyOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            List<String> succeeded = output.appliedEvidenceIds().stream()
                    .map(UUID::toString).toList();
            List<String> failed = java.util.stream.IntStream.range(0, output.rejectedCount())
                    .mapToObj(index -> "candidate-rejected-" + (index + 1)).toList();
            List<ResourceReference> refs = output.appliedEvidenceIds().stream()
                    .map(id -> new ResourceReference("EVIDENCE", id, null)).toList();
            return Optional.of(new PartialResult(succeeded, failed, refs));
        }

        @Override
        protected void validateJavaRecord(
                EvidenceApplyOutput output, StepExecutionContext context) {
            if (output.documentId() == null || output.sourceRevision() < 0
                    || output.appliedEvidenceIds() == null || output.rejectedCount() < 0
                    || output.candidateCount() < 0
                    || output.appliedEvidenceIds().size() + output.rejectedCount()
                            != output.candidateCount()
                    || output.appliedEvidenceIds().size() > MAX_CANDIDATES
                    || new HashSet<>(output.appliedEvidenceIds()).size()
                            != output.appliedEvidenceIds().size()) {
                throw new IllegalArgumentException("evidence apply output is invalid");
            }
        }
    }

    private final class FinalizeDocumentExecutor extends DocumentExecutor<FinalDocumentOutput> {

        private FinalizeDocumentExecutor() {
            super(FINALIZE_DOCUMENT, FinalDocumentOutput.class);
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            DocumentState state = state(context);
            JsonNode refs = refs(state.document());
            return localInput(
                    state,
                    refs,
                    state.document().parseStatus() + "|"
                            + state.document().evidenceExtractionStatus());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            DocumentState state = state(invocation.executionContext());
            DocumentRecord document = commandPort.finalizeDocument(
                    state.document().userId(), state.document().id(), state.agentRunId());
            return localResponse(new FinalDocumentOutput(
                    document.id(), document.sourceRevision(), document.parseStatus(),
                    document.evidenceExtractionStatus()));
        }

        @Override
        public JsonNode minimalOutput(FinalDocumentOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        protected void validateJavaRecord(
                FinalDocumentOutput output, StepExecutionContext context) {
            if (output.documentId() == null || output.sourceRevision() < 0
                    || output.parseStatus() != DocumentParseStatus.PARSED
                    || output.evidenceExtractionStatus() != EvidenceExtractionStatus.SUCCEEDED) {
                throw new IllegalArgumentException("final document output is invalid");
            }
        }
    }

    private DocumentTextRecord requiredText(DocumentRecord document) {
        return queryPort.text(document.userId(), document.id(), document.sourceRevision())
                .orElseThrow(() -> domainFailure("DOCUMENT_TEXT_MISSING"));
    }

    private List<DocumentChunkRecord> requiredChunks(DocumentRecord document) {
        List<DocumentChunkRecord> chunks =
                queryPort.chunks(document.userId(), document.id(), document.sourceRevision());
        if (chunks.isEmpty()) throw domainFailure("DOCUMENT_CHUNKS_MISSING");
        return chunks;
    }

    private ChunkOutput chunkOutput(DocumentRecord document, List<DocumentChunkRecord> chunks) {
        return new ChunkOutput(
                document.id(),
                document.sourceRevision(),
                chunks.stream().map(chunk -> new ChunkRef(
                        chunk.id(), chunk.chunkIndex(), chunk.pageFrom(), chunk.pageTo(),
                        chunk.tokenCount(), sha256(chunk.maskedContent()))).toList());
    }

    private void requireActivePolicy(EmbeddingPolicy policy) {
        if (policy == null || policy.version() < 1
                || !"OpenAI".equals(policy.provider())
                || !"text-embedding-3-small".equals(policy.model())
                || policy.dimension() != EMBEDDING_DIMENSION
                || !"cosine".equalsIgnoreCase(policy.distance()) || policy.generation() < 1) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "DOCUMENT_EMBEDDING_POLICY_INVALID",
                    "문서 임베딩 구성이 올바르지 않습니다.");
        }
    }

    private void validateCandidateShape(EvidenceCandidatePayload candidate) {
        if (candidate == null
                || !hasLength(candidate.evidenceCategory(), 1, 80)
                || !hasLength(candidate.title(), 1, 250)
                || !hasLength(candidate.content(), 1, 20_000)
                || candidate.content().indexOf('\0') >= 0
                || candidate.confidence() == null || candidate.confidence().signum() < 0
                || candidate.confidence().compareTo(BigDecimal.ONE) > 0
                || candidate.sourceChunkIds() == null || candidate.sourceChunkIds().isEmpty()
                || candidate.sourceChunkIds().size() > 20
                || new HashSet<>(candidate.sourceChunkIds()).size()
                        != candidate.sourceChunkIds().size()
                || candidate.sourceRevision() < 0
                || (candidate.validationWarning() != null
                        && candidate.validationWarning().length() > 500)) {
            throw new IllegalArgumentException("candidate shape is invalid");
        }
        Map<String, Object> metadata = candidate.metadata() == null ? Map.of() : candidate.metadata();
        if (metadata.values().stream().anyMatch(value -> value != null
                && !(value instanceof String)
                && !(value instanceof Number)
                && !(value instanceof Boolean))) {
            throw new IllegalArgumentException("candidate metadata is invalid");
        }
        try {
            if (objectMapper.writeValueAsBytes(metadata).length > 16_384) {
                throw new IllegalArgumentException("candidate metadata is too large");
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("candidate metadata is invalid", exception);
        }
    }

    private String hashChunkRefs(List<DocumentChunkRecord> chunks) {
        return sha256(chunks.stream()
                .map(chunk -> chunk.id() + ":" + chunk.chunkIndex() + ":"
                        + sha256(chunk.maskedContent()))
                .collect(Collectors.joining("|")));
    }

    private String candidateHash(EvidenceCandidatePayload candidate) {
        return sha256(candidate.evidenceCategory() + "|" + candidate.title() + "|"
                + candidate.content() + "|" + candidate.sourceChunkIds() + "|"
                + candidate.sourceRevision());
    }

    private String candidateBatchHash(EvidenceCandidateBatch batch) {
        return sha256(batch.documentId() + "|" + batch.sourceRevision() + "|"
                + batch.candidates().stream().map(this::candidateHash)
                        .collect(Collectors.joining("|")));
    }

    private UUID parseDocumentId(JsonNode input) {
        try {
            return UUID.fromString(input.path("documentId").asText());
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private String revisionScope(long sourceRevision) {
        return "document-revision-" + sourceRevision;
    }

    private boolean hasLength(String value, int min, int max) {
        return value != null && !value.isBlank() && value.length() >= min && value.length() <= max;
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                "RESOURCE_NOT_FOUND",
                "요청한 문서를 찾을 수 없습니다.");
    }

    private AiExecutionException domainFailure(String code) {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                code,
                "문서 처리 상태를 안전하게 적용하지 못했습니다.");
    }

    private AiExecutionException structuredFailure(String code) {
        return AiExecutionException.retryable(
                FailureKind.STRUCTURED_OUTPUT,
                code,
                "문서 AI 결과 형식을 확인하지 못했습니다.");
    }

    private record DocumentState(DocumentRecord document, UUID agentRunId) {}

    public record SourceOutput(
            UUID documentId, long sourceRevision, long sourceSizeBytes, String sourceChecksumSha256) {}

    public record TextOutput(
            UUID documentId,
            UUID textId,
            long sourceRevision,
            int characterCount,
            Integer pageCount,
            boolean needsManualText,
            String textHash) {}

    public record MaskOutput(
            UUID documentId,
            UUID textId,
            long sourceRevision,
            int characterCount,
            String maskedTextHash) {}

    public record ChunkRef(
            UUID chunkId,
            int chunkIndex,
            Integer pageFrom,
            Integer pageTo,
            int tokenCount,
            String maskedContentHash) {}

    public record ChunkOutput(UUID documentId, long sourceRevision, List<ChunkRef> chunks) {
        public ChunkOutput {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }

    public record EmbeddingVector(UUID chunkId, List<Double> values) {
        public EmbeddingVector {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record EmbeddingValuesOutput(List<List<Double>> vectors) {
        public EmbeddingValuesOutput {
            vectors = vectors == null ? List.of() : vectors.stream().map(List::copyOf).toList();
        }
    }

    public record EmbeddingBatch(
            UUID documentId,
            long sourceRevision,
            long policyVersion,
            int dimension,
            int generation,
            List<EmbeddingVector> vectors) {
        public EmbeddingBatch {
            vectors = vectors == null ? List.of() : List.copyOf(vectors);
        }
    }

    public record EvidenceCandidatePayload(
            String evidenceCategory,
            String title,
            String content,
            Map<String, Object> metadata,
            BigDecimal confidence,
            List<UUID> sourceChunkIds,
            long sourceRevision,
            String validationWarning) {
        public EvidenceCandidatePayload {
            metadata = metadata == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
        }
    }

    public record EvidenceCandidateBatch(
            UUID documentId, long sourceRevision, List<EvidenceCandidatePayload> candidates) {
        public EvidenceCandidateBatch {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record EvidenceApplyOutput(
            UUID documentId,
            long sourceRevision,
            List<UUID> appliedEvidenceIds,
            int rejectedCount,
            int candidateCount) {
        public EvidenceApplyOutput {
            appliedEvidenceIds = appliedEvidenceIds == null
                    ? List.of() : List.copyOf(appliedEvidenceIds);
        }
    }

    public record FinalDocumentOutput(
            UUID documentId,
            long sourceRevision,
            DocumentParseStatus parseStatus,
            EvidenceExtractionStatus evidenceExtractionStatus) {}
}
