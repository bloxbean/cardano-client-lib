package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.squareup.javapoet.CodeBlock;

public class PlutusDataFieldCodeGen implements FieldCodeGenerator {

    @Override
    public Type supportedType() { return Type.PLUTUSDATA; }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add(accessor.nullCheck(field))
                .addStatement("constr.getData().add(obj.$L)", accessor.fieldOrGetter(field))
                .add("\n")
                .build();
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add("var $L = constrData.getPlutusDataList().get($L);\n", field.getName(), field.getIndex())
                .build();
    }

    @Override
    public String toPlutusDataExpression(FieldType type, String expression) {
        return null; // PlutusData is passthrough — handled by default case
    }

    @Override
    public String fromPlutusDataExpression(FieldType type, String pdExpression) {
        return pdExpression;
    }

}
