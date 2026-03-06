package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.SerDeMethodBuilder;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

import static com.bloxbean.cardano.client.plutus.annotation.processor.ClassDefinitionGenerator.getConverterClassFromField;

public class ConstructorFieldCodeGen implements FieldCodeGenerator {

    @Override
    public Type supportedType() { return Type.CONSTRUCTOR; }

    @Override
    public CodeBlock generateSerialization(Field field, FieldAccessor accessor) {
        if (field.getFieldType().isSharedType()) {
            return CodeBlock.builder()
                    .add("//Field $L\n", field.getName())
                    .beginControlFlow("if(obj.$L != null)", accessor.fieldOrGetter(field))
                    .addStatement("constr.getData().add(obj.$L.toPlutusData())", accessor.fieldOrGetter(field))
                    .endControlFlow()
                    .build();
        } else {
            ClassName converterClass = getConverterClassFromField(field.getFieldType());
            return CodeBlock.builder()
                    .add("//Field $L\n", field.getName())
                    .beginControlFlow("if(obj.$L != null)", accessor.fieldOrGetter(field))
                    .addStatement("constr.getData().add(new $T().toPlutusData(obj.$L))", converterClass, accessor.fieldOrGetter(field))
                    .endControlFlow()
                    .build();
        }
    }

    @Override
    public CodeBlock generateDeserialization(Field field) {
        TypeName fieldTypeName = SerDeMethodBuilder.bestGuess(field.getFieldType().getJavaType().getName());
        if (field.getFieldType().isSharedType()) {
            if (field.getFieldType().isRawDataType()) {
                return CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("$T $L = $T.fromPlutusData(constrData.getPlutusDataList().get($L))",
                                fieldTypeName, field.getName(), fieldTypeName, field.getIndex())
                        .build();
            } else {
                return CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("$T $L = $T.fromPlutusData((($T)constrData.getPlutusDataList().get($L)))",
                                fieldTypeName, field.getName(), fieldTypeName, ConstrPlutusData.class, field.getIndex())
                        .build();
            }
        } else {
            ClassName converterClazz = getConverterClassFromField(field.getFieldType());
            if (field.getFieldType().isRawDataType()) {
                return CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("$T $L = new $T().fromPlutusData(constrData.getPlutusDataList().get($L))",
                                fieldTypeName, field.getName(), converterClazz, field.getIndex())
                        .build();
            } else {
                return CodeBlock.builder()
                        .add("//Field $L\n", field.getName())
                        .addStatement("$T $L = new $T().fromPlutusData((($T)constrData.getPlutusDataList().get($L)))",
                                fieldTypeName, field.getName(), converterClazz, ConstrPlutusData.class, field.getIndex())
                        .build();
            }
        }
    }

    @Override
    public String toPlutusDataExpression(FieldType type, String expression) {
        if (type.isSharedType()) {
            return expression + ".toPlutusData()";
        }
        ClassName converterClassName = getConverterClassFromField(type);
        String converterClazz = converterClassName.canonicalName();
        return String.format("new %s().toPlutusData(%s)", converterClazz, expression);
    }

    @Override
    public String fromPlutusDataExpression(FieldType type, String pdExpression) {
        if (type.isSharedType()) {
            String typeFqn = type.getJavaType().getName();
            if (type.isRawDataType()) {
                return String.format("%s.fromPlutusData(%s)", typeFqn, pdExpression);
            }
            return String.format("%s.fromPlutusData((ConstrPlutusData)%s)", typeFqn, pdExpression);
        }
        ClassName converterClassName = getConverterClassFromField(type);
        String converterClazz = converterClassName.canonicalName();
        if (type.isRawDataType()) {
            return String.format("new %s().fromPlutusData(%s)", converterClazz, pdExpression);
        }
        return String.format("new %s().fromPlutusData((ConstrPlutusData)%s)", converterClazz, pdExpression);
    }
}
