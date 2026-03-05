package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.squareup.javapoet.CodeBlock;

import java.util.Objects;
import java.util.Optional;

public class OptionalFieldCodeGen implements FieldCodeGenerator {

    private final FieldCodeGeneratorRegistry registry;

    public OptionalFieldCodeGen(FieldCodeGeneratorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Type supportedType() { return Type.OPTIONAL; }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        return generateTopLevelOptionalSer(field.getFieldType(), field.getName(), accessor.fieldOrGetter(field));
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        return generateTopLevelOptionalDeser(field);
    }

    @Override
    public CodeBlock generateNestedSerialization(FieldType type, String baseName, String outputVarName, String expression) {
        // Assign expression to a variable so we can call .isEmpty() and .get()
        String varName = baseName + "_$opt";
        return CodeBlock.builder()
                .addStatement("var $L = $L", varName, expression)
                .add(generateNestedOptionalSerCode(type, varName, outputVarName))
                .build();
    }

    @Override
    public CodeBlock generateNestedDeserialization(FieldType type, String baseName, String outputVarName, String pdExpression) {
        String constrVarName = baseName + "_constr";
        return CodeBlock.builder()
                .addStatement("var $L = ($T)$L", constrVarName, ConstrPlutusData.class, pdExpression)
                .add(generateNestedOptionalDeserCode(type, constrVarName, outputVarName))
                .build();
    }

    // Top-level Optional serialization (field on an object)
    private CodeBlock generateTopLevelOptionalSer(FieldType fieldType, String fieldName, String fieldOrGetterName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        String nestedVarName = fieldName + "_$nested";

        CodeBlock nestedBlock = generateInnerValueSer(genericType, fieldName, nestedVarName,
                "obj." + fieldOrGetterName + ".get()");

        return CodeBlock.builder()
                .add("//Field $L\n", fieldName)
                .addStatement("$T.requireNonNull(obj.$L, \"$L cannot be null\")", Objects.class, fieldOrGetterName, fieldName)
                .beginControlFlow("if(obj.$L.isEmpty())", fieldOrGetterName)
                .addStatement("var $LConstr = $T.builder().alternative(1).data($T.of()).build()", fieldName,
                        ConstrPlutusData.class, ListPlutusData.class)
                .addStatement("constr.getData().add($LConstr)", fieldName)
                .nextControlFlow("else")
                .add(nestedBlock)
                .addStatement("var $LConstr = $T.builder().alternative(0).data($T.of($L)).build()", fieldName,
                        ConstrPlutusData.class, ListPlutusData.class, nestedVarName)
                .addStatement("constr.getData().add($LConstr)", fieldName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    // Nested Optional serialization (Optional inside a collection)
    private CodeBlock generateNestedOptionalSerCode(FieldType fieldType, String varName, String outputVarName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        String nestedVarName = varName + "_$nested";

        CodeBlock nestedBlock = generateInnerValueSer(genericType, varName, nestedVarName,
                varName + ".get()");

        return CodeBlock.builder()
                .add("//Field $L\n", varName)
                .addStatement("$T.requireNonNull($L, \"$L\")", Objects.class, varName, varName + " must not be null")
                .addStatement("$T $L = null", ConstrPlutusData.class, outputVarName)
                .beginControlFlow("if($L.isEmpty())", varName)
                .addStatement("$L = $T.builder().alternative(1).data($T.of()).build()", outputVarName,
                        ConstrPlutusData.class, ListPlutusData.class)
                .nextControlFlow("else")
                .add(nestedBlock)
                .add("\n")
                .addStatement("$L = $T.builder().alternative(0).data($T.of($L)).build()", outputVarName,
                        ConstrPlutusData.class, ListPlutusData.class, nestedVarName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    // Generates serialization code for the inner value of an Optional
    private CodeBlock generateInnerValueSer(FieldType genericType, String baseName, String nestedVarName, String valueExpression) {
        // Dispatch to registry for the inner type
        return registry.dispatchNestedSerialization(genericType, baseName + "_$inner", nestedVarName, valueExpression);
    }

    // Top-level Optional deserialization (field on an object)
    private CodeBlock generateTopLevelOptionalDeser(Field field) {
        FieldType genericType = field.getFieldType().getGenericTypes().get(0);
        String nestedVarName = field.getName() + "_$nested";

        CodeBlock nestedBlock = generateInnerValueDeser(genericType, field.getName(), nestedVarName,
                field.getName() + "PlutusData");

        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add("$L $L = null;\n", field.getFieldType().getFqTypeName(), field.getName())
                .addStatement("var $LConstr = (ConstrPlutusData)constrData.getPlutusDataList().get($L)",
                        field.getName(), field.getIndex())
                .beginControlFlow("if($LConstr.getAlternative() == 1)", field.getName())
                .addStatement("$L = $T.empty()", field.getName(), Optional.class)
                .nextControlFlow("else")
                .addStatement("var $LPlutusData = $LConstr.getData().getPlutusDataList().get(0)",
                        field.getName(), field.getName())
                .add(nestedBlock)
                .addStatement("$L = Optional.ofNullable($L)", field.getName(), nestedVarName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    // Nested Optional deserialization (Optional inside a collection)
    private CodeBlock generateNestedOptionalDeserCode(FieldType fieldType, String varName, String outputVarName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        String nestedVarName = varName + "_$nested";

        CodeBlock nestedBlock = generateInnerValueDeser(genericType, varName, nestedVarName,
                varName + "PlutusData");

        return CodeBlock.builder()
                .add("//Field $L\n", varName)
                .add("$L $L = null;\n", fieldType.getFqTypeName(), outputVarName)
                .beginControlFlow("if($L.getAlternative() == 1)", varName)
                .addStatement("$L = $T.empty()", outputVarName, Optional.class)
                .nextControlFlow("else")
                .addStatement("var $LPlutusData = $L.getData().getPlutusDataList().get(0)", varName, varName)
                .add(nestedBlock)
                .addStatement("$L = Optional.ofNullable($L)", outputVarName, nestedVarName)
                .endControlFlow()
                .add("\n")
                .build();
    }

    // Generates deserialization code for the inner value of an Optional
    private CodeBlock generateInnerValueDeser(FieldType genericType, String baseName, String nestedVarName, String pdExpression) {
        return registry.dispatchNestedDeserialization(genericType, baseName + "_$inner", nestedVarName, pdExpression);
    }

}
