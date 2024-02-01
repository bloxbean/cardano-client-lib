package com.bloxbean.cardano.client.plutus.annotation.blueprint_processor;

import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatum;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.util.HexUtil;
import com.squareup.javapoet.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ValidatorProcessor {

    private final Blueprint annotation;
    private final ProcessingEnvironment processingEnv;
    private final FieldSpecProcessor fieldSpecProcessor;

    public ValidatorProcessor(Blueprint annotation, ProcessingEnvironment processingEnv) {
        this.annotation = annotation;
        this.processingEnv = processingEnv;
        this.fieldSpecProcessor = new FieldSpecProcessor(annotation, processingEnv);
    }

    /**
     * Validator Definition will be converted to a Java class.
     * All definitions within are fields and/or seperate classes
     * @param validator
     */
    public void processValidator(Validator validator) {
        // preparation of standard fields
        String packageName = annotation.packageName() + "." + validator.getTitle().split("\\.")[0];
        String title = validator.getTitle().split("\\.")[1];
        title = JavaFileUtil.firstUpperCase(title);
        String validatorClassName = title + "Validator"; // ToDO need to check for valid names
        List<FieldSpec> fields = ValidatorProcessor.getFieldSpecsForValidator(validator);

        // processing of fields
        if(validator.getRedeemer() != null)
            fields.add(fieldSpecProcessor.createDatumFieldSpec(validator.getRedeemer().getSchema(), "Redeemer", title, ""));
        if(validator.getDatum() != null)
            fields.add(fieldSpecProcessor.createDatumFieldSpec(validator.getDatum().getSchema(), "Datum", title, ""));
        if(validator.getParameters() != null) {
            for (BlueprintDatum parameter : validator.getParameters()) {
                fields.add(fieldSpecProcessor.createDatumFieldSpec(parameter.getSchema(), "Parameter", title + parameter.getTitle(), ""));
            }
        }
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(getScriptAddressMethodSpec());

        // building and saving of class
        TypeSpec build = TypeSpec.classBuilder(validatorClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Auto generated code. DO NOT MODIFY")
                .addAnnotation(Data.class)
                .addAnnotation(AllArgsConstructor.class)
                .addAnnotation(NoArgsConstructor.class)
                .addFields(fields)
                .addMethods(methods)
                .build();
        JavaFileUtil.createJavaFile(packageName, build, validatorClassName, processingEnv);
    }

    private MethodSpec getScriptAddressMethodSpec() {
        MethodSpec getScriptAddress = MethodSpec.methodBuilder("getScriptAddress")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(Network.class, "network")
                .addException(CborDeserializationException.class)
                .addStatement("$T compiledCodeAsByteString = new $T($T.decodeHexString(this.compiledCode))", ByteString.class, ByteString.class, HexUtil.class)
                .addStatement("$T script = $T.deserialize(compiledCodeAsByteString)", PlutusV2Script.class, PlutusV2Script.class)
                .addStatement("return $T.getEntAddress(script, network).toBech32()", AddressProvider.class)
                .build();
        return getScriptAddress;
    }


    public static List<FieldSpec> getFieldSpecsForValidator(Validator validator) {
        List<FieldSpec> fields = new ArrayList<>();
        fields.add(FieldSpec.builder(String.class, "title")
                .addModifiers(Modifier.PRIVATE) // need to fix AnnotationProcessor for final variables
                .initializer("$S", validator.getTitle())
                .build());
        fields.add(FieldSpec.builder(String.class, "description")
                .addModifiers(Modifier.PRIVATE)
                .initializer("$S", validator.getDescription())
                .build());
        fields.add(FieldSpec.builder(String.class, "compiledCode")
                .addModifiers(Modifier.PRIVATE)
                .initializer("$S", validator.getCompiledCode())
                .build());
        fields.add(FieldSpec.builder(String.class, "hash")
                .addModifiers(Modifier.PRIVATE)
                .initializer("$S", validator.getHash())
                .build());
        return fields;
    }
}
