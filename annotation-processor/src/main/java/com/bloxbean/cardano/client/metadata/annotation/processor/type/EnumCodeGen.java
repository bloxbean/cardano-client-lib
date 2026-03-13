package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import java.util.Optional;

/**
 * Code generation for enum fields. Enums are stored as their {@code name()} String
 * and reconstructed via {@code EnumType.valueOf(String)}.
 */
public class EnumCodeGen {

    private final MetadataFieldAccessor accessor;

    public EnumCodeGen(MetadataFieldAccessor accessor) {
        this.accessor = accessor;
    }

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                   String getExpr) {
        builder.addStatement("map.put($S, $L.name())", field.getMetadataKey(), getExpr);
    }

    /** Serialize {@code Optional<EnumType>} to map. */
    public void emitSerializeOptionalToMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                           String getExpr) {
        builder.beginControlFlow("if ($L.isPresent())", getExpr);
        builder.addStatement("map.put($S, $L.get().name())", field.getMetadataKey(), getExpr);
        builder.endControlFlow();
    }

    /** Serialize enum element to list. */
    public void emitSerializeToList(MethodSpec.Builder builder) {
        builder.addStatement("_list.add(_el.name())");
    }

    // --- Deserialization ---

    public void emitDeserializeScalar(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName enumClass = ClassName.bestGuess(field.getJavaTypeName());
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        if (field.isRecordMode()) {
            builder.addStatement("$L = $T.valueOf((String) v)",
                    MetadataFieldAccessor.recordLocal(field), enumClass);
        } else if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.valueOf((String) v))", field.getSetterName(), enumClass);
        } else {
            builder.addStatement("obj.$L = $T.valueOf((String) v)", field.getJavaFieldName(), enumClass);
        }
        builder.endControlFlow();
    }

    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName enumClass = ClassName.bestGuess(field.getElementTypeName());
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        if (field.isRecordMode()) {
            builder.addStatement("$L = $T.of($T.valueOf(($T) v))",
                    MetadataFieldAccessor.recordLocal(field), Optional.class, enumClass, String.class);
        } else if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of($T.valueOf(($T) v)))",
                    field.getSetterName(), Optional.class, enumClass, String.class);
        } else {
            builder.addStatement("obj.$L = $T.of($T.valueOf(($T) v))",
                    field.getJavaFieldName(), Optional.class, enumClass, String.class);
        }
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    /** Deserialize enum element from list. */
    public void emitDeserializeElement(MethodSpec.Builder builder, MetadataFieldInfo field) {
        ClassName enumClass = ClassName.bestGuess(field.getElementTypeName());
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.addStatement("_result.add($T.valueOf(($T) _el))", enumClass, String.class);
        builder.endControlFlow();
    }
}
