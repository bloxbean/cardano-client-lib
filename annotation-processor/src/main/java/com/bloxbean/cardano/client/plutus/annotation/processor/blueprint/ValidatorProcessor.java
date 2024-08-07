package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatum;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.AbstractValidatorExtender;
import com.bloxbean.cardano.client.util.HexUtil;
import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.CodeGenUtil.createMethodSpecsForGetterSetters;
import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

public class ValidatorProcessor {

    private final Blueprint annotation;
    private final ExtendWith extendWith;
    private final ProcessingEnvironment processingEnv;
    private final FieldSpecProcessor fieldSpecProcessor;
    private final String VALIDATOR_CLASS_SUFFIX = "Validator";

    public ValidatorProcessor(Blueprint annotation, ExtendWith extendWith, ProcessingEnvironment processingEnv) {
        this.annotation = annotation;
        this.extendWith = extendWith;
        this.processingEnv = processingEnv;
        this.fieldSpecProcessor = new FieldSpecProcessor(annotation, processingEnv);
    }

    /**
     * Validator Definition will be converted to a Java class.
     * All definitions within are fields and/or seperate classes
     *
     * @param validator     validator definition
     * @param plutusVersion plutus version
     */
    public void processValidator(Validator validator, PlutusVersion plutusVersion) {
        // preparation of standard fields
        String[] titleTokens = validator.getTitle().split("\\.");
        String pkgSuffix = null;

        if (titleTokens.length > 1) {
            pkgSuffix = titleTokens[0];
        }

        String validatorName = titleTokens[titleTokens.length - 1];

        String packageName = annotation.packageName();
        if (pkgSuffix != null)
            packageName = packageName + "." + pkgSuffix;

        String title = validatorName;
        title = JavaFileUtil.toCamelCase(title);

        List<FieldSpec> metaFields = ValidatorProcessor.getFieldSpecsForValidator(validator);

        FieldSpec networkField = FieldSpec.builder(Network.class, "network")
                .addModifiers(Modifier.PRIVATE)
                .build();

        FieldSpec scriptAddrField = FieldSpec.builder(String.class, "scriptAddress")
                .addModifiers(Modifier.PRIVATE)
                .build();

        FieldSpec plutusScriptField = FieldSpec.builder(PlutusScript.class, "plutusScript")
                .addModifiers(Modifier.PRIVATE)
                .build();

        boolean isParameterizedValidator = isParameterizedValidator(validator);

        FieldSpec applyParamCompiledCodeField = null;
        FieldSpec applyParamHashField = null;
        MethodSpec applyParamCompiledCodeGetter = null;
        MethodSpec applyParamHashGetter = null;
        if(isParameterizedValidator) {
            applyParamCompiledCodeField = FieldSpec.builder(String.class, "applyParamCompiledCode")
                    .addModifiers(Modifier.PRIVATE)
                    .build();
            applyParamHashField = FieldSpec.builder(String.class, "applyParamHash")
                    .addModifiers(Modifier.PRIVATE)
                    .build();

            applyParamCompiledCodeGetter = getApplyParamCompiledCodeGetterMethodSpec();
            applyParamHashGetter = getApplyParamHashGetterMethodSpec();

        }

        List<FieldSpec> fields = new ArrayList<>();

        //TODO -- Handle parameterized validators

        // processing of fields
        if (validator.getRedeemer() != null && validator.getRedeemer().getSchema() != null && validator.getRedeemer().getSchema().getRef() == null) { //Looks like inline schema
            var redeemerSchema = validator.getRedeemer().getSchema();
            if (redeemerSchema.getTitle() == null)
                redeemerSchema.setTitle(validator.getRedeemer().getTitle());

            fieldSpecProcessor.createDatumClass(pkgSuffix, redeemerSchema);
        }

        if (validator.getDatum() != null && validator.getDatum().getSchema() != null && validator.getDatum().getSchema().getRef() == null) { //Looks like inline schema
            var datumSchema = validator.getDatum().getSchema();
            if (datumSchema.getTitle() == null)
                datumSchema.setTitle(validator.getDatum().getTitle());

            fieldSpecProcessor.createDatumClass(pkgSuffix, datumSchema);
        }

        if (validator.getParameters() != null && validator.getParameters().size() > 0) { //check for any inline schema
            for (BlueprintDatum parameter : validator.getParameters()) {
                if (parameter.getSchema() != null && parameter.getSchema().getRef() == null) { //Looks like inline schema
                    var parameterSchema = parameter.getSchema();
                    if (parameterSchema.getTitle() == null)
                        parameterSchema.setTitle(parameter.getTitle());

                    fieldSpecProcessor.createDatumClass(pkgSuffix, parameterSchema);
                }
            }
        }

        List<MethodSpec> methods = new ArrayList<>();
        methods.addAll(createMethodSpecsForGetterSetters(fields, false));
        methods.addAll(createMethodSpecsForGetterSetters(List.of(networkField), false));
        methods.add(getScriptAddressMethodSpec(plutusVersion));
        methods.add(getPlutusScriptMethodSpec(plutusVersion, isParameterizedValidator));

        if (isParameterizedValidator) {
            methods.add(applyParamCompiledCodeGetter);
            methods.add(applyParamHashGetter);
        }

        String validatorClassName = title + VALIDATOR_CLASS_SUFFIX;
        // building and saving of class
        var builder = TypeSpec.classBuilder(validatorClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(GENERATED_CODE)
                .addMethod(getConstructorMethodSpec(isParameterizedValidator))
                .addFields(metaFields)
                .addField(networkField)
                .addField(scriptAddrField)
                .addFields(fields)
                .addField(plutusScriptField);

        if(isParameterizedValidator) {
            builder.addField(applyParamCompiledCodeField);
            builder.addField(applyParamHashField);
        }

        builder.addMethods(methods);

        if (extendWith != null) {
            var extendWithTypeMirros = getExtendWithValues(extendWith);
            ClassName validatorTypeName = ClassName.get(packageName, validatorClassName);

            for (TypeMirror extendWithTypeMirror : extendWithTypeMirros) {
                TypeElement extendWithTypeElement = (TypeElement) ((DeclaredType) extendWithTypeMirror).asElement();
                ClassName extendWithInterface = ClassName.get(extendWithTypeElement);
                ParameterizedTypeName parameterizedInterface = ParameterizedTypeName.get(extendWithInterface, validatorTypeName);
                builder.addSuperinterface(parameterizedInterface);
            }

            //Extend AbstractValidatorExtender
            ClassName abstractExtenderClass = ClassName.get(AbstractValidatorExtender.class);
            ParameterizedTypeName parameterizedSuperClass = ParameterizedTypeName.get(abstractExtenderClass, validatorTypeName);
            builder.superclass(parameterizedSuperClass);

        }
        var build = builder.build();

        try {
            JavaFileUtil.createJavaFile(packageName, build, validatorClassName, processingEnv);
        } catch (Exception e) {
            error(null, "Error creating validator class : %s", e.getMessage());
        }
    }

    //Create constructor with Network parameter
    private MethodSpec getConstructorMethodSpec(boolean isParameterizedValidator) {
        if (isParameterizedValidator) {
            MethodSpec constructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Network.class, "network")
                    .addParameter(String.class, "applyParamCompiledCode")
                    .addStatement("this.network = network")
                    .addStatement("this.applyParamCompiledCode = applyParamCompiledCode")
                    .build();
            return constructor;
        }  else {
            MethodSpec constructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Network.class, "network")
                    .addStatement("this.network = network")
                    .build();
            return constructor;
        }
    }

    private MethodSpec getScriptAddressMethodSpec(PlutusVersion plutusVersion) {
        MethodSpec getScriptAddress = MethodSpec.methodBuilder("getScriptAddress")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addJavadoc("Returns the address of the validator script")
                .beginControlFlow("if(scriptAddress == null)")
                .addStatement("var script = getPlutusScript()")
                .addStatement("scriptAddress = $T.getEntAddress(script, network).toBech32()", AddressProvider.class)
                .endControlFlow()
                .addStatement("return scriptAddress")
                .build();
        return getScriptAddress;
    }

    private MethodSpec getPlutusScriptMethodSpec(PlutusVersion plutusVersion, boolean isParameterizedValidator) {
        var builder = MethodSpec.methodBuilder("getPlutusScript")
                .addModifiers(Modifier.PUBLIC)
                .returns(PlutusScript.class);

        if (isParameterizedValidator) {
            //Use beginControl flow to check if plutusScript is null
            builder.beginControlFlow("if (plutusScript == null)");
            builder.addStatement("plutusScript = $T.getPlutusScriptFromCompiledCode(this.applyParamCompiledCode, $T.$L)", PlutusBlueprintUtil.class, plutusVersion.getClass(), plutusVersion);
            builder.endControlFlow();

            builder.addStatement("return plutusScript");
        } else {
            builder.beginControlFlow("if (plutusScript == null)");
            builder.addStatement("plutusScript = $T.getPlutusScriptFromCompiledCode(COMPILED_CODE, $T.$L)", PlutusBlueprintUtil.class, plutusVersion.getClass(), plutusVersion);
            builder.endControlFlow();
            builder.addStatement("return plutusScript");
        }

        return builder.build();
    }

    private MethodSpec getApplyParamCompiledCodeGetterMethodSpec() {
        MethodSpec getApplyParamCompiledCode = MethodSpec.methodBuilder("getApplyParamCompiledCode")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return applyParamCompiledCode")
                .build();
        return getApplyParamCompiledCode;
    }

    private MethodSpec getApplyParamHashGetterMethodSpec() {
        MethodSpec getApplyParamHash = MethodSpec.methodBuilder("getApplyParamHash")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Returns the hash of the script after applying the parameters\n")
                .addJavadoc("@throws CborRuntimeException if there is an error in getting the hash")
                .returns(String.class)
                .beginControlFlow("if (applyParamHash == null)")

                .beginControlFlow("try")
                    .addStatement("applyParamHash = $T.encodeHexString(getPlutusScript().getScriptHash())", HexUtil.class)
                    .nextControlFlow("catch ($T e)", CborSerializationException.class)
                        .addStatement("throw new $T(\"Error getting hash from compiled code\", e)", CborRuntimeException.class)
                    .endControlFlow()
                .endControlFlow()
                .addStatement("return applyParamHash")
                .build();
        return getApplyParamHash;
    }

    public static List<FieldSpec> getFieldSpecsForValidator(Validator validator) {
        List<FieldSpec> fields = new ArrayList<>();
        fields.add(FieldSpec.builder(String.class, "TITLE")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC) // need to fix AnnotationProcessor for final variables
                .initializer("$S", validator.getTitle())
                .build());
        fields.add(FieldSpec.builder(String.class, "DESCRIPTION")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .initializer("$S", validator.getDescription())
                .build());
        fields.add(FieldSpec.builder(String.class, "COMPILED_CODE")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .initializer("$S", validator.getCompiledCode())
                .build());
        fields.add(FieldSpec.builder(String.class, "HASH")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .initializer("$S", validator.getHash())
                .build());

        return fields;
    }

    private List<? extends TypeMirror> getExtendWithValues(ExtendWith extendWith) {
        try {
            extendWith.value();
        } catch (MirroredTypesException ex) {
            return ex.getTypeMirrors();
        }
        return null;
    }

    private TypeMirror getExtendWithValue(ExtendWith extendWith) {
        try {
            // This will throw MirroredTypeException
            extendWith.value();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        return null;
    }

    private static boolean isParameterizedValidator(Validator validator) {
        return validator.getParameters() != null && validator.getParameters().size() > 0;
    }

    private TypeMirror getTypeMirror(String type) {
        try {
            // This will throw MirroredTypeException
            type.getClass();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        return null;
    }

    private void error(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

}
