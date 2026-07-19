package com.hiresemble.ai.validation;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.util.Objects;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Enforces parse -> schema -> Java record -> workflow -> domain command validation in that order. */
public final class StructuredOutputValidator {

    private final ObjectMapper objectMapper;

    public StructuredOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T validate(String rawJson, Contract<T> contract) {
        JsonNode tree;
        try {
            tree = objectMapper.readTree(rawJson);
        } catch (Exception ignored) {
            throw structuredFailure();
        }
        invoke(() -> contract.schemaValidator().validate(tree), false);
        T value;
        try {
            value = objectMapper.treeToValue(tree, contract.javaType());
        } catch (Exception ignored) {
            throw structuredFailure();
        }
        invoke(() -> contract.javaRecordValidator().accept(value), false);
        invoke(() -> contract.workflowValidator().accept(value), false);
        invoke(() -> contract.domainCommandValidator().accept(value), true);
        return value;
    }

    private void invoke(Runnable validation, boolean domain) {
        try {
            validation.run();
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            if (domain) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.DOMAIN_VALIDATION,
                        "AI_DOMAIN_COMMAND_INVALID",
                        "AI 결과를 현재 리소스에 적용할 수 없습니다.");
            }
            throw structuredFailure();
        }
    }

    private AiExecutionException structuredFailure() {
        return AiExecutionException.retryable(
                FailureKind.STRUCTURED_OUTPUT,
                "AI_STRUCTURED_OUTPUT_INVALID",
                "AI 결과 형식을 확인하지 못했습니다.");
    }

    @FunctionalInterface
    public interface SchemaValidator {
        void validate(JsonNode value);
    }

    public record Contract<T>(
            Class<T> javaType,
            String schemaVersion,
            SchemaValidator schemaValidator,
            Consumer<T> javaRecordValidator,
            Consumer<T> workflowValidator,
            Consumer<T> domainCommandValidator) {
        public Contract {
            Objects.requireNonNull(javaType, "javaType");
            if (!javaType.isRecord()) {
                throw new IllegalArgumentException("structured output type must be a record");
            }
            if (schemaVersion == null || schemaVersion.isBlank()) {
                throw new IllegalArgumentException("schema version is required");
            }
            Objects.requireNonNull(schemaValidator, "schemaValidator");
            Objects.requireNonNull(javaRecordValidator, "javaRecordValidator");
            Objects.requireNonNull(workflowValidator, "workflowValidator");
            Objects.requireNonNull(domainCommandValidator, "domainCommandValidator");
        }
    }
}
