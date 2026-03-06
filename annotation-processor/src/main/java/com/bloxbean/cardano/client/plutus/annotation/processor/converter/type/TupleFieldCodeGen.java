package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.TupleInfo;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.squareup.javapoet.CodeBlock;

public class TupleFieldCodeGen implements FieldCodeGenerator {

    private final FieldCodeGeneratorRegistry registry;
    private final TupleInfo tupleInfo;
    private final TupleCodeGenerator tupleCodeGen;

    public TupleFieldCodeGen(FieldCodeGeneratorRegistry registry, TupleInfo tupleInfo, TupleCodeGenerator tupleCodeGen) {
        this.registry = registry;
        this.tupleInfo = tupleInfo;
        this.tupleCodeGen = tupleCodeGen;
    }

    @Override
    public Type supportedType() { return tupleInfo.type(); }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        CodeBlock nullCheck = accessor.nullCheck(field);
        return tupleCodeGen.generateTopLevelSerialization(tupleInfo, field.getFieldType(),
                field.getName(), accessor.fieldOrGetter(field), nullCheck,
                this::generateElementSerialization);
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add(tupleCodeGen.generateDeserializationFromExpression(tupleInfo, field.getFieldType(),
                        field.getName() + "_field_" + tupleInfo.label().toLowerCase(), field.getName(),
                        "constrData.getPlutusDataList().get(" + field.getIndex() + ")",
                        this::generateElementDeserialization))
                .add("\n")
                .build();
    }

    @Override
    public CodeBlock generateNestedSerialization(FieldType type, String baseName, String outputVarName, String expression) {
        String tupleVarName = baseName + "_" + tupleInfo.label().toLowerCase();
        return tupleCodeGen.generateSerializationFromExpression(tupleInfo, type,
                tupleVarName, outputVarName, expression,
                this::generateElementSerialization);
    }

    @Override
    public CodeBlock generateNestedDeserialization(FieldType type, String baseName, String outputVarName, String pdExpression) {
        String tupleBaseName = baseName + "_" + tupleInfo.label().toLowerCase();
        return tupleCodeGen.generateDeserializationFromExpression(tupleInfo, type,
                tupleBaseName, outputVarName, pdExpression,
                this::generateElementDeserialization);
    }

    private CodeBlock generateElementSerialization(FieldType elementType, String elemBaseName,
                                                   String elemOutputVarName, String accessorExpression) {
        return registry.dispatchNestedSerialization(elementType, elemBaseName, elemOutputVarName, accessorExpression);
    }

    private CodeBlock generateElementDeserialization(FieldType elementType, String elemBaseName,
                                                     String elemOutputVarName, String pdExpression) {
        return registry.dispatchNestedDeserialization(elementType, elemBaseName, elemOutputVarName, pdExpression);
    }
}
