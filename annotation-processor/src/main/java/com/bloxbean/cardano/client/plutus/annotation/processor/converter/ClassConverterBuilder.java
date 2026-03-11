package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.BasePlutusDataConverter;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.BlueprintGenerationException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Builds the converter TypeSpec for @Constr-annotated classes.
 * Generates toPlutusData + fromPlutusData + 4 serialize/deserialize wrapper methods.
 */
public class ClassConverterBuilder {

    private final FieldCodeGeneratorRegistry registry;
    private final SerDeMethodBuilder serDeBuilder;
    private final FieldAccessor accessor;

    public ClassConverterBuilder(FieldCodeGeneratorRegistry registry,
                                 SerDeMethodBuilder serDeBuilder) {
        this.registry = registry;
        this.serDeBuilder = serDeBuilder;
        this.accessor = new FieldAccessor();
    }

    public TypeSpec build(ClassDefinition classDef) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(classDef.getConverterClassName())
                .addModifiers(Modifier.PUBLIC)
                .superclass(BasePlutusDataConverter.class);

        MethodSpec toPlutusDataMethod = generateToPlutusDataMethod(classDef);
        MethodSpec fromPlutusDataMethod = generateFromPlutusDataMethod(classDef);

        return classBuilder
                .addJavadoc(GENERATED_CODE)
                .addMethod(toPlutusDataMethod)
                .addMethod(fromPlutusDataMethod)
                .addMethod(serDeBuilder.serialize(classDef))
                .addMethod(serDeBuilder.serializeToHex(classDef))
                .addMethod(serDeBuilder.deserialize(classDef))
                .addMethod(serDeBuilder.deserializeFromHex(classDef))
                .build();
    }

    private MethodSpec generateToPlutusDataMethod(ClassDefinition classDef) {
        TypeName objTypeName = SerDeMethodBuilder.bestGuess(classDef.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("toPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(ConstrPlutusData.class)
                .addParameter(objTypeName, "obj");

        CodeBlock.Builder body = CodeBlock.builder()
                .addStatement("$T constr = initConstr($L)", ConstrPlutusData.class, classDef.getAlternative());

        for (Field field : classDef.getFields()) {
            FieldCodeGenerator gen = registry.get(field.getFieldType().getType());
            if (gen == null) {
                throw new BlueprintGenerationException("Unsupported type : " + field.getFieldType().getType());
            }
            CodeBlock codeBlock = gen.generateSerialization(field, accessor);
            if (codeBlock != null) {
                body.add(codeBlock);
            }
        }

        body.addStatement("return constr");
        return methodBuilder.addCode(body.build()).build();
    }

    private MethodSpec generateFromPlutusDataMethod(ClassDefinition classDef) {
        TypeName objTypeName = SerDeMethodBuilder.bestGuess(classDef.getObjType());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("fromPlutusData")
                .addModifiers(Modifier.PUBLIC)
                .returns(objTypeName)
                .addParameter(ConstrPlutusData.class, "constr");

        TypeName instantiationType = objTypeName;
        if (classDef.isAbstract()) {
            instantiationType = ClassName.get(classDef.getImplPackageName(), classDef.getImplClassName());
        }

        CodeBlock initObjCodeBlock = CodeBlock.builder()
                .addStatement("var obj = new $T()", instantiationType)
                .addStatement("var constrData = constr.getData()")
                .build();

        CodeBlock.Builder bodyCodeBlock = CodeBlock.builder();
        for (Field field : classDef.getFields()) {
            FieldCodeGenerator gen = registry.get(field.getFieldType().getType());
            if (gen == null) {
                throw new BlueprintGenerationException("Unsupported type : " + field.getFieldType().getType());
            }
            CodeBlock codeBlock = gen.generateDeserialization(field);
            if (codeBlock != null) {
                bodyCodeBlock.add("\n");
                bodyCodeBlock.add(codeBlock);
                CodeBlock.Builder assignmentBlock = CodeBlock.builder();
                if (field.isHashGetter()) {
                    assignmentBlock.addStatement("obj.$L($L)", accessor.setter(field.getName()), field.getName());
                } else {
                    assignmentBlock.addStatement("obj.$L = $L", field.getName(), field.getName());
                }
                bodyCodeBlock.add(assignmentBlock.build());
            }
        }

        CodeBlock returnObjCodeBlock = CodeBlock.builder()
                .addStatement("return obj")
                .build();

        return methodBuilder
                .addCode(initObjCodeBlock)
                .addCode(bodyCodeBlock.build())
                .addCode(returnObjCodeBlock)
                .build();
    }

}
