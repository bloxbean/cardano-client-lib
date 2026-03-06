package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Builds converter TypeSpec for interface types (anyOf / multi-constructor).
 */
public class InterfaceConverterBuilder {

    private final SerDeMethodBuilder serDeMethodBuilder;

    public InterfaceConverterBuilder(SerDeMethodBuilder serDeMethodBuilder) {
        this.serDeMethodBuilder = serDeMethodBuilder;
    }

    public TypeSpec build(ClassDefinition classDef, List<ClassDefinition> constructors) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(classDef.getConverterClassName())
                .addModifiers(Modifier.PUBLIC)
                .superclass(BasePlutusDataConverter.class);

        constructors.sort(Comparator.comparingInt(ClassDefinition::getAlternative));

        String interfaceName = classDef.getDataClassName();
        String paramName = JavaFileUtil.firstLowerCase(interfaceName);
        ClassName interfaceTypeName = ClassName.get(classDef.getPackageName(), interfaceName);

        MethodSpec.Builder toPlutusDataMethod = getInterfaceConverterToPlutusData(constructors, interfaceTypeName, paramName);
        MethodSpec.Builder fromPlutusDataMethod = getInterfaceConverterFromPlutusData(classDef, constructors);

        return classBuilder
                .addJavadoc(GENERATED_CODE)
                .addMethod(toPlutusDataMethod.build())
                .addMethod(fromPlutusDataMethod.build())
                .addMethod(serDeMethodBuilder.serialize(classDef))
                .addMethod(serDeMethodBuilder.serializeToHex(classDef))
                .addMethod(serDeMethodBuilder.deserialize(classDef))
                .addMethod(serDeMethodBuilder.deserializeFromHex(classDef))
                .build();
    }

    static MethodSpec.Builder getInterfaceConverterFromPlutusData(ClassDefinition classDef, List<ClassDefinition> constructors) {
        ClassName className = ClassName.get(classDef.getPackageName(), classDef.getDataClassName());
        MethodSpec.Builder fromPlutusDataMethod = MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(className)
                .addParameter(ConstrPlutusData.class, "constr");

        for (ClassDefinition constructor : constructors) {
            ClassName constrConverterTypeName = constructor.resolveConverterClassName();

            fromPlutusDataMethod.beginControlFlow("if(constr.getAlternative() == $L)", constructor.getAlternative())
                    .addStatement("return new $T().fromPlutusData(constr)", constrConverterTypeName)
                    .endControlFlow();
        }

        fromPlutusDataMethod.addStatement("throw new $T(\"Invalid alternative: \" + constr.getAlternative())", CborRuntimeException.class);

        return fromPlutusDataMethod;
    }

    static MethodSpec.Builder getInterfaceConverterToPlutusData(List<ClassDefinition> constructors,
                                                                ClassName interfaceTypeName,
                                                                String paramName) {
        MethodSpec.Builder toPlutusDataMethod = MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(ConstrPlutusData.class)
                .addParameter(interfaceTypeName, paramName);

        toPlutusDataMethod.addStatement("$T.requireNonNull($L, \"$L cannot be null\")", Objects.class, paramName, paramName);

        for (ClassDefinition constructor : constructors) {
            ClassName constrConverterTypeName = constructor.resolveConverterClassName();
            // Use objType for correct nested class resolution (e.g., Credential.VerificationKey)
            ClassName constrTypeName = ClassName.bestGuess(constructor.getObjType());

            toPlutusDataMethod.beginControlFlow("if($L instanceof $T)", paramName, constrTypeName)
                    .addStatement("return new $T().toPlutusData(($T)$L)", constrConverterTypeName, constrTypeName, paramName)
                    .endControlFlow();
        }
        toPlutusDataMethod.addCode("\n");
        toPlutusDataMethod.addStatement("throw new $T(\"Unsupported type: \" + $L.getClass())", CborRuntimeException.class, paramName);

        return toPlutusDataMethod;
    }

}
