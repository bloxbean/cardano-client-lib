package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatum;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.squareup.javapoet.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

public class ValidatorProcessor {

    private final Blueprint annotation;
    private final ProcessingEnvironment processingEnv;
    private final FieldSpecProcessor fieldSpecProcessor;
    private final String VALIDATOR_CLASS_SUFFIX = "Validator";

    public ValidatorProcessor(Blueprint annotation, ProcessingEnvironment processingEnv) {
        this.annotation = annotation;
        this.processingEnv = processingEnv;
        this.fieldSpecProcessor = new FieldSpecProcessor(annotation, processingEnv);
    }

    /**
     * Validator Definition will be converted to a Java class.
     * All definitions within are fields and/or seperate classes
     * @param validator validator definition
     * @param plutusVersion plutus version
     */
    public void processValidator(Validator validator, PlutusVersion plutusVersion) {
        // preparation of standard fields
        String[] titleTokens = validator.getTitle().split("\\.");
        String pkgSuffix = null;

        if(titleTokens.length > 1) {
            pkgSuffix = titleTokens[0];
        }

        String validatorName = titleTokens[titleTokens.length - 1];

        String packageName = annotation.packageName();
        if (pkgSuffix != null)
            packageName = packageName + "." + pkgSuffix;

        String title = validatorName;
        title = JavaFileUtil.toCamelCase(title);

        List<FieldSpec> fields = ValidatorProcessor.getFieldSpecsForValidator(validator);

        // processing of fields
        if(validator.getRedeemer() != null)
            fields.add(fieldSpecProcessor.createDatumFieldSpec(validator.getRedeemer().getSchema(), "Redeemer", title));
        if(validator.getDatum() != null)
            fields.add(fieldSpecProcessor.createDatumFieldSpec(validator.getDatum().getSchema(), "Datum", title));
        if(validator.getParameters() != null) {
            for (BlueprintDatum parameter : validator.getParameters()) {
                fields.add(fieldSpecProcessor.createDatumFieldSpec(parameter.getSchema(), "Parameter", title + parameter.getTitle()));
            }
        }
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(getScriptAddressMethodSpec(plutusVersion));
        methods.add(getPlutusScriptMethodSpec(plutusVersion));

        String validatorClassName = title + VALIDATOR_CLASS_SUFFIX;
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
        try {
            JavaFileUtil.createJavaFile(packageName, build, validatorClassName, processingEnv);
        } catch (Exception e) {
            error(null, "Error creating validator class : %s", e.getMessage());
        }
    }

    private MethodSpec getScriptAddressMethodSpec(PlutusVersion plutusVersion) {
        MethodSpec getScriptAddress = MethodSpec.methodBuilder("getScriptAddress")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addParameter(Network.class, "network")
                .addException(CborDeserializationException.class)
                .addJavadoc("Returns the address of the validator script")
                .addStatement("var script = $T.getPlutusScriptFromCompiledCode(this.compiledCode, $T.$L)", PlutusBlueprintUtil.class, plutusVersion.getClass(), plutusVersion)
                .addStatement("return $T.getEntAddress(script, network).toBech32()", AddressProvider.class)
                .build();
        return getScriptAddress;
    }

    private MethodSpec getPlutusScriptMethodSpec(PlutusVersion plutusVersion) {
        MethodSpec getPlutusScript = MethodSpec.methodBuilder("getPlutusScript")
                .addModifiers(Modifier.PUBLIC)
                .returns(PlutusScript.class)
                .addStatement("return $T.getPlutusScriptFromCompiledCode(this.compiledCode, $T.$L)", PlutusBlueprintUtil.class, plutusVersion.getClass(), plutusVersion)
                .build();
        return getPlutusScript;
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

    private void error(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
