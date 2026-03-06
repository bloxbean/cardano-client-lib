package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.squareup.javapoet.CodeBlock;

import java.util.ArrayList;

public class ListFieldCodeGen implements FieldCodeGenerator {

    private final FieldCodeGeneratorRegistry registry;

    public ListFieldCodeGen(FieldCodeGeneratorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Type supportedType() { return Type.LIST; }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        String outputListName = field.getName() + "ListPlutusData";
        CodeBlock listCodeBlock = generateListSerCode(field.getFieldType(), field.getName(),
                outputListName, "item", "obj." + accessor.fieldOrGetter(field));
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add(accessor.nullCheck(field))
                .add(listCodeBlock)
                .addStatement("constr.getData().add($L)", outputListName)
                .add("\n")
                .build();
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .addStatement("var $LList = (ListPlutusData)constrData.getPlutusDataList().get($L)",
                        field.getName(), field.getIndex())
                .add(generateListDeserCode(field.getFieldType(), field.getName(),
                        field.getName() + "List", "item"))
                .build();
    }

    @Override
    public CodeBlock generateNestedSerialization(FieldType type, String baseName, String outputVarName, String expression) {
        String loopVarName = baseName + "_$item";
        return generateListSerCode(type, baseName, outputVarName, loopVarName, expression);
    }

    @Override
    public CodeBlock generateNestedDeserialization(FieldType type, String baseName, String outputVarName, String pdExpression) {
        String listVarName = baseName + "_list";
        String loopVarName = baseName + "_item";
        return CodeBlock.builder()
                .addStatement("var $L = (ListPlutusData)$L", listVarName, pdExpression)
                .add(generateListDeserCode(type, outputVarName, listVarName, loopVarName))
                .build();
    }

    private CodeBlock generateListSerCode(FieldType fieldType, String fieldName,
                                          String outputVarName, String loopVarName, String objectName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        CodeBlock.Builder builder = CodeBlock.builder()
                .addStatement("$T $L = $T.builder().build()", ListPlutusData.class, outputVarName, ListPlutusData.class)
                .beginControlFlow("for(var $L: $L)", loopVarName, objectName);

        String elemOutputVar = outputVarName + "_$elem";
        CodeBlock elemBlock = registry.dispatchNestedSerialization(genericType, loopVarName, elemOutputVar, loopVarName);
        builder.add(elemBlock)
               .addStatement("$L.add($L)", outputVarName, elemOutputVar);

        builder.endControlFlow();

        return builder.build();
    }

    private CodeBlock generateListDeserCode(FieldType fieldType, String fieldName,
                                            String pdListName, String itemVarName) {
        FieldType genericType = fieldType.getGenericTypes().get(0);
        CodeBlock.Builder builder = CodeBlock.builder()
                .addStatement("$L $L = new $T<>()", fieldType.getFqTypeName(), fieldName, ArrayList.class)
                .beginControlFlow("for(var $L: (($T)$L).getPlutusDataList())", itemVarName, ListPlutusData.class, pdListName);

        String elemOutputVar = fieldName + "_$elem";
        CodeBlock elemBlock = registry.dispatchNestedDeserialization(genericType, itemVarName, elemOutputVar, itemVarName);
        builder.add(elemBlock)
               .addStatement("$L.add($L)", fieldName, elemOutputVar);

        builder.endControlFlow();

        return builder.build();
    }

}
