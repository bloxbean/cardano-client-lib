package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.FieldSpecProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.math.BigInteger;
import java.util.List;

/**
 * Resolves JavaPoet {@link TypeName} representations for nested blueprint schemas,
 * delegating to {@link FieldSpecProcessor} when a referenced datum needs generation.
 */
public class SchemaTypeResolver {

    private final FieldSpecProcessor fieldSpecProcessor;

    public SchemaTypeResolver(FieldSpecProcessor fieldSpecProcessor) {
        this.fieldSpecProcessor = fieldSpecProcessor;
    }

    public TypeName resolveType(String namespace, BlueprintSchema schema) {
        if (schema.getDataType() == null) {
            // Check if this is an abstract placeholder type (like "Data" or "Redeemer")
            // These have no structure and should map to PlutusData
            if (BlueprintUtil.isAbstractPlutusDataType(schema)) {
                return ClassName.get("com.bloxbean.cardano.client.plutus.spec", "PlutusData");
            }
            return fieldSpecProcessor.getInnerDatumClass(namespace, schema);
        }

        BlueprintDatatype datatype = schema.getDataType();
        switch (datatype) {
            case bytes:
                return TypeName.get(byte[].class);
            case integer:
                return TypeName.get(BigInteger.class);
            case string:
                return TypeName.get(String.class);
            case bool:
                return TypeName.get(Boolean.class);
            case list:
                return resolveListType(namespace, schema);
            case map:
                return resolveMapType(namespace, schema);
            case option:
                return resolveOptionType(namespace, schema);
            case pair:
                return resolvePairType(namespace, schema);
            default:
                return TypeName.get(String.class);
        }
    }

    public ParameterizedTypeName resolveListType(String namespace, BlueprintSchema schema) {
        List<BlueprintSchema> items = schema.getItems();

        if (items == null || items.isEmpty()) {
            return ParameterizedTypeName.get(
                    ClassName.get("java.util", "List"),
                    TypeName.get(Object.class)
            );
        }

        if (items.size() == 1) {
            return ParameterizedTypeName.get(
                    ClassName.get("java.util", "List"),
                    resolveType(namespace, items.get(0))
            );
        }

        if ("Tuple".equalsIgnoreCase(schema.getTitle()) && items.size() == 2) {
            return ParameterizedTypeName.get(
                    ClassName.get("com.bloxbean.cardano.client.plutus.blueprint.type", "Pair"),
                    resolveType(namespace, items.get(0)),
                    resolveType(namespace, items.get(1))
            );
        }

        return ParameterizedTypeName.get(
                ClassName.get("java.util", "List"),
                TypeName.get(Object.class)
        );
    }

    public ParameterizedTypeName resolveMapType(String namespace, BlueprintSchema schema) {
        return ParameterizedTypeName.get(
                ClassName.get("java.util", "Map"),
                resolveType(namespace, schema.getKeys()),
                resolveType(namespace, schema.getValues())
        );
    }

    public ParameterizedTypeName resolveOptionType(String namespace, BlueprintSchema schema) {
        if (schema.getAnyOf() == null || schema.getAnyOf().size() != 2)
            throw new IllegalArgumentException("Option type should have 2 anyOfs");

        BlueprintSchema someSchema = schema.getAnyOf().stream()
                .filter(s -> "Some".equals(s.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Option type should have a Some type"));

        if (someSchema.getFields() == null || someSchema.getFields().size() != 1)
            throw new IllegalArgumentException("Option type should have only one field in Some type");

        return ParameterizedTypeName.get(
                ClassName.get("java.util", "Optional"),
                resolveType(namespace, someSchema.getFields().get(0))
        );
    }

    public ParameterizedTypeName resolvePairType(String namespace, BlueprintSchema schema) {
        BlueprintSchema left = schema.getLeft();
        BlueprintSchema right = schema.getRight();

        if (left == null || right == null)
            throw new IllegalArgumentException("Pair type should have left and right fields");

        return ParameterizedTypeName.get(
                ClassName.get("com.bloxbean.cardano.client.plutus.blueprint.type", "Pair"),
                resolveType(namespace, left),
                resolveType(namespace, right)
        );
    }
}
