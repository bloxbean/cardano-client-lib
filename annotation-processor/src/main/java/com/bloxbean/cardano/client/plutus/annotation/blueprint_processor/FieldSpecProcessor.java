package com.bloxbean.cardano.client.plutus.annotation.blueprint_processor;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.util.Tuple;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeSpec;
import lombok.Data;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class FieldSpecProcessor {

    private final ProcessingEnvironment processingEnv;
    private final Blueprint annotation;
    private final DataTypeProcessUtil dataTypeProcessUtil;

    public FieldSpecProcessor(Blueprint annotation, ProcessingEnvironment processingEnv) {
        this.annotation = annotation;
        this.processingEnv = processingEnv;
        this.dataTypeProcessUtil = new DataTypeProcessUtil(this, annotation, processingEnv);
    }

    /**
     * Collects all fields from a schema including the ones from allOf, anyOf, oneOf, noneOf and returns them as a list
     * @param schema schema to collect fields from
     * @return list of all fields
     */
    public static Tuple<String, List<BlueprintSchema>> collectAllFields(BlueprintSchema schema) {
        List<BlueprintSchema> toFields = new ArrayList<>();
        String javaDoc = "";
        if(schema.getAllOf() != null) {
            toFields.addAll(schema.getAllOf());
            javaDoc = "AllOf";
        } else if(schema.getAnyOf() != null) {
            toFields.addAll(schema.getAnyOf());
            javaDoc = "AnyOf";
        } else if(schema.getOneOf() != null) {
            toFields.addAll(schema.getOneOf());
            javaDoc = "OneOf";
        } else {
            toFields.add(schema);
        }
        return new Tuple<>(javaDoc, toFields);
    }

    /**
     * Creates a list of FieldSpecs for a given schema
     * @param javaDoc
     * @param schema
     * @param className
     * @return
     */
    public List<FieldSpec> CreateFieldSpecForDataTypes(String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();
        switch (schema.getDataType()) {
            case bytes:
                specs.add(dataTypeProcessUtil.processBytesDataType(javaDoc, schema, alternativeName));
                break;
            case integer:
                specs.add(dataTypeProcessUtil.processIntegerDataType(javaDoc, schema, alternativeName));
                break;
            case bool:
                specs.add(dataTypeProcessUtil.processBoolDataType(javaDoc, schema, alternativeName));
                break;
            case list:
                specs.add(dataTypeProcessUtil.processListDataType(javaDoc, schema, alternativeName));
                break;
            case map:
                specs.add(dataTypeProcessUtil.processMapDataType(javaDoc, schema, className, alternativeName));
                break;
            case constructor:
                specs.addAll(dataTypeProcessUtil.processConstructorDataType(javaDoc, schema, className, alternativeName));
                break;
            case string:
                specs.add(dataTypeProcessUtil.processStringDataType(javaDoc, schema, alternativeName));
                break;
            default:
        }
        return specs;
    }

    /**
     * Creates a list of FieldSpecs for a given list of schemas
     * @param javaDoc
     * @param schemas
     * @param className
     * @return
     */
    public List<FieldSpec> CreateFieldSpecForDataTypes(String javaDoc, List<BlueprintSchema> schemas, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();
        for (BlueprintSchema schema : schemas) {
            specs.addAll(CreateFieldSpecForDataTypes(javaDoc, schema, className, alternativeName));
        }
        return specs;
    }

    /**
     * Creates a FieldSpec for a given schema
     * @param schema
     * @param suffix
     * @param title
     * @return
     */
    public FieldSpec createDatumFieldSpec(BlueprintSchema schema, String suffix, String title) {
        return createDatumFieldSpec(schema, suffix, title, "");
    }

    /**
     * Creates a FieldSpec for a given schema
     * @param schema
     * @param suffix
     * @param title
     * @param prefix
     * @return
     */
    public FieldSpec createDatumFieldSpec(BlueprintSchema schema, String suffix, String title, String prefix) {
        String classNameString = JavaFileUtil.buildClassName(schema, suffix, title, prefix);
        TypeSpec redeemerJavaFile = createDatumTypeSpec(schema, classNameString, title);

        ClassName className = ClassName.get(annotation.packageName(), redeemerJavaFile.name);
        String fieldName = title + (schema.getDataType() == BlueprintDatatype.constructor ? String.valueOf(schema.getIndex()) : "") + suffix;

        return FieldSpec.builder(className, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    private TypeSpec createDatumTypeSpec(BlueprintSchema schema, String className, String alternativeName) {
        Tuple<String, List<BlueprintSchema>> allInnerSchemas = FieldSpecProcessor.collectAllFields(schema);
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        List<FieldSpec> fields = CreateFieldSpecForDataTypes(allInnerSchemas._1, allInnerSchemas._2, className, title);
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).addMember("alternative", "$L", schema.getIndex()).build();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addFields(fields)
                .addAnnotation(constrAnnotationBuilder)
                .addAnnotation(Data.class);

        TypeSpec build = classBuilder.build();
        JavaFileUtil.createJavaFile(annotation.packageName(), build, className, processingEnv);
        return build;
    }
}
