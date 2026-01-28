package com.bloxbean.cardano.client.plutus.blueprint.registry;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a canonical signature for a blueprint schema by normalising its structure into a stable JSON representation.
 */
public class SchemaSignatureBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public SchemaSignature build(BlueprintSchema schema) {
        Objects.requireNonNull(schema, "schema cannot be null");
        try {
            IdentityHashMap<BlueprintSchema, Boolean> visiting = new IdentityHashMap<>();
            Object node = toNode(schema, visiting);
            String json = OBJECT_MAPPER.writeValueAsString(node);
            return SchemaSignature.of(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise blueprint schema", e);
        }
    }

    private Object toNode(BlueprintSchema schema, IdentityHashMap<BlueprintSchema, Boolean> visiting) {
        if (schema == null)
            return null;

        if (schema.getRef() != null && !schema.getRef().isBlank()) {
            Map<String, Object> refNode = new LinkedHashMap<>();
            refNode.put("ref", normaliseReference(schema.getRef()));
            return refNode;
        }

        if (visiting.containsKey(schema)) {
            Map<String, Object> recursiveNode = new LinkedHashMap<>();
            recursiveNode.put("recursive", schemaIdentity(schema));
            return recursiveNode;
        }

        visiting.put(schema, Boolean.TRUE);

        Map<String, Object> node = new LinkedHashMap<>();
        putIfNotNull(node, "title", schema.getTitle());
        putIfNotNull(node, "description", schema.getDescription());
        putIfNotNull(node, "comment", schema.getComment());

        if (schema.getDataType() != null)
            node.put("dataType", schema.getDataType().name());

        if (schema.getEnumLiterals() != null && schema.getEnumLiterals().length > 0)
            node.put("enum", List.of(schema.getEnumLiterals()));

        putIfNonZero(node, "maxLength", schema.getMaxLength());
        putIfNonZero(node, "minLength", schema.getMinLength());
        putIfNonZero(node, "multipleOf", schema.getMultipleOf());
        putIfNonZero(node, "maximum", schema.getMaximum());
        putIfNonZero(node, "exclusiveMaximum", schema.getExclusiveMaximum());
        putIfNonZero(node, "minimum", schema.getMinimum());
        putIfNonZero(node, "exclusiveMinimum", schema.getExclusiveMinimum());
        putIfNonZero(node, "maxItems", schema.getMaxItems());
        putIfNonZero(node, "minItems", schema.getMinItems());
        if (schema.isUniqueItems())
            node.put("uniqueItems", true);

        putIfNonZero(node, "index", schema.getIndex());

        if (schema.getFields() != null && !schema.getFields().isEmpty())
            node.put("fields", toList(schema.getFields(), visiting));

        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty())
            node.put("anyOf", toList(schema.getAnyOf(), visiting));

        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty())
            node.put("allOf", toList(schema.getAllOf(), visiting));

        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty())
            node.put("oneOf", toList(schema.getOneOf(), visiting));

        if (schema.getNotOf() != null && !schema.getNotOf().isEmpty())
            node.put("notOf", toList(schema.getNotOf(), visiting));

        if (schema.getItems() != null && !schema.getItems().isEmpty())
            node.put("items", toList(schema.getItems(), visiting));

        if (schema.getKeys() != null)
            node.put("keys", toNode(schema.getKeys(), visiting));

        if (schema.getValues() != null)
            node.put("values", toNode(schema.getValues(), visiting));

        if (schema.getLeft() != null)
            node.put("left", toNode(schema.getLeft(), visiting));

        if (schema.getRight() != null)
            node.put("right", toNode(schema.getRight(), visiting));

        visiting.remove(schema);

        return node;
    }

    private List<Object> toList(List<BlueprintSchema> schemas, IdentityHashMap<BlueprintSchema, Boolean> visiting) {
        List<Object> list = new ArrayList<>(schemas.size());
        for (BlueprintSchema schema : schemas) {
            list.add(toNode(schema, visiting));
        }
        return list;
    }

    private void putIfNotNull(Map<String, Object> node, String key, Object value) {
        if (value != null && !(value instanceof String && ((String) value).isBlank()))
            node.put(key, value);
    }

    private void putIfNonZero(Map<String, Object> node, String key, int value) {
        if (value != 0)
            node.put(key, value);
    }

    private String normaliseReference(String ref) {
        String normalised = ref.replace("#/definitions/", "");
        normalised = normalised.replace("~1", "/");
        return normalised;
    }

    private Object schemaIdentity(BlueprintSchema schema) {
        String title = schema.getTitle();
        BlueprintDatatype datatype = schema.getDataType();
        if (title != null && !title.isBlank())
            return title;
        if (datatype != null)
            return datatype.name();
        return System.identityHashCode(schema);
    }
}
