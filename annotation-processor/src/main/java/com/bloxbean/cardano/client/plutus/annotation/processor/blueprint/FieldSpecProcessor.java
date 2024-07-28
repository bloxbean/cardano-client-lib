package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.util.Tuple;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.CodeGenUtil.createMethodSpecsForGetterSetters;

@Slf4j
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
    public List<FieldSpec> createFieldSpecForDataTypes(String ns, String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();
        if(schema.getDataType() == null) { // Processing Anyplutusdata
            specs.add(dataTypeProcessUtil.processPlutusDataType(javaDoc, schema, alternativeName));

        } else {
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
                    specs.addAll(dataTypeProcessUtil.processConstructorDataType(ns, javaDoc, schema, className, alternativeName));
                    break;
                case string:
                    specs.add(dataTypeProcessUtil.processStringDataType(javaDoc, schema, alternativeName));
                    break;
                default:
            }
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
    public List<FieldSpec> createFieldSpecForDataTypes(String ns, String javaDoc, List<BlueprintSchema> schemas, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();
        for (BlueprintSchema schema : schemas) {
            specs.addAll(createFieldSpecForDataTypes(ns, javaDoc, schema, className, alternativeName));
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
    public FieldSpec createDatumFieldSpec(String ns, String interfaceName, BlueprintSchema schema, String suffix, String title) {
        return createDatumFieldSpec(ns, interfaceName, schema, suffix, title, "");
    }

    /**
     * Creates a FieldSpec for a given schema
     * @param schema
     * @param suffix
     * @param title
     * @param prefix
     * @return
     */
    //TODO - 1
    public FieldSpec createDatumFieldSpec(String ns, String interfaceName, BlueprintSchema schema, String suffix, String title, String prefix) {
        String classNameString = JavaFileUtil.buildClassName(schema, suffix, title, prefix);
        TypeSpec redeemerJavaFile = createDatumTypeSpec(ns, interfaceName, schema, classNameString, title);

        var dataType = schema.getDataType();

        String className = redeemerJavaFile.name;
        //If Datatype is null, then get the type from inner schema
        if (dataType == null) {
            var anyOfs = schema.getAnyOf();
           if (anyOfs != null && anyOfs.size() == 1) {
               className = anyOfs.get(0).getTitle();
           }
        }

        log.debug("---------- Inside createDatumFieldSpec ---------");
        log.debug("ClasNameString : " + classNameString);
        log.debug("RedeemerJavaFile : " + redeemerJavaFile.name);

        if (schema.getRefSchema() != null) {
            String refTitle = schema.getRefSchema().getTitle();
            className = refTitle != null ? refTitle : className;
        }

        ClassName classNameType = ClassName.get(getPackageName(ns), className);
        String fieldName = title + (schema.getDataType() == BlueprintDatatype.constructor ? String.valueOf(schema.getIndex()) : "") + suffix;

        return FieldSpec.builder(classNameType, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .build();
    }

    private TypeSpec createDatumTypeSpec(String ns, String interfaceName, BlueprintSchema schema, String className, String alternativeName) {
        Tuple<String, List<BlueprintSchema>> allInnerSchemas = FieldSpecProcessor.collectAllFields(schema);
        String title = schema.getTitle() == null ? alternativeName : schema.getTitle();
        List<FieldSpec> fields = createFieldSpecForDataTypes(ns, allInnerSchemas._1, allInnerSchemas._2, className, title);
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).addMember("alternative", "$L", schema.getIndex()).build();

        className = JavaFileUtil.firstUpperCase(title); //TODO

        String pkg = getPackageName(ns);

        ClassName datumClass = ClassName.get(pkg, className);
        ClassName DataClazz = ClassName.get(Data.class);
        ParameterizedTypeName parameterizedInterface = ParameterizedTypeName.get(DataClazz, datumClass);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addFields(fields)
                .addSuperinterface(parameterizedInterface)
                .addMethods(createMethodSpecsForGetterSetters(fields, false))
                .addAnnotation(constrAnnotationBuilder);

        if (interfaceName != null && !interfaceName.isEmpty()) {
            ClassName interfaceTypeName = ClassName.get(pkg, interfaceName);
            classBuilder.addSuperinterface(interfaceTypeName);
        }

        TypeSpec build = classBuilder.build();

        log.debug("---------- Inside createDatumTypeSpec ---------");
        log.debug("Package: " + pkg);
        log.debug("Class: " + className);
        log.debug("Data type: " + schema.getDataType());

        log.debug("\n");

        if (schema.getDataType() != null) {
            JavaFileUtil.createJavaFile(pkg, build, className, processingEnv);
        }

        return build;
    }

    private String getPackageName(String ns) {
        String pkg = (ns != null && !ns.isEmpty())? annotation.packageName() + "." + ns + ".model"
                : annotation.packageName() + ".model";
        return pkg;
    }

    public TypeSpec createDatumInterface(String ns, String dataClassName) {
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).build();

        String className = JavaFileUtil.firstUpperCase(dataClassName); //TODO
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(constrAnnotationBuilder);

        TypeSpec build = interfaceBuilder.build();
        String pkg = getPackageName(ns);

        JavaFileUtil.createJavaFile(pkg, build, className, processingEnv);

        return build;
    }
}
