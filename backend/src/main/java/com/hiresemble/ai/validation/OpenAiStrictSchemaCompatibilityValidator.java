package com.hiresemble.ai.validation;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates the OpenAI strict Structured Output JSON Schema subset before a request is sent. */
public final class OpenAiStrictSchemaCompatibilityValidator {

    private static final Set<String> TYPES = Set.of(
            "string", "number", "boolean", "integer", "object", "array", "null");
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of(
            "$schema", "$defs", "$ref", "type", "properties", "required",
            "additionalProperties", "items", "enum", "const", "anyOf",
            "description", "title", "minLength", "maxLength", "pattern", "format",
            "minimum", "maximum", "multipleOf", "patternProperties", "minItems", "maxItems");
    private static final Set<String> UNSUPPORTED = Set.of(
            "allOf", "oneOf", "not", "dependentRequired", "dependentSchemas",
            "if", "then", "else", "patternProperties");
    private static final int MAX_DEPTH = 10;
    private static final int MAX_PROPERTIES = 5_000;
    private static final int MAX_SCHEMA_STRINGS = 120_000;
    private static final int MAX_ENUM_VALUES = 1_000;

    private final ObjectMapper objectMapper;

    public OpenAiStrictSchemaCompatibilityValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String schema) {
        JsonNode root;
        try {
            root = objectMapper.readTree(schema);
        } catch (Exception exception) {
            throw incompatible("INVALID_JSON", "$");
        }
        if (root == null || !root.isObject() || root.has("anyOf") || !hasType(root, "object")) {
            throw incompatible("ROOT_OBJECT_REQUIRED", "$");
        }
        Counters counters = new Counters();
        validateNode(root, "$", 1, counters);
        if (counters.properties > MAX_PROPERTIES) {
            throw incompatible("PROPERTY_LIMIT_EXCEEDED", "$");
        }
        if (counters.schemaStringLength > MAX_SCHEMA_STRINGS) {
            throw incompatible("STRING_LIMIT_EXCEEDED", "$");
        }
        if (counters.enumValues > MAX_ENUM_VALUES) {
            throw incompatible("ENUM_LIMIT_EXCEEDED", "$");
        }
    }

    private void validateNode(JsonNode node, String path, int depth, Counters counters) {
        if (depth > MAX_DEPTH) {
            throw incompatible("NESTING_LIMIT_EXCEEDED", path);
        }
        if (node == null || !node.isObject()) {
            throw incompatible("SCHEMA_NODE_INVALID", path);
        }
        for (String keyword : node.propertyNames()) {
            if (!SUPPORTED_KEYWORDS.contains(keyword)) {
                throw incompatible("KEYWORD_NOT_VERIFIED_" + keyword.toUpperCase(), path);
            }
        }
        for (String keyword : UNSUPPORTED) {
            if (node.has(keyword)) {
                throw incompatible("UNSUPPORTED_KEYWORD_" + keyword.toUpperCase(), path);
            }
        }
        countSchemaStrings(node, counters);
        validateType(node, path);

        JsonNode anyOf = node.get("anyOf");
        if (anyOf != null) {
            if (!anyOf.isArray() || anyOf.isEmpty()) {
                throw incompatible("ANY_OF_INVALID", path);
            }
            for (int index = 0; index < anyOf.size(); index++) {
                validateNode(anyOf.get(index), path + ".anyOf[" + index + "]", depth, counters);
            }
        }

        if (hasType(node, "object") || node.has("properties")) {
            validateObject(node, path, depth, counters);
        }
        if (hasType(node, "array")) {
            JsonNode items = node.get("items");
            if (items == null || !items.isObject()) {
                throw incompatible("ARRAY_ITEMS_REQUIRED", path);
            }
            validateNode(items, path + ".items", depth + 1, counters);
        }
        JsonNode definitions = node.get("$defs");
        if (definitions != null) {
            if (!definitions.isObject()) {
                throw incompatible("DEFINITIONS_INVALID", path);
            }
            for (String name : definitions.propertyNames()) {
                validateNode(definitions.get(name), path + ".$defs." + name, depth + 1, counters);
            }
        }
        JsonNode reference = node.get("$ref");
        if (reference != null && (!reference.isString() || !reference.asString().startsWith("#"))) {
            throw incompatible("EXTERNAL_REFERENCE_UNSUPPORTED", path);
        }
    }

    private void validateObject(JsonNode node, String path, int depth, Counters counters) {
        JsonNode properties = node.get("properties");
        if (properties == null || !properties.isObject() || properties.isEmpty()) {
            throw incompatible("OBJECT_PROPERTIES_REQUIRED", path);
        }
        JsonNode additional = node.get("additionalProperties");
        if (additional == null || !additional.isBoolean() || additional.asBoolean()) {
            throw incompatible("ADDITIONAL_PROPERTIES_MUST_BE_FALSE", path);
        }
        JsonNode required = node.get("required");
        if (required == null || !required.isArray()) {
            throw incompatible("REQUIRED_PROPERTIES_MISSING", path);
        }
        Set<String> propertyNames = new HashSet<>();
        propertyNames.addAll(properties.propertyNames());
        Set<String> requiredNames = new HashSet<>();
        required.forEach(value -> {
            if (!value.isString() || !requiredNames.add(value.asString())) {
                throw incompatible("REQUIRED_PROPERTIES_INVALID", path);
            }
        });
        if (!propertyNames.equals(requiredNames)) {
            throw incompatible("ALL_PROPERTIES_MUST_BE_REQUIRED", path);
        }
        counters.properties += propertyNames.size();
        for (String name : propertyNames) {
            validateNode(properties.get(name), path + ".properties." + name, depth + 1, counters);
        }
    }

    private void validateType(JsonNode node, String path) {
        JsonNode type = node.get("type");
        if (type == null) {
            if (!node.has("$ref") && !node.has("anyOf")) {
                throw incompatible("TYPE_REQUIRED", path);
            }
            return;
        }
        if (type.isString()) {
            if (!TYPES.contains(type.asString())) {
                throw incompatible("TYPE_UNSUPPORTED", path);
            }
            return;
        }
        if (!type.isArray() || type.isEmpty()) {
            throw incompatible("TYPE_INVALID", path);
        }
        Set<String> values = new HashSet<>();
        type.forEach(value -> {
            if (!value.isString() || !TYPES.contains(value.asString())
                    || !values.add(value.asString())) {
                throw incompatible("TYPE_UNION_INVALID", path);
            }
        });
        if (values.size() != 2 || !values.contains("null")) {
            throw incompatible("TYPE_UNION_UNSUPPORTED", path);
        }
    }

    private boolean hasType(JsonNode node, String expected) {
        JsonNode type = node.get("type");
        if (type == null) return false;
        if (type.isString()) return expected.equals(type.asString());
        if (type.isArray()) {
            for (JsonNode value : type) {
                if (value.isString() && expected.equals(value.asString())) return true;
            }
        }
        return false;
    }

    private void countSchemaStrings(JsonNode node, Counters counters) {
        JsonNode enumNode = node.get("enum");
        if (enumNode != null) {
            if (!enumNode.isArray()) throw incompatible("ENUM_INVALID", "$");
            counters.enumValues += enumNode.size();
            int enumStringLength = 0;
            for (JsonNode value : enumNode) {
                if (value.isString()) enumStringLength += value.asString().length();
            }
            if (enumNode.size() > 250 && enumStringLength > 15_000) {
                throw incompatible("SINGLE_ENUM_STRING_LIMIT_EXCEEDED", "$");
            }
            counters.schemaStringLength += enumStringLength;
        }
        if (node.has("const") && node.get("const").isString()) {
            counters.schemaStringLength += node.get("const").asString().length();
        }
        if (node.has("properties")) {
            for (String name : node.get("properties").propertyNames()) {
                counters.schemaStringLength += name.length();
            }
        }
        if (node.has("$defs")) {
            for (String name : node.get("$defs").propertyNames()) {
                counters.schemaStringLength += name.length();
            }
        }
    }

    private StrictSchemaCompatibilityException incompatible(String reason, String path) {
        return new StrictSchemaCompatibilityException(reason, path);
    }

    private static final class Counters {
        int properties;
        int schemaStringLength;
        int enumValues;
    }

    public static final class StrictSchemaCompatibilityException extends RuntimeException {
        private final String reason;
        private final String schemaPath;

        StrictSchemaCompatibilityException(String reason, String schemaPath) {
            super("OPENAI_STRICT_SCHEMA_INCOMPATIBLE:" + reason + ":" + schemaPath);
            this.reason = reason;
            this.schemaPath = schemaPath;
        }

        public String reason() {
            return reason;
        }

        public String schemaPath() {
            return schemaPath;
        }
    }
}
