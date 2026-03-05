package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.Objects;

/**
 * Builds the 4 convenience serialize/deserialize wrapper methods
 * that every generated converter class contains.
 */
public class SerDeMethodBuilder {

    public MethodSpec serialize(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(byte[].class)
                .addParameter(objTypeName, "obj");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(obj);", Objects.class)
                .beginControlFlow("try")
                .addStatement("var constr = toPlutusData(obj)")
                .addStatement("return $T.serialize(constr.serialize())", CborSerializationUtil.class)
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(e)", CborRuntimeException.class)
                .endControlFlow()
                .build();

        return methodBuilder.addCode(body).build();
    }

    public MethodSpec serializeToHex(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("serializeToHex")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(objTypeName, "obj");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(obj);", Objects.class)
                .addStatement("var constr = toPlutusData(obj)")
                .addStatement("return constr.serializeToHex()")
                .build();

        return methodBuilder.addCode(body).build();
    }

    public MethodSpec deserialize(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(byte[].class, "bytes");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(bytes);", Objects.class)
                .beginControlFlow("try")
                .addStatement("var di = $T.deserialize(bytes)", CborSerializationUtil.class)
                .addStatement("var constr = $T.deserialize(di)", ConstrPlutusData.class)
                .addStatement("return fromPlutusData(constr)")
                .nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(e)", CborRuntimeException.class)
                .endControlFlow()
                .build();

        return methodBuilder.addCode(body).build();
    }

    public MethodSpec deserializeFromHex(ClassDefinition classDefinition) {
        TypeName objTypeName = bestGuess(classDefinition.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("deserialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(String.class, "hex");

        var body = CodeBlock.builder()
                .addStatement("$T.requireNonNull(hex);", Objects.class)
                .addStatement("var bytes = $T.decodeHexString(hex)", HexUtil.class)
                .addStatement("return deserialize(bytes)")
                .build();

        return methodBuilder.addCode(body).build();
    }

    public static TypeName bestGuess(String name) {
        return switch (name) {
            case "int" -> ClassName.get(Integer.class);
            case "long" -> ClassName.get(Long.class);
            case "byte[]" -> ArrayTypeName.of(TypeName.BYTE);
            default -> ClassName.bestGuess(name);
        };
    }

}
