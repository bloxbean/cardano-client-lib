package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import java.util.Optional;

/**
 * Code generation for nested {@code @MetadataType} fields.
 * Delegates serialization/deserialization to the nested type's generated converter.
 */
public class NestedTypeCodeGen {

    private final MetadataFieldAccessor accessor;

    public NestedTypeCodeGen(MetadataFieldAccessor accessor) {
        this.accessor = accessor;
    }

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
        builder.addStatement("map.put($S, new $T().toMetadataMap($L))",
                field.getMetadataKey(), converterClass, getExpr);
    }

    public void emitSerializeToList(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
        builder.addStatement("_list.add(new $T().toMetadataMap(_el))", converterClass);
    }

    public void emitSerializeOptionalToMap(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
        builder.beginControlFlow("if ($L.isPresent())", getExpr);
        builder.addStatement("map.put($S, new $T().toMetadataMap($L.get()))",
                field.getMetadataKey(), converterClass, getExpr);
        builder.endControlFlow();
    }

    // --- Deserialization ---

    public void emitDeserializeScalar(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
        builder.beginControlFlow("if (v instanceof $T)", MetadataMap.class);
        accessor.emitSetFmt(builder, field, "new $T().fromMetadataMap(($T) v)", converterClass, MetadataMap.class);
        builder.endControlFlow();
    }

    public void emitDeserializeElement(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
        builder.beginControlFlow("if (_el instanceof $T)", MetadataMap.class);
        builder.addStatement("_result.add(new $T().fromMetadataMap(($T) _el))", converterClass, MetadataMap.class);
        builder.endControlFlow();
    }

    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
        builder.beginControlFlow("if (v instanceof $T)", MetadataMap.class);
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of(new $T().fromMetadataMap(($T) v)))",
                    field.getSetterName(), Optional.class, converterClass, MetadataMap.class);
        } else {
            builder.addStatement("obj.$L = $T.of(new $T().fromMetadataMap(($T) v))",
                    field.getJavaFieldName(), Optional.class, converterClass, MetadataMap.class);
        }
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }
}
