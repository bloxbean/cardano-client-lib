package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.blueprint.extender.AbstractValidatorExtender;
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
        FieldSpec scriptAddrField = FieldSpec.builder(String.class, "scriptAddress")
                .addModifiers(Modifier.PRIVATE)
                .build();

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

//            fields.add(fieldSpecProcessor.createDatumFieldSpec("", validator.getDatum().getSchema(), "", validator.getDatum().getSchema().getTitle()));
//            fieldSpecProcessor.createDatumFieldSpec("", "", validator.getDatum().getSchema(), "Datum", title);

/**        if (validator.getParameters() != null) {
            for (BlueprintDatum parameter : validator.getParameters()) {
//                fields.add(fieldSpecProcessor.createDatumFieldSpec("", parameter.getSchema(), "", parameter.getSchema().getTitle()));
                fields.add(fieldSpecProcessor.createDatumFieldSpec("",parameter.getSchema(), "Parameter", title + parameter.getTitle()));
            }
        }**/

        List<MethodSpec> methods = new ArrayList<>();
        methods.addAll(createMethodSpecsForGetterSetters(metaFields, true));
        methods.addAll(createMethodSpecsForGetterSetters(fields, false));
        methods.add(getScriptAddressMethodSpec(plutusVersion));
        methods.add(getPlutusScriptMethodSpec(plutusVersion));

        String validatorClassName = title + VALIDATOR_CLASS_SUFFIX;
        // building and saving of class
        var builder = TypeSpec.classBuilder(validatorClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(GENERATED_CODE)
                .addMethod(getConstructorMethodSpec())
                .addFields(metaFields)
                .addField(scriptAddrField)
                .addFields(fields)
                .addMethods(methods);

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
    private MethodSpec getConstructorMethodSpec() {
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Network.class, "network")
                .addStatement("this.network = network")
                .build();
        return constructor;
    }

    private MethodSpec getScriptAddressMethodSpec(PlutusVersion plutusVersion) {
        MethodSpec getScriptAddress = MethodSpec.methodBuilder("getScriptAddress")
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addJavadoc("Returns the address of the validator script")
                .addStatement("var script = $T.getPlutusScriptFromCompiledCode(this.compiledCode, $T.$L)", PlutusBlueprintUtil.class, plutusVersion.getClass(), plutusVersion)
                .addStatement("if(scriptAddress == null) scriptAddress = $T.getEntAddress(script, network).toBech32()", AddressProvider.class)
                .addStatement("return scriptAddress")
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

        fields.add(FieldSpec.builder(Network.class, "network")
                .addModifiers(Modifier.PRIVATE)
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
