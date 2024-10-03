package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
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
import static com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil.toPackageNameFormat;

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
     * Creates a Datum class for a given schema
     * If the schema has anyOf &gt; 1, it creates an interface and a class for each of the anyOf
     * This is the main method called to create Datum classes from BlueprintAnnotationProcessor
     * @param ns     namespace or package suffix
     * @param schema Definition schema to create Datum class
     */
    public void createDatumClass(String ns, BlueprintSchema schema) {
        String dataClassName = schema.getTitle();
        if (dataClassName == null || dataClassName.isEmpty()) {
            return;
        }

        //If there is no fields, then it's just an alias. So ignore class generation
        if(schema.getDataType() != null && schema.getDataType().isPrimitiveType()
                && schema.getItems() == null
                && (schema.getFields() == null || schema.getFields().size() == 0)
                && (schema.getAnyOf() == null || schema.getAnyOf().size() == 0)
                && (schema.getAllOf() == null || schema.getAllOf().size() == 0)
                && (schema.getOneOf() == null || schema.getOneOf().size() == 0)
                && (schema.getNotOf() == null || schema.getNotOf().size() == 0)) {
            return;
        }

        //Check if it's an Option, then also return
        if ("Option".equals(dataClassName)) {
            if(isOptionType(schema))
                return;
        }

        if ("Pair".equals(dataClassName)) {
            if (schema.getDataType() == BlueprintDatatype.pair)
                return;
        }

        dataClassName = JavaFileUtil.toClassNameFormat(dataClassName);


        //Check if Enum: Check if the schema has anyOf > 1 and each of the anyOf has 0 fields
        if(createEnumIfPossible(ns, schema))
            return;

        String interfaceName = null;
        //For anyOf > 1, create an interface, if size == 1, create a class
        //TODO -- What about allOf ??
        if (schema.getAnyOf() != null && schema.getAnyOf().size() > 1) {
            log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
            //More than one constructor. So let's create an interface
            createDatumInterface(ns, dataClassName, schema);
            interfaceName = dataClassName;
        }

        Tuple<String, List<BlueprintSchema>> allFields = FieldSpecProcessor.collectAllFields(schema);
        for (BlueprintSchema innerSchema : allFields._2) {
            dataClassName = schema.getTitle();
            if (dataClassName == null || dataClassName.isEmpty()) {
                continue;
            }

            dataClassName = JavaFileUtil.toClassNameFormat(dataClassName);
            createDatumFieldSpec(ns, interfaceName, innerSchema, dataClassName);
        }
    }

    private boolean isOptionType(BlueprintSchema schema) {
        //It's an option type if it has two anyOfs with first one as Some and second one as None
        if(schema.getAnyOf() == null || schema.getAnyOf().size() != 2)
            return false;

        BlueprintSchema someSchema = schema.getAnyOf().get(0);
        BlueprintSchema noneSchema = schema.getAnyOf().get(1);

        if(someSchema.getTitle() == null || noneSchema.getTitle() == null)
            return false;

        if(!"Some".equals(someSchema.getTitle()) || !"None".equals(noneSchema.getTitle()))
            return false;

        if(someSchema.getFields() == null || someSchema.getFields().size() != 1)
            return false;

        if(noneSchema.getFields() != null && noneSchema.getFields().size() != 0)
            return false;

        return true;
    }

    private boolean createEnumIfPossible(String ns, BlueprintSchema schema) {
        if (schema.getAnyOf() == null || !(schema.getAnyOf().size() > 1))
            return false;

        if (schema.getFields() != null && schema.getFields().size() != 0)
            return false;

        //check if each of the anyOf has 0 fields
        List<String> enumValues = new ArrayList<>();
        for (BlueprintSchema anyOfSchema : schema.getAnyOf()) {
            if (BlueprintDatatype.constructor != anyOfSchema.getDataType())
                return false;

            if (anyOfSchema.getTitle() == null || anyOfSchema.getTitle().isEmpty())
                return false;

            if (anyOfSchema.getFields() != null && anyOfSchema.getFields().size() > 0)
                return false;

                enumValues.add(anyOfSchema.getTitle());
        }

        String pkg = getPackageName(ns);
        String enumClassName = JavaFileUtil.toClassNameFormat(schema.getTitle());

        var enumConstrBuilder = TypeSpec.enumBuilder(enumClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Constr.class).build());

        //add enum values
        for(String value: enumValues) {
            enumConstrBuilder.addEnumConstant(value);
        }

        JavaFileUtil.createJavaFile(pkg, enumConstrBuilder.build(), enumClassName, processingEnv);

        return true;
    }

    /**
     * Get the inner class name for a given schema. This is currently used to find the generic types for list and map
     *
     * @param ns     namespace or package suffix
     * @param schema schema to get the inner class name
     * @return ClassName of the inner class
     */
    public ClassName getInnerDatumClass(String ns, BlueprintSchema schema) {
        String dataClassName = schema.getTitle();

        if (dataClassName != null)
            dataClassName = JavaFileUtil.toClassNameFormat(dataClassName);

        String finalNS = BlueprintUtil.getNSFromReference(schema.getRef());
        String pkg = getPackageName(finalNS);

        //For anyOf > 1, create an interface, if size == 1, create a class
        //TODO -- What about allOf ??
        if (schema.getAnyOf() != null && schema.getAnyOf().size() > 1) {
            log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
            //More than one constructor. So it's an interface

            return ClassName.get(pkg, dataClassName);
        }

        if (schema.getAnyOf() != null && schema.getAnyOf().size() == 1) {
            var anyOfSchema = schema.getAnyOf().get(0);
            dataClassName = anyOfSchema.getTitle();
            if (dataClassName != null) {
                dataClassName = JavaFileUtil.toClassNameFormat(dataClassName);

                return ClassName.get(pkg, dataClassName);
            }
        }

        //Otherwise
        return ClassName.get(pkg, dataClassName);
    }

    /**
     * Collects all fields from a schema including the ones from allOf, anyOf, oneOf, noneOf and returns them as a list
     *
     * @param schema schema to collect fields from
     * @return list of all fields
     */
    public static Tuple<String, List<BlueprintSchema>> collectAllFields(BlueprintSchema schema) {
        List<BlueprintSchema> toFields = new ArrayList<>();
        String javaDoc = "";
        if (schema.getAllOf() != null) {
            toFields.addAll(schema.getAllOf());
            javaDoc = "AllOf";
        } else if (schema.getAnyOf() != null) {
            toFields.addAll(schema.getAnyOf());
            javaDoc = "AnyOf";
        } else if (schema.getOneOf() != null) {
            toFields.addAll(schema.getOneOf());
            javaDoc = "OneOf";
        } else {
            toFields.add(schema);
        }
        return new Tuple<>(javaDoc, toFields);
    }

    /**
     * Creates a Datum interface for a given schema. This is used when a schema has anyOf &gt; 1
     *
     * @param ns            namespace or package suffix
     * @param dataClassName name of the interface
     * @return ClassName of the interface
     */
    public ClassName createDatumInterface(String ns, String dataClassName, BlueprintSchema schema) {
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).build();

        String className = JavaFileUtil.toClassNameFormat(dataClassName); //TODO
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(constrAnnotationBuilder);

        TypeSpec build = interfaceBuilder.build();
        String pkg = getPackageName(ns);

        JavaFileUtil.createJavaFile(pkg, build, className, processingEnv);

        return ClassName.get(pkg, className);
    }

    /**
     * Creates a Datum field for a given schema. It also creates a Datum class for the schema if it's not already created
     *
     * @param ns            namespace or package suffix
     * @param interfaceName Optional interface name if the schema has anyOf &gt; 1
     * @param schema        schema to create Datum field
     * @param title         title of the schema
     * @return Tuple of FieldSpec and ClassName of the field
     */
    public Tuple<FieldSpec, ClassName> createDatumFieldSpec(String ns, String interfaceName, BlueprintSchema schema, String title) {
        String classNameString = JavaFileUtil.toClassNameFormat(title);//JavaFileUtil.buildClassName(schema, suffix, title, prefix);
        TypeSpec redeemerJavaFile = createDatumTypeSpec(ns, interfaceName, schema);

        String className = redeemerJavaFile.name;

        log.debug("---------- Inside createDatumFieldSpec ---------");
        log.debug("ClasNameString : " + classNameString);
        log.debug("RedeemerJavaFile : " + redeemerJavaFile.name);

        if (schema.getRefSchema() != null) {
            String refTitle = schema.getRefSchema().getTitle();
            className = refTitle != null ? refTitle : className;
            className = JavaFileUtil.toClassNameFormat(className);
        }

        String finalNS = BlueprintUtil.getNSFromReference(schema.getRef());;
        String pkg = getPackageName(finalNS);

        ClassName classNameType = ClassName.get(pkg, className);

        String fieldName = title; // + (schema.getDataType() == BlueprintDatatype.constructor ? String.valueOf(schema.getIndex()) : "") ;
        fieldName = JavaFileUtil.firstLowerCase(JavaFileUtil.toCamelCase(fieldName));
        var fieldSpec = FieldSpec.builder(classNameType, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .build();

        return new Tuple<>(fieldSpec, classNameType);
    }

    private TypeSpec createDatumTypeSpec(String ns, String interfaceName, BlueprintSchema schema) {
        Tuple<String, List<BlueprintSchema>> allInnerSchemas = FieldSpecProcessor.collectAllFields(schema);

        List<FieldSpec> fields = null;

        if (schema.getDataType() != null) {
            fields = createFieldSpecForDataTypes(ns, allInnerSchemas._1, allInnerSchemas._2);
        } else {
            fields = new ArrayList<>();
        }

        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).addMember("alternative", "$L", schema.getIndex()).build();

        String title = schema.getTitle();

        String className = JavaFileUtil.toClassNameFormat(title); //TODO

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
        } else {
            log.debug("Datatype is null. Looks like we don't need to create a class for this schema");
        }

        return build;
    }

    public List<FieldSpec> createFieldSpecForDataTypes(String ns, String javaDoc, List<BlueprintSchema> schemas) {
        List<FieldSpec> specs = new ArrayList<>();
        for (BlueprintSchema schema : schemas) {
            specs.addAll(createFieldSpecForDataTypes(ns, javaDoc, schema, "", schema.getTitle()));
        }
        return specs;
    }

    public List<FieldSpec> createFieldSpecForDataTypes(String ns, String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        List<FieldSpec> specs = new ArrayList<>();

        var schemaType = schema.getDataType();
        if (schemaType == null) { // Processing Anyplutusdata
            specs.add(dataTypeProcessUtil.processPlutusDataType(javaDoc, schema, alternativeName));
        } else {
            switch (schemaType) {
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
                    specs.add(dataTypeProcessUtil.processListDataType(ns, javaDoc, schema, alternativeName));
                    break;
                case map:
                    specs.add(dataTypeProcessUtil.processMapDataType(ns, javaDoc, schema, className, alternativeName));
                    break;
                case constructor:
                    specs.addAll(dataTypeProcessUtil.processConstructorDataType(ns, javaDoc, schema, className, alternativeName));
                    break;
                case string:
                    specs.add(dataTypeProcessUtil.processStringDataType(javaDoc, schema, alternativeName));
                    break;
                case option:
                    specs.add(dataTypeProcessUtil.processOptionDataType(ns, javaDoc, schema, alternativeName));
                    break;
                case pair:
                    specs.add(dataTypeProcessUtil.processPairDataType(ns, javaDoc, schema, alternativeName));
                    break;
                default:
            }
        }
        return specs;
    }

    private String getPackageName(String ns) {
        String pkg = (ns != null && !ns.isEmpty()) ? annotation.packageName() + "." + ns + ".model"
                : annotation.packageName() + ".model";

        return toPackageNameFormat(pkg);
    }
}
