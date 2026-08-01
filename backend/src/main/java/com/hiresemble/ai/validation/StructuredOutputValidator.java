package com.hiresemble.ai.validation;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.hiresemble.ai.validation.StructuredOutputValidationException.RetryDisposition;
import com.hiresemble.ai.validation.StructuredOutputValidationException.ValidationPhase;
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
            throw deterministicFailure(
                    ValidationPhase.JSON_PARSE, "AI_SO_JSON_NOT_PARSEABLE");
        }
        invoke(
                () -> contract.schemaValidator().validate(tree),
                ValidationPhase.SCHEMA_SHAPE,
                "AI_SO_SCHEMA_SHAPE_INVALID");
        T value;
        try {
            value = objectMapper.treeToValue(tree, contract.javaType());
        } catch (Exception ignored) {
            throw deterministicFailure(
                    ValidationPhase.JAVA_BINDING, "AI_SO_JAVA_BINDING_FAILED");
        }
        invoke(
                () -> contract.javaRecordValidator().accept(value),
                ValidationPhase.JAVA_RECORD,
                "AI_SO_JAVA_RECORD_INVALID");
        invoke(
                () -> contract.workflowValidator().accept(value),
                ValidationPhase.WORKFLOW_CONTEXT,
                "AI_SO_WORKFLOW_CONTEXT_INVALID");
        invokeDomain(() -> contract.domainCommandValidator().accept(value));
        return value;
    }

    private void invoke(
            Runnable validation, ValidationPhase phase, String genericSafeReason) {
        try {
            validation.run();
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (StructuredOutputValidationException exception) {
            if (exception.retryDisposition() == RetryDisposition.REPAIR_ONCE) {
                throw AiExecutionException.repairableStructuredOutput(
                        exception.safeReason(),
                        "AI 결과의 의미 제약을 확인하지 못했습니다.",
                        exception.phase(),
                        exception.correctionGuidance());
            }
            throw deterministicFailure(exception.phase(), exception.safeReason());
        } catch (RuntimeException ignored) {
            if (phase != ValidationPhase.JAVA_RECORD
                    && phase != ValidationPhase.WORKFLOW_CONTEXT) {
                throw deterministicFailure(phase, genericSafeReason);
            }
            throw AiExecutionException.repairableStructuredOutput(
                    genericSafeReason,
                    "AI 결과의 의미 제약을 확인하지 못했습니다.",
                    phase,
                    genericCorrectionGuidance(phase));
        }
    }

    private void invokeDomain(Runnable validation) {
        try {
            validation.run();
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (StructuredOutputValidationException ignored) {
            throw domainFailure();
        } catch (RuntimeException ignored) {
            throw domainFailure();
        }
    }

    private AiExecutionException deterministicFailure(
            ValidationPhase phase, String safeReason) {
        return AiExecutionException.deterministicStructuredOutput(
                safeReason, "AI 결과 형식을 확인하지 못했습니다.", phase);
    }

    private AiExecutionException domainFailure() {
        return AiExecutionException.nonRetryable(
                FailureKind.DOMAIN_VALIDATION,
                "AI_DOMAIN_COMMAND_INVALID",
                "AI 결과를 현재 리소스에 적용할 수 없습니다.");
    }

    private String genericCorrectionGuidance(ValidationPhase phase) {
        return switch (phase) {
            case JAVA_RECORD ->
                    "Previous output violated a field constraint. Return a new object that follows every stated field limit and null rule.";
            case WORKFLOW_CONTEXT ->
                    "Previous output referenced data outside the supplied context. Return a new object using only the supplied references.";
            default -> throw new IllegalArgumentException("phase is not repairable");
        };
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
