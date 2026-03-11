package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.type.*;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.squareup.javapoet.CodeBlock;

import java.util.EnumMap;

/**
 * Registry of {@link FieldCodeGenerator} implementations, one per {@link Type}.
 * Provides dispatch methods for nested serialization/deserialization used by composite generators.
 */
public class FieldCodeGeneratorRegistry {

    private final EnumMap<Type, FieldCodeGenerator> generators = new EnumMap<>(Type.class);

    public FieldCodeGeneratorRegistry() {
        register(new IntegerFieldCodeGen());
        register(new BytesFieldCodeGen());
        register(new StringFieldCodeGen());
        register(new BoolFieldCodeGen());
        register(new PlutusDataFieldCodeGen());
        register(new ConstructorFieldCodeGen());
        register(new ListFieldCodeGen(this));
        register(new MapFieldCodeGen(this));
        register(new OptionalFieldCodeGen(this));
        // Tuple types — all use the same TupleFieldCodeGen with different TupleInfo
        TupleCodeGenerator tupleCodeGen = new TupleCodeGenerator();
        for (TupleInfo info : TupleInfo.values()) {
            register(new TupleFieldCodeGen(this, info, tupleCodeGen));
        }
    }

    private void register(FieldCodeGenerator gen) {
        generators.put(gen.supportedType(), gen);
    }

    public FieldCodeGenerator get(Type type) {
        return generators.get(type);
    }

    /**
     * Dispatch nested element serialization to the appropriate generator.
     * Used by composite generators (List, Map, Optional, Tuple) for recursive dispatch.
     */
    public CodeBlock dispatchNestedSerialization(FieldType type, String baseName, String outputVarName, String expression) {
        FieldCodeGenerator gen = get(type.getType());
        if (gen != null) {
            return gen.generateNestedSerialization(type, baseName, outputVarName, expression);
        }
        return null;
    }

    /**
     * Dispatch nested element deserialization to the appropriate generator.
     */
    public CodeBlock dispatchNestedDeserialization(FieldType type, String baseName, String outputVarName, String pdExpression) {
        FieldCodeGenerator gen = get(type.getType());
        if (gen != null) {
            return gen.generateNestedDeserialization(type, baseName, outputVarName, pdExpression);
        }
        return null;
    }

    /**
     * Get the expression-level serialization string for a type.
     * Returns the toPlutusData expression or null if unsupported.
     */
    public String toPlutusDataExpression(FieldType type, String expression) {
        FieldCodeGenerator gen = get(type.getType());
        if (gen != null) {
            return gen.toPlutusDataExpression(type, expression);
        }
        return null;
    }

    /**
     * Get the expression-level deserialization string for a type.
     */
    public String fromPlutusDataExpression(FieldType type, String pdExpression) {
        FieldCodeGenerator gen = get(type.getType());
        if (gen != null) {
            return gen.fromPlutusDataExpression(type, pdExpression);
        }
        return null;
    }
}
