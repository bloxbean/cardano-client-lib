package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.squareup.javapoet.CodeBlock;

public class BytesFieldCodeGen implements FieldCodeGenerator {

    @Override
    public Type supportedType() { return Type.BYTES; }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .add(accessor.nullCheck(field))
                .addStatement("constr.getData().add(toPlutusData(obj.$L))", accessor.fieldOrGetter(field))
                .add("\n")
                .build();
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .addStatement("var $L = (($T)constrData.getPlutusDataList().get($L)).getValue()",
                        field.getName(), BytesPlutusData.class, field.getIndex())
                .build();
    }

    @Override
    public String toPlutusDataExpression(FieldType type, String expression) {
        return "toPlutusData(" + expression + ")";
    }

    @Override
    public String fromPlutusDataExpression(FieldType type, String pdExpression) {
        return String.format("plutusDataToBytes(%s)", pdExpression);
    }
}
