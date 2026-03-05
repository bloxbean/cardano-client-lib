package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.squareup.javapoet.CodeBlock;

public class BoolFieldCodeGen implements FieldCodeGenerator {

    @Override
    public Type supportedType() { return Type.BOOL; }

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
                .add("$T $L = null;\n", Boolean.class, field.getName())
                .addStatement("var $LConstr = (($T)constrData.getPlutusDataList().get($L))",
                        field.getName(), ConstrPlutusData.class, field.getIndex())
                .beginControlFlow("if($LConstr.getAlternative() == 0)", field.getName())
                .addStatement("$L = false", field.getName())
                .nextControlFlow("else")
                .addStatement("$L = true", field.getName())
                .endControlFlow()
                .add("\n")
                .build();
    }

    @Override
    public String toPlutusDataExpression(FieldType type, String expression) {
        return "toPlutusData(" + expression + ")";
    }

    @Override
    public String fromPlutusDataExpression(FieldType type, String pdExpression) {
        return String.format("plutusDataToBoolean(%s)", pdExpression);
    }
}
