package com.hiresemble.ai.validation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Generates the Spring AI runtime schema and applies type-bound required-null unions. */
public final class StrictStructuredOutputSchemaGenerator {

    private final ObjectMapper objectMapper;

    public StrictStructuredOutputSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String generate(Class<?> outputType) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(
                    new BeanOutputConverter<>(outputType).getJsonSchema());
            applyJavaContract(root, root, outputType, new HashSet<>());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("AI_STRICT_SCHEMA_GENERATION_FAILED", exception);
        }
    }

    private void applyJavaContract(
            ObjectNode root, JsonNode schema, Type javaType, Set<Type> activeTypes) {
        JsonNode resolved = resolve(root, schema);
        Type valueType = javaType;
        if (javaType instanceof ParameterizedType parameterized) {
            valueType = parameterized.getRawType();
        }
        if (valueType instanceof Class<?> raw && Collection.class.isAssignableFrom(raw)) {
            Type elementType = javaType instanceof ParameterizedType parameterized
                    ? parameterized.getActualTypeArguments()[0]
                    : Object.class;
            applyJavaContract(root, resolved.path("items"), elementType, activeTypes);
            return;
        }
        if (valueType instanceof Class<?> raw && raw.isArray()) {
            applyJavaContract(root, resolved.path("items"), raw.getComponentType(), activeTypes);
            return;
        }
        if (javaType instanceof GenericArrayType arrayType) {
            applyJavaContract(
                    root, resolved.path("items"), arrayType.getGenericComponentType(), activeTypes);
            return;
        }
        if (!(valueType instanceof Class<?> raw) || !raw.isRecord() || !activeTypes.add(javaType)) {
            return;
        }

        try {
            JsonNode properties = resolved.path("properties");
            for (var component : raw.getRecordComponents()) {
                JsonNode property = properties.path(component.getName());
                Schema publicSchema = component.getAnnotation(Schema.class);
                if (component.isAnnotationPresent(ProviderNullable.class)
                        || publicSchema != null && publicSchema.nullable()) {
                    makeRequiredNullable(property, component.getName());
                }
                applyJavaContract(root, property, component.getGenericType(), activeTypes);
            }
        } finally {
            activeTypes.remove(javaType);
        }
    }

    private JsonNode resolve(ObjectNode root, JsonNode schema) {
        JsonNode reference = schema.get("$ref");
        if (reference == null || !reference.isString()) return schema;
        String prefix = "#/$defs/";
        String value = reference.asString();
        if (!value.startsWith(prefix) || value.substring(prefix.length()).contains("/")) {
            throw new IllegalStateException("AI_STRICT_SCHEMA_REFERENCE_UNSUPPORTED");
        }
        return root.path("$defs").path(value.substring(prefix.length()));
    }

    private void makeRequiredNullable(JsonNode schema, String propertyName) {
        if (!(schema instanceof ObjectNode object)) {
            throw new IllegalStateException("AI_STRICT_NULLABLE_SCHEMA_INVALID:" + propertyName);
        }
        JsonNode type = object.get("type");
        if (type != null && type.isArray()) {
            boolean hasNull = false;
            for (JsonNode value : type) {
                if (value.isString() && "null".equals(value.asString())) hasNull = true;
            }
            if (hasNull) return;
        }
        if (type == null || !type.isString() || "null".equals(type.asString())) {
            throw new IllegalStateException("AI_STRICT_NULLABLE_TYPE_INVALID:" + propertyName);
        }
        var union = objectMapper.createArrayNode();
        union.add(type.asString());
        union.add("null");
        object.set("type", union);
    }
}
