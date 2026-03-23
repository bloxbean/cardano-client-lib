package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * Code generation for nested {@code @MetadataType} fields.
 * Delegates serialization/deserialization to the nested type's generated converter.
 */
@RequiredArgsConstructor
public class NestedTypeCodeGen {

    private static final String IF_V_INSTANCEOF = "if (v instanceof $T)";

    private final MetadataFieldAccessor accessor;

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
        builder.beginControlFlow(IF_V_INSTANCEOF, MetadataMap.class);
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
        builder.beginControlFlow(IF_V_INSTANCEOF, MetadataMap.class);
        accessor.emitOptionalOfSetFmt(builder, field, "new $T().fromMetadataMap(($T) v)",
                converterClass, MetadataMap.class);
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    // --- Polymorphic serialization ---

    public void emitSerializePolymorphic(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        List<MetadataFieldInfo.PolymorphicSubtypeInfo> subtypes = field.getSubtypes();
        for (int i = 0; i < subtypes.size(); i++) {
            MetadataFieldInfo.PolymorphicSubtypeInfo sub = subtypes.get(i);
            ClassName subtypeClass = ClassName.bestGuess(sub.javaTypeFqn());
            ClassName converterClass = ClassName.bestGuess(sub.converterFqn());

            if (i == 0) {
                builder.beginControlFlow("if ($L instanceof $T)", getExpr, subtypeClass);
            } else {
                builder.nextControlFlow("else if ($L instanceof $T)", getExpr, subtypeClass);
            }

            builder.addStatement("$T _polyMap = new $T().toMetadataMap(($T) $L)",
                    MetadataMap.class, converterClass, subtypeClass, getExpr);
            builder.addStatement("_polyMap.put($S, $S)", field.getDiscriminatorKey(), sub.discriminatorValue());
            builder.addStatement("map.put($S, _polyMap)", field.getMetadataKey());
        }
        if (!subtypes.isEmpty()) {
            builder.endControlFlow();
        }
    }

    // --- Polymorphic deserialization ---

    public void emitDeserializePolymorphic(MethodSpec.Builder builder, MetadataFieldInfo field) {
        List<MetadataFieldInfo.PolymorphicSubtypeInfo> subtypes = field.getSubtypes();

        builder.beginControlFlow(IF_V_INSTANCEOF, MetadataMap.class);
        builder.addStatement("$T _polyMap = ($T) v", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _disc = _polyMap.get($S)", Object.class, field.getDiscriminatorKey());

        for (int i = 0; i < subtypes.size(); i++) {
            MetadataFieldInfo.PolymorphicSubtypeInfo sub = subtypes.get(i);
            ClassName converterClass = ClassName.bestGuess(sub.converterFqn());

            if (i == 0) {
                builder.beginControlFlow("if ($S.equals(_disc))", sub.discriminatorValue());
            } else {
                builder.nextControlFlow("else if ($S.equals(_disc))", sub.discriminatorValue());
            }

            accessor.emitSetFmt(builder, field, "new $T().fromMetadataMap(_polyMap)", converterClass);
        }
        if (!subtypes.isEmpty()) {
            builder.endControlFlow();
        }
        builder.endControlFlow();
    }
}
