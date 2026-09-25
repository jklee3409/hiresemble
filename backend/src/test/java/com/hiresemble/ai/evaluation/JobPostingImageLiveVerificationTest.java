package com.hiresemble.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.infrastructure.SpringAiOpenAiChatGateway;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.ImageTextExtractionGateway;
import com.hiresemble.ai.port.ImageTextExtractionGateway.ImageMedia;
import com.hiresemble.ai.port.ImageTextExtractionGateway.ImageTextExtractionRequest;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.workflow.CanonicalWorkflowDefinitions;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow.ExtractJobFieldsInput;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow.ExtractedJobFields;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow.ImageTextItem;
import com.hiresemble.ai.workflow.JobPostingExtractionWorkflow.ImageTextOutput;
import com.hiresemble.job.application.port.JobImageFetchGateway;
import com.hiresemble.job.application.port.JobImageFetchGateway.ImageAsset;
import com.hiresemble.job.application.port.JobImageFetchGateway.ImageCandidate;
import com.hiresemble.job.application.port.JobPageFetchGateway;
import com.hiresemble.job.application.port.JobPageFetchGateway.FetchResult;
import com.hiresemble.job.application.port.JobPageFetchGateway.PageClassification;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in, paid check that an image-only job posting URL is read end to end: the real SSRF-safe
 * page and image fetch, then one image-text call and one field-extraction call. Provider requests
 * are counted before they are sent and never exceed {@link #HARD_CALL_LIMIT}. It only runs through
 * the {@code jobPostingLiveVerification} Gradle task and never in test, check, CI, or E2E.
 */
class JobPostingImageLiveVerificationTest extends PostgresIntegrationTest {

    private static final int HARD_CALL_LIMIT = 3;
    private static final long PRICE_VERSION = 2026080601L;
    private static final Path REPORT = Path.of("build", "reports", "job-posting-live", "report.md");

    @Autowired private JobPageFetchGateway pageGateway;
    @Autowired private JobImageFetchGateway imageGateway;
    @Autowired private ImageTextExtractionGateway imageTextGateway;
    @Autowired private SpringAiOpenAiChatGateway chatGateway;
    @Autowired private PromptRegistry prompts;
    @Autowired private ObjectMapper objectMapper;

    private final AtomicInteger providerCalls = new AtomicInteger();

    @Test
    void imageOnlyPostingIsReadAndExtracted() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB_POSTING_LIVE_VERIFY_ENABLED")),
                "IMPLEMENTED_NOT_LIVE_VERIFIED: job posting live verification gate is disabled");
        assumeTrue(
                System.getenv("AI_PROVIDER_API_KEY") != null && !System.getenv("AI_PROVIDER_API_KEY").isBlank(),
                "IMPLEMENTED_NOT_LIVE_VERIFIED: provider key is absent");
        URI url = URI.create(env("JOB_POSTING_LIVE_URL", "https://nhqv.recruiter.co.kr/career/jobs/128898"));
        String model = env("JOB_POSTING_LIVE_MODEL", env("AI_MODEL_LOW_COST", "gpt-5-mini"));
        int allowedCalls = Math.min(HARD_CALL_LIMIT, Integer.parseInt(env("JOB_POSTING_LIVE_MAX_CALLS", "2")));
        StringBuilder report = new StringBuilder("# Job posting live verification\n\n")
                .append("- url: ").append(url).append("\n- model: ").append(model)
                .append("\n- allowed provider calls: ").append(allowedCalls).append('\n');

        FetchResult page = pageGateway.fetch(url);
        report.append("- page classification: ").append(page.classification()).append('\n');
        assertThat(page.classification()).isEqualTo(PageClassification.FETCHED);
        Document document = Jsoup.parse(page.html(), page.finalUri().toASCIIString());
        List<String> images = document.select("main img[src]").eachAttr("abs:src");
        String domText = document.select("main").text();
        report.append("- DOM text characters: ").append(domText.length())
                .append("\n- body images: ").append(images.size()).append('\n');
        assertThat(images).isNotEmpty();
        ImageAsset asset = imageGateway.fetch(
                new ImageCandidate("I1", URI.create(images.getFirst()), 45), Duration.ofSeconds(15));
        report.append("- image: ").append(asset.mimeType()).append(' ')
                .append(asset.width()).append('x').append(asset.height()).append('\n');

        PromptDefinition imagePrompt = prompt(JobPostingExtractionWorkflow.EXTRACT_JOB_IMAGE_TEXT);
        AiGatewayResponse ocr = paid(allowedCalls, report, "image text", () -> imageTextGateway.extract(
                new ImageTextExtractionRequest(
                        "openai", model, imagePrompt.promptVersion(), imagePrompt.instructions(),
                        List.of(new ImageMedia("I1", asset.mimeType(), asset.bytes(), asset.contentHash())),
                        imagePrompt.outputSchemaVersion(), Duration.ofSeconds(90), PRICE_VERSION,
                        imagePrompt.maxOutputTokens(), imagePrompt.outputType())));
        String imageText = objectMapper.readValue(ocr.rawJson(), ImageTextOutput.class).items().stream()
                .map(ImageTextItem::text)
                .collect(Collectors.joining("\n"));
        report.append("- image text characters: ").append(imageText.length()).append('\n');
        assertThat(imageText.replaceAll("\\s", "").length()).isGreaterThanOrEqualTo(120);

        PromptDefinition fieldsPrompt = prompt(JobPostingExtractionWorkflow.EXTRACT_JOB_FIELDS);
        String source = "<job_page_dom_text>\n" + domText + "\n</job_page_dom_text>\n\n"
                + "<job_page_image_text image_ref=\"I1\">\n" + imageText + "\n</job_page_image_text>";
        AiGatewayResponse fieldsResponse = paid(allowedCalls, report, "job fields", () -> chatGateway.chat(
                new ChatRequest(
                        "openai", model, fieldsPrompt.promptVersion(), fieldsPrompt.instructions(),
                        objectMapper.valueToTree(new ExtractJobFieldsInput(UUID.randomUUID(), source, false)),
                        fieldsPrompt.outputSchemaVersion(), fieldsPrompt.toolAllowlist(), 0,
                        Duration.ofSeconds(45), PRICE_VERSION, fieldsPrompt.maxOutputTokens(),
                        fieldsPrompt.outputType())));
        ExtractedJobFields fields = objectMapper.readValue(fieldsResponse.rawJson(), ExtractedJobFields.class);
        report.append("\n## Extracted fields\n\n")
                .append("- companyName: ").append(fields.companyName())
                .append("\n- title: ").append(fields.title())
                .append("\n- positionName: ").append(fields.positionName())
                .append("\n- deadlineAt: ").append(fields.deadlineAt())
                .append("\n- roleCategory: ").append(fields.roleCategory())
                .append("\n- employmentType: ").append(fields.employmentType())
                .append("\n- location: ").append(fields.location())
                .append("\n- descriptionText characters: ")
                .append(fields.descriptionText() == null ? 0 : fields.descriptionText().length())
                .append("\n\n## Image text\n\n```text\n").append(imageText).append("\n```\n")
                .append("\n## descriptionText\n\n```text\n").append(fields.descriptionText()).append("\n```\n");
        write(report);
        assertThat(fields.title()).isNotBlank();
        assertThat(fields.descriptionText()).hasSizeGreaterThanOrEqualTo(120);
    }

    private AiGatewayResponse paid(
            int allowedCalls, StringBuilder report, String label, Supplier<AiGatewayResponse> call)
            throws Exception {
        if (providerCalls.incrementAndGet() > allowedCalls) {
            write(report.append("- STOPPED: provider call limit reached before ").append(label).append('\n'));
            throw new IllegalStateException("paid provider call limit reached");
        }
        long started = System.nanoTime();
        try {
            AiGatewayResponse response = call.get();
            BigDecimal cost = response.usages().stream()
                    .map(AiUsage::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
            report.append("- call ").append(providerCalls.get()).append(' ').append(label)
                    .append(": ").append((System.nanoTime() - started) / 1_000_000).append(" ms, input ")
                    .append(response.usages().stream().mapToLong(AiUsage::inputUnits).sum())
                    .append(" / output ").append(response.usages().stream().mapToLong(AiUsage::outputUnits).sum())
                    .append(" tokens, USD ").append(cost.toPlainString()).append('\n');
            return response;
        } catch (RuntimeException failure) {
            write(report.append("- call ").append(providerCalls.get()).append(' ').append(label)
                    .append(" failed after ").append((System.nanoTime() - started) / 1_000_000)
                    .append(" ms: ").append(failure instanceof AiExecutionException execution
                            ? execution.safeCode() : failure.getClass().getSimpleName())
                    .append('\n'));
            throw failure;
        }
    }

    private PromptDefinition prompt(String stepKey) {
        return prompts.require(
                WorkflowType.JOB_POSTING_EXTRACTION,
                CanonicalWorkflowDefinitions.JOB_POSTING_EXTRACTION_VERSION,
                stepKey);
    }

    private static void write(StringBuilder report) throws Exception {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
