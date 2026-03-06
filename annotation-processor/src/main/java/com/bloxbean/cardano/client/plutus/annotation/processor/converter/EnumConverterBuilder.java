package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Builds converter TypeSpec for enum types annotated with @Constr.
 */
public class EnumConverterBuilder {

    private final SerDeMethodBuilder serDeMethodBuilder;

    public EnumConverterBuilder(SerDeMethodBuilder serDeMethodBuilder) {
        this.serDeMethodBuilder = serDeMethodBuilder;
    }

    public Optional<TypeSpec> build(ClassDefinition classDefinition) {
        if (!classDefinition.isEnum())
            return Optional.empty();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(classDefinition.getConverterClassName())
                .addModifiers(Modifier.PUBLIC)
                .superclass(BasePlutusDataConverter.class);

        ClassName enumClassName = ClassName.get(classDefinition.getPackageName(), classDefinition.getDataClassName());
        List<String> enumConstants = classDefinition.getEnumValues();
        String parameterName = JavaFileUtil.firstLowerCase(enumClassName.simpleName());

        MethodSpec.Builder toPlutusDataMethodBuilder = getEnumToPlutusData(enumClassName, parameterName, enumConstants);
        MethodSpec.Builder fromPlutusDataMethodBuilder = getEnumFromPlutusData(enumClassName, enumConstants);

        var typeSpec = classBuilder
                .addJavadoc(GENERATED_CODE)
                .addMethod(toPlutusDataMethodBuilder.build())
                .addMethod(fromPlutusDataMethodBuilder.build())
                .addMethod(serDeMethodBuilder.serialize(classDefinition))
                .addMethod(serDeMethodBuilder.serializeToHex(classDefinition))
                .addMethod(serDeMethodBuilder.deserialize(classDefinition))
                .addMethod(serDeMethodBuilder.deserializeFromHex(classDefinition))
                .build();

        return Optional.of(typeSpec);
    }

    static MethodSpec.Builder getEnumFromPlutusData(ClassName enumClassName, List<String> enumConstants) {
        MethodSpec.Builder fromPlutusDataMethodBuilder = MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(enumClassName)
                .addParameter(ConstrPlutusData.class, "constr");

        fromPlutusDataMethodBuilder.addStatement("var constrData = constr.getData()");

        for (int i = 0; i < enumConstants.size(); i++) {
            String enumConstant = enumConstants.get(i);
            fromPlutusDataMethodBuilder.beginControlFlow("if(constr.getAlternative() == $L)", i)
                    .addStatement("return $T.$L", enumClassName, enumConstant)
                    .endControlFlow();
        }

        fromPlutusDataMethodBuilder.addStatement("throw new $T($S + constr.getAlternative())", IllegalArgumentException.class, "Invalid alternative : ");
        return fromPlutusDataMethodBuilder;
    }

    static MethodSpec.Builder getEnumToPlutusData(ClassName enumClassName, String parameterName, List<String> enumConstants) {
        MethodSpec.Builder toPlutusDataMethodBuilder = MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(ConstrPlutusData.class)
                .addParameter(enumClassName, parameterName);

        for (int i = 0; i < enumConstants.size(); i++) {
            String enumConstant = enumConstants.get(i);
            toPlutusDataMethodBuilder.beginControlFlow("if($L == $T.$L)", parameterName, enumClassName, enumConstant)
                    .addStatement("return $T.builder().alternative($L).data($T.of()).build()", ConstrPlutusData.class, i, ListPlutusData.class);
            toPlutusDataMethodBuilder.endControlFlow();
        }

        toPlutusDataMethodBuilder.addStatement("throw new $T($S + $L)", IllegalArgumentException.class, "Invalid enum value : ", parameterName);
        return toPlutusDataMethodBuilder;
    }
}
