package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.squareup.javapoet.CodeBlock;

/**
 * Strategy interface for per-Type code generation.
 * Each implementation handles serialization and deserialization for one {@link Type} value.
 */
public interface FieldCodeGenerator {

    Type supportedType();

    /**
     * Top-level field serialization (for generateToPlutusDataMethod switch).
     */
    CodeBlock generateSerialization(Field field, FieldAccessor accessor);

    /**
     * Top-level field deserialization (for getDeserializeCodeBlockForField switch).
     */
    CodeBlock generateDeserialization(Field field);

    /**
     * Expression-level: convert Java value to PlutusData expression string.
     * Used for nested elements inside collections/tuples.
     * Returns null if this type does not support expression-level serialization.
     */
    default String toPlutusDataExpression(FieldType type, String expression) {
        return null;
    }

    /**
     * Expression-level: convert PlutusData var to Java value expression string.
     * Used for nested elements inside collections/tuples.
     * Returns null if this type does not support expression-level deserialization.
     */
    default String fromPlutusDataExpression(FieldType type, String pdExpression) {
        return null;
    }

    /**
     * Nested element serialization (for if-else chains inside list/map/optional/tuple).
     * Default implementation uses toPlutusDataExpression for simple types.
     */
    default CodeBlock generateNestedSerialization(FieldType type, String baseName, String outputVarName, String expression) {
        String expr = toPlutusDataExpression(type, expression);
        if (expr != null) {
            return CodeBlock.builder()
                    .addStatement("var $L = $L", outputVarName, expr)
                    .build();
        }
        return null;
    }

    /**
     * Nested element deserialization.
     * Default implementation uses fromPlutusDataExpression for simple types.
     */
    default CodeBlock generateNestedDeserialization(FieldType type, String baseName, String outputVarName, String pdExpression) {
        String expr = fromPlutusDataExpression(type, pdExpression);
        if (expr != null) {
            return CodeBlock.builder()
                    .addStatement("var $L = $L", outputVarName, expr)
                    .build();
        }
        return null;
    }
}
