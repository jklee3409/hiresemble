package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.ImageTextExtractionGateway;
import com.hiresemble.ai.port.ImageTextExtractionGateway.ImageMedia;
import com.hiresemble.ai.port.ImageTextExtractionGateway.ImageTextExtractionRequest;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.validation.StructuredOutputValidator.Contract;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowContribution;
import com.hiresemble.ai.workflow.WorkflowRegistry.ExecutableWorkflowStep;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.DomainApplyPlan;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.GatewayInvocation;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepExecutionContext;
import com.hiresemble.ai.workflow.WorkflowStepExecutor.StepInput;
import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.job.application.port.JobPageFetchException;
import com.hiresemble.job.application.port.JobPageFetchGateway;
import com.hiresemble.job.application.port.JobPageFetchGateway.FetchResult;
import com.hiresemble.job.application.port.JobPageFetchGateway.PageClassification;
import com.hiresemble.job.application.port.JobImageFetchGateway;
import com.hiresemble.job.application.port.JobImageFetchGateway.ImageAsset;
import com.hiresemble.job.application.port.JobImageFetchGateway.ImageCandidate;
import com.hiresemble.job.application.port.JobWorkflowCommandPort;
import com.hiresemble.job.application.port.JobWorkflowQueryPort;
import com.hiresemble.job.domain.JobCommands.ExtractedFields;
import com.hiresemble.job.domain.JobExtractionStatus;
import com.hiresemble.job.domain.JobRecords.JobRecord;
import com.hiresemble.job.domain.JobRecords.UserOverrides;
import com.hiresemble.job.domain.JobRecords.WorkflowSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.hiresemble.job.infrastructure.JobPageFetchProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bounded P5 JOB_POSTING_EXTRACTION workflow.
 *
 * <p>Full HTML exists only in the in-memory FETCH output and is replaced by bounded plain text
 * before any reusable checkpoint is written.
 */
public final class JobPostingExtractionWorkflow {

    public static final String FETCH_JOB_PAGE = "FETCH_JOB_PAGE";
    public static final String INSPECT_JOB_PAGE = "INSPECT_JOB_PAGE";
    public static final String FETCH_JOB_IMAGES = "FETCH_JOB_IMAGES";
    public static final String EXTRACT_JOB_IMAGE_TEXT = "EXTRACT_JOB_IMAGE_TEXT";
    public static final String COMPOSE_JOB_SOURCE_TEXT = "COMPOSE_JOB_SOURCE_TEXT";
    public static final String EXTRACT_JOB_FIELDS = "EXTRACT_JOB_FIELDS";
    public static final String MERGE_USER_OVERRIDES = "MERGE_USER_OVERRIDES";
    public static final String VALIDATE_JOB_EXTRACTION = "VALIDATE_JOB_EXTRACTION";
    public static final String APPLY_JOB_EXTRACTION = "APPLY_JOB_EXTRACTION";

