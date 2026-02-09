package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.FieldSpecProcessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.BlueprintGenerationException;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                return ClassName.get(PlutusData.class);
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
                    ClassName.get(List.class),
                    TypeName.get(Object.class)
            );
        }

        if (items.size() == 1) {
            return ParameterizedTypeName.get(
                    ClassName.get(List.class),
                    resolveType(namespace, items.get(0))
            );
        }

        if ("Tuple".equalsIgnoreCase(schema.getTitle()) && items.size() == 2) {
            return ParameterizedTypeName.get(
                    ClassName.get(Pair.class),
                    resolveType(namespace, items.get(0)),
                    resolveType(namespace, items.get(1))
            );
        }

        return ParameterizedTypeName.get(
                ClassName.get(List.class),
                TypeName.get(Object.class)
        );
    }

    public ParameterizedTypeName resolveMapType(String namespace, BlueprintSchema schema) {
        return ParameterizedTypeName.get(
                ClassName.get(Map.class),
                resolveType(namespace, schema.getKeys()),
                resolveType(namespace, schema.getValues())
        );
    }

    public ParameterizedTypeName resolveOptionType(String namespace, BlueprintSchema schema) {
        if (schema.getAnyOf() == null || schema.getAnyOf().size() != 2)
            throw new BlueprintGenerationException(
                    "Invalid Option type schema: must have exactly 2 anyOf alternatives (None and Some). " +
                    "Found: " + (schema.getAnyOf() == null ? "null" : schema.getAnyOf().size()));

        BlueprintSchema someSchema = schema.getAnyOf().stream()
                .filter(s -> "Some".equals(s.getTitle()))
                .findFirst()
                .orElseThrow(() -> new BlueprintGenerationException(
                        "Invalid Option type schema: missing 'Some' alternative. " +
                        "Option types must have both 'None' and 'Some' alternatives."));

        if (someSchema.getFields() == null || someSchema.getFields().size() != 1)
            throw new BlueprintGenerationException(
                    "Invalid Option type schema: 'Some' alternative must have exactly 1 field. " +
                    "Found: " + (someSchema.getFields() == null ? "null" : someSchema.getFields().size()));

        return ParameterizedTypeName.get(
                ClassName.get(Optional.class),
                resolveType(namespace, someSchema.getFields().get(0))
        );
    }

    public ParameterizedTypeName resolvePairType(String namespace, BlueprintSchema schema) {
        BlueprintSchema left = schema.getLeft();
        BlueprintSchema right = schema.getRight();

        if (left == null || right == null)
            throw new BlueprintGenerationException(
                    "Invalid Pair type schema: must have both 'left' and 'right' fields. " +
                    "Found - left: " + (left != null ? "present" : "null") +
                    ", right: " + (right != null ? "present" : "null"));

        return ParameterizedTypeName.get(
                ClassName.get(Pair.class),
                resolveType(namespace, left),
                resolveType(namespace, right)
        );
    }

}
