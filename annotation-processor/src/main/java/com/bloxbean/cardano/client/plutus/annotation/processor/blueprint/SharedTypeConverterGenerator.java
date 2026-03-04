package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.Objects;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Generates thin converter wrappers for shared/registered types.
 * These converters delegate to the shared type's own {@code toPlutusData()} and
 * {@code fromPlutusData()} static methods.
 */
public class SharedTypeConverterGenerator {

    /**
     * Determines the kind of shared type from its schema to generate the right converter shape.
     */
    public enum SharedTypeKind {
        /** Constructor-based type (e.g., Address, Credential) — uses ConstrPlutusData */
        CONSTRUCTOR,
        /** Bytes-wrapper type (e.g., VerificationKeyHash) — uses BytesPlutusData/PlutusData */
        BYTES
    }

    /**
     * Generates a converter TypeSpec for a shared type.
     *
     * @param sharedType the ClassName of the shared type
     * @param kind       the kind of shared type (determines serialization shape)
     * @return a TypeSpec for the converter class
     */
    public TypeSpec generate(ClassName sharedType, SharedTypeKind kind) {
        String converterName = sharedType.simpleName() + "Converter";

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(converterName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(BasePlutusDataConverter.class)
                .addJavadoc(GENERATED_CODE);

        return switch (kind) {
            case CONSTRUCTOR -> classBuilder
                    .addMethod(toPlutusDataConstr(sharedType))
                    .addMethod(fromPlutusDataConstr(sharedType))
                    .addMethod(serializeConstr(sharedType))
                    .addMethod(serializeToHexConstr(sharedType))
                    .addMethod(deserializeConstr(sharedType))
                    .addMethod(deserializeFromHexConstr(sharedType))
                    .build();
            case BYTES -> classBuilder
                    .addMethod(toPlutusDataBytes(sharedType))
                    .addMethod(fromPlutusDataBytes(sharedType))
                    .addMethod(serializeBytes(sharedType))
                    .addMethod(serializeToHexBytes(sharedType))
                    .addMethod(deserializeBytes(sharedType))
                    .addMethod(deserializeFromHexBytes(sharedType))
                    .build();
        };
    }

    /**
     * Determines the SharedTypeKind from a BlueprintSchema.
     */
    public static SharedTypeKind kindOf(BlueprintSchema schema) {
        BlueprintSchema resolved = schema.getRefSchema() != null ? schema.getRefSchema() : schema;

        if (resolved.getDataType() == BlueprintDatatype.bytes) {
            return SharedTypeKind.BYTES;
        }
        // Constructor-based types have anyOf with constructor variants, or dataType == constructor
        return SharedTypeKind.CONSTRUCTOR;
    }

    // ---- Constructor-based type methods (Address, Credential, ReferencedCredential) ----

    private MethodSpec toPlutusDataConstr(ClassName type) {
        return MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(ConstrPlutusData.class)
                .addParameter(type, "obj")
                .addStatement("$T.requireNonNull(obj, \"obj cannot be null\")", Objects.class)
                .addStatement("return obj.toPlutusData()")
                .build();
    }

    private MethodSpec fromPlutusDataConstr(ClassName type) {
        return MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addParameter(ConstrPlutusData.class, "constr")
                .addStatement("return $T.fromPlutusData(constr)", type)
                .build();
    }

    private MethodSpec serializeConstr(ClassName type) {
        return MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addParameter(type, "obj")
                .addCode(CodeBlock.builder()
                        .addStatement("$T.requireNonNull(obj)", Objects.class)
                        .beginControlFlow("try")
                        .addStatement("var constr = toPlutusData(obj)")
                        .addStatement("return $T.serialize(constr.serialize())", CborSerializationUtil.class)
                        .nextControlFlow("catch ($T e)", Exception.class)
                        .addStatement("throw new $T(e)", CborRuntimeException.class)
                        .endControlFlow()
                        .build())
                .build();
    }

    private MethodSpec serializeToHexConstr(ClassName type) {
        return MethodSpec.methodBuilder("serializeToHex")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(type, "obj")
                .addStatement("$T.requireNonNull(obj)", Objects.class)
                .addStatement("var constr = toPlutusData(obj)")
                .addStatement("return constr.serializeToHex()")
                .build();
    }

    private MethodSpec deserializeConstr(ClassName type) {
        return MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addParameter(byte[].class, "bytes")
                .addCode(CodeBlock.builder()
                        .addStatement("$T.requireNonNull(bytes)", Objects.class)
                        .beginControlFlow("try")
                        .addStatement("var di = $T.deserialize(bytes)", CborSerializationUtil.class)
                        .addStatement("var constr = $T.deserialize(di)", ConstrPlutusData.class)
                        .addStatement("return fromPlutusData(constr)")
                        .nextControlFlow("catch ($T e)", Exception.class)
                        .addStatement("throw new $T(e)", CborRuntimeException.class)
                        .endControlFlow()
                        .build())
                .build();
    }

    private MethodSpec deserializeFromHexConstr(ClassName type) {
        return MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addParameter(String.class, "hex")
                .addStatement("$T.requireNonNull(hex)", Objects.class)
                .addStatement("var bytes = $T.decodeHexString(hex)", HexUtil.class)
                .addStatement("return deserialize(bytes)")
                .build();
    }

    // ---- Bytes-wrapper type methods (VerificationKeyHash, ScriptHash, etc.) ----

    private MethodSpec toPlutusDataBytes(ClassName type) {
        return MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(PlutusData.class)
                .addParameter(type, "obj")
                .addStatement("$T.requireNonNull(obj, \"obj cannot be null\")", Objects.class)
                .addStatement("return obj.toPlutusData()")
                .build();
    }

    private MethodSpec fromPlutusDataBytes(ClassName type) {
        return MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addParameter(PlutusData.class, "data")
                .addStatement("return $T.fromPlutusData(data)", type)
                .build();
    }

    private MethodSpec serializeBytes(ClassName type) {
        return MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addParameter(type, "obj")
                .addCode(CodeBlock.builder()
                        .addStatement("$T.requireNonNull(obj)", Objects.class)
                        .beginControlFlow("try")
                        .addStatement("var plutusData = toPlutusData(obj)")
                        .addStatement("return $T.serialize(plutusData.serialize())", CborSerializationUtil.class)
                        .nextControlFlow("catch ($T e)", Exception.class)
                        .addStatement("throw new $T(e)", CborRuntimeException.class)
                        .endControlFlow()
                        .build())
                .build();
    }

    private MethodSpec serializeToHexBytes(ClassName type) {
        return MethodSpec.methodBuilder("serializeToHex")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(type, "obj")
                .addStatement("$T.requireNonNull(obj)", Objects.class)
                .addStatement("var plutusData = toPlutusData(obj)")
                .addStatement("return plutusData.serializeToHex()")
                .build();
    }

    private MethodSpec deserializeBytes(ClassName type) {
        return MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addParameter(byte[].class, "bytes")
                .addCode(CodeBlock.builder()
                        .addStatement("$T.requireNonNull(bytes)", Objects.class)
                        .beginControlFlow("try")
                        .addStatement("var di = $T.deserialize(bytes)", CborSerializationUtil.class)
                        .addStatement("var data = $T.deserialize(di)", PlutusData.class)
                        .addStatement("return fromPlutusData(data)")
                        .nextControlFlow("catch ($T e)", Exception.class)
                        .addStatement("throw new $T(e)", CborRuntimeException.class)
                        .endControlFlow()
                        .build())
                .build();
    }

    private MethodSpec deserializeFromHexBytes(ClassName type) {
        return MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addParameter(String.class, "hex")
                .addStatement("$T.requireNonNull(hex)", Objects.class)
                .addStatement("var bytes = $T.decodeHexString(hex)", HexUtil.class)
                .addStatement("return deserialize(bytes)")
                .build();
    }

}
