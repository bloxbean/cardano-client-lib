package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.squareup.javapoet.CodeBlock;

public class IntegerFieldCodeGen implements FieldCodeGenerator {

    @Override
    public Type supportedType() { return Type.INTEGER; }

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
        String getValueMethodName = getValueMethodNameForIntType(field.getFieldType());
        return CodeBlock.builder()
                .add("//Field $L\n", field.getName())
                .addStatement("var $L = (($T)constrData.getPlutusDataList().get($L)).$L",
                        field.getName(), BigIntPlutusData.class, field.getIndex(), getValueMethodName)
                .build();
    }

    @Override
    public String toPlutusDataExpression(FieldType type, String expression) {
        return "toPlutusData(" + expression + ")";
    }

    @Override
    public String fromPlutusDataExpression(FieldType type, String pdExpression) {
        if (type.getJavaType() == JavaType.INT || type.getJavaType() == JavaType.INTEGER)
            return String.format("plutusDataToInteger(%s)", pdExpression);
        else if (type.getJavaType() == JavaType.LONG || type.getJavaType() == JavaType.LONG_OBJECT)
            return String.format("plutusDataToLong(%s)", pdExpression);
        else if (type.getJavaType() == JavaType.BIGINTEGER)
            return String.format("plutusDataToBigInteger(%s)", pdExpression);
        return "";
    }

    static String getValueMethodNameForIntType(FieldType fieldType) {
        if (fieldType.getJavaType() == JavaType.INT || fieldType.getJavaType() == JavaType.INTEGER)
            return "getValue().intValue()";
        else if (fieldType.getJavaType() == JavaType.LONG || fieldType.getJavaType() == JavaType.LONG_OBJECT)
            return "getValue().longValue()";
        return "getValue()";
    }
}