    public static final int MAX_SANITIZED_CHARACTERS = 80_000;
    private static final int MAX_RAW_PAGE_CHARACTERS = 10 * 1024 * 1024;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);

    private final JobWorkflowQueryPort queryPort;
    private final JobWorkflowCommandPort commandPort;
    private final JobPageFetchGateway fetchGateway;
    private final JobImageFetchGateway imageFetchGateway;
    private final ImageTextExtractionGateway imageTextExtractionGateway;
    private final JobPageFetchProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobPostingExtractionWorkflow(
            JobWorkflowQueryPort queryPort,
            JobWorkflowCommandPort commandPort,
            JobPageFetchGateway fetchGateway,
            JobImageFetchGateway imageFetchGateway,
            ImageTextExtractionGateway imageTextExtractionGateway,
            JobPageFetchProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.fetchGateway = Objects.requireNonNull(fetchGateway);
        this.imageFetchGateway = Objects.requireNonNull(imageFetchGateway);
        this.imageTextExtractionGateway = Objects.requireNonNull(imageTextExtractionGateway);
        this.properties = Objects.requireNonNull(properties);
        this.properties.validate();
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.JOB_POSTING_EXTRACTION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                TerminalPartialPolicy.rejectUnexpected(),
                List.of(
                        step(FETCH_JOB_PAGE, new FetchJobPageExecutor()),
                        step(INSPECT_JOB_PAGE, new InspectJobPageExecutor()),
                        step(FETCH_JOB_IMAGES, new FetchJobImagesExecutor()),
                        step(EXTRACT_JOB_IMAGE_TEXT, new ExtractJobImageTextExecutor()),
                        step(COMPOSE_JOB_SOURCE_TEXT, new ComposeJobSourceTextExecutor()),
                        step(EXTRACT_JOB_FIELDS, new ExtractJobFieldsExecutor()),
                        step(MERGE_USER_OVERRIDES, new MergeUserOverridesExecutor()),
                        step(VALIDATE_JOB_EXTRACTION, new ValidateJobExtractionExecutor()),
                        step(APPLY_JOB_EXTRACTION, new ApplyJobExtractionExecutor())));
    }

    private ExecutableWorkflowStep step(String key, WorkflowStepExecutor<?> executor) {
        return new ExecutableWorkflowStep(key, executor);
    }

    private abstract class JobExecutor<T> implements WorkflowStepExecutor<T> {

        private final String stepKey;
        private final String outputSchemaVersion;
        private final Class<T> outputType;
        private final Set<String> allowedOutputFields;

        private JobExecutor(
                String stepKey,
                String outputSchemaVersion,
                Class<T> outputType,
                Set<String> allowedOutputFields) {
            this.stepKey = stepKey;
            this.outputSchemaVersion = outputSchemaVersion;
            this.outputType = outputType;
            this.allowedOutputFields = Set.copyOf(allowedOutputFields);
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
                    outputSchemaVersion,
                    value -> {
                        if (value == null
                                || !value.isObject()
                                || !allowedOutputFields.containsAll(value.propertyNames())) {
                            throw new IllegalArgumentException(
                                    "job structured output schema is invalid");
                        }
                    },
                    value -> validateJavaRecord(value, context),
                    value -> validateWorkflowOutput(value, context),
                    value -> validateDomainOutput(value, context));
        }

        protected void validateJavaRecord(T output, StepExecutionContext context) {}

        protected void validateWorkflowOutput(T output, StepExecutionContext context) {}

        protected void validateDomainOutput(T output, StepExecutionContext context) {}

        protected final JobState state(StepExecutionContext context) {
            if (context == null
                    || context.run().workflowType() != WorkflowType.JOB_POSTING_EXTRACTION
                    || !"JOB".equals(context.run().resourceType())
                    || context.run().resourceId() == null) {
                throw ownerFailure();
            }
            UUID inputJobId = parseJobId(context.run().inputReferenceSnapshot());
            if (!context.run().resourceId().equals(inputJobId)) {
                throw ownerFailure();
            }
            WorkflowSnapshot job = queryPort.snapshot(context.run().userId(), inputJobId);
            if (!context.run().userId().equals(job.userId())
                    || !context.run().id().equals(job.latestAgentRunId())) {
                throw ownerFailure();
            }
            return new JobState(job, context.run().id());
        }

        protected final StepInput localInput(
                JobState state,
                JsonNode refs,
                String canonicalSuffix,
                JsonNode gatewayPayload) {
            return new StepInput(
                    state.job().jobId().toString(),
                    refs,
                    stepKey + "|" + state.job().jobId() + "|" + canonicalSuffix,
                    gatewayPayload,
                    null,
                    state.job().version());
        }

        protected final AiGatewayResponse localResponse(Object output) {
            try {
                return new AiGatewayResponse(objectMapper.writeValueAsString(output), java.util.List.of());
            } catch (Exception exception) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        "JOB_WORKFLOW_SERIALIZATION_FAILED",
                        "채용 공고 추출 결과를 안전하게 처리하지 못했습니다.");
            }
        }

        protected final JsonNode tree(Object value) {
            return objectMapper.valueToTree(value);
        }

        protected final JsonNode baseRefs(WorkflowSnapshot job) {
            return objectMapper.createObjectNode()
                    .put("jobId", job.jobId().toString())
                    .put("jobVersion", job.version())
                    .put("sourceUrl", job.sourceUrl())
                    .put("canonicalUrl", job.canonicalUrl());
        }

        protected final JsonNode semanticRefs(WorkflowSnapshot job) {
            return objectMapper.createObjectNode()
                    .put("jobId", job.jobId().toString())
                    .put("sourceUrl", job.sourceUrl())
                    .put("canonicalUrl", job.canonicalUrl());
        }
    }

    private final class FetchJobPageExecutor extends JobExecutor<FetchedJobPageOutput> {

        private FetchJobPageExecutor() {
            super(
                    FETCH_JOB_PAGE,
                    "job-fetch-output-v2",
                    FetchedJobPageOutput.class,
                    Set.of(
                            "jobId",
                            "jobVersion",
                            "sourceType",
                            "sourceUrl",
                            "finalUrl",
                            "classification",
                            "httpStatus",
                            "retrievedAt",
                            "contentLength",
                            "contentHash",
                            "resolvedCharset",
                            "charsetSource",
                            "rawByteLength",
                            "replacementCharacterCount",
                            "replacementCharacterRatio",
                            "content"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            WorkflowSnapshot job = state.job();
            JsonNode refs = baseRefs(job);
            UserOverrides overrides = job.userOverrides();
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("manualTextPresent", overrides.descriptionText() != null)
                    .put(
                            "manualTextHash",
                            overrides.descriptionText() == null
                                    ? null
                                    : sha256(overrides.descriptionText()));
            FetchJobPageInput input = new FetchJobPageInput(
                    job.jobId(),
                    job.version(),
                    job.sourceUrl(),
                    job.canonicalUrl(),
                    overrides.descriptionText() != null,
                    overrides.descriptionText() == null
                            ? null
                            : sha256(overrides.descriptionText()));
            RequiredUserAction action =
                    job.extractionStatus() == JobExtractionStatus.NEEDS_MANUAL_INPUT
                                    && overrides.descriptionText() == null
                            ? manualAction(job)
                            : null;
            StepInput prepared = localInput(
                    state,
                    refs,
                    job.canonicalUrl()
                            + "|"
                            + nullSafe(input.manualTextHash()),
                    tree(input));
            return action == null
                    ? prepared
                    : new StepInput(
                            prepared.scopeKey(),
                            prepared.sanitizedInputRefs(),
                            prepared.canonicalInputMaterial(),
                            prepared.gatewayPayload(),
                            action,
                            prepared.expectedResourceVersion());
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            JobState initial = state(invocation.executionContext());
            WorkflowSnapshot extracting = beginExtracting(initial);
            String manualText = extracting.userOverrides().descriptionText();
            if (manualText != null) {
                return localResponse(manualOutput(extracting, manualText));
            }

            FetchResult fetched;
            try {
                fetched = fetchGateway.fetch(URI.create(extracting.sourceUrl()));
            } catch (JobPageFetchException exception) {
                throw fetchFailure(exception);
            } catch (IllegalArgumentException exception) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.REQUEST_VALIDATION,
                        "JOB_PAGE_URL_INVALID",
                        "채용 공고 URL을 확인해 주세요.");
            }

            WorkflowSnapshot current = state(invocation.executionContext()).job();
            if (current.userOverrides().descriptionText() != null) {
                return localResponse(
                        manualOutput(current, current.userOverrides().descriptionText()));
            }
            if (fetched.classification() != PageClassification.FETCHED) {
                JobRecord waiting = commandPort.markNeedsManualInput(
                        current.userId(),
                        current.jobId(),
                        initial.agentRunId(),
                        current.version());
                return localResponse(new FetchedJobPageOutput(
                        waiting.id(),
                        waiting.version(),
                        JobContentSource.REMOTE_JOB_PAGE,
                        current.sourceUrl(),
                        fetched.finalUri().toString(),
                        fetched.classification().name(),
                        fetched.httpStatus(),
                        clock.instant(),
                        0,
                        null,
                        null,
                        null,
                        0,
                        0,
                        0d,
                        null));
            }
            String html = fetched.html();
            return localResponse(new FetchedJobPageOutput(
                    current.jobId(),
                    current.version(),
                    JobContentSource.REMOTE_JOB_PAGE,
                    current.sourceUrl(),
                    fetched.finalUri().toString(),
                    fetched.classification().name(),
                    fetched.httpStatus(),
                    clock.instant(),
                    html.length(),
                    sha256(html),
                    fetched.charsetMetadata() == null ? null : fetched.charsetMetadata().resolvedCharset(),
                    fetched.charsetMetadata() == null ? null : fetched.charsetMetadata().detectionSource().name(),
                    fetched.charsetMetadata() == null ? 0 : fetched.charsetMetadata().rawByteLength(),
                    fetched.charsetMetadata() == null ? 0 : fetched.charsetMetadata().replacementCharacterCount(),
                    fetched.charsetMetadata() == null ? 0d : fetched.charsetMetadata().replacementCharacterRatio(),
                    html));
        }

        @Override
        public JsonNode minimalOutput(FetchedJobPageOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("jobId", output.jobId().toString())
                    .put("jobVersion", output.jobVersion())
                    .put("sourceType", output.sourceType().name())
                    .put("sourceUrl", output.sourceUrl())
                    .put("finalUrl", output.finalUrl())
                    .put("classification", output.classification())
                    .put("httpStatus", output.httpStatus())
                    .put("retrievedAt", output.retrievedAt().toString())
                    .put("contentLength", output.contentLength())
                    .put("contentHash", output.contentHash())
                    .put("resolvedCharset", output.resolvedCharset())
                    .put("charsetSource", output.charsetSource())
                    .put("rawByteLength", output.rawByteLength())
                    .put("replacementCharacterCount", output.replacementCharacterCount())
                    .put("replacementCharacterRatio", output.replacementCharacterRatio());
        }

        @Override
        public Optional<RequiredUserAction> requiredUserAction(
                FetchedJobPageOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            return output.content() == null
                    ? Optional.of(manualAction(state(context).job()))
                    : Optional.empty();
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                FetchedJobPageOutput output, StepExecutionContext context) {
            boolean containsContent = output.content() != null;
            if (output.jobId() == null
                    || output.jobVersion() < 0
                    || output.sourceType() == null
                    || !hasScalar(output.sourceUrl(), 2_000)
                    || !hasScalar(output.finalUrl(), 2_000)
                    || !hasScalar(output.classification(), 50)
                    || output.httpStatus() < 100
                    || output.httpStatus() > 599
                    || output.retrievedAt() == null
                    || output.contentLength() < 0
                    || output.contentLength() > MAX_RAW_PAGE_CHARACTERS
                    || output.rawByteLength() < 0
                    || output.replacementCharacterCount() < 0
                    || output.replacementCharacterRatio() < 0d
                    || output.replacementCharacterRatio() > 1d
                    || containsContent != (output.contentLength() > 0)
                    || containsContent != isHash(output.contentHash())
                    || (containsContent && output.content().length() != output.contentLength())) {
                throw new IllegalArgumentException("fetched page output is invalid");
            }
        }

        @Override
        protected void validateWorkflowOutput(
                FetchedJobPageOutput output, StepExecutionContext context) {
            if (context != null && !state(context).job().jobId().equals(output.jobId())) {
                throw new IllegalArgumentException("fetched page owner is invalid");
            }
        }

        private WorkflowSnapshot beginExtracting(JobState state) {
            WorkflowSnapshot job = state.job();
            if (job.extractionStatus() == JobExtractionStatus.EXTRACTING) {
                return job;
            }
            JobRecord extracting = commandPort.markExtracting(
                    job.userId(), job.jobId(), state.agentRunId(), job.version());
            return queryPort.snapshot(extracting.userId(), extracting.id());
        }

        private FetchedJobPageOutput manualOutput(
                WorkflowSnapshot job, String manualText) {
            return new FetchedJobPageOutput(
                    job.jobId(),
                    job.version(),
                    JobContentSource.USER_PROVIDED_JOB_TEXT,
                    job.sourceUrl(),
                    job.canonicalUrl(),
                    JobExtractionStatus.MANUAL_INPUT_PROVIDED.name(),
                    200,
                    clock.instant(),
                    manualText.length(),
                    sha256(manualText),
                    "UTF-8",
                    "USER_INPUT",
                    manualText.getBytes(StandardCharsets.UTF_8).length,
                    replacementCount(manualText),
                    replacementRatio(manualText),
                    manualText);
        }
    }

    private final class InspectJobPageExecutor extends JobExecutor<PageInspectionOutput> {

        private InspectJobPageExecutor() {
            super(
                    INSPECT_JOB_PAGE,
                    "job-page-inspection-output-v2",
                    PageInspectionOutput.class,
                    Set.of(
                            "jobId",
                            "jobVersion",
                            "sourceType",
                            "retrievedAt",
                            "classification",
                            "metrics",
                            "domText",
                            "domTextHash",
                            "truncated",
                            "candidates"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            FetchedJobPageOutput fetched = requiredEphemeral(
                    context, FETCH_JOB_PAGE, FetchedJobPageOutput.class);
            JsonNode refs = semanticRefs(state.job());
            var values = (tools.jackson.databind.node.ObjectNode) refs;
            values.put("sourceType", fetched.sourceType().name())
                    .put("finalUrl", fetched.finalUrl())
                    .put("contentLength", fetched.contentLength())
                    .put("contentHash", fetched.contentHash());
            InspectJobPageInput input = new InspectJobPageInput(
                    fetched.jobId(),
                    fetched.jobVersion(),
                    fetched.sourceType(),
                    fetched.finalUrl(),
                    fetched.retrievedAt(),
                    fetched.content());
            return localInput(
                    state,
                    refs,
                    fetched.sourceType().name() + "|" + fetched.contentHash(),
                    tree(input));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            InspectJobPageInput input =
                    read(invocation.input().gatewayPayload(), InspectJobPageInput.class);
            Inspection inspected = input.sourceType() == JobContentSource.REMOTE_JOB_PAGE
                    ? inspectHtml(input.content(), input.finalUrl())
                    : inspectManualText(input.content());
            String domText = inspected.domText();
            boolean truncated = domText.length() > MAX_SANITIZED_CHARACTERS;
            if (truncated) {
                domText = domText.substring(0, MAX_SANITIZED_CHARACTERS).stripTrailing();
            }
            return localResponse(new PageInspectionOutput(
                    input.jobId(),
                    input.jobVersion(),
                    input.sourceType(),
                    input.retrievedAt(),
                    inspected.classification(),
                    inspected.metrics(),
                    domText,
                    domText.isBlank() ? null : sha256(domText),
                    truncated,
                    inspected.candidates()));
        }

        @Override
        public JsonNode minimalOutput(PageInspectionOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("jobId", output.jobId().toString())
                    .put("jobVersion", output.jobVersion())
                    .put("sourceType", output.sourceType().name())
                    .put("retrievedAt", output.retrievedAt().toString())
                    .put("classification", output.classification().name())
                    .put("domTextHash", output.domTextHash())
                    .put("truncated", output.truncated())
                    .set("metrics", tree(output.metrics()));
        }

        @Override public boolean reusable() { return false; }

        @Override
        protected void validateJavaRecord(
                PageInspectionOutput output, StepExecutionContext context) {
            if (output.jobId() == null
                    || output.jobVersion() < 0
                    || output.sourceType() == null
                    || output.retrievedAt() == null
                    || output.classification() == null
                    || output.metrics() == null
                    || output.domText() == null
                    || output.domText().length() > MAX_SANITIZED_CHARACTERS
                    || output.domText().isBlank() == isHash(output.domTextHash())
                    || output.candidates() == null
                    || output.candidates().size() > 18) {
                throw new IllegalArgumentException("page inspection output is invalid");
            }
        }
    }

    private final class FetchJobImagesExecutor extends JobExecutor<FetchedJobImagesOutput> {

        private FetchJobImagesExecutor() {
            super(FETCH_JOB_IMAGES, "job-images-fetch-output-v2", FetchedJobImagesOutput.class,
                    Set.of("jobId", "assets", "rejectedCount", "totalBytes"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            PageInspectionOutput inspection = requiredEphemeral(
                    context, INSPECT_JOB_PAGE, PageInspectionOutput.class);
            FetchJobImagesInput input = new FetchJobImagesInput(
                    inspection.jobId(), inspection.classification(), inspection.candidates());
            JsonNode refs = semanticRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("classification", inspection.classification().name())
                    .put("candidateCount", inspection.candidates().size())
                    .put("domTextHash", inspection.domTextHash());
            return localInput(state, refs,
                    inspection.classification() + "|" + inspection.domTextHash()
                            + "|" + inspection.candidates().stream()
                                    .map(PageImageCandidate::candidateHash)
                                    .reduce("", (left, right) -> left + right),
                    tree(input));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            FetchJobImagesInput input = read(
                    invocation.input().gatewayPayload(), FetchJobImagesInput.class);
            if (input.classification() != PageContentClassification.IMAGE_AUGMENTATION_REQUIRED) {
                return localResponse(new FetchedJobImagesOutput(input.jobId(), List.of(), 0, 0));
            }
            List<ImageAsset> assets = new ArrayList<>();
            int rejected = 0;
            int total = 0;
            JobPageFetchException retryableFailure = null;
            long deadlineNanos = System.nanoTime()
                    + properties.getImageResponseTimeout().toNanos();
            for (PageImageCandidate candidate : input.candidates().stream()
                    .sorted(Comparator.comparingInt(PageImageCandidate::score).reversed())
                    .limit(properties.getMaxImageCandidates())
                    .toList()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    retryableFailure = new JobPageFetchException("JOB_IMAGE_TIMEOUT", true);
                    break;
                }
                try {
                    ImageAsset asset = imageFetchGateway.fetch(new ImageCandidate(
                                    candidate.imageRef(), URI.create(candidate.resolvedUrl()), candidate.score()),
                            Duration.ofNanos(remainingNanos));
                    if (total + asset.bytes().length > properties.getMaxTotalImageBytes()) {
                        rejected++;
                        continue;
                    }
                    assets.add(asset);
                    total += asset.bytes().length;
                } catch (JobPageFetchException failure) {
                    rejected++;
                    if (failure.retryable()) retryableFailure = failure;
                } catch (IllegalArgumentException failure) {
                    rejected++;
                }
            }
            if (assets.isEmpty() && retryableFailure != null) throw fetchFailure(retryableFailure);
            return localResponse(new FetchedJobImagesOutput(input.jobId(), assets, rejected, total));
        }

        @Override
        public JsonNode minimalOutput(FetchedJobImagesOutput output, ObjectMapper ignored) {
            var node = objectMapper.createObjectNode()
                    .put("jobId", output.jobId().toString())
                    .put("assetCount", output.assets().size())
                    .put("rejectedCount", output.rejectedCount())
                    .put("totalBytes", output.totalBytes());
            node.set("assets", tree(output.assets().stream()
                    .map(asset -> new SafeImageAsset(asset.imageRef(), asset.mimeType(),
                            asset.bytes().length, asset.width(), asset.height(), asset.contentHash()))
                    .toList()));
            return node;
        }

        @Override public boolean reusable() { return false; }

        @Override
        protected void validateJavaRecord(FetchedJobImagesOutput output, StepExecutionContext context) {
            if (output.jobId() == null || output.assets() == null
                    || output.assets().size() > properties.getMaxImageCandidates()
                    || output.rejectedCount() < 0 || output.totalBytes() < 0
                    || output.totalBytes() > properties.getMaxTotalImageBytes()
                    || output.assets().stream().mapToInt(asset -> asset.bytes().length).sum()
                            != output.totalBytes()) {
                throw new IllegalArgumentException("fetched images output is invalid");
            }
        }
    }

    private final class ExtractJobImageTextExecutor extends JobExecutor<ImageTextOutput> {

        private ExtractJobImageTextExecutor() {
            super(EXTRACT_JOB_IMAGE_TEXT, "job-image-text-output-v2", ImageTextOutput.class,
                    Set.of("items"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            FetchedJobImagesOutput fetched = requiredEphemeral(
                    context, FETCH_JOB_IMAGES, FetchedJobImagesOutput.class);
            JsonNode refs = semanticRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("imageCount", fetched.assets().size())
                    .put("imageContentHash", sha256(fetched.assets().stream()
                            .map(ImageAsset::contentHash).reduce("", (left, right) -> left + right)))
                    .put("imagePolicyVersion", "job-image-policy-v1");
            return localInput(state, refs,
                    fetched.assets().stream().map(ImageAsset::contentHash)
                            .reduce("none", (left, right) -> left + "|" + right)
                            + "|job-image-prompt-v1",
                    tree(new ExtractJobImageTextInput(fetched.assets())));
        }

        @Override
        public boolean requiresProvider(StepExecutionContext context) {
            FetchedJobImagesOutput fetched = requiredEphemeral(
                    context, FETCH_JOB_IMAGES, FetchedJobImagesOutput.class);
            return !fetched.assets().isEmpty() && imageTextExtractionGateway.available();
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ExtractJobImageTextInput input = read(
                    invocation.input().gatewayPayload(), ExtractJobImageTextInput.class);
            if (input.assets().isEmpty() || !imageTextExtractionGateway.available()) {
                return localResponse(new ImageTextOutput(List.of()));
            }
            return imageTextExtractionGateway.extract(new ImageTextExtractionRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    input.assets().stream().map(asset -> new ImageMedia(
                            asset.imageRef(), asset.mimeType(), asset.bytes(), asset.contentHash())).toList(),
                    invocation.prompt().outputSchemaVersion(),
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        public JsonNode minimalOutput(ImageTextOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override public boolean reusable() { return true; }

        @Override
        protected void validateJavaRecord(ImageTextOutput output, StepExecutionContext context) {
            if (output.items() == null || output.items().size() > properties.getMaxImageCandidates()
                    || output.items().stream().anyMatch(item -> item == null || item.text() == null
                            || item.text().length() > MAX_SANITIZED_CHARACTERS
                            || replacementRatio(item.text()) > properties.getMaxReplacementCharacterRatio())) {
                throw new IllegalArgumentException("image text output is invalid");
            }
        }
    }

    private final class ComposeJobSourceTextExecutor extends JobExecutor<ComposedJobSourceOutput> {

        private ComposeJobSourceTextExecutor() {
            super(COMPOSE_JOB_SOURCE_TEXT, "job-source-compose-output-v2", ComposedJobSourceOutput.class,
                    Set.of("jobId", "sourceText", "sourceTextHash", "domUsed", "imageUsed",
                            "manualSource", "truncated", "needsManualInput"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            PageInspectionOutput inspection = requiredEphemeral(
                    context, INSPECT_JOB_PAGE, PageInspectionOutput.class);
            ImageTextOutput imageText = requiredUpstream(
                    context, EXTRACT_JOB_IMAGE_TEXT, ImageTextOutput.class);
            JsonNode refs = semanticRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("classification", inspection.classification().name())
                    .put("domTextHash", inspection.domTextHash())
                    .put("imageTextHash", sha256(imageText.items().stream()
                            .map(ImageTextItem::text).reduce("", (left, right) -> left + right)));
            return localInput(state, refs,
                    inspection.classification() + "|" + inspection.domTextHash() + "|"
                            + imageText.items().size(),
                    tree(new ComposeJobSourceInput(inspection, imageText)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ComposeJobSourceInput input = read(
                    invocation.input().gatewayPayload(), ComposeJobSourceInput.class);
            PageInspectionOutput inspection = input.inspection();
            List<String> imageTexts = input.imageText().items().stream()
                    .map(ImageTextItem::text)
                    .map(JobPostingExtractionWorkflow.this::normalizePlainText)
                    .filter(this::meaningfulImageText)
                    .toList();
            boolean domUsed = inspection.classification() == PageContentClassification.TEXT_SUFFICIENT
                    || (inspection.classification() == PageContentClassification.IMAGE_AUGMENTATION_REQUIRED
                            && meaningfulCharacters(inspection.domText()) >= 120);
            boolean imageUsed = !imageTexts.isEmpty();
            boolean sufficient = inspection.classification() == PageContentClassification.TEXT_SUFFICIENT
                    || (inspection.classification() == PageContentClassification.IMAGE_AUGMENTATION_REQUIRED
                            && imageUsed);
            StringBuilder source = new StringBuilder();
            if (domUsed) source.append("<job_page_dom_text>\n")
                    .append(deduplicateLines(inspection.domText()))
                    .append("\n</job_page_dom_text>");
            for (int index = 0; index < imageTexts.size(); index++) {
                if (!source.isEmpty()) source.append("\n\n");
                source.append("<job_page_image_text image_ref=\"I")
                        .append(index + 1).append("\">\n")
                        .append(deduplicateLines(imageTexts.get(index)))
                        .append("\n</job_page_image_text>");
            }
            String text = source.toString();
            boolean truncated = text.length() > MAX_SANITIZED_CHARACTERS;
            if (truncated) text = text.substring(0, MAX_SANITIZED_CHARACTERS).stripTrailing();
            int sourceMinimum = inspection.sourceType() == JobContentSource.USER_PROVIDED_JOB_TEXT
                    ? 40 : properties.getMinDescriptionMeaningfulCharacters();
            boolean needsManual = !sufficient || meaningfulCharacters(text) < sourceMinimum;
            JobState current = state(invocation.executionContext());
            if (needsManual) {
                commandPort.markNeedsManualInput(current.job().userId(), current.job().jobId(),
                        current.agentRunId(), current.job().version());
                text = "";
            }
            return localResponse(new ComposedJobSourceOutput(
                    input.inspection().jobId(), text, text.isBlank() ? null : sha256(text),
                    domUsed, imageUsed,
                    inspection.sourceType() == JobContentSource.USER_PROVIDED_JOB_TEXT,
                    truncated, needsManual));
        }

        private boolean meaningfulImageText(String value) {
            return !isSemanticNull(value)
                    && meaningfulCharacters(value) >= properties.getMinDescriptionMeaningfulCharacters()
                    && replacementRatio(value) <= properties.getMaxReplacementCharacterRatio();
        }

        @Override
        public JsonNode minimalOutput(ComposedJobSourceOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode().put("jobId", output.jobId().toString())
                    .put("sourceTextHash", output.sourceTextHash())
                    .put("sourceTextLength", output.sourceText().length())
                    .put("domUsed", output.domUsed()).put("imageUsed", output.imageUsed())
                    .put("manualSource", output.manualSource())
                    .put("truncated", output.truncated()).put("needsManualInput", output.needsManualInput());
        }

        @Override
        public Optional<RequiredUserAction> requiredUserAction(
                ComposedJobSourceOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            return output.needsManualInput() ? Optional.of(manualAction(state(context).job())) : Optional.empty();
        }

        @Override public boolean reusable() { return false; }

        @Override
        protected void validateJavaRecord(ComposedJobSourceOutput output, StepExecutionContext context) {
            if (output.jobId() == null || output.sourceText() == null
                    || output.sourceText().length() > MAX_SANITIZED_CHARACTERS
                    || output.needsManualInput() != output.sourceText().isBlank()
                    || output.needsManualInput() == isHash(output.sourceTextHash())) {
                throw new IllegalArgumentException("composed source output is invalid");
            }
        }
    }

    private final class ExtractJobFieldsExecutor extends JobExecutor<ExtractedJobFields> {

        private ExtractJobFieldsExecutor() {
            super(
                    EXTRACT_JOB_FIELDS,
                    "job-fields-output-v2",
                    ExtractedJobFields.class,
                    fieldNames());
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            ComposedJobSourceOutput composed =
                    requiredEphemeral(context, COMPOSE_JOB_SOURCE_TEXT, ComposedJobSourceOutput.class);
            if (composed.needsManualInput()) {
                throw domainFailure("JOB_SOURCE_TEXT_MISSING");
            }
            JsonNode refs = semanticRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("sourceTextHash", composed.sourceTextHash())
                    .put("characterCount", composed.sourceText().length())
                    .put("domUsed", composed.domUsed())
                    .put("imageUsed", composed.imageUsed())
                    .put("truncated", composed.truncated());
            ExtractJobFieldsInput input = new ExtractJobFieldsInput(
                    composed.jobId(), composed.sourceText(), composed.truncated());
            return localInput(
                    state,
                    refs,
                    composed.sourceTextHash(),
                    tree(input));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            state(invocation.executionContext());
            return invocation.chatGateway().chat(new ChatRequest(
                    invocation.modelRoute().providerKey(),
                    invocation.modelRoute().productKey(),
                    invocation.prompt().promptVersion(),
                    invocation.prompt().instructions(),
                    invocation.input().gatewayPayload(),
                    invocation.prompt().outputSchemaVersion(),
                    invocation.prompt().toolAllowlist(),
                    0,
                    CHAT_TIMEOUT,
                    invocation.executionContext().run().priceVersion(),
                    invocation.prompt().maxOutputTokens(),
                    invocation.prompt().outputType()));
        }

        @Override
        public JsonNode minimalOutput(ExtractedJobFields output, ObjectMapper ignored) {
            return safeFieldsReference(output);
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                ExtractedJobFields output, StepExecutionContext context) {
            validateFields(output);
        }
    }

    private final class MergeUserOverridesExecutor
            extends JobExecutor<MergedJobFieldsOutput> {

        private MergeUserOverridesExecutor() {
            super(
                    MERGE_USER_OVERRIDES,
                    "job-merge-output-v2",
                    MergedJobFieldsOutput.class,
                    Set.of("jobId", "jobVersion", "fields", "mergeHash"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            ExtractedJobFields extracted =
                    requiredEphemeral(context, EXTRACT_JOB_FIELDS, ExtractedJobFields.class);
            UserOverrides overrides = state.job().userOverrides();
            JsonNode refs = baseRefs(state.job());
            var values = (tools.jackson.databind.node.ObjectNode) refs;
            values.put("extractedFieldsHash", fieldsHash(extracted))
                    .put("companyOverrideHash", hashOrNull(overrides.companyName()))
                    .put("titleOverrideHash", hashOrNull(overrides.title()))
                    .put("positionOverrideHash", hashOrNull(overrides.positionName()))
                    .put("descriptionOverrideHash", hashOrNull(overrides.descriptionText()))
                    .put(
                            "deadlineOverride",
                            overrides.deadlineAt() == null
                                    ? null
                                    : overrides.deadlineAt().toString());
            MergeUserOverridesInput input = new MergeUserOverridesInput(
                    state.job().jobId(),
                    state.job().version(),
                    extracted,
                    overrides);
            return localInput(
                    state,
                    refs,
                    fieldsHash(extracted) + "|" + overridesHash(overrides),
                    tree(input));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            MergeUserOverridesInput input =
                    read(invocation.input().gatewayPayload(), MergeUserOverridesInput.class);
            JobState current = state(invocation.executionContext());
            if (current.job().version() != invocation.input().expectedResourceVersion()) {
                throw domainFailure("JOB_VERSION_CHANGED_DURING_MERGE");
            }
            ExtractedJobFields merged =
                    merge(input.extractedFields(), current.job().userOverrides());
            return localResponse(new MergedJobFieldsOutput(
                    current.job().jobId(),
                    current.job().version(),
                    merged,
                    fieldsHash(merged)));
        }

        @Override
        public JsonNode minimalOutput(MergedJobFieldsOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("jobId", output.jobId().toString())
                    .put("jobVersion", output.jobVersion())
                    .put("mergeHash", output.mergeHash())
                    .set("fieldsReference", safeFieldsReference(output.fields()));
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                MergedJobFieldsOutput output, StepExecutionContext context) {
            if (output.jobId() == null
                    || output.jobVersion() < 0
                    || output.fields() == null
                    || !isHash(output.mergeHash())) {
                throw structuredFailure("JOB_MERGED_FIELDS_SHAPE_INVALID");
            }
            if (!output.mergeHash().equals(fieldsHash(output.fields()))) {
                throw structuredFailure("JOB_MERGED_FIELDS_HASH_INVALID");
            }
            try {
                validateMergedFields(output.fields());
            } catch (IllegalArgumentException exception) {
                throw structuredFailure("JOB_MERGED_FIELDS_VALUE_INVALID");
            }
        }
    }

    private final class ValidateJobExtractionExecutor
            extends JobExecutor<ValidatedJobFieldsOutput> {

        private ValidateJobExtractionExecutor() {
            super(VALIDATE_JOB_EXTRACTION, "job-extraction-validation-output-v2",
                    ValidatedJobFieldsOutput.class,
                    Set.of("jobId", "jobVersion", "fields", "validationHash", "needsManualInput"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            MergedJobFieldsOutput merged = requiredEphemeral(
                    context, MERGE_USER_OVERRIDES, MergedJobFieldsOutput.class);
            ComposedJobSourceOutput source = requiredEphemeral(
                    context, COMPOSE_JOB_SOURCE_TEXT, ComposedJobSourceOutput.class);
            JsonNode refs = baseRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("mergeHash", merged.mergeHash())
                    .put("sourceTextHash", source.sourceTextHash())
                    .put("domUsed", source.domUsed())
                    .put("imageUsed", source.imageUsed());
            return localInput(state, refs, merged.mergeHash() + "|" + source.sourceTextHash(),
                    tree(new ValidateJobExtractionInput(merged.fields(), source)));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ValidateJobExtractionInput input = read(
                    invocation.input().gatewayPayload(), ValidateJobExtractionInput.class);
            JobState current = state(invocation.executionContext());
            ExtractedJobFields normalized = normalizeSemanticNulls(
                    merge(input.fields(), current.job().userOverrides()));
            boolean valid = validFinalFields(normalized, input.source());
            if (!valid) {
                commandPort.markNeedsManualInput(current.job().userId(), current.job().jobId(),
                        current.agentRunId(), current.job().version());
            }
            return localResponse(new ValidatedJobFieldsOutput(
                    current.job().jobId(), current.job().version(), valid ? normalized : null,
                    valid ? fieldsHash(normalized) : null, !valid));
        }

        @Override
        public JsonNode minimalOutput(ValidatedJobFieldsOutput output, ObjectMapper ignored) {
            var node = objectMapper.createObjectNode().put("jobId", output.jobId().toString())
                    .put("jobVersion", output.jobVersion())
                    .put("validationHash", output.validationHash())
                    .put("needsManualInput", output.needsManualInput());
            if (output.fields() != null) node.set("fieldsReference", safeFieldsReference(output.fields()));
            return node;
        }

        @Override
        public Optional<RequiredUserAction> requiredUserAction(
                ValidatedJobFieldsOutput output, JsonNode minimalOutput, StepExecutionContext context) {
            return output.needsManualInput() ? Optional.of(manualAction(state(context).job())) : Optional.empty();
        }

        @Override public boolean reusable() { return false; }

        @Override
        protected void validateJavaRecord(ValidatedJobFieldsOutput output, StepExecutionContext context) {
            if (output.jobId() == null || output.jobVersion() < 0
                    || output.needsManualInput() == (output.fields() != null)
                    || output.needsManualInput() == isHash(output.validationHash())) {
                throw new IllegalArgumentException("validated extraction output is invalid");
            }
            if (output.fields() != null) validateFields(output.fields());
        }
    }

    private final class ApplyJobExtractionExecutor
            extends JobExecutor<JobExtractionApplyOutput> {

        private ApplyJobExtractionExecutor() {
            super(
                    APPLY_JOB_EXTRACTION,
                    "job-apply-output-v2",
                    JobExtractionApplyOutput.class,
                    Set.of("jobId", "expectedJobVersion", "fields", "applyHash"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            ValidatedJobFieldsOutput validated = requiredEphemeral(
                    context, VALIDATE_JOB_EXTRACTION, ValidatedJobFieldsOutput.class);
            if (validated.needsManualInput() || validated.fields() == null) {
                throw domainFailure("JOB_EXTRACTION_NOT_VALIDATED");
            }
            JsonNode refs = baseRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("mergeHash", validated.validationHash());
            ApplyJobExtractionInput input = new ApplyJobExtractionInput(
                    state.job().jobId(),
                    state.job().version(),
                    validated.fields(),
                    validated.validationHash());
            return localInput(
                    state,
                    refs,
                    validated.validationHash() + "|" + state.job().version(),
                    tree(input));
        }

        @Override
        public AiGatewayResponse invoke(GatewayInvocation invocation) {
            ApplyJobExtractionInput input =
                    read(invocation.input().gatewayPayload(), ApplyJobExtractionInput.class);
            JobState current = state(invocation.executionContext());
            ExtractedJobFields latestMerged =
                    merge(input.fields(), current.job().userOverrides());
            return localResponse(new JobExtractionApplyOutput(
                    current.job().jobId(),
                    current.job().version(),
                    latestMerged,
                    fieldsHash(latestMerged)));
        }

        @Override
        public JsonNode minimalOutput(JobExtractionApplyOutput output, ObjectMapper ignored) {
            return objectMapper.createObjectNode()
                    .put("jobId", output.jobId().toString())
                    .put("expectedJobVersion", output.expectedJobVersion())
                    .put("applyHash", output.applyHash())
                    .set("fieldsReference", safeFieldsReference(output.fields()));
        }

        @Override
        public Optional<DomainApplyPlan> domainApply(
                JobExtractionApplyOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            JobState state = state(context);
            WorkflowSnapshot current = state.job();
            if (!output.jobId().equals(current.jobId())
                    || output.expectedJobVersion() != current.version()
                    || !state.agentRunId().equals(current.latestAgentRunId())) {
                throw domainFailure("JOB_EXTRACTION_APPLY_STALE");
            }
            ExtractedJobFields latestMerged =
                    merge(output.fields(), current.userOverrides());
            try {
                commandPort.applyExtraction(
                        current.userId(),
                        current.jobId(),
                        state.agentRunId(),
                        current.version(),
                        latestMerged.toDomainFields());
            } catch (BusinessException exception) {
                throw domainFailure("JOB_EXTRACTION_APPLY_REJECTED");
            }
            return Optional.empty();
        }

        @Override
        public boolean reusable() {
            return false;
        }

        @Override
        protected void validateJavaRecord(
                JobExtractionApplyOutput output, StepExecutionContext context) {
            if (output.jobId() == null
                    || output.expectedJobVersion() < 0
                    || output.fields() == null
                    || !isHash(output.applyHash())
                    || !output.applyHash().equals(fieldsHash(output.fields()))) {
                throw new IllegalArgumentException("job apply output is invalid");
            }
            validateFields(output.fields());
        }
    }

    private RequiredUserAction manualAction(WorkflowSnapshot job) {
        String label = firstPresent(
                job.userOverrides().positionName(),
                job.userOverrides().title(),
                job.userOverrides().companyName(),
                "채용 공고");
        return new RequiredUserAction(
                RequiredUserActionType.PROVIDE_JOB_TEXT,
                new ResourceReference("JOB", job.jobId(), label),
                "/jobs/" + job.jobId(),
                "공고 내용을 자동으로 충분히 읽지 못했어요. 공고 본문을 직접 입력하면 분석을 계속할 수 있어요.");
    }

    private ExtractedJobFields merge(
            ExtractedJobFields extracted, UserOverrides overrides) {
        Instant deadline = overrides.deadlineAt() != null
                ? overrides.deadlineAt()
                : extracted.deadlineAt();
        BigDecimal confidence = deadline == null
                ? null
                : overrides.deadlineAt() != null
                        ? new BigDecimal("1.000")
                        : extracted.deadlineConfidence();
        return normalizeSemanticNulls(new ExtractedJobFields(
                firstPresent(overrides.companyName(), extracted.companyName()),
                firstPresent(overrides.title(), extracted.title()),
                firstPresent(overrides.positionName(), extracted.positionName()),
                firstPresent(overrides.descriptionText(), extracted.descriptionText()),
                deadline,
                confidence,
                extracted.roleCategory(),
                extracted.employmentType(),
                extracted.location()));
    }

    private void validateFields(ExtractedJobFields fields) {
        if (fields == null
                || !optionalScalar(fields.companyName(), 200)
                || !optionalScalar(fields.title(), 300)
                || !optionalScalar(fields.positionName(), 300)
                || !hasText(fields.descriptionText(), 200_000)
                || !optionalScalar(fields.roleCategory(), 100)
                || !optionalScalar(fields.employmentType(), 100)
                || !optionalScalar(fields.location(), 200)
                || (fields.deadlineAt() == null) != (fields.deadlineConfidence() == null)
                || (fields.deadlineConfidence() != null
                        && (fields.deadlineConfidence().compareTo(BigDecimal.ZERO) < 0
                                || fields.deadlineConfidence().compareTo(BigDecimal.ONE) > 0
                                || fields.deadlineConfidence().scale() > 3))) {
            throw new IllegalArgumentException("extracted job fields are invalid");
        }
    }

    private void validateMergedFields(ExtractedJobFields fields) {
        if (fields == null
                || !optionalScalar(fields.companyName(), 200)
                || !optionalScalar(fields.title(), 300)
                || !optionalScalar(fields.positionName(), 300)
                || (fields.descriptionText() != null && !hasText(fields.descriptionText(), 200_000))
                || !optionalScalar(fields.roleCategory(), 100)
                || !optionalScalar(fields.employmentType(), 100)
                || !optionalScalar(fields.location(), 200)
                || (fields.deadlineAt() == null) != (fields.deadlineConfidence() == null)) {
            throw new IllegalArgumentException("merged job fields are invalid");
        }
    }

    private ExtractedJobFields normalizeSemanticNulls(ExtractedJobFields fields) {
        return new ExtractedJobFields(
                nullableValue(fields.companyName()), nullableValue(fields.title()),
                nullableValue(fields.positionName()), nullableValue(fields.descriptionText()),
                fields.deadlineAt(), fields.deadlineConfidence(), nullableValue(fields.roleCategory()),
                nullableValue(fields.employmentType()), nullableValue(fields.location()));
    }

    private String nullableValue(String value) {
        if (value == null) return null;
        String normalized = normalizePlainText(value);
        return isSemanticNull(normalized) ? null : normalized;
    }

    private boolean validFinalFields(ExtractedJobFields fields, ComposedJobSourceOutput source) {
        if (fields == null || fields.descriptionText() == null || source == null
                || (!source.domUsed() && !source.imageUsed())) return false;
        try {
            validateFields(fields);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        int minimum = source.manualSource() ? 40 : properties.getMinDescriptionMeaningfulCharacters();
        if (isSemanticNull(fields.descriptionText())
                || meaningfulCharacters(fields.descriptionText()) < minimum
                || replacementRatio(fields.descriptionText())
                        > properties.getMaxReplacementCharacterRatio()
                || menuDominated(fields.descriptionText())) return false;
        return java.util.stream.Stream.of(fields.companyName(), fields.title(), fields.positionName())
                .filter(Objects::nonNull)
                .noneMatch(value -> value.indexOf('\ufffd') >= 0 || isSemanticNull(value));
    }

    private boolean isSemanticNull(String value) {
        if (value == null) return true;
        return Set.of("null", "undefined", "none", "n/a", "nan")
                .contains(value.strip().toLowerCase(Locale.ROOT));
    }

    private int replacementCount(String value) {
        return value == null ? 0 : (int) value.chars().filter(c -> c == 0xfffd).count();
    }

    private double replacementRatio(String value) {
        return value == null || value.isEmpty() ? 0d : (double) replacementCount(value) / value.length();
    }

    private int meaningfulCharacters(String value) {
        return value == null ? 0 : (int) value.codePoints()
                .filter(c -> Character.isLetterOrDigit(c)).count();
    }

    private boolean menuDominated(String value) {
        List<String> lines = value.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        if (lines.size() < 5) return false;
        long shortLines = lines.stream().filter(line -> meaningfulCharacters(line) <= 12).count();
        return (double) shortLines / lines.size() > 0.75d;
    }

    private Set<String> fieldNames() {
        return Set.of(
                "companyName",
                "title",
                "positionName",
                "descriptionText",
                "deadlineAt",
                "deadlineConfidence",
                "roleCategory",
                "employmentType",
                "location");
    }

    private Inspection inspectManualText(String text) {
        String normalized = normalizePlainText(text);
        PageQualityMetrics metrics = metrics(
                normalized, normalized, 0, 0, 0, 0, false, false, normalized.length());
        return new Inspection(PageContentClassification.TEXT_SUFFICIENT, metrics, normalized, List.of());
    }

    private Inspection inspectHtml(String html, String baseUri) {
        if (html == null || html.isBlank()) {
            return new Inspection(PageContentClassification.AUTOMATIC_EXTRACTION_INSUFFICIENT,
                    metrics("", "", 0, 0, 0, 0, false, false, 0), "", List.of());
        }
        Document document = Jsoup.parse(html, baseUri);
        int before = normalizePlainText(document.body() == null ? "" : document.body().wholeText()).length();
        String title = normalizePlainText(document.title());
        String description = document
                .select("meta[name=description],meta[property=og:description]")
                .stream()
                .map(element -> element.attr("content"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(this::normalizePlainText)
                .orElse("");
        List<PageImageCandidate> candidates = imageCandidates(document, baseUri);
        int linkCharacters = meaningfulCharacters(document.select("a").text());
        String mainText = normalizePlainText(document.select("main,article,[role=main]").stream()
                .map(Element::wholeText).reduce("", (left, right) -> left + "\n" + right));
        document.select(
                        "script,style,noscript,template,svg,canvas,iframe,object,embed,"
                                + "form,nav,header,footer,[hidden],[aria-hidden=true]")
                .remove();
        String body = document.body() == null ? "" : document.body().wholeText();
        String domText = deduplicateLines(normalizePlainText(String.join("\n", title, description, body)));
        int largeCandidates = (int) candidates.stream().filter(candidate -> candidate.score() >= 60).count();
        PageQualityMetrics metrics = metrics(domText, mainText, linkCharacters, candidates.size(),
                largeCandidates, before, !title.isBlank(), !description.isBlank(), domText.length());
        boolean textSufficient = metrics.meaningfulCharacterCount()
                        >= properties.getMinDomMeaningfulCharacters()
                && metrics.meaningfulLineCount() >= 4
                && metrics.linkTextRatio() <= 0.45d
                && metrics.replacementCharacterRatio()
                        <= properties.getMaxReplacementCharacterRatio()
                && metrics.duplicateLineRatio() <= 0.65d;
        PageContentClassification classification = textSufficient
                ? PageContentClassification.TEXT_SUFFICIENT
                : !candidates.isEmpty()
                        ? PageContentClassification.IMAGE_AUGMENTATION_REQUIRED
                        : PageContentClassification.AUTOMATIC_EXTRACTION_INSUFFICIENT;
        return new Inspection(classification, metrics, domText, candidates);
    }

    private PageQualityMetrics metrics(String text, String mainText, int linkCharacters,
            int candidates, int largeCandidates, int before, boolean title, boolean description,
            int visibleLength) {
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        long placeholders = lines.stream().filter(this::isSemanticNull).count();
        long meaningfulLines = lines.stream().filter(line -> meaningfulCharacters(line) >= 12).count();
        long unique = lines.stream().map(line -> line.toLowerCase(Locale.ROOT)).distinct().count();
        double duplicate = lines.isEmpty() ? 0d : 1d - (double) unique / lines.size();
        int meaningful = meaningfulCharacters(text);
        return new PageQualityMetrics(
                visibleLength, meaningful, meaningfulCharacters(mainText),
                meaningful == 0 ? 0d : Math.min(1d, (double) linkCharacters / meaningful),
                before, replacementCount(text), replacementRatio(text), (int) placeholders,
                (int) meaningfulLines, duplicate, candidates, largeCandidates, title, description,
                text.isBlank() ? null : sha256(text));
    }

    private List<PageImageCandidate> imageCandidates(Document document, String baseUri) {
        Map<String, PageImageCandidate> unique = new LinkedHashMap<>();
        for (Element element : document.select(
                "img[src],img[srcset],img[data-src],img[data-original],picture source[srcset],[style*=background-image]")) {
            List<String> sources = new ArrayList<>();
            addSource(sources, element.attr("src"));
            addSource(sources, element.attr("data-src"));
            addSource(sources, element.attr("data-original"));
            addSrcset(sources, element.attr("srcset"));
            String style = element.attr("style");
            java.util.regex.Matcher background = java.util.regex.Pattern.compile(
                    "(?i)background-image\\s*:\\s*url\\(\\s*['\"]?([^)'\"]+)")
                    .matcher(style);
            if (background.find()) addSource(sources, background.group(1));
            for (String source : sources) {
                try {
                    URI resolved = URI.create(baseUri).resolve(source.strip());
                    if (!resolved.isAbsolute()) continue;
                    int score = imageScore(element, resolved);
                    if (score < 10) continue;
                    String url = resolved.toASCIIString();
                    String hash = sha256(url);
                    PageImageCandidate candidate = new PageImageCandidate(
                            "I" + (unique.size() + 1), url, hash, score,
                            intAttribute(element, "width"), intAttribute(element, "height"));
                    unique.merge(url, candidate,
                            (left, right) -> left.score() >= right.score() ? left : right);
                } catch (IllegalArgumentException ignored) {
                    // Invalid candidates are excluded before the secure fetch boundary.
                }
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(PageImageCandidate::score).reversed())
                .limit(18)
                .toList();
    }

    private void addSource(List<String> target, String value) {
        if (value != null && !value.isBlank() && !value.startsWith("data:")) target.add(value);
    }

    private void addSrcset(List<String> target, String value) {
        if (value == null || value.isBlank()) return;
        for (String item : value.split(",")) {
            String[] parts = item.strip().split("\\s+", 2);
            addSource(target, parts[0]);
        }
    }

    private int imageScore(Element element, URI uri) {
        int width = intAttribute(element, "width");
        int height = intAttribute(element, "height");
        int score = 20;
        if (width >= 600) score += 30;
        if (height >= 300) score += 20;
        if ((long) width * height >= 400_000L) score += 20;
        if (element.closest("main,article,[role=main]") != null) score += 25;
        String hints = (uri.getPath() + " " + element.attr("alt") + " "
                + element.className() + " " + element.id()).toLowerCase(Locale.ROOT);
        if (hints.matches(".*(recruit|recru|hire|job|posting|채용|공고).*")) score += 20;
        if (hints.matches(".*(icon|logo|sprite|pixel|tracking|spacer|button|badge).*")) score -= 80;
        if (width > 0 && width <= 64 || height > 0 && height <= 64) score -= 70;
        if (element.attr("style").toLowerCase(Locale.ROOT).contains("width:100%")) score += 20;
        return score;
    }

    private int intAttribute(Element element, String name) {
        String value = element.attr(name).replaceAll("[^0-9].*", "");
        try { return value.isBlank() ? 0 : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String deduplicateLines(String value) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String line : normalizePlainText(value).lines().toList()) {
            String normalized = line.strip();
            if (!normalized.isBlank()) unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        return String.join("\n", unique.values());
    }

    private String normalizePlainText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replaceAll("[\\p{Zs}\\t\\f]+", " ")
                .replaceAll(" *\\R+ *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private AiExecutionException fetchFailure(JobPageFetchException failure) {
        if (!failure.retryable()) {
            return AiExecutionException.nonRetryable(
                    FailureKind.SAFETY,
                    failure.safeErrorCode(),
                    "채용 공고 페이지를 안전하게 가져오지 못했습니다.");
        }
        FailureKind kind = failure.safeErrorCode().contains("TIMEOUT")
                ? FailureKind.TIMEOUT
                : failure.safeErrorCode().contains("TEMPORARY")
                        ? FailureKind.PROVIDER_5XX
                        : FailureKind.NETWORK;
        return AiExecutionException.retryable(
                kind,
                failure.safeErrorCode(),
                "채용 공고 페이지를 일시적으로 가져오지 못했습니다.");
    }

    private <T> T requiredEphemeral(
            StepExecutionContext context, String stepKey, Class<T> type) {
        Object value = context.ephemeralOutputs().get(stepKey);
        if (!type.isInstance(value)) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "JOB_EPHEMERAL_OUTPUT_MISSING",
                    "채용 공고 추출 단계를 복구하지 못했습니다.");
        }
        return type.cast(value);
    }

    private <T> T requiredUpstream(
            StepExecutionContext context, String stepKey, Class<T> type) {
        JsonNode value = context.upstreamOutputs().get(stepKey);
        if (value == null) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "JOB_UPSTREAM_OUTPUT_MISSING",
                    "채용 공고 추출 단계를 복구하지 못했습니다.");
        }
        return read(value, type);
    }

    private <T> T read(JsonNode value, Class<T> type) {
        try {
            return objectMapper.treeToValue(value, type);
        } catch (Exception exception) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "JOB_CHECKPOINT_INVALID",
                    "채용 공고 추출 단계를 복구하지 못했습니다.");
        }
    }

    private UUID parseJobId(JsonNode input) {
        try {
            return UUID.fromString(input.path("jobId").asText());
        } catch (RuntimeException exception) {
            throw ownerFailure();
        }
    }

    private String overridesHash(UserOverrides overrides) {
        return sha256(String.join(
                "|",
                nullSafe(overrides.companyName()),
                nullSafe(overrides.title()),
                nullSafe(overrides.positionName()),
                nullSafe(hashOrNull(overrides.descriptionText())),
                overrides.deadlineAt() == null ? "-" : overrides.deadlineAt().toString()));
    }

    private String fieldsHash(ExtractedJobFields fields) {
        return sha256(String.join(
                "|",
                canonicalHashField(fields.companyName()),
                canonicalHashField(fields.title()),
                canonicalHashField(fields.positionName()),
                canonicalHashField(fields.descriptionText()),
                canonicalHashField(
                        fields.deadlineAt() == null ? null : fields.deadlineAt().toString()),
                canonicalHashField(fields.deadlineConfidence() == null
                        ? null
                        : fields.deadlineConfidence()
                                .setScale(3, RoundingMode.UNNECESSARY)
                                .toPlainString()),
                canonicalHashField(fields.roleCategory()),
                canonicalHashField(fields.employmentType()),
                canonicalHashField(fields.location())));
    }

    private JsonNode safeFieldsReference(ExtractedJobFields fields) {
        int presentFieldCount = 0;
        presentFieldCount += fields.companyName() == null ? 0 : 1;
        presentFieldCount += fields.title() == null ? 0 : 1;
        presentFieldCount += fields.positionName() == null ? 0 : 1;
        presentFieldCount += fields.descriptionText() == null ? 0 : 1;
        presentFieldCount += fields.deadlineAt() == null ? 0 : 1;
        presentFieldCount += fields.deadlineConfidence() == null ? 0 : 1;
        presentFieldCount += fields.roleCategory() == null ? 0 : 1;
        presentFieldCount += fields.employmentType() == null ? 0 : 1;
        presentFieldCount += fields.location() == null ? 0 : 1;
        return objectMapper.createObjectNode()
                .put("fieldsHash", fieldsHash(fields))
                .put(
                        "descriptionHash",
                        fields.descriptionText() == null
                                ? null
                                : sha256(fields.descriptionText()))
                .put(
                        "descriptionLength",
                        fields.descriptionText() == null
                                ? 0
                                : fields.descriptionText().length())
                .put("presentFieldCount", presentFieldCount);
    }

    private String canonicalHashField(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private String hashOrNull(String value) {
        return value == null ? null : sha256(value);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean optionalScalar(String value, int maximum) {
        return value == null || hasScalar(value, maximum);
    }

    private boolean hasScalar(String value, int maximum) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximum
                && value.chars().noneMatch(Character::isISOControl);
    }

    private boolean hasText(String value, int maximum) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximum
                && value.chars().noneMatch(
                        character -> Character.isISOControl(character)
                                && character != '\n'
                                && character != '\t');
    }

    private boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private AiExecutionException ownerFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.OWNER,
                "RESOURCE_NOT_FOUND",
                "요청한 채용 공고를 찾을 수 없습니다.");
    }

    private AiExecutionException domainFailure(String code) {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                code,
                "채용 공고 추출 결과를 현재 공고에 적용할 수 없습니다.");
    }

    private AiExecutionException structuredFailure(String code) {
        return AiExecutionException.deterministicStructuredOutput(
                code,
                "채용 공고 추출 결과 형식을 확인하지 못했습니다.",
                ValidationPhase.JAVA_RECORD);
    }

    private record JobState(WorkflowSnapshot job, UUID agentRunId) {}

    public enum JobContentSource {
        REMOTE_JOB_PAGE,
        USER_PROVIDED_JOB_TEXT
    }

    public record FetchJobPageInput(
            UUID jobId,
            long jobVersion,
            String sourceUrl,
            String canonicalUrl,
            boolean manualTextPresent,
            String manualTextHash) {}

    public record FetchedJobPageOutput(
            UUID jobId,
            long jobVersion,
            JobContentSource sourceType,
            String sourceUrl,
            String finalUrl,
            String classification,
            int httpStatus,
            Instant retrievedAt,
            int contentLength,
            String contentHash,
            String resolvedCharset,
            String charsetSource,
            int rawByteLength,
            int replacementCharacterCount,
            double replacementCharacterRatio,
            String content) {}

    public record InspectJobPageInput(
            UUID jobId,
            long jobVersion,
            JobContentSource sourceType,
            String finalUrl,
            Instant retrievedAt,
            String content) {}

    public enum PageContentClassification {
        TEXT_SUFFICIENT,
        IMAGE_AUGMENTATION_REQUIRED,
        AUTOMATIC_EXTRACTION_INSUFFICIENT
    }

    public record PageQualityMetrics(
            int visibleTextLength,
            int meaningfulCharacterCount,
            int mainAreaCharacterCount,
            double linkTextRatio,
            int textLengthBeforeChromeRemoval,
            int replacementCharacterCount,
            double replacementCharacterRatio,
            int placeholderLineCount,
            int meaningfulLineCount,
            double duplicateLineRatio,
            int imageCandidateCount,
            int largeImageCandidateCount,
            boolean titlePresent,
            boolean metaDescriptionPresent,
            String domTextHash) {}

    public record PageImageCandidate(
            String imageRef,
            String resolvedUrl,
            String candidateHash,
            int score,
            int declaredWidth,
            int declaredHeight) {}

    public record PageInspectionOutput(
            UUID jobId,
            long jobVersion,
            JobContentSource sourceType,
            Instant retrievedAt,
            PageContentClassification classification,
            PageQualityMetrics metrics,
            String domText,
            String domTextHash,
            boolean truncated,
            List<PageImageCandidate> candidates) {}

    public record FetchJobImagesInput(
            UUID jobId,
            PageContentClassification classification,
            List<PageImageCandidate> candidates) {}

    public record FetchedJobImagesOutput(
            UUID jobId, List<ImageAsset> assets, int rejectedCount, int totalBytes) {}

    public record SafeImageAsset(
            String imageRef, String mimeType, int byteLength, int width, int height, String contentHash) {}

    public record ExtractJobImageTextInput(List<ImageAsset> assets) {}

    public record ImageTextItem(String text, boolean truncated) {}

    public record ImageTextOutput(List<ImageTextItem> items) {}

    public record ComposeJobSourceInput(PageInspectionOutput inspection, ImageTextOutput imageText) {}

    public record ComposedJobSourceOutput(
            UUID jobId,
            String sourceText,
            String sourceTextHash,
            boolean domUsed,
            boolean imageUsed,
            boolean manualSource,
            boolean truncated,
            boolean needsManualInput) {}

    public record ExtractJobFieldsInput(
            UUID jobId,
            String sourceText,
            boolean truncated) {}

    public record ExtractedJobFields(
            String companyName,
            String title,
            String positionName,
            String descriptionText,
            Instant deadlineAt,
            BigDecimal deadlineConfidence,
            String roleCategory,
            String employmentType,
            String location) {

        public ExtractedFields toDomainFields() {
            return new ExtractedFields(
                    companyName,
                    title,
                    positionName,
                    descriptionText,
                    deadlineAt,
                    deadlineConfidence,
                    roleCategory,
                    employmentType,
                    location);
        }
    }

    public record MergeUserOverridesInput(
            UUID jobId,
            long jobVersion,
            ExtractedJobFields extractedFields,
            UserOverrides userOverrides) {}

    public record MergedJobFieldsOutput(
            UUID jobId,
            long jobVersion,
            ExtractedJobFields fields,
            String mergeHash) {}

    public record ValidateJobExtractionInput(
            ExtractedJobFields fields, ComposedJobSourceOutput source) {}

    public record ValidatedJobFieldsOutput(
            UUID jobId,
            long jobVersion,
            ExtractedJobFields fields,
            String validationHash,
            boolean needsManualInput) {}

    public record ApplyJobExtractionInput(
            UUID jobId,
            long expectedJobVersion,
            ExtractedJobFields fields,
            String mergeHash) {}

    public record JobExtractionApplyOutput(
            UUID jobId,
            long expectedJobVersion,
            ExtractedJobFields fields,
            String applyHash) {}

    private record Inspection(
            PageContentClassification classification,
            PageQualityMetrics metrics,
            String domText,
            List<PageImageCandidate> candidates) {}
}
