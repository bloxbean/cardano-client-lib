package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.squareup.javapoet.CodeBlock;

import java.util.Map;

public class IntegerFieldCodeGen implements FieldCodeGenerator {

    private static final Map<JavaType, String> FROM_PD_METHODS = Map.of(
            JavaType.INT, "plutusDataToInteger",
            JavaType.INTEGER, "plutusDataToInteger",
            JavaType.LONG, "plutusDataToLong",
            JavaType.LONG_OBJECT, "plutusDataToLong",
            JavaType.BIGINTEGER, "plutusDataToBigInteger"
    );

    private static final Map<JavaType, String> VALUE_METHODS = Map.of(
            JavaType.INT, "getValue().intValue()",
            JavaType.INTEGER, "getValue().intValue()",
            JavaType.LONG, "getValue().longValue()",
            JavaType.LONG_OBJECT, "getValue().longValue()"
    );

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
        String method = FROM_PD_METHODS.get(type.getJavaType());
        return method != null ? String.format("%s(%s)", method, pdExpression) : "";
    }

    static String getValueMethodNameForIntType(FieldType fieldType) {
        return VALUE_METHODS.getOrDefault(fieldType.getJavaType(), "getValue()");
    }
}
