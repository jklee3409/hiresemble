package com.hiresemble.ai.workflow;

import com.hiresemble.agentrun.domain.model.RequiredUserAction;
import com.hiresemble.agentrun.domain.model.RequiredUserActionType;
import com.hiresemble.agentrun.domain.model.ResourceReference;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
    public static final String SANITIZE_PAGE_TEXT = "SANITIZE_PAGE_TEXT";
    public static final String EXTRACT_JOB_FIELDS = "EXTRACT_JOB_FIELDS";
    public static final String MERGE_USER_OVERRIDES = "MERGE_USER_OVERRIDES";
    public static final String APPLY_JOB_EXTRACTION = "APPLY_JOB_EXTRACTION";

    public static final int MAX_SANITIZED_CHARACTERS = 80_000;
    private static final int MAX_RAW_PAGE_CHARACTERS = 10 * 1024 * 1024;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);

    private final JobWorkflowQueryPort queryPort;
    private final JobWorkflowCommandPort commandPort;
    private final JobPageFetchGateway fetchGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JobPostingExtractionWorkflow(
            JobWorkflowQueryPort queryPort,
            JobWorkflowCommandPort commandPort,
            JobPageFetchGateway fetchGateway,
            ObjectMapper objectMapper,
            Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort);
        this.commandPort = Objects.requireNonNull(commandPort);
        this.fetchGateway = Objects.requireNonNull(fetchGateway);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExecutableWorkflowContribution contribution() {
        return new ExecutableWorkflowContribution(
                WorkflowType.JOB_POSTING_EXTRACTION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                List.of(
                        step(FETCH_JOB_PAGE, new FetchJobPageExecutor()),
                        step(SANITIZE_PAGE_TEXT, new SanitizePageTextExecutor()),
                        step(EXTRACT_JOB_FIELDS, new ExtractJobFieldsExecutor()),
                        step(MERGE_USER_OVERRIDES, new MergeUserOverridesExecutor()),
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
                    "job-fetch-output-v1",
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
                    .put("contentHash", output.contentHash());
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
                    manualText);
        }
    }

    private final class SanitizePageTextExecutor extends JobExecutor<SanitizedPageTextOutput> {

        private SanitizePageTextExecutor() {
            super(
                    SANITIZE_PAGE_TEXT,
                    "job-sanitize-output-v1",
                    SanitizedPageTextOutput.class,
                    Set.of(
                            "jobId",
                            "jobVersion",
                            "sourceType",
                            "sourceUrl",
                            "finalUrl",
                            "retrievedAt",
                            "sanitizedText",
                            "sanitizedTextHash",
                            "characterCount",
                            "truncated",
                            "needsManualInput"));
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
            SanitizePageTextInput input = new SanitizePageTextInput(
                    fetched.jobId(),
                    fetched.jobVersion(),
                    fetched.sourceType(),
                    fetched.sourceUrl(),
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
            SanitizePageTextInput input =
                    read(invocation.input().gatewayPayload(), SanitizePageTextInput.class);
            String sanitized = input.sourceType() == JobContentSource.REMOTE_JOB_PAGE
                    ? sanitizeHtml(input.content(), input.finalUrl())
                    : normalizePlainText(input.content());
            boolean truncated = sanitized.length() > MAX_SANITIZED_CHARACTERS;
            if (truncated) {
                sanitized = sanitized.substring(0, MAX_SANITIZED_CHARACTERS).stripTrailing();
            }
            JobState current = state(invocation.executionContext());
            boolean needsManual = sanitized.isBlank();
            long version = current.job().version();
            if (needsManual) {
                JobRecord waiting = commandPort.markNeedsManualInput(
                        current.job().userId(),
                        current.job().jobId(),
                        current.agentRunId(),
                        current.job().version());
                version = waiting.version();
            }
            return localResponse(new SanitizedPageTextOutput(
                    current.job().jobId(),
                    version,
                    input.sourceType(),
                    input.sourceUrl(),
                    input.finalUrl(),
                    input.retrievedAt(),
                    sanitized,
                    needsManual ? null : sha256(sanitized),
                    sanitized.length(),
                    truncated,
                    needsManual));
        }

        @Override
        public JsonNode minimalOutput(SanitizedPageTextOutput output, ObjectMapper ignored) {
            return tree(output);
        }

        @Override
        public Optional<RequiredUserAction> requiredUserAction(
                SanitizedPageTextOutput output,
                JsonNode minimalOutput,
                StepExecutionContext context) {
            return output.needsManualInput()
                    ? Optional.of(manualAction(state(context).job()))
                    : Optional.empty();
        }

        @Override
        protected void validateJavaRecord(
                SanitizedPageTextOutput output, StepExecutionContext context) {
            if (output.jobId() == null
                    || output.jobVersion() < 0
                    || output.sourceType() == null
                    || !hasScalar(output.sourceUrl(), 2_000)
                    || !hasScalar(output.finalUrl(), 2_000)
                    || output.retrievedAt() == null
                    || output.sanitizedText() == null
                    || output.characterCount() != output.sanitizedText().length()
                    || output.characterCount() > MAX_SANITIZED_CHARACTERS
                    || output.needsManualInput() != output.sanitizedText().isBlank()
                    || output.needsManualInput() == isHash(output.sanitizedTextHash())) {
                throw new IllegalArgumentException("sanitized page output is invalid");
            }
        }
    }

    private final class ExtractJobFieldsExecutor extends JobExecutor<ExtractedJobFields> {

        private ExtractJobFieldsExecutor() {
            super(
                    EXTRACT_JOB_FIELDS,
                    "job-fields-output-v1",
                    ExtractedJobFields.class,
                    fieldNames());
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            SanitizedPageTextOutput sanitized =
                    requiredUpstream(context, SANITIZE_PAGE_TEXT, SanitizedPageTextOutput.class);
            if (sanitized.needsManualInput()) {
                throw domainFailure("JOB_SANITIZED_TEXT_MISSING");
            }
            JsonNode refs = semanticRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("sourceType", sanitized.sourceType().name())
                    .put("finalUrl", sanitized.finalUrl())
                    .put("sanitizedTextHash", sanitized.sanitizedTextHash())
                    .put("characterCount", sanitized.characterCount())
                    .put("truncated", sanitized.truncated());
            ExtractJobFieldsInput input = new ExtractJobFieldsInput(
                    sanitized.jobId(),
                    sanitized.sourceType(),
                    sanitized.sourceUrl(),
                    sanitized.finalUrl(),
                    sanitized.retrievedAt(),
                    sanitized.sanitizedText(),
                    sanitized.truncated());
            return localInput(
                    state,
                    refs,
                    sanitized.sanitizedTextHash(),
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
                    "job-merge-output-v1",
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
                validateFields(output.fields());
            } catch (IllegalArgumentException exception) {
                throw structuredFailure("JOB_MERGED_FIELDS_VALUE_INVALID");
            }
        }
    }

    private final class ApplyJobExtractionExecutor
            extends JobExecutor<JobExtractionApplyOutput> {

        private ApplyJobExtractionExecutor() {
            super(
                    APPLY_JOB_EXTRACTION,
                    "job-apply-output-v1",
                    JobExtractionApplyOutput.class,
                    Set.of("jobId", "expectedJobVersion", "fields", "applyHash"));
        }

        @Override
        public StepInput prepare(StepExecutionContext context) {
            JobState state = state(context);
            MergedJobFieldsOutput merged =
                    requiredEphemeral(context, MERGE_USER_OVERRIDES, MergedJobFieldsOutput.class);
            JsonNode refs = baseRefs(state.job());
            ((tools.jackson.databind.node.ObjectNode) refs)
                    .put("mergeHash", merged.mergeHash());
            ApplyJobExtractionInput input = new ApplyJobExtractionInput(
                    state.job().jobId(),
                    state.job().version(),
                    merged.fields(),
                    merged.mergeHash());
            return localInput(
                    state,
                    refs,
                    merged.mergeHash() + "|" + state.job().version(),
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
                "채용 공고 본문을 직접 입력해 주세요.");
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
        return new ExtractedJobFields(
                firstPresent(overrides.companyName(), extracted.companyName()),
                firstPresent(overrides.title(), extracted.title()),
                firstPresent(overrides.positionName(), extracted.positionName()),
                firstPresent(overrides.descriptionText(), extracted.descriptionText()),
                deadline,
                confidence,
                extracted.roleCategory(),
                extracted.employmentType(),
                extracted.location());
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

    private String sanitizeHtml(String html, String baseUri) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document document = Jsoup.parse(html, baseUri);
        String title = normalizePlainText(document.title());
        String description = document
                .select("meta[name=description],meta[property=og:description]")
                .stream()
                .map(element -> element.attr("content"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(this::normalizePlainText)
                .orElse("");
        document.select(
                        "script,style,noscript,template,svg,canvas,iframe,object,embed,"
                                + "form,nav,header,footer,[hidden],[aria-hidden=true]")
                .remove();
        String body = document.body() == null ? "" : document.body().text();
        return normalizePlainText(String.join("\n", title, description, body));
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
        return AiExecutionException.retryable(
                FailureKind.STRUCTURED_OUTPUT,
                code,
                "채용 공고 추출 결과 형식을 확인하지 못했습니다.");
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
            String content) {}

    public record SanitizePageTextInput(
            UUID jobId,
            long jobVersion,
            JobContentSource sourceType,
            String sourceUrl,
            String finalUrl,
            Instant retrievedAt,
            String content) {}

    public record SanitizedPageTextOutput(
            UUID jobId,
            long jobVersion,
            JobContentSource sourceType,
            String sourceUrl,
            String finalUrl,
            Instant retrievedAt,
            String sanitizedText,
            String sanitizedTextHash,
            int characterCount,
            boolean truncated,
            boolean needsManualInput) {}

    public record ExtractJobFieldsInput(
            UUID jobId,
            JobContentSource sourceType,
            String sourceUrl,
            String finalUrl,
            Instant retrievedAt,
            String sanitizedText,
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
}
