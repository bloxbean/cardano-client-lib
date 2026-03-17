package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGenRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Code generation for {@code Optional<T>} fields.
 * Serialization: delegates inner value to the appropriate scalar strategy.
 * Deserialization: emits present/absent branches.
 */
@RequiredArgsConstructor
public class OptionalCodeGen {

    private final MetadataTypeCodeGenRegistry registry;
    private final MetadataFieldAccessor accessor;
    private final EnumCodeGen enumCodeGen;
    @Setter
    private NestedTypeCodeGen nestedCodeGen;

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                   String getExpr) {
        builder.beginControlFlow("if ($L.isPresent())", getExpr);
        if (field.isElementNestedType()) {
            ClassName converterClass = ClassName.bestGuess(field.getNestedConverterFqn());
            builder.addStatement("map.put($S, new $T().toMetadataMap($L.get()))",
                    field.getMetadataKey(), converterClass, getExpr);
        } else if (field.isElementEnumType()) {
            builder.addStatement("map.put($S, $L.get().name())", field.getMetadataKey(), getExpr);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getElementTypeName());
            codeGen.emitSerializeToMapDefault(builder, field.getMetadataKey(),
                    getExpr + ".get()", field.getElementTypeName());
        }
        builder.endControlFlow();
    }

    // --- Deserialization ---

    public void emitDeserializeFromMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        if (field.isElementNestedType()) {
            nestedCodeGen.emitDeserializeOptional(builder, field);
        } else if (field.isElementEnumType()) {
            enumCodeGen.emitDeserializeOptional(builder, field);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getElementTypeName());
            codeGen.emitDeserializeOptional(builder, field, accessor);
        }
    }
}
