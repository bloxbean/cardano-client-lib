package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

public class DataImplGenerator implements CodeGenerator {
    private ProcessingEnvironment processingEnvironment;

    public DataImplGenerator(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    @Override
    public TypeSpec generate(ClassDefinition classDef) {
        String dataImplClass = classDef.getImplClassName();

        ClassName converterClass = ClassName.bestGuess(classDef.getConverterPackageName() + "." + classDef.getConverterClassName());
        ClassName dataClass = ClassName.bestGuess(classDef.getPackageName() + "." + classDef.getDataClassName());

        //Data.class
        ClassName DataClazz = ClassName.get(Data.class);
        ParameterizedTypeName parameterizedInterface = ParameterizedTypeName.get(DataClazz, dataClass);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(dataImplClass)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(parameterizedInterface)
                .superclass(dataClass);

        FieldSpec converterField = FieldSpec.builder(converterClass, "converter")
                .addModifiers(Modifier.PRIVATE)
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("this.converter = new $T()", converterClass)
                .build();

        return classBuilder
                .addJavadoc(GENERATED_CODE)
                .addField(converterField)
                .addMethod(constructor)
                .addMethod(getToPlutusDataMethodSpec(dataClass))
                .addMethod(getFromPlutusDataMethodSpec(dataClass))
                .build();
    }

    private MethodSpec getToPlutusDataMethodSpec(ClassName dataClass) {
        MethodSpec toPlutusDataMethod = MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ClassName.get(ConstrPlutusData.class))
                .addStatement("return converter.toPlutusData(($T)this)", dataClass)
                .build();

        return toPlutusDataMethod;
    }

    private MethodSpec getFromPlutusDataMethodSpec(ClassName dataClass) {
        MethodSpec fromPlutusDataMethod = MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(dataClass)
                .addParameter(ClassName.get(ConstrPlutusData.class), "data")
                .addStatement("return converter.fromPlutusData(data)")
                .build();

        return fromPlutusDataMethod;
    }
}
