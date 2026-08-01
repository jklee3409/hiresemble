package com.hiresemble.ai.infrastructure;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaRegistry;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaRegistry.ValidatedSchema;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.openai.errors.OpenAIServiceException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.springframework.ai.chat.model.ChatResponse;

/** Shared safe OpenAI Chat boundary used by text and image-text capabilities. */
final class OpenAiChatFailureSupport {

    enum Capability {
        CHAT("AI_CHAT", "AI 응답", "chat"),
        IMAGE_TEXT("AI_IMAGE", "공고 이미지", "image-text");

        private final String codePrefix;
        private final String userLabel;
        private final String diagnosticName;

        Capability(String codePrefix, String userLabel, String diagnosticName) {
            this.codePrefix = codePrefix;
            this.userLabel = userLabel;
            this.diagnosticName = diagnosticName;
        }
    }

    private OpenAiChatFailureSupport() {}

    static String requireCompletedText(
            ChatResponse response, List<AiUsage> usages, Capability capability) {
        if (response == null || response.getResults() == null
                || response.getResults().size() != 1 || response.hasToolCalls()
                || response.getResult() == null || response.getResult().getOutput() == null) {
            throw incomplete(capability, usages);
        }
        var result = response.getResult();
        var message = result.getOutput();
        Object refusal = message.getMetadata() == null
                ? null : message.getMetadata().get("refusal");
        if (refusal != null && !refusal.toString().isBlank()) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.SAFETY,
                    capability.codePrefix + "_SAFETY_BLOCKED",
                    capability.userLabel + " 처리가 안전 정책에 따라 중단되었습니다.",
                    usages);
        }
        FinishReason finishReason = FinishReason.from(
                result.getMetadata() == null ? null : result.getMetadata().getFinishReason());
        if (finishReason == FinishReason.LENGTH) {
            throw AiExecutionException.deterministicStructuredOutput(
                            capability.codePrefix + "_OUTPUT_TRUNCATED",
                            capability.userLabel + " 결과가 허용된 출력 길이를 초과했습니다.",
                            ValidationPhase.JSON_PARSE)
                    .withIncurredUsages(usages);
        }
        if (finishReason == FinishReason.CONTENT_FILTER) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.SAFETY,
                    capability.codePrefix + "_SAFETY_BLOCKED",
                    capability.userLabel + " 처리가 안전 정책에 따라 중단되었습니다.",
                    usages);
        }
        if (finishReason != FinishReason.STOP) {
            throw incomplete(capability, usages);
        }
        String text = message.getText();
        if (text == null || text.isBlank()) {
            throw AiExecutionException.deterministicStructuredOutput(
                            capability == Capability.CHAT
                                    ? "AI_SO_JSON_NOT_PARSEABLE"
                                    : "AI_IMAGE_OUTPUT_INVALID",
                            capability.userLabel + " 결과 형식을 확인하지 못했습니다.",
                            ValidationPhase.JSON_PARSE)
                    .withIncurredUsages(usages);
        }
        return text;
    }

    static AiExecutionException mapServiceFailure(
            OpenAIServiceException exception, Capability capability) {
        int status = exception.statusCode();
        if (status == 400 && isStructuredSchemaRejection(exception)) {
            return AiExecutionException.nonRetryable(
                    FailureKind.STRUCTURED_SCHEMA,
                    capability.codePrefix + "_STRUCTURED_SCHEMA_REJECTED",
                    capability.userLabel + " 응답 형식 구성이 올바르지 않습니다.");
        }
        if (status == 400) return configuration(capability, "REQUEST_REJECTED");
        if (status == 401 || status == 403) {
            return configuration(capability, "CREDENTIALS_REJECTED");
        }
        if (status == 404) return configuration(capability, "MODEL_OR_ENDPOINT_NOT_FOUND");
        if (status == 429) {
            if (exception.code().filter("insufficient_quota"::equals).isPresent()) {
                return AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        capability.codePrefix + "_PROVIDER_QUOTA_UNAVAILABLE",
                        "AI 서비스 사용 가능 한도를 확인해 주세요.");
            }
            return AiExecutionException.retryable(
                    FailureKind.RATE_LIMIT,
                    capability.codePrefix + "_RATE_LIMITED",
                    capability.userLabel + " 요청이 일시적으로 제한되었습니다.");
        }
        if (status >= 500) {
            return AiExecutionException.retryable(
                    FailureKind.PROVIDER_5XX,
                    capability.codePrefix + "_PROVIDER_UNAVAILABLE",
                    capability.userLabel + " 서비스를 일시적으로 사용할 수 없습니다.");
        }
        return configuration(capability, "CONFIGURATION_INVALID");
    }

    static AiExecutionException timeout(Capability capability, List<AiUsage> usages) {
        return AiExecutionException.retryable(
                        FailureKind.TIMEOUT,
                        capability.codePrefix + "_TIMEOUT",
                        capability.userLabel + " 처리 시간이 초과되었습니다.")
                .withIncurredUsages(usages);
    }

    static AiExecutionException network(Capability capability) {
        return AiExecutionException.retryable(
                FailureKind.NETWORK,
                capability.codePrefix + "_NETWORK_ERROR",
                capability.userLabel + " 서비스에 연결하지 못했습니다.");
    }

    static boolean isTimeout(Throwable value) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }

    static void logProviderFailure(
            Logger log,
            OpenAIServiceException exception,
            ValidatedSchema schema,
            Capability capability) {
        String requestId = safeDiagnostic(exception.headers().values("x-request-id").stream()
                .findFirst().orElse(null));
        log.warn(
                "OpenAI request rejected: status={}, code={}, param={}, requestId={}, schemaName={}, schemaVersion={}, schemaHash={}, contractName={}, capability={}",
                exception.statusCode(),
                safeDiagnostic(exception.code().orElse(null)),
                safeDiagnostic(exception.param().orElse(null)),
                requestId,
                StrictStructuredOutputSchemaRegistry.PROVIDER_SCHEMA_NAME,
                schema.version(),
                schema.hash(),
                schema.contractName(),
                capability.diagnosticName);
    }

    private static AiExecutionException incomplete(
            Capability capability, List<AiUsage> usages) {
        return AiExecutionException.deterministicStructuredOutput(
                        capability.codePrefix + "_COMPLETION_INCOMPLETE",
                        capability.userLabel + " 결과가 정상적으로 완료되지 않았습니다.",
                        ValidationPhase.JSON_PARSE)
                .withIncurredUsages(usages);
    }

    private static AiExecutionException configuration(
            Capability capability, String suffix) {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                capability.codePrefix + "_" + suffix,
                capability.userLabel + " 서비스 구성이 올바르지 않습니다.");
    }

    private static boolean isStructuredSchemaRejection(OpenAIServiceException exception) {
        String code = exception.code().orElse("");
        String param = exception.param().orElse("");
        return "invalid_json_schema".equals(code)
                || code.contains("structured_schema")
                || param.equals("response_format")
                || param.startsWith("response_format.json_schema");
    }

    private static String safeDiagnostic(String value) {
        if (value == null || value.isBlank() || value.length() > 100
                || !value.matches("[A-Za-z0-9_.\\-\\[\\]]+")) return "NOT_AVAILABLE";
        return value;
    }

    private enum FinishReason {
        STOP,
        LENGTH,
        CONTENT_FILTER,
        INCOMPLETE;

        private static FinishReason from(String value) {
            if (value == null || value.isBlank()) return INCOMPLETE;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "stop" -> STOP;
                case "length" -> LENGTH;
                case "content_filter" -> CONTENT_FILTER;
                default -> INCOMPLETE;
            };
        }
    }
}
