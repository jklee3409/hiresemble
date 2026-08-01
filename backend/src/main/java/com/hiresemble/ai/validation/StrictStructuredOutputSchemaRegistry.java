package com.hiresemble.ai.validation;

import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Generates, validates and retains the exact strict schema later sent by ChatGateway. */
public final class StrictStructuredOutputSchemaRegistry {

    public static final String PROVIDER_SCHEMA_NAME = "json_schema";

    private final Map<SchemaKey, ValidatedSchema> schemas;

    public StrictStructuredOutputSchemaRegistry(
            PromptRegistry promptRegistry,
            StrictStructuredOutputSchemaGenerator generator,
            OpenAiStrictSchemaCompatibilityValidator validator) {
        Map<SchemaKey, ValidatedSchema> values = new LinkedHashMap<>();
        for (PromptDefinition definition : promptRegistry.strictStructuredOutputDefinitions()) {
            String schema = generator.generate(definition.outputType());
            validator.validate(schema);
            SchemaKey key = new SchemaKey(definition.outputType(), definition.outputSchemaVersion());
            ValidatedSchema value = new ValidatedSchema(
                    contractName(definition),
                    definition.outputSchemaVersion(),
                    sha256(schema),
                    schema);
            ValidatedSchema previous = values.putIfAbsent(key, value);
            if (previous != null && !previous.schema().equals(value.schema())) {
                throw new IllegalStateException("AI_STRICT_SCHEMA_DEFINITION_CONFLICT");
            }
        }
        this.schemas = Map.copyOf(values);
    }

    public ValidatedSchema require(Class<?> outputType, String outputSchemaVersion) {
        ValidatedSchema schema = schemas.get(new SchemaKey(outputType, outputSchemaVersion));
        if (schema == null) {
            throw new IllegalStateException("AI_STRICT_SCHEMA_NOT_REGISTERED");
        }
        return schema;
    }

    public Map<SchemaKey, ValidatedSchema> schemas() {
        return schemas;
    }

    private static String contractName(PromptDefinition definition) {
        return (definition.key().workflowType().name() + "_" + definition.key().stepKey())
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record SchemaKey(Class<?> outputType, String outputSchemaVersion) {
        public SchemaKey {
            Objects.requireNonNull(outputType, "outputType");
            if (outputSchemaVersion == null || outputSchemaVersion.isBlank()) {
                throw new IllegalArgumentException("outputSchemaVersion is required");
            }
        }
    }

    public record ValidatedSchema(
            String contractName,
            String version,
            String hash,
            String schema) {}
}
